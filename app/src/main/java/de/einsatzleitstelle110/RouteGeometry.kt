package de.einsatzleitstelle110

/** Indexed geometry shared by simulated movement and animation. Points are a read-only route. */
internal class RouteGeometry(val points: List<GeoPoint>) {
    init {
        require(points.size >= 2)
    }

    private val cumulativeKm = DoubleArray(points.size).also { distances ->
        for (i in 1 until points.size)
            distances[i] = distances[i - 1] + GeoData.km(points[i - 1], points[i])
    }

    val lengthKm: Double get() = cumulativeKm.last()

    fun distanceAtVertex(index: Int): Double = cumulativeKm[index]

    fun distanceAt(progress: Double): Double = lengthKm * progress.coerceIn(0.0, 1.0)

    /** The earliest segment ending at or beyond the given distance. */
    fun segmentEnd(distance: Double): Int {
        var first = 1
        var last = points.lastIndex
        while (first < last) {
            val mid = (first + last) / 2
            if (cumulativeKm[mid] >= distance) last = mid
            else first = mid + 1
        }
        return first
    }

    fun positionOn(segmentEnd: Int, distance: Double): GeoPoint {
        val startDistance = cumulativeKm[segmentEnd - 1]
        val length = cumulativeKm[segmentEnd] - startDistance
        val start = points[segmentEnd - 1]
        val end = points[segmentEnd]
        if (length <= 0) return start
        val fraction = ((distance - startDistance) / length).coerceIn(0.0, 1.0)
        return GeoPoint(start.lon + (end.lon - start.lon) * fraction, start.lat + (end.lat - start.lat) * fraction)
    }

    fun position(progress: Double): GeoPoint {
        require(progress.isFinite())
        if (progress <= 0) return points.first()
        if (progress >= 1) return points.last()
        val distance = distanceAt(progress)
        return positionOn(segmentEnd(distance), distance)
    }
}
