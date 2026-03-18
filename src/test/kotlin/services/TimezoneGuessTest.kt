package services

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TimezoneGuessTest {
    @Test
    fun `timezone city name maps to matching capital`() {
        val zone = ZoneId.of("Europe/Amsterdam")
        val city = guessCityByTimezone(zone, Instant.EPOCH)
        assertEquals("Amsterdam", city.name)
    }
}
