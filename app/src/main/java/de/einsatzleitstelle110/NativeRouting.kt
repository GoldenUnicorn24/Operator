package de.einsatzleitstelle110

import android.content.Context
import java.util.PriorityQueue
import kotlin.math.*

data class NativeRoute(
    val points: List<GeoPoint>,
    val distanceKm: Double,
    val durationMinutes: Int,
    val basis: String,
)

/**
 * Routing graph of one state's main road network. Nodes are junctions (exact coordinate match of
 * polyline end points) or, for the legacy Hamburg file, every vertex. Edge weights are travel
 * minutes for a reference vehicle (62 km/h average) using class speeds; the game scales them by
 * the actual vehicle speed. Detail maps honor one-way direction; turn restrictions and traffic
 * remain game approximations.
 */
class RoadGraph(val code: String, val map: StateMap, everyVertex: Boolean = map.source == "LGV") {
    val lon: DoubleArray
    val lat: DoubleArray
    private val first: IntArray
    private val target: IntArray
    private val origin: IntArray
    private val incomingFirst: IntArray
    private val incomingEdge: IntArray
    private val streetNames: Array<String?>
    private val minutes: FloatArray
    private val km: FloatArray
    private val road: IntArray
    private val fromVertex: IntArray
    private val toVertex: IntArray
    private val main: BooleanArray
    private val grid = HashMap<Long, IntArray>()
    val bounds: DoubleArray
    val basis = when {
        map.source == "LGV" -> "HAMBURG_MAIN_ROADS_APPROXIMATION"
        map.version >= 2 -> "OSM_DETAIL_ROADS_APPROXIMATION"
        else -> "OSM_MAIN_ROADS_APPROXIMATION"
    }
    val nodeCount: Int
        get() = lon.size

    init {
        val ids = HashMap<Long, Int>()
        val lo = ArrayList<Double>()
        val la = ArrayList<Double>()
        fun node(x: Double, y: Double): Int =
            ids.getOrPut(((x * 1e5).roundToLong() shl 32) xor ((y * 1e5).roundToLong() and 0xffffffffL)) {
                lo.add(x)
                la.add(y)
                lo.size - 1
            }
        data class E(val a: Int, val b: Int, val min: Float, val km: Float, val road: Int, val va: Int, val vb: Int)
        val list = ArrayList<E>()
        for ((ri, r) in map.roads.withIndex()) {
            if (r.size < 2) continue
            val speed = classSpeed(r.cls)
            val stops = if (everyVertex) (0 until r.size).toList() else listOf(0, r.size - 1)
            for (s in 0 until stops.size - 1) {
                val va = stops[s]
                val vb = stops[s + 1]
                var length = 0.0
                for (k in va until vb) length += GeoData.km(GeoPoint(r.lon(k), r.lat(k)), GeoPoint(r.lon(k + 1), r.lat(k + 1)))
                val a = node(r.lon(va), r.lat(va))
                val b = node(r.lon(vb), r.lat(vb))
                if (a == b) continue
                val m = (length / speed * 60).toFloat()
                if (r.oneway >= 0) list.add(E(a, b, m, length.toFloat(), ri, va, vb))
                if (r.oneway <= 0) list.add(E(b, a, m, length.toFloat(), ri, vb, va))
            }
        }
        lon = lo.toDoubleArray()
        lat = la.toDoubleArray()
        val n = lon.size
        val count = IntArray(n + 1)
        for (e in list) count[e.a + 1]++
        for (i in 0 until n) count[i + 1] += count[i]
        first = count.copyOf()
        val fill = count.copyOf()
        target = IntArray(list.size)
        origin = IntArray(list.size)
        streetNames = arrayOfNulls(n)
        minutes = FloatArray(list.size)
        km = FloatArray(list.size)
        road = IntArray(list.size)
        fromVertex = IntArray(list.size)
        toVertex = IntArray(list.size)
        for (e in list) {
            val k = fill[e.a]++
            target[k] = e.b
            origin[k] = e.a
            val line = map.roads[e.road]
            if (line.cls != 0 && line.name.isNotBlank()) {
                if (streetNames[e.a] == null) streetNames[e.a] = line.name
                if (streetNames[e.b] == null) streetNames[e.b] = line.name
            }
            minutes[k] = e.min
            km[k] = e.km
            road[k] = e.road
            fromVertex[k] = e.va
            toVertex[k] = e.vb
        }
        // Reverse CSR: a field queried at the destination must traverse incoming edges.
        incomingFirst = IntArray(n + 1)
        for (v in target) incomingFirst[v + 1]++
        for (i in 0 until n) incomingFirst[i + 1] += incomingFirst[i]
        val incomingFill = incomingFirst.copyOf()
        incomingEdge = IntArray(list.size)
        for (e in target.indices) incomingEdge[incomingFill[target[e]]++] = e

        // Largest weak component: connectivity must not depend on one-way traversal order.
        // It excludes isolated fragments without discarding valid one-way entrances or exits.
        val parent = IntArray(n) { it }
        val sizes = IntArray(n) { 1 }
        fun root(node: Int): Int {
            var v = node
            while (parent[v] != v) {
                parent[v] = parent[parent[v]]
                v = parent[v]
            }
            return v
        }
        for (e in list) {
            var a = root(e.a)
            var b = root(e.b)
            if (a == b) continue
            if (sizes[a] < sizes[b]) { val t = a; a = b; b = t }
            parent[b] = a
            sizes[a] += sizes[b]
        }
        val best = (0 until n).maxByOrNull { sizes[root(it)] }?.let { root(it) }
        main = BooleanArray(n) { root(it) == best }
        val cells = HashMap<Long, MutableList<Int>>()
        for (i in 0 until n) if (main[i]) cells.getOrPut(cell(lon[i], lat[i])) { mutableListOf() }.add(i)
        for ((k, v) in cells) grid[k] = v.toIntArray()
        bounds =
            if (n == 0) doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            else doubleArrayOf(lon.min(), lat.min(), lon.max(), lat.max())
    }

