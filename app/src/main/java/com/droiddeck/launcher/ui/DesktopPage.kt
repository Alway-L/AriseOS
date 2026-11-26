package com.droiddeck.launcher.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Download
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.FileUtils
import com.droiddeck.launcher.frontend.Library
import com.droiddeck.launcher.runtime.UserApps
import com.droiddeck.launcher.session.SessionService
import com.droiddeck.launcher.store.UserAppsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private val DESKTOP_TABS = modeSheetTabs(steam = false) + SheetTab.FILES

@Composable
internal fun DesktopPage(s: FrontEndState, a: FrontEndActions, modifier: Modifier, onSelect: (String) -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val narrow = LocalNarrowPane.current
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { UserAppsState.refresh(ctx) }
    LaunchedEffect(s.ready) { com.droiddeck.launcher.store.StoreState.refresh(ctx) }
    val installed = s.emulators.filter { it.installed }
    val apps = UserAppsState.items
    var adding by rememberSaveable { mutableStateOf(false) }
    val mode = SessionService.MODE_DESKTOP
    val tabs = DESKTOP_TABS.map { it to sheetTabLabel(it) }
    val open = if (s.desktopInstalled) stringResource(R.string.content_open_desktop) else stringResource(R.string.content_install_open_desktop)
    val portrait = portraitScreen()
    val stageWidth = if (LocalConfiguration.current.screenWidthDp >= 1000) 480.dp else 340.dp
    fun openFrom(at: Rect?, art: Any?, page: String) {
        at?.let { PageOrigin.mark(Origin(it, 16f * density.density, art = art)) }
        onSelect(page)
    }

    LiftHero(
        key = "desktop", tabs = tabs,
        onOpen = a.onDesktopSettings, onPrimary = a.onDesktop,
        modifier = modifier.clipToBounds(),
        start = if (narrow) 20.dp else 44.dp, bottom = 44.dp,
        background = {
            if (prismaLook()) PrismaWallpaper(Modifier.fillMaxSize())
            else AriseWallpaper(Modifier.fillMaxSize(), markX = if (narrow) 0.5f else 0.72f, markY = if (narrow) 0.34f else 0.44f)
        },
        scrim = { lifted ->
            WallFade()
            BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { val v = lifted(); alpha = 1f - v; translationY = -12.dp.toPx() * v }) {
                val width = maxWidth
                if (portrait) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = (maxHeight - 236.dp).coerceAtLeast(160.dp))
                            .verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 12.dp),
                    ) {
                        DashSummary(s, a, onSelect, Modifier.padding(horizontal = 20.dp), big = true)
                        DesktopDock(s, a, installed, apps, openFrom = ::openFrom, portrait = true, width = width)
                    }
                } else {
                    DesktopStage(Modifier.padding(start = 20.dp, top = 14.dp, bottom = 10.dp).width(stageWidth).fillMaxHeight())
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().padding(start = stageWidth + 36.dp)
                            .verticalScroll(rememberScrollState()).padding(top = 14.dp, end = 20.dp, bottom = 14.dp),
                    ) {
                        DashSummary(s, a, onSelect, big = false)
                        DesktopDock(s, a, installed, apps, openFrom = ::openFrom, portrait = false, width = width - stageWidth - 56.dp, startPad = 0.dp)
                    }
                }
            }
        },
        words = {
            val pal = LocalPalette.current
            val label = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(pal.primary, pal.signal)), androidx.compose.foundation.shape.CircleShape))
                    Text(stringResource(R.string.workspace_desktop).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp, color = colors.onSurfaceVariant)
                }
            }
            val status = @Composable {
                Text(
                    stringResource(if (s.desktopInstalled) R.string.arise_desktop_ready else R.string.arise_desktop_missing),
                    fontSize = 13.5.sp, color = if (s.desktopInstalled) pal.signal else colors.onSurfaceVariant,
                )
            }
            if (portrait) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DesktopStage(Modifier.size(width = 92.dp, height = 62.dp), small = true)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        label()
                        Text(stringResource(R.string.rail_workspace), fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, color = colors.onBackground)
                        status()
                    }
                }
                BusyChip(s)
            } else Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = stageWidth - 40.dp)) {
                label()
                Text(stringResource(R.string.rail_workspace), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, color = colors.onBackground)
                status()
                BusyChip(s)
            }
        },
        button = { click ->
            PrimaryButton(if (portrait || !s.desktopInstalled) open else stringResource(R.string.store_open), enabled = !s.busy, main = true, large = true, icon = Icons.Outlined.DesktopWindows, onClick = click)
        },
        beside = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                WorkspaceShortcuts(s)
                HeroIconButton(Icons.Outlined.Storefront, stringResource(R.string.flathub_name), FlathubTeal, "desktop:flathub") { at -> openFrom(at, null, "store") }
                HeroIconButton(Icons.Filled.Add, stringResource(R.string.user_apps_add), null, "desktop:add") { if (!s.busy) adding = true }
                if (s.desktopPresent && (portrait || stageWidth > 400.dp)) UninstallDesktop(enabled = !s.runtimeActionsBlocked, onUninstall = a.onRemoveDesktop)
            }
        },
        rows = { tab ->
            if (tab == SheetTab.FILES) DesktopFiles(s, a)
            else a.modeSheet(mode, tab, SheetExtras())
        },
    )
    if (adding && !s.busy) AddAppDialog(s.ready, onDismiss = { adding = false }) { request, label -> UserAppsState.add(ctx, request, label) }
}


