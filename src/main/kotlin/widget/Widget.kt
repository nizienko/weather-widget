@file:Suppress("UnstableApiUsage")

package widget

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.BalloonImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GotItComponentBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.openmeteo.api.common.time.Time
import com.openmeteo.api.common.units.TemperatureUnit
import com.openmeteo.api.common.units.WindSpeedUnit
import services.HourData
import services.WeatherService
import settings.WeatherWidgetConfigurable
import settings.WeatherWidgetSettingsState
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.text.SimpleDateFormat
import javax.swing.JComponent
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

    private fun generateWeatherTable(data: List<Pair<Time, HourData>>): String {
        val isRainExpected = data.any { it.second.rain > 0.0 }
        return buildString {
            append("<table align=\"center\" border=\"5\">")
            append("<tr><th>Time</th><th>Temp(${temperatureUnitText(model.temperatureUnit)})</th>")
            if (isRainExpected) append("<th>Rain(mm)</th>")
            append("<th>Wind(${windSpeedUnitText(model.windSpeedUnit)})</th><th></th></tr>")
            data.forEach { (time, hourData) ->
                append("<tr>")
                append("<td>")
                append(formatter.format(time))
                append("h")
                append("</td>")
                append("<td>")
                if (hourData.temperature > 0.0) append("+")
                append(hourData.temperature.roundToInt())
                append("</td>")
                if (isRainExpected) {
                    append("<td>")
                    append(hourData.rain)
                    append("</td>")
                }
                append("<td>")
                append(hourData.wind.roundToInt())
                append("</td>")
                append("<td>")
                append(getWindDirectionText(hourData.windDirection))
                append("</td>")
                append("</tr>")
            }
            append("</table>")
            if (isRainExpected.not()) {
                append("<br><b>No rain expected</b>")
            }
        }
    }

    init {
        preferredSize = Dimension(60, preferredSize.height)
        addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == 1) {
                    val balloon = GotItComponentBuilder(generateWeatherTable(model.rainData))
                        .withLink("Settings") {
                            ShowSettingsUtil.getInstance()
                                .showSettingsDialog(null, WeatherWidgetConfigurable::class.java)
                        }
                        .withHeader(model.city)
                        .showButton(false)
                        .build(this@WidgetComponent)
                    if (balloon is BalloonImpl) {
                        balloon.setHideOnClickOutside(true)
                        balloon.showInCenterOf(this@WidgetComponent)
                    }
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
    }

    override fun paint(g: Graphics) {
        g.font = TextPanel.getFont()
        super.paintComponents(g)
        setupAntialiasing(g)
        val rainData = model.rainData
        val isRainExpected = rainData.any { it.second.rain > 0.0 }
        if (isRainExpected) {
            val stepSize = width / rainData.size
            for (i in rainData.indices) {
                drawStep(g, i * stepSize, stepSize, rainData[i])
            }
            val nowX = calcNowX(rainData, width)
            g.color = UIUtil.getErrorForeground()
            g.drawLine(nowX, height / 2, nowX, height - 2)
        }
        val widgetText = StringBuilder()
        if (model.showWind) {
            val direction = rainData.first().second.windDirection
            val windSpeed = rainData.first().second.wind.roundToInt().toString()
            widgetText.append(getWindDirectionText(direction) + windSpeed)
        }
        if (model.showTemperature) {
            if (widgetText.isNotEmpty()) widgetText.append(" ")
            val temperature =
                rainData.first().second.temperature.roundToInt().let { if (it > 0) "+$it" else it.toString() }
            widgetText.append(temperature)
        }
        if (widgetText.isNotEmpty()) {
            var y = UIUtil.getStringY(widgetText.toString(), Rectangle(width, height), g as Graphics2D)
            if (SystemInfo.isJetBrainsJvm && ExperimentalUI.isNewUI()) {
                y += g.fontMetrics.leading
            }
            g.color = if (model.hover) {
                JBUI.CurrentTheme.StatusBar.Widget.HOVER_FOREGROUND
            } else {
                JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
            }
            g.drawString(widgetText.toString(), width / 2 - g.fontMetrics.stringWidth(widgetText.toString()) / 2, y)
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

    private val maxValue = 1.0
    private fun calcLevel(value: Double, maxInt: Int): Int {
        if (value >= maxValue) return maxInt
        return (value % maxValue * maxInt).toInt()
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
}