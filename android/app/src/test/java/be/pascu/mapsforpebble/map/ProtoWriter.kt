package be.pascu.mapsforpebble.map

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

    fun tag(
        field: Int,
        wire: Int,
    ): ProtoWriter = varint(((field shl 3) or wire).toLong())

    fun string(
        field: Int,
        value: String,
    ): ProtoWriter {
        val bytes = value.toByteArray(Charsets.UTF_8)
        tag(field, 2).varint(bytes.size.toLong())
        out.write(bytes)
        return this
    }

    fun message(
        field: Int,
        body: ByteArray,
    ): ProtoWriter {
        tag(field, 2).varint(body.size.toLong())
        out.write(body)
        return this
    }

    fun packed(
        field: Int,
        values: LongArray,
    ): ProtoWriter {
        val inner = ProtoWriter()
        for (value in values) inner.varint(value)
        return message(field, inner.bytes())
    }

    fun fixed32(
        field: Int,
        value: Int,
    ): ProtoWriter {
        tag(field, 5)
        for (i in 0 until 4) out.write((value ushr (8 * i)) and 0xff)
        return this
    }

    fun fixed64(
        field: Int,
        value: Long,
    ): ProtoWriter {
        tag(field, 1)
        for (i in 0 until 8) out.write(((value ushr (8 * i)) and 0xff).toInt())
        return this
    }

    fun bytes(): ByteArray = out.toByteArray()

    companion object {
        fun zigzag(value: Int): Long = ((value shl 1) xor (value shr 31)).toLong()
    }
}
