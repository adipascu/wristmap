package org.peblum.mapsforpebble.map

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class Preview(
    val path: FloatArray,
    val turnX: Float,
    val turnY: Float,
    val hasTurn: Boolean,
    val onRoad: Boolean,
    val currentRoad: String?,
    val nextStreet: List<FloatArray>,
    val afterTurn: FloatArray = FloatArray(0),
    val routed: Boolean = false,
)

private class RelativeRoad(
    val points: FloatArray,
    val road: Road,
)

private class Entry(
    val forward: Int,
    val behind: Int,
    val cost: Float,
)

private class Snap(
    val road: RelativeRoad,
    val index: Int,
    val segment: Int,
    val x: Float,
    val y: Float,
    val distance: Double,
)

object RoutePreview {
    private var cachedTiles: List<TileData> = emptyList()
    private var cachedGraph: RoadGraph? = null
    private var cachedOriginX = 0
    private var cachedOriginY = 0

    private const val SNAP_MAX_M = 40.0
    private const val CURRENT_ROAD_MAX_M = 30.0
    private const val TURN_SNAP_MAX_M = 60.0
    private const val TARGET_MAX_M = 25.0
    private const val JOIN_MAX_M = 3.0
    private const val TOUCH_MAX_M = 1.5
    private const val AFTER_TURN_M = 60.0
    private const val ROUTE_SLACK_FRACTION = 0.35
    private const val ROUTE_SLACK_MIN_M = 30.0
    private const val MAX_HOPS = 16
    private const val MAX_STEPS = 4000
    private const val MAX_JOIN_ANGLE = 80.0
    private const val ALIGNMENT_PENALTY_M = 30.0

    fun build(
        x: Double,
        y: Double,
        headingDeg: Double,
        distanceMeters: Double?,
        nextStreetName: String,
        tiles: List<TileData>,
        metersPerUnit: Double,
        turnsLeft: Boolean? = null,
    ): Preview {
        val roads = tiles.flatMap { tile -> tile.roads.filter { walkable(it.kind) }.map { relative(tile, it, x, y) } }
        val named = tiles.flatMap { tile -> tile.namedRoads.map { relative(tile, it, x, y) } }
        val wanted = Names.normalize(nextStreetName)
        val matching = if (wanted.isEmpty()) emptyList() else named.filter { Names.matches(it.road.normalizedNames, wanted) }
        val nearestNamed = nearest(named, 0f, 0f)
        val currentRoad =
            if (nearestNamed != null && nearestNamed.distance * metersPerUnit <= CURRENT_ROAD_MAX_M) {
                nearestNamed.road.road.displayName
            } else {
                null
            }

        val highlight = matching.map { it.points }

        if (distanceMeters == null) {
            return Preview(FloatArray(0), 0f, 0f, false, false, currentRoad, highlight)
        }

        val headingRad = Math.toRadians(headingDeg)
        val headingX = sin(headingRad).toFloat()
        val headingY = -cos(headingRad).toFloat()
        val distanceUnits = (distanceMeters / metersPerUnit).toFloat()
        val snap = snapToRoad(roads, headingX, headingY, (SNAP_MAX_M / metersPerUnit), ALIGNMENT_PENALTY_M / metersPerUnit)

        if (snap != null && matching.isNotEmpty()) {
            val found =
                route(snap, tiles, matching, headingX, headingY, x, y, distanceUnits, metersPerUnit, turnsLeft, currentRoad, highlight)
            if (found != null) return found
        }

        val path = ArrayList<Float>()
        val onRoad = snap != null
        if (snap == null) {
            path.add(0f)
            path.add(0f)
            path.add(headingX * distanceUnits)
            path.add(headingY * distanceUnits)
        } else {
            walk(snap, roads, headingX, headingY, distanceUnits, (JOIN_MAX_M / metersPerUnit).toFloat(), path)
        }
        var turnX = path[path.size - 2]
        var turnY = path[path.size - 1]
        val snappedTurn = nearest(matching, turnX, turnY)
        if (snappedTurn != null && snappedTurn.distance * metersPerUnit <= TURN_SNAP_MAX_M) {
            turnX = snappedTurn.x
            turnY = snappedTurn.y
            path.add(turnX)
            path.add(turnY)
        }
        return Preview(path.toFloatArray(), turnX, turnY, true, onRoad, currentRoad, highlight)
    }

