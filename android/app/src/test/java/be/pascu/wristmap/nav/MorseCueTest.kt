package be.pascu.wristmap.nav

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MorseCueTest {
    @Test
    fun leftIsDotDashDotDot() {
        assertArrayEquals(intArrayOf(100, 100, 300, 100, 100, 100, 100), MorseCue.durations(Maneuver.TURN_LEFT))
    }

    @Test
    fun slightLeftPrefixesAShortLetter() {
        assertArrayEquals(intArrayOf(100, 300, 100, 100, 300, 100, 100, 100, 100), MorseCue.durations(Maneuver.SLIGHT_LEFT))
    }

    @Test
    fun everyKnownManeuverHasAShortCode() {
        for (maneuver in Maneuver.values().filter { it != Maneuver.UNKNOWN }) {
            val durations = MorseCue.durations(maneuver)!!
            assertEquals(true, durations.size <= 9)
            assertEquals(true, durations.sum() <= 1500)
        }
        assertNull(MorseCue.durations(Maneuver.UNKNOWN))
    }

    @Test
    fun patternIsTheEncodedDurations() {
        assertArrayEquals(MorseCue.encode(MorseCue.durations(Maneuver.TURN_RIGHT)!!), MorseCue.pattern(Maneuver.TURN_RIGHT))
        assertNull(MorseCue.pattern(Maneuver.UNKNOWN))
    }

    @Test
    fun encodesLittleEndianHalfWords() {
        assertArrayEquals(byteArrayOf(0x2C, 0x01, 0x64, 0x00), MorseCue.encode(intArrayOf(300, 100)))
    }
}
