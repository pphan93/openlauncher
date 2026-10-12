package com.openlauncher.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.DefaultShortcutIcon
import com.openlauncher.app.data.IconPackIcon
import com.openlauncher.app.data.ShortcutIconCategory
import com.openlauncher.app.data.ShortcutConfig
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.theme.LocalDayMode
import com.openlauncher.app.util.IconPackManager
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*
import kotlin.math.roundToInt

private val ICON_SIZE   = 20.dp
private val SLOT_SIZE   = 52.dp
private val HORIZONTAL_SLOT_SIZE = 88.dp
private val VERTICAL_DASHBOARD_SLOT_SIZE = 70.dp

@Composable
fun Sidebar(
    currentDest: NavDestination,
    settings: AppSettings,
    installedIconFor: (String) -> Drawable?,
    onNavigate: (NavDestination) -> Unit,
    onShortcutClick: (Int) -> Unit,
    onShortcutLongPress: (Int) -> Unit,
    onShortcutRemove: (Int) -> Unit,
    onShortcutSetIcon: (Int, DefaultShortcutIcon?) -> Unit,
    requestedIconPickerSlot: Int? = null,
    onIconPickerDismiss: () -> Unit = {},
    onReorder: (from: Int, to: Int) -> Unit,
    isHorizontal: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDayMode    = LocalDayMode.current
    val accent       = Color(settings.dashboardAccentColor)
    val screenBg     = if (isDayMode) Color(0xFFF0F1ED) else Color(0xFF090A0A)
    val sidebarBg    = (if (isDayMode) Color.White else Color(0xFF181919))
        .copy(alpha = settings.widgetOpacity.coerceIn(0f, 1f))
    val iconInactive = if (isDayMode) Color(settings.dayFontColor).copy(alpha = 0.68f) else Color(settings.fontColor).copy(alpha = 0.72f)
    val dividerColor = if (isDayMode) Color(0xFFCCCCCC) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    val context      = LocalContext.current
    val density      = LocalDensity.current
    val slotSizePx   = with(density) {
        (if (isHorizontal) HORIZONTAL_SLOT_SIZE else VERTICAL_DASHBOARD_SLOT_SIZE).toPx()
    }

    var actionSheetSlot by remember { mutableStateOf<Int?>(null) }
    var iconPickerSlot  by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(requestedIconPickerSlot) {
        if (requestedIconPickerSlot != null) iconPickerSlot = requestedIconPickerSlot
    }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetPx  by remember { mutableFloatStateOf(0f) }

    val homeNavigationIcon = remember(settings.navigationIcons.home) {
        loadNavigationIcon(context, settings.navigationIcons.home)
    }
    val appsNavigationIcon = remember(settings.navigationIcons.apps) {
        loadNavigationIcon(context, settings.navigationIcons.apps)
    }
    val settingsNavigationIcon = remember(settings.navigationIcons.settings) {
        loadNavigationIcon(context, settings.navigationIcons.settings)
    }

    fun dragTargetIndex(): Int = if (draggingIndex < 0) -1 else
        (draggingIndex + (dragOffsetPx / slotSizePx).roundToInt())
            .coerceIn(0, settings.shortcuts.size - 1)

    fun slotTranslation(index: Int): Float {
        if (draggingIndex < 0 || index == draggingIndex) return 0f
        val from = draggingIndex
        val to   = dragTargetIndex()
        return when {
            from < to && index in (from + 1)..to -> -slotSizePx
            from > to && index in to until from  ->  slotSizePx
            else -> 0f
        }
    }

    val shortcutsContent: @Composable (List<Int>) -> Unit = { indices ->
        indices.forEach { index ->
            val shortcut = settings.shortcuts[index]
            val isDragging  = (index == draggingIndex)
            val translation = if (isDragging) dragOffsetPx else slotTranslation(index)

            ShortcutSlot(
                shortcut        = shortcut,
                accent          = accent,
                resolvedIcon    = if (shortcut.packageName.isNotEmpty())
                                      installedIconFor(shortcut.packageName) else null,
                isDragging      = isDragging,
                dragTranslation = translation,
                isHorizontal    = isHorizontal,
                dashboardStyle = true,
                iconColor = iconInactive,
                onClick         = { onShortcutClick(index) },
                onLongPress     = {
                    if (shortcut.packageName.isNotEmpty()) {
                        actionSheetSlot = index
                    } else {
                        onShortcutLongPress(index)
                    }
                },
                onDragStart = {
                    draggingIndex = index
                    dragOffsetPx  = 0f
                },
                onDragDelta = { d -> dragOffsetPx += d },
                onDragEnd   = {
                    val from = draggingIndex
                    val to   = dragTargetIndex()
                    if (from >= 0 && to != from) onReorder(from, to)
                    draggingIndex = -1
                    dragOffsetPx  = 0f
                }
            )
        }
    }

    if (isHorizontal) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(screenBg)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(sidebarBg)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NavButton(PhIcons.Regular.House, "Home", currentDest == NavDestination.HOME, accent, iconInactive, true, true, homeNavigationIcon) {
                    onNavigate(NavDestination.HOME)
                }
                NavButton(PhIcons.Regular.SquaresFour, "Apps", currentDest == NavDestination.APP_LIBRARY, accent, iconInactive, true, true, appsNavigationIcon) {
                    onNavigate(NavDestination.APP_LIBRARY)
                }
                NavButton(PhIcons.Regular.Gear, "Settings", currentDest == NavDestination.SETTINGS, accent, iconInactive, true, true, settingsNavigationIcon) {
                    onNavigate(NavDestination.SETTINGS)
                }
                shortcutsContent(settings.shortcuts.indices.toList())
            }

        }
    } else {
        Box(
            modifier = modifier
                .width(84.dp)
                .fillMaxHeight()
                .background(screenBg)
                .padding(horizontal = 7.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(sidebarBg)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavButton(PhIcons.Regular.House, "Home", currentDest == NavDestination.HOME, accent, iconInactive, false, true, homeNavigationIcon) {
                    onNavigate(NavDestination.HOME)
                }
                NavButton(PhIcons.Regular.SquaresFour, "Apps", currentDest == NavDestination.APP_LIBRARY, accent, iconInactive, false, true, appsNavigationIcon) {
                    onNavigate(NavDestination.APP_LIBRARY)
                }
                NavButton(PhIcons.Regular.Gear, "Settings", currentDest == NavDestination.SETTINGS, accent, iconInactive, false, true, settingsNavigationIcon) {
                    onNavigate(NavDestination.SETTINGS)
                }
                shortcutsContent(settings.shortcuts.indices.toList())
            }
        }
    }

    // ── Action sheet dialog ──────────────────────────────────────────────────
    actionSheetSlot?.let { slot ->
        ShortcutActionDialog(
            accent      = accent,
            onChangeApp = {
                actionSheetSlot = null
                onShortcutLongPress(slot)
            },
            onCustomizeIcon = {
                actionSheetSlot = null
                iconPickerSlot = slot
            },
            onRemove = {
                actionSheetSlot = null
                onShortcutRemove(slot)
            },
            onDismiss = { actionSheetSlot = null }
        )
    }

    // ── Icon picker dialog ───────────────────────────────────────────────────
    iconPickerSlot?.let { slot ->
        IconPickerDialog(
            accent          = accent,
            hasNativeIcon   = settings.shortcuts.getOrNull(slot)?.packageName?.isNotEmpty() == true,
            currentOverride = settings.shortcuts.getOrNull(slot)?.customIconOverride,
            onPick  = { icon ->
                iconPickerSlot = null
                onShortcutSetIcon(slot, icon)
                onIconPickerDismiss()
            },
            onDismiss = {
                iconPickerSlot = null
                onIconPickerDismiss()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutSlot(
    shortcut: ShortcutConfig,
    accent: Color,
    resolvedIcon: Drawable?,
    isDragging: Boolean,
    dragTranslation: Float,
    isHorizontal: Boolean,
    dashboardStyle: Boolean,
    iconColor: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val currentOnClick      by rememberUpdatedState(onClick)
    val currentOnLongPress  by rememberUpdatedState(onLongPress)
    val currentOnDragStart  by rememberUpdatedState(onDragStart)
    val currentOnDragDelta  by rememberUpdatedState(onDragDelta)
    val currentOnDragEnd    by rememberUpdatedState(onDragEnd)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(
                if (isHorizontal) Modifier.fillMaxHeight().width(HORIZONTAL_SLOT_SIZE)
                else if (dashboardStyle) Modifier.fillMaxWidth().height(VERTICAL_DASHBOARD_SLOT_SIZE)
                else Modifier.fillMaxWidth().height(SLOT_SIZE)
            )
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                if (isHorizontal) translationX = dragTranslation else translationY = dragTranslation
                alpha = if (isDragging) 0.55f else 1f
            }
            .combinedClickable(
                onClick     = { currentOnClick() },
                onLongClick = { }
            )
            .pointerInput(isHorizontal) {
                var longPressTriggered = false
                var hasSignificantDrag = false
                var totalDrag          = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { _ ->
                        longPressTriggered = true
                        hasSignificantDrag = false
                        totalDrag          = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val delta = if (isHorizontal) dragAmount.x else dragAmount.y
                        totalDrag += delta
                        if (!hasSignificantDrag && kotlin.math.abs(totalDrag) > viewConfiguration.touchSlop) {
                            hasSignificantDrag = true
                            currentOnDragStart()
                        }
                        if (hasSignificantDrag) currentOnDragDelta(delta)
                    },
                    onDragEnd = {
                        if (longPressTriggered && !hasSignificantDrag) currentOnLongPress()
                        if (hasSignificantDrag) currentOnDragEnd()
                        longPressTriggered = false
                        hasSignificantDrag = false
                        totalDrag          = 0f
                    },
                    onDragCancel = {
                        if (hasSignificantDrag) currentOnDragEnd()
                        longPressTriggered = false
                        hasSignificantDrag = false
                        totalDrag          = 0f
                    }
                )
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val override = shortcut.customIconOverride
            when {
            override != null && override != DefaultShortcutIcon.NONE -> {
                Icon(
                    imageVector        = override.toIcon(),
                    contentDescription = shortcut.label,
                    tint               = iconColor,
                    modifier           = Modifier.size(ICON_SIZE)
                )
            }
            resolvedIcon != null -> {
                // Cache per icon — every slot recomposes each drag frame, and an
                // un-remembered toBitmap allocated a fresh bitmap per slot per frame
                val bmp = remember(resolvedIcon) { resolvedIcon.toBitmap(44, 44) }
                Icon(
                    painter            = BitmapPainter(bmp.asImageBitmap()),
                    contentDescription = shortcut.label,
                    tint               = iconColor,
                    modifier           = Modifier.size(ICON_SIZE)
                )
            }
            shortcut.isDefault -> {
                Icon(
                    imageVector        = shortcut.defaultIcon.toIcon(),
                    contentDescription = shortcut.label,
                    tint               = iconColor,
                    modifier           = Modifier.size(ICON_SIZE)
                )
            }
            else -> {
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = "Add shortcut",
                    tint               = iconColor.copy(alpha = 0.42f),
                    modifier           = Modifier.size(ICON_SIZE)
                )
            }
            }
            if (isHorizontal || dashboardStyle) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = shortcut.label.ifEmpty { "Add" },
                    color = iconColor,
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ShortcutActionDialog(
    accent: Color,
    onChangeApp: () -> Unit,
    onCustomizeIcon: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF111111))
                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                .padding(vertical = 4.dp)
                .width(180.dp)
        ) {
            ActionRow("CHANGE APP",     Icons.Default.SwapHoriz, accent, onChangeApp)
            HorizontalDivider(color = Color(0xFF1A1A1A))
            ActionRow("CUSTOMIZE ICON", Icons.Default.Palette,   accent, onCustomizeIcon)
            HorizontalDivider(color = Color(0xFF1A1A1A))
            ActionRow("REMOVE",         Icons.Default.Delete,     Color(0xFF993333), onRemove)
        }
    }
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, color = tint, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun IconPickerDialog(
    accent: Color,
    hasNativeIcon: Boolean,
    currentOverride: DefaultShortcutIcon?,
    onPick: (DefaultShortcutIcon?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ShortcutIconCategory?>(null) }
    val vectorOptions = DefaultShortcutIcon.entries.filter {
        it != DefaultShortcutIcon.NONE &&
            (selectedCategory == null || it.category == selectedCategory)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF111111))
                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Text(
                "CHOOSE ICON",
                color         = Color(0xFF888888),
                fontSize      = 9.sp,
                letterSpacing = 2.sp,
                modifier      = Modifier.padding(bottom = 10.dp)
            )

            if (hasNativeIcon) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (currentOverride == null) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onPick(null) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Apps, null, tint = if (currentOverride == null) accent else Color(0xFF666666), modifier = Modifier.size(18.dp))
                    Text(
                        "NATIVE APP ICON",
                        color         = if (currentOverride == null) accent else Color(0xFF888888),
                        fontSize      = 9.sp,
                        letterSpacing = 1.sp
                    )
                }
                HorizontalDivider(color = Color(0xFF1A1A1A), modifier = Modifier.padding(vertical = 6.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconCategoryChip(
                    label = "All",
                    selected = selectedCategory == null,
                    accent = accent,
                    onClick = { selectedCategory = null }
                )
                ShortcutIconCategory.entries.forEach { category ->
                    IconCategoryChip(
                        label = category.displayName,
                        selected = selectedCategory == category,
                        accent = accent,
                        onClick = { selectedCategory = category }
                    )
                }
            }

            LazyVerticalGrid(
                columns               = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                modifier              = Modifier
                    .widthIn(min = 320.dp, max = 460.dp)
                    .heightIn(max = 360.dp)
            ) {
                items(vectorOptions, key = { it.name }) { iconOption ->
                    val isSelected = currentOverride == iconOption
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .height(62.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.18f) else Color(0xFF1A1A1A))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accent.copy(alpha = 0.45f) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onPick(iconOption) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector        = iconOption.toIcon(),
                            contentDescription = iconOption.displayName,
                            tint               = if (isSelected) accent else Color(0xFF888888),
                            modifier           = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = iconOption.displayName.uppercase(),
                            color = if (isSelected) accent else Color(0xFF777777),
                            fontSize = 7.sp,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCategoryChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Text(
        text = label.uppercase(),
        color = if (selected) accent else Color(0xFF777777),
        fontSize = 8.sp,
        letterSpacing = 0.7.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}

@Composable
private fun NavButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accent: Color,
    iconInactive: Color,
    isHorizontal: Boolean = false,
    dashboardStyle: Boolean = false,
    customIcon: Drawable? = null,
    onClick: () -> Unit
) {
    val isDayMode = LocalDayMode.current
    val activeIconColor = if (dashboardStyle) {
        if (accent.luminance() > 0.42f) Color.Black else Color.White
    } else if (isDayMode) Color(0xFF111111) else Color.White
    val activeBg = if (dashboardStyle) accent else if (isDayMode) Color(0xFF000000).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f)
    val customBitmap = remember(customIcon) {
        customIcon?.let { runCatching { it.toBitmap(64, 64) }.getOrNull() }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(
                if (isHorizontal) Modifier.padding(horizontal = 4.dp, vertical = 7.dp).fillMaxHeight().width(HORIZONTAL_SLOT_SIZE).clip(RoundedCornerShape(14.dp))
                else if (dashboardStyle) Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth().height(VERTICAL_DASHBOARD_SLOT_SIZE).clip(RoundedCornerShape(14.dp))
                else Modifier.fillMaxWidth().height(SLOT_SIZE)
            )
            .background(if (isActive) activeBg else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        if (isHorizontal || dashboardStyle) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (customBitmap != null) {
                    androidx.compose.foundation.Image(
                        painter = BitmapPainter(customBitmap.asImageBitmap()),
                        contentDescription = label,
                        modifier = Modifier.size(ICON_SIZE)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isActive) activeIconColor else iconInactive,
                        modifier = Modifier.size(ICON_SIZE)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    color = if (isActive) activeIconColor else iconInactive,
                    fontSize = 7.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1
                )
            }
        } else {
            if (customBitmap != null) {
                androidx.compose.foundation.Image(
                    painter = BitmapPainter(customBitmap.asImageBitmap()),
                    contentDescription = label,
                    modifier = Modifier.size(ICON_SIZE)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeIconColor else iconInactive,
                    modifier = Modifier.size(ICON_SIZE)
                )
            }
        }
    }
}

