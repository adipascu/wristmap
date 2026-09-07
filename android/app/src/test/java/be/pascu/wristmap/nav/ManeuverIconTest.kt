package be.pascu.wristmap.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverIconTest {
    @Test
    fun packsOpaquePixelsMsbFirst() {
        val pixels = IntArray(ManeuverIcon.SIZE * ManeuverIcon.SIZE)
        pixels[0] = 0xFFFFFFFF.toInt()
        pixels[7] = 0xFFFFFFFF.toInt()
        pixels[ManeuverIcon.SIZE + 8] = 0xFFFFFFFF.toInt()
        val packed = ManeuverIcon.packPixels(pixels)
        assertEquals(0x81.toByte(), packed[0])
        assertEquals(0x80.toByte(), packed[ManeuverIcon.ROW_BYTES + 1])
    }

    @Test
    fun opaqueIconsCompareAgainstTheCornerColour() {
        val pixels = IntArray(ManeuverIcon.SIZE * ManeuverIcon.SIZE) { 0xFF2040C0.toInt() }
        pixels[5] = 0xFFFFFFFF.toInt()
        pixels[6] = 0xFF2242C2.toInt()
        val packed = ManeuverIcon.packPixels(pixels)
        assertEquals(0x04.toByte(), packed[0])
    }
}
