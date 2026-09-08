package be.pascu.mapsforpebble.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunAltitudeTest {
    private val brusselsLat = 50.85
    private val brusselsLon = 4.35
    private val tolerance = 0.05

    @Test
    fun matchesKnownAltitudesAroundTheWorld() {
        assertEquals(44.920, SunAltitude.degreesAbove(brusselsLat, brusselsLon, 1_788_782_400_000L), tolerance)
        assertEquals(62.585, SunAltitude.degreesAbove(brusselsLat, brusselsLon, 1_782_042_240_000L), tolerance)
        assertEquals(-61.462, SunAltitude.degreesAbove(brusselsLat, brusselsLon, 1_797_894_000_000L), tolerance)
        assertEquals(89.877, SunAltitude.degreesAbove(0.0, 0.0, 1_774_008_420_000L), tolerance)
        assertEquals(72.503, SunAltitude.degreesAbove(-33.87, 151.21, 1_768_439_400_000L), tolerance)
        assertEquals(66.077, SunAltitude.degreesAbove(-0.18, -78.47, 1_782_061_200_000L), tolerance)
    }

    @Test
    fun theSunNeverSetsOverTromsoAtTheSolstice() {
        assertEquals(4.034, SunAltitude.degreesAbove(69.65, 18.96, 1_782_000_000_000L), tolerance)
    }

    @Test
    fun theSolsticeAltitudeIsTheLatitudePlusTheTilt() {
        val solsticeNoon = 1_782_042_240_000L
        assertEquals(90.0 - brusselsLat + 23.44, SunAltitude.degreesAbove(brusselsLat, brusselsLon, solsticeNoon), 0.1)
    }

    @Test
    fun duskIsNotYetDarkButAnHourLaterIs() {
        val justAfterSunset = 1_788_804_000_000L
        val anHourLater = 1_788_807_600_000L
        assertEquals(1.481, SunAltitude.degreesAbove(brusselsLat, brusselsLon, justAfterSunset), tolerance)
        assertEquals(-7.733, SunAltitude.degreesAbove(brusselsLat, brusselsLon, anHourLater), tolerance)
        assertFalse(SunAltitude.isDark(brusselsLat, brusselsLon, justAfterSunset))
        assertTrue(SunAltitude.isDark(brusselsLat, brusselsLon, anHourLater))
    }

    @Test
    fun middayIsNeverDark() {
        assertFalse(SunAltitude.isDark(brusselsLat, brusselsLon, 1_782_042_240_000L))
        assertTrue(SunAltitude.isDark(brusselsLat, brusselsLon, 1_797_894_000_000L))
    }

    @Test
    fun longitudeShiftsTheSolarDay() {
        val sunset = 1_788_804_000_000L
        val here = SunAltitude.degreesAbove(brusselsLat, brusselsLon, sunset)
        val sixHoursEast = SunAltitude.degreesAbove(brusselsLat, brusselsLon + 90.0, sunset)
        val sixHoursWest = SunAltitude.degreesAbove(brusselsLat, brusselsLon - 90.0, sunset)
        assertTrue(sixHoursEast < here)
        assertTrue(here < sixHoursWest)
    }

    @Test
    fun thePolesSeeOppositeSuns() {
        val solsticeNoon = 1_782_042_240_000L
        assertEquals(23.44, SunAltitude.degreesAbove(90.0, 0.0, solsticeNoon), 0.1)
        assertEquals(-23.44, SunAltitude.degreesAbove(-90.0, 0.0, solsticeNoon), 0.1)
    }

    @Test
    fun aTimeBeforeTheEpochStillResolves() {
        val nineteenSixtyNine = -20_000_000_000L
        assertEquals(56.439, SunAltitude.degreesAbove(brusselsLat, brusselsLon, nineteenSixtyNine), tolerance)
    }
}
