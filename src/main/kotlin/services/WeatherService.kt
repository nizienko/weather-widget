package services

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.openmeteo.api.Forecast
import com.openmeteo.api.OpenMeteo
import com.openmeteo.api.common.Response
import com.openmeteo.api.common.time.Time
import com.openmeteo.api.common.time.Timezone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.WeatherData.*
import settings.WeatherWidgetSettingsState
import widget.WidgetComponent
import java.time.ZoneId

@Service
class WeatherService(val scope: CoroutineScope) {
    init {
        scope.launch {
            withContext(Dispatchers.Default) {
                while (true) {
                    if (activeWidget != null) {
                        getRainData()
                    }
                    delay(20_000)
                }
            }
        }
    }

    private val settings = service<WeatherWidgetSettingsState>()
    private var cachedRainData: WeatherData = NotPresent

    fun getRainData(): WeatherData {
        if (lastAttempt + 30_000 > System.currentTimeMillis()) return cachedRainData
        when (cachedRainData) {
            is NotPresent -> scope.launch { update() }
            is Error -> scope.launch { update() }
            is Present -> if (lastUpdate + 5 * 60_000 < System.currentTimeMillis()) {
                scope.launch { update() }
            }
        }
        return cachedRainData
    }

    suspend fun update() {
        activeWidget?.let {
            cachedRainData = loadWeather()
            withContext(Dispatchers.EDT) {
                activeWidget?.revalidate()
                activeWidget?.repaint()
                activeWidget?.updateTooltip()
            }
        }
    }

    var activeWidget: WidgetComponent? = null

    private var lastUpdate: Long = 0L
    private var lastAttempt: Long = 0L
    private suspend fun loadWeather(): WeatherData = withContext(Dispatchers.IO) {
        return@withContext try {
            val data = WeatherClient(settings).getRainData()
            Present(data).also {
                lastUpdate = System.currentTimeMillis()
                lastAttempt = System.currentTimeMillis()
            }
        } catch (e: Throwable) {
            lastAttempt = System.currentTimeMillis()
            val error = buildString {
                append(e::class.java.name)
                if (e.message != null) {
                    append(":\n${e.message}")
                }
            }
            Error(error)
        }
    }
}

sealed interface WeatherData {
    class Present(val data: List<Pair<Time, HourData>>) : WeatherData
    data object NotPresent : WeatherData
    class Error(val message: String) : WeatherData
}

data class HourData(
    val rain: Double,
    val temperature: Double,
    val wind: Double,
    val windDirection: Double,
    val weatherCode: Int,
    val surfacePressure: Double,
)

class WeatherClient(private val settings: WeatherWidgetSettingsState) {
    private val client = OpenMeteo(settings.latitude, settings.longitude)

    @OptIn(Response.ExperimentalGluedUnitTimeStepValues::class)
    fun getRainData(): List<Pair<Time, HourData>> {
        val forecast = client.forecast {
            hourly = Forecast.Hourly {
                listOf(precipitation, temperature2m, windspeed10m, winddirection10m, weathercode, surfacePressure)
            }
            forecastDays = 2
            temperatureUnit = settings.temperatureUnit
            windSpeedUnit = settings.windSpeedUnit
            timezone = Timezone.getTimeZone(ZoneId.systemDefault())
        }.getOrThrow()
        val now = System.currentTimeMillis() - 60 * 60_000
        Forecast.Hourly.run {
            val precipitationData = forecast.hourly.getValue(precipitation).values.filter { (t, _) -> t.time > now }
            val temperature = forecast.hourly.getValue(temperature2m).values.filter { (t, _) -> t.time > now }
            val wind = forecast.hourly.getValue(windspeed10m).values.filter { (t, _) -> t.time > now }
            val windDirection = forecast.hourly.getValue(winddirection10m).values.filter { (t, _) -> t.time > now }
            val weatherCode = forecast.hourly.getValue(weathercode).values.filter { (t, _) -> t.time > now }
            val surfacePressure = forecast.hourly.getValue(surfacePressure).values.filter { (t, _) -> t.time > now }
            val merged = precipitationData.map {
                it.key to HourData(
                    it.value!!,
                    temperature[it.key] ?: 0.0,
                    wind[it.key] ?: 0.0,
                    windDirection[it.key] ?: 0.0,
                    weatherCode[it.key]?.toInt() ?: 0,
                    surfacePressure[it.key] ?: 0.0
                )
            }
            forecast.hourly.getValue(precipitation).run {
                return merged
                    .toList()
                    .take(service<WeatherWidgetSettingsState>().hours)
            }
        }
    }
}