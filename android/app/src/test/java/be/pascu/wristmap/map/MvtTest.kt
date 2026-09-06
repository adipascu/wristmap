package be.pascu.wristmap.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(value: Long): ProtoWriter {
        var remaining = value
        while (remaining and 0x7fL.inv() != 0L) {
            out.write(((remaining and 0x7f) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        out.write(remaining.toInt())
        return this
    }

    fun tag(field: Int, wire: Int): ProtoWriter = varint(((field shl 3) or wire).toLong())

    fun string(field: Int, value: String): ProtoWriter {
        val bytes = value.toByteArray(Charsets.UTF_8)
        tag(field, 2).varint(bytes.size.toLong())
        out.write(bytes)
        return this
    }

    fun message(field: Int, body: ByteArray): ProtoWriter {
        tag(field, 2).varint(body.size.toLong())
        out.write(body)
        return this
    }

    fun packed(field: Int, values: LongArray): ProtoWriter {
        val inner = ProtoWriter()
        for (value in values) inner.varint(value)
        return message(field, inner.bytes())
    }

    fun bytes(): ByteArray = out.toByteArray()
}

class MvtTest {
    private fun zigzag(value: Int): Long = ((value shl 1) xor (value shr 31)).toLong()

    private fun tile(): ByteArray {
        val feature = ProtoWriter()
            .packed(2, longArrayOf(0, 0, 1, 1))
            .tag(3, 0).varint(2)
            .packed(4, longArrayOf(9, zigzag(10), zigzag(20), 10, zigzag(100), zigzag(0)))
            .bytes()
        val minor = ProtoWriter().string(1, "minor").bytes()
        val name = ProtoWriter().string(1, "Rue de la Loi").bytes()
        val layer = ProtoWriter()
            .tag(15, 0).varint(2)
            .string(1, "transportation_name")
            .message(2, feature)
            .string(3, "class")
            .string(3, "name")
            .message(4, minor)
            .message(4, name)
            .tag(5, 0).varint(4096)
            .bytes()
        return ProtoWriter().message(3, layer).bytes()
    }

    @Test
    fun decodesLayerFeatureAndGeometry() {
        val layers = Mvt.decode(tile(), setOf("transportation_name"))
        assertEquals(1, layers.size)
        val feature = layers[0].features.single()
        assertEquals(MvtFeature.LINESTRING, feature.type)
        assertEquals("minor", feature.tags["class"])
        assertEquals("Rue de la Loi", feature.tags["name"])
        assertArrayEquals(floatArrayOf(10f, 20f, 110f, 20f), feature.geometry.single(), 0f)
    }

    @Test
    fun buildsTileDataInTileLocalUnits() {
        val data = TileData.fromMvt(3, 5, Mvt.decode(tile(), TileData.LAYERS))
        assertEquals(3 * 4096, data.originX)
        assertEquals(5 * 4096, data.originY)
        val road = data.namedRoads.single()
        assertEquals("Rue de la Loi", road.displayName)
        assertEquals(RoadKind.MINOR, road.kind)
        assertArrayEquals(floatArrayOf(10f, 20f, 110f, 20f), road.points, 0f)
    }

    @Test
    fun ignoresUnwantedLayers() {
        assertEquals(0, Mvt.decode(tile(), setOf("water")).size)
    }
}
