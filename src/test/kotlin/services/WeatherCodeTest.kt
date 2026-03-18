package services

import kotlin.test.Test
import kotlin.test.assertEquals

class WeatherCodeTest {
    @Test
    fun `map known code to description`() {
        assertEquals("Clear sky", WeatherCode.get(0))
        assertEquals("Thunderstorm with heavy hail", WeatherCode.get(99))
    }

    @Test
    fun `unknown code falls back to placeholder`() {
        assertEquals("___", WeatherCode.get(-1))
    }
}
