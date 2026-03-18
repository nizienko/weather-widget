package services

import com.openmeteo.api.common.time.Time
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import settings.PressureUnit
import settings.WeatherWidgetSettingsState
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherServiceTest {
    private val settings = WeatherWidgetSettingsState().apply {
        latitude = 1.0f
        longitude = 2.0f
        hours = 2
        pressureUnit = PressureUnit.HPA
    }

    @Test
    fun `forceWeatherCheck publishes data on success`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val clockValue = 1234L
        val provider = object : WeatherProvider {
            override fun getRainData(): List<Pair<Time, HourData>> {
                return listOf(
                    Time(0L) to HourData(0.2, 10.0, 5.0, 90.0, 2, 1013.0),
                    Time(3_600_000L) to HourData(0.0, 11.0, 4.0, 180.0, 1, 1012.0)
                )
            }
        }

        WeatherService.testHooks = WeatherService.TestHooks(
            scope = scope,
            settingsProvider = { settings },
            weatherClientFactory = { provider },
            clock = { clockValue },
            startPolling = false,
            ioDispatcher = dispatcher
        )
        val service = WeatherService()

        try {
            service.forceWeatherCheck()
            runCurrent()

            val value = service.weatherDataFlow.value
            val present = assertIs<WeatherData.Present>(value)
            assertEquals(clockValue, present.time)
            assertEquals(2, present.data.size)
        } finally {
            WeatherService.testHooks = null
        }
    }

    @Test
    fun `forceWeatherCheck publishes error on failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val provider = object : WeatherProvider {
            override fun getRainData(): List<Pair<Time, HourData>> {
                throw IllegalStateException("boom")
            }
        }

        WeatherService.testHooks = WeatherService.TestHooks(
            scope = scope,
            settingsProvider = { settings },
            weatherClientFactory = { provider },
            clock = { 55L },
            startPolling = false,
            ioDispatcher = dispatcher
        )
        val service = WeatherService()

        try {
            service.forceWeatherCheck()
            runCurrent()

            val value = service.weatherDataFlow.value
            val error = assertIs<WeatherData.Error>(value)
            assertTrue(error.message.contains("IllegalStateException"))
            assertTrue(error.message.contains("boom"))
            assertEquals(55L, error.time)
        } finally {
            WeatherService.testHooks = null
        }
    }
}
