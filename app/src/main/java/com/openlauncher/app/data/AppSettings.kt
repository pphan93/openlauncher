package com.openlauncher.app.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class UnitSystem { METRIC, IMPERIAL }
enum class AppFont { POPPINS, SYSTEM, JETBRAINS_MONO, SOURCE_CODE_PRO }
enum class DayNightMode { DARK, LIGHT, AUTO, SYSTEM }
enum class SidebarPosition { LEFT, RIGHT, BOTTOM }
enum class GradientDirection { TOP_TO_BOTTOM, LEFT_TO_RIGHT, DIAGONAL, RADIAL }
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

data class ShortcutConfig(
    val packageName: String = "",
    val label: String = "",
    val isDefault: Boolean = false,
    val defaultIcon: DefaultShortcutIcon = DefaultShortcutIcon.NONE,
    // null = native app icon; non-null = override with this vector icon
    val customIconOverride: DefaultShortcutIcon? = null
)

data class BluetoothDriverProfile(
    val deviceAddress: String,
    val deviceName: String,
    val imageUri: String,
    val focusX: Float = 0f,
    val focusY: Float = 0f
)

data class DriverProfilePhoto(
    val imageUri: String = "",
    val focusX: Float = 0f,
    val focusY: Float = 0f
)

data class AppSettings(
    val vehicleName: String = "MY CAR",
    val licensePlate: String = "",
    val profileName: String = "Driver",
    val profileImageUri: String = "",
    val profileImageFocusX: Float = 0f,
    val profileImageFocusY: Float = 0f,
    val autoBluetoothDriverPhoto: Boolean = false,
    val bluetoothDriverProfiles: List<BluetoothDriverProfile> = emptyList(),
    val dashboardPhotoUris: List<String> = emptyList(),
    val accentColor: Int = Color.White.toArgb(),
    val dashboardAccentColor: Int = Color(0xFFF2F400).toArgb(),
    val backgroundColor: Int = Color.Black.toArgb(),
    val fontColor: Int = Color.White.toArgb(),
    val dayFontColor: Int = Color(0xFF111111).toArgb(),
    val wallpaperUri: String = "",
    val fontBold: Boolean = false,
    val textScale: Float = 1.2f,
    val uiScale: Float = 1.0f,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val appFont: AppFont = AppFont.POPPINS,
    val widgetOpacity: Float = 1.0f,
    val manualWeatherLocationName: String = "",
    val manualWeatherLatitude: Double? = null,
    val manualWeatherLongitude: Double? = null,
    val shortcuts: List<ShortcutConfig> = defaultShortcuts(),
    val carPlayPackage: String = "",
    val androidAutoPackage: String = "",
    val useGradient: Boolean = false,
    val gradientEndColor: Int = Color.Black.toArgb(),
    val wallpaperDim: Float = 0.55f,
    val sidebarPosition: SidebarPosition = SidebarPosition.BOTTOM,
    val sidebarScale: Float = 1.0f,
    val bottomBarShortcutsRight: Boolean = false,
    val dayNightMode: DayNightMode = DayNightMode.DARK,
    // Head unit's radio app — mirrored & controlled via its MediaSession
    val radioPackage: String = "",
    val onboardingCompleted: Boolean = false,
    val compassOffset: Float = 0f,
    val gradientDirection: GradientDirection = GradientDirection.DIAGONAL,
    val useCustomBackgroundColor: Boolean = false
)

fun defaultShortcuts() = listOf(
    ShortcutConfig(label = "Menu", isDefault = true, defaultIcon = DefaultShortcutIcon.CAR),
    ShortcutConfig(label = "Direction", isDefault = true, defaultIcon = DefaultShortcutIcon.NAVIGATION),
    ShortcutConfig(label = "Camera", isDefault = true, defaultIcon = DefaultShortcutIcon.CAMERA),
    ShortcutConfig(label = "Media", isDefault = true, defaultIcon = DefaultShortcutIcon.MUSIC)
)
