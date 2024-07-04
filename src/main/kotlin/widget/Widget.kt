@file:Suppress("UnstableApiUsage")

package widget

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.InplaceButton
import com.intellij.ui.NewUI
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.openmeteo.api.common.time.Time
import com.openmeteo.api.common.units.TemperatureUnit
import com.openmeteo.api.common.units.WindSpeedUnit
import services.HourData
import services.WeatherCode
import services.WeatherData
import services.WeatherService
import settings.PressureUnit
import settings.WeatherWidgetConfigurable
import settings.WeatherWidgetSettingsState
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.text.SimpleDateFormat
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt

class WeatherWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String {
        return "weatherWidgetFactory"
    }

    override fun getDisplayName(): String {
        return "Weather"
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return WeatherWidget()
    }
}

private class WeatherWidget : CustomStatusBarWidget {
    override fun ID(): String {
        return "weatherWidget"
    }

    private var widget: WidgetComponent? = null
    override fun getComponent(): JComponent {
        widget = WidgetComponent(WidgetModel())
        return widget ?: throw IllegalStateException()
    }

    override fun dispose() {
        super.dispose()
        widget?.dispose()
    }
}

class WidgetComponent(private val model: WidgetModel) : JPanel(), Disposable {
    private val formatter = SimpleDateFormat("HH")

    private fun temperatureUnitText(unit: TemperatureUnit) = when (unit) {
        TemperatureUnit.Celsius -> "°C"
        TemperatureUnit.Fahrenheit -> "°F"
    }

    private fun windSpeedUnitText(unit: WindSpeedUnit) = when (unit) {
        WindSpeedUnit.KilometresPerHour -> "km/h"
        WindSpeedUnit.MetresPerSeconds -> "m/s"
        WindSpeedUnit.MilesPerHour -> "m/h"
        WindSpeedUnit.Knots -> "kn"
    }

    private fun generateWeatherTable(data: WeatherData): String = when (data) {
        is WeatherData.Error -> "<html>${data.message.replace("\n", "<br>")}</html>"
        is WeatherData.NotPresent -> "<html>no data</html>"
        is WeatherData.Present -> {
            val weatherData = data.data
            val isPrecipitationExpected = weatherData.any { it.second.rain > 0.0 }
            buildString {
                append("<html><table align=\"center\" cellpadding=\"5\">")
                append("<tr><th>Time</th><th>Temp</th>")
                if (isPrecipitationExpected) append("<th>Precipitation</th>")
                append("<th>Pressure</th><th>Wind</th><th></th><th></th></tr>")
                weatherData.forEach { (time, hourData) ->
                    append("<tr>")
                    append("<td>")
                    append(formatter.format(time))
                    append("h")
                    append("</td>")
                    append("<td>")
                    if (hourData.temperature > 0.0) append("+")
                    append(hourData.temperature.roundToInt())
                    append(" " + temperatureUnitText(model.temperatureUnit))
                    append("</td>")
                    if (isPrecipitationExpected) {
                        append("<td>")
                        if (hourData.weatherCode > 3) append(WeatherCode.get(hourData.weatherCode) + " ")
                        if (hourData.rain > 0.0) {
                            append(hourData.rain)
                            append(" mm")
                        }
                        append("</td>")
                    }
                    append("<td>")
                    append(
                        hourData.surfacePressure.recalculate(model.pressureUnit).roundToInt()
                            .toString() + " " + model.pressureUnit.value
                    )
                    append("</td>")
                    append("<td>")
                    append(hourData.wind.roundToInt())
                    append(" " + windSpeedUnitText(model.windSpeedUnit))
                    append("</td>")
                    append("<td>")
                    append(getWindDirectionText(hourData.windDirection))
                    append("</td>")
                    append("</tr>")
                }
                append("</table>")
                if (isPrecipitationExpected.not()) {
                    append("<br><b>No precipitation expected</b>")
                }
                append("</html>")
            }
        }
    }

