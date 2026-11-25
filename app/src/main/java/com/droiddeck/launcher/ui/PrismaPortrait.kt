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

@Composable
internal fun PrismaDashboard(s: FrontEndState, a: FrontEndActions, modifier: Modifier, onSelect: (String) -> Unit) {
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
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
    ) {
        Rise(0) { ContinueCard(s, a, last, onSelect) }
        val v = vitals
        Rise(1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Kpi(
                        stringResource(R.string.dash_fps), recent?.lastAvgFps?.let { "%.0f".format(it) } ?: "—", null,
                        detail = recent?.lastAvgFps?.let { stringResource(R.string.dash_fps_hint) } ?: stringResource(R.string.dash_fps_none),
                        modifier = Modifier.weight(1f),
                    )
                    Kpi(
                        stringResource(R.string.dash_today), com.droiddeck.launcher.session.GameStats.format(today), null,
                        detail = (today - yesterday).let { d ->
                            if (yesterday == 0L && today == 0L) stringResource(R.string.dash_today_none)
                            else (if (d >= 0) "+" else "−") + com.droiddeck.launcher.session.GameStats.format(kotlin.math.abs(d)) + " " + stringResource(R.string.dash_vs_yesterday)
                        },
                        tint = if (today > yesterday && today > 0) pal.good else null, modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Kpi(
                        stringResource(R.string.dash_temperature), v?.tempC?.let { "%.0f°".format(it) } ?: "—", "C",
                        progress = v?.tempC?.let { ((it - 20f) / 30f).coerceIn(0f, 1f) },
                        tint = v?.tempC?.let { if (it >= 43f) AttentionAmber else null }, modifier = Modifier.weight(1f),
                    )
                    val free = v?.let { Formatter.formatShortFileSize(ctx, it.freeBytes) }
                    val total = v?.let { Formatter.formatShortFileSize(ctx, it.totalBytes) }
                    Kpi(
                        stringResource(R.string.dash_storage), free ?: "—", null,
                        detail = total?.let { stringResource(R.string.dash_of_total, it) }, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Rise(2) {
            Column(Modifier.fillMaxWidth().clip(Card).background(pal.surface.copy(alpha = 0.82f)).border(1.dp, pal.line, Card)) {
                DashRow(Icons.Outlined.SportsEsports, stringResource(R.string.rail_steam), stringResource(R.string.dash_steam_hint), stringResource(R.string.dash_open), divider = true, onClick = a.onPlay)
                DashRow(Icons.Outlined.DesktopWindows, stringResource(R.string.rail_workspace), stringResource(R.string.dash_desktop_hint), stringResource(R.string.dash_more), divider = s.storeDownloadsActive > 0 || s.updates.hasUpdate) { onSelect(DESKTOP_PAGE) }
                if (s.storeDownloadsActive > 0) DashRow(
                    Icons.Outlined.Download, stringResource(R.string.dash_downloads, s.storeDownloadsActive), null, stringResource(R.string.dash_open),
                    divider = s.updates.hasUpdate,
                ) {
                    com.droiddeck.launcher.stores.StoresState.openDownloads = true
                    onSelect("stores")
                }
                if (s.updates.hasUpdate) DashRow(Icons.Outlined.SystemUpdate, stringResource(R.string.dash_update), stringResource(R.string.dash_update_hint), stringResource(R.string.dash_open), divider = false) { onSelect("updates") }
            }
        }
    }
}

internal const val DESKTOP_PAGE = "desktop-page"

@Composable
private fun ContinueCard(s: FrontEndState, a: FrontEndActions, last: com.droiddeck.launcher.frontend.Library.SteamGame?, onSelect: (String) -> Unit) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(Card).background(pal.surface.copy(alpha = 0.82f)).border(1.dp, pal.line, Card)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.3f).background(Brush.linearGradient(listOf(pal.primary, pal.primary2)))) {
            val art = last?.hero ?: last?.art
            if (art != null) coil.compose.AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6050610)))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (s.running != null) R.string.dash_running else R.string.dash_continue).uppercase(),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = pal.signal,
                )
                Text(
                    s.running ?: last?.name ?: stringResource(R.string.dash_no_recent), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                when {
                    s.running != null -> ""
                    last != null -> stringResource(R.string.dash_played, DateUtils.getRelativeTimeSpanString(last.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()) +
                        (com.droiddeck.launcher.session.GameStats.game(LocalContext.current, last.gameIdString)?.totalSeconds?.takeIf { it > 0 }
                            ?.let { " · " + stringResource(R.string.dash_total, com.droiddeck.launcher.session.GameStats.format(it)) } ?: "")
                    else -> stringResource(R.string.dash_no_recent_hint)
                },
                fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            when {
                s.running != null -> PrimaryButton(stringResource(R.string.dash_resume), compact = true, icon = Icons.Filled.PlayArrow, onClick = a.onResume)
                last != null -> PrimaryButton(stringResource(R.string.dash_play), compact = true, enabled = !s.busy, icon = Icons.Filled.PlayArrow) { a.onSteamGame(last) }
                else -> SecondaryButton(stringResource(R.string.dash_open_library), compact = true) { onSelect("games") }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, unit: String?, modifier: Modifier, progress: Float? = null, detail: String? = null, tint: Color? = null) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clip(Card).background(pal.surface.copy(alpha = 0.82f)).border(1.dp, pal.line, Card).padding(14.dp),
    ) {
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tint ?: colors.onBackground, maxLines = 1)
            if (unit != null) Text(" $unit", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (progress != null) Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(pal.line)) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(pal.primary, tint ?: pal.primary2))))
        }
        if (detail != null) Text(detail, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun DashRow(icon: ImageVector, title: String, hint: String?, action: String, divider: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(pal.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = pal.signal, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.signal)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
    if (divider) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(1.dp).background(pal.line))
}
