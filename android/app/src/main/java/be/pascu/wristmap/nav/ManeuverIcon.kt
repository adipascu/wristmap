package be.pascu.wristmap.nav

import android.graphics.Bitmap

object ManeuverIcon {
    const val SIZE = 40
    const val ROW_BYTES = 5
    const val BYTES = ROW_BYTES * SIZE

    fun pack(source: Bitmap?): ByteArray? {
        if (source == null) return null
        return try {
            val software = if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            }
            val scaled = Bitmap.createScaledBitmap(software, SIZE, SIZE, true)
            val pixels = IntArray(SIZE * SIZE)
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            if (scaled !== software) scaled.recycle()
            if (software !== source) software.recycle()
            packPixels(pixels)
        } catch (e: Throwable) {
            null
        }
    }

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

    private fun differs(pixel: Int, background: Int): Boolean {
        val dr = ((pixel ushr 16) and 0xff) - ((background ushr 16) and 0xff)
        val dg = ((pixel ushr 8) and 0xff) - ((background ushr 8) and 0xff)
        val db = (pixel and 0xff) - (background and 0xff)
        return dr * dr + dg * dg + db * db > 64 * 64
    }
}
