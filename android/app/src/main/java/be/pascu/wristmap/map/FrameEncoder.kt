package be.pascu.wristmap.map

object FrameEncoder {
    const val WHITE = 0
    const val BLACK = 1
    const val GRAY = 2
    const val ACCENT = 3

    const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    const val COLOR_BLACK = 0xFF000000.toInt()
    const val COLOR_GRAY = 0xFFAAAAAA.toInt()
    const val COLOR_ACCENT = 0xFF0000FF.toInt()

    fun strideOf(width: Int): Int = (width * 2 + 7) / 8

    fun classify(argb: Int): Int {
        val r = (argb ushr 16) and 0xff
        val g = (argb ushr 8) and 0xff
        val b = argb and 0xff
        if (b >= 128 && r < 128 && g < 128) return ACCENT
        val luminance = (r + g + b) / 3
        return when {
            luminance >= 212 -> WHITE
            luminance < 85 -> BLACK
            else -> GRAY
        }
    }

    fun encode(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): ByteArray {
        val stride = strideOf(width)
        val out = ByteArray(stride * height)
        for (y in 0 until height) {
            val rowStart = y * stride
            val pixelRow = y * width
            for (x in 0 until width) {
                val index = classify(pixels[pixelRow + x])
                val position = rowStart + (x ushr 2)
                out[position] = (out[position].toInt() or (index shl (6 - 2 * (x and 3)))).toByte()
            }
        }
        return out
    }

    fun toWmf(
        width: Int,
        height: Int,
        data: ByteArray,
    ): ByteArray {
        val out = ByteArray(4 + data.size)
        out[0] = (width and 0xff).toByte()
        out[1] = (width ushr 8).toByte()
        out[2] = (height and 0xff).toByte()
        out[3] = (height ushr 8).toByte()
        data.copyInto(out, 4)
        return out
    }
}
