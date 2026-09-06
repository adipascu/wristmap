package be.pascu.wristmap.pebble

import be.pascu.wristmap.Navigator
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID

class WatchListenerService : BasePebbleListenerService() {

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: WatchIdentifier,
    ): ReceiveResult {
        if (watchappUUID != Protocol.APP_UUID) return ReceiveResult.Nack
        if (data.containsKey(Protocol.HELLO)) {
            Navigator.onWatchHello(
                number(data[Protocol.INBOX_MAX])?.toInt(),
                number(data[Protocol.MAP_VIEW_WIDTH])?.toInt(),
                number(data[Protocol.MAP_VIEW_HEIGHT])?.toInt(),
            )
        }
        number(data[Protocol.ZOOM_LEVEL])?.let { Navigator.onZoomLevel(it.toInt()) }
        return ReceiveResult.Ack
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        if (watchappUUID == Protocol.APP_UUID) Navigator.onWatchAppOpened()
    }

    override fun onAppClosed(watchappUUID: UUID, watch: WatchIdentifier) {
        if (watchappUUID == Protocol.APP_UUID) Navigator.onWatchAppClosed()
    }

    private fun number(item: PebbleDictionaryItem?): Long? = when (item) {
        is PebbleDictionaryItem.UInt8 -> item.value.toLong()
        is PebbleDictionaryItem.UInt16 -> item.value.toLong()
        is PebbleDictionaryItem.UInt32 -> item.value.toLong()
        is PebbleDictionaryItem.Int8 -> item.value.toLong()
        is PebbleDictionaryItem.Int16 -> item.value.toLong()
        is PebbleDictionaryItem.Int32 -> item.value.toLong()
        else -> null
    }
}
