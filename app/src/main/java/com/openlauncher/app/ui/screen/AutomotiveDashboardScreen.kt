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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.WaterDrop
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.openlauncher.app.ui.theme.Poppins
import com.openlauncher.app.util.LocationData
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
private val DashboardDarkBackground = Color(0xFF090A0A)
private val DashboardDarkCard = Color(0xFF181919)
private val DashboardLightBackground = Color(0xFFF0F1ED)
private val DashboardLightCard = Color.White
private val DashboardRadius = RoundedCornerShape(18.dp)

@Composable
fun AutomotiveDashboardScreen(
    settings: AppSettings,
    weather: WeatherState?,
    nowPlaying: NowPlayingState?,
    location: LocationData?,
    bearing: Float,
    isWifi: Boolean,
    isData: Boolean,
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
    val primary = if (isDayMode) Color(0xFF171817) else Color.White
    val secondary = if (isDayMode) Color(0xFF656963) else Color(0xFFA9ACA6)
    val cardAlpha = settings.widgetOpacity.coerceIn(0f, 1f)
    val visibleCard = cardColor.copy(alpha = cardColor.alpha * cardAlpha)

    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Poppins)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(background)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
        DashboardHeader(
            settings = settings,
            isWifi = isWifi,
            isData = isData,
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
                GreetingCard(settings.profileName, visibleCard, primary, secondary)
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
                    modifier = Modifier.weight(0.95f)
                )
                DashboardWeatherClimateCard(
                    state = weather,
                    metric = settings.unitSystem.name == "METRIC",
                    cardColor = visibleCard,
                    primary = primary,
                    secondary = secondary,
                    modifier = Modifier.weight(1.45f)
                )
            }

            Column(
                modifier = Modifier.weight(1.72f).fillMaxHeight(),
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
                location = location,
                bearing = bearing,
                metric = settings.unitSystem.name == "METRIC",
                cardColor = visibleCard,
                primary = primary,
                secondary = secondary,
                modifier = Modifier.weight(0.98f).fillMaxHeight()
            )
        }
    }
    }
}

