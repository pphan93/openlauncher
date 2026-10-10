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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.DefaultShortcutIcon
import com.openlauncher.app.data.ShortcutIconCategory
import com.openlauncher.app.data.ShortcutConfig
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.theme.LocalDayMode
import com.openlauncher.app.ui.theme.Poppins
import kotlin.math.roundToInt

private val ICON_SIZE   = 22.dp
private val SLOT_SIZE   = 52.dp
private val HORIZONTAL_SLOT_SIZE = 88.dp

/** A theme-tintable CarPlay-style mark: an open circular ring around Play. */
private val CarPlayIcon: ImageVector = ImageVector.Builder(
    name = "CarPlay",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.6f,
        strokeLineCap = StrokeCap.Round
    ) {
        moveTo(19.2f, 6.7f)
        curveTo(17.45f, 3.75f, 14.25f, 2.15f, 10.85f, 2.25f)
        curveTo(5.55f, 2.4f, 1.4f, 6.75f, 1.55f, 12.05f)
        curveTo(1.7f, 17.45f, 6.15f, 21.7f, 11.55f, 21.55f)
        curveTo(14.9f, 21.45f, 18.0f, 19.7f, 19.65f, 16.85f)
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(8.7f, 7.45f)
        curveTo(8.7f, 6.85f, 9.35f, 6.5f, 9.85f, 6.82f)
        lineTo(16.3f, 10.92f)
        curveTo(16.95f, 11.33f, 16.95f, 12.27f, 16.3f, 12.68f)
        lineTo(9.85f, 16.78f)
        curveTo(9.35f, 17.1f, 8.7f, 16.75f, 8.7f, 16.15f)
        close()
    }
}.build()

/** Four outlined tiles matching the app-grid symbol in the automotive reference. */
private val AppsOutlineIcon: ImageVector = ImageVector.Builder(
    name = "AppsOutline",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    listOf(4f to 4f, 14f to 4f, 4f to 14f, 14f to 14f).forEach { (x, y) ->
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(x + 1f, y)
            lineTo(x + 5f, y)
            curveTo(x + 5.55f, y, x + 6f, y + 0.45f, x + 6f, y + 1f)
            lineTo(x + 6f, y + 5f)
            curveTo(x + 6f, y + 5.55f, x + 5.55f, y + 6f, x + 5f, y + 6f)
            lineTo(x + 1f, y + 6f)
            curveTo(x + 0.45f, y + 6f, x, y + 5.55f, x, y + 5f)
            lineTo(x, y + 1f)
            curveTo(x, y + 0.45f, x + 0.45f, y, x + 1f, y)
            close()
        }
    }
}.build()

