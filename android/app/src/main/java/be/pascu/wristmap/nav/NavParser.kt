package be.pascu.wristmap.nav

object NavParser {
    private val separator = Regex("""\s+[\p{Punct}·•‧・–—|]+\s+""")
    private val clock = Regex("""\b\d{1,2}[:.]\d{2}(\s*[AaPp][Mm])?\b""")
    private val duration = Regex(
        """\d+\s*(h|hr|hrs|hour|hours|min|mins|minute|minutes|std|u|uur)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val etaWords = Regex("""(?i)\b(arrive|arrival|eta|by|at|arrivée|aankomst|om|à)\b""")
    private val nonLetter = Regex("""[^\p{L}]""")
    private val streetMarkers = listOf(
        " onto ", " towards ", " toward ", " on ", " at ",
        " sur ", " vers ", " dans ",
        " op ", " naar ", " richting ",
        " auf ", " Richtung ",
    )

    fun parse(raw: RawNotification): NavState {
        val lines = raw.lines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (raw.rerouting) {
            val text = lines.firstOrNull { !Distance.isDistance(it) } ?: "Rerouting"
            return NavState(active = true, maneuver = Maneuver.STRAIGHT, instruction = text, rerouting = true)
        }

        var distance = ""
        var instruction = ""
        var timeRemain = ""
        var distRemain = ""
        var eta = ""

        for (line in lines) {
            val parts = line.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val hasClock = parts.any { clock.containsMatchIn(it) }
                val hasDuration = parts.any { duration.containsMatchIn(it) }
                val hasDistance = parts.any { Distance.isDistance(it) }
                if (hasClock || (hasDuration && hasDistance)) {
                    for (part in parts) {
                        when {
                            clock.containsMatchIn(part) && eta.isEmpty() -> eta = clockOf(part)
                            Distance.isDistance(part) && distRemain.isEmpty() -> distRemain = part
                            duration.containsMatchIn(part) && timeRemain.isEmpty() -> timeRemain = part
                        }
                    }
                    continue
                }
                if (Distance.isDistance(parts[0]) && !Distance.isDistance(parts[1])) {
                    if (distance.isEmpty()) distance = parts[0]
                    if (instruction.isEmpty()) instruction = parts.drop(1).joinToString(" ")
                    continue
                }
            }
            when {
                Distance.isDistance(line) -> if (distance.isEmpty()) distance = line
                isEtaOnly(line) -> if (eta.isEmpty()) eta = clockOf(line)
                isDurationOnly(line) -> if (timeRemain.isEmpty()) timeRemain = line
                else -> if (instruction.isEmpty()) instruction = line
            }
        }
        if (eta.isEmpty()) {
            lines.firstNotNullOfOrNull { clock.find(it)?.value }?.let { eta = it }
        }

        return NavState(
            active = true,
            maneuver = guessManeuver(instruction),
            distance = distance,
            distanceMeters = Distance.toMeters(distance),
            street = extractStreet(instruction),
            instruction = instruction,
            eta = eta,
            distRemain = distRemain,
            timeRemain = timeRemain,
        )
    }

    private fun clockOf(text: String): String = clock.find(text)?.value ?: text

    private fun isEtaOnly(text: String): Boolean {
        val match = clock.find(text) ?: return false
        val rest = (text.substring(0, match.range.first) + text.substring(match.range.last + 1))
            .replace(etaWords, " ")
            .replace(nonLetter, " ")
            .trim()
        return rest.isEmpty()
    }

    private fun isDurationOnly(text: String): Boolean {
        if (!duration.containsMatchIn(text)) return false
        return duration.replace(text, " ").replace(nonLetter, " ").trim().isEmpty()
    }

    fun extractStreet(instruction: String): String {
        if (instruction.isEmpty()) return ""
        for (marker in streetMarkers) {
            val index = instruction.indexOf(marker, ignoreCase = true)
            if (index >= 0) return instruction.substring(index + marker.length).trim()
        }
        return instruction
    }

    fun guessManeuver(text: String): Maneuver {
        val t = text.lowercase()
        fun any(vararg words: String) = words.any { it in t }
        return when {
            t.isBlank() -> Maneuver.UNKNOWN
            any("u-turn", "u turn", "make a u", "demi-tour", "omkeren", "keer om", "wenden") -> Maneuver.UTURN
            any("roundabout", "rotary", "traffic circle", "rond-point", "rotonde", "kreisverkehr") -> Maneuver.ROUNDABOUT
            any("destination", "arrive", "arrived", "bestemming", "ziel") -> Maneuver.DESTINATION
            any("merge", "insérez", "invoegen", "einfädeln") -> Maneuver.MERGE
            any("exit", "ramp", "sortie", "afrit", "ausfahrt") -> Maneuver.RAMP
            any("slight left", "slightly left", "keep left", "légèrement à gauche", "serrez à gauche", "flauw naar links", "links aanhouden", "leicht links") -> Maneuver.SLIGHT_LEFT
            any("slight right", "slightly right", "keep right", "légèrement à droite", "serrez à droite", "flauw naar rechts", "rechts aanhouden", "leicht rechts") -> Maneuver.SLIGHT_RIGHT
            any("sharp left", "fortement à gauche", "scherp naar links", "scharf links") -> Maneuver.SHARP_LEFT
            any("sharp right", "fortement à droite", "scherp naar rechts", "scharf rechts") -> Maneuver.SHARP_RIGHT
            any("left", "gauche", "links") -> Maneuver.TURN_LEFT
            any("right", "droite", "rechts") -> Maneuver.TURN_RIGHT
            any("head", "continue", "straight", "continuez", "tout droit", "rechtdoor", "ga ", "geradeaus", "weiter") -> Maneuver.STRAIGHT
            else -> Maneuver.UNKNOWN
        }
    }
}