    private fun cell(x: Double, y: Double): Long =
        (floor(x / CELL).toLong() shl 32) xor (floor(y / CELL).toLong() and 0xffffffffL)

    fun covers(p: GeoPoint, margin: Double = .08) =
        nodeCount > 0 &&
            p.lon in bounds[0] - margin..bounds[2] + margin &&
            p.lat in bounds[1] - margin..bounds[3] + margin

    /** Nearest node of the main network, searched in growing grid rings. */
    fun nearest(p: GeoPoint): Int = nearestMatching(p) { true }

    /** A named road junction inside the caller's jurisdiction, never a synthetic address. */
    fun namedStreetPoint(p: GeoPoint, predicate: (GeoPoint) -> Boolean): Pair<GeoPoint, String>? {
        val node = nearestMatching(p) { streetNames[it] != null && predicate(point(it)) }
        return if (node < 0) null else point(node) to streetNames[node]!!
    }

    private fun nearestMatching(p: GeoPoint, accept: (Int) -> Boolean): Int {
        if (nodeCount == 0) return -1
        val cx = floor(p.lon / CELL).toLong()
        val cy = floor(p.lat / CELL).toLong()
        var bestNode = -1
        var bestKm = Double.MAX_VALUE
        for (ring in 0..40) {
            for (dx in -ring..ring) for (dy in -ring..ring) {
                if (max(abs(dx), abs(dy)) != ring) continue
                val ids = grid[((cx + dx) shl 32) xor ((cy + dy) and 0xffffffffL)] ?: continue
                for (i in ids) {
                    if (!accept(i)) continue
                    val d = GeoData.km(p, GeoPoint(lon[i], lat[i]))
                    if (d < bestKm) {
                        bestKm = d
                        bestNode = i
                    }
                }
            }
            // One extra ring guarantees the true nearest node when cells are not square in km.
            if (bestNode >= 0 && ring >= 1 && bestKm < (ring - .5) * CELL * 70) break
        }
        return bestNode
    }

    fun point(i: Int) = GeoPoint(lon[i], lat[i])

    /** Reference minutes from every node to [source], using the reversed directed graph. */
    fun field(source: Int): FloatArray {
        val dist = FloatArray(nodeCount) { Float.POSITIVE_INFINITY }
        if (source !in 0 until nodeCount) return dist
        val open = PriorityQueue<Long>()
        dist[source] = 0f
        open.add(source.toLong())
        while (open.isNotEmpty()) {
            val packed = open.poll()
            val v = (packed and 0xffffffffL).toInt()
            val d = Float.fromBits((packed ushr 32).toInt())
            if (d > dist[v]) continue
            for (j in incomingFirst[v] until incomingFirst[v + 1]) {
                val k = incomingEdge[j]
                val nd = d + minutes[k]
                val t = origin[k]
                if (nd < dist[t]) {
                    dist[t] = nd
                    open.add((nd.toRawBits().toLong() shl 32) or t.toLong())
                }
            }
        }
        return dist
    }

