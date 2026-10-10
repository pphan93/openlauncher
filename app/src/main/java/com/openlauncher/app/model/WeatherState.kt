package com.openlauncher.app.model

import java.text.SimpleDateFormat
import java.util.Locale

data class WeatherState(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val windspeedKmh: Double,
    val isDay: Boolean,
    val dailyForecast: List<DailyForecast> = emptyList()
) {
    // roundToInt, not toInt — truncation displayed 20.9° as 20°
    fun temperatureDisplay(metric: Boolean): String =
        if (metric) "${Math.round(temperatureCelsius)}°C"
        else "${Math.round(celsiusToFahrenheit(temperatureCelsius))}°F"

    val conditionLabel: String get() = wmoCodeToLabel(weatherCode)
    val conditionIcon: String get() = wmoCodeToEmoji(weatherCode, isDay)
}

data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val maximumCelsius: Double,
    val minimumCelsius: Double
) {
    fun maximumDisplay(metric: Boolean): String = temperatureDisplay(maximumCelsius, metric)
    fun minimumDisplay(metric: Boolean): String = temperatureDisplay(minimumCelsius, metric)

    val dayLabel: String get() = runCatching {
        val dateValue = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        dateValue?.let { SimpleDateFormat("EEE", Locale.getDefault()).format(it).uppercase() }
    }.getOrNull() ?: date

    val conditionIcon: String get() = wmoCodeToEmoji(weatherCode, true)
}

private fun celsiusToFahrenheit(c: Double) = c * 9.0 / 5.0 + 32.0

private fun temperatureDisplay(celsius: Double, metric: Boolean): String =
    if (metric) "${Math.round(celsius)}°" else "${Math.round(celsiusToFahrenheit(celsius))}°"

private fun wmoCodeToLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2, 3 -> "Cloudy"
    45, 48 -> "Foggy"
    51, 53, 55 -> "Drizzle"
    61, 63, 65 -> "Rain"
    71, 73, 75 -> "Snow"
    80, 81, 82 -> "Showers"
    95 -> "Thunderstorm"
    96, 99 -> "Hail"
    else -> "Unknown"
}

private fun wmoCodeToEmoji(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1, 2 -> if (isDay) "⛅" else "🌤"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> "🌧️"
    71, 73, 75 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}
