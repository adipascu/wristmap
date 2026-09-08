package be.pascu.mapsforpebble.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSearchTest {
    private val mainStreet = floatArrayOf(0f, 0f, 300f, 0f, 600f, 0f, 900f, 0f)
    private val bouchers = floatArrayOf(300f, -400f, 300f, 0f, 300f, 400f)
    private val farStreet = floatArrayOf(600f, 0f, 600f, -400f)

    private fun tileOf(
        roads: List<Road>,
        named: List<Road>,
    ) = TileData(originX = 0, originY = 0, roads = roads, namedRoads = named, water = emptyList(), waterways = emptyList())

    private val grid =
        tileOf(
            listOf(
                Road(mainStreet, RoadKind.MINOR, emptyList()),
                Road(bouchers, RoadKind.MINOR, emptyList()),
                Road(farStreet, RoadKind.MINOR, emptyList()),
            ),
            listOf(
                Road(mainStreet, RoadKind.MINOR, listOf("Main Street")),
                Road(bouchers, RoadKind.MINOR, listOf("Rue des Bouchers")),
                Road(farStreet, RoadKind.MINOR, listOf("Far Street")),
            ),
        )

    @Test
    fun routesToTheJunctionWithTheNamedStreet() {
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue des Bouchers", listOf(grid), 1.0)
        assertTrue(preview.routed)
        assertTrue(preview.hasTurn)
        assertEquals(200f, preview.turnX, 0.5f)
        assertEquals(0f, preview.turnY, 0.5f)
        assertArrayEquals(floatArrayOf(0f, 0f, 200f, 0f), preview.path, 0.5f)
    }

    @Test
    fun turnsOffTheStraightRoadWhenTheNamedStreetIsAroundACorner() {
        val elbow = floatArrayOf(300f, 0f, 300f, 200f, 500f, 200f)
        val target = floatArrayOf(500f, 100f, 500f, 200f, 500f, 300f)
        val tile =
            tileOf(
                listOf(
                    Road(mainStreet, RoadKind.MINOR, emptyList()),
                    Road(elbow, RoadKind.MINOR, emptyList()),
                    Road(target, RoadKind.MINOR, emptyList()),
                ),
                listOf(Road(target, RoadKind.MINOR, listOf("Rue Cible"))),
            )
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 600.0, "Rue Cible", listOf(tile), 1.0)
        assertTrue(preview.routed)
        assertEquals(400f, preview.turnX, 0.5f)
        assertEquals(200f, preview.turnY, 0.5f)
        assertArrayEquals(floatArrayOf(0f, 0f, 200f, 0f, 200f, 200f, 400f, 200f), preview.path, 0.5f)
    }

    @Test
    fun prefersTheJunctionClosestToTheAnnouncedDistance() {
        val twice = floatArrayOf(300f, -400f, 300f, 0f, 300f, 400f)
        val alsoBouchers = floatArrayOf(600f, -400f, 600f, 0f, 600f, 400f)
        val tile =
            tileOf(
                listOf(
                    Road(mainStreet, RoadKind.MINOR, emptyList()),
                    Road(twice, RoadKind.MINOR, emptyList()),
                    Road(alsoBouchers, RoadKind.MINOR, emptyList()),
                ),
                listOf(
                    Road(twice, RoadKind.MINOR, listOf("Rue des Bouchers")),
                    Road(alsoBouchers, RoadKind.MINOR, listOf("Rue des Bouchers")),
                ),
            )
        val near = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue des Bouchers", listOf(tile), 1.0)
        val far = RoutePreview.build(100.0, 0.0, 90.0, 500.0, "Rue des Bouchers", listOf(tile), 1.0)
        assertEquals(200f, near.turnX, 0.5f)
        assertEquals(500f, far.turnX, 0.5f)
    }

    @Test
    fun fallsBackToTheStraightWalkWhenNothingIsInRange() {
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Far Street", listOf(grid), 1.0)
        assertFalse(preview.routed)
        assertTrue(preview.hasTurn)
    }

    @Test
    fun fallsBackWhenTheNamedStreetIsBehindYou() {
        val preview = RoutePreview.build(800.0, 0.0, 90.0, 200.0, "Rue des Bouchers", listOf(grid), 1.0)
        assertFalse(preview.routed)
    }

    @Test
    fun walksBackwardsAlongTheRoadWhenHeadingWest() {
        val preview = RoutePreview.build(800.0, 0.0, 270.0, 500.0, "Rue des Bouchers", listOf(grid), 1.0)
        assertTrue(preview.routed)
        assertEquals(-500f, preview.turnX, 0.5f)
        assertEquals(0f, preview.turnY, 0.5f)
    }

    @Test
    fun drawsTheStreetPastALeftTurn() {
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue des Bouchers", listOf(grid), 1.0, turnsLeft = true)
        assertArrayEquals(floatArrayOf(200f, 0f, 200f, -60f), preview.afterTurn, 0.01f)
    }

    @Test
    fun drawsTheStreetPastARightTurn() {
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue des Bouchers", listOf(grid), 1.0, turnsLeft = false)
        assertArrayEquals(floatArrayOf(200f, 0f, 200f, 60f), preview.afterTurn, 0.01f)
    }

    @Test
    fun drawsNothingPastTheTurnWithoutADirection() {
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue des Bouchers", listOf(grid), 1.0)
        assertEquals(0, preview.afterTurn.size)
    }

    @Test
    fun stopsAtTheEndOfTheStreetPastTheTurn() {
        val short = floatArrayOf(300f, -20f, 300f, 0f, 300f, 20f)
        val tile =
            tileOf(
                listOf(Road(mainStreet, RoadKind.MINOR, emptyList()), Road(short, RoadKind.MINOR, emptyList())),
                listOf(Road(short, RoadKind.MINOR, listOf("Rue Courte"))),
            )
        val left = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue Courte", listOf(tile), 1.0, turnsLeft = true)
        val right = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue Courte", listOf(tile), 1.0, turnsLeft = false)
        assertArrayEquals(floatArrayOf(200f, 0f, 200f, -20f), left.afterTurn, 0.01f)
        assertArrayEquals(floatArrayOf(200f, 0f, 200f, 20f), right.afterTurn, 0.01f)
    }

    @Test
    fun ignoresAZeroLengthStepPastTheTurn() {
        val doubled = floatArrayOf(300f, -30f, 300f, -30f, 300f, 0f, 300f, 30f)
        val tile =
            tileOf(
                listOf(Road(mainStreet, RoadKind.MINOR, emptyList()), Road(doubled, RoadKind.MINOR, emptyList())),
                listOf(Road(doubled, RoadKind.MINOR, listOf("Rue Double"))),
            )
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue Double", listOf(tile), 1.0, turnsLeft = true)
        assertArrayEquals(floatArrayOf(200f, 0f, 200f, -30f), preview.afterTurn, 0.01f)
    }

    @Test
    fun drawsNothingPastTheTurnWhenTheNamedStreetIsASinglePoint() {
        val dot = floatArrayOf(300f, 0f)
        val tile =
            tileOf(
                listOf(Road(mainStreet, RoadKind.MINOR, emptyList()), Road(bouchers, RoadKind.MINOR, emptyList())),
                listOf(Road(dot, RoadKind.MINOR, listOf("Rue Point"))),
            )
        val preview = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue Point", listOf(tile), 1.0, turnsLeft = true)
        assertTrue(preview.routed)
        assertEquals(0, preview.afterTurn.size)
    }

    @Test
    fun neverDoublesBackThroughYourOwnPosition() {
        val street = floatArrayOf(0f, 0f, 300f, 0f, 500f, 0f, 900f, 0f)
        val behind = floatArrayOf(300f, -400f, 300f, 0f, 300f, 400f)
        val tile =
            tileOf(
                listOf(Road(street, RoadKind.MINOR, emptyList()), Road(behind, RoadKind.MINOR, emptyList())),
                listOf(Road(behind, RoadKind.MINOR, listOf("Rue Derriere"))),
            )
        val preview = RoutePreview.build(500.0, 0.0, 90.0, 400.0, "Rue Derriere", listOf(tile), 1.0)
        assertFalse(preview.routed)
        assertTrue(preview.turnX > 0f)
    }

    @Test
    fun joinsASideStreetThatEndsPartWayAlongAnother() {
        val through = floatArrayOf(0f, 0f, 600f, 0f)
        val side = floatArrayOf(300f, 0f, 300f, 400f)
        val tile =
            tileOf(
                listOf(Road(through, RoadKind.MINOR, emptyList()), Road(side, RoadKind.MINOR, emptyList())),
                listOf(Road(side, RoadKind.MINOR, listOf("Rue Laterale"))),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 300.0, "Rue Laterale", listOf(tile), 1.0)
        assertTrue(preview.routed)
        assertEquals(300f, preview.turnX, 0.5f)
        assertEquals(0f, preview.turnY, 0.5f)
    }

    @Test
    fun leavesStairsOutOfTheSearch() {
        val street = floatArrayOf(0f, 0f, 300f, 0f, 600f, 0f)
        val stairs = floatArrayOf(0f, 0f, 300f, 0f)
        val target = floatArrayOf(300f, -400f, 300f, 0f, 300f, 400f)
        val withStairs =
            tileOf(
                listOf(
                    Road(stairs, RoadKind.STEPS, emptyList()),
                    Road(street, RoadKind.MINOR, emptyList()),
                    Road(target, RoadKind.MINOR, emptyList()),
                ),
                listOf(Road(target, RoadKind.MINOR, listOf("Rue Cible"))),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 300.0, "Rue Cible", listOf(withStairs), 1.0)
        assertTrue(preview.routed)
        assertEquals(300f, preview.turnX, 0.5f)
    }

    @Test
    fun reusesTheGraphWhileTheTilesStayTheSame() {
        val tiles = listOf(grid)
        val first = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue des Bouchers", tiles, 1.0)
        val second = RoutePreview.build(150.0, 0.0, 90.0, 150.0, "Rue des Bouchers", tiles, 1.0)
        assertTrue(first.routed)
        assertTrue(second.routed)
        assertEquals(200f, first.turnX, 0.5f)
        assertEquals(150f, second.turnX, 0.5f)
    }

    @Test
    fun spansSeveralTilesFromTheLowestOrigin() {
        val west = floatArrayOf(0f, 4096f, 4096f, 4096f)
        val east = floatArrayOf(0f, 0f, 400f, 0f)
        val target = floatArrayOf(200f, 0f, 200f, 400f)
        val far =
            TileData(
                originX = 4096,
                originY = 4096,
                roads = listOf(Road(east, RoadKind.MINOR, emptyList()), Road(target, RoadKind.MINOR, emptyList())),
                namedRoads = listOf(Road(target, RoadKind.MINOR, listOf("Rue Voisine"))),
                water = emptyList(),
                waterways = emptyList(),
            )
        val near =
            TileData(
                originX = 0,
                originY = 0,
                roads = listOf(Road(west, RoadKind.MINOR, emptyList())),
                namedRoads = emptyList(),
                water = emptyList(),
                waterways = emptyList(),
            )
        val preview = RoutePreview.build(4000.0, 4096.0, 90.0, 296.0, "Rue Voisine", listOf(far, near), 1.0)
        assertTrue(preview.routed)
        assertEquals(296f, preview.turnX, 1f)
    }

    @Test
    fun keepsTheNearestNodeBehindAsTheBlockedOne() {
        val street = floatArrayOf(900f, 0f, 300f, 0f, 0f, 0f, -300f, 0f, -600f, 0f)
        val target = floatArrayOf(300f, -400f, 300f, 0f, 300f, 400f)
        val tile =
            tileOf(
                listOf(Road(street, RoadKind.MINOR, emptyList()), Road(target, RoadKind.MINOR, emptyList())),
                listOf(Road(target, RoadKind.MINOR, listOf("Rue Devant"))),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 300.0, "Rue Devant", listOf(tile), 1.0)
        assertTrue(preview.routed)
        assertEquals(300f, preview.turnX, 0.5f)
    }

    @Test
    fun ignoresAnEndpointThatOnlyPassesNearTheRoad() {
        val through = floatArrayOf(0f, 0f, 600f, 0f)
        val aside = floatArrayOf(300f, 2f, 300f, 400f)
        val tile =
            tileOf(
                listOf(Road(through, RoadKind.MINOR, emptyList()), Road(aside, RoadKind.MINOR, emptyList())),
                listOf(Road(aside, RoadKind.MINOR, listOf("Rue Ecartee"))),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 300.0, "Rue Ecartee", listOf(tile), 1.0)
        assertFalse(preview.routed)
    }

    @Test
    fun foldsATouchThatLandsOnAVertexIntoIt() {
        val through = floatArrayOf(0f, 0f, 300f, 0f, 600f, 0f)
        val hair = floatArrayOf(0.02f, 0f, 0.02f, 400f)
        val target = floatArrayOf(300f, -400f, 300f, 0f, 300f, 400f)
        val tile =
            tileOf(
                listOf(
                    Road(through, RoadKind.MINOR, emptyList()),
                    Road(hair, RoadKind.MINOR, emptyList()),
                    Road(target, RoadKind.MINOR, emptyList()),
                ),
                listOf(Road(target, RoadKind.MINOR, listOf("Rue Cible"))),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 300.0, "Rue Cible", listOf(tile), 1.0)
        assertTrue(preview.routed)
        assertEquals(300f, preview.turnX, 0.5f)
    }

    @Test
    fun readsTheTurnDirectionOnAStreetDrawnTheOtherWay() {
        val reversed = floatArrayOf(300f, 400f, 300f, 0f, 300f, -400f)
        val tile =
            tileOf(
                listOf(Road(mainStreet, RoadKind.MINOR, emptyList()), Road(reversed, RoadKind.MINOR, emptyList())),
                listOf(Road(reversed, RoadKind.MINOR, listOf("Rue Inverse"))),
            )
        val left = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue Inverse", listOf(tile), 1.0, turnsLeft = true)
        val right = RoutePreview.build(100.0, 0.0, 90.0, 200.0, "Rue Inverse", listOf(tile), 1.0, turnsLeft = false)
        assertArrayEquals(floatArrayOf(200f, 0f, 200f, -60f), left.afterTurn, 0.01f)
        assertArrayEquals(floatArrayOf(200f, 0f, 200f, 60f), right.afterTurn, 0.01f)
    }

    @Test
    fun findsAJunctionInTheMiddleOfAMergedWay() {
        val merged = floatArrayOf(0f, 0f, 250f, 0f, 500f, 0f, 750f, 0f)
        val side = floatArrayOf(500f, 0f, 500f, 300f)
        val tile =
            tileOf(
                listOf(Road(merged, RoadKind.MINOR, emptyList()), Road(side, RoadKind.MINOR, emptyList())),
                listOf(Road(side, RoadKind.MINOR, listOf("Rue Laterale"))),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 500.0, "Rue Laterale", listOf(tile), 1.0)
        assertTrue(preview.routed)
        assertEquals(500f, preview.turnX, 0.5f)
        assertEquals(0f, preview.turnY, 0.5f)
    }

    @Test
    fun densifiesLongNamedSegmentsSoAMidBlockNodeCounts() {
        val long = floatArrayOf(0f, 0f, 200f, 0f, 400f, 0f)
        val sparse = floatArrayOf(200f, -600f, 200f, 600f)
        val tile =
            tileOf(
                listOf(Road(long, RoadKind.MINOR, emptyList()), Road(sparse, RoadKind.MINOR, emptyList())),
                listOf(Road(sparse, RoadKind.MINOR, listOf("Rue Longue"))),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 200.0, "Rue Longue", listOf(tile), 1.0)
        assertTrue(preview.routed)
        assertEquals(200f, preview.turnX, 0.5f)
    }

    @Test
    fun mergesRepeatedVerticesIntoOneNode() {
        val stutter = floatArrayOf(0f, 0f, 300f, 0f, 300f, 0f, 600f, 0f)
        val side = floatArrayOf(300f, 0f, 300f, 300f)
        val tile =
            tileOf(
                listOf(Road(stutter, RoadKind.MINOR, emptyList()), Road(side, RoadKind.MINOR, emptyList())),
                listOf(Road(side, RoadKind.MINOR, listOf("Rue Bis"))),
            )
        val preview = RoutePreview.build(0.0, 0.0, 90.0, 300.0, "Rue Bis", listOf(tile), 1.0)
        assertTrue(preview.routed)
        assertEquals(300f, preview.turnX, 0.5f)
    }
}
