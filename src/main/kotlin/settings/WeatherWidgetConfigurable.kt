package settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.components.BorderLayoutPanel
import com.openmeteo.api.common.units.TemperatureUnit
import com.openmeteo.api.common.units.WindSpeedUnit
import services.WeatherService
import services.findClosestCity
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class WeatherWidgetConfigurable : Configurable {
    private val ui = SettingsComponent(WeatherWidgetSettingsState.getInstance())
    override fun createComponent(): JComponent {
        return ui.getComponent()
    }

    override fun isModified(): Boolean {
        val settings = WeatherWidgetSettingsState.getInstance()
        val latitude = ui.latitude.text.toFloatOrNull() ?: return false
        val longitude = ui.longitude.text.toFloatOrNull() ?: return false
        return latitude != settings.latitude
                || longitude != settings.longitude
                || ui.getHours() != settings.hours
                || ui.showWindDirection.isSelected != settings.showWind
                || ui.showTemperature.isSelected != settings.showTemperature
                || ui.cityName.text != settings.cityName
                || ui.colorPicker.selectedColor != settings.rainBarColor
                || ui.temperatureUnit.selectedItem != settings.temperatureUnit
                || ui.windSpeedUnit.selectedItem != settings.windSpeedUnit
                || ui.pressureUnit.selectedItem != settings.pressureUnit
    }

    override fun apply() {
        val settings = WeatherWidgetSettingsState.getInstance()
        settings.latitude = ui.latitude.text.toFloat()
        settings.longitude = ui.longitude.text.toFloat()
        settings.hours = ui.getHours()
        settings.showWind = ui.showWindDirection.isSelected
        settings.showTemperature = ui.showTemperature.isSelected
        settings.cityName = ui.cityName.text
        settings.rainBarColor = ui.colorPicker.selectedColor ?: settings.rainBarColor
        settings.temperatureUnit = ui.temperatureUnit.selectedItem as TemperatureUnit
        settings.windSpeedUnit = ui.windSpeedUnit.selectedItem as WindSpeedUnit
        settings.pressureUnit = ui.pressureUnit.selectedItem as PressureUnit
        service<WeatherService>().update()
    }


    override fun getDisplayName(): String {
        return "Weather Widget"
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        ui.dispose()
    }
}

private class SettingsComponent(settingsState: WeatherWidgetSettingsState) : Disposable {
    private val floatVerifier: InputVerifier = object : InputVerifier() {
        override fun verify(input: JComponent): Boolean {
            return try {
                (input as? JBTextField)?.text?.toFloat() ?: return false
                cityName.text = findClosestCity(latitude.text.toFloat(), longitude.text.toFloat())
                true
            } catch (e: NumberFormatException) {
                PopupUtil.showBalloonForComponent(
                    input,
                    "Wrong format",
                    MessageType.WARNING,
                    true,
                    this@SettingsComponent
                )
                false
            }
        }
    }

    inner class CityNameListener : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) {}
        override fun removeUpdate(e: DocumentEvent?) {}
        override fun changedUpdate(e: DocumentEvent?) {
            findCity()
        }
    }

    val latitude = JBTextField(settingsState.latitude.toString()).apply {
        inputVerifier = floatVerifier
        document.addDocumentListener(CityNameListener())
    }
    val longitude = JBTextField(settingsState.longitude.toString()).apply {
        inputVerifier = floatVerifier
        document.addDocumentListener(CityNameListener())
    }

    private fun findCity() {
        cityName.text = findClosestCity(latitude.text.toFloat(), longitude.text.toFloat())
    }

    private val hours = JSpinner(SpinnerNumberModel(settingsState.hours, 2, 10, 1))

    val showWindDirection = JBCheckBox().apply {
        isSelected = settingsState.showWind
    }

    val showTemperature = JBCheckBox().apply {
        isSelected = settingsState.showTemperature
    }

    val colorPicker = ColorPanel().apply {
        selectedColor = settingsState.rainBarColor
    }

    val temperatureUnit = JComboBox<TemperatureUnit>().apply {
        TemperatureUnit.entries.forEach { addItem(it) }
        selectedItem = settingsState.temperatureUnit
    }

    val windSpeedUnit = JComboBox<WindSpeedUnit>().apply {
        listOf(WindSpeedUnit.KilometresPerHour, WindSpeedUnit.MetresPerSeconds).forEach { addItem(it) }
        selectedItem = settingsState.windSpeedUnit
    }

    val pressureUnit = JComboBox<PressureUnit>().apply {
        PressureUnit.entries.forEach { addItem(it) }
        selectedItem = settingsState.pressureUnit
        setRenderer { _, value, _, _, _ -> JBLabel(value.value) }
    }

    val cityName = JBTextField(settingsState.cityName)

    fun getHours(): Int = hours.value as Int

    fun getComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Latitude", latitude)
        .addLabeledComponent("Longitude", longitude)
        .addLabeledComponent("Place name", cityName)
        .addSeparator()
        .addLabeledComponent("Show next hours", hours)
        .addLabeledComponent("Show wind", showWindDirection)
        .addLabeledComponent("Wind speed unit", windSpeedUnit)
        .addLabeledComponent("Show temperature", showTemperature)
        .addLabeledComponent("Temperature unit", temperatureUnit)
        .addLabeledComponent("Pressure unit", pressureUnit)
        .addSeparator()
        .addLabeledComponent("Precipitation bars color", colorPicker)
        .panel.let {
            BorderLayoutPanel().apply { addToTop(it) }
        }

    override fun dispose() {
    }
}