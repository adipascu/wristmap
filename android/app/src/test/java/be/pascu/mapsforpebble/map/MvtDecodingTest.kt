package be.pascu.mapsforpebble.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MvtDecodingTest {
    private fun layerWithFeature(
        featureBody: ByteArray,
        vararg values: ByteArray,
    ): ByteArray {
        val layer =
            ProtoWriter()
                .tag(15, 0)
                .varint(2)
                .string(1, "water")
                .message(2, featureBody)
        for (key in listOf("class", "intermittent", "height", "depth", "id", "delta", "signed")) layer.string(3, key)
        for (value in values) layer.message(4, value)
        layer.tag(5, 0).varint(4096)
        return ProtoWriter().message(3, layer.bytes()).bytes()
    }

    @Test
    fun decodesEveryValueTypeAndPolygonRings() {
        val feature =
            ProtoWriter()
                .tag(1, 0)
                .varint(7)
                .packed(2, longArrayOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 0, 99, 99, 0))
                .tag(3, 0)
                .varint(3)
                .packed(
                    4,
                    longArrayOf(
                        9,
                        ProtoWriter.zigzag(0),
                        ProtoWriter.zigzag(0),
                        (2 shl 3) or 2,
                        ProtoWriter.zigzag(10),
                        ProtoWriter.zigzag(0),
                        ProtoWriter.zigzag(0),
                        ProtoWriter.zigzag(10),
                        15,
                    ),
                ).tag(9, 0)
                .varint(1)
                .bytes()
        val values =
            arrayOf(
                ProtoWriter().string(1, "lake").bytes(),
                ProtoWriter().tag(7, 0).varint(1).bytes(),
                ProtoWriter().fixed32(2, java.lang.Float.floatToIntBits(2.5f)).bytes(),
                ProtoWriter().fixed64(3, java.lang.Double.doubleToLongBits(-1.25)).bytes(),
                ProtoWriter().tag(5, 0).varint(42).bytes(),
                ProtoWriter()
                    .tag(6, 0)
                    .varint(ProtoWriter.zigzag(-3))
                    .tag(9, 0)
                    .varint(0)
                    .bytes(),
                ProtoWriter().tag(4, 0).varint(-5L and 0xffffffffL).bytes(),
            )
        val layer = Mvt.decode(layerWithFeature(feature, *values), setOf("water")).single()
        val decoded = layer.features.single()
        assertEquals(MvtFeature.POLYGON, decoded.type)
        assertEquals("lake", decoded.tags["class"])
        assertEquals(true, decoded.tags["intermittent"])
        assertEquals(2.5f, decoded.tags["height"])
        assertEquals(-1.25, decoded.tags["depth"])
        assertEquals(42L, decoded.tags["id"])
        assertEquals(-3L, decoded.tags["delta"])
        assertEquals(4294967291L, decoded.tags["signed"])
        assertEquals(7, decoded.tags.size)
        assertArrayEquals(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 0f), decoded.geometry.single(), 0f)
    }

    @Test
    fun skipsUnknownFieldsOfEveryWireType() {
        val feature =
            ProtoWriter()
                .fixed64(20, 1L)
                .fixed32(21, 1)
                .string(22, "x")
                .tag(23, 0)
                .varint(5)
                .tag(3, 0)
                .varint(1)
                .packed(4, longArrayOf(9, ProtoWriter.zigzag(3), ProtoWriter.zigzag(4)))
                .bytes()
        val tile =
            ProtoWriter()
                .tag(7, 0)
                .varint(1)
                .message(
                    3,
                    ProtoWriter()
                        .string(
                            1,
                            "water",
                        ).message(2, feature)
                        .message(4, ProtoWriter().string(1, "x").bytes())
                        .tag(5, 0)
                        .varint(4096)
                        .bytes(),
                ).bytes()
        val decoded =
            Mvt
                .decode(tile, setOf("water"))
                .single()
                .features
                .single()
        assertEquals(MvtFeature.POINT, decoded.type)
        assertArrayEquals(floatArrayOf(3f, 4f), decoded.geometry.single(), 0f)
    }

    @Test
    fun rejectsUnknownWireTypesAndGeometryCommands() {
        val badWire = ProtoWriter().tag(9, 4).bytes()
        assertThrows(IllegalStateException::class.java) { Mvt.decode(badWire, setOf("water")) }
        assertThrows(IllegalStateException::class.java) { Mvt.decode(ProtoWriter().tag(9, 3).bytes(), setOf("water")) }
        assertThrows(IllegalStateException::class.java) { Mvt.decodeGeometry(longArrayOf(3)) }
    }

    @Test
    fun startsANewPartOnEachMoveTo() {
        val commands =
            longArrayOf(
                (2 shl 3) or 1,
                ProtoWriter.zigzag(1),
                ProtoWriter.zigzag(1),
                ProtoWriter.zigzag(5),
                ProtoWriter.zigzag(5),
                (1 shl 3) or 2,
                ProtoWriter.zigzag(1),
                ProtoWriter.zigzag(0),
            )
        val parts = Mvt.decodeGeometry(commands)
        assertEquals(2, parts.size)
        assertArrayEquals(floatArrayOf(1f, 1f), parts[0], 0f)
        assertArrayEquals(floatArrayOf(6f, 6f, 7f, 6f), parts[1], 0f)
        assertEquals(0, Mvt.decodeGeometry(longArrayOf(15)).size)
    }

    @Test
    fun readsFixedWidthNumbersLittleEndian() {
        val reader = ProtoReader(byteArrayOf(0x78, 0x56, 0x34, 0x12, 1, 0, 0, 0, 0, 0, 0, 0), 0, 12)
        assertEquals(0x12345678, reader.readFixed32())
        assertEquals(1L, reader.readFixed64())
        assertEquals(false, reader.hasMore())
    }
}
