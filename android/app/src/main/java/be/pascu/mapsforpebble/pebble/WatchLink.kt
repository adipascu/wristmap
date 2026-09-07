package be.pascu.mapsforpebble.pebble

import android.content.Context
import be.pascu.mapsforpebble.nav.NavState
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

sealed class SendResult {
    object Ok : SendResult() {
        override fun toString() = "ok"
    }

    object NoPebbleApp : SendResult() {
        override fun toString() = "Pebble app not installed"
    }

    object NoWatch : SendResult() {
        override fun toString() = "no watch connected"
    }

    object AppNotOpen : SendResult() {
        override fun toString() = "watchapp not open"
    }

    object NoPermission : SendResult() {
        override fun toString() = "companion not allowed by watchapp"
    }

    object Nacked : SendResult() {
        override fun toString() = "watch rejected message"
    }

    object Timeout : SendResult() {
        override fun toString() = "timeout"
    }

    data class Failed(
        val reason: String,
    ) : SendResult() {
        override fun toString() = reason
    }
}

class WatchLink(
    context: Context,
) {
    private val sender = DefaultPebbleSender(context.applicationContext)
    private val requests = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var inboxMax: Int = DEFAULT_INBOX_MAX

    @Volatile var mapWidth: Int = DEFAULT_MAP_WIDTH

    @Volatile var mapHeight: Int = DEFAULT_MAP_HEIGHT
    private var frameId = 0

    val chunkSize: Int get() = (inboxMax - CHUNK_OVERHEAD).coerceIn(MIN_CHUNK, MAX_CHUNK)

    suspend fun launchApp(): SendResult = request { sender.startAppOnTheWatch(Protocol.APP_UUID) }

    suspend fun stopApp(): SendResult = request { sender.stopAppOnTheWatch(Protocol.APP_UUID) }

    suspend fun sendNav(
        state: NavState,
        arrow: ByteArray?,
        cue: ByteArray? = null,
    ): SendResult = send(navDictionary(state, arrow, cue))

    suspend fun sendStopped(): SendResult = send(mapOf(Protocol.NAV_ACTIVE to PebbleDictionaryItem.UInt8(0)))

    suspend fun sendFrame(
        width: Int,
        height: Int,
        zoom: Double,
        data: ByteArray,
    ): SendResult {
        frameId = (frameId + 1) and 0xff
        for (chunk in FrameChunks.split(frameId, width, height, zoom, data, chunkSize)) {
            val result = send(chunk)
            if (result != SendResult.Ok) return result
        }
        return SendResult.Ok
    }

    fun close() = sender.close()

    private fun navDictionary(
        state: NavState,
        arrow: ByteArray?,
        cue: ByteArray?,
    ): PebbleDictionary {
        val dictionary = HashMap<UInt, PebbleDictionaryItem>()
        dictionary[Protocol.NAV_ACTIVE] = PebbleDictionaryItem.UInt8(if (state.active) 1 else 0)
        dictionary[Protocol.MANEUVER] = PebbleDictionaryItem.UInt8(state.maneuver.id)
        if (arrow != null) dictionary[Protocol.ARROW_BITMAP] = PebbleDictionaryItem.Bytes(arrow)
        if (cue != null) dictionary[Protocol.HAPTIC_PATTERN] = PebbleDictionaryItem.Bytes(cue)
        dictionary[Protocol.DISTANCE] = PebbleDictionaryItem.Text(state.distance)
        dictionary[Protocol.STREET] = PebbleDictionaryItem.Text(state.street)
        dictionary[Protocol.INSTRUCTION] = PebbleDictionaryItem.Text(state.instruction)
        dictionary[Protocol.ETA] = PebbleDictionaryItem.Text(state.eta)
        dictionary[Protocol.DIST_REMAIN] = PebbleDictionaryItem.Text(state.distRemain)
        dictionary[Protocol.TIME_REMAIN] = PebbleDictionaryItem.Text(state.timeRemain)
        return dictionary
    }

    private suspend fun send(dictionary: PebbleDictionary): SendResult = request { sender.sendDataToPebble(Protocol.APP_UUID, dictionary) }

    private suspend fun request(block: suspend () -> Map<WatchIdentifier, TransmissionResult>?): SendResult {
        val pending = requests.async { runCatching { block() } }
        val outcome = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { pending.await() }
        if (outcome == null) {
            pending.cancel()
            return SendResult.Timeout
        }
        val results = outcome.getOrElse { return SendResult.Failed(it.toString()) } ?: return SendResult.NoPebbleApp
        if (results.isEmpty()) return SendResult.NoWatch
        return when (val worst = results.values.firstOrNull { it !is TransmissionResult.Success }) {
            null -> SendResult.Ok
            TransmissionResult.FailedWatchNotConnected -> SendResult.NoWatch
            TransmissionResult.FailedWatchNacked -> SendResult.Nacked
            TransmissionResult.FailedTimeout -> SendResult.Timeout
            TransmissionResult.FailedDifferentAppOpen -> SendResult.AppNotOpen
            TransmissionResult.FailedNoPermissions -> SendResult.NoPermission
            else -> SendResult.Failed(worst.toString())
        }
    }

    companion object {
        const val DEFAULT_INBOX_MAX = 2044
        const val DEFAULT_MAP_WIDTH = 200
        const val DEFAULT_MAP_HEIGHT = 152
        private const val CHUNK_OVERHEAD = 96
        private const val MIN_CHUNK = 200
        private const val MAX_CHUNK = 8000
        private const val REQUEST_TIMEOUT_MS = 20_000L
    }
}