    private fun route(
        snap: Snap,
        tiles: List<TileData>,
        matching: List<RelativeRoad>,
        headingX: Float,
        headingY: Float,
        x: Double,
        y: Double,
        distanceUnits: Float,
        metersPerUnit: Double,
        turnsLeft: Boolean?,
        currentRoad: String?,
        highlight: List<FloatArray>,
    ): Preview? {
        val graph = graphOf(tiles, (TOUCH_MAX_M / metersPerUnit).toFloat())
        val blockX = (x - cachedOriginX).toFloat()
        val blockY = (y - cachedOriginY).toFloat()
        val entry = entryNode(graph, snap, headingX, headingY, blockX, blockY)
        val slackUnits = (maxOf(ROUTE_SLACK_MIN_M, distanceUnits * metersPerUnit * ROUTE_SLACK_FRACTION) / metersPerUnit).toFloat()
        val paths = graph.shortestPaths(entry.forward, entry.cost, distanceUnits + slackUnits, entry.behind)
        var best = -1
        var bestError = slackUnits
        for (node in targetNodes(graph, matching, blockX, blockY, (TARGET_MAX_M / metersPerUnit).toFloat())) {
            val error = abs(paths.cost[node] - distanceUnits)
            if (error < bestError) {
                bestError = error
                best = node
            }
        }
        if (best == -1) return null
        val trace = paths.trace(best)
        val path = ArrayList<Float>(trace.size * 2 + 2)
        path.add(snap.x)
        path.add(snap.y)
        for (node in trace) {
            path.add(graph.xs[node] - blockX)
            path.add(graph.ys[node] - blockY)
        }
        val turnX = graph.xs[best] - blockX
        val turnY = graph.ys[best] - blockY
        val afterTurn = afterTurn(matching, path, turnsLeft, (AFTER_TURN_M / metersPerUnit).toFloat())
        return Preview(path.toFloatArray(), turnX, turnY, true, true, currentRoad, highlight, afterTurn, true)
    }

    private fun walkable(kind: RoadKind): Boolean = kind != RoadKind.RAIL && kind != RoadKind.STEPS

    private fun graphOf(
        tiles: List<TileData>,
        touchUnits: Float,
    ): RoadGraph {
        val cached = cachedGraph
        if (cached != null && tiles.size == cachedTiles.size && tiles.indices.all { tiles[it] === cachedTiles[it] }) {
            return cached
        }
        var originX = tiles[0].originX
        var originY = tiles[0].originY
        for (tile in tiles) {
            if (tile.originX < originX) originX = tile.originX
            if (tile.originY < originY) originY = tile.originY
        }
        val blocks =
            tiles.flatMap { tile ->
                tile.roads.filter { walkable(it.kind) }.map { road ->
                    val offsetX = (tile.originX - originX).toFloat()
                    val offsetY = (tile.originY - originY).toFloat()
                    FloatArray(road.points.size) { i -> road.points[i] + if (i % 2 == 0) offsetX else offsetY }
                }
            }
        val graph = RoadGraph.of(blocks, touchUnits)
        cachedTiles = tiles
        cachedGraph = graph
        cachedOriginX = originX
        cachedOriginY = originY
        return graph
    }