@Composable
private fun DashboardHeader(
    settings: AppSettings,
    isWifi: Boolean,
    isData: Boolean,
    primary: Color,
    secondary: Color
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE | MMMM d, yyyy", Locale.getDefault()).format(now) }
    val connection = when {
        settings.androidAutoPackage.isNotEmpty() -> "Android Auto"
        settings.carPlayPackage.isNotEmpty() -> "CarPlay"
        isWifi -> "Wi-Fi connected"
        isData -> "Mobile data"
        else -> "Bluetooth ready"
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(time, color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "$date  •  ${settings.manualWeatherLocationName.ifEmpty { "Current location" }}",
                color = secondary,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        if (isWifi) Icon(Icons.Default.Wifi, "Wi-Fi", tint = secondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Bluetooth, "Bluetooth", tint = secondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        if (settings.profileImageUri.isNotEmpty()) {
            AsyncImage(
                model = settings.profileImageUri,
                contentDescription = "Driver profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape)
            )
        } else {
            Icon(Icons.Default.AccountCircle, "Driver profile", tint = DashboardYellow, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(settings.profileName, color = primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(connection, color = secondary, fontSize = 8.sp)
        }
    }
}

@Composable
private fun GreetingCard(name: String, cardColor: Color, primary: Color, secondary: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(DashboardRadius)
            .background(cardColor)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(DashboardYellow),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.WbSunny, null, tint = Color.Black, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Hi ${name.ifEmpty { "Driver" }},", color = primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("ready for the road?", color = secondary, fontSize = 8.sp)
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

    Column(
        modifier = modifier.fillMaxWidth().clip(DashboardRadius).background(cardColor)
            .clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state?.albumArt != null) {
                Image(
                    bitmap = state.albumArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(8.dp)).background(DashboardYellow),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.MusicNote, null, tint = Color.Black, modifier = Modifier.size(29.dp)) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(state?.title?.ifEmpty { "Tap to open music" } ?: "Tap to open music", color = primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(state?.artist.orEmpty(), color = secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SkipPrevious, "Previous track", tint = primary, modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = {
                    if (duration > 0L) onSeek((safePosition - 10_000L).coerceAtLeast(0L))
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.FastRewind, "Back 10 seconds", tint = primary, modifier = Modifier.size(21.dp))
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(DashboardYellow)
            ) {
                Icon(
                    if (state?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play or pause",
                    tint = Color.Black,
                    modifier = Modifier.size(25.dp)
                )
            }
            IconButton(
                onClick = {
                    if (duration > 0L) onSeek((safePosition + 10_000L).coerceAtMost(duration))
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.FastForward, "Forward 10 seconds", tint = primary, modifier = Modifier.size(21.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SkipNext, "Next track", tint = primary, modifier = Modifier.size(22.dp))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(elapsedLabel, color = secondary, fontSize = 10.sp)
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
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(durationLabel, color = secondary, fontSize = 10.sp)
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
                color = DashboardYellow,
                start = Offset(trackStart, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = trackWidth,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = DashboardYellow,
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
                Text("Forecast unavailable", color = secondary, fontSize = 8.sp)
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
            ClimateStatus(Icons.Default.Air, "FAN", "—", primary, secondary)
            ClimateStatus(Icons.Default.DeviceThermostat, "CABIN", "—°", primary, secondary)
            ClimateStatus(Icons.Default.EventSeat, "DRIVER", "OFF", primary, secondary)
            ClimateStatus(Icons.Default.EventSeat, "PASS.", "OFF", primary, secondary)
        }
    }
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
    Canvas(modifier) {
        val yellow = Color(0xFFFFD83D)
        val cloud = Color(0xFFF0F3F6)
        val cloudShade = Color(0xFFB8C3CE)
        val rain = Color(0xFF68C7FF)
        val snow = Color(0xFFCDEEFF)

        fun drawSun(center: Offset = Offset(size.width * 0.34f, size.height * 0.34f), radius: Float = size.minDimension * 0.17f) {
            drawCircle(yellow, radius, center)
            repeat(8) { index ->
                val angle = Math.toRadians(index * 45.0)
                val inner = radius * 1.35f
                val outer = radius * 1.72f
                drawLine(
                    yellow,
                    Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                    Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                    strokeWidth = size.minDimension * 0.055f,
                    cap = StrokeCap.Round
                )
            }
        }

        fun drawFilledCloud() {
            val shape = Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.69f)
                cubicTo(size.width * 0.05f, size.height * 0.67f, size.width * 0.05f, size.height * 0.48f, size.width * 0.23f, size.height * 0.45f)
                cubicTo(size.width * 0.29f, size.height * 0.22f, size.width * 0.61f, size.height * 0.18f, size.width * 0.72f, size.height * 0.42f)
                cubicTo(size.width * 0.92f, size.height * 0.39f, size.width * 0.99f, size.height * 0.66f, size.width * 0.79f, size.height * 0.70f)
                lineTo(size.width * 0.16f, size.height * 0.70f)
                close()
            }
            drawPath(shape, cloudShade)
            drawPath(shape, cloud.copy(alpha = 0.92f), style = Stroke(size.minDimension * 0.025f, cap = StrokeCap.Round))
        }

        when (weatherCode) {
            0 -> if (isDay) {
                drawSun(center = center, radius = size.minDimension * 0.22f)
            } else {
                drawCircle(Color(0xFFFFF1A8), size.minDimension * 0.25f, center)
                drawCircle(Color(0xFF181919), size.minDimension * 0.23f, Offset(center.x + size.width * 0.12f, center.y - size.height * 0.08f))
            }
            1, 2 -> {
                drawSun()
                drawFilledCloud()
            }
            45, 48 -> {
                drawFilledCloud()
                repeat(2) { index ->
                    val y = size.height * (0.80f + index * 0.12f)
                    drawLine(cloudShade, Offset(size.width * 0.22f, y), Offset(size.width * 0.78f, y), size.minDimension * 0.055f, StrokeCap.Round)
                }
            }
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> {
                drawFilledCloud()
                listOf(0.31f, 0.50f, 0.69f).forEach { x ->
                    drawLine(rain, Offset(size.width * x, size.height * 0.76f), Offset(size.width * (x - 0.045f), size.height * 0.91f), size.minDimension * 0.07f, StrokeCap.Round)
                }
            }
            71, 73, 75 -> {
                drawFilledCloud()
                listOf(0.34f, 0.52f, 0.70f).forEach { x ->
                    drawCircle(snow, size.minDimension * 0.045f, Offset(size.width * x, size.height * 0.84f))
                }
            }
            95, 96, 99 -> {
                drawFilledCloud()
                val bolt = Path().apply {
                    moveTo(size.width * 0.55f, size.height * 0.69f)
                    lineTo(size.width * 0.39f, size.height * 0.86f)
                    lineTo(size.width * 0.53f, size.height * 0.86f)
                    lineTo(size.width * 0.42f, size.height)
                }
                drawPath(bolt, yellow, style = Stroke(size.minDimension * 0.085f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            else -> drawFilledCloud()
        }
    }
}

@Composable
private fun ClimateStatus(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, primary: Color, secondary: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = secondary, modifier = Modifier.size(16.dp))
        Text(value, color = primary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(label, color = secondary, fontSize = 6.sp)
    }
}

@Composable
private fun PhotoCarouselCard(
    photoUris: List<String>,
    cardColor: Color,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
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
                Icon(Icons.Default.PhotoLibrary, null, tint = DashboardYellow, modifier = Modifier.size(44.dp))
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
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var events by remember { mutableStateOf<List<DashboardCalendarEvent>>(emptyList()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        events = if (hasPermission) {
            withContext(Dispatchers.IO) { loadUpcomingCalendarEvents(context) }
        } else {
            emptyList()
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
                .background(Brush.verticalGradient(listOf(Color(0xFF5BE27D), DashboardYellow, Color(0xFFFFBE73))))
        )
        Spacer(Modifier.width(18.dp))
        Icon(Icons.Default.CalendarMonth, null, tint = primary, modifier = Modifier.size(27.dp))
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
                    Text("Your next 7 days are clear", color = secondary, fontSize = 9.sp)
                }
            }
            else -> {
                CalendarEventSummary(events[0], primary, secondary, Modifier.weight(1f))
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
                    color = secondary.copy(alpha = 0.13f)
                )
                events.getOrNull(1)?.let {
                    CalendarEventSummary(it, primary, secondary, Modifier.weight(1f))
                } ?: Column(Modifier.weight(1f)) {
                    Text("Nothing after that", color = primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("Next 7 days", color = secondary, fontSize = 9.sp)
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
    Column(modifier) {
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
    val end = now + 7L * 24L * 60L * 60L * 1_000L
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
                while (cursor.moveToNext() && size < 2) {
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
    location: LocationData?,
    bearing: Float,
    metric: Boolean,
    cardColor: Color,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    val heading = compassHeading(bearing)
    val speed = ((location?.speedMps ?: 0f) * if (metric) 3.6f else 2.23694f).coerceAtLeast(0f)
    val speedUnit = if (metric) "km/h" else "mph"
    val rangeUnit = if (metric) "km" else "mi"

    Column(
        modifier = modifier.clip(DashboardRadius).background(Color(0xFF0B111C)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF080B12), Color(0xFF242A40), Color(0xFF080D17))
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
                drawPath(distantMountains, Color(0xFF151C31).copy(alpha = 0.76f))

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
                drawPath(foregroundValley, Color(0xFF0A1224).copy(alpha = 0.96f))
                drawLine(
                    color = Color(0xFF5863E8).copy(alpha = 0.68f),
                    start = Offset(size.width * 0.44f, size.height * 0.45f),
                    end = Offset(size.width * 0.03f, size.height * 0.91f),
                    strokeWidth = 1.4.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF5863E8).copy(alpha = 0.68f),
                    start = Offset(size.width * 0.56f, size.height * 0.45f),
                    end = Offset(size.width * 0.97f, size.height * 0.91f),
                    strokeWidth = 1.4.dp.toPx()
                )
            }
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 23.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    speed.roundToInt().toString(),
                    color = primary,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 66.sp
                )
                Text(speedUnit, color = secondary, fontSize = 12.sp, fontWeight = FontWeight.Light)
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
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFF1F2F3))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        licensePlate.ifBlank { "ADD PLATE" },
                        color = Color(0xFF101216),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 9.dp)
                    .fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111827).copy(alpha = 0.96f)).padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.WaterDrop, null, tint = Color(0xFF43E6B0), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("--%", color = primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Box(
                    Modifier.padding(horizontal = 10.dp).weight(1f).height(3.dp)
                        .clip(CircleShape).background(secondary.copy(alpha = 0.18f))
                )
                Text("-- $rangeUnit", color = primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)
                .clip(RoundedCornerShape(11.dp)).background(Color(0xFF111722)).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VehicleStatusItem(Icons.Default.Lock, "DOORS", "NO DATA", Color(0xFF7ACBFA), primary, secondary)
            VerticalDivider(Modifier.height(36.dp), color = secondary.copy(alpha = 0.14f))
            VehicleStatusItem(Icons.Outlined.Explore, "COMPASS", "${bearing.roundToInt()}° $heading", Color(0xFFAD80FF), primary, secondary)
            VerticalDivider(Modifier.height(36.dp), color = secondary.copy(alpha = 0.14f))
            VehicleStatusItem(Icons.Default.LocalGasStation, "FUEL SIDE", "LEFT", Color(0xFF55D893), primary, secondary)
        }
    }
}

@Composable
private fun VehicleStatusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
