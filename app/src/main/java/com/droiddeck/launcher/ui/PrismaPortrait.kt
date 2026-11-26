package com.droiddeck.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.StatFs
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun portraitScreen(): Boolean = LocalConfiguration.current.let { it.screenHeightDp > it.screenWidthDp }

private val Card = RoundedCornerShape(18.dp)

@Composable
private fun greetingShort(): String {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.greet_short_morning
            in 12..17 -> R.string.greet_short_afternoon
            else -> R.string.greet_short_night
        },
    )
}

@Composable
internal fun PrismaHeader(s: FrontEndState, a: FrontEndActions) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    remember { UserName.load(ctx) }
    val name = UserName.name
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(pal.primary, pal.primary2)))
                .border(2.dp, pal.signal.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (name.isNotBlank()) Text(name.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            else PrismaMark(22.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(greetingShort(), fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
            Text(
                name.ifBlank { stringResource(R.string.app_name) }, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (s.running != null) Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(pal.good.copy(alpha = 0.16f))
                .clickable(onClick = a.onResume).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = pal.good, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.dash_resume), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.good)
        }
    }
}

@Composable
internal fun PrismaLineBar(
    s: FrontEndState, selected: String,
    onSelect: (String) -> Unit, onFocusSelect: (String) -> Unit, modifier: Modifier,
) {
    val pal = LocalPalette.current
    val shown = Hops.railPreview ?: selected
    val needsAttention = setupNeedsAttention(s)
    Row(
        modifier.fillMaxWidth().background(pal.background.copy(alpha = 0.94f)).selectableGroup().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (key in navigationKeys(s)) {
            val (label, icon) = if (key == WORKSPACE_TAB) R.string.dash_home to Icons.Outlined.Home else railLabelIcon(key)
            key(key) {
                LineBarItem(
                    stringResource(label), icon, key, shown == key,
                    badge = (key == "setup" && needsAttention) || (key == "updates" && s.updates.hasUpdate),
                    onFocus = { onFocusSelect(key) }, modifier = Modifier.weight(1f),
                ) { onSelect(key) }
            }
        }
    }
}

@Composable
private fun LineBarItem(
    label: String, icon: ImageVector, key: String, current: Boolean, badge: Boolean,
    onFocus: () -> Unit, modifier: Modifier, onClick: () -> Unit,
) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    val frontFocus = LocalFrontFocus.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    if (frontFocus != null) LaunchedEffect(focused) {
        if (focused) {
            frontFocus.focusedRail = key
            onFocus()
        } else if (frontFocus.focusedRail == key) frontFocus.focusedRail = null
    }
    val fg by animateColorAsState(if (current || focused || hovered) colors.onBackground else colors.onSurfaceVariant, Motion.tw(220), label = "lineFg")
    val line by animateFloatAsState(if (current) 1f else if (focused) 0.5f else 0f, Motion.sp(0.7f), label = "lineGlow")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (frontFocus != null) Modifier.focusRequester(frontFocus.railFor(key)) else Modifier)
            .testTag("rail-$key")
            .onGloballyPositioned { Hops.railBounds[key] = it.boundsInRoot() }
            .height(66.dp)
            .hoverable(src)
            .selectable(selected = current, interactionSource = src, indication = LocalIndication.current, role = Role.Tab, onClick = onClick),
    ) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth(0.42f * line).height(3.dp)
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(Brush.horizontalGradient(listOf(pal.primary, pal.primary2))),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                Icon(icon, contentDescription = null, tint = if (current) pal.signal else fg, modifier = Modifier.size(22.dp))
                if (badge) Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(AttentionAmber))
            }
            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private class DeviceVitals(val tempC: Float?, val batteryPct: Int?, val freeBytes: Long, val totalBytes: Long)

private fun readVitals(context: Context): DeviceVitals {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    val stat = runCatching { StatFs(context.filesDir.path) }.getOrNull()
    return DeviceVitals(
        tempC = if (temp == Int.MIN_VALUE) null else temp / 10f,
        batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else null,
        freeBytes = stat?.availableBytes ?: 0L, totalBytes = stat?.totalBytes ?: 0L,
    )
}

