package be.pascu.wristmap.map

import java.text.Normalizer

enum class RoadKind { MOTORWAY, PRIMARY, SECONDARY, MINOR, SERVICE, PATH, CYCLEWAY, RAIL }

class Road(val points: FloatArray, val kind: RoadKind, val names: List<String>) {
    val displayName: String? get() = names.firstOrNull()
    val normalizedNames: List<String> = names.map { Names.normalize(it) }
}

class Area(val rings: List<FloatArray>)

class TileData(
    val originX: Int,
    val originY: Int,
    val roads: List<Road>,
    val namedRoads: List<Road>,
    val water: List<Area>,
    val waterways: List<FloatArray>,
) {
    val approximateBytes: Int =
        (roads.sumOf { it.points.size } + namedRoads.sumOf { it.points.size } +
            water.sumOf { area -> area.rings.sumOf { it.size } } + waterways.sumOf { it.size }) * 4 +
            (roads.size + namedRoads.size + water.size + waterways.size) * 64

    companion object {
        val LAYERS = setOf("transportation", "transportation_name", "water", "waterway")

        fun fromMvt(tileX: Int, tileY: Int, layers: List<MvtLayer>): TileData {
            val roads = ArrayList<Road>()
            val namedRoads = ArrayList<Road>()
            val water = ArrayList<Area>()
            val waterways = ArrayList<FloatArray>()
            for (layer in layers) {
                val scale = WebMercator.EXTENT.toFloat() / layer.extent
                fun scaled(points: FloatArray): FloatArray =
                    if (scale == 1f) points else FloatArray(points.size) { points[it] * scale }
                when (layer.name) {
                    "transportation" -> for (feature in layer.features) {
                        if (feature.type != MvtFeature.LINESTRING) continue
                        val kind = roadKind(feature.tags) ?: continue
                        for (part in feature.geometry) roads.add(Road(scaled(part), kind, emptyList()))
                    }
                    "transportation_name" -> for (feature in layer.features) {
                        if (feature.type != MvtFeature.LINESTRING) continue
                        val kind = roadKind(feature.tags) ?: RoadKind.MINOR
                        val names = names(feature.tags)
                        if (names.isEmpty()) continue
                        for (part in feature.geometry) namedRoads.add(Road(scaled(part), kind, names))
                    }
                    "water" -> for (feature in layer.features) {
                        if (feature.type != MvtFeature.POLYGON) continue
                        water.add(Area(feature.geometry.map { scaled(it) }))
                    }
                    "waterway" -> for (feature in layer.features) {
                        if (feature.type != MvtFeature.LINESTRING) continue
                        for (part in feature.geometry) waterways.add(scaled(part))
                    }
                }
            }
            return TileData(tileX * WebMercator.EXTENT, tileY * WebMercator.EXTENT, roads, namedRoads, water, waterways)
        }

        private fun roadKind(tags: Map<String, Any>): RoadKind? {
            val subclass = tags["subclass"]?.toString()
            return when (tags["class"]?.toString()) {
                "motorway", "trunk" -> RoadKind.MOTORWAY
                "primary" -> RoadKind.PRIMARY
                "secondary", "tertiary" -> RoadKind.SECONDARY
                "minor", "busway", "bus_guideway" -> RoadKind.MINOR
                "service", "track", "raceway" -> RoadKind.SERVICE
                "path" -> if (subclass == "cycleway") RoadKind.CYCLEWAY else RoadKind.PATH
                "rail", "transit" -> RoadKind.RAIL
                else -> null
            }
        }

        private fun names(tags: Map<String, Any>): List<String> {
            val ordered = ArrayList<String>()
            tags["name"]?.toString()?.takeIf { it.isNotBlank() }?.let { ordered.add(it) }
            for ((key, value) in tags) {
                if ((key.startsWith("name:") || key.startsWith("name_") || key == "ref") && value is String && value.isNotBlank()) {
                    ordered.add(value)
                }
            }
            return ordered.distinct()
        }
    }
}

object Names {
    private val marks = Regex("""\p{M}""")
    private val strip = Regex("""[^\p{L}\p{N} ]""")
    private val spaces = Regex("""\s+""")

    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase()
            .replace(strip, " ")
            .replace(spaces, " ")
            .trim()

    fun matches(candidates: List<String>, wanted: String): Boolean {
        if (wanted.length < 3) return false
        return candidates.any { candidate ->
            candidate == wanted ||
                (wanted.length >= 4 && candidate.contains(wanted)) ||
                (candidate.length >= 4 && wanted.contains(candidate))
        }
    }
}
