package be.pascu.wristmap.map

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
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
)

private class RelativeRoad(
    val points: FloatArray,
    val road: Road,
)

private class Snap(
    val road: RelativeRoad,
    val segment: Int,
    val x: Float,
    val y: Float,
    val distance: Double,
)

object RoutePreview {
    private const val SNAP_MAX_M = 40.0
    private const val CURRENT_ROAD_MAX_M = 30.0
    private const val TURN_SNAP_MAX_M = 60.0
    private const val JOIN_MAX_M = 3.0
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
    ): Preview {
        val roads = tiles.flatMap { tile -> tile.roads.filter { it.kind != RoadKind.RAIL }.map { relative(tile, it, x, y) } }
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

        if (distanceMeters == null) {
            return Preview(FloatArray(0), 0f, 0f, false, false, currentRoad, matching.map { it.points })
        }

        val headingRad = Math.toRadians(headingDeg)
        val headingX = sin(headingRad).toFloat()
        val headingY = -cos(headingRad).toFloat()
        val distanceUnits = (distanceMeters / metersPerUnit).toFloat()
        val snap = snapToRoad(roads, headingX, headingY, (SNAP_MAX_M / metersPerUnit), ALIGNMENT_PENALTY_M / metersPerUnit)
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
        return Preview(path.toFloatArray(), turnX, turnY, true, onRoad, currentRoad, matching.map { it.points })
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
        for (road in roads) {
            val points = road.points
            var i = 0
            while (i + 3 < points.size) {
                val projected = project(points[i], points[i + 1], points[i + 2], points[i + 3], px, py)
                val distance = hypot((projected[0] - px).toDouble(), (projected[1] - py).toDouble())
                if (best == null || distance < best.distance) {
                    best = Snap(road, i / 2, projected[0], projected[1], distance)
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
        for (road in roads) {
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
                        best = Snap(road, i / 2, projected[0], projected[1], distance)
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
