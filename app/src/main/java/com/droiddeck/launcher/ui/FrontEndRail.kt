package com.droiddeck.launcher.ui

import androidx.compose.ui.platform.testTag
import com.droiddeck.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.droiddeck.launcher.session.SessionPrefs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import com.droiddeck.launcher.core.PhantomProcessLimit


internal val AttentionAmber = Color(0xFFFFB547)

@Composable
internal fun BottomBar(
    s: FrontEndState, selected: String,
    onSelect: (String) -> Unit, onFocusSelect: (String) -> Unit, a: FrontEndActions, modifier: Modifier,
) {
    val shown = Hops.railPreview ?: selected
    val keys = navigationKeys(s)
    val bounds = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val tint = androidx.compose.runtime.rememberUpdatedState(LocalPalette.current.primary)
    val content = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val pill = RoundedCornerShape(24.dp)
    val line = LocalPalette.current.line
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp, vertical = 4.dp)
            .graphicsLayer { shadowElevation = 16.dp.toPx(); shape = pill; clip = false }
            .clip(pill).background(MaterialTheme.colorScheme.surface).border(1.dp, line, pill).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val itemWidth = (maxWidth / keys.size).coerceIn(56.dp, 88.dp)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).onPlaced { content[0] = it }) {
                if (shown in keys) ChipGlide(keys.indexOf(shown), bounds[shown], tint, GlideAxis.Horizontal, corner = 18.dp, fill = 1f, outline = 0f)
                Row(Modifier.selectableGroup().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                    NavigationItems(s, shown, compact = true, iconOnly = false, height = 56.dp, width = itemWidth,
                        place = { key -> Modifier.onGloballyPositioned { item -> content[0]?.let { bounds[key] = it.localBoundingBoxOf(item) }; Hops.railBounds[key] = item.boundsInRoot() } },
                        onSelect = onSelect, onFocusSelect = onFocusSelect)
                }
            }
        }
        if (s.running != null) ResumeRailItem(s.running, compact = true, iconOnly = true, a.onResume)
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun navigationKeys(s: FrontEndState) = listOf(WORKSPACE_TAB, "games", "components", "setup", "updates")

internal object RailLabels {
    var shown by mutableStateOf(true)
        private set

    fun load(context: android.content.Context) {
        shown = SessionPrefs.railLabels(context)
    }

    fun set(context: android.content.Context, show: Boolean) {
        shown = show
        SessionPrefs.setRailLabels(context, show)
    }
}

internal object UserName {
    var name by mutableStateOf("")
        private set

    fun load(context: android.content.Context) {
        name = SessionPrefs.userName(context)
    }

    fun set(context: android.content.Context, value: String) {
        SessionPrefs.setUserName(context, value)
        name = SessionPrefs.userName(context)
    }
}

@Composable
internal fun greeting(name: String): String {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    return stringResource(
        when {
            hour in 5..11 -> R.string.greet_morning
            hour in 12..17 -> R.string.greet_afternoon
            else -> R.string.greet_night
        },
        name,
    )
}

private val prismaA = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M64 22 L103 104 H84 L64 60 L44 104 H25 Z")
private val prismaNotch = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M64 76 L75 98 H53 Z")

@Composable
internal fun PrismaMark(size: Dp, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 128f
        drawScale(k, k, androidx.compose.ui.geometry.Offset.Zero) {
            val corner = androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(108f, 0f); lineTo(128f, 20f); lineTo(128f, 128f); lineTo(20f, 128f); lineTo(0f, 108f); close() }
            drawPath(corner, Color(0xFF0E1124))
            val brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFFA855F7)), androidx.compose.ui.geometry.Offset(0f, 128f), androidx.compose.ui.geometry.Offset(128f, 0f))
            drawPath(prismaA.toPath(), brush)
            drawPath(prismaNotch.toPath(), pal.onBackground)
            drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(108f, 0f); lineTo(128f, 20f); lineTo(108f, 20f); close() }, brush)
        }
    }
}

@Composable
internal fun TopTabs(
    s: FrontEndState, selected: String,
    onSelect: (String) -> Unit, onFocusSelect: (String) -> Unit, a: FrontEndActions, modifier: Modifier,
) {
    val pal = LocalPalette.current
    val colors = MaterialTheme.colorScheme
    val shown = Hops.railPreview ?: selected
    val needsAttention = setupNeedsAttention(s)
    val ctx = LocalContext.current
    remember { UserName.load(ctx) }
    Row(
        modifier.fillMaxWidth().height(52.dp).background(pal.background.copy(alpha = 0.78f)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrismaMark(26.dp)
        Row(Modifier.padding(start = 8.dp, end = 12.dp)) {
            Text("ARISE", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = colors.onBackground, maxLines = 1)
            Text("OS", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = pal.signal, maxLines = 1)
        }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            navigationKeys(s).forEachIndexed { index, key ->
                val (label, icon) = railLabelIcon(key)
                key(key) {
                    PrismaTab(
                        index + 1, stringResource(label), icon, key, shown == key,
                        badge = (key == "setup" && needsAttention) || (key == "updates" && s.updates.hasUpdate),
                        count = if (key == "games") s.storeDownloadsActive else 0,
                        onFocus = { onFocusSelect(key) },
                    ) { onSelect(key) }
                }
            }
        }
        val name = UserName.name
        if (name.isNotBlank() && !isNarrowScreen()) Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(pal.primary, pal.primary2))),
                contentAlignment = Alignment.Center,
            ) { Text(name.take(1).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
        }
        if (s.running != null) ResumeRailItem(s.running, compact = true, iconOnly = true, a.onResume)
    }
}

