package com.droiddeck.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R

/** Pages reached from the Área de trabalho tab instead of having a tab of their own. */
internal val WORKSPACE_PAGES = setOf("steam", "stores", "android-apps")

internal const val WORKSPACE_TAB = "desktop"

@Composable
internal fun WorkspaceShortcuts(s: FrontEndState) {
    val show = LocalShowSection.current
    val density = LocalDensity.current.density
    fun open(at: Rect?, page: String) {
        at?.let { PageOrigin.mark(Origin(it, 14f * density)) }
        show(page)
    }
    WorkspaceChip(Icons.Outlined.SportsEsports, stringResource(R.string.workspace_steam), "steam") { open(it, "steam") }
    if (s.gameStoresEnabled) WorkspaceChip(Icons.Outlined.LocalMall, stringResource(R.string.workspace_stores), "stores") { open(it, "stores") }
    if (s.isHomeApp) WorkspaceChip(Icons.Outlined.Apps, stringResource(R.string.workspace_android_apps), "android-apps") { open(it, "android-apps") }
}

@Composable
private fun WorkspaceChip(icon: ImageVector, label: String, id: String, onClick: (Rect?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else if (hot) 1.04f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "workspaceChip")
    val shape = RoundedCornerShape(14.dp)
    var at by remember { mutableStateOf<Rect?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(56.dp).testTag("workspace-$id").paneItem("btn:workspace-$id").onGloballyPositioned { at = it.boundsInRoot() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(if (hot) pal.signal.copy(alpha = 0.18f) else colors.surface.copy(alpha = 0.92f))
            .glideBorder(hot, shape, pal.signal, pal.line2)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button) { onClick(at) }
            .controllerConfirm { onClick(at) }
            .padding(horizontal = 16.dp),
    ) {
        Icon(icon, null, tint = pal.signal, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
    }
}
