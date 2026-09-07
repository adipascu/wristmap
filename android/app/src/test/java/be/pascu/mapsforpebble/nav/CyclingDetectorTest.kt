package be.pascu.mapsforpebble.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CyclingDetectorTest {
    private fun CyclingDetector.ride(
        speedMps: Float,
        fromMs: Long,
        toMs: Long,
    ): Boolean {
        var result = false
        var nowMs = fromMs
        while (nowMs <= toMs) {
            result = update(speedMps, nowMs)
            nowMs += 1_000L
        }
        return result
    }

    @Test
    fun walkingSpeedNeverCounts() {
        val detector = CyclingDetector()
        assertFalse(detector.ride(1.4f, 0L, 60_000L))
        assertFalse(detector.cycling)
    }

    @Test
    fun cyclingSpeedMustLastTheWholeWindow() {
        val detector = CyclingDetector()
        assertFalse(detector.ride(5f, 0L, 9_000L))
        assertTrue(detector.update(5f, 10_000L))
        assertTrue(detector.cycling)
    }

    @Test
    fun aDipBelowTheThresholdRestartsTheWindow() {
        val detector = CyclingDetector()
        assertFalse(detector.ride(5f, 0L, 6_000L))
        assertFalse(detector.update(1f, 7_000L))
        assertFalse(detector.ride(5f, 8_000L, 17_000L))
        assertTrue(detector.update(5f, 18_000L))
    }

    @Test
    fun aGapInFixesRestartsTheWindow() {
        val detector = CyclingDetector()
        assertFalse(detector.ride(5f, 0L, 5_000L))
        assertFalse(detector.ride(5f, 9_000L, 18_000L))
        assertTrue(detector.update(5f, 19_000L))
    }

    @Test
    fun aReplayedOldFixDoesNotStartTheWindow() {
        val detector = CyclingDetector()
        assertFalse(detector.update(5f, -3_600_000L))
        assertFalse(detector.ride(5f, 0L, 9_000L))
        assertTrue(detector.update(5f, 10_000L))
    }

    @Test
    fun latchesForTheTripOnceDetected() {
        val detector = CyclingDetector(thresholdMps = 2f, sustainMs = 1_000L)
        detector.update(3f, 0L)
        assertTrue(detector.update(3f, 1_000L))
        assertTrue(detector.update(0f, 2_000L))
        assertTrue(detector.cycling)
    }

    @Test
    fun assumeAndResetSwitchTheLatch() {
        val detector = CyclingDetector()
        detector.assume()
        assertTrue(detector.cycling)
        detector.reset()
        assertFalse(detector.cycling)
        assertFalse(detector.update(5f, 0L))
    }
}
