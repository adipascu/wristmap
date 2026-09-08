package org.peblum.mapsforpebble.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManeuverTest {
    @Test
    fun leftManoeuvresTurnLeft() {
        for (maneuver in listOf(Maneuver.TURN_LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT, Maneuver.UTURN)) {
            assertEquals(true, maneuver.turnsLeft)
        }
    }

    @Test
    fun rightManoeuvresTurnRight() {
        for (maneuver in listOf(Maneuver.TURN_RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT)) {
            assertEquals(false, maneuver.turnsLeft)
        }
    }

    @Test
    fun theRestHaveNoSide() {
        val rest =
            listOf(
                Maneuver.UNKNOWN,
                Maneuver.STRAIGHT,
                Maneuver.MERGE,
                Maneuver.ROUNDABOUT,
                Maneuver.RAMP,
                Maneuver.DESTINATION,
            )
        for (maneuver in rest) {
            assertNull(maneuver.turnsLeft)
        }
    }
}
