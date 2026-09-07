package be.pascu.mapsforpebble.pebble

import java.util.UUID

object Protocol {
    val APP_UUID: UUID = UUID.fromString("58b9be94-3f7b-4338-94e5-90890b2ab7a0")

    val NAV_ACTIVE = 1u
    val MANEUVER = 2u
    val ARROW_BITMAP = 3u
    val DISTANCE = 4u
    val STREET = 5u
    val INSTRUCTION = 6u
    val ETA = 7u
    val DIST_REMAIN = 8u
    val TIME_REMAIN = 9u
    val HAPTIC_PATTERN = 10u
    val KEEP_LIT = 11u

    val MAP_WIDTH = 20u
    val MAP_HEIGHT = 21u
    val MAP_FRAME = 22u
    val MAP_TOTAL = 23u
    val MAP_OFFSET = 24u
    val MAP_DATA = 25u
    val MAP_ZOOM = 26u

    val HELLO = 40u
    val INBOX_MAX = 41u
    val MAP_VIEW_WIDTH = 42u
    val MAP_VIEW_HEIGHT = 43u
    val ZOOM_LEVEL = 45u

    const val ZOOM_SCALE = 100
}
