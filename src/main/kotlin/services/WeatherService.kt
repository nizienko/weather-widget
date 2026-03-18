package services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.Disposable
import org.jetbrains.annotations.TestOnly
import com.openmeteo.api.Forecast
import com.openmeteo.api.OpenMeteo
import com.openmeteo.api.common.Response
import com.openmeteo.api.common.time.Time
import com.openmeteo.api.common.time.Timezone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.WeatherData.*
import settings.WeatherWidgetSettingsState
import java.time.ZoneId

@Service
class WeatherService : Disposable {
    data class TestHooks(
        val scope: CoroutineScope,
        val settingsProvider: () -> WeatherWidgetSettingsState,
        val weatherClientFactory: (WeatherWidgetSettingsState) -> WeatherProvider,
        val clock: () -> Long,
        val startPolling: Boolean,
        val ioDispatcher: CoroutineDispatcher
    )

    companion object {
        @TestOnly
        internal var testHooks: TestHooks? = null
    }

    private val hooks = testHooks
    private val serviceJob = SupervisorJob()
    val scope: CoroutineScope = hooks?.scope ?: CoroutineScope(serviceJob + Dispatchers.Default)
    private val settingsProvider: () -> WeatherWidgetSettingsState =
        hooks?.settingsProvider ?: { service<WeatherWidgetSettingsState>() }
    private val weatherClientFactory: (WeatherWidgetSettingsState) -> WeatherProvider =
        hooks?.weatherClientFactory ?: { WeatherClient(it) }
    private val clock: () -> Long = hooks?.clock ?: { System.currentTimeMillis() }
    private val startPolling: Boolean = hooks?.startPolling ?: true
    private val ioDispatcher: CoroutineDispatcher = hooks?.ioDispatcher ?: Dispatchers.IO
    private val _weatherDataFlow: MutableStateFlow<WeatherData> =
        MutableStateFlow(NotPresent(0L))
    val weatherDataFlow = _weatherDataFlow.asStateFlow()

    init {
        if (startPolling) {
            scope.launch {
                while (true) {
                    val lastState = weatherDataFlow.value
                    val now = clock()
                    val needUpdate = when (lastState) {
                        is Error -> now - lastState.time > 30_000
                        is NotPresent -> now - lastState.time > 5_000
                        is Present -> now - lastState.time > 5 * 60_000
                    }
                    if (needUpdate) {
                        val data = withContext(ioDispatcher) { loadWeather() }
                        _weatherDataFlow.emit(data)
                    }
                    delay(20_000)
                }
            }
        }
    }

    fun forceWeatherCheck() {
        scope.launch {
            val data = withContext(ioDispatcher) { loadWeather() }
            _weatherDataFlow.emit(data)
        }
    }

    private fun loadWeather(): WeatherData {
        val time = clock()
        val settings = settingsProvider()
        return try {
            val data = weatherClientFactory(settings).getRainData()
            Present(data, time)
        } catch (e: Throwable) {
            val error = buildString {
                append(e::class.java.name)
                if (e.message != null) {
                    append(":\n${e.message}")
                }
            }
            Error(error, time)
        }
    }

    override fun dispose() {
        serviceJob.cancel()
    }
}

sealed interface WeatherData {
    val time: Long

    class Present(val data: List<Pair<Time, HourData>>, override val time: Long) : WeatherData
    class NotPresent(override val time: Long) : WeatherData
    class Error(val message: String, override val time: Long) : WeatherData
}

data class HourData(
    val rain: Double,
    val temperature: Double,
    val wind: Double,
    val windDirection: Double,
    val weatherCode: Int,
    val surfacePressure: Double,
)

interface WeatherProvider {
    fun getRainData(): List<Pair<Time, HourData>>
}

class WeatherClient(private val settings: WeatherWidgetSettingsState) : WeatherProvider {
    private val client = settings.currentLocation().run {
        OpenMeteo(latitude, longitude)
    }

    @OptIn(Response.ExperimentalGluedUnitTimeStepValues::class)
    override fun getRainData(): List<Pair<Time, HourData>> {
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
