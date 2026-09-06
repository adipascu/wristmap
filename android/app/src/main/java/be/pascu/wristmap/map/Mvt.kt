package be.pascu.wristmap.map

class MvtLayer(
    val name: String,
    val extent: Int,
    val features: List<MvtFeature>,
)

class MvtFeature(
    val type: Int,
    val tags: Map<String, Any>,
    val geometry: List<FloatArray>,
) {
    companion object {
        const val POINT = 1
        const val LINESTRING = 2
        const val POLYGON = 3
    }
}

class ProtoReader(
    private val buffer: ByteArray,
    private var position: Int,
    private val end: Int,
) {
    fun hasMore(): Boolean = position < end

    fun readVarint(): Long {
        var shift = 0
        var result = 0L
        while (true) {
            val byte = buffer[position++].toInt()
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
    }

    fun readTag(): Int = readVarint().toInt()

    fun readMessage(): ProtoReader {
        val length = readVarint().toInt()
        val reader = ProtoReader(buffer, position, position + length)
        position += length
        return reader
    }

    fun readString(): String {
        val length = readVarint().toInt()
        val text = String(buffer, position, length, Charsets.UTF_8)
        position += length
        return text
    }

    fun readPackedVarints(): LongArray {
        val inner = readMessage()
        val values = ArrayList<Long>()
        while (inner.hasMore()) values.add(inner.readVarint())
        return values.toLongArray()
    }

    fun readFixed32(): Int {
        var value = 0
        for (i in 0 until 4) value = value or ((buffer[position + i].toInt() and 0xff) shl (8 * i))
        position += 4
        return value
    }

    fun readFixed64(): Long {
        var value = 0L
        for (i in 0 until 8) value = value or ((buffer[position + i].toLong() and 0xff) shl (8 * i))
        position += 8
        return value
    }

    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> position += 8
            2 -> position += readVarint().toInt()
            5 -> position += 4
            else -> throw IllegalStateException("unsupported wire type $wireType")
        }
    }
}

object Mvt {
    fun decode(
        bytes: ByteArray,
        wantedLayers: Set<String>,
    ): List<MvtLayer> {
        val layers = ArrayList<MvtLayer>()
        val reader = ProtoReader(bytes, 0, bytes.size)
        while (reader.hasMore()) {
            val tag = reader.readTag()
            if (tag ushr 3 == 3) {
                val layer = decodeLayer(reader.readMessage(), wantedLayers)
                if (layer != null) layers.add(layer)
            } else {
                reader.skip(tag and 7)
            }
        }
        return layers
    }

    private class RawFeature(
        val type: Int,
        val tags: LongArray,
        val geometry: LongArray,
    )

    private fun decodeLayer(
        reader: ProtoReader,
        wantedLayers: Set<String>,
    ): MvtLayer? {
        var name = ""
        var extent = 4096
        val keys = ArrayList<String>()
        val values = ArrayList<Any>()
        val featureReaders = ArrayList<ProtoReader>()
        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag ushr 3) {
                1 -> name = reader.readString()
                2 -> featureReaders.add(reader.readMessage())
                3 -> keys.add(reader.readString())
                4 -> values.add(decodeValue(reader.readMessage()))
                5 -> extent = reader.readVarint().toInt()
                else -> reader.skip(tag and 7)
            }
        }
        if (name !in wantedLayers) return null
        val features =
            featureReaders.map { decodeFeature(it) }.map { raw ->
                val tags = HashMap<String, Any>()
                var i = 0
                while (i + 1 < raw.tags.size) {
                    val key = keys.getOrNull(raw.tags[i].toInt())
                    val value = values.getOrNull(raw.tags[i + 1].toInt())
                    if (key != null && value != null) tags[key] = value
                    i += 2
                }
                MvtFeature(raw.type, tags, decodeGeometry(raw.geometry))
            }
        return MvtLayer(name, extent, features)
    }

    private fun decodeFeature(reader: ProtoReader): RawFeature {
        var type = 0
        var tags = LongArray(0)
        var geometry = LongArray(0)
        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag ushr 3) {
                2 -> tags = reader.readPackedVarints()
                3 -> type = reader.readVarint().toInt()
                4 -> geometry = reader.readPackedVarints()
                else -> reader.skip(tag and 7)
            }
        }
        return RawFeature(type, tags, geometry)
    }

    private fun decodeValue(reader: ProtoReader): Any {
        var value: Any = ""
        while (reader.hasMore()) {
            val tag = reader.readTag()
            value =
                when (tag ushr 3) {
                    1 -> {
                        reader.readString()
                    }

                    2 -> {
                        java.lang.Float.intBitsToFloat(reader.readFixed32())
                    }

                    3 -> {
                        java.lang.Double.longBitsToDouble(reader.readFixed64())
                    }

                    4, 5 -> {
                        reader.readVarint()
                    }

                    6 -> {
                        zigzag(reader.readVarint())
                    }

                    7 -> {
                        reader.readVarint() != 0L
                    }

                    else -> {
                        reader.skip(tag and 7)
                        value
                    }
                }
        }
        return value
    }

    private fun zigzag(value: Long): Long = (value ushr 1) xor -(value and 1)

    fun decodeGeometry(commands: LongArray): List<FloatArray> {
        val parts = ArrayList<FloatArray>()
        var current = ArrayList<Float>()
        var x = 0L
        var y = 0L
        var i = 0
        while (i < commands.size) {
            val command = commands[i++]
            val id = (command and 7).toInt()
            val count = (command ushr 3).toInt()
            when (id) {
                1 -> {
                    repeat(count) {
                        if (current.size >= 2) parts.add(current.toFloatArray())
                        current = ArrayList()
                        x += zigzag(commands[i++])
                        y += zigzag(commands[i++])
                        current.add(x.toFloat())
                        current.add(y.toFloat())
                    }
                }

                2 -> {
                    repeat(count) {
                        x += zigzag(commands[i++])
                        y += zigzag(commands[i++])
                        current.add(x.toFloat())
                        current.add(y.toFloat())
                    }
                }

                7 -> {
                    if (current.size >= 2) {
                        current.add(current[0])
                        current.add(current[1])
                    }
                }

                else -> {
                    throw IllegalStateException("unknown geometry command $id")
                }
            }
        }
        if (current.size >= 2) parts.add(current.toFloatArray())
        return parts
    }
}
