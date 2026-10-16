package com.openlauncher.app.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.openlauncher.app.data.AppFont
import com.openlauncher.app.BuildConfig
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.BluetoothDriverProfile
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.SidebarPosition
import com.openlauncher.app.data.ShortcutConfig
import com.openlauncher.app.data.GradientDirection
import com.openlauncher.app.data.IconPackIcon
import com.openlauncher.app.data.LicensePlateRegion
import com.openlauncher.app.data.UnitSystem
import com.openlauncher.app.model.IconPackInfo
import com.openlauncher.app.ui.components.IconPackPickerDialog
import com.openlauncher.app.ui.components.IconPackDrawablePickerDialog
import com.openlauncher.app.ui.components.ValetPinSetupDialog
import com.openlauncher.app.ui.theme.LocalDayMode
import com.openlauncher.app.util.SunriseSunset
import com.openlauncher.app.util.BluetoothDriverDevice
import com.openlauncher.app.util.clearPrivateImportDirectory
import com.openlauncher.app.util.compatibleFilePickerIntent
import com.openlauncher.app.util.importFileToPrivateStorage
import com.openlauncher.app.util.IconPackManager
import com.openlauncher.app.util.selectedUris
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.openlauncher.app.ui.components.ColorPickerDialog
import com.openlauncher.app.ui.components.ConfirmDialog
import com.openlauncher.app.viewmodel.LauncherViewModel
import com.openlauncher.app.viewmodel.UpdateStatus

private data class PendingProfileCrop(
    val imageUri: String,
    val device: BluetoothDriverDevice? = null
)

private enum class NavigationIconTarget(val displayName: String) {
    HOME("Home"),
    APPS("Apps / Launcher"),
    SETTINGS("Settings")
}

