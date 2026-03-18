package services

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import settings.SavedLocation

data class CapitalCity(val name: String, val latitude: Float, val longitude: Float)

private fun haversine(lat1: Float, lon1: Float, lat2: Float, lon2: Float): Float {
    val R = 6371.0 // Radius of the Earth in kilometers
    val dLat = Math.toRadians(lat2.toDouble() - lat1)
    val dLon = Math.toRadians(lon2.toDouble() - lon1.toDouble())
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1.toDouble())) * cos(Math.toRadians(lat2.toDouble())) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (R * c).toFloat()
}

fun findClosestCity(latitude: Float, longitude: Float): String {
    var closestCity = cities[0]
    var smallestDistance = haversine(latitude, longitude, closestCity.latitude, closestCity.longitude)

    for (city in cities) {
        val distance = haversine(latitude, longitude, city.latitude, city.longitude)
        if (distance < smallestDistance) {
            closestCity = city
            smallestDistance = distance
        }
    }
    return closestCity.name
}

fun guessCityByTimezone(zoneId: ZoneId = ZoneId.systemDefault(), instant: Instant = Instant.now()): SavedLocation {
    val zoneMatch = findCityByZoneId(zoneId)
    if (zoneMatch != null) return zoneMatch.toSavedLocation()
    val offsetHours = zoneId.rules.getOffset(instant).totalSeconds / 3600.0
    return cities.minWith(compareBy<CapitalCity> {
        abs(approximateOffsetHours(it.longitude) - offsetHours)
    }.thenBy {
        abs(it.longitude - offsetHours * 15.0)
    }).toSavedLocation()
}

private fun approximateOffsetHours(longitude: Float): Double = longitude / 15.0

private fun findCityByZoneId(zoneId: ZoneId): CapitalCity? {
    val zoneName = zoneId.id.substringAfterLast('/')
        .replace('_', ' ')
        .trim()
    if (zoneName.isEmpty()) return null
    return cities.firstOrNull { it.name.equals(zoneName, ignoreCase = true) }
}

private fun CapitalCity.toSavedLocation(): SavedLocation =
    SavedLocation(name, latitude, longitude)