/** Continue card, device vitals and play time at the top of the desktop screen in portrait. */
@Composable
internal fun DashSummary(s: FrontEndState, a: FrontEndActions, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    var vitals by remember { mutableStateOf<DeviceVitals?>(null) }
    var recent by remember { mutableStateOf<com.droiddeck.launcher.session.GameStats.Game?>(null) }
    var today by remember { mutableStateOf(0L) }
    var yesterday by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            vitals = withContext(Dispatchers.IO) { readVitals(ctx.applicationContext) }
            recent = com.droiddeck.launcher.session.GameStats.recent(ctx)
            today = com.droiddeck.launcher.session.GameStats.todaySeconds(ctx)
            yesterday = com.droiddeck.launcher.session.GameStats.yesterdaySeconds(ctx)
            delay(30_000)
        }
    }
    val last = s.steamGames.filter { it.lastPlayed > 0L }.maxByOrNull { it.lastPlayed }
    val v = vitals
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        Rise(0) { ContinueStrip(s, a, last, onSelect) }
        Rise(1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniKpi(stringResource(R.string.dash_fps), recent?.lastAvgFps?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f))
                MiniKpi(
                    stringResource(R.string.dash_today), com.droiddeck.launcher.session.GameStats.format(today), Modifier.weight(1f),
                    tint = if (today > yesterday && today > 0) pal.good else null,
                )
                MiniKpi(
                    stringResource(R.string.dash_temperature), v?.tempC?.let { "%.0f°C".format(it) } ?: "—", Modifier.weight(1f),
                    progress = v?.tempC?.let { ((it - 20f) / 30f).coerceIn(0f, 1f) },
                    tint = v?.tempC?.let { if (it >= 43f) AttentionAmber else null },
                )
                MiniKpi(
                    stringResource(R.string.dash_storage), v?.let { Formatter.formatShortFileSize(ctx, it.freeBytes) } ?: "—", Modifier.weight(1f),
                    progress = v?.takeIf { it.totalBytes > 0 }?.let { 1f - it.freeBytes.toFloat() / it.totalBytes },
                )
            }
        }
        if (s.storeDownloadsActive > 0 || s.updates.hasUpdate) Rise(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.storeDownloadsActive > 0) AlertPill(Icons.Outlined.Download, stringResource(R.string.dash_downloads, s.storeDownloadsActive)) {
                    com.droiddeck.launcher.stores.StoresState.openDownloads = true
                    onSelect("stores")
                }
                if (s.updates.hasUpdate) AlertPill(Icons.Outlined.SystemUpdate, stringResource(R.string.dash_update)) { onSelect("updates") }
            }
        }
    }
}

internal const val DESKTOP_PAGE = "desktop-page"

@Composable
private fun ContinueStrip(s: FrontEndState, a: FrontEndActions, last: com.droiddeck.launcher.frontend.Library.SteamGame?, onSelect: (String) -> Unit) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(Card).background(pal.surface.copy(alpha = 0.82f)).border(1.dp, pal.line, Card).padding(10.dp),
    ) {
        Box(Modifier.size(width = 84.dp, height = 56.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(pal.primary, pal.primary2)))) {
            val art = last?.hero ?: last?.art
            if (art != null) coil.compose.AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (s.running != null) R.string.dash_running else R.string.dash_continue).uppercase(),
                fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = pal.signal,
            )
            Text(
                s.running ?: last?.name ?: stringResource(R.string.dash_no_recent), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val total = last?.let { com.droiddeck.launcher.session.GameStats.game(ctx, it.gameIdString)?.totalSeconds }?.takeIf { it > 0 }
            if (s.running == null && last != null) Text(
                DateUtils.getRelativeTimeSpanString(last.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() +
                    (total?.let { " · " + stringResource(R.string.dash_total, com.droiddeck.launcher.session.GameStats.format(it)) } ?: ""),
                fontSize = 11.5.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            s.running != null -> PrimaryButton(stringResource(R.string.dash_resume), compact = true, icon = Icons.Filled.PlayArrow, onClick = a.onResume)
            last != null -> PrimaryButton(stringResource(R.string.dash_play), compact = true, enabled = !s.busy, icon = Icons.Filled.PlayArrow) { a.onSteamGame(last) }
            else -> SecondaryButton(stringResource(R.string.dash_open_library), compact = true) { onSelect("games") }
        }
    }
}

@Composable
private fun MiniKpi(label: String, value: String, modifier: Modifier, progress: Float? = null, tint: Color? = null) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(pal.surface.copy(alpha = 0.82f)).border(1.dp, pal.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 10.5.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tint ?: colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(if (progress != null) pal.line else Color.Transparent)) {
            if (progress != null) Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(pal.primary, tint ?: pal.primary2))))
        }
    }
}

@Composable
private fun AlertPill(icon: ImageVector, text: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(pal.signal.copy(alpha = 0.14f)).border(1.dp, pal.signal.copy(alpha = 0.4f), RoundedCornerShape(99.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = pal.signal, modifier = Modifier.size(15.dp))
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1)
    }
}
