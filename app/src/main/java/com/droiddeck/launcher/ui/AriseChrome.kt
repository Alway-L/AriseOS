package com.droiddeck.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The AriseOS page title: the A mark, a small line above and the page name in black weight. */
@Composable
internal fun AriseTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val narrow = LocalNarrowPane.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        DroidDeckLogo(if (narrow) 26.dp else 30.dp, body = colors.onBackground, orb = LocalPalette.current.signal)
        Column {
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, fontSize = if (narrow) 22.sp else 26.sp, fontWeight = FontWeight.Black, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
