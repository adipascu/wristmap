package org.peblum.mapsforpebble.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePreviewTest {
    private val mainStreet = floatArrayOf(0f, 0f, 1000f, 0f)
    private val crossStreet = floatArrayOf(500f, -300f, 500f, 300f)
    private val tile =
        TileData(
            originX = 0,
            originY = 0,
            roads = listOf(Road(mainStreet, RoadKind.MINOR, emptyList()), Road(crossStreet, RoadKind.MINOR, emptyList())),
            namedRoads =
                listOf(
                    Road(mainStreet, RoadKind.MINOR, listOf("Main Street")),
                    Road(crossStreet, RoadKind.MINOR, listOf("Rue Test - Teststraat")),
                ),
            water = emptyList(),
            waterways = emptyList(),
        )

    @Test
    fun walksAlongRoadAndSnapsTurnToNamedStreet() {
        val preview = RoutePreview.build(100.0, 5.0, 90.0, 380.0, "Rue Test", listOf(tile), 1.0)
        assertTrue(preview.hasTurn)
        assertTrue(preview.onRoad)
        assertEquals(400f, preview.turnX, 0.5f)
        assertEquals(-5f, preview.turnY, 0.5f)
        assertEquals("Main Street", preview.currentRoad)
        assertEquals(1, preview.nextStreet.size)
    }

    @Test
    fun walksBackwardsWhenHeadingWest() {
        val preview = RoutePreview.build(800.0, 0.0, 270.0, 300.0, "", listOf(tile), 1.0)
        assertEquals(-300f, preview.turnX, 0.5f)
        assertEquals(0f, preview.turnY, 0.5f)
    }

    @Test
    fun stopsFollowingAtSharpCorner() {
        val first = floatArrayOf(0f, 0f, 100f, 0f)
        val second = floatArrayOf(100f, 0f, 100f, 200f)
        val joined =
            TileData(
                0,
                0,
                listOf(Road(first, RoadKind.MINOR, emptyList()), Road(second, RoadKind.MINOR, emptyList())),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 250.0, "", listOf(joined), 1.0)
        assertEquals(250f, preview.turnX, 0.5f)
        assertEquals(0f, preview.turnY, 0.5f)
    }

    @Test
    fun fallsBackToStraightLineWithoutRoads() {
        val preview = RoutePreview.build(5000.0, 5000.0, 0.0, 200.0, "", listOf(tile), 1.0)
        assertFalse(preview.onRoad)
        assertEquals(0f, preview.turnX, 0.5f)
        assertEquals(-200f, preview.turnY, 0.5f)
    }

    @Test
    fun continuesOntoConnectedRoadPastEnd() {
        val first = floatArrayOf(0f, 0f, 100f, 0f)
        val second = floatArrayOf(100f, 0f, 273.2f, 100f)
        val joined =
            TileData(
                0,
                0,
                listOf(Road(first, RoadKind.MINOR, emptyList()), Road(second, RoadKind.MINOR, emptyList())),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 250.0, "", listOf(joined), 1.0)
        assertEquals(229.9f, preview.turnX, 0.5f)
        assertEquals(75f, preview.turnY, 0.5f)
    }

    @Test
    fun withoutDistanceOnlyHighlightsStreet() {
        val preview = RoutePreview.build(100.0, 0.0, 90.0, null, "Teststraat", listOf(tile), 1.0)
        assertFalse(preview.hasTurn)
        assertEquals(1, preview.nextStreet.size)
    }
}
