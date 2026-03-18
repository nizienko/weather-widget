package settings

import com.openmeteo.api.common.units.TemperatureUnit
import com.openmeteo.api.common.units.WindSpeedUnit
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvertersTest {
    private val temperatureConverter = TemperatureUnitConverter()
    private val windConverter = WindSpeedUnitConverter()
    private val pressureConverter = PressureUnitConverter()
    private val colorConverter = ColorConverter()

    @Test
    fun `temperature converter round trips`() {
        val text = temperatureConverter.toString(TemperatureUnit.Fahrenheit)
        assertEquals(TemperatureUnit.Fahrenheit, temperatureConverter.fromString(text))
    }

    @Test
    fun `wind speed converter round trips`() {
        val text = windConverter.toString(WindSpeedUnit.MetresPerSeconds)
        assertEquals(WindSpeedUnit.MetresPerSeconds, windConverter.fromString(text))
    }

    @Test
    fun `pressure converter round trips`() {
        val text = pressureConverter.toString(PressureUnit.MMHG)
        assertEquals(PressureUnit.MMHG, pressureConverter.fromString(text))
    }

    @Test
    fun `color converter round trips`() {
        val original = Color(10, 20, 30)
        val encoded = colorConverter.toString(original)
        assertEquals(original, colorConverter.fromString(encoded))
    }
}