private fun loadNavigationIcon(context: android.content.Context, icon: IconPackIcon): Drawable? =
    if (icon.isConfigured) {
        IconPackManager.loadDrawable(context, icon.packageName, icon.drawableName)
    } else {
        null
    }

fun DefaultShortcutIcon.toIcon(): ImageVector = when (this) {
    // Vehicle
    DefaultShortcutIcon.CAR          -> PhIcons.Regular.Car
    DefaultShortcutIcon.CAR_PLAY     -> PhIcons.Regular.Airplay
    DefaultShortcutIcon.ELECTRIC_CAR -> PhIcons.Regular.ChargingStation
    DefaultShortcutIcon.GAS_STATION  -> PhIcons.Regular.GasPump
    DefaultShortcutIcon.EV_STATION   -> PhIcons.Regular.ChargingStation
    DefaultShortcutIcon.CAR_REPAIR   -> PhIcons.Regular.Wrench
    DefaultShortcutIcon.TIRE_REPAIR  -> PhIcons.Regular.Tire
    DefaultShortcutIcon.PARKING      -> PhIcons.Regular.LetterCircleP
    DefaultShortcutIcon.TRAFFIC      -> PhIcons.Regular.TrafficSignal
    DefaultShortcutIcon.MOTORCYCLE   -> PhIcons.Regular.Motorcycle
    DefaultShortcutIcon.DASHBOARD    -> PhIcons.Regular.Speedometer
    // Navigation
    DefaultShortcutIcon.MAP          -> PhIcons.Regular.MapTrifold
    DefaultShortcutIcon.NAVIGATION   -> PhIcons.Regular.NavigationArrow
    DefaultShortcutIcon.COMPASS      -> PhIcons.Regular.Compass
    DefaultShortcutIcon.LOCATION     -> PhIcons.Regular.MapPin
    DefaultShortcutIcon.ROUTE        -> PhIcons.Regular.Path
    DefaultShortcutIcon.NEAR_ME      -> PhIcons.Regular.Crosshair
    // Audio & media
    DefaultShortcutIcon.RADIO         -> PhIcons.Regular.Radio
    DefaultShortcutIcon.MUSIC         -> PhIcons.Regular.MusicNote
    DefaultShortcutIcon.LIBRARY_MUSIC -> PhIcons.Regular.Playlist
    DefaultShortcutIcon.PLAY          -> PhIcons.Regular.Play
    DefaultShortcutIcon.SPEAKER       -> PhIcons.Regular.SpeakerHigh
    DefaultShortcutIcon.HEADSET       -> PhIcons.Regular.Headset
    DefaultShortcutIcon.EQUALIZER     -> PhIcons.Regular.Equalizer
    DefaultShortcutIcon.VOLUME_UP     -> PhIcons.Regular.SpeakerHigh
    DefaultShortcutIcon.PODCAST       -> PhIcons.Regular.Broadcast
    DefaultShortcutIcon.MICROPHONE    -> PhIcons.Regular.Microphone
    DefaultShortcutIcon.TV            -> PhIcons.Regular.Television
    DefaultShortcutIcon.VIDEOCAM      -> PhIcons.Regular.VideoCamera
    // Communication
    DefaultShortcutIcon.PHONE        -> PhIcons.Regular.Phone
    DefaultShortcutIcon.MESSAGE      -> PhIcons.Regular.ChatText
    DefaultShortcutIcon.CHAT         -> PhIcons.Regular.Chat
    DefaultShortcutIcon.EMAIL        -> PhIcons.Regular.Envelope
    DefaultShortcutIcon.CONTACTS     -> PhIcons.Regular.AddressBook
    // Connectivity
    DefaultShortcutIcon.BLUETOOTH    -> PhIcons.Regular.Bluetooth
    DefaultShortcutIcon.WIFI         -> PhIcons.Regular.WifiHigh
    DefaultShortcutIcon.HOTSPOT      -> PhIcons.Regular.Broadcast
    DefaultShortcutIcon.USB          -> PhIcons.Regular.Usb
    DefaultShortcutIcon.CAST         -> PhIcons.Regular.Screencast
    DefaultShortcutIcon.CELLULAR     -> PhIcons.Regular.CellSignalFull
    // Lighting & climate
    DefaultShortcutIcon.LIGHTBULB    -> PhIcons.Regular.Lightbulb
    DefaultShortcutIcon.BRIGHTNESS   -> PhIcons.Regular.SunDim
    DefaultShortcutIcon.AC           -> PhIcons.Regular.Snowflake
    DefaultShortcutIcon.FAN          -> PhIcons.Regular.Fan
    DefaultShortcutIcon.THERMOSTAT   -> PhIcons.Regular.Thermometer
    DefaultShortcutIcon.SUN          -> PhIcons.Regular.Sun
    DefaultShortcutIcon.NIGHT        -> PhIcons.Regular.Moon
    DefaultShortcutIcon.RAIN         -> PhIcons.Regular.CloudRain
    // General utility
    DefaultShortcutIcon.CAMERA        -> PhIcons.Regular.Camera
    DefaultShortcutIcon.STAR          -> PhIcons.Regular.Star
    DefaultShortcutIcon.FAVORITE      -> PhIcons.Regular.Heart
    DefaultShortcutIcon.TIMER         -> PhIcons.Regular.Timer
    DefaultShortcutIcon.CLOCK         -> PhIcons.Regular.Clock
    DefaultShortcutIcon.ALARM         -> PhIcons.Regular.Alarm
    DefaultShortcutIcon.CALENDAR      -> PhIcons.Regular.Calendar
    DefaultShortcutIcon.CALCULATOR    -> PhIcons.Regular.Calculator
    DefaultShortcutIcon.SHOPPING      -> PhIcons.Regular.ShoppingCart
    DefaultShortcutIcon.FOOD          -> PhIcons.Regular.ForkKnife
    DefaultShortcutIcon.COFFEE        -> PhIcons.Regular.Coffee
    DefaultShortcutIcon.HOME          -> PhIcons.Regular.House
    DefaultShortcutIcon.WORK          -> PhIcons.Regular.Briefcase
    DefaultShortcutIcon.SEARCH        -> PhIcons.Regular.MagnifyingGlass
    DefaultShortcutIcon.NOTIFICATIONS -> PhIcons.Regular.Bell
    DefaultShortcutIcon.LOCK          -> PhIcons.Regular.Lock
    DefaultShortcutIcon.SECURITY      -> PhIcons.Regular.ShieldCheck
    DefaultShortcutIcon.SETTINGS      -> PhIcons.Regular.Gear
    DefaultShortcutIcon.POWER         -> PhIcons.Regular.Power
    DefaultShortcutIcon.DOWNLOAD      -> PhIcons.Regular.Download
    DefaultShortcutIcon.GLOBE         -> PhIcons.Regular.Globe
    DefaultShortcutIcon.NONE          -> PhIcons.Regular.SquaresFour
}
