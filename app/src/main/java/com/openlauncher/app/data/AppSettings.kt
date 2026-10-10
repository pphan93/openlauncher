package com.openlauncher.app.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ClockStyle { DIGITAL, ANALOG }
enum class UnitSystem { METRIC, IMPERIAL }
enum class AppFont { SYSTEM, JETBRAINS_MONO, SOURCE_CODE_PRO }
enum class DayNightMode { DARK, LIGHT, AUTO, SYSTEM }
enum class SidebarPosition { LEFT, RIGHT, BOTTOM }
enum class GradientDirection { TOP_TO_BOTTOM, LEFT_TO_RIGHT, DIAGONAL, RADIAL }
enum class MusicVisualizerStyle {
    CLASSIC_BARS,
    MIRRORED_BARS,
    CIRCULAR_SPECTRUM,
    WAVEFORM,
    RING_OF_FIRE
}

enum class ShortcutIconCategory(val displayName: String) {
    VEHICLE("Vehicle"),
    NAVIGATION("Navigation"),
    MEDIA("Media"),
    COMMUNICATION("Communication"),
    CONNECTIVITY("Connectivity"),
    CLIMATE("Climate"),
    GENERAL("General")
}

/**
 * Theme-friendly vector icons available for sidebar app shortcuts.
 *
 * These enum names are persisted in DataStore JSON. Existing names must not be
 * renamed or removed; adding new values is backwards-compatible.
 */
