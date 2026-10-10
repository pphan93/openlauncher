package com.openlauncher.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class OpenMeteoResponse(
    @SerializedName("current_weather") val currentWeather: CurrentWeather?,
    @SerializedName("daily") val daily: DailyWeather?
)

data class CurrentWeather(
    @SerializedName("temperature")  val temperature: Double,
    @SerializedName("windspeed")    val windspeed: Double,
    @SerializedName("weathercode")  val weathercode: Int,
    @SerializedName("is_day")       val isDay: Int
)

data class DailyWeather(
    @SerializedName("time") val dates: List<String> = emptyList(),
    @SerializedName("weather_code") val weatherCodes: List<Int> = emptyList(),
    @SerializedName("temperature_2m_max") val maximumTemperatures: List<Double> = emptyList(),
    @SerializedName("temperature_2m_min") val minimumTemperatures: List<Double> = emptyList()
)

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude")         latitude: Double,
        @Query("longitude")        longitude: Double,
        @Query("current_weather")  currentWeather: Boolean = true,
        @Query("daily")            daily: String = "weather_code,temperature_2m_max,temperature_2m_min",
        @Query("forecast_days")    forecastDays: Int = 7,
        @Query("timezone")         timezone: String = "auto",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("windspeed_unit")   windspeedUnit: String = "kmh"
    ): OpenMeteoResponse
}

object WeatherApi {
    private val client = OkHttpClient.Builder().build()

    val service: WeatherApiService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WeatherApiService::class.java)
}
