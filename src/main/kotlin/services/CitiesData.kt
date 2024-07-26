package services

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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