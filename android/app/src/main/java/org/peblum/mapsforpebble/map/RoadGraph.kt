package org.peblum.mapsforpebble.map

import java.util.PriorityQueue
import kotlin.math.hypot
import kotlin.math.roundToInt

class RoadGraph(
    val xs: FloatArray,
    val ys: FloatArray,
    private val neighbourStart: IntArray,
    private val neighbours: IntArray,
    private val lengths: FloatArray,
    val nodeIds: Array<IntArray>,
) {
    val nodeCount: Int get() = xs.size

    fun neighboursOf(node: Int): IntRange = neighbourStart[node] until neighbourStart[node + 1]

    fun neighbourAt(slot: Int): Int = neighbours[slot]

    fun lengthAt(slot: Int): Float = lengths[slot]

    fun shortestPaths(
        source: Int,
        sourceCost: Float,
        maxCost: Float,
        blocked: Int,
    ): Route {
        val cost = FloatArray(nodeCount) { Float.MAX_VALUE }
        val parent = IntArray(nodeCount) { -1 }
        val queue = PriorityQueue<Step>(compareBy { it.cost })
        cost[source] = sourceCost
        queue.add(Step(source, sourceCost))
        while (queue.isNotEmpty()) {
            val head = queue.remove()
            if (head.cost > cost[head.node]) continue
            if (head.cost > maxCost) break
            for (slot in neighboursOf(head.node)) {
                val next = neighbourAt(slot)
                if (next == blocked) continue
                val candidate = head.cost + lengthAt(slot)
                if (candidate < cost[next]) {
                    cost[next] = candidate
                    parent[next] = head.node
                    queue.add(Step(next, candidate))
                }
            }
        }
        return Route(cost, parent)
    }

    private class Step(
        val node: Int,
        val cost: Float,
    )

    class Route(
        val cost: FloatArray,
        private val parent: IntArray,
    ) {
        fun trace(node: Int): IntArray {
            var count = 0
            var walk = node
            while (walk != -1) {
                count++
                walk = parent[walk]
            }
            val result = IntArray(count)
            walk = node
            while (walk != -1) {
                count--
                result[count] = walk
                walk = parent[walk]
            }
            return result
        }
    }

    companion object {
        private const val QUANTUM = 0.05f

        fun of(
            roads: List<FloatArray>,
            touchUnits: Float,
        ): RoadGraph {
            val ids = HashMap<Long, Int>()
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            val from = ArrayList<Int>()
            val to = ArrayList<Int>()
            val endpoints = endpointIndex(roads, touchUnits)
            val nodeIds =
                Array(roads.size) { road ->
                    val points = roads[road]
                    val assigned = ArrayList<Int>(points.size / 2)
                    var previous = -1
                    var i = 0
                    while (i + 1 < points.size) {
                        val node = nodeOf(ids, xs, ys, points[i], points[i + 1])
                        assigned.add(node)
                        if (previous != -1 && previous != node) {
                            from.add(previous)
                            to.add(node)
                        }
                        previous = node
                        if (i + 3 < points.size) {
                            for (touch in touching(endpoints, points, i, touchUnits)) {
                                val split = nodeOf(ids, xs, ys, touch[0], touch[1])
                                if (split != previous) {
                                    from.add(previous)
                                    to.add(split)
                                    assigned.add(split)
                                    previous = split
                                }
                            }
                        }
                        i += 2
                    }
                    assigned.toIntArray()
                }
            return assemble(xs, ys, from, to, nodeIds)
        }

        private fun endpointIndex(
            roads: List<FloatArray>,
            touchUnits: Float,
        ): HashMap<Long, MutableList<FloatArray>> {
            val index = HashMap<Long, MutableList<FloatArray>>()
            for (points in roads) {
                if (points.size < 4) continue
                for (offset in intArrayOf(0, points.size - 2)) {
                    val corner = floatArrayOf(points[offset], points[offset + 1])
                    index.getOrPut(cell(corner[0], corner[1], touchUnits)) { ArrayList() }.add(corner)
                }
            }
            return index
        }

        private fun touching(
            endpoints: HashMap<Long, MutableList<FloatArray>>,
            points: FloatArray,
            i: Int,
            touchUnits: Float,
        ): List<FloatArray> {
            val ax = points[i]
            val ay = points[i + 1]
            val bx = points[i + 2]
            val by = points[i + 3]
            val lengthSquared = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
            if (lengthSquared == 0f) return emptyList()
            val found = ArrayList<FloatArray>()
            val seen = HashSet<Long>()
            var step = 0f
            while (step <= 1f) {
                val alongX = ax + (bx - ax) * step
                val alongY = ay + (by - ay) * step
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val bucket = endpoints[cell(alongX + dx * touchUnits, alongY + dy * touchUnits, touchUnits)] ?: continue
                        for (corner in bucket) {
                            val t = ((corner[0] - ax) * (bx - ax) + (corner[1] - ay) * (by - ay)) / lengthSquared
                            if (t <= 0f || t >= 1f) continue
                            val px = ax + (bx - ax) * t
                            val py = ay + (by - ay) * t
                            if (hypot((corner[0] - px).toDouble(), (corner[1] - py).toDouble()) > touchUnits) continue
                            if (seen.add(key(corner[0], corner[1]))) found.add(floatArrayOf(corner[0], corner[1], t))
                        }
                    }
                }
                step += touchUnits / hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
            }
            found.sortBy { it[2] }
            return found
        }

        private fun cell(
            x: Float,
            y: Float,
            size: Float,
        ): Long = ((x / size).roundToInt().toLong() shl 32) or ((y / size).roundToInt().toLong() and 0xffffffffL)

        private fun nodeOf(
            ids: HashMap<Long, Int>,
            xs: ArrayList<Float>,
            ys: ArrayList<Float>,
            x: Float,
            y: Float,
        ): Int {
            val cellKey = key(x, y)
            val existing = ids[cellKey]
            if (existing != null) return existing
            val id = xs.size
            ids[cellKey] = id
            xs.add(x)
            ys.add(y)
            return id
        }

        fun key(
            x: Float,
            y: Float,
        ): Long = ((x / QUANTUM).roundToInt().toLong() shl 32) or ((y / QUANTUM).roundToInt().toLong() and 0xffffffffL)

        private fun assemble(
            xs: ArrayList<Float>,
            ys: ArrayList<Float>,
            from: ArrayList<Int>,
            to: ArrayList<Int>,
            nodeIds: Array<IntArray>,
        ): RoadGraph {
            val count = xs.size
            val degree = IntArray(count)
            for (i in from.indices) {
                degree[from[i]]++
                degree[to[i]]++
            }
            val start = IntArray(count + 1)
            for (node in 0 until count) start[node + 1] = start[node] + degree[node]
            val cursor = start.copyOf()
            val neighbours = IntArray(from.size * 2)
            val lengths = FloatArray(from.size * 2)
            for (i in from.indices) {
                val a = from[i]
                val b = to[i]
                val length = hypot((xs[b] - xs[a]).toDouble(), (ys[b] - ys[a]).toDouble()).toFloat()
                neighbours[cursor[a]] = b
                lengths[cursor[a]] = length
                cursor[a]++
                neighbours[cursor[b]] = a
                lengths[cursor[b]] = length
                cursor[b]++
            }
            return RoadGraph(xs.toFloatArray(), ys.toFloatArray(), start, neighbours, lengths, nodeIds)
        }
    }
}
