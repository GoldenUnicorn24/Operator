package de.einsatzleitstelle110

import kotlin.math.floor

/**
 * Pure screen-space clustering. Neighbouring grid cells are joined too, so pins on a bucket edge
 * never conceal an inaccessible vehicle.
 */
object MapClustering {
    data class Marker<T>(val x: Float, val y: Float, val value: T)

    data class Cluster<T>(val x: Float, val y: Float, val members: List<Marker<T>>)

    fun <T> cluster(markers: List<Marker<T>>, radius: Float): List<Cluster<T>> {
        require(radius > 0)
        val parent = IntArray(markers.size) { it }
        fun root(index: Int): Int {
            var n = index
            while (parent[n] != n) {
                parent[n] = parent[parent[n]]
                n = parent[n]
            }
            return n
        }
        val buckets = HashMap<Pair<Int, Int>, MutableList<Int>>()
        for ((index, m) in markers.withIndex()) {
            val bx = floor(m.x / radius).toInt()
            val by = floor(m.y / radius).toInt()
            for (x in bx - 1..bx + 1) for (y in by - 1..by + 1) for (other in
                buckets[x to y].orEmpty()) {
                val p = markers[other]
                if ((p.x - m.x) * (p.x - m.x) + (p.y - m.y) * (p.y - m.y) < radius * radius)
                    parent[root(index)] = root(other)
            }
            buckets.getOrPut(bx to by) { mutableListOf() }.add(index)
        }
        return markers.indices
            .groupBy { root(it) }
            .values
            .map { ids ->
                Cluster(
                    ids.sumOf { markers[it].x.toDouble() }.toFloat() / ids.size,
                    ids.sumOf { markers[it].y.toDouble() }.toFloat() / ids.size,
                    ids.map { markers[it] },
                )
            }
    }
}
