package com.openlauncher.app.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun QuickControlsWidget(
    accent: Color,
    isDayMode: Boolean = false,
    isEditing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    fun readVolume() =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume

    fun readBrightness() = runCatching {
        AndroidSettings.System.getInt(
            context.contentResolver,
            AndroidSettings.System.SCREEN_BRIGHTNESS
        ) / 255f
    }.getOrDefault(0.5f).coerceIn(0.03f, 1f)

    var volume by remember { mutableFloatStateOf(readVolume()) }
    var brightness by remember { mutableFloatStateOf(readBrightness()) }
    var canWriteBrightness by remember { mutableStateOf(AndroidSettings.System.canWrite(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                volume = readVolume()
            }
        }
        ContextCompat.registerReceiver(
            context,
            volumeReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                brightness = readBrightness()
            }
        }
        context.contentResolver.registerContentObserver(
            AndroidSettings.System.getUriFor(AndroidSettings.System.SCREEN_BRIGHTNESS),
            false,
            brightnessObserver
        )

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canWriteBrightness = AndroidSettings.System.canWrite(context)
                brightness = readBrightness()
                volume = readVolume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            runCatching { context.unregisterReceiver(volumeReceiver) }
            context.contentResolver.unregisterContentObserver(brightnessObserver)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    fun openSettings(action: String, data: Uri? = null) {
        val intent = Intent(action).apply {
            this.data = data
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }

    val primary = if (isDayMode) Color(0xFF171717) else MaterialTheme.colorScheme.onBackground
    val secondary = if (isDayMode) Color(0xFF666666) else primary.copy(alpha = 0.52f)
    val inactiveTrack = if (isDayMode) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 23.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        ControlSlider(
            icon = if (volume <= 0.001f) {
                Icons.AutoMirrored.Filled.VolumeOff
            } else {
                Icons.AutoMirrored.Filled.VolumeUp
            },
            label = "VOLUME",
            value = volume,
            accent = accent,
            primary = primary,
            secondary = secondary,
            inactiveTrack = inactiveTrack,
            enabled = !isEditing,
            onValueChange = { value ->
                volume = value
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (value * maxVolume).toInt().coerceIn(0, maxVolume),
                    0
                )
            }
        )

        ControlSlider(
            icon = Icons.Default.Brightness6,
            label = if (canWriteBrightness) "BRIGHTNESS" else "BRIGHTNESS · ALLOW",
            value = brightness,
            accent = accent,
            primary = primary,
            secondary = secondary,
            inactiveTrack = inactiveTrack,
            enabled = !isEditing,
            onValueChange = { value ->
                brightness = value
                if (canWriteBrightness) {
                    AndroidSettings.System.putInt(
                        context.contentResolver,
                        AndroidSettings.System.SCREEN_BRIGHTNESS_MODE,
                        AndroidSettings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    AndroidSettings.System.putInt(
                        context.contentResolver,
                        AndroidSettings.System.SCREEN_BRIGHTNESS,
                        (value * 255).toInt().coerceIn(8, 255)
                    )
                }
            },
            onValueChangeFinished = {
                if (!canWriteBrightness) {
                    openSettings(
                        AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickControlButton(
                icon = Icons.Default.Wifi,
                label = "WI-FI",
                accent = accent,
                secondary = secondary,
                isEditing = isEditing,
                onClick = {
                    openSettings(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            "android.settings.panel.action.WIFI"
                        else AndroidSettings.ACTION_WIFI_SETTINGS
                    )
                }
            )
            QuickControlButton(
                icon = Icons.Default.Bluetooth,
                label = "BT",
                accent = accent,
                secondary = secondary,
                isEditing = isEditing,
                onClick = { openSettings(AndroidSettings.ACTION_BLUETOOTH_SETTINGS) }
            )
            QuickControlButton(
                icon = Icons.Default.WifiTethering,
                label = "HOTSPOT",
                accent = accent,
                secondary = secondary,
                isEditing = isEditing,
                onClick = { openSettings("android.settings.TETHER_SETTINGS") }
            )
            QuickControlButton(
                icon = Icons.Default.Settings,
                label = "DISPLAY",
                accent = accent,
                secondary = secondary,
                isEditing = isEditing,
                onClick = { openSettings(AndroidSettings.ACTION_DISPLAY_SETTINGS) }
            )
        }
    }
}

@Composable
private fun ControlSlider(
    icon: ImageVector,
    label: String,
    value: Float,
    accent: Color,
    primary: Color,
    secondary: Color,
    inactiveTrack: Color,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = primary.copy(alpha = 0.80f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = secondary,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = inactiveTrack,
                    disabledThumbColor = secondary,
                    disabledActiveTrackColor = secondary.copy(alpha = 0.45f),
                    disabledInactiveTrackColor = inactiveTrack
                )
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = "${(value * 100).toInt()}%",
            color = secondary,
            fontSize = 8.sp,
            modifier = Modifier.width(29.dp)
        )
    }
}

@Composable
private fun QuickControlButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    secondary: Color,
    isEditing: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.then(
            if (!isEditing) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accent.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = accent, modifier = Modifier.size(17.dp))
        }
        Text(
            text = label,
            color = secondary,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
