package be.pascu.wristmap.map

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameEncoderTest {
    @Test
    fun packsFourPixelsPerByteMsbFirst() {
        val pixels = intArrayOf(FrameEncoder.COLOR_WHITE, FrameEncoder.COLOR_BLACK, FrameEncoder.COLOR_GRAY, FrameEncoder.COLOR_ACCENT, FrameEncoder.COLOR_BLACK)
        val packed = FrameEncoder.encode(pixels, 5, 1)
        assertEquals(2, packed.size)
        assertEquals(0x1B.toByte(), packed[0])
        assertEquals(0x40.toByte(), packed[1])
    }

    @Test
    fun classifiesNearbyColours() {
        assertEquals(FrameEncoder.WHITE, FrameEncoder.classify(0xFFF0F0F0.toInt()))
        assertEquals(FrameEncoder.BLACK, FrameEncoder.classify(0xFF202020.toInt()))
        assertEquals(FrameEncoder.GRAY, FrameEncoder.classify(0xFF999999.toInt()))
        assertEquals(FrameEncoder.ACCENT, FrameEncoder.classify(0xFF1010F0.toInt()))
    }

    @Test
    fun writesWmfHeaderLittleEndian() {
        val wmf = FrameEncoder.toWmf(200, 152, byteArrayOf(1, 2))
        assertEquals(200.toByte(), wmf[0])
        assertEquals(0.toByte(), wmf[1])
        assertEquals(152.toByte(), wmf[2])
        assertEquals(6, wmf.size)
    }
}
