package be.pascu.wristmap.pebble

import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameChunksTest {
    private val data = ByteArray(7600) { it.toByte() }

    @Test
    fun wholeFrameFitsInOneLargeMessage() {
        val chunks = FrameChunks.split(3, 200, 152, data, 8000)
        assertEquals(1, chunks.size)
        val first = chunks[0]
        assertEquals(PebbleDictionaryItem.UInt16(200), first[Protocol.MAP_WIDTH])
        assertEquals(PebbleDictionaryItem.UInt16(152), first[Protocol.MAP_HEIGHT])
        assertEquals(PebbleDictionaryItem.UInt32(7600L), first[Protocol.MAP_TOTAL])
        assertEquals(PebbleDictionaryItem.UInt8(3), first[Protocol.MAP_FRAME])
        assertEquals(7600, (first[Protocol.MAP_DATA] as PebbleDictionaryItem.Bytes).value.size)
    }

    @Test
    fun smallInboxSplitsIntoSequentialChunks() {
        val chunks = FrameChunks.split(1, 200, 152, data, 2000)
        assertEquals(4, chunks.size)
        assertEquals(listOf(0L, 2000L, 4000L, 6000L), chunks.map { (it[Protocol.MAP_OFFSET] as PebbleDictionaryItem.UInt32).value.toLong() })
        assertTrue(chunks[0].containsKey(Protocol.MAP_TOTAL))
        assertFalse(chunks[1].containsKey(Protocol.MAP_TOTAL))
        assertEquals(1600, (chunks[3][Protocol.MAP_DATA] as PebbleDictionaryItem.Bytes).value.size)
        assertEquals(6000.toByte(), (chunks[3][Protocol.MAP_DATA] as PebbleDictionaryItem.Bytes).value[0])
    }
}