// Resolved at call site via LocalDayMode — see SettingsDivider / SettingsSection

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    accent: Color,
    updateStatus: UpdateStatus,
    onCheckUpdates: () -> Unit,
    onResumeInstall: () -> Unit,
    manualWeatherLocationState: LauncherViewModel.ManualWeatherLocationState,
    onSetManualWeatherLocation: (String) -> Unit,
    onClearManualWeatherLocation: () -> Unit,
    bluetoothDriverDevices: List<BluetoothDriverDevice>,
    onRefreshBluetoothDriverDevices: () -> Unit,
    onUpdate: (AppSettings.() -> AppSettings) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showResetDialog       by remember { mutableStateOf(false) }
    var showIconPackPicker    by remember { mutableStateOf(false) }
    var showValetPinDialog    by remember { mutableStateOf(false) }
    var enableValetAfterPinSave by remember { mutableStateOf(false) }
    var installedIconPacks    by remember { mutableStateOf<List<IconPackInfo>>(emptyList()) }
    var iconPacksLoading      by remember { mutableStateOf(true) }
    var iconPackRefreshKey    by remember { mutableIntStateOf(0) }
    var navigationIconTarget  by remember { mutableStateOf<NavigationIconTarget?>(null) }
    var packDrawableNames     by remember { mutableStateOf<List<String>>(emptyList()) }
    var packIconsLoading      by remember { mutableStateOf(false) }
    var showAccentPicker      by remember { mutableStateOf(false) }
    var showDashboardAccentPicker by remember { mutableStateOf(false) }
    var showBgPicker          by remember { mutableStateOf(false) }
    var showGradientEndPicker by remember { mutableStateOf(false) }
    var showFontColorPicker   by remember { mutableStateOf(false) }
    var showDayFontColorPicker by remember { mutableStateOf(false) }
    var showPlateTextColorPicker by remember { mutableStateOf(false) }
    var pendingBluetoothPhotoDevice by remember { mutableStateOf<BluetoothDriverDevice?>(null) }
    var pendingProfileCrop by remember { mutableStateOf<PendingProfileCrop?>(null) }
    var bluetoothPermissionGranted by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(iconPackRefreshKey) {
        iconPacksLoading = true
        installedIconPacks = withContext(kotlinx.coroutines.Dispatchers.IO) {
            IconPackManager.discover(context)
        }
        iconPacksLoading = false
    }

    LaunchedEffect(settings.iconPackPackage, iconPackRefreshKey) {
        if (settings.iconPackPackage.isBlank()) {
            packDrawableNames = emptyList()
            packIconsLoading = false
        } else {
            packIconsLoading = true
            packDrawableNames = withContext(kotlinx.coroutines.Dispatchers.IO) {
                IconPackManager.listDrawableNames(context, settings.iconPackPackage)
            }
            packIconsLoading = false
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        bluetoothPermissionGranted = granted
        if (granted) onRefreshBluetoothDriverDevices()
    }

    LaunchedEffect(Unit) {
        if (updateStatus == UpdateStatus.Idle) onCheckUpdates()
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        selectedUris(result.data).firstOrNull()?.let { uri ->
            coroutineScope.launch {
                val imported = importFileToPrivateStorage(
                    context, uri, "wallpaper", "wallpaper", replaceExisting = true
                )
                if (imported != null) {
                    onUpdate { copy(wallpaperUri = imported.uri) }
                } else {
                    Toast.makeText(context, "The selected wallpaper could not be opened", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val profileImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        selectedUris(result.data).firstOrNull()?.let { uri ->
            coroutineScope.launch {
                val imported = importFileToPrivateStorage(
                    context,
                    uri,
                    "profile_photo",
                    "profile_${System.currentTimeMillis()}"
                )
                if (imported != null) {
                    pendingProfileCrop = PendingProfileCrop(imported.uri)
                } else {
                    Toast.makeText(context, "The selected profile photo could not be opened", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val bluetoothProfileImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val device = pendingBluetoothPhotoDevice
        pendingBluetoothPhotoDevice = null
        if (device != null) {
            selectedUris(result.data).firstOrNull()?.let { uri ->
                coroutineScope.launch {
                    val safeAddress = device.address.filter(Char::isLetterOrDigit)
                    val imported = importFileToPrivateStorage(
                        context,
                        uri,
                        "bluetooth_driver_photos",
                        "driver_${safeAddress}_${System.currentTimeMillis()}"
                    )
                    if (imported != null) {
                        pendingProfileCrop = PendingProfileCrop(imported.uri, device)
                    } else {
                        Toast.makeText(context, "The selected driver photo could not be opened", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Some Android head units omit DocumentsUI entirely. Use a custom activity
    // result so we can fall back to any installed gallery or file manager.
    val dashboardPhotosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = selectedUris(result.data)
            if (uris.isNotEmpty()) {
                coroutineScope.launch {
                    val imported = uris.mapIndexedNotNull { index, uri ->
                        importFileToPrivateStorage(
                            context, uri, "dashboard_photos", "photo_$index"
                        )?.uri
                    }
                    if (imported.isNotEmpty()) {
                        val retained = imported.toSet()
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            java.io.File(context.filesDir, "dashboard_photos").listFiles()?.forEach { file ->
                                if (Uri.fromFile(file).toString() !in retained) file.delete()
                            }
                        }
                        onUpdate { copy(dashboardPhotoUris = imported) }
                    } else {
                        Toast.makeText(context, "The selected photos could not be opened", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val isDayMode = LocalDayMode.current
    val screenBg  = MaterialTheme.colorScheme.background

    pendingProfileCrop?.let { pending ->
        ProfilePhotoPositionDialog(
            imageUri = pending.imageUri,
            accent = accent,
            onDismiss = { pendingProfileCrop = null },
            onConfirm = { focusX, focusY ->
                val device = pending.device
                if (device == null) {
                    onUpdate {
                        copy(
                            profileImageUri = pending.imageUri,
                            profileImageFocusX = focusX,
                            profileImageFocusY = focusY
                        )
                    }
                } else {
                    onUpdate {
                        copy(
                            autoBluetoothDriverPhoto = true,
                            bluetoothDriverProfiles = bluetoothDriverProfiles
                                .filterNot { it.deviceAddress.equals(device.address, ignoreCase = true) } +
                                BluetoothDriverProfile(
                                    deviceAddress = device.address,
                                    deviceName = device.name,
                                    imageUri = pending.imageUri,
                                    focusX = focusX,
                                    focusY = focusY
                                )
                        )
                    }
                }
                pendingProfileCrop = null
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Title ────────────────────────────────────────────────────────────
        Text(
            text          = "SETTINGS",
            style         = MaterialTheme.typography.titleLarge,
            color         = if (isDayMode) MaterialTheme.colorScheme.onBackground else accent,
            letterSpacing = 3.sp,
            fontSize      = 14.sp
        )

        Spacer(Modifier.height(4.dp))

        // ── Permissions ──────────────────────────────────────────────────────
        SettingsSection("Permissions") {
            val isMediaConnected by com.openlauncher.app.service.MediaListenerService.isConnected.collectAsState()
            val isWazeAccessibilityConnected by
                com.openlauncher.app.service.WazeNavigationAccessibilityService.isConnected.collectAsState()

            // Bumped on ON_RESUME so statuses refresh when the user returns from
            // system settings (recomposition alone doesn't re-run these checks)
            var permissionRefresh by remember { mutableIntStateOf(0) }
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permissionRefresh++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // canDrawOverlays requires API 23 — on Android 5.x the permission
            // model doesn't exist, so treat it as granted
            val canDrawOverlays = remember(permissionRefresh) {
                android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
            }
            val hasLocation = remember(permissionRefresh) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            val isDefaultLauncher = remember(permissionRefresh) {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                context.packageManager.resolveActivity(
                    home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                )?.activityInfo?.packageName == context.packageName
            }

            val homeRoleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { permissionRefresh++ }

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionRefresh++ }

            SettingsButton(
                label    = "Set as Default Launcher",
                sublabel = if (isDefaultLauncher) "Active — Open Launcher is the home app"
                           else "Required so the head unit boots into Open Launcher",
                icon     = Icons.Default.Home,
                accent   = if (isDefaultLauncher) accent else Color(0xFF993333),
                onClick  = {
                    // Preferred: the system home-role dialog (API 29+). Vendor ROMs
                    // sometimes ship without it, so fall through to the home-settings
                    // screen, then the default-apps screen.
                    var launched = false
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
                            !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
                        ) {
                            launched = runCatching {
                                homeRoleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                            }.isSuccess
                        }
                    }
                    if (!launched) {
                        launched = runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess
                    }
                    if (!launched) {
                        runCatching {
                            context.startActivity(
                                Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            )
            SettingsDivider()
            SettingsButton(
                label    = "Notification Access",
                sublabel = if (isMediaConnected) "Granted — media controls active" else "Required for Now Playing widget",
                icon     = if (isMediaConnected) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                accent   = if (isMediaConnected) accent else Color(0xFF993333),
                onClick  = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            SettingsDivider()
            SettingsButton(
                label = "Waze Turn-by-Turn",
                sublabel = if (isWazeAccessibilityConnected) {
                    "Enabled — next turns can appear on the dashboard"
                } else {
                    "Enable Open Launcher Waze Guidance in Accessibility"
                },
                icon = Icons.Default.Navigation,
                accent = if (isWazeAccessibilityConnected) accent else Color(0xFF993333),
                onClick = {
                    val service = android.content.ComponentName(
                        context,
                        com.openlauncher.app.service.WazeNavigationAccessibilityService::class.java
                    )
                    val openedDetails = runCatching {
                        context.startActivity(
                            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                                .putExtra(Intent.EXTRA_COMPONENT_NAME, service)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.isSuccess
                    if (!openedDetails) {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
            SettingsDivider()
            SettingsButton(
                label    = "Draw Over Other Apps",
                sublabel = if (canDrawOverlays) "Granted — PIP overlay enabled" else "Required for PIP floating window",
                icon     = if (canDrawOverlays) Icons.Default.Layers else Icons.Default.LayersClear,
                accent   = if (canDrawOverlays) accent else Color(0xFF993333),
                onClick  = {
                    if (android.os.Build.VERSION.SDK_INT >= 23) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            )
            SettingsDivider()
            SettingsButton(
                label    = "Location Access",
                sublabel = if (hasLocation) "Granted — GPS, compass & weather active" else "Required for compass, speed & weather",
                icon     = if (hasLocation) Icons.Default.LocationOn else Icons.Default.LocationOff,
                accent   = if (hasLocation) accent else Color(0xFF993333),
                onClick  = {
                    if (!hasLocation) {
                        // Ask in-app first — previously the only grant path was the
                        // onboarding flow; skipping it left GPS features dead forever
                        locationPermissionLauncher.launch(arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            )
        }

        // ── Vehicle Name ─────────────────────────────────────────────────────
        SettingsSection("Dashboard Profile") {
            var profileNameInput by remember(settings.profileName) { mutableStateOf(settings.profileName) }
            SettingsRow(
                label = "Driver Name",
                sublabel = "Shown beside the connection status in the top bar",
                icon = Icons.Default.AccountCircle
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = profileNameInput,
                        onValueChange = { profileNameInput = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                        colors = outlinedFieldColors(accent),
                        modifier = Modifier.width(160.dp)
                    )
                    if (profileNameInput != settings.profileName) {
                        IconButton(
                            onClick = { onUpdate { copy(profileName = profileNameInput.trim().ifEmpty { "Driver" }) } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Check, "Save profile name", tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            SettingsDivider()
            SettingsButton(
                label = "Profile Photo",
                sublabel = if (settings.profileImageUri.isEmpty()) "Choose a driver image" else "Driver image selected",
                icon = Icons.Default.AddAPhoto,
                accent = accent,
                onClick = {
                    launchCompatiblePicker(context, profileImagePicker, "image/*")
                }
            )
            if (settings.profileImageUri.isNotEmpty()) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            clearPrivateImportDirectory(context, "profile_photo")
                            onUpdate {
                                copy(
                                    profileImageUri = "",
                                    profileImageFocusX = 0f,
                                    profileImageFocusY = 0f
                                )
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("REMOVE PROFILE PHOTO", color = Color(0xFF993333), fontSize = 9.sp)
                }
            }
            SettingsDivider()
            SettingsRow(
                label = "Auto-switch Driver Photo",
                sublabel = "Use the photo assigned to the connected Bluetooth device",
                icon = Icons.Default.Bluetooth
            ) {
                Switch(
                    checked = settings.autoBluetoothDriverPhoto,
                    onCheckedChange = { enabled ->
                        onUpdate { copy(autoBluetoothDriverPhoto = enabled) }
                        if (enabled && !bluetoothPermissionGranted &&
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                        ) {
                            bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                        } else if (enabled) {
                            onRefreshBluetoothDriverDevices()
                        }
                    },
                    colors = switchColors(accent)
                )
            }

            if (settings.autoBluetoothDriverPhoto) {
                if (!bluetoothPermissionGranted) {
                    SettingsButton(
                        label = "Allow Bluetooth Access",
                        sublabel = "Required to identify the connected driver device",
                        icon = Icons.Default.BluetoothSearching,
                        accent = accent,
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        }
                    )
                } else if (bluetoothDriverDevices.isEmpty()) {
                    Text(
                        "No paired Android Bluetooth devices found. Pair the phone in system Bluetooth settings, then reopen this page.",
                        color = if (isDayMode) Color(0xFF777777) else Color(0xFF777777),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                } else {
                    bluetoothDriverDevices.forEachIndexed { index, device ->
                        val mapping = settings.bluetoothDriverProfiles.firstOrNull {
                            it.deviceAddress.equals(device.address, ignoreCase = true)
                        }
                        if (index > 0) SettingsDivider()
                        SettingsRow(
                            label = device.name,
                            sublabel = buildString {
                                append(if (device.connected) "Connected" else "Paired")
                                append(if (mapping != null) " • Photo assigned" else " • No photo")
                            },
                            icon = if (device.connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth
                        ) {
                            TextButton(
                                onClick = {
                                    pendingBluetoothPhotoDevice = device
                                    launchCompatiblePicker(context, bluetoothProfileImagePicker, "image/*")
                                }
                            ) {
                                Text(if (mapping == null) "SET PHOTO" else "CHANGE", color = accent, fontSize = 9.sp)
                            }
                            if (mapping != null) {
                                IconButton(
                                    onClick = {
                                        onUpdate {
                                            copy(bluetoothDriverProfiles = bluetoothDriverProfiles.filterNot {
                                                it.deviceAddress.equals(device.address, ignoreCase = true)
                                            })
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, "Remove driver photo", tint = Color(0xFF993333), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                SettingsDivider()
            }

            SettingsButton(
                label = "Dashboard Photos",
                sublabel = if (settings.dashboardPhotoUris.isEmpty()) {
                    "Choose local, Google Photos, or Google Drive images"
                } else {
                    "${settings.dashboardPhotoUris.size} images in the carousel"
                },
                icon = Icons.Default.Collections,
                accent = accent,
                onClick = {
                    launchCompatiblePicker(context, dashboardPhotosPicker, "image/*", allowMultiple = true)
                }
            )
            if (settings.dashboardPhotoUris.isNotEmpty()) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            clearPrivateImportDirectory(context, "dashboard_photos")
                            onUpdate { copy(dashboardPhotoUris = emptyList()) }
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("CLEAR DASHBOARD PHOTOS", color = Color(0xFF993333), fontSize = 9.sp)
                }
            }
        }

        SettingsSection("Vehicle") {
            var nameInput by remember(settings.vehicleName) { mutableStateOf(settings.vehicleName) }
            SettingsRow(
                label    = "Vehicle Name",
                sublabel = "",
                icon     = Icons.Default.DirectionsCar
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value         = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder   = { Text("MY CAR", color = if (isDayMode) Color(0xFF999999) else Color(0xFF444444), fontSize = 12.sp) },
                        singleLine    = true,
                        textStyle     = LocalTextStyle.current.copy(fontSize = 12.sp, color = if (isDayMode) Color(0xFF111111) else Color.White),
                        colors        = outlinedFieldColors(accent),
                        modifier      = Modifier.width(140.dp)
                    )
                    if (nameInput != settings.vehicleName) {
                        IconButton(onClick = { onUpdate { copy(vehicleName = nameInput) } }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Check, "Save", tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            SettingsDivider()

            SettingsRow(
                label = "Show Vehicle Name",
                sublabel = "Display it in the center of the dashboard status bar",
                icon = Icons.Default.Visibility
            ) {
                Switch(
                    checked = settings.showVehicleNameInStatusBar,
                    onCheckedChange = { enabled ->
                        onUpdate { copy(showVehicleNameInStatusBar = enabled) }
                    },
                    colors = switchColors(accent)
                )
            }

            SettingsDivider()

            SettingsRow(
                label = "Driving Animation",
                sublabel = "Animate the road and wind effects on the vehicle card",
                icon = Icons.Default.Animation
            ) {
                Switch(
                    checked = settings.drivingAnimationEnabled,
                    onCheckedChange = { enabled ->
                        onUpdate { copy(drivingAnimationEnabled = enabled) }
                    },
                    colors = switchColors(accent)
                )
            }

            SettingsDivider()

            var fullTankRangeInput by remember(settings.fullTankRangeKm) {
                mutableStateOf(settings.fullTankRangeKm.toInt().toString())
            }
            val parsedFullTankRange = fullTankRangeInput.toFloatOrNull()
            SettingsRow(
                label = "Full-Tank Range",
                sublabel = "Used to estimate fuel percentage from remaining range",
                icon = Icons.Default.LocalGasStation
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = fullTankRangeInput,
                        onValueChange = { input ->
                            fullTankRangeInput = input.filter(Char::isDigit).take(4)
                        },
                        suffix = { Text("km", fontSize = 11.sp) },
                        singleLine = true,
                        isError = fullTankRangeInput.isNotEmpty() &&
                            (parsedFullTankRange == null || parsedFullTankRange !in 1f..3_000f),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 12.sp,
                            color = if (isDayMode) Color(0xFF111111) else Color.White
                        ),
                        colors = outlinedFieldColors(accent),
                        modifier = Modifier.width(110.dp)
                    )
                    if (parsedFullTankRange != null &&
                        parsedFullTankRange in 1f..3_000f &&
                        parsedFullTankRange != settings.fullTankRangeKm
                    ) {
                        IconButton(
                            onClick = { onUpdate { copy(fullTankRangeKm = parsedFullTankRange) } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Check, "Save full-tank range", tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            SettingsDivider()

            var plateInput by remember(settings.licensePlate) { mutableStateOf(settings.licensePlate) }
            SettingsRow(
                label = "License Plate",
                sublabel = "Shown on the vehicle card",
                icon = Icons.Default.Badge
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = plateInput,
                        onValueChange = { input ->
                            plateInput = input.uppercase().filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.take(12)
                        },
                        placeholder = { Text("ABC 123", color = if (isDayMode) Color(0xFF999999) else Color(0xFF444444), fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = if (isDayMode) Color(0xFF111111) else Color.White),
                        colors = outlinedFieldColors(accent),
                        modifier = Modifier.width(140.dp)
                    )
                    if (plateInput != settings.licensePlate) {
                        IconButton(
                            onClick = { onUpdate { copy(licensePlate = plateInput.trim()) } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Check, "Save license plate", tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            SettingsDivider()

            SettingsRow(
                label = "Plate Style",
                sublabel = settings.licensePlateRegion.displayName,
                icon = Icons.Default.Map
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    LicensePlateRegion.entries.forEach { region ->
                        FilterChip(
                            selected = settings.licensePlateRegion == region,
                            onClick = { onUpdate { copy(licensePlateRegion = region) } },
                            label = { Text(region.displayName, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }

            SettingsDivider()

            SettingsRow(
                label = "Plate Number Color",
                sublabel = "Color of the registration number",
                icon = Icons.Default.FormatColorText
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(settings.licensePlateTextColor))
                        .clickable { showPlateTextColorPicker = true }
                )
            }

            SettingsDivider()

            SettingsRow(
                label = "Navigation Position",
                sublabel = if (settings.sidebarPosition == SidebarPosition.LEFT) "Left side" else "Bottom",
                icon = Icons.Default.Dock
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = settings.sidebarPosition == SidebarPosition.BOTTOM,
                        onClick = { onUpdate { copy(sidebarPosition = SidebarPosition.BOTTOM) } },
                        label = { Text("Bottom", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = Color.Black
                        )
                    )
                    FilterChip(
                        selected = settings.sidebarPosition == SidebarPosition.LEFT,
                        onClick = { onUpdate { copy(sidebarPosition = SidebarPosition.LEFT) } },
                        label = { Text("Left", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            SettingsDivider()

            SettingsRow(label = "Unit System", sublabel = if (settings.unitSystem == UnitSystem.METRIC) "Metric (°C, km)" else "Imperial (°F, mi)", icon = Icons.Default.Straighten) {
                Row {
                    FilterChip(
                        selected = settings.unitSystem == UnitSystem.METRIC,
                        onClick  = { onUpdate { copy(unitSystem = UnitSystem.METRIC) } },
                        label    = { Text("Metric", fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor     = Color.Black
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = settings.unitSystem == UnitSystem.IMPERIAL,
                        onClick  = { onUpdate { copy(unitSystem = UnitSystem.IMPERIAL) } },
                        label    = { Text("Imperial", fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor     = Color.Black
                        )
                    )
                }
            }
        }

        // ── Sidebar Shortcuts ─────────────────────────────────────────────────
        SettingsSection("Weather") {
            var locationInput by remember(settings.manualWeatherLocationName) {
                mutableStateOf(settings.manualWeatherLocationName)
            }
            SettingsRow(
                label = "Manual Location",
                sublabel = if (settings.manualWeatherLocationName.isNotEmpty()) {
                    "Fallback when GPS is unavailable: ${settings.manualWeatherLocationName}"
                } else {
                    "Enter a city or town to use when GPS has no fix"
                },
                icon = Icons.Default.LocationCity
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        placeholder = { Text("Edmonton, Canada", fontSize = 11.sp) },
                        singleLine = true,
                        enabled = !manualWeatherLocationState.isSearching,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 11.sp,
                            color = if (isDayMode) Color(0xFF111111) else Color.White
                        ),
                        colors = outlinedFieldColors(accent),
                        modifier = Modifier.width(220.dp)
                    )
                    IconButton(
                        onClick = { onSetManualWeatherLocation(locationInput) },
                        enabled = !manualWeatherLocationState.isSearching,
                        modifier = Modifier.size(34.dp)
                    ) {
                        if (manualWeatherLocationState.isSearching) {
                            CircularProgressIndicator(
                                color = accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Default.Search, "Find location", tint = accent, modifier = Modifier.size(17.dp))
                        }
                    }
                    if (settings.manualWeatherLocationName.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                locationInput = ""
                                onClearManualWeatherLocation()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Clear, "Clear manual location", modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            if (manualWeatherLocationState.message.isNotEmpty()) {
                Text(
                    text = manualWeatherLocationState.message,
                    color = if (manualWeatherLocationState.isError) Color(0xFFCC6666) else accent,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 10.dp)
                )
            }
        }

        SettingsSection("Sidebar") {
            Column {
                SettingsRow(
                    label    = "Sidebar Size",
                    sublabel = "${"%.0f".format(settings.sidebarScale * 100)}% — enlarges the bar and app icons",
                    icon     = Icons.Default.ZoomOutMap
                ) {}
                Slider(
                    value         = settings.sidebarScale,
                    onValueChange = { scale -> onUpdate { copy(sidebarScale = scale) } },
                    valueRange    = 1.0f..1.5f,
                    steps         = 4,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            SettingsDivider()

            settings.shortcuts.forEachIndexed { index, shortcut ->
                if (index > 0) SettingsDivider()
                SettingsRow(
                    label    = "Slot ${index + 1}",
                    sublabel = when {
                        shortcut.label.isNotEmpty()       -> shortcut.label
                        shortcut.packageName.isNotEmpty() -> shortcut.packageName
                        else                              -> "Empty"
                    },
                    icon     = Icons.Default.Apps
                ) {
                    if (settings.shortcuts.size > 1) {
                        IconButton(
                            onClick  = {
                                onUpdate {
                                    copy(shortcuts = shortcuts.toMutableList().also { it.removeAt(index) })
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF993333), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            SettingsDivider()

            SettingsButton(
                label    = "Add Slot",
                sublabel = "Append an empty shortcut to the sidebar",
                icon     = Icons.Default.Add,
                accent   = accent,
                onClick  = { onUpdate { copy(shortcuts = shortcuts + ShortcutConfig()) } }
            )
        }

        // ── Appearance ───────────────────────────────────────────────────────
        SettingsSection("Appearance") {
            // Display Mode
            SettingsRow(
                label    = "Display Mode",
                sublabel = when (settings.dayNightMode) {
                    DayNightMode.DARK   -> "Always dark"
                    DayNightMode.LIGHT  -> "Always light"
                    DayNightMode.AUTO   -> "Sunrise / sunset"
                    DayNightMode.SYSTEM -> "Follows system theme"
                },
                icon = when (settings.dayNightMode) {
                    DayNightMode.DARK   -> Icons.Default.NightlightRound
                    DayNightMode.LIGHT  -> Icons.Default.LightMode
                    DayNightMode.AUTO   -> Icons.Default.Brightness4
                    DayNightMode.SYSTEM -> Icons.Default.PhoneAndroid
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayNightMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.dayNightMode == mode,
                            onClick  = { onUpdate { copy(dayNightMode = mode) } },
                            label    = {
                                Text(
                                    text      = when (mode) {
                                        DayNightMode.DARK   -> "Dark"
                                        DayNightMode.LIGHT  -> "Light"
                                        DayNightMode.AUTO   -> "Sunset"
                                        DayNightMode.SYSTEM -> "System"
                                    },
                                    fontSize  = 9.sp,
                                    letterSpacing = 0.5.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor     = Color.Black
                            )
                        )
                    }
                }
            }

            SettingsDivider()

            val selectedIconPackName = installedIconPacks
                .firstOrNull { it.packageName == settings.iconPackPackage }
                ?.displayName
            SettingsRow(
                label = "App Icon Pack",
                sublabel = when {
                    settings.iconPackPackage.isBlank() -> "System app icons"
                    selectedIconPackName != null -> selectedIconPackName
                    else -> "Selected pack unavailable — using system icons"
                },
                icon = Icons.Default.Apps
            ) {
                TextButton(onClick = { showIconPackPicker = true }) {
                    Text("CHOOSE", color = accent, fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }

            SettingsDivider()

            SettingsRow(
                label = "Navigation Icons",
                sublabel = if (settings.iconPackPackage.isBlank()) {
                    "Choose an app icon pack first"
                } else {
                    "Select icons for Home, Apps, and Settings"
                },
                icon = Icons.Default.Home
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NavigationIconTarget.entries.forEach { target ->
                        val configured = when (target) {
                            NavigationIconTarget.HOME -> settings.navigationIcons.home.isConfigured
                            NavigationIconTarget.APPS -> settings.navigationIcons.apps.isConfigured
                            NavigationIconTarget.SETTINGS -> settings.navigationIcons.settings.isConfigured
                        }
                        OutlinedButton(
                            onClick = { navigationIconTarget = target },
                            enabled = settings.iconPackPackage.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                target.name + if (configured) " ✓" else "",
                                fontSize = 8.sp,
                                color = if (configured) accent else LocalContentColor.current
                            )
                        }
                    }
                }
            }

            SettingsDivider()

            Column {
                SettingsRow(
                    label = "Widget Transparency",
                    sublabel = if (settings.widgetOpacity == 0f) {
                        "All widget backgrounds fully transparent"
                    } else {
                        "${"%.0f".format(settings.widgetOpacity * 100)}% background opacity for all widgets"
                    },
                    icon = Icons.Default.Opacity
                ) {}
                Slider(
                    value = settings.widgetOpacity,
                    onValueChange = { opacity -> onUpdate { copy(widgetOpacity = opacity) } },
                    valueRange = 0f..1f,
                    steps = 9,
                    colors = sliderColors(accent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            SettingsDivider()

            // Accent color
            SettingsRow(
                label    = "Accent Color",
                sublabel = "UI highlight color",
                icon     = Icons.Default.Palette
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(settings.accentColor))
                        .clickable { showAccentPicker = true }
                )
            }

            SettingsDivider()

            SettingsRow(
                label = "Dashboard Accent",
                sublabel = "Replaces the yellow automotive highlights",
                icon = Icons.Default.DirectionsCar
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(settings.dashboardAccentColor))
                        .clickable { showDashboardAccentPicker = true }
                )
            }

            SettingsDivider()

            // Background color + gradient
            SettingsRow(
                label    = "Background",
                sublabel = if (settings.useGradient) "Gradient" else "Solid color",
                icon     = Icons.Default.FormatColorFill
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Start color swatch
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(settings.backgroundColor))
                            .clickable { showBgPicker = true }
                    )
                    if (settings.useGradient) {
                        androidx.compose.material3.Icon(
                            Icons.Default.ArrowForward, null,
                            tint = if (isDayMode) Color(0xFF999999) else Color(0xFF555555), modifier = Modifier.size(14.dp)
                        )
                        // End color swatch
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(settings.gradientEndColor))
                                .clickable { showGradientEndPicker = true }
                        )
                    }
                    if (settings.useCustomBackgroundColor) {
                        TextButton(
                            onClick = {
                                onUpdate {
                                    copy(
                                        useCustomBackgroundColor = false,
                                        backgroundColor = Color.Black.toArgb(),
                                        useGradient = false
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("DEFAULT", color = accent, fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            SettingsDivider()

            // Font Color row
            SettingsRow(
                label    = "Font Color",
                sublabel = "Custom text color in dark mode",
                icon     = Icons.Default.FormatSize
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(settings.fontColor))
                        .clickable { showFontColorPicker = true }
                )
            }

            SettingsDivider()

            SettingsRow(
                label = "Day Font Color",
                sublabel = "Custom text color in day mode",
                icon = Icons.Default.LightMode
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(settings.dayFontColor))
                        .clickable { showDayFontColorPicker = true }
                )
            }

            SettingsDivider()

            SettingsRow(label = "Use Gradient", sublabel = "Blend two colors as background", icon = Icons.Default.Gradient) {
                Switch(checked = settings.useGradient,
                    onCheckedChange = { onUpdate { copy(useGradient = it) } },
                    colors = switchColors(accent))
            }

            if (settings.useGradient) {
                SettingsDivider()
                SettingsRow(
                    label    = "Gradient Direction",
                    sublabel = when (settings.gradientDirection) {
                        GradientDirection.TOP_TO_BOTTOM -> "Top to Bottom"
                        GradientDirection.LEFT_TO_RIGHT -> "Left to Right"
                        GradientDirection.DIAGONAL      -> "Diagonal (Linear)"
                        GradientDirection.RADIAL        -> "Radial (Circular)"
                    },
                    icon     = Icons.Default.TrendingFlat
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GradientDirection.entries.forEach { dir ->
                            FilterChip(
                                selected = settings.gradientDirection == dir,
                                onClick  = { onUpdate { copy(gradientDirection = dir) } },
                                label    = {
                                    Text(
                                        text      = when (dir) {
                                            GradientDirection.TOP_TO_BOTTOM -> "Vertical"
                                            GradientDirection.LEFT_TO_RIGHT -> "Horizontal"
                                            GradientDirection.DIAGONAL      -> "Diagonal"
                                            GradientDirection.RADIAL        -> "Radial"
                                        },
                                        fontSize  = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent,
                                    selectedLabelColor     = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            SettingsDivider()

            // Wallpaper
            SettingsButton(
                label    = "Set Wallpaper",
                sublabel = if (settings.wallpaperUri.isNotEmpty()) "Custom wallpaper active" else "Choose image from gallery",
                icon     = Icons.Default.Wallpaper,
                accent   = accent,
                onClick  = { launchCompatiblePicker(context, wallpaperPicker, "image/*") }
            )
            if (settings.wallpaperUri.isNotEmpty()) {
                Column {
                    SettingsRow(
                        label    = "Wallpaper Dim",
                        sublabel = "${"%.0f".format(settings.wallpaperDim * 100)}%",
                        icon     = Icons.Default.BrightnessLow
                    ) {}
                    Slider(
                        value         = settings.wallpaperDim,
                        onValueChange = { onUpdate { copy(wallpaperDim = it) } },
                        valueRange    = 0f..0.95f,
                        steps         = 18,
                        colors        = sliderColors(accent),
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick  = {
                            coroutineScope.launch {
                                clearPrivateImportDirectory(context, "wallpaper")
                                onUpdate { copy(wallpaperUri = "") }
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("REMOVE WALLPAPER", color = Color(0xFF993333), fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }

        // ── Typography ───────────────────────────────────────────────────────
        SettingsSection("Typography") {
            SettingsRow(label = "Font", sublabel = fontDisplayName(settings.appFont), icon = Icons.Default.FontDownload) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    com.openlauncher.app.data.AppFont.entries.forEach { font ->
                        FilterChip(
                            selected = settings.appFont == font,
                            onClick  = { onUpdate { copy(appFont = font) } },
                            label    = { Text(fontDisplayName(font), fontSize = 9.sp, letterSpacing = 0.5.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor     = Color.Black
                            )
                        )
                    }
                }
            }

            SettingsDivider()

            SettingsRow(label = "Bold Font", sublabel = "Heavier weight across all text", icon = Icons.Default.FormatBold) {
                Switch(
                    checked         = settings.fontBold,
                    onCheckedChange = { onUpdate { copy(fontBold = it) } },
                    colors          = switchColors(accent)
                )
            }

            SettingsDivider()

            Column {
                SettingsRow(
                    label    = "Text Scale",
                    sublabel = "${"%.0f".format(settings.textScale * 100)}%",
                    icon     = Icons.Default.TextFields
                ) {}
                Slider(
                    value         = settings.textScale,
                    onValueChange = { onUpdate { copy(textScale = it) } },
                    valueRange    = 0.8f..1.4f,
                    steps         = 5,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            SettingsDivider()

            Column {
                SettingsRow(
                    label    = "UI Scale",
                    sublabel = "${"%.0f".format(settings.uiScale * 100)}%  — scales all elements",
                    icon     = Icons.Default.ZoomIn
                ) {}
                Slider(
                    value         = settings.uiScale,
                    onValueChange = { onUpdate { copy(uiScale = it) } },
                    valueRange    = 0.7f..1.5f,
                    steps         = 7,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }

        // ── GPS & Calibration ───────────────────────────────────────────────
        SettingsSection("GPS & Calibration") {
            var calibrationStatus by remember { mutableStateOf<String?>(null) }
            val coroutineScope = rememberCoroutineScope()
            var isCalibratingCompass by remember { mutableStateOf(false) }
            var compassCountdown by remember { mutableIntStateOf(0) }

            // 1. Reset A-GPS Button
            SettingsButton(
                label    = "Reset A-GPS Assistance Data",
                sublabel = calibrationStatus ?: "Forces cold start to download fresh satellite orbits entirely offline",
                icon     = Icons.Default.MyLocation,
                accent   = accent,
                onClick  = {
                    calibrationStatus = "Clearing A-GPS cache..."
                    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                    var success = false
                    try {
                        // "delete_aiding_data" is the command AOSP's GPS provider
                        // actually recognizes (requires ACCESS_LOCATION_EXTRA_COMMANDS)
                        success = lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "delete_aiding_data", android.os.Bundle())
                        lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
                        lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "force_time_injection", null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    calibrationStatus = if (success) {
                        "Cold start forced — go outdoors for a fresh satellite lock (2–3 min)"
                    } else {
                        "Not supported by this device's GPS driver — no data was cleared"
                    }
                }
            )
            
            SettingsDivider()
            
            // 2. Drive-in-circles magnetometer sweep. Android's sensor stack
            // self-calibrates the magnetometer continuously — the circles feed it
            // diverse readings. The timer guides the sweep; it does not (and
            // cannot) apply offsets itself, so the message must not claim it did.
            SettingsButton(
                label    = "Magnetometer Sweep (Parking Lot)",
                sublabel = if (isCalibratingCompass) {
                    "Sweep active: Drive slowly in two 360° circles... (${compassCountdown}s remaining)"
                } else {
                    "Guided sweep — Android self-calibrates the compass while you circle"
                },
                icon     = Icons.Default.Navigation,
                accent   = if (isCalibratingCompass) Color.Green else accent,
                onClick  = {
                    if (!isCalibratingCompass) {
                        isCalibratingCompass = true
                        compassCountdown = 30
                        coroutineScope.launch {
                            while (compassCountdown > 0) {
                                delay(1000)
                                compassCountdown--
                            }
                            isCalibratingCompass = false
                            calibrationStatus = "Sweep complete — check the compass widget; if heading is still off, use the manual offset below"
                        }
                    }
                }
            )

            SettingsDivider()

            // 4. Manual Compass Heading Offset Slider
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                SettingsRow(
                    label    = "Compass Heading Offset",
                    sublabel = "Manual Alignment: ${if (settings.compassOffset >= 0) "+" else ""}${settings.compassOffset.toInt()}°  — aligns compass with vehicle front",
                    icon     = Icons.Default.Explore
                ) {}
                Slider(
                    value         = settings.compassOffset,
                    onValueChange = { onUpdate { copy(compassOffset = it) } },
                    valueRange    = -180f..180f,
                    steps         = 71, // 5 degree steps: 360 / 5 - 1 = 71 steps
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }

        // ── Updates ──────────────────────────────────────────────────────────
        SettingsSection("Valet Mode") {
            val hasValetPin = settings.valetPinSalt.isNotBlank() && settings.valetPinHash.isNotBlank()
            SettingsButton(
                label = if (hasValetPin) "Change Valet PIN" else "Set Valet PIN",
                sublabel = if (hasValetPin) {
                    "PIN is configured securely"
                } else {
                    "Create a 4–8 digit PIN before enabling valet mode"
                },
                icon = Icons.Default.Pin,
                accent = accent,
                onClick = {
                    enableValetAfterPinSave = false
                    showValetPinDialog = true
                }
            )

            SettingsDivider()

            SettingsRow(
                label = "Enable Valet Mode",
                sublabel = "Shows a blank screen until the PIN is entered",
                icon = Icons.Default.Lock
            ) {
                Switch(
                    checked = settings.valetModeEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasValetPin) {
                            enableValetAfterPinSave = true
                            showValetPinDialog = true
                        } else {
                            onUpdate { copy(valetModeEnabled = enabled) }
                        }
                    },
                    colors = switchColors(accent)
                )
            }
        }

        SettingsSection("Updates") {
            SettingsButton(
                label = when (updateStatus) {
                    UpdateStatus.Checking -> "Checking for Updates"
                    is UpdateStatus.Downloading -> "Downloading Update"
                    is UpdateStatus.AwaitingInstallPermission -> "Allow Update Installation"
                    is UpdateStatus.AwaitingInstallConfirmation -> "Install Update"
                    else -> "Check for Updates"
                },
                sublabel = when (val status = updateStatus) {
                    UpdateStatus.Idle -> "Check GitHub and install a newer APK automatically"
                    UpdateStatus.Checking -> "Contacting GitHub…"
                    is UpdateStatus.UpToDate -> "No new update — v${status.version} is current"
                    is UpdateStatus.Downloading -> {
                        val progress = status.progressPercent?.let { " — $it%" } ?: ""
                        "Downloading v${status.version}$progress"
                    }
                    is UpdateStatus.AwaitingInstallPermission ->
                        "Enable Allow from this source; installation continues when you return"
                    is UpdateStatus.AwaitingInstallConfirmation ->
                        "Downloaded v${status.version} — confirm installation in Android"
                    is UpdateStatus.Error -> status.message
                },
                icon     = Icons.Default.SystemUpdate,
                accent   = accent,
                onClick  = {
                    when (updateStatus) {
                        UpdateStatus.Checking, is UpdateStatus.Downloading -> Unit
                        is UpdateStatus.AwaitingInstallPermission,
                        is UpdateStatus.AwaitingInstallConfirmation -> onResumeInstall()
                        else -> onCheckUpdates()
                    }
                }
            )
        }

        // ── Maintenance ──────────────────────────────────────────────────────
        SettingsSection("Maintenance") {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = { showResetDialog = true },
                shape    = RoundedCornerShape(4.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0000)),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.RestartAlt, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset to Defaults", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text          = "v${BuildConfig.VERSION_NAME}  ·  Open Launcher  ·  2026",
            color         = if (isDayMode) Color(0xFFAAAAAA) else Color(0xFF2A2A2A),
            fontSize      = 10.sp,
            letterSpacing = 1.sp,
            modifier      = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )
    }
    } // end Box

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showResetDialog) {
        ConfirmDialog(
            title        = "Reset Settings",
            message      = "Are you sure you want to reset all settings to default? This cannot be undone.",
            confirmLabel = "Reset",
            onConfirm    = { onReset(); showResetDialog = false },
            onDismiss    = { showResetDialog = false }
        )
    }

    if (showIconPackPicker) {
        IconPackPickerDialog(
            packs = installedIconPacks,
            selectedPackage = settings.iconPackPackage,
            isLoading = iconPacksLoading,
            accent = accent,
            onSelect = { packageName ->
                onUpdate { copy(iconPackPackage = packageName) }
                showIconPackPicker = false
            },
            onRefresh = { iconPackRefreshKey++ },
            onDismiss = { showIconPackPicker = false }
        )
    }

    if (showValetPinDialog) {
        ValetPinSetupDialog(
            existingSalt = settings.valetPinSalt,
            existingHash = settings.valetPinHash,
            onSave = { record ->
                onUpdate {
                    copy(
                        valetPinSalt = record.salt,
                        valetPinHash = record.hash,
                        valetModeEnabled = enableValetAfterPinSave
                    )
                }
                showValetPinDialog = false
                enableValetAfterPinSave = false
            },
            onDismiss = {
                showValetPinDialog = false
                enableValetAfterPinSave = false
            }
        )
    }

    navigationIconTarget?.let { target ->
        IconPackDrawablePickerDialog(
            title = "${target.displayName} Icon",
            iconPackPackage = settings.iconPackPackage,
            drawableNames = packDrawableNames,
            isLoading = packIconsLoading,
            accent = accent,
            onSelect = { drawableName ->
                val selectedIcon = IconPackIcon(settings.iconPackPackage, drawableName)
                onUpdate {
                    copy(
                        navigationIcons = when (target) {
                            NavigationIconTarget.HOME -> navigationIcons.copy(home = selectedIcon)
                            NavigationIconTarget.APPS -> navigationIcons.copy(apps = selectedIcon)
                            NavigationIconTarget.SETTINGS -> navigationIcons.copy(settings = selectedIcon)
                        }
                    )
                }
                navigationIconTarget = null
            },
            onUseDefault = {
                onUpdate {
                    copy(
                        navigationIcons = when (target) {
                            NavigationIconTarget.HOME -> navigationIcons.copy(home = IconPackIcon())
                            NavigationIconTarget.APPS -> navigationIcons.copy(apps = IconPackIcon())
                            NavigationIconTarget.SETTINGS -> navigationIcons.copy(settings = IconPackIcon())
                        }
                    )
                }
                navigationIconTarget = null
            },
            onDismiss = { navigationIconTarget = null }
        )
    }

    if (showAccentPicker) {
        ColorPickerDialog(
            title           = "Accent Color",
            initialColor    = Color(settings.accentColor),
            onColorSelected = { c -> onUpdate { copy(accentColor = c.toArgb()) } },
            onDismiss       = { showAccentPicker = false }
        )
    }

    if (showDashboardAccentPicker) {
        ColorPickerDialog(
            title = "Dashboard Accent",
            initialColor = Color(settings.dashboardAccentColor),
            onColorSelected = { color -> onUpdate { copy(dashboardAccentColor = color.toArgb()) } },
            onDismiss = { showDashboardAccentPicker = false }
        )
    }

    if (showBgPicker) {
        ColorPickerDialog(
            title           = "Background Color",
            initialColor    = Color(settings.backgroundColor),
            onColorSelected = { c -> 
                onUpdate { 
                    copy(
                        backgroundColor = c.toArgb(),
                        useCustomBackgroundColor = true
                    ) 
                } 
            },
            onDismiss       = { showBgPicker = false }
        )
    }

    if (showGradientEndPicker) {
        ColorPickerDialog(
            title           = "Gradient End Color",
            initialColor    = Color(settings.gradientEndColor),
            onColorSelected = { c -> 
                onUpdate { 
                    copy(
                        gradientEndColor = c.toArgb(),
                        useCustomBackgroundColor = true
                    ) 
                } 
            },
            onDismiss       = { showGradientEndPicker = false }
        )
    }

    if (showFontColorPicker) {
        ColorPickerDialog(
            title           = "Font Color",
            initialColor    = Color(settings.fontColor),
            onColorSelected = { c -> onUpdate { copy(fontColor = c.toArgb()) } },
            onDismiss       = { showFontColorPicker = false }
        )
    }

    if (showDayFontColorPicker) {
        ColorPickerDialog(
            title = "Day Font Color",
            initialColor = Color(settings.dayFontColor),
            onColorSelected = { color -> onUpdate { copy(dayFontColor = color.toArgb()) } },
            onDismiss = { showDayFontColorPicker = false }
        )
    }

    if (showPlateTextColorPicker) {
        ColorPickerDialog(
            title = "Plate Number Color",
            initialColor = Color(settings.licensePlateTextColor),
            onColorSelected = { color -> onUpdate { copy(licensePlateTextColor = color.toArgb()) } },
            onDismiss = { showPlateTextColorPicker = false }
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun launchCompatiblePicker(
    context: android.content.Context,
    launcher: ActivityResultLauncher<Intent>,
    mimeType: String,
    allowMultiple: Boolean = false
) {
    val pickerIntent = compatibleFilePickerIntent(context, mimeType, allowMultiple)
    if (pickerIntent == null) {
        Toast.makeText(context, "No compatible gallery or file manager is installed", Toast.LENGTH_LONG).show()
    } else {
        runCatching { launcher.launch(pickerIntent) }
            .onFailure {
                Toast.makeText(context, "Unable to open the file picker", Toast.LENGTH_LONG).show()
            }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDayMode     = LocalDayMode.current
    val sectionColor  = if (isDayMode) Color(0xFF888888) else Color(0xFF3A3A3A)
    val dividerColor  = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF1E1E1E)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text          = title.uppercase(),
            style         = MaterialTheme.typography.labelSmall,
            color         = sectionColor,
            letterSpacing = 2.sp,
            modifier      = Modifier.padding(top = 16.dp, bottom = 6.dp)
        )
        HorizontalDivider(color = dividerColor)
        Column(modifier = Modifier.fillMaxWidth(), content = content)
        HorizontalDivider(color = dividerColor)
    }
}

@Composable
private fun ProfilePhotoPositionDialog(
    imageUri: String,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Float, Float) -> Unit
) {
    var focusX by remember(imageUri) { mutableFloatStateOf(0f) }
    var focusY by remember(imageUri) { mutableFloatStateOf(0f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("POSITION DRIVER PHOTO", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Drag the photo until the face is centered",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .onSizeChanged { previewSize = it }
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black)
                        .pointerInput(imageUri, previewSize) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (previewSize.width > 0 && previewSize.height > 0) {
                                    focusX = (focusX - dragAmount.x * 2f / previewSize.width).coerceIn(-1f, 1f)
                                    focusY = (focusY - dragAmount.y * 2f / previewSize.height).coerceIn(-1f, 1f)
                                }
                            }
                        }
                        .border(2.dp, accent, androidx.compose.foundation.shape.CircleShape)
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Driver photo positioning preview",
                        contentScale = ContentScale.Crop,
                        alignment = androidx.compose.ui.BiasAlignment(focusX, focusY),
                        modifier = Modifier.matchParentSize()
                    )
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { focusX = 0f; focusY = 0f }) {
                    Text("CENTER", color = accent, fontSize = 9.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { onConfirm(focusX, focusY) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = if (accent.luminance() > 0.45f) Color.Black else Color.White
                        )
                    ) {
                        Text("USE PHOTO", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    sublabel: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable RowScope.() -> Unit
) {
    val isDayMode   = LocalDayMode.current
    val labelColor  = if (isDayMode) MaterialTheme.colorScheme.onBackground else Color(0xFFDDDDDD)
    val subColor    = if (isDayMode) Color(0xFF888888) else Color(0xFF444444)
    val iconTint    = if (isDayMode) Color(0xFF777777) else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor, fontSize = 13.sp)
            if (sublabel.isNotEmpty())
                Text(sublabel, style = MaterialTheme.typography.labelSmall, color = subColor, fontSize = 11.sp)
        }
        content()
    }
}

@Composable
private fun ColumnScope.SettingsButton(
    label: String,
    sublabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    val isDayMode  = LocalDayMode.current
    val labelColor = if (isDayMode) MaterialTheme.colorScheme.onBackground else Color(0xFFDDDDDD)
    val subColor   = if (isDayMode) Color(0xFF888888) else Color(0xFF444444)
    val chevronC   = if (isDayMode) Color(0xFFBBBBBB) else Color(0xFF2A2A2A)
    val iconTint   = if (isDayMode) Color(0xFF777777) else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 0.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor, fontSize = 13.sp)
            if (sublabel.isNotEmpty())
                Text(sublabel, style = MaterialTheme.typography.labelSmall, color = subColor, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = chevronC, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ColumnScope.SettingsDivider() {
    val isDayMode = LocalDayMode.current
    HorizontalDivider(color = if (isDayMode) Color(0xFFDDDDDD) else Color(0xFF141414))
}

@Composable
private fun outlinedFieldColors(accent: Color): androidx.compose.material3.TextFieldColors {
    val isDayMode = LocalDayMode.current
    val textColor = if (isDayMode) MaterialTheme.colorScheme.onBackground else Color.White
    val borderU   = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF2A2A2A)
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = accent,
        unfocusedBorderColor = borderU,
        focusedTextColor     = textColor,
        unfocusedTextColor   = textColor,
        cursorColor          = accent,
        focusedLabelColor    = accent,
        unfocusedLabelColor  = if (isDayMode) Color(0xFF888888) else Color(0xFF666666)
    )
}

@Composable
private fun switchColors(accent: Color): androidx.compose.material3.SwitchColors {
    val isDayMode = LocalDayMode.current
    return SwitchDefaults.colors(
        checkedThumbColor    = if (isDayMode) Color.White else Color.Black,
        checkedTrackColor    = accent,
        uncheckedThumbColor  = if (isDayMode) Color(0xFFBBBBBB) else Color(0xFF888888),
        uncheckedTrackColor  = if (isDayMode) Color(0xFFDDDDDD) else Color(0xFF1E1E1E),
        uncheckedBorderColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF3A3A3A)
    )
}

@Composable
private fun sliderColors(accent: Color): androidx.compose.material3.SliderColors {
    val isDayMode = LocalDayMode.current
    return SliderDefaults.colors(
        thumbColor         = accent,
        activeTrackColor   = accent,
        inactiveTrackColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF2A2A2A)
    )
}


private fun fontDisplayName(font: AppFont): String = when (font) {
    AppFont.POPPINS         -> "Poppins"
    AppFont.INTER           -> "Inter"
    AppFont.MANROPE         -> "Manrope"
    AppFont.BARLOW_CONDENSED -> "Barlow"
    AppFont.OXANIUM         -> "Oxanium"
    AppFont.SYSTEM          -> "System"
    AppFont.JETBRAINS_MONO  -> "JetBrains Mono"
    AppFont.SOURCE_CODE_PRO -> "Source Code Pro"
}
