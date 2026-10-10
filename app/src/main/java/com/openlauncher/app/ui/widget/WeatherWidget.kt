package com.openlauncher.app.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.model.DailyForecast
import com.openlauncher.app.model.WeatherState
import java.util.Locale
import kotlin.math.min

@Composable
fun WeatherWidget(
    state: WeatherState?,
    accent: Color,
    metric: Boolean,
    isDayMode: Boolean = false,
    backgroundOpacity: Float = 1f,
    modifier: Modifier = Modifier
) {
    val panelOpacity = backgroundOpacity.coerceIn(0f, 1f)
    val contentColor = if (isDayMode) Color(0xFF242629) else Color(0xFFE7E9EC)
    val mutedColor = if (isDayMode) Color(0xFF747980) else Color(0xFF8B9097)
    val topPanel = (if (isDayMode) Color(0x0D000000) else Color(0x1AFFFFFF)).multiplyAlpha(panelOpacity)
    val temperaturePanel = (if (isDayMode) Color(0x10000000) else Color(0x22000000)).multiplyAlpha(panelOpacity)

    BoxWithConstraints(modifier = modifier) {
        if (state == null) {
            EmptyWeatherState(accent, contentColor, mutedColor)
            return@BoxWithConstraints
        }

        val compact = maxHeight < 170.dp || maxWidth < 230.dp
        val forecastDays = when {
            maxWidth >= 250.dp -> 7
            maxWidth >= 180.dp -> 5
            else -> 3
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (compact) 0.54f else 0.57f)
                    .background(topPanel),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (compact) 12.dp else 18.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "CURRENTLY",
                        color = mutedColor,
                        fontSize = if (compact) 8.sp else 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = state.conditionLabel.uppercase(Locale.getDefault()),
                        color = contentColor,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.25.sp,
                        maxLines = 1
                    )
                }

                WeatherGlyph(
                    weatherCode = state.weatherCode,
                    isDay = state.isDay,
                    color = contentColor.copy(alpha = 0.9f),
                    accent = accent,
                    modifier = Modifier.size(if (compact) 50.dp else 66.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (compact) 82.dp else 104.dp)
                        .background(temperaturePanel),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = state.temperatureNumber(metric),
                            color = contentColor,
                            fontSize = if (compact) 37.sp else 48.sp,
                            fontWeight = FontWeight.ExtraLight,
                            letterSpacing = (-2).sp
                        )
                        Text(
                            text = "°${if (metric) "C" else "F"}",
                            color = mutedColor,
                            fontSize = if (compact) 10.sp else 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = if (compact) 8.dp else 10.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accent.copy(alpha = 0.55f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (compact) 0.46f else 0.43f)
                    .padding(horizontal = 7.dp, vertical = if (compact) 5.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.dailyForecast.take(forecastDays).forEachIndexed { index, day ->
                    ForecastDay(
                        day = day,
                        isToday = index == 0,
                        metric = metric,
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        accent = accent,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWeatherState(accent: Color, contentColor: Color, mutedColor: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WeatherGlyph(
            weatherCode = 3,
            isDay = true,
            color = mutedColor,
            accent = accent,
            modifier = Modifier.size(46.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "WAITING FOR LOCATION",
            color = contentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Set a fallback city in Settings → Weather",
            color = mutedColor,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ForecastDay(
    day: DailyForecast,
    isToday: Boolean,
    metric: Boolean,
    contentColor: Color,
    mutedColor: Color,
    accent: Color,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (isToday) "TODAY" else day.dayLabel,
            color = if (isToday) accent else mutedColor,
            fontSize = if (compact) 6.sp else 7.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        WeatherGlyph(
            weatherCode = day.weatherCode,
            isDay = true,
            color = if (isToday) contentColor else mutedColor,
            accent = accent,
            modifier = Modifier.size(if (compact) 22.dp else 27.dp)
        )
        Text(
            text = day.maximumDisplay(metric),
            color = contentColor,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold
        )
        if (!compact) {
            Text(
                text = day.minimumDisplay(metric),
                color = mutedColor,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
fun WeatherGlyph(
    weatherCode: Int,
    isDay: Boolean,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = min(size.width, size.height) * 0.065f
        val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val precipitationColor = accent.copy(alpha = 0.95f)

        when (weatherCode) {
            0 -> drawSunOrMoon(isDay, color, strokeWidth)
            45, 48 -> {
                drawCloud(color, stroke)
                drawLine(color, Offset(size.width * 0.20f, size.height * 0.78f), Offset(size.width * 0.80f, size.height * 0.78f), strokeWidth, StrokeCap.Round)
                drawLine(color.copy(alpha = 0.65f), Offset(size.width * 0.29f, size.height * 0.91f), Offset(size.width * 0.70f, size.height * 0.91f), strokeWidth, StrokeCap.Round)
            }
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> {
                drawCloud(color, stroke)
                drawRain(precipitationColor, strokeWidth)
            }
            71, 73, 75 -> {
                drawCloud(color, stroke)
                drawSnow(precipitationColor, strokeWidth * 0.55f)
            }
            95, 96, 99 -> {
                drawCloud(color, stroke)
                val lightning = Path().apply {
                    moveTo(size.width * 0.55f, size.height * 0.62f)
                    lineTo(size.width * 0.40f, size.height * 0.81f)
                    lineTo(size.width * 0.54f, size.height * 0.81f)
                    lineTo(size.width * 0.43f, size.height * 0.98f)
                }
                drawPath(lightning, precipitationColor, style = stroke)
            }
            1, 2 -> {
                drawSunOrMoon(isDay, accent.copy(alpha = 0.7f), strokeWidth * 0.75f, small = true)
                drawCloud(color, stroke)
            }
            else -> drawCloud(color, stroke)
        }
    }
}

private fun DrawScope.drawCloud(color: Color, stroke: Stroke) {
    val cloud = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.66f)
        cubicTo(size.width * 0.05f, size.height * 0.66f, size.width * 0.06f, size.height * 0.43f, size.width * 0.24f, size.height * 0.41f)
        cubicTo(size.width * 0.31f, size.height * 0.16f, size.width * 0.64f, size.height * 0.14f, size.width * 0.73f, size.height * 0.38f)
        cubicTo(size.width * 0.94f, size.height * 0.36f, size.width * 0.98f, size.height * 0.65f, size.width * 0.79f, size.height * 0.68f)
        lineTo(size.width * 0.18f, size.height * 0.68f)
    }
    drawPath(cloud, color, style = stroke)
}

private fun DrawScope.drawSunOrMoon(
    isDay: Boolean,
    color: Color,
    strokeWidth: Float,
    small: Boolean = false
) {
    val glyphCenter = if (small) Offset(size.width * 0.30f, size.height * 0.31f) else center
    val radius = min(size.width, size.height) * if (small) 0.16f else 0.27f
    if (isDay) {
        drawCircle(color, radius, glyphCenter, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            val inner = radius * 1.35f
            val outer = radius * 1.70f
            drawLine(
                color,
                Offset(glyphCenter.x + kotlin.math.cos(angle).toFloat() * inner, glyphCenter.y + kotlin.math.sin(angle).toFloat() * inner),
                Offset(glyphCenter.x + kotlin.math.cos(angle).toFloat() * outer, glyphCenter.y + kotlin.math.sin(angle).toFloat() * outer),
                strokeWidth,
                StrokeCap.Round
            )
        }
    } else {
        drawArc(
            color = color,
            startAngle = 55f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(glyphCenter.x - radius, glyphCenter.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(strokeWidth, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawRain(color: Color, strokeWidth: Float) {
    listOf(0.32f, 0.50f, 0.68f).forEachIndexed { index, x ->
        val y = if (index == 1) 0.72f else 0.68f
        drawLine(
            color,
            Offset(size.width * x, size.height * y),
            Offset(size.width * (x - 0.05f), size.height * (y + 0.18f)),
            strokeWidth,
            StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnow(color: Color, strokeWidth: Float) {
    listOf(Offset(0.36f, 0.80f), Offset(0.64f, 0.80f)).forEach { point ->
        val snowCenter = Offset(size.width * point.x, size.height * point.y)
        val arm = size.minDimension * 0.075f
        drawLine(color, Offset(snowCenter.x - arm, snowCenter.y), Offset(snowCenter.x + arm, snowCenter.y), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(snowCenter.x, snowCenter.y - arm), Offset(snowCenter.x, snowCenter.y + arm), strokeWidth, StrokeCap.Round)
    }
}

private fun WeatherState.temperatureNumber(metric: Boolean): String {
    val value = if (metric) temperatureCelsius else temperatureCelsius * 9.0 / 5.0 + 32.0
    return Math.round(value).toString()
}

private fun Color.multiplyAlpha(multiplier: Float): Color =
    copy(alpha = alpha * multiplier.coerceIn(0f, 1f))
