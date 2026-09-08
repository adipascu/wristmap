package org.peblum.mapsforpebble.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadGraphTest {
    @Test
    fun sharesANodeWhereTwoRoadsMeet() {
        val graph =
            RoadGraph.of(listOf(floatArrayOf(0f, 0f, 100f, 0f), floatArrayOf(100f, 0f, 100f, 100f)), 1f)
        assertEquals(3, graph.nodeCount)
        assertEquals(graph.nodeIds[0][1], graph.nodeIds[1][0])
        assertEquals(2, graph.neighboursOf(graph.nodeIds[0][1]).count())
    }

    @Test
    fun collapsesARepeatedVertex() {
        val graph = RoadGraph.of(listOf(floatArrayOf(0f, 0f, 50f, 0f, 50f, 0f, 100f, 0f)), 1f)
        assertEquals(3, graph.nodeCount)
        assertEquals(graph.nodeIds[0][1], graph.nodeIds[0][2])
    }

    @Test
    fun measuresEdgeLengths() {
        val graph = RoadGraph.of(listOf(floatArrayOf(0f, 0f, 30f, 40f)), 1f)
        val slot = graph.neighboursOf(0).first()
        assertEquals(50f, graph.lengthAt(slot), 0.01f)
        assertEquals(1, graph.neighbourAt(slot))
    }

    @Test
    fun takesTheCheaperOfTwoWaysRound() {
        val direct = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f)
        val detour = floatArrayOf(0f, 0f, 95f, 105f, 100f, 100f)
        val graph = RoadGraph.of(listOf(direct, detour), 1f)
        val corner = graph.nodeIds[0][2]
        val paths = graph.shortestPaths(graph.nodeIds[0][0], 0f, 1000f, -1)
        assertTrue(paths.cost[corner] < 200f)
        assertArrayEquals(
            intArrayOf(graph.nodeIds[1][0], graph.nodeIds[1][1], graph.nodeIds[1][2]),
            paths.trace(corner),
        )
    }

    @Test
    fun stopsExpandingPastTheCostCeiling() {
        val line = floatArrayOf(0f, 0f, 100f, 0f, 200f, 0f, 300f, 0f)
        val graph = RoadGraph.of(listOf(line), 1f)
        val paths = graph.shortestPaths(graph.nodeIds[0][0], 0f, 150f, -1)
        assertEquals(100f, paths.cost[graph.nodeIds[0][1]], 0.01f)
        assertEquals(Float.MAX_VALUE, paths.cost[graph.nodeIds[0][3]], 0.01f)
    }

    @Test
    fun handlesASourceWithNoNeighbours() {
        val graph = RoadGraph.of(listOf(floatArrayOf(0f, 0f), floatArrayOf(500f, 0f, 600f, 0f)), 1f)
        val lonely = graph.nodeIds[0][0]
        val paths = graph.shortestPaths(lonely, 0f, 1000f, -1)
        assertEquals(0f, paths.cost[lonely], 0.01f)
        assertArrayEquals(intArrayOf(lonely), paths.trace(lonely))
        assertEquals(Float.MAX_VALUE, paths.cost[graph.nodeIds[1][1]], 0.01f)
    }

    @Test
    fun carriesTheSourceCostIntoEveryDistance() {
        val graph = RoadGraph.of(listOf(floatArrayOf(0f, 0f, 100f, 0f)), 1f)
        val paths = graph.shortestPaths(graph.nodeIds[0][0], 40f, 1000f, -1)
        assertEquals(140f, paths.cost[graph.nodeIds[0][1]], 0.01f)
    }

    @Test
    fun hasNoNodesWithoutRoads() {
        assertEquals(0, RoadGraph.of(emptyList(), 1f).nodeCount)
    }
}
