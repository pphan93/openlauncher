package com.openlauncher.app.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.os.SystemClock
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.core.content.ContextCompat
import com.openlauncher.app.R
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.model.DailyForecast
import com.openlauncher.app.model.WeatherState
import com.openlauncher.app.model.VehicleData
import com.openlauncher.app.model.ClimateTemperatureState
import com.openlauncher.app.model.VehicleClimateState
import com.openlauncher.app.data.DriverProfilePhoto
import com.openlauncher.app.data.LicensePlateRegion
import com.openlauncher.app.ui.theme.toFontFamily
import com.openlauncher.app.util.LocationData
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar as JavaCalendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DashboardYellow = Color(0xFFF2F400)
private val LocalDashboardAccent = staticCompositionLocalOf { DashboardYellow }
private val LocalDashboardAccentContent = staticCompositionLocalOf { Color.Black }
private val DashboardDarkBackground = Color(0xFF090A0A)
private val DashboardDarkCard = Color(0xFF181919)
private val DashboardLightBackground = Color(0xFFF0F1ED)
private val DashboardLightCard = Color.White
private val DashboardRadius = RoundedCornerShape(18.dp)

@Composable
fun AutomotiveDashboardScreen(
    settings: AppSettings,
    weather: WeatherState?,
    weatherError: String?,
    nowPlaying: NowPlayingState?,
    location: LocationData?,
    gpsLocationName: String,
    bearing: Float,
    vehicleData: VehicleData,
    profilePhoto: DriverProfilePhoto,
    isDayMode: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onTapNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (isDayMode) DashboardLightBackground else DashboardDarkBackground
    val cardColor = if (isDayMode) DashboardLightCard else DashboardDarkCard
    val primary = if (isDayMode) Color(settings.dayFontColor) else Color(settings.fontColor)
    val secondary = primary.copy(alpha = if (isDayMode) 0.62f else 0.68f)
    val cardAlpha = settings.widgetOpacity.coerceIn(0f, 1f)
    val visibleCard = cardColor.copy(alpha = cardColor.alpha * cardAlpha)
    val dashboardAccent = Color(settings.dashboardAccentColor)
    val dashboardAccentContent = if (dashboardAccent.luminance() > 0.42f) Color.Black else Color.White
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = settings.appFont.toFontFamily()),
        LocalDashboardAccent provides dashboardAccent,
        LocalDashboardAccentContent provides dashboardAccentContent
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(background)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
        DashboardHeader(
            settings = settings,
            hasGpsFix = location != null,
            gpsLocationName = gpsLocationName,
            profilePhoto = profilePhoto,
            primary = primary,
            secondary = secondary
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(0.92f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMusicCard(
                    state = nowPlaying,
                    cardColor = visibleCard,
                    primary = primary,
                    secondary = secondary,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrev = onPrev,
                    onSeek = onSeek,
                    onOpen = onTapNowPlaying,
                    modifier = Modifier.weight(1.25f)
                )
                DashboardWeatherClimateCard(
                    state = weather,
                    climate = vehicleData.climate,
                    statusMessage = when {
                        weather != null -> null
                        weatherError != null -> "Weather unavailable • check internet"
                        location == null && settings.manualWeatherLatitude == null ->
                            "Waiting for GPS • set a manual location in Settings"
                        else -> "Loading weather…"
                    },
                    metric = settings.unitSystem.name == "METRIC",
                    cardColor = visibleCard,
                    primary = primary,
                    secondary = secondary,
                    modifier = Modifier.weight(1.35f)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.72f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PhotoCarouselCard(
                    photoUris = settings.dashboardPhotoUris,
                    cardColor = visibleCard,
                    primary = primary,
                    secondary = secondary,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                DashboardCalendarCard(
                    cardColor = visibleCard,
                    primary = primary,
                    secondary = secondary,
                    modifier = Modifier.fillMaxWidth().height(84.dp)
                )
            }

            VehicleStatusCard(
                licensePlate = settings.licensePlate,
                licensePlateRegion = settings.licensePlateRegion,
                licensePlateTextColor = Color(settings.licensePlateTextColor),
                location = location,
                bearing = bearing,
                vehicleData = vehicleData,
                drivingAnimationEnabled = settings.drivingAnimationEnabled,
                fullTankRangeKm = settings.fullTankRangeKm,
                metric = settings.unitSystem.name == "METRIC",
                isDayMode = isDayMode,
                themePrimary = primary,
                themeSecondary = secondary,
                modifier = Modifier.weight(0.98f).fillMaxHeight()
            )
        }
    }
    }
}

@Composable
private fun DashboardHeader(
    settings: AppSettings,
    hasGpsFix: Boolean,
    gpsLocationName: String,
    profilePhoto: DriverProfilePhoto,
    primary: Color,
    secondary: Color
) {
    val dashboardAccent = LocalDashboardAccent.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE | MMMM d, yyyy", Locale.getDefault()).format(now) }
    val locationLabel = when {
        hasGpsFix && gpsLocationName.isNotBlank() -> gpsLocationName
        hasGpsFix -> "Locating city…"
        settings.manualWeatherLocationName.isNotBlank() -> settings.manualWeatherLocationName
        else -> "Waiting for GPS"
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(time, color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "$date  •  $locationLabel",
                color = secondary,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
        if (settings.showVehicleNameInStatusBar && settings.vehicleName.isNotBlank()) {
            Text(
                text = settings.vehicleName.trim(),
                color = primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.38f)
            )
        }
        if (profilePhoto.imageUri.isNotEmpty()) {
            AsyncImage(
                model = profilePhoto.imageUri,
                contentDescription = "Driver profile",
                contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(
                    profilePhoto.focusX.coerceIn(-1f, 1f),
                    profilePhoto.focusY.coerceIn(-1f, 1f)
                ),
                modifier = Modifier.align(Alignment.CenterEnd).size(32.dp).clip(CircleShape)
            )
        } else {
            Icon(
                PhIcons.Regular.UserCircle,
                "Driver profile",
                tint = dashboardAccent,
                modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
            )
        }
    }
}