/** A drawn preview of the Linux desktop: two windows over the wallpaper and a taskbar. */
@Composable
private fun DesktopStage(modifier: Modifier, small: Boolean = false) {
    val pal = LocalPalette.current
    val corner = if (small) 12.dp else 22.dp
    val drift = androidx.compose.animation.core.rememberInfiniteTransition(label = "stage")
    val t by drift.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(9000), androidx.compose.animation.core.RepeatMode.Reverse), label = "stageDrift")
    Box(
        modifier.clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(pal.primary.copy(alpha = 0.42f), pal.primary2.copy(alpha = 0.34f))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(corner)),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r = androidx.compose.ui.geometry.CornerRadius(if (small) 3.dp.toPx() else 7.dp.toPx())
            val fill = Color(0x8C080A18)
            val edge = Color.White.copy(alpha = 0.16f)
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
            fun win(x: Float, y: Float, ww: Float, wh: Float) {
                drawRoundRect(fill, Offset(x, y), androidx.compose.ui.geometry.Size(ww, wh), r)
                drawRoundRect(edge, Offset(x, y), androidx.compose.ui.geometry.Size(ww, wh), r, style = stroke)
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(x + wh * 0.06f, y + wh * 0.07f), androidx.compose.ui.geometry.Size(ww * 0.18f, wh * 0.05f), r)
            }
            val bar = if (small) 6.dp.toPx() else 16.dp.toPx()
            win(w * (0.08f + 0.02f * t), h * 0.10f, w * 0.48f, (h - bar) * 0.48f)
            win(w * (0.42f - 0.02f * t), h * 0.24f, w * 0.48f, (h - bar) * 0.48f)
            drawRect(Color(0xB3080A18), Offset(0f, h - bar), androidx.compose.ui.geometry.Size(w, bar))
        }
        if (!small) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC060814))))
    }
}

@Composable
private fun compactDesktop() = LocalNarrowPane.current || LocalConfiguration.current.screenHeightDp < 600

private enum class DockState { READY, GET, WORKING }

private class DockItem(
    val key: String, val name: String, val detail: String, val art: Any?,
    val fallback: androidx.compose.ui.graphics.vector.ImageVector, val state: DockState,
    val progress: Int? = null, val streaming: Boolean = false, val bleed: Boolean = false,
    val onClick: (Rect?) -> Unit,
)

private val STREAMING_APPS = setOf("moonlight", "chiaki")