enum class DefaultShortcutIcon(
    val displayName: String,
    val category: ShortcutIconCategory
) {
    NONE("None", ShortcutIconCategory.GENERAL),

    // Vehicle
    CAR("Car", ShortcutIconCategory.VEHICLE),
    CAR_PLAY("CarPlay", ShortcutIconCategory.VEHICLE),
    ELECTRIC_CAR("Electric car", ShortcutIconCategory.VEHICLE),
    GAS_STATION("Gas station", ShortcutIconCategory.VEHICLE),
    EV_STATION("EV charging", ShortcutIconCategory.VEHICLE),
    CAR_REPAIR("Car repair", ShortcutIconCategory.VEHICLE),
    TIRE_REPAIR("Tire repair", ShortcutIconCategory.VEHICLE),
    PARKING("Parking", ShortcutIconCategory.VEHICLE),
    TRAFFIC("Traffic", ShortcutIconCategory.VEHICLE),
    MOTORCYCLE("Motorcycle", ShortcutIconCategory.VEHICLE),
    DASHBOARD("Dashboard", ShortcutIconCategory.VEHICLE),

    // Navigation
    MAP("Map", ShortcutIconCategory.NAVIGATION),
    NAVIGATION("Navigation", ShortcutIconCategory.NAVIGATION),
    COMPASS("Compass", ShortcutIconCategory.NAVIGATION),
    LOCATION("Location", ShortcutIconCategory.NAVIGATION),
    ROUTE("Route", ShortcutIconCategory.NAVIGATION),
    NEAR_ME("Near me", ShortcutIconCategory.NAVIGATION),

    // Audio & media
    RADIO("Radio", ShortcutIconCategory.MEDIA),
    MUSIC("Music", ShortcutIconCategory.MEDIA),
    LIBRARY_MUSIC("Music library", ShortcutIconCategory.MEDIA),
    PLAY("Play", ShortcutIconCategory.MEDIA),
    SPEAKER("Speaker", ShortcutIconCategory.MEDIA),
    HEADSET("Headset", ShortcutIconCategory.MEDIA),
    EQUALIZER("Equalizer", ShortcutIconCategory.MEDIA),
    VOLUME_UP("Volume", ShortcutIconCategory.MEDIA),
    PODCAST("Podcast", ShortcutIconCategory.MEDIA),
    MICROPHONE("Microphone", ShortcutIconCategory.MEDIA),
    TV("TV", ShortcutIconCategory.MEDIA),
    VIDEOCAM("Video camera", ShortcutIconCategory.MEDIA),

    // Communication
    PHONE("Phone", ShortcutIconCategory.COMMUNICATION),
    MESSAGE("Message", ShortcutIconCategory.COMMUNICATION),
    CHAT("Chat", ShortcutIconCategory.COMMUNICATION),
    EMAIL("Email", ShortcutIconCategory.COMMUNICATION),
    CONTACTS("Contacts", ShortcutIconCategory.COMMUNICATION),

    // Connectivity
    BLUETOOTH("Bluetooth", ShortcutIconCategory.CONNECTIVITY),
    WIFI("Wi-Fi", ShortcutIconCategory.CONNECTIVITY),
    HOTSPOT("Hotspot", ShortcutIconCategory.CONNECTIVITY),
    USB("USB", ShortcutIconCategory.CONNECTIVITY),
    CAST("Cast", ShortcutIconCategory.CONNECTIVITY),
    CELLULAR("Cellular", ShortcutIconCategory.CONNECTIVITY),

    // Lighting & climate
    LIGHTBULB("Lights", ShortcutIconCategory.CLIMATE),
    BRIGHTNESS("Brightness", ShortcutIconCategory.CLIMATE),
    AC("Air conditioning", ShortcutIconCategory.CLIMATE),
    FAN("Fan", ShortcutIconCategory.CLIMATE),
    THERMOSTAT("Thermostat", ShortcutIconCategory.CLIMATE),
    SUN("Day mode", ShortcutIconCategory.CLIMATE),
    NIGHT("Night mode", ShortcutIconCategory.CLIMATE),
    RAIN("Weather", ShortcutIconCategory.CLIMATE),

    // General utility
    CAMERA("Camera", ShortcutIconCategory.GENERAL),
    STAR("Star", ShortcutIconCategory.GENERAL),
    FAVORITE("Favorite", ShortcutIconCategory.GENERAL),
    TIMER("Timer", ShortcutIconCategory.GENERAL),
    CLOCK("Clock", ShortcutIconCategory.GENERAL),
    ALARM("Alarm", ShortcutIconCategory.GENERAL),
    CALENDAR("Calendar", ShortcutIconCategory.GENERAL),
    CALCULATOR("Calculator", ShortcutIconCategory.GENERAL),
    SHOPPING("Shopping", ShortcutIconCategory.GENERAL),
    FOOD("Food", ShortcutIconCategory.GENERAL),
    COFFEE("Coffee", ShortcutIconCategory.GENERAL),
    HOME("Home", ShortcutIconCategory.GENERAL),
    WORK("Work", ShortcutIconCategory.GENERAL),
    SEARCH("Search", ShortcutIconCategory.GENERAL),
    NOTIFICATIONS("Notifications", ShortcutIconCategory.GENERAL),
    LOCK("Lock", ShortcutIconCategory.GENERAL),
    SECURITY("Security", ShortcutIconCategory.GENERAL),
    SETTINGS("Settings", ShortcutIconCategory.GENERAL),
    POWER("Power", ShortcutIconCategory.GENERAL),
    DOWNLOAD("Download", ShortcutIconCategory.GENERAL),
    GLOBE("Web", ShortcutIconCategory.GENERAL)
}

data class SoundPadConfig(
    val label: String,
    val audioUri: String = "",
    val synthType: String = "BEEP"
)

fun defaultSoundboardPads() = listOf(
    SoundPadConfig("mario_jump",   synthType = "mario_jump"),
    SoundPadConfig("mario_coin",   synthType = "mario_coin"),
    SoundPadConfig("boom",         synthType = "boom"),
    SoundPadConfig("loud_fart",    synthType = "loud_fart"),
    SoundPadConfig("+",            synthType = ""),
    SoundPadConfig("+",            synthType = "")
)

data class ShortcutConfig(
    val packageName: String = "",
    val label: String = "",
    val isDefault: Boolean = false,
    val defaultIcon: DefaultShortcutIcon = DefaultShortcutIcon.NONE,
    // null = native app icon; non-null = override with this vector icon
    val customIconOverride: DefaultShortcutIcon? = null
)

