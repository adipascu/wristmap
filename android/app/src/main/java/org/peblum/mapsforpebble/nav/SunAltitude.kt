package org.peblum.mapsforpebble.nav

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object SunAltitude {
    const val CIVIL_TWILIGHT_DEGREES = -6.0

    private const val MS_PER_DAY = 86_400_000.0
    private const val UNIX_EPOCH_JULIAN_DAY = 2_440_587.5
    private const val J2000_JULIAN_DAY = 2_451_545.0
    private const val DEGREES_PER_RADIAN = 180.0 / Math.PI
    private const val RADIANS_PER_DEGREE = Math.PI / 180.0
    private const val DEGREES_PER_HOUR = 15.0

    fun degreesAbove(
        latitude: Double,
        longitude: Double,
        timeMs: Long,
    ): Double {
        val days = timeMs / MS_PER_DAY + UNIX_EPOCH_JULIAN_DAY - J2000_JULIAN_DAY
        val meanLongitude = wrapDegrees(280.460 + 0.985_647_4 * days)
        val meanAnomaly = wrapDegrees(357.528 + 0.985_600_3 * days) * RADIANS_PER_DEGREE
        val eclipticLongitude =
            (meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly)) * RADIANS_PER_DEGREE
        val obliquity = (23.439 - 0.000_000_4 * days) * RADIANS_PER_DEGREE
        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude)) * DEGREES_PER_RADIAN
        val declination = asin(sin(obliquity) * sin(eclipticLongitude))
        val siderealDegrees = wrapDegrees((18.697_374_558 + 24.065_709_824_419_08 * days) * DEGREES_PER_HOUR)
        val hourAngle = wrapDegrees(siderealDegrees + longitude - rightAscension) * RADIANS_PER_DEGREE
        val latitudeRadians = latitude * RADIANS_PER_DEGREE
        val sine =
            sin(latitudeRadians) * sin(declination) +
                cos(latitudeRadians) * cos(declination) * cos(hourAngle)
        return asin(sine.coerceIn(-1.0, 1.0)) * DEGREES_PER_RADIAN
    }

    fun isDark(
        latitude: Double,
        longitude: Double,
        timeMs: Long,
    ): Boolean = degreesAbove(latitude, longitude, timeMs) < CIVIL_TWILIGHT_DEGREES

    private fun wrapDegrees(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
}