@Composable
private fun DesktopDock(
    s: FrontEndState, a: FrontEndActions, installed: List<Library.Emulator>, apps: List<UserApps.App>,
    openFrom: (Rect?, Any?, String) -> Unit, portrait: Boolean, width: androidx.compose.ui.unit.Dp,
    startPad: androidx.compose.ui.unit.Dp? = null,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val narrow = LocalNarrowPane.current
    val available = s.emulators.filter { !it.installed }
    val flatpaks = com.droiddeck.launcher.store.StoreState.installed.filter { f -> apps.none { it.key == "flatpak:${f.id}" } }
    var dockAt by remember { mutableStateOf<Rect?>(null) }
    val dockContext = LocalContext.current
    val extras = com.droiddeck.launcher.runtime.ExtraEmulators.all.filter { com.droiddeck.launcher.runtime.ExtraEmulators.installed(apps, it) == null }
    val ae = com.droiddeck.launcher.runtime.AndroidEmulators
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ae.refresh++
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val installedAndroid = remember(ae.refresh, ae.working) { ae.all.associate { it.id to ae.installedPackage(dockContext, it) } }
    val items = buildList {
        installed.forEach { e ->
            val k = "emu:${e.id}"
            val system = if (e.systemRes != 0) stringResource(e.systemRes) else e.system.replaceFirstChar { c -> c.uppercase() }
            add(DockItem(k, e.name, if (e.id == "retroarch") system else "$system · " + pluralStringResource(R.plurals.mode_added_count, e.games.size, e.games.size),
                e.iconRes, Icons.Outlined.Apps, DockState.READY) { at -> openFrom(at, e.iconRes, k) })
        }
        flatpaks.forEach { f ->
            add(DockItem("flatpak:${f.id}", f.name, f.summary ?: stringResource(R.string.flathub_name),
                f.icon ?: com.droiddeck.launcher.store.FlathubApi.iconUrl(f.id), Icons.Outlined.Apps, DockState.READY) { at ->
                com.droiddeck.launcher.store.StoreState.openRequest = f.id
                openFrom(at, f.icon, "store")
            })
        }
        apps.forEach { app ->
            val k = "user:${app.key}"
            add(DockItem(k, app.name, stringResource(app.kind.label()), app.icon,
                if (app.kind == UserApps.Kind.SCRIPT) Icons.Outlined.Terminal else Icons.Outlined.Apps, DockState.READY) { at -> openFrom(at, app.icon, k) })
        }
        ae.all.forEach { x ->
            val pkg = installedAndroid[x.id]
            val busyHere = ae.working == x.id
            add(DockItem("android:${x.id}", x.name,
                if (busyHere) (if (ae.percent >= 0) "${ae.percent}%" else stringResource(R.string.store_installing)) else x.system,
                x.iconRes, Icons.Outlined.Apps, if (busyHere) DockState.WORKING else if (pkg != null) DockState.READY else DockState.GET,
                progress = if (busyHere) ae.percent else null, streaming = x.id in STREAMING_APPS, bleed = true) { _ ->
                if (pkg != null) ae.launch(dockContext, x) else if (ae.working == null) ae.install(dockContext, x)
            })
        }
        available.forEach { e ->
            val pkg = Library.packageId(e.id)?.let { id -> s.packages?.firstOrNull { it.id == id } }
            val busyHere = pkg != null && s.packageBusyId == pkg.id
            val canGet = pkg != null && !s.busy && s.packageBusyId == null && s.ready && !s.packageCatalogLoading && !s.sessionRunning
            val system = if (e.systemRes != 0) stringResource(e.systemRes) else e.system.replaceFirstChar { c -> c.uppercase() }
            add(DockItem("emu:${e.id}", e.name, if (busyHere) (s.packageStage ?: stringResource(R.string.store_installing)) else system,
                e.iconRes, Icons.Outlined.Apps, if (busyHere) DockState.WORKING else DockState.GET,
                progress = if (busyHere) s.packagePercent else null) { at ->
                if (canGet && pkg != null && at != null) {
                    a.onInstallPackage(pkg.id)
                    val row = dockAt
                    Flights.fly(Flight(at, to = { val r = row ?: return@Flight null; Offset(r.center.x, r.top) }))
                } else openFrom(at, e.iconRes, "emu:${e.id}")
            })
        }
        extras.forEach { x ->
            val busyHere = UserAppsState.working == x.name
            add(DockItem("extra:${x.id}", x.name, if (busyHere) (UserAppsState.stage ?: stringResource(R.string.store_installing)) else x.system,
                x.iconRes, Icons.Outlined.Apps, if (busyHere) DockState.WORKING else DockState.GET,
                progress = if (busyHere) UserAppsState.percent else null) { _ ->
                if (UserAppsState.working == null && s.ready && !s.busy) {
                    UserAppsState.add(dockContext, com.droiddeck.launcher.runtime.ExtraEmulators.request(dockContext, x), x.name)
                }
            })
        }
    }
    if (items.isEmpty() && UserAppsState.working == null && UserAppsState.lastError == null) return
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val filters = listOf(
        stringResource(R.string.dock_filter_all) to items,
        stringResource(R.string.dock_filter_installed) to items.filter { it.state == DockState.READY },
        stringResource(R.string.dock_filter_get) to items.filter { it.state != DockState.READY },
        stringResource(R.string.dock_filter_streaming) to items.filter { it.streaming },
    )
    val shown = filters[filter.coerceIn(0, filters.lastIndex)].second
    val pad = startPad ?: if (narrow) 20.dp else 44.dp
    Box(Modifier.fillMaxWidth().padding(top = if (portrait || startPad != null) 6.dp else if (compactDesktop()) 14.dp else 22.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.user_apps_section).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(start = pad, bottom = 8.dp),
            )
            val chipScroll = rememberScrollState()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().edgeFade(chipScroll).horizontalScroll(chipScroll).padding(start = pad, end = 20.dp, bottom = 10.dp),
            ) {
                filters.forEachIndexed { i, (label, list) ->
                    if (i == 3 && list.isEmpty()) return@forEachIndexed
                    DockFilterChip("$label · ${list.size}", selected = filter == i, id = "dock-filter-$i") { filter = i }
                }
            }
            if (portrait) {
                val cols = ((width - pad * 2) / 88.dp).toInt().coerceIn(3, 6)
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { dockAt = it.boundsInRoot() }
                        .padding(start = pad, end = pad, top = 4.dp, bottom = 8.dp),
                ) {
                    shown.chunked(cols).forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEachIndexed { c, item ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                    key(item.key) { DockIcon(item, first = r == 0 && c == 0) }
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                val scroll = rememberScrollState()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { dockAt = it.boundsInRoot() }
                        .edgeFade(scroll).horizontalScroll(scroll).padding(start = (pad - 8.dp).coerceAtLeast(0.dp), end = 20.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    shown.forEachIndexed { i, item -> key(item.key) { Box(Modifier.width(92.dp), contentAlignment = Alignment.TopCenter) { DockIcon(item, first = i == 0) } } }
                }
            }
            if (UserAppsState.working != null || UserAppsState.lastError != null) {
                Box(Modifier.padding(start = pad, end = 20.dp, top = 6.dp)) { UserAppsProgressLine() }
            }
            ae.error?.let { msg ->
                Text(msg, fontSize = 12.5.sp, color = pal.error, modifier = Modifier.padding(start = pad, end = 20.dp, top = 6.dp))
            }
        }
    }
}