const val GRID_COLS = 3
const val GRID_ROWS = 2

data class WidgetConfig(
    val id: String,          // Stable ID from the widget library (for example "CLOCK")
    val gridX: Int,          // column 0..(GRID_COLS-1)
    val gridY: Int,          // row    0..(GRID_ROWS-1)
    val spanX: Int = 1,
    val spanY: Int = 1,
    val enabled: Boolean = true
)

const val EXTERNAL_WIDGET_PREFIX = "ANDROID_WIDGET_"

fun externalWidgetLayoutId(appWidgetId: Int) = "$EXTERNAL_WIDGET_PREFIX$appWidgetId"

data class ExternalWidgetConfig(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val label: String
)

data class AppSettings(
    val vehicleName: String = "MY CAR",
    val profileName: String = "Driver",
    val profileImageUri: String = "",
    val dashboardPhotoUris: List<String> = emptyList(),
    val accentColor: Int = Color.White.toArgb(),
    val backgroundColor: Int = Color.Black.toArgb(),
    val fontColor: Int = Color.White.toArgb(),
    val wallpaperUri: String = "",
    val fontBold: Boolean = false,
    val textScale: Float = 1.2f,
    val uiScale: Float = 1.0f,
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val appFont: AppFont = AppFont.JETBRAINS_MONO,
    val showWeather: Boolean = true,
    val widgetOpacity: Float = 1.0f,
    val manualWeatherLocationName: String = "",
    val manualWeatherLatitude: Double? = null,
    val manualWeatherLongitude: Double? = null,
    val showClock: Boolean = true,
    val showTelemetry: Boolean = true,
    val showNowPlaying: Boolean = true,
    val shortcuts: List<ShortcutConfig> = defaultShortcuts(),
    val widgetLayout: List<WidgetConfig> = defaultWidgetLayout(),
    val externalWidgets: List<ExternalWidgetConfig> = emptyList(),
    val carPlayPackage: String = "",
    val androidAutoPackage: String = "",
    val useGradient: Boolean = false,
    val gradientEndColor: Int = Color.Black.toArgb(),
    val wallpaperDim: Float = 0.55f,
    val sidebarPosition: SidebarPosition = SidebarPosition.BOTTOM,
    val sidebarScale: Float = 1.0f,
    val bottomBarShortcutsRight: Boolean = false,
    val showAltimeter: Boolean = false,
    val showSpeedometer: Boolean = false,
    val dayNightMode: DayNightMode = DayNightMode.DARK,
    val showPip: Boolean = false,
    val pipAppPackage: String = "",
    // Head unit's radio app — mirrored & controlled via its MediaSession
    val radioPackage: String = "",
    val onboardingCompleted: Boolean = false,
    val showVitals: Boolean = false,
    val showTripTracker: Boolean = false,
    val compassOffset: Float = 0f,
    val showSoundboard: Boolean = false,
    val showMusicVisualizer: Boolean = false,
    val showQuickControls: Boolean = false,
    val musicVisualizerStyle: MusicVisualizerStyle = MusicVisualizerStyle.MIRRORED_BARS,
    val soundboardPads: List<SoundPadConfig> = defaultSoundboardPads(),
    val vitalsAsBars: Boolean = false,
    val speedometerDigitalOnly: Boolean = false,
    val gradientDirection: GradientDirection = GradientDirection.DIAGONAL,
    val useCustomBackgroundColor: Boolean = false
)

fun defaultShortcuts() = listOf(
    ShortcutConfig(label = "Radio", isDefault = true, defaultIcon = DefaultShortcutIcon.RADIO),
    ShortcutConfig(label = "Camera", isDefault = true, defaultIcon = DefaultShortcutIcon.CAMERA),
    ShortcutConfig(label = "Music", isDefault = true, defaultIcon = DefaultShortcutIcon.MUSIC),
    ShortcutConfig(label = "Phone", isDefault = true, defaultIcon = DefaultShortcutIcon.PHONE)
)

