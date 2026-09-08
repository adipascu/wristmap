package org.peblum.mapsforpebble.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

object WebMercator {
    const val TILE_ZOOM = 14
    const val EXTENT = 4096
    const val EARTH_CIRCUMFERENCE = 40075016.686
    val WORLD_UNITS: Double = (1 shl TILE_ZOOM).toDouble() * EXTENT

    fun toWorldX(lon: Double): Double = (lon + 180.0) / 360.0 * WORLD_UNITS

    fun toWorldY(lat: Double): Double {
        val rad = Math.toRadians(lat)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * WORLD_UNITS
    }

    fun metersPerUnit(lat: Double): Double = EARTH_CIRCUMFERENCE * cos(Math.toRadians(lat)) / WORLD_UNITS

    fun metersPerPixel(
        lat: Double,
        zoom: Double,
    ): Double = EARTH_CIRCUMFERENCE * cos(Math.toRadians(lat)) / (256.0 * 2.0.pow(zoom))

    fun pixelsPerUnit(zoom: Double): Double = 2.0.pow(zoom - TILE_ZOOM) * 256.0 / EXTENT

    fun tileOf(world: Double): Int = Math.floorDiv(world.toLong(), EXTENT.toLong()).toInt()
}
