package be.pascu.mapsforpebble.nav

class CyclingDetector(
    private val thresholdMps: Float = CYCLING_SPEED_MPS,
    private val sustainMs: Long = SUSTAIN_MS,
    private val maxGapMs: Long = MAX_GAP_MS,
) {
    @Volatile
    var cycling = false
        private set

    private var aboveSince: Long? = null
    private var lastMs: Long? = null

    @Synchronized
    fun update(
        speedMps: Float,
        nowMs: Long,
    ): Boolean {
        val stale = lastMs?.let { nowMs - it > maxGapMs } ?: false
        lastMs = nowMs
        if (cycling) return true
        if (speedMps < thresholdMps) {
            aboveSince = null
            return false
        }
        if (stale) aboveSince = null
        val since = aboveSince ?: nowMs.also { aboveSince = it }
        if (nowMs - since >= sustainMs) cycling = true
        return cycling
    }

    @Synchronized
    fun assume() {
        cycling = true
    }

    @Synchronized
    fun reset() {
        cycling = false
        aboveSince = null
        lastMs = null
    }

    companion object {
        const val CYCLING_SPEED_MPS = 3.5f
        const val SUSTAIN_MS = 10_000L
        const val MAX_GAP_MS = 3_000L
    }
}
