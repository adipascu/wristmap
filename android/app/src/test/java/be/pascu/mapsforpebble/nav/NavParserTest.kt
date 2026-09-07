package be.pascu.mapsforpebble.nav

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
    fun fallsBackWhenReroutingHasOnlyADistance() {
        val state = NavParser.parse(RawNotification(listOf("200 m", ""), true))
        assertEquals("Rerouting", state.instruction)
    }

    @Test
    fun readsDurationOnlyAndEtaLines() {
        val state = NavParser.parse(RawNotification(listOf("12 min", "10:45", "Turn left · now"), false))
        assertEquals("12 min", state.timeRemain)
        assertEquals("10:45", state.eta)
        assertEquals("Turn left · now", state.instruction)
    }

    @Test
    fun findsAnArrivalTimeInsideLongerText() {
        val state = NavParser.parse(RawNotification(listOf("Arrive 10:45 via A12"), false))
        assertEquals("Arrive 10:45 via A12", state.instruction)
        assertEquals("10:45", state.eta)
    }

    @Test
    fun readsATripLineWithoutAClock() {
        val state = NavParser.parse(RawNotification(listOf("1 km · 12 min", "200 m · 1 km"), false))
        assertEquals("1 km", state.distRemain)
        assertEquals("12 min", state.timeRemain)
        assertEquals("", state.distance)
    }

    @Test
    fun ignoresEmptyPartsAndDurationOnlyLines() {
        val state = NavParser.parse(RawNotification(listOf("12 min · Turn left", "200 m · \u00A0 · Turn right", "13 min", "14 min"), false))
        assertEquals("12 min · Turn left", state.instruction)
        assertEquals("200 m", state.distance)
        assertEquals("13 min", state.timeRemain)
    }

    @Test
    fun keepsTheFirstValueOfEachTripField() {
        val state =
            NavParser.parse(
                RawNotification(listOf("10:45 · 10:50 · 1 km · 2 km · 12 min · 13 min", "100 m", "Turn left", "200 m · Turn right"), false),
            )
        assertEquals("10:45", state.eta)
        assertEquals("1 km", state.distRemain)
        assertEquals("12 min", state.timeRemain)
        assertEquals("100 m", state.distance)
        assertEquals("Turn left", state.instruction)
    }

    @Test
    fun keepsTheFirstOfRepeatedFields() {
        val state = NavParser.parse(RawNotification(listOf("100 m", "300 m", "Turn right", "Turn left", "11:00", "12:00"), false))
        assertEquals("100 m", state.distance)
        assertEquals("Turn right", state.instruction)
        assertEquals("11:00", state.eta)
    }

    @Test
    fun guessesEveryManoeuvreKeyword() {
        val expectations =
            mapOf(
                "Make a U-turn" to Maneuver.UTURN,
                "At the roundabout take the 2nd exit" to Maneuver.ROUNDABOUT,
                "Merge onto E40" to Maneuver.MERGE,
                "Take exit 12" to Maneuver.RAMP,
                "Slight left onto Rue A" to Maneuver.SLIGHT_LEFT,
                "Keep right onto Rue B" to Maneuver.SLIGHT_RIGHT,
                "Sharp left onto Rue C" to Maneuver.SHARP_LEFT,
                "Sharp right onto Rue D" to Maneuver.SHARP_RIGHT,
                "Head north on Rue E" to Maneuver.STRAIGHT,
                "Wobble" to Maneuver.UNKNOWN,
                "" to Maneuver.UNKNOWN,
            )
        for ((text, maneuver) in expectations) assertEquals(text, maneuver, NavParser.guessManeuver(text))
        assertEquals("", NavParser.extractStreet(""))
        assertEquals("Wobble", NavParser.extractStreet("Wobble"))
    }

    @Test
    fun destinationIsRecognised() {
        val state = NavParser.parse(RawNotification(listOf("40 m", "Your destination is on the right"), false))
        assertEquals(Maneuver.DESTINATION, state.maneuver)
    }
}
