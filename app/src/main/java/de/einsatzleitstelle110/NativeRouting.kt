package de.einsatzleitstelle110

import android.content.Context
import java.util.PriorityQueue
import kotlin.math.*
import org.json.JSONArray

data class NativeRoute(
    val points: List<GeoPoint>,
    val distanceKm: Double,
    val durationMinutes: Int,
    val basis: String,
)

/**
 * Offline main-road A* when both endpoints connect to the supplied Hamburg graph. Always a game
 * approximation: bidirectional roads, no live traffic.
 */
object NativeRouting {
    private data class Edge(val to: Int, val km: Double)

    private val nodes = mutableListOf<GeoPoint>()
    private val edges = mutableListOf<MutableList<Edge>>()
    private var loaded = false

    @Synchronized
    fun initialize(context: Context) {
        if (loaded) return
        try {
            loadRoadsJson(
                context.assets.open("hamburg-roads.json").bufferedReader().use { it.readText() }
            )
        } catch (_: Exception) {
            nodes.clear()
            edges.clear()
        }
    }

    @Synchronized
    fun loadRoadsJson(raw: String) {
        nodes.clear()
        edges.clear()
        loaded = false
        val data = JSONArray(raw)
        val ids = HashMap<String, Int>()
        fun node(point: GeoPoint): Int =
            ids.getOrPut("${(point.lon*10000).roundToInt()}:${(point.lat*10000).roundToInt()}") {
                nodes.add(point)
                edges.add(mutableListOf())
                nodes.lastIndex
            }
        for (i in 0 until data.length()) {
            val points = data.getJSONObject(i).getJSONArray("points")
            var previous = -1
            for (j in 0 until points.length()) {
                val raw = points.getJSONArray(j)
                val index = node(GeoPoint(raw.getDouble(0), raw.getDouble(1)))
                if (previous >= 0 && previous != index) {
                    val km = GeoData.km(nodes[previous], nodes[index])
                    edges[previous].add(Edge(index, km))
                    edges[index].add(Edge(previous, km))
                }
                previous = index
            }
        }
        loaded = true
    }

    fun route(start: GeoPoint, end: GeoPoint, speedKmh: Double): NativeRoute {
        require(
            speedKmh.isFinite() &&
                speedKmh > 0 &&
                listOf(start.lon, start.lat, end.lon, end.lat).all { it.isFinite() }
        )
        val direct = GeoData.km(start, end)
        fun fallback() =
            NativeRoute(
                listOf(start, end),
                direct * 1.28,
                max(1, ceil(direct * 1.28 / speedKmh * 60).toInt()),
                "DISTANCE_APPROXIMATION",
            )
        if (nodes.isEmpty() || direct < .1) return fallback()
        // Do not snap a journey from another state onto the Hamburg graph.
        fun nearHamburg(p: GeoPoint) = p.lon in 9.65..10.35 && p.lat in 53.35..53.80
        if (!nearHamburg(start) || !nearHamburg(end)) return fallback()
        val from = nodes.indices.minByOrNull { GeoData.km(start, nodes[it]) } ?: return fallback()
        val to = nodes.indices.minByOrNull { GeoData.km(end, nodes[it]) } ?: return fallback()
        val startAccess = GeoData.km(start, nodes[from])
        val endAccess = GeoData.km(end, nodes[to])
        if (startAccess > 3 || endAccess > 3) return fallback()
        val scores = DoubleArray(nodes.size) { Double.POSITIVE_INFINITY }
        val previous = IntArray(nodes.size) { -1 }
        data class Search(val node: Int, val estimate: Double)
        val open = PriorityQueue<Search>(compareBy { it.estimate })
        val closed = BooleanArray(nodes.size)
        scores[from] = 0.0
        open.add(Search(from, GeoData.km(nodes[from], nodes[to])))
        var visits = 0
        while (open.isNotEmpty() && visits++ < 50000) {
            val current = open.remove().node
            if (closed[current]) continue
            if (current == to) {
                val reverse = mutableListOf<GeoPoint>()
                var n = to
                while (n != -1) {
                    reverse.add(nodes[n])
                    n = previous[n]
                }
                reverse.reverse()
                val points = listOf(start) + reverse + listOf(end)
                val distance = scores[to] + startAccess + endAccess
                return NativeRoute(
                    points,
                    distance,
                    max(1, ceil(distance / speedKmh * 60).toInt()),
                    "HAMBURG_MAIN_ROADS_APPROXIMATION",
                )
            }
            closed[current] = true
            for (edge in edges[current]) {
                val score = scores[current] + edge.km
                if (score < scores[edge.to]) {
                    scores[edge.to] = score
                    previous[edge.to] = current
                    open.add(Search(edge.to, score + GeoData.km(nodes[edge.to], nodes[to])))
                }
            }
        }
        return fallback()
    }

    /** Distance-weighted interpolation supports the irregular road-geometry vertices. */
    fun position(points: List<GeoPoint>, progress: Double): GeoPoint {
        require(points.size >= 2 && progress.isFinite())
        if (progress <= 0) return points.first()
        if (progress >= 1) return points.last()
        val lengths = points.zipWithNext { a, b -> GeoData.km(a, b) }
        var remaining = lengths.sum() * progress
        for (i in lengths.indices) {
            if (remaining <= lengths[i] || i == lengths.lastIndex) {
                val f = if (lengths[i] > 0) remaining / lengths[i] else 0.0
                return GeoPoint(
                    points[i].lon + (points[i + 1].lon - points[i].lon) * f,
                    points[i].lat + (points[i + 1].lat - points[i].lat) * f,
                )
            }
            remaining -= lengths[i]
        }
        return points.last()
    }
}
