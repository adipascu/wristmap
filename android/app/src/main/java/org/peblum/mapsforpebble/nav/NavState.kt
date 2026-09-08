package org.peblum.mapsforpebble.nav

enum class Maneuver(
    val id: Int,
) {
    UNKNOWN(0),
    STRAIGHT(1),
    TURN_LEFT(2),
    TURN_RIGHT(3),
    SLIGHT_LEFT(4),
    SLIGHT_RIGHT(5),
    SHARP_LEFT(6),
    SHARP_RIGHT(7),
    UTURN(8),
    MERGE(9),
    ROUNDABOUT(10),
    RAMP(11),
    DESTINATION(12),
    ;

    val turnsLeft: Boolean?
        get() =
            when (this) {
                TURN_LEFT, SLIGHT_LEFT, SHARP_LEFT, UTURN -> true
                TURN_RIGHT, SLIGHT_RIGHT, SHARP_RIGHT -> false
                else -> null
            }
}

data class NavState(
    val active: Boolean,
    val maneuver: Maneuver = Maneuver.UNKNOWN,
    val distance: String = "",
    val distanceMeters: Double? = null,
    val street: String = "",
    val instruction: String = "",
    val eta: String = "",
    val distRemain: String = "",
    val timeRemain: String = "",
    val rerouting: Boolean = false,
) {
    val hasInstruction: Boolean get() = instruction.isNotEmpty() || street.isNotEmpty()

    fun sameInstruction(other: NavState): Boolean = maneuver == other.maneuver && street == other.street && instruction == other.instruction

    companion object {
        val STOPPED = NavState(active = false)
    }
}

data class RawNotification(
    val lines: List<String>,
    val rerouting: Boolean,
)