/** Minimal lane-marking icon matching the Direction symbol in the reference. */
private val DirectionOutlineIcon: ImageVector = ImageVector.Builder(
    name = "DirectionOutline",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round
    ) {
        moveTo(6.5f, 3f)
        lineTo(5f, 21f)
        moveTo(17.5f, 3f)
        lineTo(19f, 21f)
        moveTo(12f, 3f)
        lineTo(12f, 7f)
        moveTo(12f, 10f)
        lineTo(12f, 14f)
        moveTo(12f, 17f)
        lineTo(12f, 21f)
    }
}.build()

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
    val accent       = if (isHorizontal) Color(0xFFF2F400) else Color(settings.accentColor)
    val sidebarBg    = if (isDayMode) Color(0xFFFFFFFF) else Color(0xFF181919)
    val iconInactive = if (isDayMode) Color(0xFF777777) else Color(0xFFB4B7B2)
    val dividerColor = if (isDayMode) Color(0xFFCCCCCC) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    val density      = LocalDensity.current
    val slotSizePx   = with(density) {
        (if (isHorizontal) HORIZONTAL_SLOT_SIZE else SLOT_SIZE).toPx()
    }

    var actionSheetSlot by remember { mutableStateOf<Int?>(null) }
    var iconPickerSlot  by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(requestedIconPickerSlot) {
        if (requestedIconPickerSlot != null) iconPickerSlot = requestedIconPickerSlot
    }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetPx  by remember { mutableFloatStateOf(0f) }

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

    val navButtons: @Composable () -> Unit = {
        NavButton(
            icon         = AppsOutlineIcon,
            label        = "Apps",
            isActive     = currentDest == NavDestination.APP_LIBRARY,
            accent       = accent,
            iconInactive = iconInactive,
            isHorizontal = isHorizontal,
            onClick      = { onNavigate(NavDestination.APP_LIBRARY) }
        )
        NavButton(
            icon         = Icons.Outlined.Settings,
            label        = "Settings",
            isActive     = currentDest == NavDestination.SETTINGS,
            accent       = accent,
            iconInactive = iconInactive,
            isHorizontal = isHorizontal,
            onClick      = { onNavigate(NavDestination.SETTINGS) }
        )
        NavButton(
            icon         = Icons.Outlined.Home,
            label        = "Home",
            isActive     = currentDest == NavDestination.HOME,
            accent       = accent,
            iconInactive = iconInactive,
            isHorizontal = isHorizontal,
            onClick      = { onNavigate(NavDestination.HOME) }
        )
    }

    if (isHorizontal) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(if (isDayMode) Color(0xFFF0F1ED) else Color(0xFF090A0A))
                .padding(horizontal = 24.dp, vertical = 7.dp)
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
                NavButton(Icons.Outlined.Home, "Home", currentDest == NavDestination.HOME, accent, iconInactive, true) {
                    onNavigate(NavDestination.HOME)
                }
                NavButton(AppsOutlineIcon, "Apps", currentDest == NavDestination.APP_LIBRARY, accent, iconInactive, true) {
                    onNavigate(NavDestination.APP_LIBRARY)
                }
                NavButton(Icons.Outlined.Settings, "Settings", currentDest == NavDestination.SETTINGS, accent, iconInactive, true) {
                    onNavigate(NavDestination.SETTINGS)
                }
                shortcutsContent(settings.shortcuts.indices.toList())
            }

        }
    } else {
        Column(
            modifier = modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(sidebarBg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                shortcutsContent(settings.shortcuts.indices.toList())
            }

            HorizontalDivider(color = dividerColor)
            navButtons()
            Spacer(Modifier.height(4.dp))
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
                else              Modifier.fillMaxWidth().height(SLOT_SIZE)
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
            val iconInactive = if (LocalDayMode.current) Color(0xFF777777) else Color(0xFF8B8E89)
            val override = shortcut.customIconOverride
            when {
            override != null && override != DefaultShortcutIcon.NONE -> {
                Icon(
                    imageVector        = override.toIcon(),
                    contentDescription = shortcut.label,
                    tint               = iconInactive,
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
                    tint               = Color.Unspecified,
                    modifier           = Modifier.size(26.dp)
                )
            }
            shortcut.isDefault -> {
                Icon(
                    imageVector        = shortcut.defaultIcon.toIcon(),
                    contentDescription = shortcut.label,
                    tint               = iconInactive,
                    modifier           = Modifier.size(ICON_SIZE)
                )
            }
            else -> {
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = "Add shortcut",
                    tint               = if (LocalDayMode.current) Color(0xFFBBBBBB) else Color(0xFF252525),
                    modifier           = Modifier.size(ICON_SIZE)
                )
            }
            }
            if (isHorizontal) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = shortcut.label.ifEmpty { "Add" },
                    color = iconInactive,
                    fontSize = 7.sp,
                    fontFamily = Poppins,
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
    onClick: () -> Unit
) {
    val isDayMode = LocalDayMode.current
    val activeIconColor = if (isHorizontal) Color.Black else if (isDayMode) Color(0xFF111111) else Color.White
    val activeBg = if (isHorizontal) Color(0xFFF2F400) else if (isDayMode) Color(0xFF000000).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(
                if (isHorizontal) Modifier.padding(horizontal = 4.dp, vertical = 7.dp).fillMaxHeight().width(HORIZONTAL_SLOT_SIZE).clip(RoundedCornerShape(14.dp))
                else              Modifier.fillMaxWidth().height(SLOT_SIZE)
            )
            .background(if (isActive) activeBg else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        if (isHorizontal) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeIconColor else iconInactive,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    color = if (isActive) activeIconColor else iconInactive,
                    fontSize = 7.sp,
                    fontFamily = Poppins,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1
                )
            }
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