@Composable
private fun PrismaTab(
    number: Int, label: String, icon: ImageVector, key: String, current: Boolean,
    badge: Boolean, count: Int, onFocus: () -> Unit, onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
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
    val fg by animateColorAsState(
        if (current || focused || hovered) colors.onBackground else colors.onSurfaceVariant, Motion.tw(220), label = "tabFg",
    )
    val line by animateFloatAsState(if (current) 1f else if (focused) 0.4f else 0f, Motion.sp(0.7f), label = "tabLine")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(current) { if (current) bringIntoView.bringIntoView() }
    Box(
        modifier = Modifier
            .then(if (frontFocus != null) Modifier.focusRequester(frontFocus.railFor(key)) else Modifier)
            .testTag("rail-$key")
            .onGloballyPositioned { Hops.railBounds[key] = it.boundsInRoot() }
            .height(52.dp)
            .bringIntoViewRequester(bringIntoView)
            .hoverable(src)
            .selectable(selected = current, interactionSource = src, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("%02d".format(number), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = fg.copy(alpha = 0.7f))
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 13.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium, color = fg, maxLines = 1, softWrap = false)
            if (count > 0) Text(
                if (count > 9) "9+" else count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.onSignal,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(pal.signal).padding(horizontal = 5.dp, vertical = 1.dp),
            )
            if (badge) Box(Modifier.size(7.dp).clip(CircleShape).background(AttentionAmber))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(line).height(3.dp)
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(pal.primary, pal.primary2))),
        )
    }
}

@Composable
internal fun SideRail(
    s: FrontEndState, selected: String,
    onSelect: (String) -> Unit, onFocusSelect: (String) -> Unit, a: FrontEndActions, modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalConfiguration.current.screenHeightDp < 420
    val railContext = LocalContext.current
    remember { RailLabels.load(railContext) }
    val iconOnly = isNarrowScreen() || !RailLabels.shown
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(if (iconOnly) 64.dp else 92.dp).background(colors.surface).padding(vertical = if (compact) 8.dp else 12.dp),
    ) {
        val count = navigationKeys(s).size
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val fit = (maxHeight / count - 4.dp).coerceIn(44.dp, if (iconOnly) 48.dp else if (compact) 52.dp else 60.dp)
        val shown = Hops.railPreview ?: selected
        val keys = navigationKeys(s)
        val bounds = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
        val tint = androidx.compose.runtime.rememberUpdatedState(LocalPalette.current.primary)
        val content = remember { arrayOfNulls<LayoutCoordinates>(1) }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).onPlaced { content[0] = it }) {
        if (shown in keys) ChipGlide(keys.indexOf(shown), bounds[shown], tint, GlideAxis.Vertical, corner = 18.dp, fill = 1f, outline = 0f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            val place: (String) -> Modifier = { key ->
                Modifier.onGloballyPositioned { item ->
                    content[0]?.let { bounds[key] = it.localBoundingBoxOf(item) }
                    Hops.railBounds[key] = item.boundsInRoot()
                }
            }
            NavigationItems(s, shown, compact, iconOnly, fit, if (iconOnly) 52.dp else 80.dp, place, onSelect, onFocusSelect)
        }
        }
        }
        var lastRunning by remember { mutableStateOf("") }
        if (s.running != null) lastRunning = s.running
        AnimatedVisibility(
            s.running != null,
            enter = expandVertically(Motion.sp(0.75f)) + fadeIn(Motion.tw(300)),
            exit = shrinkVertically(Motion.tw(220)) + fadeOut(Motion.tw(180)),
        ) { ResumeRailItem(lastRunning, compact, iconOnly, a.onResume) }
    }
}

internal fun railLabelIcon(key: String): Pair<Int, ImageVector> = when (key) {
    "steam" -> R.string.rail_steam to Icons.Outlined.SportsEsports
    "games" -> R.string.rail_games to Icons.Outlined.VideoLibrary
    "stores" -> R.string.rail_stores to Icons.Outlined.LocalMall
    "desktop" -> R.string.rail_workspace to Icons.Outlined.DesktopWindows
    "components" -> R.string.rail_components to Icons.Outlined.Layers
    "android-apps" -> R.string.rail_apps to Icons.Outlined.Apps
    "setup" -> R.string.rail_setup to Icons.Outlined.Tune
    else -> R.string.rail_updates to Icons.Outlined.SystemUpdate
}

