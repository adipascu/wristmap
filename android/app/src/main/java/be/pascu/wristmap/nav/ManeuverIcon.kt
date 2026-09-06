package be.pascu.wristmap.nav

object ManeuverIcon {
    const val SIZE = 40
    const val ROW_BYTES = 5
    const val BYTES = ROW_BYTES * SIZE

    fun packPixels(pixels: IntArray): ByteArray {
        val translucent = pixels.any { alpha(it) < 250 }
        val background = pixels[0]
        val out = ByteArray(BYTES)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val pixel = pixels[y * SIZE + x]
                val ink = if (translucent) alpha(pixel) >= 128 else differs(pixel, background)
                if (ink) {
                    val index = y * ROW_BYTES + (x ushr 3)
                    out[index] = (out[index].toInt() or (0x80 ushr (x and 7))).toByte()
                }
            }
        }
        return out
    }

    private fun alpha(pixel: Int): Int = pixel ushr 24

    private fun differs(
        pixel: Int,
        background: Int,
    ): Boolean {
        val dr = ((pixel ushr 16) and 0xff) - ((background ushr 16) and 0xff)
        val dg = ((pixel ushr 8) and 0xff) - ((background ushr 8) and 0xff)
        val db = (pixel and 0xff) - (background and 0xff)
        return dr * dr + dg * dg + db * db > 64 * 64
    }
}
