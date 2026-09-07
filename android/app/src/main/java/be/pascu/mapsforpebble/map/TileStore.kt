package be.pascu.mapsforpebble.map

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

class TileStore(
    private val cacheDir: File,
    private val userAgent: String,
    private val scope: CoroutineScope,
    private val onTileLoaded: () -> Unit,
) {
    private val memory =
        object : LruCache<Long, TileData>(MEMORY_BUDGET_BYTES) {
            override fun sizeOf(
                key: Long,
                value: TileData,
            ): Int = value.approximateBytes
        }
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()
    private val failedAt = ConcurrentHashMap<Long, Long>()

    @Volatile private var template: String? = null

    @Volatile private var templateFailedAt = 0L

    init {
        scope.launch(Dispatchers.IO) { pruneDisk() }
    }

    fun get(
        x: Int,
        y: Int,
    ): TileData? {
        val key = key(x, y)
        memory.get(key)?.let { return it }
        val file = tileFile(x, y)
        val bytes = readFresh(file, TILE_MAX_AGE_MS)
        if (bytes == null) {
            scheduleDownload(key, x, y, file)
            return null
        }
        return decode(x, y, file, bytes)
    }

    private fun decode(
        x: Int,
        y: Int,
        file: File,
        bytes: ByteArray,
    ): TileData? {
        val tile =
            try {
                TileData.fromMvt(x, y, Mvt.decode(unzipped(bytes), TileData.LAYERS))
            } catch (e: Exception) {
                Log.w(TAG, "tile $x/$y failed to decode: $e")
                file.delete()
                failedAt[key(x, y)] = System.currentTimeMillis()
                return null
            }
        memory.put(key(x, y), tile)
        return tile
    }

    private fun scheduleDownload(
        key: Long,
        x: Int,
        y: Int,
        file: File,
    ) {
        val lastFailure = failedAt[key]
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < RETRY_MS) return
        if (!inFlight.add(key)) return
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = download(x, y)
                if (bytes == null) {
                    failedAt[key] = System.currentTimeMillis()
                } else {
                    failedAt.remove(key)
                    store(file, bytes)
                    if (decode(x, y, file, bytes) != null) onTileLoaded()
                }
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private fun tileFile(
        x: Int,
        y: Int,
    ): File = File(cacheDir, "${WebMercator.TILE_ZOOM}/$x/$y.pbf")

    private fun readFresh(
        file: File,
        maxAgeMs: Long,
    ): ByteArray? {
        if (!file.isFile) return null
        if (System.currentTimeMillis() - file.lastModified() > maxAgeMs) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    private fun store(
        file: File,
        bytes: ByteArray,
    ) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeBytes(bytes)
            temp.renameTo(file)
        }
    }

    private fun pruneDisk() {
        val cutoff = System.currentTimeMillis() - TILE_MAX_AGE_MS
        cacheDir.walkBottomUp().forEach { entry ->
            if (entry.isFile && entry.lastModified() < cutoff) entry.delete()
        }
    }

    private fun tileTemplate(): String? {
        template?.let { return it }
        if (System.currentTimeMillis() - templateFailedAt < RETRY_MS) return null
        val file = File(cacheDir, "tilejson.json")
        val json = readFresh(file, TILEJSON_MAX_AGE_MS) ?: fetch(URL(TILEJSON_URL))?.also { store(file, it) }
        val parsed =
            json?.let {
                runCatching { JSONObject(String(unzipped(it), Charsets.UTF_8)).getJSONArray("tiles").getString(0) }.getOrNull()
            }
        if (parsed == null) {
            templateFailedAt = System.currentTimeMillis()
            return null
        }
        template = parsed
        return parsed
    }

    private fun download(
        x: Int,
        y: Int,
    ): ByteArray? {
        val url =
            tileTemplate()
                ?.replace("{z}", WebMercator.TILE_ZOOM.toString())
                ?.replace("{x}", x.toString())
                ?.replace("{y}", y.toString())
                ?: return null
        return fetch(URL(url))
    }

    private fun fetch(url: URL): ByteArray? =
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept-Encoding", "gzip")
            try {
                if (connection.responseCode == 204) {
                    ByteArray(0)
                } else if (connection.responseCode == 200) {
                    connection.inputStream.use { it.readBytes() }
                } else {
                    Log.w(TAG, "$url HTTP ${connection.responseCode}")
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "$url failed: $e")
            null
        }

    private fun unzipped(bytes: ByteArray): ByteArray =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } else {
            bytes
        }

    private fun key(
        x: Int,
        y: Int,
    ): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

    companion object {
        private const val TAG = "TileStore"
        const val TILEJSON_URL = "https://tiles.openfreemap.org/planet"
        private const val MEMORY_BUDGET_BYTES = 8 * 1024 * 1024
        private const val TILE_MAX_AGE_MS = 14L * 24 * 3600 * 1000
        private const val TILEJSON_MAX_AGE_MS = 24L * 3600 * 1000
        private const val RETRY_MS = 20_000L
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 12000
    }
}
