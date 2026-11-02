package de.einsatzleitstelle110

import kotlin.math.max

/** Clamp a centered district camera. At fit zoom the district cannot be panned off screen. */
object DistrictCamera {
    fun pan(requested: Float, contentPixels: Double, viewportPixels: Double): Float {
        val limit = max(0.0, (contentPixels - viewportPixels) / 2)
        return requested.toDouble().coerceIn(-limit, limit).toFloat()
    }
}
