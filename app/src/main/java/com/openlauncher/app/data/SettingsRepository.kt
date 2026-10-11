package com.openlauncher.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val VEHICLE_NAME       = stringPreferencesKey("vehicle_name")
        val LICENSE_PLATE      = stringPreferencesKey("license_plate")
        val LICENSE_PLATE_REGION = stringPreferencesKey("license_plate_region")
        val LICENSE_PLATE_TEXT_COLOR = intPreferencesKey("license_plate_text_color")
        val PROFILE_NAME       = stringPreferencesKey("profile_name")
        val PROFILE_IMAGE_URI  = stringPreferencesKey("profile_image_uri")
        val PROFILE_IMAGE_FOCUS_X = floatPreferencesKey("profile_image_focus_x")
        val PROFILE_IMAGE_FOCUS_Y = floatPreferencesKey("profile_image_focus_y")
        val AUTO_BLUETOOTH_DRIVER_PHOTO = booleanPreferencesKey("auto_bluetooth_driver_photo")
        val BLUETOOTH_DRIVER_PROFILES_JSON = stringPreferencesKey("bluetooth_driver_profiles_json")
        val DASHBOARD_PHOTO_URIS = stringPreferencesKey("dashboard_photo_uris")
        val ACCENT_COLOR       = intPreferencesKey("accent_color")
        val DASHBOARD_ACCENT_COLOR = intPreferencesKey("dashboard_accent_color")
        val BG_COLOR           = intPreferencesKey("bg_color")
        val FONT_COLOR         = intPreferencesKey("font_color")
        val DAY_FONT_COLOR     = intPreferencesKey("day_font_color")
        val WALLPAPER_URI      = stringPreferencesKey("wallpaper_uri")
        val FONT_BOLD          = booleanPreferencesKey("font_bold")
        val TEXT_SCALE         = floatPreferencesKey("text_scale")
        val UI_SCALE           = floatPreferencesKey("ui_scale")
        val UNIT_SYSTEM        = stringPreferencesKey("unit_system")
        val APP_FONT           = stringPreferencesKey("app_font")
        val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")
        val WEATHER_WIDGET_OPACITY_LEGACY = floatPreferencesKey("weather_widget_opacity")
        val MANUAL_WEATHER_LOCATION_NAME = stringPreferencesKey("manual_weather_location_name")
        val MANUAL_WEATHER_LATITUDE = doublePreferencesKey("manual_weather_latitude")
        val MANUAL_WEATHER_LONGITUDE = doublePreferencesKey("manual_weather_longitude")
        val SHORTCUTS_JSON     = stringPreferencesKey("shortcuts_json")
        val CAR_PLAY_PACKAGE      = stringPreferencesKey("car_play_package")
        val ANDROID_AUTO_PACKAGE  = stringPreferencesKey("android_auto_package")
        val USE_GRADIENT          = booleanPreferencesKey("use_gradient")
        val GRADIENT_END_COLOR    = intPreferencesKey("gradient_end_color")
        val WALLPAPER_DIM         = floatPreferencesKey("wallpaper_dim")
        val RIGHT_HAND_DRIVE      = booleanPreferencesKey("right_hand_drive") // kept for migration
        val SIDEBAR_POSITION           = stringPreferencesKey("sidebar_position")
        val SIDEBAR_SCALE              = floatPreferencesKey("sidebar_scale")
        val BOTTOM_BAR_SHORTCUTS_RIGHT = booleanPreferencesKey("bottom_bar_shortcuts_right")
        val DAY_NIGHT_MODE        = stringPreferencesKey("day_night_mode")
        val RADIO_PACKAGE         = stringPreferencesKey("radio_package")
        val ONBOARDING_COMPLETED  = booleanPreferencesKey("onboarding_completed")
        val COMPASS_OFFSET        = floatPreferencesKey("compass_offset")
        val GRADIENT_DIRECTION    = stringPreferencesKey("gradient_direction")
        val USE_CUSTOM_BG_COLOR   = booleanPreferencesKey("use_custom_bg_color")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> readSettings(prefs) }

    private fun readSettings(prefs: Preferences): AppSettings {
            val defaults = AppSettings()
            val shortcutsJson = prefs[Keys.SHORTCUTS_JSON]
            val shortcuts = if (shortcutsJson != null) {
                runCatching {
                    gson.fromJson<List<ShortcutConfig>>(
                        shortcutsJson,
                        object : TypeToken<List<ShortcutConfig>>() {}.type
                    )
                }.getOrNull() ?: defaults.shortcuts
            } else defaults.shortcuts

            return AppSettings(
                vehicleName    = prefs[Keys.VEHICLE_NAME]     ?: defaults.vehicleName,
                licensePlate   = prefs[Keys.LICENSE_PLATE]     ?: defaults.licensePlate,
                licensePlateRegion = prefs[Keys.LICENSE_PLATE_REGION]?.let {
                    runCatching { LicensePlateRegion.valueOf(it) }.getOrNull()
                } ?: defaults.licensePlateRegion,
                licensePlateTextColor = prefs[Keys.LICENSE_PLATE_TEXT_COLOR]
                    ?: defaults.licensePlateTextColor,
                profileName    = prefs[Keys.PROFILE_NAME]     ?: defaults.profileName,
                profileImageUri = prefs[Keys.PROFILE_IMAGE_URI] ?: defaults.profileImageUri,
                profileImageFocusX = (prefs[Keys.PROFILE_IMAGE_FOCUS_X]
                    ?: defaults.profileImageFocusX).coerceIn(-1f, 1f),
                profileImageFocusY = (prefs[Keys.PROFILE_IMAGE_FOCUS_Y]
                    ?: defaults.profileImageFocusY).coerceIn(-1f, 1f),
                autoBluetoothDriverPhoto = prefs[Keys.AUTO_BLUETOOTH_DRIVER_PHOTO]
                    ?: defaults.autoBluetoothDriverPhoto,
                bluetoothDriverProfiles = prefs[Keys.BLUETOOTH_DRIVER_PROFILES_JSON]?.let {
                    runCatching {
                        gson.fromJson<List<BluetoothDriverProfile>>(
                            it,
                            object : TypeToken<List<BluetoothDriverProfile>>() {}.type
                        )
                    }.getOrNull()
                } ?: defaults.bluetoothDriverProfiles,
                dashboardPhotoUris = prefs[Keys.DASHBOARD_PHOTO_URIS]?.let {
                    runCatching {
                        gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type)
                    }.getOrNull()
                } ?: defaults.dashboardPhotoUris,
                accentColor    = prefs[Keys.ACCENT_COLOR]     ?: defaults.accentColor,
                dashboardAccentColor = prefs[Keys.DASHBOARD_ACCENT_COLOR] ?: defaults.dashboardAccentColor,
                backgroundColor = prefs[Keys.BG_COLOR]        ?: defaults.backgroundColor,
                fontColor      = prefs[Keys.FONT_COLOR]       ?: defaults.fontColor,
                dayFontColor   = prefs[Keys.DAY_FONT_COLOR]   ?: defaults.dayFontColor,
                wallpaperUri   = prefs[Keys.WALLPAPER_URI]    ?: defaults.wallpaperUri,
                fontBold       = prefs[Keys.FONT_BOLD]        ?: defaults.fontBold,
                textScale      = prefs[Keys.TEXT_SCALE]       ?: defaults.textScale,
                uiScale        = prefs[Keys.UI_SCALE]         ?: defaults.uiScale,
                unitSystem     = prefs[Keys.UNIT_SYSTEM]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: defaults.unitSystem,
                appFont        = prefs[Keys.APP_FONT]?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: defaults.appFont,
                widgetOpacity = (
                    prefs[Keys.WIDGET_OPACITY]
                        ?: prefs[Keys.WEATHER_WIDGET_OPACITY_LEGACY]
                        ?: defaults.widgetOpacity
                ).coerceIn(0f, 1f),
                manualWeatherLocationName = prefs[Keys.MANUAL_WEATHER_LOCATION_NAME] ?: defaults.manualWeatherLocationName,
                manualWeatherLatitude = prefs[Keys.MANUAL_WEATHER_LATITUDE],
                manualWeatherLongitude = prefs[Keys.MANUAL_WEATHER_LONGITUDE],
                shortcuts      = shortcuts,
                carPlayPackage      = prefs[Keys.CAR_PLAY_PACKAGE]      ?: defaults.carPlayPackage,
                androidAutoPackage  = prefs[Keys.ANDROID_AUTO_PACKAGE]  ?: defaults.androidAutoPackage,
                useGradient      = prefs[Keys.USE_GRADIENT]        ?: defaults.useGradient,
                gradientEndColor = prefs[Keys.GRADIENT_END_COLOR]  ?: defaults.gradientEndColor,
                wallpaperDim     = prefs[Keys.WALLPAPER_DIM]       ?: defaults.wallpaperDim,
                sidebarPosition  = prefs[Keys.SIDEBAR_POSITION]?.let { runCatching { SidebarPosition.valueOf(it) }.getOrNull() }
                                   ?: if (prefs[Keys.RIGHT_HAND_DRIVE] == true) SidebarPosition.RIGHT else defaults.sidebarPosition,
                sidebarScale     = (prefs[Keys.SIDEBAR_SCALE] ?: defaults.sidebarScale).coerceIn(1.0f, 1.5f),
                bottomBarShortcutsRight = prefs[Keys.BOTTOM_BAR_SHORTCUTS_RIGHT] ?: defaults.bottomBarShortcutsRight,
                dayNightMode     = prefs[Keys.DAY_NIGHT_MODE]?.let { runCatching { DayNightMode.valueOf(it) }.getOrNull() } ?: defaults.dayNightMode,
                radioPackage     = prefs[Keys.RADIO_PACKAGE]    ?: defaults.radioPackage,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
                compassOffset    = prefs[Keys.COMPASS_OFFSET]    ?: defaults.compassOffset,
                gradientDirection = prefs[Keys.GRADIENT_DIRECTION]?.let { runCatching { GradientDirection.valueOf(it) }.getOrNull() } ?: defaults.gradientDirection,
                useCustomBackgroundColor = prefs[Keys.USE_CUSTOM_BG_COLOR] ?: defaults.useCustomBackgroundColor
            )
    }

    suspend fun saveSettings(s: AppSettings) {
        context.dataStore.edit { prefs -> writeSettings(prefs, s) }
    }

    /**
     * Atomic read-modify-write. DataStore serializes edit blocks, so concurrent
     * updates can't overwrite each other (unlike transforming a stale snapshot).
     */
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> writeSettings(prefs, transform(readSettings(prefs))) }
    }

    private fun writeSettings(prefs: MutablePreferences, s: AppSettings) {
            prefs[Keys.VEHICLE_NAME]       = s.vehicleName
            prefs[Keys.LICENSE_PLATE]      = s.licensePlate
            prefs[Keys.LICENSE_PLATE_REGION] = s.licensePlateRegion.name
            prefs[Keys.LICENSE_PLATE_TEXT_COLOR] = s.licensePlateTextColor
            prefs[Keys.PROFILE_NAME]       = s.profileName
            prefs[Keys.PROFILE_IMAGE_URI]  = s.profileImageUri
            prefs[Keys.PROFILE_IMAGE_FOCUS_X] = s.profileImageFocusX.coerceIn(-1f, 1f)
            prefs[Keys.PROFILE_IMAGE_FOCUS_Y] = s.profileImageFocusY.coerceIn(-1f, 1f)
            prefs[Keys.AUTO_BLUETOOTH_DRIVER_PHOTO] = s.autoBluetoothDriverPhoto
            prefs[Keys.BLUETOOTH_DRIVER_PROFILES_JSON] = gson.toJson(s.bluetoothDriverProfiles)
            prefs[Keys.DASHBOARD_PHOTO_URIS] = gson.toJson(s.dashboardPhotoUris)
            prefs[Keys.ACCENT_COLOR]       = s.accentColor
            prefs[Keys.DASHBOARD_ACCENT_COLOR] = s.dashboardAccentColor
            prefs[Keys.BG_COLOR]           = s.backgroundColor
            prefs[Keys.FONT_COLOR]         = s.fontColor
            prefs[Keys.DAY_FONT_COLOR]     = s.dayFontColor
            prefs[Keys.WALLPAPER_URI]      = s.wallpaperUri
            prefs[Keys.FONT_BOLD]          = s.fontBold
            prefs[Keys.TEXT_SCALE]         = s.textScale
            prefs[Keys.UI_SCALE]           = s.uiScale
            prefs[Keys.UNIT_SYSTEM]        = s.unitSystem.name
            prefs[Keys.APP_FONT]           = s.appFont.name
            prefs[Keys.WIDGET_OPACITY] = s.widgetOpacity.coerceIn(0f, 1f)
            prefs.remove(Keys.WEATHER_WIDGET_OPACITY_LEGACY)
            prefs[Keys.MANUAL_WEATHER_LOCATION_NAME] = s.manualWeatherLocationName
            s.manualWeatherLatitude?.let { prefs[Keys.MANUAL_WEATHER_LATITUDE] = it }
                ?: prefs.remove(Keys.MANUAL_WEATHER_LATITUDE)
            s.manualWeatherLongitude?.let { prefs[Keys.MANUAL_WEATHER_LONGITUDE] = it }
                ?: prefs.remove(Keys.MANUAL_WEATHER_LONGITUDE)
            prefs[Keys.SHORTCUTS_JSON]     = gson.toJson(s.shortcuts)
            prefs[Keys.CAR_PLAY_PACKAGE]      = s.carPlayPackage
            prefs[Keys.ANDROID_AUTO_PACKAGE]  = s.androidAutoPackage
            prefs[Keys.USE_GRADIENT]       = s.useGradient
            prefs[Keys.GRADIENT_END_COLOR] = s.gradientEndColor
            prefs[Keys.WALLPAPER_DIM]      = s.wallpaperDim
            prefs[Keys.SIDEBAR_POSITION]           = s.sidebarPosition.name
            prefs[Keys.SIDEBAR_SCALE]              = s.sidebarScale
            prefs[Keys.BOTTOM_BAR_SHORTCUTS_RIGHT] = s.bottomBarShortcutsRight
            prefs[Keys.DAY_NIGHT_MODE]     = s.dayNightMode.name
            prefs[Keys.RADIO_PACKAGE]      = s.radioPackage
            prefs[Keys.ONBOARDING_COMPLETED] = s.onboardingCompleted
            prefs[Keys.COMPASS_OFFSET]     = s.compassOffset
            prefs[Keys.GRADIENT_DIRECTION] = s.gradientDirection.name
            prefs[Keys.USE_CUSTOM_BG_COLOR] = s.useCustomBackgroundColor
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
