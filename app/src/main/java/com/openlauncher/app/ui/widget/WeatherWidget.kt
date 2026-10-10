package com.openlauncher.app.ui.widget

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.model.WeatherState

@Composable
fun WeatherWidget(
    state: WeatherState?,
    accent: Color,
    metric: Boolean,
    isDayMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
    val subColor     = if (isDayMode) Color(0xFF888888) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

    BoxWithConstraints(modifier = modifier) {
        if (state == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "⌖", color = accent, fontSize = 28.sp)
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "WAITING FOR LOCATION",
                    color = contentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Set a fallback city in Settings → Weather",
                    color = subColor,
                    fontSize = 8.sp
                )
            }
        } else {
            val forecastDays = when {
                maxWidth >= 250.dp -> 7
                maxWidth >= 180.dp -> 5
                else -> 3
            }
            Column(
                modifier            = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = state.conditionIcon, fontSize = 32.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = state.temperatureDisplay(metric),
                            color = contentColor,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = state.conditionLabel.uppercase(),
                            color = subColor,
                            fontSize = 8.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (state.dailyForecast.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = accent.copy(alpha = 0.22f))
                    Spacer(Modifier.height(9.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        state.dailyForecast.take(forecastDays).forEachIndexed { index, day ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (index == 0) "TODAY" else day.dayLabel,
                                    color = if (index == 0) accent else subColor,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(text = day.conditionIcon, fontSize = 15.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = day.maximumDisplay(metric),
                                    color = contentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = day.minimumDisplay(metric),
                                    color = subColor,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