fun defaultWidgetLayout() = listOf(
    WidgetConfig("CLOCK",       gridX = 0, gridY = 0, spanX = 1, spanY = 1),
    WidgetConfig("WEATHER",     gridX = 1, gridY = 0, spanX = 1, spanY = 1),
    WidgetConfig("TELEMETRY",   gridX = 2, gridY = 0, spanX = 1, spanY = 2),
    WidgetConfig("NOW_PLAYING", gridX = 0, gridY = 1, spanX = 2, spanY = 1)
)

fun AppSettings.activeWidgetIds(): Set<String> = buildSet {
    if (showClock) add("CLOCK")
    if (showWeather) add("WEATHER")
    if (showNowPlaying) add("NOW_PLAYING")
    if (showTelemetry) add("TELEMETRY")
    if (showAltimeter) add("ALTIMETER")
    if (showSpeedometer) add("SPEEDOMETER")
    if (showVitals) add("VITALS")
    if (showTripTracker) add("TRIP_TRACKER")
    if (showSoundboard) add("SOUNDBOARD")
    if (showMusicVisualizer) add("MUSIC_VISUALIZER")
    if (showQuickControls) add("QUICK_CONTROLS")
    externalWidgets.forEach { add(externalWidgetLayoutId(it.appWidgetId)) }
}

/**
 * Moves [movingId] to ([targetX], [targetY]) and pushes any displaced widgets to the
 * first available free cell, cascading until all conflicts are resolved.
 * Operates only on the supplied [layout] list — callers should pass only active/enabled widgets.
 */
fun computeWidgetMove(
    layout: List<WidgetConfig>,
    movingId: String,
    targetX: Int,
    targetY: Int
): List<WidgetConfig> {
    val moving = layout.find { it.id == movingId } ?: return layout
    val placed = moving.copy(
        gridX = targetX.coerceIn(0, GRID_COLS - moving.spanX),
        gridY = targetY.coerceIn(0, GRID_ROWS - moving.spanY)
    )

    val others  = layout.filter { it.id != movingId }
    val result  = mutableListOf(placed)
    val occupied = buildOccupied(result).toMutableSet()

    // Stable widgets that don't conflict go first; displaced ones are pushed afterwards
    val (stable, displaced) = others.partition { w -> result.none { widgetsOverlap(it, w) } }

    for (w in stable) {
        result.add(w)
        for (dx in 0 until w.spanX) for (dy in 0 until w.spanY) occupied.add(w.gridX + dx to w.gridY + dy)
    }

    for (w in displaced) {
        val pos = firstFreeGridPos(w.spanX, w.spanY, occupied)
        val resolved = if (pos != null) w.copy(gridX = pos.first, gridY = pos.second) else w
        result.add(resolved)
        for (dx in 0 until resolved.spanX) for (dy in 0 until resolved.spanY) occupied.add(resolved.gridX + dx to resolved.gridY + dy)
    }

    return result
}

private fun buildOccupied(widgets: List<WidgetConfig>) = buildSet<Pair<Int, Int>> {
    widgets.forEach { w -> for (dx in 0 until w.spanX) for (dy in 0 until w.spanY) add(w.gridX + dx to w.gridY + dy) }
}

private fun widgetsOverlap(a: WidgetConfig, b: WidgetConfig): Boolean =
    a.gridX < b.gridX + b.spanX && a.gridX + a.spanX > b.gridX &&
    a.gridY < b.gridY + b.spanY && a.gridY + a.spanY > b.gridY

private fun firstFreeGridPos(spanX: Int, spanY: Int, occupied: Set<Pair<Int, Int>>): Pair<Int, Int>? {
    for (row in 0 until GRID_ROWS) for (col in 0 until GRID_COLS) {
        if (col + spanX > GRID_COLS || row + spanY > GRID_ROWS) continue
        if ((0 until spanX).all { dx -> (0 until spanY).all { dy -> (col + dx to row + dy) !in occupied } })
            return col to row
    }
    return null
}
