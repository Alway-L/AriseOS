package com.droiddeck.launcher.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/** The AriseOS loading mark: the chevron rises into place over the A, the inner triangle pulses as it lands, and a light band sweeps up the A. */
@Composable
internal fun LogoThrobber(modifier: Modifier = Modifier, running: Boolean = true) {
    val ink = MaterialTheme.colorScheme.onBackground
    val pal = LocalPalette.current
    val low = pal.primary
    val high = pal.signal
    val loop = rememberInfiniteTransition(label = "throbber")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)), label = "loop")
    val still = !running || Motion.scale == 0f
    val body = remember { Path() }
    val chevron = remember { Path() }
    val accent = remember { Path() }
    Canvas(modifier.aspectRatio(LogoShape.WIDTH / LogoShape.HEIGHT)) {
        val p = if (still) REST else loop.value
        val s = size.width / LogoShape.WIDTH
        fun px(x: Float, y: Float) = Offset((x - LogoShape.LEFT) * s, y * s)
        LogoShape.left(body, 0f, ::px)
        LogoShape.right(chevron, ::px)
        LogoShape.accent(accent, ::px)
        val top = px(LogoShape.CX, LogoShape.TOP).y
        val bottom = px(LogoShape.CX, LogoShape.BOTTOM).y

        drawPath(body, Brush.verticalGradient(listOf(high, low), startY = top, endY = bottom))
        if (!still) {
            val band = bottom - (bottom - top + BAND * s * 2) * Sweep.transform(p) + BAND * s
            clipPath(body) {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.32f), 1f to Color.Transparent,
                        startY = band - BAND * s, endY = band + BAND * s,
                    ),
                )
            }
        }

        val (rise, alpha) = when {
            p < ARRIVE -> {
                val q = Overshoot.transform(p / ARRIVE)
                (1f - q) * RISE_FROM to (p / ARRIVE).coerceIn(0f, 1f)
            }
            p < LEAVE -> 0f to 1f
            else -> {
                val q = Leave.transform((p - LEAVE) / (1f - LEAVE))
                -q * RISE_TO to 1f - q
            }
        }
        translate(0f, rise * s) { drawPath(chevron, ink.copy(alpha = alpha)) }
        if (p > LEAVE) {
            val q = (p - LEAVE) / (1f - LEAVE)
            translate(0f, RISE_FROM * s * (1f - q)) { drawPath(chevron, ink.copy(alpha = 0.25f * q)) }
        }

        val pulse = if (still) 0f else exp(-((p - ARRIVE) / 0.08f).let { it * it })
        val centre = px(LogoShape.CX, LogoShape.CY)
        if (pulse > 0.01f) drawCircle(
            Brush.radialGradient(listOf(high.copy(alpha = 0.55f * pulse), Color.Transparent), centre, LogoShape.BALL * s * 2.2f),
            LogoShape.BALL * s * 2.2f, centre,
        )
        scale(1f + 0.14f * pulse, 1f + 0.14f * pulse, centre) { drawPath(accent, high) }
    }
}

internal fun throbberBall(bounds: androidx.compose.ui.geometry.Rect): Pair<Offset, Float> {
    val s = bounds.width / LogoShape.WIDTH
    return Offset(bounds.left + (LogoShape.CX - LogoShape.LEFT) * s, bounds.top + LogoShape.CY * s) to LogoShape.BALL * s
}

private const val LOOP_MS = 1900
private const val REST = 0.5f
private const val ARRIVE = 0.34f
private const val LEAVE = 0.72f
private const val RISE_FROM = 150f
private const val RISE_TO = 70f
private const val BAND = 90f
private val Overshoot = CubicBezierEasing(0.22f, 1.35f, 0.4f, 1f)
private val Leave = CubicBezierEasing(0.55f, 0f, 0.9f, 0.6f)
private val Sweep = CubicBezierEasing(0.45f, 0f, 0.35f, 1f)

/** The AriseOS mark in artwork/ariseos-icon.svg units, shifted so the chevron's tip sits at y = 0. */
internal object LogoShape {
    const val LEFT = 254f
    const val WIDTH = 516f
    const val HEIGHT = 575f
    const val CX = 512f
    const val CY = 489f
    const val BALL = 48f
    const val TOP = 0f
    const val BOTTOM = 565f
    private const val Y0 = 175f
    private const val CHEVRON_HALF = 11f

    private class Pt(val x: Float, val y: Float)

    private val bodyPts = listOf(
        Pt(512f, 250f), Pt(760f, 740f), Pt(650f, 740f), Pt(512f, 462f), Pt(374f, 740f), Pt(264f, 740f),
    ).map { Pt(it.x, it.y - Y0) }

    private val accentPts = listOf(Pt(512f, 560f), Pt(590f, 716f), Pt(434f, 716f)).map { Pt(it.x, it.y - Y0) }

    private val chevronPts: List<Pt> = run {
        val a = Pt(392f, 270f - Y0)
        val b = Pt(512f, 190f - Y0)
        val c = Pt(632f, 270f - Y0)
        val len = hypot(b.x - a.x, b.y - a.y)
        val nx = -(b.y - a.y) / len
        val ny = (b.x - a.x) / len
        val miter = CHEVRON_HALF / ((b.x - a.x) / len)
        fun cap(cx: Float, cy: Float, from: Double, sweep: Double) = List(9) { i ->
            val t = from + sweep * i / 8
            Pt(cx + CHEVRON_HALF * cos(t).toFloat(), cy + CHEVRON_HALF * sin(t).toFloat())
        }
        val upA = Pt(a.x - nx * CHEVRON_HALF, a.y - ny * CHEVRON_HALF)
        val lowA = Pt(a.x + nx * CHEVRON_HALF, a.y + ny * CHEVRON_HALF)
        val upC = Pt(c.x + nx * CHEVRON_HALF, c.y - ny * CHEVRON_HALF)
        val lowC = Pt(c.x - nx * CHEVRON_HALF, c.y + ny * CHEVRON_HALF)
        val angA = atan2((upA.y - a.y).toDouble(), (upA.x - a.x).toDouble())
        listOf(upA, Pt(b.x, b.y - miter), upC) +
            cap(c.x, c.y, atan2((upC.y - c.y).toDouble(), (upC.x - c.x).toDouble()), PI) +
            listOf(lowC, Pt(b.x, b.y + miter), lowA) +
            cap(a.x, a.y, angA + PI, PI)
    }

    private fun fill(path: Path, pts: List<Pt>, px: (Float, Float) -> Offset) {
        path.reset()
        pts.forEachIndexed { i, q ->
            val o = px(q.x, q.y)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        path.close()
    }

    /** The A itself; [bend] is kept for callers written for the old mark and has no effect. */
    @Suppress("UNUSED_PARAMETER")
    fun left(path: Path, bend: Float, px: (Float, Float) -> Offset) = fill(path, bodyPts, px)

    /** The rising chevron above the A. */
    fun right(path: Path, px: (Float, Float) -> Offset) = fill(path, chevronPts, px)

    /** The inner triangle, drawn in the accent colour. */
    fun accent(path: Path, px: (Float, Float) -> Offset) = fill(path, accentPts, px)
}
