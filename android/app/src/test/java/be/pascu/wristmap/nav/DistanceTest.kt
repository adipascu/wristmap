package be.pascu.wristmap.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceTest {
    @Test
    fun convertsMetricAndImperial() {
        assertEquals(1200.0, Distance.toMeters("1,2 km")!!, 0.01)
        assertEquals(106.68, Distance.toMeters("350 ft")!!, 0.01)
        assertEquals(482.8, Distance.toMeters("0.3 mi")!!, 0.1)
        assertEquals(75.0, Distance.toMeters("75 m")!!, 0.01)
    }

    @Test
    fun readsThousandsSeparators() {
        assertEquals(304.8, Distance.toMeters("1,000 ft")!!, 0.01)
        assertEquals(1500.0, Distance.toMeters("1,5 km")!!, 0.01)
    }

    @Test
    fun rejectsOtherText() {
        assertNull(Distance.toMeters("14 min"))
        assertFalse(Distance.isDistance("Turn left"))
        assertTrue(Distance.isDistance("200 m"))
    }
}