    private fun entryNode(
        graph: RoadGraph,
        snap: Snap,
        headingX: Float,
        headingY: Float,
        blockX: Float,
        blockY: Float,
    ): Entry {
        var forward = graph.nodeIds[snap.index][0]
        var forwardCost = Float.MAX_VALUE
        var behind = -1
        var behindCost = Float.MAX_VALUE
        for (node in graph.nodeIds[snap.index]) {
            val dx = graph.xs[node] - blockX - snap.x
            val dy = graph.ys[node] - blockY - snap.y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dx * headingX + dy * headingY >= 0f) {
                if (distance < forwardCost) {
                    forwardCost = distance
                    forward = node
                }
            } else if (distance < behindCost) {
                behindCost = distance
                behind = node
            }
        }
        return Entry(forward, behind, forwardCost)
    }

    private fun targetNodes(
        graph: RoadGraph,
        matching: List<RelativeRoad>,
        blockX: Float,
        blockY: Float,
        radiusUnits: Float,
    ): IntArray {
        val cells = HashMap<Long, MutableList<Int>>()
        val markX = ArrayList<Float>()
        val markY = ArrayList<Float>()
        for (road in matching) {
            val points = road.points
            var i = 0
            while (i + 1 < points.size) {
                addMark(cells, markX, markY, points[i], points[i + 1], radiusUnits)
                if (i + 3 < points.size) {
                    val span = hypot((points[i + 2] - points[i]).toDouble(), (points[i + 3] - points[i + 1]).toDouble())
                    val steps = (span / radiusUnits).toInt()
                    for (step in 1..steps) {
                        val fraction = step.toFloat() / (steps + 1)
                        addMark(
                            cells,
                            markX,
                            markY,
                            points[i] + (points[i + 2] - points[i]) * fraction,
                            points[i + 1] + (points[i + 3] - points[i + 1]) * fraction,
                            radiusUnits,
                        )
                    }
                }
                i += 2
            }
        }
        val found = ArrayList<Int>()
        for (node in 0 until graph.nodeCount) {
            if (nearMark(cells, markX, markY, graph.xs[node] - blockX, graph.ys[node] - blockY, radiusUnits)) found.add(node)
        }
        return found.toIntArray()
    }

    private fun addMark(
        cells: HashMap<Long, MutableList<Int>>,
        markX: ArrayList<Float>,
        markY: ArrayList<Float>,
        x: Float,
        y: Float,
        radiusUnits: Float,
    ) {
        markX.add(x)
        markY.add(y)
        cells.getOrPut(cellKey(x, y, radiusUnits)) { ArrayList() }.add(markX.size - 1)
    }

    private fun nearMark(
        cells: HashMap<Long, MutableList<Int>>,
        markX: ArrayList<Float>,
        markY: ArrayList<Float>,
        x: Float,
        y: Float,
        radiusUnits: Float,
    ): Boolean {
        for (dx in -1..1) {
            for (dy in -1..1) {
                val bucket = cells[cellKey(x + dx * radiusUnits, y + dy * radiusUnits, radiusUnits)] ?: continue
                for (id in bucket) {
                    if (hypot((markX[id] - x).toDouble(), (markY[id] - y).toDouble()) <= radiusUnits) return true
                }
            }
        }
        return false
    }

    private fun cellKey(
        x: Float,
        y: Float,
        cellUnits: Float,
    ): Long = ((x / cellUnits).roundToInt().toLong() shl 32) or ((y / cellUnits).roundToInt().toLong() and 0xffffffffL)

    private fun afterTurn(
        matching: List<RelativeRoad>,
        path: List<Float>,
        turnsLeft: Boolean?,
        lengthUnits: Float,
    ): FloatArray {
        if (turnsLeft == null) return FloatArray(0)
        val turnX = path[path.size - 2]
        val turnY = path[path.size - 1]
        val arrivalX = turnX - path[path.size - 4]
        val arrivalY = turnY - path[path.size - 3]
        val onStreet = nearest(matching, turnX, turnY) ?: return FloatArray(0)
        val points = onStreet.road.points
        val segment = onStreet.segment
        val aheadX = points[segment * 2 + 2] - points[segment * 2]
        val aheadY = points[segment * 2 + 3] - points[segment * 2 + 1]
        val cross = arrivalX * aheadY - arrivalY * aheadX
        val forward = if (turnsLeft) cross < 0f else cross > 0f
        val result = ArrayList<Float>()
        result.add(onStreet.x)
        result.add(onStreet.y)
        var remaining = lengthUnits
        var x = onStreet.x
        var y = onStreet.y
        var index = if (forward) segment + 1 else segment
        while (index >= 0 && index * 2 + 1 < points.size) {
            val nextX = points[index * 2]
            val nextY = points[index * 2 + 1]
            val step = hypot((nextX - x).toDouble(), (nextY - y).toDouble()).toFloat()
            if (step >= remaining) {
                val fraction = remaining / step
                result.add(x + (nextX - x) * fraction)
                result.add(y + (nextY - y) * fraction)
                return result.toFloatArray()
            }
            remaining -= step
            x = nextX
            y = nextY
            if (step > 0f) {
                result.add(x)
                result.add(y)
            }
            index = if (forward) index + 1 else index - 1
        }
        return result.toFloatArray()
    }

    private fun relative(
        tile: TileData,
        road: Road,
        x: Double,
        y: Double,
    ): RelativeRoad {
        val offsetX = tile.originX - x
        val offsetY = tile.originY - y
        val points = FloatArray(road.points.size)
        var i = 0
        while (i + 1 < points.size) {
            points[i] = (road.points[i] + offsetX).toFloat()
            points[i + 1] = (road.points[i + 1] + offsetY).toFloat()
            i += 2
        }
        return RelativeRoad(points, road)
    }

    private fun nearest(
        roads: List<RelativeRoad>,
        px: Float,
        py: Float,
    ): Snap? {
        var best: Snap? = null
        for ((index, road) in roads.withIndex()) {
            val points = road.points
            var i = 0
            while (i + 3 < points.size) {
                val projected = project(points[i], points[i + 1], points[i + 2], points[i + 3], px, py)
                val distance = hypot((projected[0] - px).toDouble(), (projected[1] - py).toDouble())
                if (best == null || distance < best.distance) {
                    best = Snap(road, index, i / 2, projected[0], projected[1], distance)
                }
                i += 2
            }
        }
        return best
    }

    private fun snapToRoad(
        roads: List<RelativeRoad>,
        headingX: Float,
        headingY: Float,
        maxUnits: Double,
        penaltyUnits: Double,
    ): Snap? {
        var best: Snap? = null
        var bestScore = Double.MAX_VALUE
        for ((index, road) in roads.withIndex()) {
            val points = road.points
            var i = 0
            while (i + 3 < points.size) {
                val projected = project(points[i], points[i + 1], points[i + 2], points[i + 3], 0f, 0f)
                val distance = hypot(projected[0].toDouble(), projected[1].toDouble())
                if (distance <= maxUnits) {
                    val aligned = abs(alignment(points[i + 2] - points[i], points[i + 3] - points[i + 1], headingX, headingY))
                    val score = distance + if (aligned < 0.7) penaltyUnits else 0.0
                    if (score < bestScore) {
                        bestScore = score
                        best = Snap(road, index, i / 2, projected[0], projected[1], distance)
                    }
                }
                i += 2
            }
        }
        return best
    }

    private fun walk(
        snap: Snap,
        roads: List<RelativeRoad>,
        headingX: Float,
        headingY: Float,
        distanceUnits: Float,
        joinUnits: Float,
        path: MutableList<Float>,
    ) {
        var road = snap.road
        var points = road.points
        var segment = snap.segment
        var forward =
            alignment(
                points[segment * 2 + 2] - points[segment * 2],
                points[segment * 2 + 3] - points[segment * 2 + 1],
                headingX,
                headingY,
            ) >=
                0
        var x = snap.x
        var y = snap.y
        var directionX = headingX
        var directionY = headingY
        var remaining = distanceUnits
        var hops = 0
        path.add(x)
        path.add(y)
        for (step in 0 until MAX_STEPS) {
            val target = if (forward) segment + 1 else segment
            val targetX = points[target * 2]
            val targetY = points[target * 2 + 1]
            val length = hypot((targetX - x).toDouble(), (targetY - y).toDouble()).toFloat()
            if (length >= remaining) {
                val fraction = if (length == 0f) 0f else remaining / length
                x += (targetX - x) * fraction
                y += (targetY - y) * fraction
                path.add(x)
                path.add(y)
                return
            }
            if (length > 0f) {
                directionX = (targetX - x) / length
                directionY = (targetY - y) / length
            }
            remaining -= length
            x = targetX
            y = targetY
            path.add(x)
            path.add(y)
            if (forward) segment++ else segment--
            val atEnd = if (forward) segment + 1 >= points.size / 2 else segment < 0
            if (!atEnd) continue
            val next = continuation(roads, road, x, y, directionX, directionY, joinUnits)
            if (next == null || ++hops > MAX_HOPS) break
            road = next.first
            points = road.points
            forward = next.second
            segment = if (forward) 0 else points.size / 2 - 2
        }
        path.add(x + directionX * remaining)
        path.add(y + directionY * remaining)
    }

    private fun continuation(
        roads: List<RelativeRoad>,
        current: RelativeRoad,
        x: Float,
        y: Float,
        directionX: Float,
        directionY: Float,
        joinUnits: Float,
    ): Pair<RelativeRoad, Boolean>? {
        var best: Pair<RelativeRoad, Boolean>? = null
        var bestAngle = MAX_JOIN_ANGLE
        for (road in roads) {
            if (road === current) continue
            val points = road.points
            val count = points.size / 2
            if (count < 2) continue
            val startsHere = hypot((points[0] - x).toDouble(), (points[1] - y).toDouble()) <= joinUnits
            val endsHere = hypot((points[points.size - 2] - x).toDouble(), (points[points.size - 1] - y).toDouble()) <= joinUnits
            if (startsHere) {
                val angle = angleBetween(directionX, directionY, points[2] - points[0], points[3] - points[1])
                if (angle < bestAngle) {
                    bestAngle = angle
                    best = road to true
                }
            }
            if (endsHere) {
                val last = points.size - 2
                val angle = angleBetween(directionX, directionY, points[last - 2] - points[last], points[last - 1] - points[last + 1])
                if (angle < bestAngle) {
                    bestAngle = angle
                    best = road to false
                }
            }
        }
        return best
    }

    private fun alignment(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Double {
        val lengthA = sqrt((ax * ax + ay * ay).toDouble())
        val lengthB = sqrt((bx * bx + by * by).toDouble())
        if (lengthA == 0.0 || lengthB == 0.0) return 0.0
        return (ax * bx + ay * by) / (lengthA * lengthB)
    }

    private fun angleBetween(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Double = Math.toDegrees(acos(alignment(ax, ay, bx, by).coerceIn(-1.0, 1.0)))

    private fun project(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        px: Float,
        py: Float,
    ): FloatArray {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return floatArrayOf(ax, ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return floatArrayOf(ax + dx * t, ay + dy * t)
    }
}
