package be.pascu.mapsforpebble.map

object AutoZoom {
    const val MIN = 14.0
    const val MAX = 18.0
    private const val CYCLING_MAX = 17.0
    private const val CYCLING_SPEED_MPS = 4f
    private const val DEFAULT = 17.0

    fun choose(
        lat: Double,
        mapHeight: Int,
        distanceMeters: Double?,
        speedMps: Float?,
    ): Double {
        val ceiling = if (speedMps != null && speedMps > CYCLING_SPEED_MPS) CYCLING_MAX else MAX
        if (distanceMeters == null) return minOf(DEFAULT, ceiling)
        val aheadPixels = MapRenderer.distanceToTop(mapHeight)
        var zoom = ceiling
        while (zoom > MIN && distanceMeters > aheadPixels * WebMercator.metersPerPixel(lat, zoom)) zoom -= 1.0
        return zoom
    }
}
