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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class Shard(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

private val shards = listOf(
    Shard(0.86f, 0.18f, 64f, 0.0f, 6f),
    Shard(0.68f, 0.82f, 38f, 0.35f, 8f),
    Shard(0.52f, 0.12f, 26f, 0.7f, 5f),
    Shard(0.18f, 0.74f, 30f, 0.5f, 7f),
)

/** Every motion repeats a whole number of times per loop, so the loop has no visible jump. */
private const val LOOP_S = 60f

@Composable
internal fun PrismaWallpaper(modifier: Modifier = Modifier) {
    val still = Motion.scale == 0f
    val clock by rememberInfiniteTransition(label = "prismaWallpaper")
        .animateFloat(0f, 1f, infiniteRepeatable(tween((LOOP_S * 1000).toInt(), easing = LinearEasing)), label = "clock")
    val tri = remember { Path() }
    Canvas(modifier) {
        val turn = if (still) 0f else clock * 2f * PI.toFloat()
        val w = size.width; val h = size.height
        val big = maxOf(w, h)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0D1E), Color(0xFF070915), Color(0xFF05060F))))

        val glows = listOf(Color(0xFF1E40AF) to 0f, Color(0xFF4338CA) to 2.1f, Color(0xFF7E22CE) to 4.2f)
        for ((c, start) in glows) {
            val a = turn + start
            val at = Offset(w * (0.55f + 0.30f * cos(a)), h * (0.45f + 0.32f * sin(a)))
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.42f), Color.Transparent), at, big * 0.48f), big * 0.48f, at)
        }

        val cell = 48.dp.toPx()
        val grid = Color(0xFF6366F1).copy(alpha = 0.06f)
        var gx = 0f
        while (gx < w) { drawLine(grid, Offset(gx, 0f), Offset(gx, h), 1f); gx += cell }
        var gy = 0f
        while (gy < h) { drawLine(grid, Offset(0f, gy), Offset(w, gy), 1f); gy += cell }

        val gap = 60.dp.toPx()
        val drift = (if (still) 0f else clock * 5f % 1f) * gap * 4f
        rotate(25f, Offset(w / 2f, h / 2f)) {
            var x = -big - gap + drift % gap
            while (x < big * 2f) {
                drawLine(Color.White.copy(alpha = 0.025f), Offset(x, -big), Offset(x, big * 2f), 2.dp.toPx())
                x += gap
            }
            val beam = 120.dp.toPx()
            listOf(0.10f to Color(0xFF818CF8), 0.45f to Color(0xFFC084FC), 0.80f to Color(0xFF818CF8)).forEachIndexed { i, (pos, c) ->
                val sweep = sin(turn * 7f + i * 2.1f)
                val cx = w * pos + sweep * 100.dp.toPx()
                val glow = 0.13f + 0.09f * (sweep + 1f) / 2f
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, c.copy(alpha = glow), Color.Transparent), cx - beam / 2f, cx + beam / 2f),
                    Offset(cx - beam / 2f, -big), androidx.compose.ui.geometry.Size(beam, big * 3f),
                )
            }
        }

        for (s in shards) {
            val bob = sin(turn * s.speed + s.phase * 6.28f)
            val side = s.size.dp.toPx()
            val cx = w * s.x; val cy = h * s.y - bob * 14.dp.toPx()
            val rot = bob * 12f
            tri.reset()
            tri.moveTo(cx, cy - side * 0.58f)
            tri.lineTo(cx + side / 2f, cy + side * 0.29f)
            tri.lineTo(cx - side / 2f, cy + side * 0.29f)
            tri.close()
            rotate(rot, Offset(cx, cy)) {
                drawPath(tri, Brush.linearGradient(listOf(Color(0xFF6366F1).copy(alpha = 0.22f), Color.Transparent), Offset(cx, cy - side), Offset(cx, cy + side)))
                drawPath(tri, Color(0xFFA5B4FC).copy(alpha = 0.30f), style = Stroke(1.dp.toPx()))
            }
        }

        drawRect(Brush.verticalGradient(listOf(Color(0xFF070915).copy(alpha = 0.55f), Color.Transparent, Color(0xFF070915).copy(alpha = 0.65f))))
    }
}

@Composable
internal fun prismaLook(): Boolean = LocalPalette.current.id == Themes.PRISMA
