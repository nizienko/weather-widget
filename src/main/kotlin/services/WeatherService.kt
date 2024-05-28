package services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.openmeteo.api.Forecast
import com.openmeteo.api.OpenMeteo
import com.openmeteo.api.common.Response
import com.openmeteo.api.common.time.Time
import com.openmeteo.api.common.time.Timezone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import settings.WeatherWidgetSettingsState
import widget.WidgetComponent
import java.time.ZoneId

@Service
class WeatherService(coroutineScope: CoroutineScope) {
    init {
        coroutineScope.launch {
            while (true) {
                if (activeWidget != null) {
                    getRainData()
                }
                delay(20_000)
            }
        }
    }

    private val settings = service<WeatherWidgetSettingsState>()
    private var cachedRainData = loadWeather()

    fun getRainData(): WeatherData {
        when (cachedRainData) {
            is WeatherData.Error -> update()
            is WeatherData.Present -> if (lastUpdate + 5 * 60_000 < System.currentTimeMillis()) {
                update()
            }
        }
        return cachedRainData
    }

    fun update() {
        activeWidget?.let {
            cachedRainData = loadWeather()
            activeWidget?.repaint()
            activeWidget?.revalidate()
        }
    }

    var activeWidget: WidgetComponent? = null

    private var lastUpdate: Long = 0L
    private fun loadWeather(): WeatherData {
        return try {
            WeatherData.Present(WeatherClient(settings).getRainData())
                .also {
                    lastUpdate = System.currentTimeMillis()
                }
        } catch (e: Throwable) {
            val error = buildString {
                append(e::class.java.name)
                if (e.message != null) {
                    append(":\n${e.message}")
                }
            }
            WeatherData.Error(error)
        }
    }
}

sealed interface WeatherData {
    class Present(val data: List<Pair<Time, HourData>>) : WeatherData
    class Error(val message: String) : WeatherData
}

data class HourData(val rain: Double, val temperature: Double, val wind: Double, val windDirection: Double)
class WeatherClient(private val settings: WeatherWidgetSettingsState) {
    private val client = OpenMeteo(settings.latitude, settings.longitude)

    @OptIn(Response.ExperimentalGluedUnitTimeStepValues::class)
    fun getRainData(): List<Pair<Time, HourData>> {
        val forecast = client.forecast {
            hourly = Forecast.Hourly {
                listOf(rain, temperature2m, windspeed10m, winddirection10m)
            }
            forecastDays = 2
            temperatureUnit = settings.temperatureUnit
            windSpeedUnit = settings.windSpeedUnit
            timezone = Timezone.getTimeZone(ZoneId.systemDefault())
        }.getOrThrow()
        val now = System.currentTimeMillis() - 60 * 60_000
        Forecast.Hourly.run {
            val rains = forecast.hourly.getValue(rain).values.filter { (t, _) -> t.time > now }
            val temperature = forecast.hourly.getValue(temperature2m).values.filter { (t, _) -> t.time > now }
            val wind = forecast.hourly.getValue(windspeed10m).values.filter { (t, _) -> t.time > now }
            val windDirection = forecast.hourly.getValue(winddirection10m).values.filter { (t, _) -> t.time > now }
            val merged = rains.map {
                it.key to HourData(
                    it.value!!,
                    temperature[it.key] ?: 0.0,
                    wind[it.key] ?: 0.0,
                    windDirection[it.key] ?: 0.0
                )
            }
            forecast.hourly.getValue(rain).run {
                return merged
                    .toList()
                    .take(service<WeatherWidgetSettingsState>().hours)
            }
        }
    }
}