@Composable
private fun DashboardMusicCard(
    state: NowPlayingState?,
    cardColor: Color,
    primary: Color,
    secondary: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dashboardAccent = LocalDashboardAccent.current
    val dashboardAccentContent = LocalDashboardAccentContent.current
    val duration = state?.controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
    var position by remember(state) { mutableLongStateOf(state?.controller?.playbackState?.position ?: 0L) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state?.isPlaying, state?.controller, dragging) {
        while (!dragging) {
            val playback = state?.controller?.playbackState
            position = if (playback != null && playback.state == android.media.session.PlaybackState.STATE_PLAYING) {
                (playback.position + (SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime) * playback.playbackSpeed).toLong()
            } else playback?.position ?: 0L
            delay(500)
        }
    }
    val safePosition = position.coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
    val elapsedLabel = if (duration > 0L) formatPlaybackTime(safePosition) else "--:--"
    val durationLabel = if (duration > 0L) formatPlaybackTime(duration) else "--:--"

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().clip(DashboardRadius).background(cardColor)
            .clickable(onClick = onOpen)
    ) {
        val compact = maxHeight < 190.dp
        val artworkSize = if (compact) 48.dp else 68.dp
        val horizontalPadding = if (compact) 12.dp else 18.dp
        val verticalPadding = if (compact) 10.dp else 16.dp
        val controlSize = if (compact) 30.dp else 34.dp
        val playSize = if (compact) 38.dp else 44.dp
        val controlIconSize = if (compact) 18.dp else 22.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state?.albumArt != null) {
                Image(
                    bitmap = state.albumArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(artworkSize).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(artworkSize).clip(RoundedCornerShape(8.dp)).background(dashboardAccent),
                    contentAlignment = Alignment.Center
                ) { Icon(PhIcons.Regular.MusicNote, null, tint = dashboardAccentContent, modifier = Modifier.size(if (compact) 23.dp else 29.dp)) }
            }
            Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
            Column(Modifier.weight(1f)) {
                Text(state?.title?.ifEmpty { "Tap to open music" } ?: "Tap to open music", color = primary, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) Spacer(Modifier.height(3.dp))
                Text(state?.artist.orEmpty(), color = secondary, fontSize = if (compact) 9.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(controlSize)) {
                Icon(PhIcons.Regular.SkipBack, "Previous track", tint = primary, modifier = Modifier.size(controlIconSize))
            }
            IconButton(
                onClick = {
                    if (duration > 0L) onSeek((safePosition - 10_000L).coerceAtLeast(0L))
                },
                modifier = Modifier.size(controlSize)
            ) {
                Icon(PhIcons.Regular.Rewind, "Back 10 seconds", tint = primary, modifier = Modifier.size(controlIconSize))
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(playSize).clip(CircleShape).background(dashboardAccent)
            ) {
                Icon(
                    if (state?.isPlaying == true) PhIcons.Regular.Pause else PhIcons.Regular.Play,
                    "Play or pause",
                    tint = dashboardAccentContent,
                    modifier = Modifier.size(if (compact) 22.dp else 25.dp)
                )
            }
            IconButton(
                onClick = {
                    if (duration > 0L) onSeek((safePosition + 10_000L).coerceAtMost(duration))
                },
                modifier = Modifier.size(controlSize)
            ) {
                Icon(PhIcons.Regular.FastForward, "Forward 10 seconds", tint = primary, modifier = Modifier.size(controlIconSize))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(controlSize)) {
                Icon(PhIcons.Regular.SkipForward, "Next track", tint = primary, modifier = Modifier.size(controlIconSize))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(elapsedLabel, color = secondary, fontSize = if (compact) 8.sp else 10.sp)
            ReferenceSeekBar(
                progress = if (duration > 0) (safePosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                enabled = duration > 0,
                onProgressChange = {
                    dragging = true
                    position = (duration * it).toLong()
                },
                onProgressFinished = {
                    if (duration > 0) onSeek(position)
                    dragging = false
                },
                modifier = Modifier.weight(1f).padding(horizontal = if (compact) 5.dp else 8.dp)
            )
            Text(durationLabel, color = secondary, fontSize = if (compact) 8.sp else 10.sp)
        }
        }
    }
}