fun DefaultShortcutIcon.toIcon(): ImageVector = when (this) {
    // Vehicle
    DefaultShortcutIcon.CAR         -> Icons.Outlined.DirectionsCar
    DefaultShortcutIcon.CAR_PLAY    -> CarPlayIcon
    DefaultShortcutIcon.ELECTRIC_CAR -> Icons.Default.ElectricCar
    DefaultShortcutIcon.GAS_STATION -> Icons.Default.LocalGasStation
    DefaultShortcutIcon.EV_STATION  -> Icons.Default.EvStation
    DefaultShortcutIcon.CAR_REPAIR  -> Icons.Default.CarRepair
    DefaultShortcutIcon.TIRE_REPAIR -> Icons.Default.TireRepair
    DefaultShortcutIcon.PARKING     -> Icons.Default.LocalParking
    DefaultShortcutIcon.TRAFFIC     -> Icons.Default.Traffic
    DefaultShortcutIcon.MOTORCYCLE  -> Icons.Default.TwoWheeler
    DefaultShortcutIcon.DASHBOARD   -> Icons.Default.Speed
    // Navigation
    DefaultShortcutIcon.MAP         -> Icons.Default.Map
    DefaultShortcutIcon.NAVIGATION  -> DirectionOutlineIcon
    DefaultShortcutIcon.COMPASS     -> Icons.Default.Explore
    DefaultShortcutIcon.LOCATION    -> Icons.Default.Place
    DefaultShortcutIcon.ROUTE       -> Icons.Default.Route
    DefaultShortcutIcon.NEAR_ME     -> Icons.Default.NearMe
    // Audio & media
    DefaultShortcutIcon.RADIO       -> Icons.Outlined.Radio
    DefaultShortcutIcon.MUSIC       -> Icons.Outlined.MusicNote
    DefaultShortcutIcon.LIBRARY_MUSIC -> Icons.Default.LibraryMusic
    DefaultShortcutIcon.PLAY        -> Icons.Default.PlayArrow
    DefaultShortcutIcon.SPEAKER     -> Icons.Default.Speaker
    DefaultShortcutIcon.HEADSET     -> Icons.Default.Headset
    DefaultShortcutIcon.EQUALIZER   -> Icons.Default.Equalizer
    DefaultShortcutIcon.VOLUME_UP   -> Icons.Default.VolumeUp
    DefaultShortcutIcon.PODCAST     -> Icons.Default.Podcasts
    DefaultShortcutIcon.MICROPHONE  -> Icons.Default.Mic
    DefaultShortcutIcon.TV          -> Icons.Default.Tv
    DefaultShortcutIcon.VIDEOCAM    -> Icons.Default.Videocam
    // Communication
    DefaultShortcutIcon.PHONE       -> Icons.Outlined.Phone
    DefaultShortcutIcon.MESSAGE     -> Icons.Default.Message
    DefaultShortcutIcon.CHAT        -> Icons.Default.Chat
    DefaultShortcutIcon.EMAIL       -> Icons.Default.Email
    DefaultShortcutIcon.CONTACTS    -> Icons.Default.Contacts
    // Connectivity
    DefaultShortcutIcon.BLUETOOTH   -> Icons.Default.Bluetooth
    DefaultShortcutIcon.WIFI        -> Icons.Default.Wifi
    DefaultShortcutIcon.HOTSPOT     -> Icons.Default.WifiTethering
    DefaultShortcutIcon.USB         -> Icons.Default.Usb
    DefaultShortcutIcon.CAST        -> Icons.Default.Cast
    DefaultShortcutIcon.CELLULAR    -> Icons.Default.SignalCellularAlt
    // Lighting & climate
    DefaultShortcutIcon.LIGHTBULB   -> Icons.Default.Lightbulb
    DefaultShortcutIcon.BRIGHTNESS  -> Icons.Default.BrightnessHigh
    DefaultShortcutIcon.AC          -> Icons.Default.AcUnit
    DefaultShortcutIcon.FAN         -> Icons.Default.Air
    DefaultShortcutIcon.THERMOSTAT  -> Icons.Default.Thermostat
    DefaultShortcutIcon.SUN         -> Icons.Default.WbSunny
    DefaultShortcutIcon.NIGHT       -> Icons.Default.DarkMode
    DefaultShortcutIcon.RAIN        -> Icons.Default.WaterDrop
    // General utility
    DefaultShortcutIcon.CAMERA      -> Icons.Outlined.CameraAlt
    DefaultShortcutIcon.STAR        -> Icons.Default.Star
    DefaultShortcutIcon.FAVORITE    -> Icons.Default.Favorite
    DefaultShortcutIcon.TIMER       -> Icons.Default.Timer
    DefaultShortcutIcon.CLOCK       -> Icons.Default.Schedule
    DefaultShortcutIcon.ALARM       -> Icons.Default.Alarm
    DefaultShortcutIcon.CALENDAR    -> Icons.Outlined.CalendarMonth
    DefaultShortcutIcon.CALCULATOR  -> Icons.Default.Calculate
    DefaultShortcutIcon.SHOPPING    -> Icons.Default.ShoppingCart
    DefaultShortcutIcon.FOOD        -> Icons.Default.Restaurant
    DefaultShortcutIcon.COFFEE      -> Icons.Default.LocalCafe
    DefaultShortcutIcon.HOME        -> Icons.Outlined.Home
    DefaultShortcutIcon.WORK        -> Icons.Default.Work
    DefaultShortcutIcon.SEARCH      -> Icons.Default.Search
    DefaultShortcutIcon.NOTIFICATIONS -> Icons.Default.Notifications
    DefaultShortcutIcon.LOCK        -> Icons.Default.Lock
    DefaultShortcutIcon.SECURITY    -> Icons.Default.Security
    DefaultShortcutIcon.SETTINGS    -> Icons.Outlined.Settings
    DefaultShortcutIcon.POWER       -> Icons.Default.PowerSettingsNew
    DefaultShortcutIcon.DOWNLOAD    -> Icons.Default.Download
    DefaultShortcutIcon.GLOBE       -> Icons.Default.Language
    DefaultShortcutIcon.NONE        -> Icons.Default.Apps
}