@Composable
private fun DockFilterChip(label: String, selected: Boolean, id: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val shape = RoundedCornerShape(99.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.paneItem("chip:$id").heightIn(min = 36.dp).clip(shape)
            .background(if (selected) colors.onBackground else colors.surface.copy(alpha = 0.85f))
            .glideBorder(hot, shape, pal.signal, if (selected) Color.Transparent else pal.line2)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
            .controllerConfirm(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.background else colors.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun DockIcon(item: DockItem, first: Boolean) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else if (hot) 1.06f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "dockIcon")
    val size = 62.dp
    val shape = RoundedCornerShape(19.dp)
    var at by remember { mutableStateOf<Rect?>(null) }
    val dim = item.state == DockState.GET && !hot
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp).paneItem("tile:desktop:${item.key}").then(if (first) Modifier.firstTile() else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button) { item.onClick(at) }
            .controllerConfirm { item.onClick(at) }
            .semantics { contentDescription = item.name }
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (dim) 0.62f else 1f }) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(size).onGloballyPositioned { at = it.boundsInRoot() }
                    .then(if (hot) Modifier.border(2.dp, pal.signal, RoundedCornerShape(22.dp)).padding(3.dp) else Modifier)
                    .clip(shape).background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.surface))),
            ) {
                when (val art = item.art) {
                    is Int -> Image(
                        painterResource(art), null, contentScale = if (item.bleed) ContentScale.Crop else ContentScale.Fit,
                        modifier = if (item.bleed) Modifier.fillMaxSize() else Modifier.size(size * 0.66f),
                    )
                    null -> Icon(item.fallback, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(size * 0.42f))
                    else -> AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.66f))
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(22.dp)
                    .background(colors.background, androidx.compose.foundation.shape.CircleShape),
            ) {
                when (item.state) {
                    DockState.READY -> Box(Modifier.size(9.dp).background(Color(0xFF34D399), androidx.compose.foundation.shape.CircleShape))
                    DockState.GET -> Icon(Icons.Outlined.Download, null, tint = pal.signal, modifier = Modifier.size(13.dp))
                    DockState.WORKING -> {
                        val p = item.progress
                        if (p != null && p >= 0) CircularProgressIndicator(progress = { p / 100f }, strokeWidth = 2.dp, color = pal.signal, trackColor = pal.signal.copy(alpha = 0.25f), modifier = Modifier.size(16.dp))
                        else CircularProgressIndicator(strokeWidth = 2.dp, color = pal.signal, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Text(item.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(item.detail, fontSize = 11.sp, lineHeight = 13.sp, color = colors.onSurfaceVariant.copy(alpha = if (dim) 0.7f else 1f), maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun HeroIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color?, id: String, onClick: (Rect?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else if (hot) 1.04f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "heroIcon")
    val shape = RoundedCornerShape(12.dp)
    val accent = tint ?: pal.signal
    var at by remember { mutableStateOf<Rect?>(null) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(56.dp).paneItem("btn:$id").onGloballyPositioned { at = it.boundsInRoot() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(if (hot) accent.copy(alpha = 0.18f) else colors.surface.copy(alpha = 0.92f))
            .glideBorder(hot, shape, accent, pal.line2)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button) { onClick(at) }
            .controllerConfirm { onClick(at) }
            .semantics { contentDescription = description },
    ) { Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun DesktopWall(icons: List<Any>) {
    val pal = LocalPalette.current
    val ground = remember(pal) { Brush.linearGradient(listOf(pal.surfaceVariant, pal.surface)) }
    TiltedWall(icons.size, if (icons.isEmpty()) 75_000 else 50_000, capW = 132.dp, capH = 84.dp, gap = 14.dp, corner = 8.dp) { i ->
        WallCapsule(ground, edge = pal.line2, icon = icons.getOrNull(i), bar = pal.background.copy(alpha = 0.7f))
    }
}

@Composable
private fun DesktopFiles(s: FrontEndState, a: FrontEndActions) {
    val ctx = LocalContext.current
    SettingsGroup(stringResource(R.string.setup_tool_files)) {
        SettingsRow(stringResource(R.string.setup_tool_files), stringResource(R.string.setup_tool_files_hint)) {
            SecondaryButton(stringResource(R.string.store_open), compact = true, onClick = a.onFiles)
        }
        SettingsRow(stringResource(R.string.setup_tool_roms), s.romsDir ?: stringResource(R.string.setup_tool_roms_hint)) {
            SecondaryButton(stringResource(R.string.setup_choose), compact = true, onClick = a.onRoms)
        }
    }
    if (s.desktopPresent) SettingsGroup(stringResource(R.string.content_linux_desktop)) {
        val size by produceState<Long?>(null) { value = withContext(Dispatchers.IO) { runCatching { com.droiddeck.launcher.runtime.DesktopCatalog.desktopSize(ctx) }.getOrNull() } }
        SettingsRow(stringResource(R.string.desktop_size), size?.let { FileUtils.sizeToString(it) } ?: "…") {}
        SettingsRow(stringResource(R.string.desktop_remove_title), stringResource(R.string.desktop_remove_text)) {
            UninstallDesktop(enabled = !s.runtimeActionsBlocked, onUninstall = a.onRemoveDesktop)
        }
    }
}

@Composable
private fun UninstallDesktop(enabled: Boolean, onUninstall: () -> Unit) {
    val canUninstall by rememberUpdatedState(enabled)
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.common_uninstall)
    val title = stringResource(R.string.desktop_uninstall_title)
    val note = stringResource(R.string.desktop_remove_text)
    val keep = stringResource(R.string.flathub_keep)
    var at by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.onGloballyPositioned { at = it.boundsInRoot() }) {
        SecondaryButton(label, enabled = enabled, compact = true) {
            val from = at ?: return@SecondaryButton
            Steps.ask(StepAsk(
                anchor = from, side = StepSide.Left, accent = colors.error, pillCorner = 12.dp, items = 3,
                handle = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.error, maxLines = 1) },
            ) {
                StepTitle(title)
                Text(note, fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.stepItem(1).padding(start = 4.dp, bottom = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth().stepItem(2)) {
                    StepChoice(keep, enabled = open, modifier = Modifier.focusRequester(first)) { Steps.fold() }
                    StepChoice(label, danger = true, enabled = open && canUninstall) { Steps.fold(); onUninstall() }
                }
            })
        }
    }
}

@Composable
private fun UserAppsProgressLine() {
    val colors = MaterialTheme.colorScheme
    val working = UserAppsState.working
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (working != null) {
            val stage = UserAppsState.stage ?: stringResource(R.string.user_apps_starting)
            val percent = UserAppsState.percent
            Text(
                if (percent >= 0) stringResource(R.string.user_apps_progress_percent, working, stage, percent) else stringResource(R.string.user_apps_progress, working, stage),
                fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (percent >= 0) androidx.compose.material3.LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp))
            else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
        } else UserAppsState.lastError?.let { Note(it) }
    }
}
