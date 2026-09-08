package org.peblum.mapsforpebble.pebble

import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import kotlin.math.min
import kotlin.math.roundToInt

object FrameChunks {
    fun split(
        frameId: Int,
        width: Int,
        height: Int,
        zoom: Double,
        data: ByteArray,
        chunkSize: Int,
    ): List<PebbleDictionary> {
        val chunks = ArrayList<PebbleDictionary>()
        var offset = 0
        while (offset < data.size) {
            val end = min(offset + chunkSize, data.size)
            val dictionary = HashMap<UInt, PebbleDictionaryItem>()
            dictionary[Protocol.MAP_FRAME] = PebbleDictionaryItem.UInt8(frameId)
            dictionary[Protocol.MAP_OFFSET] = PebbleDictionaryItem.UInt32(offset.toLong())
            dictionary[Protocol.MAP_DATA] = PebbleDictionaryItem.Bytes(data.copyOfRange(offset, end))
            if (offset == 0) {
                dictionary[Protocol.MAP_WIDTH] = PebbleDictionaryItem.UInt16(width)
                dictionary[Protocol.MAP_HEIGHT] = PebbleDictionaryItem.UInt16(height)
                dictionary[Protocol.MAP_TOTAL] = PebbleDictionaryItem.UInt32(data.size.toLong())
                dictionary[Protocol.MAP_ZOOM] = PebbleDictionaryItem.UInt16((zoom * Protocol.ZOOM_SCALE).roundToInt())
            }
            chunks.add(dictionary)
            offset = end
        }
        return chunks
    }
}
