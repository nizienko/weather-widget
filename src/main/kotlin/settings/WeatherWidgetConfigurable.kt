package settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.openmeteo.api.common.units.TemperatureUnit
import com.openmeteo.api.common.units.WindSpeedUnit
import services.CapitalCity
import services.WeatherService
import services.cities
import services.guessCityByTimezone
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
        val locationsModified = ui.getSavedLocations() != settings.savedLocations
        val activeIndexModified = ui.getActiveLocationIndex() != settings.selectedLocationIndex
        return ui.getHours() != settings.hours
                || ui.showWindDirection.isSelected != settings.showWind
                || ui.showTemperature.isSelected != settings.showTemperature
                || ui.colorPicker.selectedColor != settings.rainBarColor
                || ui.temperatureUnit.selectedItem != settings.temperatureUnit
                || ui.windSpeedUnit.selectedItem != settings.windSpeedUnit
                || ui.pressureUnit.selectedItem != settings.pressureUnit
                || locationsModified
                || activeIndexModified
    }

    override fun apply() {
        val settings = WeatherWidgetSettingsState.getInstance()
        settings.hours = ui.getHours()
        settings.showWind = ui.showWindDirection.isSelected
        settings.showTemperature = ui.showTemperature.isSelected
        settings.rainBarColor = ui.colorPicker.selectedColor ?: settings.rainBarColor
        settings.temperatureUnit = ui.temperatureUnit.selectedItem as TemperatureUnit
        settings.windSpeedUnit = ui.windSpeedUnit.selectedItem as WindSpeedUnit
        settings.pressureUnit = ui.pressureUnit.selectedItem as PressureUnit
        settings.savedLocations = ui.getSavedLocations().toMutableList()
        settings.selectedLocationIndex = ui.getActiveLocationIndex()
        service<WeatherService>().forceWeatherCheck()
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
                true
            } catch (_: NumberFormatException) {
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

    val latitude = JBTextField().apply {
        inputVerifier = floatVerifier
    }
    val longitude = JBTextField().apply {
        inputVerifier = floatVerifier
    }

    private val findCityButton = JButton("Find City").apply {
        addActionListener {
            JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<CapitalCity>(null, cities.sortedBy { it.name }) {
                override fun onChosen(
                    selectedValue: CapitalCity,
                    finalChoice: Boolean
                ): PopupStep<*>? {
                    cityName.text = selectedValue.name
                    latitude.text = selectedValue.latitude.toString()
                    longitude.text = selectedValue.longitude.toString()
                    return FINAL_CHOICE
                }
                override fun isSpeedSearchEnabled(): Boolean {
                    return true
                }

                override fun getTextFor(value: CapitalCity): String {
                    return value.name
                }
            }).showUnderneathOf(this)
        }
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

    val temperatureUnit = ComboBox<TemperatureUnit>().apply {
        TemperatureUnit.entries.forEach { addItem(it) }
        selectedItem = settingsState.temperatureUnit
    }

    val windSpeedUnit = ComboBox<WindSpeedUnit>().apply {
        listOf(WindSpeedUnit.KilometresPerHour, WindSpeedUnit.MetresPerSeconds).forEach { addItem(it) }
        selectedItem = settingsState.windSpeedUnit
    }

    val pressureUnit = ComboBox<PressureUnit>().apply {
        PressureUnit.entries.forEach { addItem(it) }
        selectedItem = settingsState.pressureUnit
        setRenderer { _, value, _, _, _ -> JBLabel(value.value) }
    }

    val cityName = JBTextField()

    private val locationsModel = DefaultListModel<SavedLocation>().apply {
        settingsState.savedLocations.forEach { addElement(it.copy()) }
    }
    private val locationsList = JBList(locationsModel).apply {
        visibleRowCount = 5
        setCellRenderer { _, value, _, _, _ ->
            JBLabel("${value.name} (${value.latitude}, ${value.longitude})")
        }
    }
    private val addLocationButton = JButton("Add New").apply {
        addActionListener {
            val location = currentLocation() ?: return@addActionListener
            locationsModel.addElement(location)
            locationsList.selectedIndex = locationsModel.size() - 1
            populateFields(location)
        }
    }
    private val updateLocationButton = JButton("Update Selected").apply {
        addActionListener {
            val index = locationsList.selectedIndex
            if (index < 0) return@addActionListener
            val location = currentLocation() ?: return@addActionListener
            locationsModel.set(index, location)
            populateFields(location)
        }
    }
    private val removeLocationButton = JButton("Remove Selected").apply {
        addActionListener {
            val index = locationsList.selectedIndex
            if (index >= 0 && locationsModel.size() > 1) {
                locationsModel.remove(index)
            }
        }
    }
    private val autoDetectButton = JButton("Auto-Detect (Timezone)").apply {
        addActionListener {
            val detected = guessCityByTimezone()
            populateFields(detected)
        }
    }
    private val locationsHint = JBLabel("Edit fields and click Update Selected to save changes.").apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }

    fun getHours(): Int = hours.value as Int

    fun getSavedLocations(): List<SavedLocation> = (0 until locationsModel.size()).map { index ->
        locationsModel.getElementAt(index)
    }

    fun getActiveLocationIndex(): Int = locationsList.selectedIndex.coerceAtLeast(0)

    private fun currentLocation(): SavedLocation? {
        val lat = latitude.text.toFloatOrNull() ?: return null
        val lon = longitude.text.toFloatOrNull() ?: return null
        val name = cityName.text.trim().ifEmpty { "Custom" }
        return SavedLocation(name, lat, lon)
    }

    private fun populateFields(location: SavedLocation) {
        cityName.text = location.name
        latitude.text = location.latitude.toString()
        longitude.text = location.longitude.toString()
    }

    fun getComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Place", cityName)
        .addLabeledComponent("Latitude", latitude)
        .addLabeledComponent("Longitude", longitude)
        .addComponent(BorderLayoutPanel().apply {
            addToLeft(autoDetectButton)
            addToRight(findCityButton)
        })
        .addLabeledComponent(
            "Saved locations",
            BorderLayoutPanel().apply {
                addToCenter(JScrollPane(locationsList))
                addToRight(
                    JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(addLocationButton)
                        add(updateLocationButton)
                        add(removeLocationButton)
                    }
                )
            }
        )
        .addSeparator()
        .addLabeledComponent("Show next hours", hours)
        .addLabeledComponent("Show wind", showWindDirection)
        .addLabeledComponent("Wind speed unit", windSpeedUnit)
        .addLabeledComponent("Show temperature", showTemperature)
        .addLabeledComponent("Temperature unit", temperatureUnit)
        .addLabeledComponent("Pressure unit", pressureUnit)
        .addSeparator()
        .addLabeledComponent("Precipitation bars color", colorPicker)
        .addSeparator()
        .addComponent(locationsHint)

        .panel.let {
            BorderLayoutPanel().apply { addToTop(it) }
        }

    override fun dispose() {
    }

    init {
        val initialIndex = settingsState.selectedLocationIndex.coerceIn(0, locationsModel.size() - 1)
        if (locationsModel.size() > 0) {
            locationsList.selectedIndex = initialIndex
            populateFields(locationsModel.getElementAt(initialIndex))
        }
        locationsList.addListSelectionListener {
            val location = locationsList.selectedValue ?: return@addListSelectionListener
            populateFields(location)
        }
    }
}
