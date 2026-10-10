package com.openlauncher.app.ui.screen

import android.media.MediaMetadata
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openlauncher.app.R
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.model.WeatherState
import com.openlauncher.app.ui.widget.WeatherGlyph
import com.openlauncher.app.ui.widget.SpeedometerWidget
import com.openlauncher.app.util.LocationData
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
                    modifier = Modifier.weight(1.25f)
                )
                DashboardWeatherClimateCard(
                    state = weather,
                    metric = settings.unitSystem.name == "METRIC",
                    cardColor = visibleCard,
                    primary = primary,
                    secondary = secondary,
                    modifier = Modifier.weight(1.2f)
                )
            }

            PhotoCarouselCard(
                photoUris = settings.dashboardPhotoUris,
                cardColor = visibleCard,
                primary = primary,
                secondary = secondary,
                modifier = Modifier.weight(1.72f).fillMaxHeight()
            )

            VehicleStatusCard(
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

    Column(
        modifier = modifier.fillMaxWidth().clip(DashboardRadius).background(cardColor)
            .clickable(onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state?.albumArt != null) {
                Image(
                    bitmap = state.albumArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(DashboardYellow),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.MusicNote, null, tint = Color.Black) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state?.title?.ifEmpty { "Tap to open music" } ?: "Tap to open music", color = primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state?.artist.orEmpty(), color = secondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", tint = primary, modifier = Modifier.size(19.dp)) }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(DashboardYellow)
            ) { Icon(if (state?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.Black, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.SkipNext, "Next", tint = primary, modifier = Modifier.size(19.dp)) }
        }
        Slider(
            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = {
                dragging = true
                position = (duration * it).toLong()
            },
            onValueChangeFinished = {
                if (duration > 0) onSeek(position)
                dragging = false
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = DashboardYellow,
                activeTrackColor = DashboardYellow,
                inactiveTrackColor = secondary.copy(alpha = 0.18f)
            ),
            modifier = Modifier.fillMaxWidth().height(18.dp)
        )
    }
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
    Column(
        modifier = modifier.fillMaxWidth().clip(DashboardRadius).background(cardColor).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state?.temperatureDisplay(metric) ?: "--°", color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Outdoor temperature", color = secondary, fontSize = 8.sp)
            }
            WeatherGlyph(
                weatherCode = state?.weatherCode ?: 3,
                isDay = state?.isDay ?: true,
                color = primary,
                accent = DashboardYellow,
                modifier = Modifier.size(40.dp)
            )
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
        Text("Climate shown as status only • physical controls remain active", color = secondary, fontSize = 7.sp, maxLines = 1)
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

@Composable
private fun VehicleStatusCard(
    location: LocationData?,
    bearing: Float,
    metric: Boolean,
    cardColor: Color,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    val heading = compassHeading(bearing)

    Column(
        modifier = modifier.clip(DashboardRadius).background(cardColor).padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("2019 CAMRY LE", color = primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("TOYOTA", color = secondary, fontSize = 7.sp, letterSpacing = 1.4.sp)
        }

        Image(
            painter = painterResource(R.drawable.camry_2019_le_black_front),
            contentDescription = "Black 2019 Toyota Camry LE",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().weight(0.95f).padding(vertical = 3.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(82.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            VehicleMetricGauge(Icons.Default.LocalGasStation, "FUEL", "--%", 0f, Color(0xFF79CCFA), primary, secondary)
            VehicleMetricGauge(Icons.Default.Explore, "COMPASS", "${bearing.roundToInt()}° $heading", bearing / 360f, Color(0xFF95E6B2), primary, secondary)
            VehicleMetricGauge(Icons.Default.Route, "RANGE", "--", 0f, Color(0xFF79CCFA), primary, secondary)
        }

        SpeedometerWidget(
            location = location,
            isMetric = metric,
            accent = Color(0xFF79CCFA),
            isDayMode = primary != Color.White,
            modifier = Modifier.fillMaxWidth().weight(1.12f)
        )
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
    secondary: Color
) {
    Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
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
            Icon(icon, null, tint = accent, modifier = Modifier.size(13.dp))
            Text(value, color = primary, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, color = secondary, fontSize = 6.sp)
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
