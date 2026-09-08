package org.peblum.mapsforpebble.map

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoZoomTest {
    private val lat = 50.8467
    private val height = 152

    @Test
    fun zoomsOutUntilTheTurnFits() {
        assertEquals(15.0, AutoZoom.choose(lat, height, 150.0, 1f), 0.0)
        assertEquals(14.0, AutoZoom.choose(lat, height, 300.0, 1f), 0.0)
        assertEquals(17.0, AutoZoom.choose(lat, height, 40.0, 1f), 0.0)
        assertEquals(18.0, AutoZoom.choose(lat, height, 20.0, 1f), 0.0)
    }

    @Test
    fun capsZoomWhenCycling() {
        assertEquals(17.0, AutoZoom.choose(lat, height, 20.0, 6f), 0.0)
        assertEquals(17.0, AutoZoom.choose(lat, height, null, 6f), 0.0)
        assertEquals(17.0, AutoZoom.choose(lat, height, null, null), 0.0)
    }

    @Test
    fun neverZoomsBelowMinimum() {
        assertEquals(14.0, AutoZoom.choose(lat, height, 5000.0, 1f), 0.0)
    }
}
