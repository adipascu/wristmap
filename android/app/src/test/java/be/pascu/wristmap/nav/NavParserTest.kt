package be.pascu.wristmap.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavParserTest {
    @Test
    fun parsesEnglishNotification() {
        val state = NavParser.parse(RawNotification(listOf("200 m", "Turn left onto Rue de la Loi", "14 min · 2.1 km · 10:45"), false))
        assertEquals("200 m", state.distance)
        assertEquals(200.0, state.distanceMeters!!, 0.01)
        assertEquals("Rue de la Loi", state.street)
        assertEquals(Maneuver.TURN_LEFT, state.maneuver)
        assertEquals("10:45", state.eta)
        assertEquals("2.1 km", state.distRemain)
        assertEquals("14 min", state.timeRemain)
    }

    @Test
    fun parsesFrenchNotification() {
        val state = NavParser.parse(RawNotification(listOf("300 m", "Tournez à gauche sur Rue Neuve", "12 min · 1,5 km · 18:03"), false))
        assertEquals("Rue Neuve", state.street)
        assertEquals(Maneuver.TURN_LEFT, state.maneuver)
        assertEquals(300.0, state.distanceMeters!!, 0.01)
        assertEquals("1,5 km", state.distRemain)
        assertEquals("18:03", state.eta)
    }

    @Test
    fun parsesDutchNotification() {
        val state = NavParser.parse(RawNotification(listOf("50 m", "Sla rechtsaf naar Nieuwstraat"), false))
        assertEquals("Nieuwstraat", state.street)
        assertEquals(Maneuver.TURN_RIGHT, state.maneuver)
    }

    @Test
    fun parsesRealWalkingNotification() {
        val state = NavParser.parse(RawNotification(listOf("140 m · Turn right onto Rue du Houblon/Hopstraat", "Arrive 11:55 PM"), false))
        assertEquals("140 m", state.distance)
        assertEquals("Rue du Houblon/Hopstraat", state.street)
        assertEquals(Maneuver.TURN_RIGHT, state.maneuver)
        assertEquals("11:55 PM", state.eta)
    }

    @Test
    fun parsesLockscreenStyleLine() {
        val state = NavParser.parse(RawNotification(listOf("200 m · Turn right onto Main St"), false))
        assertEquals("200 m", state.distance)
        assertEquals("Main St", state.street)
        assertEquals(Maneuver.TURN_RIGHT, state.maneuver)
    }

    @Test
    fun reroutingKeepsNavigationActiveWithoutDistance() {
        val state = NavParser.parse(RawNotification(listOf("Rerouting…"), true))
        assertEquals(true, state.active)
        assertEquals(true, state.rerouting)
        assertNull(state.distanceMeters)
    }

    @Test
    fun destinationIsRecognised() {
        val state = NavParser.parse(RawNotification(listOf("40 m", "Your destination is on the right"), false))
        assertEquals(Maneuver.DESTINATION, state.maneuver)
    }
}
