package de.einsatzleitstelle110

import kotlin.math.*

/** Closed road circuits. Trees/dead ends produce no patrol route instead of repeated U-turns. */
object PatrolRouting {
    private data class Edge(val to: Int, val road: Int, val forward: Boolean)
    private class Network(val map: StateMap) {
        val points = mutableListOf<GeoPoint>()
        val edges = mutableListOf<MutableList<Edge>>()
        init {
            val ids = HashMap<Pair<Long, Long>, Int>()
            fun node(p: GeoPoint): Int = ids.getOrPut((p.lon * 1e5).roundToLong() to (p.lat * 1e5).roundToLong()) {
                points.add(p); edges.add(mutableListOf()); points.lastIndex
            }
            for ((ri, r) in map.roads.withIndex()) {
                if (r.size < 2) continue
                val a = node(GeoPoint(r.lon(0), r.lat(0)))
                val b = node(GeoPoint(r.lon(r.size - 1), r.lat(r.size - 1)))
                if (a == b) continue
                if (r.oneway >= 0) edges[a].add(Edge(b, ri, true))
                if (r.oneway <= 0) edges[b].add(Edge(a, ri, false))
            }
        }
    }
    private val networks = LinkedHashMap<StateMap, Network>()

    @Synchronized
    private fun network(map: StateMap): Network = networks[map] ?: Network(map).also {
        networks[map] = it
        while (networks.size > 2) networks.remove(networks.keys.first())
    }

    fun circuit(
        map: StateMap,
        start: GeoPoint,
        speedKmh: Double,
        seed: Long,
        contains: (GeoPoint) -> Boolean,
        focus: GeoPoint? = null,
    ): NativeRoute? {
        if (!contains(start)) return null
        val n = network(map)
        fun allowed(p: GeoPoint) = contains(p) && (focus == null || GeoData.km(p, focus) <= 1.8)
        val checked = IntArray(map.roads.size)
        fun roadAllowed(ri: Int): Boolean {
            if (checked[ri] != 0) return checked[ri] == 1
            val r = map.roads[ri]
            var valid = true
            for (i in 0 until r.size) {
                val a = GeoPoint(r.lon(i), r.lat(i))
                if (!allowed(a)) { valid = false; break }
                if (i > 0) {
                    val b = GeoPoint(r.lon(i - 1), r.lat(i - 1))
                    val samples = ceil(GeoData.km(a, b) / .1).toInt().coerceAtLeast(1)
                    for (k in 1 until samples) {
                        val f = k.toDouble() / samples
                        if (!allowed(GeoPoint(a.lon + (b.lon - a.lon) * f, a.lat + (b.lat - a.lat) * f))) { valid = false; break }
                    }
                }
                if (!valid) break
            }
            checked[ri] = if (valid) 1 else -1
            return valid
        }
        val anchors = n.points.indices.filter { allowed(n.points[it]) && n.edges[it].size >= 1 }
            .sortedBy { GeoData.km(n.points[it], focus ?: start) }.take(16)
        for (anchor in anchors) {
            val visited = BooleanArray(n.points.size)
            val route = mutableListOf<Edge>()
            var visits = 0
            fun find(v: Int, previous: Int): Boolean {
                if (++visits > 5000 || route.size >= 64) return false
                visited[v] = true
                val edges = n.edges[v]
                val offset = ((seed + v * 31L) and 0x7fffffff).toInt() % edges.size.coerceAtLeast(1)
                for (i in edges.indices) {
                    val e = edges[(i + offset) % edges.size]
                    if (e.to == previous || !roadAllowed(e.road)) continue
                    if (e.to == anchor && route.size >= 2) { route.add(e); return true }
                    if (visited[e.to]) continue
                    route.add(e)
                    if (find(e.to, v)) return true
                    route.removeAt(route.lastIndex)
                }
                return false
            }
            if (!find(anchor, -1)) continue
            val approach = NativeRouting.route(start, n.points[anchor], speedKmh, map.code)
            if (approach.basis == "DISTANCE_APPROXIMATION" && GeoData.km(start, n.points[anchor]) > .02) continue
            if (approach.points.any { !contains(it) } || approach.points.zipWithNext().any { (a, b) ->
                    val samples = ceil(GeoData.km(a, b) / .1).toInt().coerceAtLeast(1)
                    (1 until samples).any { k ->
                        val f = k.toDouble() / samples
                        !contains(GeoPoint(a.lon + (b.lon - a.lon) * f, a.lat + (b.lat - a.lat) * f))
                    }
                }) continue
            val points = approach.points.toMutableList()
            var km = approach.distanceKm
            var minutes = approach.durationMinutes.toDouble()
            for (e in route) {
                val r = map.roads[e.road]
                val indices = if (e.forward) 1 until r.size else r.size - 2 downTo 0
                for (i in indices) {
                    val p = GeoPoint(r.lon(i), r.lat(i))
                    val distance = GeoData.km(points.last(), p)
                    km += distance
                    minutes += distance / RoadGraph.classSpeed(r.cls) * 60 * RoadGraph.REFERENCE_SPEED / speedKmh
                    points.add(p)
                }
            }
            return NativeRoute(points, km, ceil(minutes).toInt().coerceAtLeast(2), "OSM_PATROL_CIRCUIT")
        }
        return null
    }
}
