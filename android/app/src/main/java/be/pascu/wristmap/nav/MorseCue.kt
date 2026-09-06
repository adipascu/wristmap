package be.pascu.wristmap.nav

object MorseCue {
    const val DOT_MS = 100
    const val DASH_MS = 300
    const val GAP_MS = 100
    const val LETTER_GAP_MS = 300

    private val alphabet =
        mapOf(
            'E' to ".",
            'T' to "-",
            'S' to "...",
            'L' to ".-..",
            'R' to ".-.",
            'U' to "..-",
            'O' to "---",
            'D' to "-..",
            'M' to "--",
            'X' to "-..-",
        )

    fun letters(maneuver: Maneuver): String? =
        when (maneuver) {
            Maneuver.STRAIGHT -> "S"
            Maneuver.TURN_LEFT -> "L"
            Maneuver.TURN_RIGHT -> "R"
            Maneuver.SLIGHT_LEFT -> "EL"
            Maneuver.SLIGHT_RIGHT -> "ER"
            Maneuver.SHARP_LEFT -> "TL"
            Maneuver.SHARP_RIGHT -> "TR"
            Maneuver.UTURN -> "U"
            Maneuver.MERGE -> "M"
            Maneuver.ROUNDABOUT -> "O"
            Maneuver.RAMP -> "X"
            Maneuver.DESTINATION -> "D"
            Maneuver.UNKNOWN -> null
        }

    fun durations(maneuver: Maneuver): IntArray? {
        val word = letters(maneuver) ?: return null
        val segments = ArrayList<Int>()
        for ((index, letter) in word.withIndex()) {
            if (index > 0) segments.add(LETTER_GAP_MS)
            for ((symbolIndex, symbol) in alphabet.getValue(letter).withIndex()) {
                if (symbolIndex > 0) segments.add(GAP_MS)
                segments.add(if (symbol == '.') DOT_MS else DASH_MS)
            }
        }
        return segments.toIntArray()
    }

    fun encode(durations: IntArray): ByteArray {
        val out = ByteArray(durations.size * 2)
        for ((index, duration) in durations.withIndex()) {
            out[index * 2] = (duration and 0xff).toByte()
            out[index * 2 + 1] = (duration ushr 8).toByte()
        }
        return out
    }

    fun pattern(maneuver: Maneuver): ByteArray? = durations(maneuver)?.let { encode(it) }
}
