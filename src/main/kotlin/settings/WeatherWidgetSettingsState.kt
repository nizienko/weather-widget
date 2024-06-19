package settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.openmeteo.api.common.units.TemperatureUnit
import com.openmeteo.api.common.units.WindSpeedUnit
import java.awt.Color


@State(
    name = "com.github.nizienko.weatherWidget.WeatherWidgetSettingsState",
    storages = [Storage("WeatherWidget.xml")]
)
class WeatherWidgetSettingsState : PersistentStateComponent<WeatherWidgetSettingsState> {
    var latitude: Float = 51.49141f
    var longitude: Float = -0.035749f
    var hours: Int = 5
    var showWind: Boolean = true
    var showTemperature: Boolean = true
    var cityName: String = "London"

    @OptionTag(converter = TemperatureUnitConverter::class)
    var temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius

    @OptionTag(converter = WindSpeedUnitConverter::class)
    var windSpeedUnit: WindSpeedUnit = WindSpeedUnit.KilometresPerHour

    @OptionTag(converter = ColorConverter::class)
    var rainBarColor: Color = Color(66, 135, 245)

    @OptionTag(converter = PressureUnitConverter::class)
    var pressureUnit: PressureUnit = PressureUnit.MMHG

    companion object {
        fun getInstance() = service<WeatherWidgetSettingsState>()
    }

    override fun getState(): WeatherWidgetSettingsState {
        return this
    }

    override fun loadState(state: WeatherWidgetSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

internal class ColorConverter : Converter<Color>() {
    override fun fromString(value: String): Color {
        return Color(value.toInt())
    }

    override fun toString(value: Color): String {
        return value.rgb.toString()
    }
}

internal class TemperatureUnitConverter : Converter<TemperatureUnit>() {
    override fun fromString(value: String): TemperatureUnit {
        return TemperatureUnit.valueOf(value)
    }

    override fun toString(value: TemperatureUnit): String {
        return value.name
    }
}

internal class WindSpeedUnitConverter : Converter<WindSpeedUnit>() {
    override fun fromString(value: String): WindSpeedUnit {
        return WindSpeedUnit.valueOf(value)
    }

    override fun toString(value: WindSpeedUnit): String {
        return value.name
    }
}

internal class PressureUnitConverter : Converter<PressureUnit>() {
    override fun fromString(value: String): PressureUnit {
        return PressureUnit.valueOf(value)
    }

    override fun toString(value: PressureUnit): String {
        return value.name
    }
}

enum class PressureUnit(val value: String, val multiplier: Double) { MMHG("mmHg", 0.75006375541921), HPA("hPa", 1.0)}
