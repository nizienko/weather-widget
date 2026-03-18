package services

import kotlin.test.Test
import kotlin.test.assertEquals

class CitiesDataTest {
    @Test
    fun `closest city for London coordinates is London`() {
        val city = findClosestCity(51.5074f, -0.1278f)
        assertEquals("London", city)
    }

    @Test
    fun `closest city for Tokyo coordinates is Tokyo`() {
        val city = findClosestCity(35.6895f, 139.6917f)
        assertEquals("Tokyo", city)
    }

    @Test
    fun `closest city near Paris returns Paris`() {
        assertEquals("Paris", findClosestCity(48.857f, 2.3522f))
    }
}
