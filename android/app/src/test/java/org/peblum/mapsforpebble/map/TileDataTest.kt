package org.peblum.mapsforpebble.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileDataTest {
    private fun line(vararg tags: Pair<String, Any>) = MvtFeature(MvtFeature.LINESTRING, tags.toMap(), listOf(floatArrayOf(0f, 0f, 2f, 2f)))

    @Test
    fun classifiesEveryRoadClassAndSkipsTheRest() {
        val layer =
            MvtLayer(
                "transportation",
                2048,
                listOf(
                    line("class" to "motorway"),
                    line("class" to "trunk"),
                    line("class" to "primary"),
                    line("class" to "secondary"),
                    line("class" to "tertiary"),
                    line("class" to "minor"),
                    line("class" to "busway"),
                    line("class" to "bus_guideway"),
                    line("class" to "service"),
                    line("class" to "track"),
                    line("class" to "raceway"),
                    line("class" to "path", "subclass" to "cycleway"),
                    line("class" to "path", "subclass" to "steps"),
                    line("class" to "path", "subclass" to "footway"),
                    line("class" to "rail"),
                    line("class" to "transit"),
                    line("class" to "ferry"),
                    line(),
                    MvtFeature(MvtFeature.POLYGON, mapOf("class" to "minor"), listOf(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f))),
                ),
            )
        val data = TileData.fromMvt(1, 2, listOf(layer))
        assertEquals(
            listOf(
                RoadKind.MOTORWAY,
                RoadKind.MOTORWAY,
                RoadKind.PRIMARY,
                RoadKind.SECONDARY,
                RoadKind.SECONDARY,
                RoadKind.MINOR,
                RoadKind.MINOR,
                RoadKind.MINOR,
                RoadKind.SERVICE,
                RoadKind.SERVICE,
                RoadKind.SERVICE,
                RoadKind.CYCLEWAY,
                RoadKind.STEPS,
                RoadKind.PATH,
                RoadKind.RAIL,
                RoadKind.RAIL,
            ),
            data.roads.map { it.kind },
        )
        assertArrayEquals(floatArrayOf(0f, 0f, 4f, 4f), data.roads.first().points, 0f)
        assertEquals(4096, data.originX)
        assertEquals(8192, data.originY)
        assertTrue(data.approximateBytes > 0)
    }

    @Test
    fun keepsNamesFromEveryNameLikeTagAndDropsUnnamedRoads() {
        val layer =
            MvtLayer(
                "transportation_name",
                4096,
                listOf(
                    line(
                        "class" to "primary",
                        "name" to "Rue Neuve",
                        "name:nl" to "Nieuwstraat",
                        "name_int" to "Rue Neuve",
                        "ref" to "N1",
                        "name:de" to 7L,
                        "name:en" to " ",
                        "layer" to 1L,
                    ),
                    line("class" to "unknown", "name:fr" to "Sans classe"),
                    line("class" to "minor", "name" to "  "),
                    line("class" to "minor", "name" to 12L),
                    MvtFeature(MvtFeature.POINT, mapOf("name" to "Point"), listOf(floatArrayOf(1f, 1f))),
                ),
            )
        val data = TileData.fromMvt(0, 0, listOf(layer))
        assertEquals(2, data.namedRoads.size)
        assertEquals(listOf("Rue Neuve", "Nieuwstraat", "N1"), data.namedRoads[0].names)
        assertEquals(RoadKind.PRIMARY, data.namedRoads[0].kind)
        assertEquals(RoadKind.MINOR, data.namedRoads[1].kind)
        assertEquals("Sans classe", data.namedRoads[1].displayName)
    }

    @Test
    fun keepsWaterPolygonsAndWaterwayLines() {
        val water =
            MvtLayer(
                "water",
                4096,
                listOf(MvtFeature(MvtFeature.POLYGON, emptyMap(), listOf(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f))), line()),
            )
        val waterway =
            MvtLayer(
                "waterway",
                4096,
                listOf(line("class" to "river"), MvtFeature(MvtFeature.POLYGON, emptyMap(), listOf(floatArrayOf(0f, 0f)))),
            )
        val data = TileData.fromMvt(0, 0, listOf(water, waterway, MvtLayer("building", 4096, listOf(line()))))
        assertEquals(1, data.water.size)
        assertEquals(1, data.water[0].rings.size)
        assertEquals(1, data.waterways.size)
        assertEquals(0, data.roads.size)
    }

    @Test
    fun nameMatchingRespectsMinimumLengths() {
        assertFalse(Names.matches(listOf("rue"), "ru"))
        assertTrue(Names.matches(listOf("rue"), "rue"))
        assertFalse(Names.matches(listOf("rue de la loi"), "loi"))
        assertTrue(Names.matches(listOf("wetstraat"), "rue de la loi wetstraat"))
        assertFalse(Names.matches(listOf("loi"), "rue de la loi wetstraat"))
        assertFalse(Names.matches(listOf("abc"), "rue de la loi"))
    }
}