    /** A* in reference minutes. Returns node path or empty when unreachable. */
    fun path(from: Int, to: Int): Pair<IntArray, IntArray>? {
        if (from !in 0 until nodeCount || to !in 0 until nodeCount) return null
        if (from == to) return IntArray(1) { from } to IntArray(0)
        val g = FloatArray(nodeCount) { Float.POSITIVE_INFINITY }
        val via = IntArray(nodeCount) { -1 }
        val closed = BooleanArray(nodeCount)
        val goal = point(to)
        fun h(i: Int) = (GeoData.km(point(i), goal) / MAX_SPEED * 60).toFloat()
        data class S(val n: Int, val f: Float)
        val open = PriorityQueue<S>(compareBy { it.f })
        g[from] = 0f
        open.add(S(from, h(from)))
        var visits = 0
        while (open.isNotEmpty() && visits++ < 400000) {
            val cur = open.remove().n
            if (closed[cur]) continue
            if (cur == to) {
                val nodes = ArrayList<Int>()
                val edges = ArrayList<Int>()
                var n = to
                while (n != from) {
                    nodes.add(n)
                    edges.add(via[n])
                    n = origin[via[n]]
                }
                nodes.add(from)
                return nodes.reversed().toIntArray() to edges.reversed().toIntArray()
            }
            closed[cur] = true
            for (k in first[cur] until first[cur + 1]) {
                val t = target[k]
                val ng = g[cur] + minutes[k]
                if (ng < g[t]) {
                    g[t] = ng
                    via[t] = k
                    open.add(S(t, ng + h(t)))
                }
            }
        }
        return null
    }

    fun edgeMinutes(e: Int) = minutes[e]

    fun edgeKm(e: Int) = km[e]

    /** Road geometry of an edge in travel direction (excluding its first vertex). */
    fun appendGeometry(e: Int, out: MutableList<GeoPoint>) {
        val r = map.roads[road[e]]
        val a = fromVertex[e]
        val b = toVertex[e]
        if (a < b) for (k in a + 1..b) out.add(GeoPoint(r.lon(k), r.lat(k)))
        else for (k in a - 1 downTo b) out.add(GeoPoint(r.lon(k), r.lat(k)))
    }

    companion object {
        const val CELL = .02
        const val MAX_SPEED = 100.0
        const val REFERENCE_SPEED = 62.0
        const val ACCESS_SPEED = 28.0

        fun classSpeed(cls: Int) =
            when (cls) {
                0 -> 100.0
                1 -> 66.0
                2 -> 44.0
                3 -> 25.0
                4, 5 -> 12.0
                else -> 30.0
            }
    }
}

/**
 * Offline routing facade. Uses the bundled road graph of the state when available; otherwise an
 * explicitly labelled distance approximation. Helicopters fly direct lines.
 */
