package be.pascu.mapsforpebble.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePreviewEdgeTest {
    private fun tile(
        roads: List<Road>,
        named: List<Road> = emptyList(),
    ) = TileData(0, 0, roads, named, emptyList(), emptyList())

    @Test
    fun ignoresRailwaysAndUnnamedNearbyRoads() {
        val rail = Road(floatArrayOf(0f, 0f, 1000f, 0f), RoadKind.RAIL, emptyList())
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 100.0, "Rue Test", listOf(tile(listOf(rail))), 1.0)
        assertFalse(preview.onRoad)
        assertNull(preview.currentRoad)
        assertEquals(0, preview.nextStreet.size)
        assertEquals(100f, preview.turnX, 0.5f)
    }

    @Test
    fun snapsToAMisalignedRoadWhenNothingElseIsNear() {
        val crossing = Road(floatArrayOf(100f, -300f, 100f, 300f), RoadKind.MINOR, emptyList())
        val preview = RoutePreview.build(100.0, 0.0, 100.0, 50.0, "", listOf(tile(listOf(crossing))), 1.0)
        assertTrue(preview.onRoad)
        assertEquals(0f, preview.turnX, 0.5f)
        assertEquals(50f, preview.turnY, 0.5f)
    }

    @Test
    fun walksBackwardsThroughARoadThatEndsAtTheJunction() {
        val first = Road(floatArrayOf(0f, 0f, 100f, 0f), RoadKind.MINOR, emptyList())
        val second = Road(floatArrayOf(-200f, 0f, 0f, 0f), RoadKind.MINOR, emptyList())
        val slanted = Road(floatArrayOf(-200f, 100f, 0f, 0f), RoadKind.MINOR, emptyList())
        val steep = Road(floatArrayOf(-100f, 200f, 0f, 0f), RoadKind.MINOR, emptyList())
        val degenerate = Road(floatArrayOf(0f, 0f, 0f, 0f, -50f, 0f), RoadKind.MINOR, emptyList())
        val preview = RoutePreview.build(50.0, 0.0, 270.0, 150.0, "", listOf(tile(listOf(first, slanted, degenerate, second, steep))), 1.0)
        assertEquals(-150f, preview.turnX, 0.5f)
        assertEquals(0f, preview.turnY, 0.5f)
    }

    @Test
    fun givesUpAfterTooManyHopsAndExtendsStraight() {
        val roads = (0 until 30).map { Road(floatArrayOf(it * 10f, 0f, it * 10f + 10f, 0f), RoadKind.MINOR, emptyList()) }
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 500.0, "", listOf(tile(roads)), 1.0)
        assertTrue(preview.onRoad)
        assertEquals(500f, preview.turnX, 0.5f)
    }

    @Test
    fun toleratesDuplicatePointsAndShortRoads() {
        val stuttering = Road(floatArrayOf(0f, 0f, 0f, 0f, 100f, 0f, 100f, 0f), RoadKind.MINOR, emptyList())
        val stub = Road(floatArrayOf(100f, 0f), RoadKind.MINOR, emptyList())
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 120.0, "", listOf(tile(listOf(stuttering, stub))), 1.0)
        assertEquals(120f, preview.turnX, 0.5f)
    }

    @Test
    fun doesNotSnapToAFarNamedStreet() {
        val main = Road(floatArrayOf(0f, 0f, 1000f, 0f), RoadKind.MINOR, emptyList())
        val far = Road(floatArrayOf(500f, 500f, 600f, 500f), RoadKind.MINOR, listOf("Rue Test"))
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 100.0, "Rue Test", listOf(tile(listOf(main), listOf(far))), 1.0)
        assertEquals(100f, preview.turnX, 0.5f)
        assertEquals(1, preview.nextStreet.size)
        assertNull(preview.currentRoad)
    }

    @Test
    fun picksTheNearestOfThreeRoads() {
        val near = Road(floatArrayOf(0f, 5f, 1000f, 5f), RoadKind.MINOR, emptyList())
        val farther = Road(floatArrayOf(0f, 20f, 1000f, 20f), RoadKind.MINOR, emptyList())
        val farthest = Road(floatArrayOf(0f, 30f, 1000f, 30f), RoadKind.MINOR, emptyList())
        val named = Road(floatArrayOf(0f, 20f, 1000f, 20f), RoadKind.MINOR, listOf("Twenty"))
        val nearerNamed = Road(floatArrayOf(0f, 5f, 1000f, 5f), RoadKind.MINOR, listOf("Five"))
        val preview =
            RoutePreview.build(
                100.0,
                0.0,
                90.0,
                10.0,
                "",
                listOf(tile(listOf(farther, near, farthest), listOf(named, nearerNamed, named))),
                1.0,
            )
        assertEquals(5f, preview.turnY, 0.5f)
        assertEquals("Five", preview.currentRoad)
    }

    @Test
    fun aZeroDistanceStaysAtTheSnappedVertex() {
        val road = Road(floatArrayOf(0f, 0f, 0f, 0f, 100f, 0f), RoadKind.MINOR, emptyList())
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 0.0, "", listOf(tile(listOf(road))), 1.0)
        assertTrue(preview.hasTurn)
        assertEquals(0f, preview.turnX, 0.5f)
        val dot = Road(floatArrayOf(5f, 5f, 5f, 5f), RoadKind.MINOR, emptyList())
        val stuck = RoutePreview.build(5.0, 5.0, 90.0, 0.0, "", listOf(tile(listOf(dot))), 1.0)
        assertEquals(0f, stuck.turnX, 0.5f)
    }

    @Test
    fun runsOffTheStartOfARoadWalkedBackwards() {
        val road = Road(floatArrayOf(0f, 0f, 50f, 0f, 100f, 0f), RoadKind.MINOR, emptyList())
        val preview = RoutePreview.build(80.0, 0.0, 270.0, 60.0, "", listOf(tile(listOf(road))), 1.0)
        assertEquals(-60f, preview.turnX, 0.5f)
        val offTheEnd = RoutePreview.build(10.0, 0.0, 270.0, 20.0, "", listOf(tile(listOf(road))), 1.0)
        assertEquals(-20f, offTheEnd.turnX, 0.5f)
    }

    @Test
    fun stopsAfterTheStepLimitOnAnEndlessPolyline() {
        val points = FloatArray(2 * 5000) { if (it % 2 == 0) (it / 2).toFloat() else 0f }
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 4990.0, "", listOf(tile(listOf(Road(points, RoadKind.MINOR, emptyList())))), 1.0)
        assertEquals(4990f, preview.turnX, 1f)
    }
}
