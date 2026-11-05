package com.droiddeck.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

private class Mote(val x: Float, val size: Float, val speed: Float, val phase: Float, val tint: Int)

private val motes: List<Mote> = List(28) { i ->
    fun r(k: Int): Float { val v = sin((i * 127.1 + k * 311.7)) * 43758.5453; return (v - kotlin.math.floor(v)).toFloat() }
    Mote(r(1), 1.5f + r(2) * 3.5f, 0.05f + r(3) * 0.09f, r(4), (r(5) * 3).toInt())
}

/**
 * The animated ground behind a store's own screen: the store colour's glow, a slowly turning dashed ring,
 * a breathing halo and motes rising through it. With animations off it holds still.
 */
@Composable
internal fun StoreBackdrop(accent: Color, modifier: Modifier = Modifier, centerY: Float = 0.3f) {
    val bg = MaterialTheme.colorScheme.background
    val signal = LocalPalette.current.signal
    val still = Motion.scale == 0f
    val clock by rememberInfiniteTransition(label = "storeBackdrop")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "clock")
    Canvas(modifier.fillMaxSize()) {
        val t = if (still) 0f else clock * 40f
        val c = Offset(size.width / 2f, size.height * centerY)
        val r = minOf(size.width, size.height) * 0.42f
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.42f), bg.copy(alpha = 0f)), c, maxOf(size.width, size.height) * 0.75f))
        val breath = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 3.5f)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f + 0.14f * breath), Color.Transparent), c, r * (0.8f + 0.1f * breath)), r, c)
        rotate(t * 9f, c) {
            drawCircle(accent.copy(alpha = 0.35f), r, c, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f))))
        }
        rotate(-t * 5f, c) {
            drawCircle(signal.copy(alpha = 0.18f), r * 0.72f, c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 18f))))
        }
        for (m in motes) {
            val y = ((m.phase - t * m.speed) % 1f + 1f) % 1f
            val fade = sin(PI.toFloat() * y)
            val colour = when (m.tint) { 0 -> accent; 1 -> signal; else -> Color.White }
            val p = Offset(m.x * size.width, y * size.height * 1.05f)
            drawCircle(colour.copy(alpha = 0.18f * fade), m.size.dp.toPx() * 3f, p)
            drawCircle(colour.copy(alpha = 0.75f * fade), m.size.dp.toPx(), p)
        }
    }
}

/** The store's tile: its letter on its colour, with a light band sweeping across. */
@Composable
internal fun StoreTile(accent: Color, glyph: String, size: Dp = 112.dp, ink: Color = Color.White) {
    val still = Motion.scale == 0f
    val sweep by rememberInfiniteTransition(label = "storeTile")
        .animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sweep")
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
            .graphicsLayer { shadowElevation = 24.dp.toPx(); this.shape = shape; clip = false; ambientShadowColor = accent; spotShadowColor = accent }
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f).compositeOver(Color.Black))))
            .drawWithContent {
                drawContent()
                if (!still) {
                    val x = size.toPx() * sweep
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), x - 30.dp.toPx(), x + 30.dp.toPx()))
                }
            },
    ) {
        Text(glyph, color = ink, fontSize = (size.value * 0.46f).sp, fontWeight = FontWeight.Black)
    }
}

private fun Color.compositeOver(under: Color): Color {
    val a = alpha
    return Color(red * a + under.red * (1 - a), green * a + under.green * (1 - a), blue * a + under.blue * (1 - a), 1f)
}

/** Backdrop plus tile, laid out for a store's screen. */
@Composable
internal fun StoreHero(accent: Color, glyph: String, modifier: Modifier = Modifier, centerY: Float = 0.3f) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        StoreBackdrop(accent, Modifier.fillMaxSize(), centerY)
        val tile = 112.dp
        Box(Modifier.offset(x = maxWidth / 2 - tile / 2, y = maxHeight * centerY - tile / 2)) { StoreTile(accent, glyph, tile) }
    }
}