@Composable
private fun ReferenceSeekBar(
    progress: Float,
    enabled: Boolean,
    onProgressChange: (Float) -> Unit,
    onProgressFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dashboardAccent = LocalDashboardAccent.current
    val clampedProgress = progress.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier.height(24.dp).pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun update(x: Float) {
                    onProgressChange((x / size.width.toFloat()).coerceIn(0f, 1f))
                }
                update(down.position.x)
                down.consume()
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) {
                        update(change.position.x)
                        change.consume()
                    }
                } while (change?.pressed == true)
                onProgressFinished()
            }
        }
    ) {
        val centerY = size.height / 2f
        val trackStart = 5.dp.toPx()
        val trackEnd = size.width - 5.dp.toPx()
        val thumbX = trackStart + (trackEnd - trackStart) * clampedProgress
        val trackWidth = 3.dp.toPx()

        drawLine(
            color = Color.Black.copy(alpha = 0.78f),
            start = Offset(trackStart, centerY),
            end = Offset(trackEnd, centerY),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
        if (enabled) {
            drawLine(
                color = dashboardAccent,
                start = Offset(trackStart, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = trackWidth,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = dashboardAccent,
                radius = 5.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    return "%d:%02d".format(Locale.getDefault(), totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun DashboardWeatherClimateCard(
    state: WeatherState?,
    climate: VehicleClimateState?,
    statusMessage: String?,
    metric: Boolean,
    cardColor: Color,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    val upcomingForecast = state?.dailyForecast?.drop(1)?.take(4).orEmpty()

    Column(
        modifier = modifier.fillMaxWidth().clip(DashboardRadius).background(cardColor).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state?.temperatureDisplay(metric) ?: "--°", color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Outdoor temperature", color = secondary, fontSize = 8.sp)
            }
            DashboardWeatherIcon(
                weatherCode = state?.weatherCode ?: 3,
                isDay = state?.isDay ?: true,
                modifier = Modifier.size(46.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (upcomingForecast.isEmpty()) {
                Text(statusMessage ?: "Forecast unavailable", color = secondary, fontSize = 8.sp)
            } else {
                upcomingForecast.forEach { day ->
                    DashboardForecastDay(
                        day = day,
                        metric = metric,
                        primary = primary,
                        secondary = secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(secondary.copy(alpha = 0.09f)).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ClimateStatus(
                PhIcons.Regular.Fan,
                "FAN",
                climate?.fanDisplay() ?: "—",
                primary,
                secondary
            )
            ClimateStatus(
                PhIcons.Regular.Thermometer,
                "TEMP",
                climate?.driverTemperatureDisplay(metric) ?: "—°",
                primary,
                secondary
            )
            ClimateStatus("MODE", climate?.airflowDisplay() ?: "—", primary, secondary) {
                ClimateAirflowIcon(climate, secondary)
            }
            ClimateStatus("DEFROST", climate?.defrostDisplay() ?: "—", primary, secondary) {
                ClimateDefrostIcon(climate, secondary)
            }
            ClimateStatus("A/C", climate?.let { if (it.acOn) "ON" else "OFF" } ?: "—", primary, secondary) {
                Text("A/C", color = secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun VehicleClimateState.fanDisplay(): String = when {
    automatic -> "AUTO"
    fanLevel <= 0 -> "OFF"
    fanLevelMax != null -> "$fanLevel/$fanLevelMax"
    else -> fanLevel.toString()
}

private fun VehicleClimateState.driverTemperatureDisplay(metric: Boolean): String =
    when (driverTemperatureState) {
        ClimateTemperatureState.LOW -> "LO"
        ClimateTemperatureState.HIGH -> "HI"
        ClimateTemperatureState.UNKNOWN -> "—°"
        ClimateTemperatureState.VALUE -> driverTemperatureCelsius?.let { celsius ->
            if (metric) {
                val rounded = (celsius * 2f).roundToInt() / 2f
                if (rounded % 1f == 0f) "${rounded.roundToInt()}°" else "$rounded°"
            } else {
                "${(celsius * 9f / 5f + 32f).roundToInt()}°"
            }
        } ?: "—°"
    }

private fun VehicleClimateState.airflowDisplay(): String = when {
    airUp && airFace && airDown -> "ALL"
    airUp && airFace -> "GLASS + FACE"
    airUp && airDown -> "GLASS + FEET"
    airFace && airDown -> "FACE + FEET"
    airUp -> "WINDSHIELD"
    airFace -> "FACE"
    airDown -> "FEET"
    fanLevel <= 0 -> "OFF"
    else -> "—"
}

private fun VehicleClimateState.defrostDisplay(): String = when {
    frontDefrostOn && rearDefrostOn -> "BOTH"
    frontDefrostOn -> "FRONT"
    rearDefrostOn -> "REAR"
    else -> "OFF"
}

@Composable
private fun DashboardForecastDay(
    day: DailyForecast,
    metric: Boolean,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(9.dp))
            .background(secondary.copy(alpha = 0.07f)).padding(horizontal = 3.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(day.dayLabel, color = secondary, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        DashboardWeatherIcon(
            weatherCode = day.weatherCode,
            isDay = true,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(day.maximumDisplay(metric), color = primary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(day.minimumDisplay(metric), color = secondary, fontSize = 7.sp)
        }
    }
}

@Composable
private fun DashboardWeatherIcon(
    weatherCode: Int,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector
    val tint: Color
    when (weatherCode) {
        0 -> {
            icon = if (isDay) PhIcons.Regular.Sun else PhIcons.Regular.Moon
            tint = Color(0xFFFFD83D)
        }
        1, 2 -> {
            icon = if (isDay) PhIcons.Regular.CloudSun else PhIcons.Regular.CloudMoon
            tint = Color(0xFFFFD83D)
        }
        45, 48 -> {
            icon = PhIcons.Regular.CloudFog
            tint = Color(0xFFB8C3CE)
        }
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> {
            icon = PhIcons.Regular.CloudRain
            tint = Color(0xFF68C7FF)
        }
        71, 73, 75, 77, 85, 86 -> {
            icon = PhIcons.Regular.CloudSnow
            tint = Color(0xFFCDEEFF)
        }
        95, 96, 99 -> {
            icon = PhIcons.Regular.CloudLightning
            tint = Color(0xFFFFD83D)
        }
        else -> {
            icon = PhIcons.Regular.Cloud
            tint = Color(0xFFB8C3CE)
        }
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

@Composable
private fun ClimateStatus(icon: ImageVector, label: String, value: String, primary: Color, secondary: Color) {
    ClimateStatus(label, value, primary, secondary) {
        Icon(icon, null, tint = secondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ClimateStatus(
    label: String,
    value: String,
    primary: Color,
    secondary: Color,
    iconContent: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.height(16.dp), contentAlignment = Alignment.Center) {
            iconContent()
        }
        Text(value, color = primary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(label, color = secondary, fontSize = 6.sp)
    }
}

@Composable
private fun ClimateAirflowIcon(climate: VehicleClimateState?, tint: Color) {
    val showFace = climate?.airFace == true
    val showFeet = climate?.airDown == true
    val showWindshield = climate?.airUp == true
    if (showWindshield && showFeet) {
        Box(
            modifier = Modifier.size(width = 25.dp, height = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_climate_windshield_feet),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint
            )
        }
        return
    }
    if (!showFace && !showFeet && !showWindshield) {
        Box(
            modifier = Modifier.size(width = 25.dp, height = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_climate_airflow_off),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint
            )
        }
        return
    }
    if (showFace || showFeet) {
        val icon = when {
            showFace && showFeet -> R.drawable.ic_climate_seat_adjustment
            showFace -> R.drawable.ic_climate_seat_face
            else -> R.drawable.ic_climate_seat_feet
        }
        Box(
            modifier = Modifier.size(width = 25.dp, height = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint
            )
        }
        return
    }

    Canvas(modifier = Modifier.size(width = 25.dp, height = 16.dp)) {
        val lineWidth = 1.45.dp.toPx()
        val lineStyle = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun glassAir(x: Float) {
            val wave = Path().apply {
                moveTo(size.width * x, size.height * 0.48f)
                cubicTo(
                    size.width * (x - 0.06f), size.height * 0.40f,
                    size.width * (x + 0.07f), size.height * 0.32f,
                    size.width * x, size.height * 0.23f
                )
                cubicTo(
                    size.width * (x - 0.05f), size.height * 0.16f,
                    size.width * (x + 0.05f), size.height * 0.10f,
                    size.width * x, size.height * 0.04f
                )
            }
            drawPath(wave, tint, style = lineStyle)
        }

        // Passenger and seat silhouette follows the supplied HVAC pictograms.
        drawCircle(
            tint,
            radius = size.height * 0.105f,
            center = Offset(size.width * 0.79f, size.height * 0.13f),
            style = lineStyle
        )
        val passenger = Path().apply {
            moveTo(size.width * 0.73f, size.height * 0.31f)
            cubicTo(
                size.width * 0.68f, size.height * 0.43f,
                size.width * 0.66f, size.height * 0.55f,
                size.width * 0.70f, size.height * 0.65f
            )
            lineTo(size.width * 0.83f, size.height * 0.70f)
            lineTo(size.width * 0.92f, size.height * 0.94f)
        }
        drawPath(passenger, tint, style = lineStyle)
        val seat = Path().apply {
            moveTo(size.width * 0.64f, size.height * 0.39f)
            lineTo(size.width * 0.64f, size.height * 0.68f)
            cubicTo(
                size.width * 0.66f, size.height * 0.79f,
                size.width * 0.75f, size.height * 0.80f,
                size.width * 0.83f, size.height * 0.80f
            )
            lineTo(size.width * 0.94f, size.height * 0.95f)
        }
        drawPath(seat, tint, style = lineStyle)

        if (showWindshield) {
            val windshield = Path().apply {
                moveTo(size.width * 0.07f, size.height * 0.43f)
                lineTo(size.width * 0.14f, size.height * 0.10f)
                quadraticTo(
                    size.width * 0.36f, size.height * 0.01f,
                    size.width * 0.56f, size.height * 0.11f
                )
                lineTo(size.width * 0.62f, size.height * 0.43f)
            }
            drawPath(windshield, tint, style = lineStyle)
            glassAir(0.24f)
            glassAir(0.38f)
            glassAir(0.52f)
        }
    }
}

@Composable
private fun ClimateDefrostIcon(climate: VehicleClimateState?, tint: Color) {
    val front = climate?.frontDefrostOn == true
    val rear = climate?.rearDefrostOn == true

    if (front && rear) {
        Row(
            modifier = Modifier.size(width = 25.dp, height = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_climate_defrost_front),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tint
            )
            Spacer(Modifier.width(1.dp))
            Icon(
                painter = painterResource(R.drawable.ic_climate_defrost_rear),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tint
            )
        }
        return
    }
    if (rear && !front) {
        Icon(
            painter = painterResource(R.drawable.ic_climate_defrost_rear),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint
        )
        return
    }
    Icon(
        painter = painterResource(R.drawable.ic_climate_defrost_front),
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = tint
    )
}

@Composable
private fun PhotoCarouselCard(
    photoUris: List<String>,
    cardColor: Color,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    val dashboardAccent = LocalDashboardAccent.current
    var index by remember(photoUris) { mutableIntStateOf(0) }
    LaunchedEffect(photoUris) {
        while (photoUris.size > 1) {
            delay(8_000)
            index = (index + 1) % photoUris.size
        }
    }
    Box(
        modifier = modifier.clip(DashboardRadius).background(cardColor)
            .clickable(enabled = photoUris.size > 1) { index = (index + 1) % photoUris.size }
    ) {
        if (photoUris.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(PhIcons.Regular.ImagesSquare, null, tint = dashboardAccent, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text("PHOTO JOURNEY", color = primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Choose local, Google Photos, or Drive images in Settings", color = secondary, fontSize = 8.sp)
            }
        } else {
            Crossfade(targetState = photoUris[index.coerceIn(0, photoUris.lastIndex)], label = "dashboard_photo") { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Dashboard photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.46f)).padding(14.dp)
            ) {
                Column {
                    Text("YOUR JOURNEY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Photo ${index + 1} of ${photoUris.size} • tap to advance", color = Color.White.copy(alpha = 0.68f), fontSize = 8.sp)
                }
            }
        }
    }
}

private data class DashboardCalendarEvent(
    val title: String,
    val startsAt: Long
)

@Composable
private fun DashboardCalendarCard(
    cardColor: Color,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dashboardAccent = LocalDashboardAccent.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var events by remember { mutableStateOf<List<DashboardCalendarEvent>>(emptyList()) }
    val calendarPageCount = maxOf(1, (events.size + CALENDAR_EVENTS_PER_PAGE - 1) / CALENDAR_EVENTS_PER_PAGE)
    val calendarPagerState = rememberPagerState(pageCount = { calendarPageCount })
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            events = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            events = withContext(Dispatchers.IO) { loadUpcomingCalendarEvents(context) }
            delay(CALENDAR_REFRESH_INTERVAL_MS)
        }
    }

    Row(
        modifier = modifier.clip(DashboardRadius).background(cardColor)
            .clickable(enabled = !hasPermission) {
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(3.dp).fillMaxHeight().clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF5BE27D), dashboardAccent, Color(0xFFFFBE73))))
        )
        Spacer(Modifier.width(18.dp))
        Icon(PhIcons.Regular.Calendar, null, tint = primary, modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(18.dp))

        when {
            !hasPermission -> {
                Column(Modifier.weight(1f)) {
                    Text("Connect Calendar", color = primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tap to show your next appointments", color = secondary, fontSize = 9.sp)
                }
            }
            events.isEmpty() -> {
                Column(Modifier.weight(1f)) {
                    Text("No upcoming events", color = primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your next 30 days are clear", color = secondary, fontSize = 9.sp)
                }
            }
            else -> {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    HorizontalPager(
                        state = calendarPagerState,
                        userScrollEnabled = calendarPageCount > 1,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val firstIndex = page * CALENDAR_EVENTS_PER_PAGE
                        Row(
                            modifier = Modifier.fillMaxSize()
                                .padding(end = if (calendarPageCount > 1) 36.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            events.getOrNull(firstIndex)?.let { event ->
                                CalendarEventSummary(event, primary, secondary, Modifier.weight(1f))
                            }
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
                                color = secondary.copy(alpha = 0.13f)
                            )
                            events.getOrNull(firstIndex + 1)?.let { event ->
                                CalendarEventSummary(event, primary, secondary, Modifier.weight(1f))
                            } ?: Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("No more events", color = primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("Next 30 days", color = secondary, fontSize = 9.sp)
                            }
                        }
                    }
                    if (calendarPageCount > 1) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                PhIcons.Regular.HandSwipeLeft,
                                contentDescription = "Swipe for more events",
                                tint = secondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "${calendarPagerState.currentPage + 1}/$calendarPageCount",
                                color = secondary,
                                fontSize = 7.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEventSummary(
    event: DashboardCalendarEvent,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(
            event.title,
            color = primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(calendarEventTimeLabel(event.startsAt), color = secondary, fontSize = 9.sp, maxLines = 1)
    }
}

private fun loadUpcomingCalendarEvents(context: Context): List<DashboardCalendarEvent> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
        PackageManager.PERMISSION_GRANTED
    ) return emptyList()

    val now = System.currentTimeMillis()
    val end = now + 30L * 24L * 60L * 60L * 1_000L
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(now.toString())
        .appendPath(end.toString())
        .build()
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN
    )

    return runCatching {
        context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Instances.BEGIN} >= ?",
            arrayOf(now.toString()),
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            buildList {
                while (cursor.moveToNext() && size < MAX_UPCOMING_CALENDAR_EVENTS) {
                    add(
                        DashboardCalendarEvent(
                            title = cursor.getString(titleIndex)?.ifBlank { "Calendar event" } ?: "Calendar event",
                            startsAt = cursor.getLong(beginIndex)
                        )
                    )
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())
}

private const val CALENDAR_EVENTS_PER_PAGE = 2
private const val MAX_UPCOMING_CALENDAR_EVENTS = 50
private const val CALENDAR_REFRESH_INTERVAL_MS = 60_000L

private fun calendarEventTimeLabel(startsAt: Long): String {
    val now = JavaCalendar.getInstance()
    val event = JavaCalendar.getInstance().apply { timeInMillis = startsAt }
    val tomorrow = JavaCalendar.getInstance().apply { add(JavaCalendar.DAY_OF_YEAR, 1) }
    val day = when {
        now.get(JavaCalendar.YEAR) == event.get(JavaCalendar.YEAR) &&
            now.get(JavaCalendar.DAY_OF_YEAR) == event.get(JavaCalendar.DAY_OF_YEAR) -> "Today"
        tomorrow.get(JavaCalendar.YEAR) == event.get(JavaCalendar.YEAR) &&
            tomorrow.get(JavaCalendar.DAY_OF_YEAR) == event.get(JavaCalendar.DAY_OF_YEAR) -> "Tomorrow"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(startsAt))
    }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(startsAt))
    return "$day  •  $time"
}

@Composable
private fun VehicleStatusCard(
    licensePlate: String,
    licensePlateRegion: LicensePlateRegion,
    licensePlateTextColor: Color,
    location: LocationData?,
    bearing: Float,
    vehicleData: VehicleData,
    drivingAnimationEnabled: Boolean,
    fullTankRangeKm: Float,
    metric: Boolean,
    isDayMode: Boolean,
    themePrimary: Color,
    themeSecondary: Color,
    modifier: Modifier = Modifier
) {
    val heading = compassHeading(bearing)
    val speed = ((location?.speedMps ?: 0f) * if (metric) 3.6f else 2.23694f).coerceAtLeast(0f)
    val speedUnit = if (metric) "km/h" else "mph"
    val rangeUnit = if (metric) "km" else "mi"
    val fuelPercent = (
        vehicleData.fuelPercent ?: vehicleData.rangeKm?.let { rangeKm ->
            rangeKm / fullTankRangeKm * 100f
        }
    )?.coerceIn(0f, 100f)
    val displayedRange = vehicleData.rangeKm?.let { if (metric) it else it * 0.621371f }
    val doorStatus = vehicleData.doors?.let { if (it.anyOpen) "OPEN" else "CLOSED" } ?: "NO DATA"
    val doorIcon = if (vehicleData.doors?.anyOpen == true) PhIcons.Regular.LockOpen else PhIcons.Regular.Lock
    val vehiclePrimary = themePrimary
    val vehicleSecondary = themeSecondary
    val vehicleBackground = if (isDayMode) Color(0xFFE8EEF6) else Color(0xFF0B111C)
    val sceneGradient = if (isDayMode) {
        listOf(Color(0xFFF7FBFF), Color(0xFFDCE9F7), Color(0xFFB8C9DD))
    } else {
        listOf(Color(0xFF080B12), Color(0xFF242A40), Color(0xFF080D17))
    }
    val distantMountainColor = if (isDayMode) Color(0xFFC8D5E4) else Color(0xFF151C31).copy(alpha = 0.76f)
    val valleyColor = if (isDayMode) Color(0xFFAAB9CC) else Color(0xFF0A1224).copy(alpha = 0.96f)
    val laneColor = if (isDayMode) Color(0xFF4D5FE4) else Color(0xFF5863E8)
    val telemetryBackground = if (isDayMode) Color(0xFFF6F9FD).copy(alpha = 0.96f) else Color(0xFF111827).copy(alpha = 0.96f)
    val statusBackground = if (isDayMode) Color(0xFFF4F7FB) else Color(0xFF111722)
    val fuelAccent = if (isDayMode) Color(0xFF16865F) else Color(0xFF43E6B0)
    val doorAccent = if (isDayMode) Color(0xFF1976A8) else Color(0xFF7ACBFA)
    val compassAccent = if (isDayMode) Color(0xFF7048CF) else Color(0xFFAD80FF)
    val fuelSideAccent = if (isDayMode) Color(0xFF16865F) else Color(0xFF55D893)
    val licensePlateDrawable = when (licensePlateRegion) {
        LicensePlateRegion.ALBERTA -> R.drawable.license_plate_alberta
        LicensePlateRegion.ONTARIO -> R.drawable.license_plate_ontario
        LicensePlateRegion.BRITISH_COLUMBIA -> R.drawable.license_plate_british_columbia
        LicensePlateRegion.JAPAN_GLOW -> R.drawable.license_plate_japan_glow
    }
    val isJapanGlowPlate = licensePlateRegion == LicensePlateRegion.JAPAN_GLOW
    val roadCycleMillis = (1_450f - speed.coerceAtMost(90f) * 8f).roundToInt().coerceAtLeast(650)
    val roadPhase = if (drivingAnimationEnabled) {
        val drivingAnimation = rememberInfiniteTransition(label = "vehicle_driving")
        val animatedRoadPhase by drivingAnimation.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = roadCycleMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "road_motion"
        )
        animatedRoadPhase
    } else {
        0f
    }

    Column(
        modifier = modifier.clip(DashboardRadius).background(vehicleBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .background(
                    Brush.verticalGradient(
                        sceneGradient
                    )
                )
        ) {
            Canvas(Modifier.matchParentSize()) {
                val horizon = size.height * 0.35f
                val distantMountains = Path().apply {
                    moveTo(0f, horizon - size.height * 0.025f)
                    cubicTo(
                        size.width * 0.18f, horizon,
                        size.width * 0.34f, horizon + size.height * 0.08f,
                        size.width * 0.48f, horizon + size.height * 0.105f
                    )
                    cubicTo(
                        size.width * 0.53f, horizon + size.height * 0.11f,
                        size.width * 0.70f, horizon + size.height * 0.025f,
                        size.width, horizon - size.height * 0.02f
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(distantMountains, distantMountainColor)

                val foregroundValley = Path().apply {
                    moveTo(0f, horizon + size.height * 0.015f)
                    cubicTo(
                        size.width * 0.17f, horizon + size.height * 0.045f,
                        size.width * 0.34f, horizon + size.height * 0.12f,
                        size.width * 0.47f, horizon + size.height * 0.155f
                    )
                    cubicTo(
                        size.width * 0.50f, horizon + size.height * 0.165f,
                        size.width * 0.53f, horizon + size.height * 0.165f,
                        size.width * 0.57f, horizon + size.height * 0.145f
                    )
                    cubicTo(
                        size.width * 0.70f, horizon + size.height * 0.075f,
                        size.width * 0.85f, horizon + size.height * 0.04f,
                        size.width, horizon + size.height * 0.01f
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(foregroundValley, valleyColor)
                drawLine(
                    color = laneColor.copy(alpha = if (isDayMode) 0.78f else 0.68f),
                    start = Offset(size.width * 0.44f, size.height * 0.45f),
                    end = Offset(size.width * 0.03f, size.height * 0.91f),
                    strokeWidth = 1.4.dp.toPx()
                )
                drawLine(
                    color = laneColor.copy(alpha = if (isDayMode) 0.78f else 0.68f),
                    start = Offset(size.width * 0.56f, size.height * 0.45f),
                    end = Offset(size.width * 0.97f, size.height * 0.91f),
                    strokeWidth = 1.4.dp.toPx()
                )

                // Perspective markers stream toward the viewer while the car stays centered.
                // Squaring the phase keeps distant markers slow and makes nearby ones rush past.
                if (drivingAnimationEnabled) {
                    repeat(7) { marker ->
                        val phase = (marker / 7f + roadPhase) % 1f
                        val perspective = phase * phase
                        val startY = size.height * (0.46f + perspective * 0.45f)
                        val markerLength = size.height * (0.012f + perspective * 0.055f)
                        val markerWidth = (0.8f + perspective * 3.2f).dp.toPx()
                        drawLine(
                            color = laneColor.copy(alpha = 0.3f + perspective * 0.55f),
                            start = Offset(size.width * 0.5f, startY),
                            end = Offset(size.width * 0.5f, (startY + markerLength).coerceAtMost(size.height * 0.94f)),
                            strokeWidth = markerWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    // Air trails peel away from the car toward both edges. Keeping the strokes
                    // outside the center preserves speed and plate readability.
                    repeat(6) { trail ->
                        val phase = (trail / 6f + roadPhase * 1.25f) % 1f
                        val perspective = phase * phase
                        val y = size.height * (0.47f + perspective * 0.38f)
                        val innerOffset = size.width * (0.13f + perspective * 0.08f)
                        val trailLength = size.width * (0.025f + perspective * 0.09f)
                        val trailColor = laneColor.copy(alpha = 0.1f + perspective * 0.28f)
                        val trailWidth = (0.45f + perspective * 1.25f).dp.toPx()

                        drawLine(
                            color = trailColor,
                            start = Offset(size.width * 0.5f - innerOffset, y),
                            end = Offset(
                                size.width * 0.5f - innerOffset - trailLength,
                                y + trailLength * 0.28f
                            ),
                            strokeWidth = trailWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = trailColor,
                            start = Offset(size.width * 0.5f + innerOffset, y),
                            end = Offset(
                                size.width * 0.5f + innerOffset + trailLength,
                                y + trailLength * 0.28f
                            ),
                            strokeWidth = trailWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 23.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    speed.roundToInt().toString(),
                    color = vehiclePrimary,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 66.sp
                )
                Text(speedUnit, color = vehicleSecondary, fontSize = 12.sp, fontWeight = FontWeight.Light)
            }

            Box(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-42).dp)
                    .fillMaxWidth(0.86f).aspectRatio(1.5f)
            ) {
                Image(
                    painter = painterResource(R.drawable.camry_2019_le_black_rear),
                    contentDescription = "Rear view of black 2019 Toyota Camry LE",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.align(Alignment.Center).offset(y = (-4).dp)
                        .width(60.dp).height(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(licensePlateDrawable),
                        contentDescription = "${licensePlateRegion.displayName} license plate",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize()
                    )
                    Text(
                        licensePlate.ifBlank { "ADD PLATE" },
                        color = licensePlateTextColor,
                        fontSize = if (isJapanGlowPlate) 10.sp else 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.45.sp,
                        style = LocalTextStyle.current.copy(
                            shadow = if (isJapanGlowPlate) {
                                Shadow(
                                    color = Color(0xFF42FFB8),
                                    offset = Offset.Zero,
                                    blurRadius = 8f
                                )
                            } else null
                        ),
                        modifier = Modifier.offset(y = if (isJapanGlowPlate) 5.dp else (-1).dp),
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 9.dp)
                    .fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(telemetryBackground).padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(PhIcons.Regular.Drop, null, tint = fuelAccent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    fuelPercent?.roundToInt()?.let { "$it%" } ?: "--%",
                    color = vehiclePrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    Modifier.padding(horizontal = 10.dp).weight(1f).height(3.dp)
                        .clip(CircleShape).background(vehicleSecondary.copy(alpha = 0.24f))
                ) {
                    if (fuelPercent != null) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(fuelPercent / 100f)
                                .background(fuelAccent)
                        )
                    }
                }
                Text(
                    displayedRange?.roundToInt()?.let { "$it $rangeUnit" } ?: "-- $rangeUnit",
                    color = vehiclePrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)
                .clip(RoundedCornerShape(11.dp)).background(statusBackground).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VehicleStatusItem(doorIcon, "DOORS", doorStatus, doorAccent, vehiclePrimary, vehicleSecondary)
            VerticalDivider(Modifier.height(36.dp), color = vehicleSecondary.copy(alpha = 0.22f))
            VehicleStatusItem(PhIcons.Regular.Compass, "COMPASS", "${bearing.roundToInt()}° $heading", compassAccent, vehiclePrimary, vehicleSecondary)
            VerticalDivider(Modifier.height(36.dp), color = vehicleSecondary.copy(alpha = 0.22f))
            VehicleStatusItem(PhIcons.Regular.GasPump, "FUEL SIDE", "LEFT", fuelSideAccent, vehiclePrimary, vehicleSecondary)
        }
    }
}

@Composable
private fun VehicleStatusItem(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    primary: Color,
    secondary: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Icon(icon, null, tint = primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = primary, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun VehicleMetricGauge(
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float,
    accent: Color,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val inset = 7.dp.toPx()
            val gaugeSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            drawArc(
                color = secondary.copy(alpha = 0.16f),
                startAngle = 12f,
                sweepAngle = 156f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = gaugeSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    color = accent,
                    startAngle = 12f,
                    sweepAngle = 156f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = gaugeSize,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(12.dp))
            Text(value, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(label, color = secondary, fontSize = 7.sp)
        }
    }
}

@Composable
private fun VehicleSpeedometerGauge(
    location: LocationData?,
    metric: Boolean,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    val speed = ((location?.speedMps ?: 0f) * if (metric) 3.6f else 2.23694f).coerceAtLeast(0f)
    val maxSpeed = if (metric) 200f else 120f
    val accent = Color(0xFF79CCFA)
    val progress = (speed / maxSpeed).coerceIn(0f, 1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.51f)
            val radius = minOf(size.width * 0.46f, size.height * 0.46f)
            val startAngle = 150f
            val sweepAngle = 240f
            val bounds = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)

            drawArc(
                color = secondary.copy(alpha = 0.18f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = bounds,
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    color = accent.copy(alpha = 0.18f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = bounds,
                    style = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = bounds,
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            val tickCount = 50
            for (index in 0..tickCount) {
                val fraction = index / tickCount.toFloat()
                val angle = startAngle + sweepAngle * fraction
                val radians = Math.toRadians(angle.toDouble())
                val isMajor = index % 5 == 0
                val outerRadius = radius - 7.dp.toPx()
                val innerRadius = outerRadius - if (isMajor) 13.dp.toPx() else 6.dp.toPx()
                val tickColor = if (fraction <= progress && progress > 0f) accent else secondary.copy(alpha = if (isMajor) 0.32f else 0.18f)
                drawLine(
                    color = tickColor,
                    start = Offset(
                        center.x + (outerRadius * cos(radians)).toFloat(),
                        center.y + (outerRadius * sin(radians)).toFloat()
                    ),
                    end = Offset(
                        center.x + (innerRadius * cos(radians)).toFloat(),
                        center.y + (innerRadius * sin(radians)).toFloat()
                    ),
                    strokeWidth = if (isMajor) 1.7.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = primary.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 8.sp.toPx()
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            val labelRadius = radius * 0.66f
            for (step in 1..9) {
                val value = maxSpeed * step / 10f
                val angle = startAngle + sweepAngle * (value / maxSpeed)
                val radians = Math.toRadians(angle.toDouble())
                val x = center.x + (labelRadius * cos(radians)).toFloat()
                val y = center.y + (labelRadius * sin(radians)).toFloat() - (labelPaint.ascent() + labelPaint.descent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(value.roundToInt().toString(), x, y, labelPaint)
            }

            drawCircle(
                color = secondary.copy(alpha = 0.16f),
                radius = radius * 0.31f,
                center = center,
                style = Stroke(1.dp.toPx())
            )
            drawArc(
                color = accent,
                startAngle = 65f,
                sweepAngle = 50f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.54f, center.y - radius * 0.54f),
                size = Size(radius * 1.08f, radius * 1.08f),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                speed.roundToInt().toString(),
                color = primary,
                fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(if (metric) "km/h" else "mph", color = secondary, fontSize = 8.sp)
        }
    }
}

private fun compassHeading(degrees: Float): String = when (((degrees % 360f) + 360f) % 360f) {
    in 22.5f..<67.5f -> "NE"
    in 67.5f..<112.5f -> "E"
    in 112.5f..<157.5f -> "SE"
    in 157.5f..<202.5f -> "S"
    in 202.5f..<247.5f -> "SW"
    in 247.5f..<292.5f -> "W"
    in 292.5f..<337.5f -> "NW"
    else -> "N"
}