    init {
        preferredSize = Dimension(60, preferredSize.height)
        addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == 1) {
                    val bg = when (model.rainData) {
                        is WeatherData.Error -> JBUI.CurrentTheme.NotificationError.backgroundColor()
                        is WeatherData.Present, WeatherData.NotPresent -> JBUI.CurrentTheme.NotificationWarning.backgroundColor()
                    }
                    val fg = when (model.rainData) {
                        is WeatherData.Error -> JBUI.CurrentTheme.NotificationError.foregroundColor()
                        is WeatherData.Present, WeatherData.NotPresent -> JBUI.CurrentTheme.NotificationWarning.foregroundColor()
                    }
                    val panel = BorderLayoutPanel()
                    val title =
                        JBLabel("<html><h2>${model.weather} in ${model.city}</h2></html>", UIUtil.ComponentStyle.LARGE)
                    val settingsButton = InplaceButton(
                        IconButton(
                            "Settings",
                            AllIcons.General.Gear,
                            AllIcons.General.GearPlain
                        )
                    ) {
                        ShowSettingsUtil.getInstance()
                            .showSettingsDialog(null, WeatherWidgetConfigurable::class.java)
                    }
                    panel.background = bg
                    panel.foreground = fg
                    panel.addToTop(BorderLayoutPanel().apply {
                        background = bg
                        foreground = fg
                        addToCenter(title)
                        addToRight(settingsButton)
                    })
                    panel.addToCenter(JLabel(generateWeatherTable(model.rainData)))
                    JBPopupFactory.getInstance()
                        .createBalloonBuilder(panel)
                        .setFillColor(bg)
                        .setBorderColor(fg)
                        .setBorderInsets(Insets(2, 15, 10, 15))
                        .setCornerRadius(JBUI.scale(8))
                        .setFadeoutTime(20_000)
                        .createBalloon()
                        .show(AnchoredPoint(AnchoredPoint.Anchor.TOP, this@WidgetComponent), Balloon.Position.above)
                }
            }

            override fun mousePressed(e: MouseEvent?) {
            }

            override fun mouseReleased(e: MouseEvent?) {
            }

            override fun mouseEntered(e: MouseEvent?) {
                model.hover = true
            }

            override fun mouseExited(e: MouseEvent?) {
                model.hover = false
            }
        })
        service<WeatherService>().activeWidget = this
        updateTooltip()
    }

    override fun paint(g: Graphics) {
        g.font = TextPanel.getFont()
        super.paintComponents(g)
        setupAntialiasing(g)

        fun textY(text: String): Int {
            var textY = UIUtil.getStringY(text, Rectangle(width, height), g as Graphics2D)
            if (SystemInfo.isJetBrainsJvm && NewUI.isEnabled()) {
                textY += g.fontMetrics.leading
            }
            return textY
        }

        when (val data = model.rainData) {
            is WeatherData.Error, WeatherData.NotPresent -> {
                val errorText = "No data"
                g.color = JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
                g.drawString(errorText, width / 2 - g.fontMetrics.stringWidth(errorText) / 2, textY(errorText))
            }

            is WeatherData.Present -> {
                val weatherData = data.data
                val isRainExpected = weatherData.any { it.second.rain > 0.0 }
                if (isRainExpected) {
                    val stepSize = width / weatherData.size
                    for (i in weatherData.indices) {
                        drawStep(g, i * stepSize, stepSize, weatherData[i])
                    }
                    val nowX = calcNowX(weatherData, width)
                    g.color = UIUtil.getErrorForeground()
                    g.drawLine(nowX, height / 2, nowX, height - 2)
                }
                val widgetText = StringBuilder()
                if (model.showWind) {
                    val direction = weatherData.first().second.windDirection
                    val windSpeed = weatherData.first().second.wind.roundToInt().toString()
                    widgetText.append(getWindDirectionText(direction) + windSpeed)
                }
                if (model.showTemperature) {
                    if (widgetText.isNotEmpty()) widgetText.append(" ")
                    val temperature =
                        weatherData.first().second.temperature.roundToInt()
                            .let { if (it > 0) "+$it" else it.toString() }
                    widgetText.append(temperature)
                }
                if (widgetText.isNotEmpty()) {
                    g.color = if (model.hover) {
                        JBUI.CurrentTheme.StatusBar.Widget.HOVER_FOREGROUND
                    } else {
                        JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
                    }
                    g.drawString(
                        widgetText.toString(),
                        width / 2 - g.fontMetrics.stringWidth(widgetText.toString()) / 2,
                        textY(widgetText.toString())
                    )
                }
            }
        }
        g.dispose()
    }

    private fun drawStep(
        g: Graphics,
        startPoint: Int,
        stepWidth: Int,
        rainData: Pair<Time, HourData>
    ) {
        g.color = model.rainColor
        val level = calcLevel(rainData.second.rain, height - 2)
        g.fillRect(startPoint + 1, height - level - 1, stepWidth - 2, level)
    }

    private val maxValue = 15.0
    private fun calcLevel(value: Double, maxInt: Int): Int {
        if (value == 0.0) return 0
        if (value >= maxValue) return maxInt
        val barHeight = value / maxValue * maxInt
        if (barHeight <= 3) return 3
        return barHeight.toInt()
    }

    private fun calcNowX(rainData: List<Pair<Time, HourData>>, maxWidth: Int): Int {
        val minTime = rainData.first().first.time
        val maxTime = (rainData.last().first.time + 60 * 60_000) - minTime
        val now = System.currentTimeMillis() - minTime
        return (maxWidth.toDouble() / maxTime * now).roundToInt()
    }

    override fun dispose() {
        service<WeatherService>().activeWidget = null
    }

    fun updateTooltip() {
        toolTipText = when (val data = model.rainData) {
            is WeatherData.Error -> data.message
            is WeatherData.NotPresent -> ""
            is WeatherData.Present -> model.weather
        }
    }
}

private fun getWindDirectionText(windDirection: Double): String {
    if (windDirection > 337.5) return "↓"
    if (windDirection > 292.5) return "↘"
    if (windDirection > 247.5) return "→"
    if (windDirection > 202.5) return "↗"
    if (windDirection > 157.5) return "↑"
    if (windDirection > 122.5) return "↖"
    if (windDirection > 67.5) return "←"
    if (windDirection > 22.5) return "↙"
    return "↓"
}

class WidgetModel {
    private val service = service<WeatherService>()
    private val settings = WeatherWidgetSettingsState.getInstance()
    val rainData
        get() = service.getRainData()
    var hover: Boolean = false
    val showWind
        get() = settings.showWind
    val showTemperature
        get() = settings.showTemperature
    val city: String
        get() = settings.cityName
    val rainColor
        get() = settings.rainBarColor
    val temperatureUnit
        get() = settings.temperatureUnit
    val windSpeedUnit
        get() = settings.windSpeedUnit
    val pressureUnit
        get() = settings.pressureUnit
    val weather
        get() = when (val rainData = rainData) {
            is WeatherData.Error, WeatherData.NotPresent -> ""
            is WeatherData.Present -> rainData.data[0].second.weatherCode.let { WeatherCode.get(it) }
        }
}

private fun Double.recalculate(unit: PressureUnit) = this * unit.multiplier