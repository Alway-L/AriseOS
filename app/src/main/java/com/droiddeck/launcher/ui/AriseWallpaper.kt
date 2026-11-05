package com.droiddeck.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private class Spark(val x: Float, val size: Float, val speed: Float, val phase: Float, val cyan: Boolean)

private val sparks: List<Spark> = List(36) { i ->
    fun r(k: Int): Float { val v = sin(i * 91.7 + k * 271.3) * 43758.5453; return (v - floor(v)).toFloat() }
    Spark(r(1), 1.2f + r(2) * 3f, 0.04f + r(3) * 0.08f, r(4), r(5) > 0.55f)
}

/**
 * AriseOS's own animated wallpaper: a purple night with a cyan aurora drifting across it, a horizon grid
 * flowing towards the viewer, the "A" mark breathing behind everything and sparks rising. With animations
 * off it holds still.
 */
@Composable
internal fun AriseWallpaper(modifier: Modifier = Modifier, markX: Float = 0.72f, markY: Float = 0.46f) {
    val pal = LocalPalette.current
    val purple = pal.primary
    val cyan = pal.signal
    val still = Motion.scale == 0f
    val clock by rememberInfiniteTransition(label = "ariseWallpaper")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "clock")
    val body = remember { Path() }
    val accent = remember { Path() }
    val chevron = remember { Path() }
    Canvas(modifier) {
        val t = if (still) 0f else clock * 60f
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0D0820), Color(0xFF090614), Color(0xFF050309))))

        // Aurora: two soft blobs drifting in slow figure-eights.
        val a1 = Offset(w * (0.30f + 0.12f * sin(t * 0.11f)), h * (0.28f + 0.06f * sin(t * 0.17f)))
        val a2 = Offset(w * (0.78f + 0.10f * sin(t * 0.09f + 2f)), h * (0.22f + 0.08f * sin(t * 0.13f + 1f)))
        drawCircle(Brush.radialGradient(listOf(purple.copy(alpha = 0.45f), Color.Transparent), a1, maxOf(w, h) * 0.55f), maxOf(w, h) * 0.55f, a1)
        drawCircle(Brush.radialGradient(listOf(cyan.copy(alpha = 0.22f), Color.Transparent), a2, maxOf(w, h) * 0.45f), maxOf(w, h) * 0.45f, a2)

        // Horizon grid flowing forward.
        val horizon = h * 0.70f
        val vp = Offset(w * 0.5f, horizon)
        val line = 1.dp.toPx()
        for (i in -12..12) {
            val x = w * 0.5f + i * w * 0.11f
            drawLine(purple.copy(alpha = 0.10f), vp, Offset(vp.x + (x - vp.x) * 3f, h), line)
        }
        val flow = (t * 0.25f) % 1f
        for (k in 0 until 9) {
            val d = (k + flow) / 9f
            val y = horizon + (h - horizon) * d * d
            drawLine(cyan.copy(alpha = 0.04f + 0.14f * d), Offset(0f, y), Offset(w, y), line)
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, purple.copy(alpha = 0.16f), Color.Transparent), horizon - h * 0.08f, horizon + h * 0.04f))

        // The mark, breathing.
        val breath = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 5f)
        val markH = h * 0.62f
        val s = markH / LogoShape.HEIGHT
        val origin = Offset(w * markX - LogoShape.WIDTH * s / 2f, h * markY - markH / 2f)
        fun px(x: Float, y: Float) = Offset(origin.x + (x - LogoShape.LEFT) * s, origin.y + y * s)
        body.reset(); accent.reset(); chevron.reset()
        LogoShape.left(body, 0f, ::px); LogoShape.accent(accent, ::px); LogoShape.right(chevron, ::px)
        val centre = px(LogoShape.CX, LogoShape.CY)
        drawCircle(Brush.radialGradient(listOf(purple.copy(alpha = 0.22f + 0.12f * breath), Color.Transparent), centre, markH * 0.75f), markH * 0.75f, centre)
        drawPath(body, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f + 0.04f * breath), purple.copy(alpha = 0.06f)), origin.y, origin.y + markH))
        drawPath(body, purple.copy(alpha = 0.30f + 0.15f * breath), style = Stroke(1.5.dp.toPx()))
        drawPath(accent, Brush.linearGradient(listOf(cyan.copy(alpha = 0.35f + 0.25f * breath), purple.copy(alpha = 0.30f)), origin, origin + Offset(markH, markH)))
        drawPath(chevron, cyan.copy(alpha = 0.30f + 0.30f * breath))

        // Sparks rising.
        for (p in sparks) {
            val y = ((p.phase - t * p.speed) % 1f + 1f) % 1f
            val fade = sin(PI.toFloat() * y)
            val c = if (p.cyan) cyan else Color(0xFFC4B5FD)
            val at = Offset(p.x * w + sin(t * 0.3f + p.phase * 6f) * 8.dp.toPx(), y * h * 1.05f)
            drawCircle(c.copy(alpha = 0.16f * fade), p.size.dp.toPx() * 3f, at)
            drawCircle(c.copy(alpha = 0.70f * fade), p.size.dp.toPx(), at)
        }
    }
}