internal fun setupNeedsAttention(s: FrontEndState): Boolean = !s.ready || (s.available != null && s.available != s.installed) ||
    PhantomProcessLimit.blocksSteam(s.phantomProcessStatus)

@Composable
private fun NavigationItems(
    s: FrontEndState, shown: String, compact: Boolean, iconOnly: Boolean, height: Dp, width: Dp,
    place: (String) -> Modifier, onSelect: (String) -> Unit, onFocusSelect: (String) -> Unit,
) {
    val setupNeedsAttention = setupNeedsAttention(s)
    for (key in navigationKeys(s)) {
        val (label, icon) = railLabelIcon(key)
        key(key) {
            RailItem(stringResource(label), icon, key, shown == key, compact, iconOnly, height, width,
                badge = (key == "setup" && setupNeedsAttention) || (key == "updates" && s.updates.hasUpdate),
                count = if (key == "games") s.storeDownloadsActive else 0,
                place = place, onFocus = { onFocusSelect(key) }) { onSelect(key) }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun RailItem(
    label: String, icon: ImageVector, key: String, current: Boolean, compact: Boolean, iconOnly: Boolean, height: Dp, width: Dp,
    badge: Boolean = false,
    count: Int = 0,
    place: (String) -> Modifier = { Modifier },
    onFocus: () -> Unit = {}, onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val frontFocus = LocalFrontFocus.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    if (frontFocus != null) LaunchedEffect(focused) {
        if (focused) {
            frontFocus.focusedRail = key
            onFocus()
        }
        else if (frontFocus.focusedRail == key) frontFocus.focusedRail = null
    }
    val fg by animateColorAsState(
        if (current) Color.White else if (focused || hovered) colors.onBackground else colors.onSurfaceVariant,
        Motion.tw(220), label = "railFg",
    )
    val fill by animateColorAsState(
        if (current) Color.Transparent else if (focused || hovered) Color.White.copy(alpha = 0.05f) else Color.Transparent,
        Motion.tw(220), label = "railFill",
    )
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "railScale")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(current) { if (current) bringIntoView.bringIntoView() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(if (frontFocus != null) Modifier.focusRequester(frontFocus.railFor(key)) else Modifier)
            .testTag("rail-$key")
            .then(place(key))
            .landingPop(Hops.railLanded[key] ?: 0)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width = width, height = height)
            .bringIntoViewRequester(bringIntoView)
            .clip(Shape14)
            .background(fill)
            .glideBorder(focused, Shape14, pal.signal)
            .hoverable(src)
            .selectable(selected = current, interactionSource = src, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
            .then(if (iconOnly) Modifier.semantics { contentDescription = label } else Modifier),
    ) {
        if (iconOnly) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (height < 56.dp) 20.dp else 22.dp))
            FitText(label, maxSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.SemiBold, color = fg)
        }
        if (badge) Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = if (iconOnly) 7.dp else 8.dp, end = if (iconOnly) 9.dp else 18.dp)
                .size(8.dp).clip(CircleShape).background(AttentionAmber),
        )
        if (count > 0) Text(
            if (count > 9) "9+" else count.toString(),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.onSignal, maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = if (iconOnly) 4.dp else 6.dp, end = if (iconOnly) 5.dp else 12.dp)
                .landingPop().clip(RoundedCornerShape(8.dp)).background(pal.signal).padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ResumeRailItem(name: String, compact: Boolean, iconOnly: Boolean, onResume: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    val resumeDescription = stringResource(R.string.rail_resume_named, name)
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ringScale by pulse.animateFloat(0.4f, 1.6f, infiniteRepeatable(tween(1600, easing = Motion.Ease), RepeatMode.Restart), label = "ring")
    val still = Motion.scale == 0f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .padding(top = 6.dp)
            .width(if (iconOnly) 52.dp else 80.dp)
            .clip(Shape14)
            .background(pal.good.copy(alpha = if (focused || hovered) 0.20f else 0.12f))
            .glideBorder(focused, Shape14, pal.signal)
            .hoverable(src)
            .clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = onResume)
            .semantics { contentDescription = resumeDescription }
            .padding(vertical = if (iconOnly) 15.dp else if (compact) 6.dp else 9.dp, horizontal = 4.dp),
    ) {
        Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            if (!still) Box(modifier = Modifier.size(14.dp).graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = (1.6f - ringScale) / 1.2f }.border(1.5.dp, pal.good, CircleShape))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(pal.good))
        }
        if (!iconOnly) {
            Text(stringResource(R.string.resume_session), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, softWrap = false)
            Text(name, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