object NativeRouting {
    private var appContext: Context? = null
    private val graphs = HashMap<String, RoadGraph>()
    private val fields = LinkedHashMap<String, FloatArray>()

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext ?: context
    }

    /** Build (or fetch) the graph of a state. Safe to call from a background thread. */
    @Synchronized
    fun graph(code: String): RoadGraph? {
        graphs[code]?.let { return it }
        val map = MapData.loaded(code) ?: appContext?.let { MapData.state(it, code) } ?: return null
        if (map.roads.isEmpty()) return null
        val g = RoadGraph(code, map)
        graphs[code] = g
        return g
    }

    @Synchronized
    fun loadedGraph(code: String): RoadGraph? = graphs[code]

    /** Legacy entry point: install the official Hamburg main-road file. */
    @Synchronized
    fun loadRoadsJson(raw: String) {
        val map = MapData.parseLegacyHamburg(raw)
        MapData.install(map)
        graphs["DE-HH"] = RoadGraph("DE-HH", map)
        fields.clear()
    }

    @Synchronized
    fun install(map: StateMap) {
        MapData.install(map)
        graphs[map.code] = RoadGraph(map.code, map)
        fields.clear()
    }

    @Synchronized
    fun clear() {
        graphs.clear()
        fields.clear()
    }

    private fun pickGraph(start: GeoPoint, end: GeoPoint, region: String?): RoadGraph? {
        if (region != null) return graph(region)?.takeIf { it.covers(start) && it.covers(end) }
        return synchronized(this) { graphs.values.firstOrNull { it.covers(start) && it.covers(end) } }
    }

    fun approximation(start: GeoPoint, end: GeoPoint, speedKmh: Double): NativeRoute {
        val direct = GeoData.km(start, end)
        return NativeRoute(
            listOf(start, end),
            direct * 1.28,
            max(1, ceil(direct * 1.28 / speedKmh * 60).toInt()),
            "DISTANCE_APPROXIMATION",
        )
    }

    fun air(start: GeoPoint, end: GeoPoint, speedKmh: Double): NativeRoute {
        val direct = GeoData.km(start, end)
        return NativeRoute(listOf(start, end), direct, max(1, ceil(direct / speedKmh * 60).toInt()), "AIR_DIRECT")
    }

    private fun accessMinutes(km: Double) = km * 1.3 / RoadGraph.ACCESS_SPEED * 60

    fun route(start: GeoPoint, end: GeoPoint, speedKmh: Double, region: String? = null): NativeRoute {
        require(
            speedKmh.isFinite() &&
                speedKmh > 0 &&
                listOf(start.lon, start.lat, end.lon, end.lat).all { it.isFinite() }
        )
        val direct = GeoData.km(start, end)
        val g = pickGraph(start, end, region) ?: return approximation(start, end, speedKmh)
        if (direct < .15 && g.map.version < 2) return approximation(start, end, speedKmh)
        val from = g.nearest(start)
        val to = g.nearest(end)
        if (from < 0 || to < 0) return approximation(start, end, speedKmh)
        val startAccess = GeoData.km(start, g.point(from))
        val endAccess = GeoData.km(end, g.point(to))
        if (startAccess > 25 || endAccess > 25) return approximation(start, end, speedKmh)
        val found = g.path(from, to) ?: return approximation(start, end, speedKmh)
        val points = ArrayList<GeoPoint>()
        points.add(start)
        points.add(g.point(from))
        var refMinutes = accessMinutes(startAccess) + accessMinutes(endAccess)
        var distance = (startAccess + endAccess) * 1.3
        for (e in found.second) {
            g.appendGeometry(e, points)
            refMinutes += g.edgeMinutes(e)
            distance += g.edgeKm(e)
        }
        points.add(end)
        // A road detour that is absurdly longer than driving cross-country means the network is
        // missing a link; prefer the honest approximation then.
        if (g.map.version < 2 && distance > direct * 3.2 + 8) return approximation(start, end, speedKmh)
        return NativeRoute(
            points,
            distance,
            max(1, ceil(refMinutes * RoadGraph.REFERENCE_SPEED / speedKmh).toInt()),
            g.basis,
        )
    }

    /** Route for a specific unit: helicopters fly, everybody else drives. */
    fun routeFor(u: UnitState, end: GeoPoint): NativeRoute {
        val start = GeoPoint(u.lon, u.lat)
        return if (u.type == "HELI") air(start, end, u.vehicleSpeed) else route(start, end, u.vehicleSpeed, u.region)
    }

    /**
     * Fast ETA (minutes, without readiness time) of many units to one target: one Dijkstra from the
     * target instead of one A* per candidate.
     */
    fun eta(u: UnitState, target: GeoPoint): Int {
        val start = GeoPoint(u.lon, u.lat)
        if (u.type == "HELI") return air(start, target, u.vehicleSpeed).durationMinutes
        val g = graph(u.region)
        if (g == null || !g.covers(start) || !g.covers(target) || (g.map.version < 2 && GeoData.km(start, target) < .15))
            return approximation(start, target, u.vehicleSpeed).durationMinutes
        val goal = g.nearest(target)
        val key = "${g.code}:$goal"
        val field =
            synchronized(this) {
                fields.remove(key)?.also { fields[key] = it }
                    ?: g.field(goal).also {
                        fields[key] = it
                        while (fields.size > 12) fields.remove(fields.keys.first())
                    }
            }
        val from = g.nearest(start)
        val ref = field.getOrElse(from) { Float.POSITIVE_INFINITY }
        if (!ref.isFinite()) return approximation(start, target, u.vehicleSpeed).durationMinutes
        val total =
            ref + accessMinutes(GeoData.km(start, g.point(from))) + accessMinutes(GeoData.km(target, g.point(goal)))
        return max(1, ceil(total * RoadGraph.REFERENCE_SPEED / u.vehicleSpeed).toInt())
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
