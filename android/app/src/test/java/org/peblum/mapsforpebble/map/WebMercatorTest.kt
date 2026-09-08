package org.peblum.mapsforpebble.map

import org.junit.Assert.assertEquals
import org.junit.Test

class WebMercatorTest {
    @Test
    fun grandPlaceFallsInExpectedTile() {
        assertEquals(8390, WebMercator.tileOf(WebMercator.toWorldX(4.3525)))
        assertEquals(5496, WebMercator.tileOf(WebMercator.toWorldY(50.8467)))
    }

    @Test
    fun scalesAreConsistent() {
        val lat = 50.8467
        val perPixelViaUnits = WebMercator.metersPerUnit(lat) / WebMercator.pixelsPerUnit(17.0)
        assertEquals(WebMercator.metersPerPixel(lat, 17.0), perPixelViaUnits, 1e-6)
        assertEquals(0.5, WebMercator.pixelsPerUnit(17.0), 1e-9)
    }
}
