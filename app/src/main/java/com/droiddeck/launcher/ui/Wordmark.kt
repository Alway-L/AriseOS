package com.droiddeck.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.hud.DroidDeckMark

@Composable
internal fun Wordmark(height: Dp, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = LocalPalette.current.signal
    val name = stringResource(R.string.app_name)
    val fontSize = with(LocalDensity.current) { (height * 0.82f).toSp() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.28f),
        modifier = modifier.height(height).clearAndSetSemantics { contentDescription = name },
    ) {
        Canvas(Modifier.height(height).width(height * (DroidDeckMark.VIEW_W / DroidDeckMark.VIEW_H))) {
            drawIntoCanvas { DroidDeckMark.draw(it.nativeCanvas, 0f, 0f, size.height, ink.toArgb(), accent.toArgb()) }
        }
        Text(name, color = ink, fontSize = fontSize, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
    }
}
