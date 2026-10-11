package com.openlauncher.app

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.SidebarPosition
import com.openlauncher.app.data.GradientDirection
import com.openlauncher.app.data.EXTERNAL_WIDGET_PREFIX
import com.openlauncher.app.data.ExternalWidgetConfig
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.components.Sidebar
import com.openlauncher.app.ui.screen.*
import com.openlauncher.app.ui.theme.OpenLauncherTheme
import com.openlauncher.app.ui.widget.OPEN_LAUNCHER_APP_WIDGET_HOST_ID
import com.openlauncher.app.viewmodel.LauncherViewModel
import kotlin.math.ceil

class MainActivity : ComponentActivity() {

    companion object {
        private const val STATE_PENDING_APP_WIDGET_ID = "pending_app_widget_id"
        private const val STATE_PENDING_APP_WIDGET_PROVIDER = "pending_app_widget_provider"
    }

    private val vm: LauncherViewModel by viewModels()
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val appWidgetHost by lazy { AppWidgetHost(this, OPEN_LAUNCHER_APP_WIDGET_HOST_ID) }
    private var pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingAppWidgetProvider: AppWidgetProviderInfo? = null

    private val bindAppWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrFinishPendingWidget()
        else cancelPendingWidget()
    }

    private val configureAppWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) finishPendingWidget()
        else cancelPendingWidget()
    }

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            vm.startLocationUpdates()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun beginAddingAndroidWidget(provider: AppWidgetProviderInfo) {
        cancelPendingWidget()
        pendingAppWidgetProvider = provider
        pendingAppWidgetId = appWidgetHost.allocateAppWidgetId()

        val bound = runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(pendingAppWidgetId, provider.provider)
        }.getOrDefault(false)

        if (bound) {
            configureOrFinishPendingWidget()
            return
        }

        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingAppWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
        }
        runCatching { bindAppWidget.launch(bindIntent) }
            .onFailure {
                android.widget.Toast.makeText(
                    this,
                    "This head unit does not provide the Android widget approval screen.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                cancelPendingWidget()
            }
    }

    private fun configureOrFinishPendingWidget() {
        val provider = appWidgetManager.getAppWidgetInfo(pendingAppWidgetId)
            ?: pendingAppWidgetProvider
            ?: return cancelPendingWidget()
        pendingAppWidgetProvider = provider

        val configureComponent = provider.configure
        if (configureComponent == null) {
            finishPendingWidget()
            return
        }

        val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configureComponent
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingAppWidgetId)
        }
        runCatching { configureAppWidget.launch(configureIntent) }
            .onFailure {
                // A few older providers declare a stale configuration activity.
                // Keep the bound widget usable instead of discarding it.
                finishPendingWidget()
            }
    }

    private fun finishPendingWidget() {
        val appWidgetId = pendingAppWidgetId
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)
            ?: pendingAppWidgetProvider
            ?: return cancelPendingWidget()
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val cellWidthDp = ((resources.configuration.screenWidthDp - 80f) / 3f).coerceAtLeast(120f)
        val cellHeightDp = (resources.configuration.screenHeightDp / 2f).coerceAtLeast(100f)
        val minWidthDp = provider.minWidth / density
        val minHeightDp = provider.minHeight / density
        val preferredSpanX = ceil(minWidthDp / cellWidthDp).toInt().coerceIn(1, 3)
        val preferredSpanY = ceil(minHeightDp / cellHeightDp).toInt().coerceIn(1, 2)

        vm.addExternalWidget(
            widget = ExternalWidgetConfig(
                appWidgetId = appWidgetId,
                providerPackage = provider.provider.packageName,
                providerClass = provider.provider.className,
                label = provider.loadLabel(packageManager).toString()
            ),
            preferredSpanX = preferredSpanX,
            preferredSpanY = preferredSpanY
        )
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingAppWidgetProvider = null
    }

    private fun cancelPendingWidget() {
        if (pendingAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { appWidgetHost.deleteAppWidgetId(pendingAppWidgetId) }
        }
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingAppWidgetProvider = null
    }

    private fun removeWidget(widgetId: String) {
        if (widgetId.startsWith(EXTERNAL_WIDGET_PREFIX)) {
            widgetId.removePrefix(EXTERNAL_WIDGET_PREFIX).toIntOrNull()?.let { appWidgetId ->
                runCatching { appWidgetHost.deleteAppWidgetId(appWidgetId) }
            }
        }
        vm.removeWidget(widgetId)
    }

    private fun resetSettingsAndWidgets() {
        cancelPendingWidget()
        vm.settings.value.externalWidgets.forEach { widget ->
            runCatching { appWidgetHost.deleteAppWidgetId(widget.appWidgetId) }
        }
        vm.resetSettings()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAppWidgetId = savedInstanceState?.getInt(
            STATE_PENDING_APP_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val savedProvider = savedInstanceState
            ?.getString(STATE_PENDING_APP_WIDGET_PROVIDER)
            ?.let(ComponentName::unflattenFromString)
        pendingAppWidgetProvider = if (pendingAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetManager.getAppWidgetInfo(pendingAppWidgetId)
                ?: appWidgetManager.installedProviders.firstOrNull { it.provider == savedProvider }
        } else null
        hideSystemBars()

        setContent {
            val settingsLoaded by vm.settingsLoaded.collectAsStateWithLifecycle()
            val settings       by vm.settings.collectAsStateWithLifecycle()
            val nav            by vm.nav.collectAsStateWithLifecycle()
            val apps        by vm.apps.collectAsStateWithLifecycle()
            val appsLoading by vm.appsLoading.collectAsStateWithLifecycle()
            val nowPlaying  by vm.nowPlaying.collectAsStateWithLifecycle()
            val weather     by vm.weather.collectAsStateWithLifecycle()
            val location    by vm.location.collectAsStateWithLifecycle()
            val bearing     by vm.compassBearing.collectAsStateWithLifecycle()
            val vehicleData by vm.vehicleData.collectAsStateWithLifecycle()
            val bluetoothDriverDevices by vm.bluetoothDriverDevices.collectAsStateWithLifecycle()
            val activeProfilePhoto by vm.activeProfilePhoto.collectAsStateWithLifecycle()
            val isWifi      by vm.isWifi.collectAsStateWithLifecycle()
            val isData      by vm.isData.collectAsStateWithLifecycle()
            val isDayModeVM by vm.isDayMode.collectAsStateWithLifecycle()
            val hardwareRadio by vm.hardwareRadio.collectAsStateWithLifecycle()
            val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
            val manualWeatherLocationState by vm.manualWeatherLocationState.collectAsStateWithLifecycle()
            val systemIsDark = isSystemInDarkTheme()
            val isDayMode = if (settings.dayNightMode == DayNightMode.SYSTEM) !systemIsDark else isDayModeVM
            val pickerSlot      by vm.shortcutPickerSlot.collectAsStateWithLifecycle()
            val iconPickerSlot  by vm.shortcutIconPickerSlot.collectAsStateWithLifecycle()
            val appPickerTarget by vm.appPickerTarget.collectAsStateWithLifecycle()

            val accent         = Color(settings.accentColor)
            val bg             = if (settings.useCustomBackgroundColor) {
                Color(settings.backgroundColor)
            } else {
                if (isDayMode) Color(0xFFEEEEEE) else Color.Black
            }
            val textColor      = if (isDayMode) Color(settings.dayFontColor) else Color(settings.fontColor)
            val bgGradientEnd  = Color(settings.gradientEndColor)
            val bgBrush        = if (settings.useCustomBackgroundColor && settings.useGradient) {
                val colors = listOf(bg, bgGradientEnd)
                when (settings.gradientDirection) {
                    GradientDirection.TOP_TO_BOTTOM -> androidx.compose.ui.graphics.Brush.verticalGradient(colors)
                    GradientDirection.LEFT_TO_RIGHT -> androidx.compose.ui.graphics.Brush.horizontalGradient(colors)
                    GradientDirection.DIAGONAL -> androidx.compose.ui.graphics.Brush.linearGradient(colors)
                    GradientDirection.RADIAL -> androidx.compose.ui.graphics.Brush.radialGradient(colors)
                }
            } else null

            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density   = baseDensity.density * settings.uiScale,
                    fontScale = baseDensity.fontScale
                )
            ) {
                if (!settingsLoaded) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                } else OpenLauncherTheme(
                    accent     = accent,
                    background = bg,
                    textColor  = textColor,
                    fontBold   = settings.fontBold,
                    textScale  = settings.textScale,
                    appFont    = settings.appFont,
                    isDayMode  = isDayMode,
                    useCustomBg = settings.useCustomBackgroundColor
                ) {
                if (!settings.onboardingCompleted) {
                    OnboardingScreen(
                        accent = accent,
                        onComplete = {
                            vm.updateSettings { copy(onboardingCompleted = true) }
                            // Start location updates immediately upon completion
                            vm.startLocationUpdates()
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().let { m ->
                        if (bgBrush != null) m.background(bgBrush) else m.background(bg)
                    }) {
                        // Optional wallpaper layer
                        if (settings.wallpaperUri.isNotEmpty()) {
                            AsyncImage(
                                model              = android.net.Uri.parse(settings.wallpaperUri),
                                contentDescription = null,
                                contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize()
                            )
                            Box(modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = settings.wallpaperDim)))
                        }

                        val isBottomBar = settings.sidebarPosition == SidebarPosition.BOTTOM

                        val sidebarContent: @Composable () -> Unit = {
                            val sidebarDensity = Density(
                                density = baseDensity.density *
                                    (1.0f + (settings.uiScale - 1.0f) * 0.35f) *
                                    settings.sidebarScale,
                                fontScale = baseDensity.fontScale
                            )
                            CompositionLocalProvider(LocalDensity provides sidebarDensity) {
                                Sidebar(
                                    currentDest   = nav,
                                    settings      = settings,
                                    isHorizontal  = isBottomBar,
                                    installedIconFor = { pkg ->
                                        apps.find { it.packageName == pkg }?.icon
                                    },
                                    onNavigate    = { dest ->
                                        vm.cancelShortcutPicker()
                                        vm.cancelCarPlayPicker()
                                        vm.exitRearrangeMode()
                                        vm.navigate(dest)
                                    },
                                    onShortcutClick = { slot ->
                                        val shortcut = settings.shortcuts[slot]
                                        if (shortcut.packageName.isNotEmpty()) {
                                            vm.launchApp(shortcut.packageName)
                                        }
                                    },
                                    onShortcutLongPress  = { slot -> vm.startShortcutPicker(slot) },
                                    onShortcutRemove     = { slot -> vm.removeShortcut(slot) },
                                    onShortcutSetIcon    = { slot, icon -> vm.setShortcutIcon(slot, icon) },
                                    requestedIconPickerSlot = iconPickerSlot,
                                    onIconPickerDismiss  = { vm.cancelShortcutIconPicker() },
                                    onReorder            = { from, to -> vm.reorderShortcut(from, to) }
                                )
                            }
                        }

                        val mainPane: @Composable (Modifier) -> Unit = { paneModifier ->
                            // ── Main content pane ─────────────────────────────
                            AnimatedContent(
                                targetState   = nav,
                                transitionSpec = {
                                    fadeIn() + slideInHorizontally { it / 10 } togetherWith
                                    fadeOut() + slideOutHorizontally { -it / 10 }
                                },
                                modifier = paneModifier,
                                label    = "pane_transition"
                            ) { destination ->
                                when (destination) {
                                    NavDestination.HOME -> AutomotiveDashboardScreen(
                                        settings = settings,
                                        weather = weather,
                                        nowPlaying = nowPlaying,
                                        location = location,
                                        bearing = bearing,
                                        vehicleData = vehicleData,
                                        profilePhoto = activeProfilePhoto,
                                        isDayMode = isDayMode,
                                        onPlayPause = vm::playPause,
                                        onNext = vm::skipNext,
                                        onPrev = vm::skipPrev,
                                        onSeek = vm::seekTo,
                                        onTapNowPlaying = {
                                            vm.launchApp("com.google.android.apps.youtube.music")
                                        }
                                    )

                                    NavDestination.APP_LIBRARY -> AppLibraryScreen(
                                        apps                = apps,
                                        isLoading           = appsLoading,
                                        isPickerMode        = pickerSlot != null,
                                        pickerSlot          = pickerSlot,
                                        isCarPlayPickerMode = appPickerTarget != null,
                                        carPlayPickerLabel  = when (appPickerTarget) {
                                            com.openlauncher.app.viewmodel.LauncherViewModel.AppPickerTarget.ANDROID_AUTO -> "CHOOSE ANDROID AUTO APP"
                                            com.openlauncher.app.viewmodel.LauncherViewModel.AppPickerTarget.PIP          -> "CHOOSE PIP APP"
                                            com.openlauncher.app.viewmodel.LauncherViewModel.AppPickerTarget.RADIO        -> "CHOOSE RADIO APP"
                                            else -> "CHOOSE CARPLAY APP"
                                        },
                                        accent              = accent,
                                        onAppClick          = { app -> vm.launchApp(app.packageName) },
                                        onPickerSelect      = { slot, app -> vm.assignShortcut(slot, app) },
                                        onCarPlaySelect     = { app -> vm.assignPickerApp(app) }
                                    )

                                    NavDestination.SETTINGS -> SettingsScreen(
                                        settings        = settings,
                                        accent          = accent,
                                        updateStatus    = updateStatus,
                                        onCheckUpdates  = { vm.checkForUpdates() },
                                        onResumeInstall = { vm.resumePendingUpdateInstall() },
                                        manualWeatherLocationState = manualWeatherLocationState,
                                        onSetManualWeatherLocation = vm::setManualWeatherLocation,
                                        onClearManualWeatherLocation = vm::clearManualWeatherLocation,
                                        bluetoothDriverDevices = bluetoothDriverDevices,
                                        onRefreshBluetoothDriverDevices = vm::refreshBluetoothDriverDevices,
                                        onUpdate        = { block -> vm.updateSettings(block) },
                                        onReset         = ::resetSettingsAndWidgets
                                    )
                                }
                            }
                        }

                        if (isBottomBar) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                mainPane(Modifier.weight(1f).fillMaxWidth())
                                sidebarContent()
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (settings.sidebarPosition == SidebarPosition.LEFT) {
                                    sidebarContent()
                                }
                                mainPane(Modifier.weight(1f).fillMaxHeight())
                                if (settings.sidebarPosition == SidebarPosition.RIGHT) {
                                    sidebarContent()
                                }
                            }
                        }
                    }
                }
            }
            } // CompositionLocalProvider
        }
    }

    override fun onResume() {
        super.onResume()
        vm.resumeUpdateAfterPermission()
        vm.refreshConnectivity()
        vm.refreshMedia()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PENDING_APP_WIDGET_ID, pendingAppWidgetId)
        outState.putString(
            STATE_PENDING_APP_WIDGET_PROVIDER,
            pendingAppWidgetProvider?.provider?.flattenToString()
        )
    }

    override fun onStop() {
        super.onStop()
        runCatching { appWidgetHost.stopListening() }
        vm.stopLocationUpdates()
    }

    override fun onStart() {
        super.onStart()
        runCatching { appWidgetHost.startListening() }
        vm.startLocationUpdates()
    }
}
