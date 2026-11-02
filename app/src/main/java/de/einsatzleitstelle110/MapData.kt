package de.einsatzleitstelle110

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One road polyline. [points] holds lon/lat pairs interleaved. Class 0 = Autobahn, 1 = Bundes-/Hauptstraße, 2 = Neben-/Stadtstraße, 9 = Zubringer. */
class RoadLine(val cls: Int, val ref: String, val points: DoubleArray) {
    val size: Int
        get() = points.size / 2

    fun lon(i: Int) = points[2 * i]

    fun lat(i: Int) = points[2 * i + 1]
}

/** Real town or district with population (OSM/Natural Earth). Used for labels and call density. */
data class MapPlace(
    val name: String,
    val kind: String,
    val population: Int,
    val lon: Double,
    val lat: Double,
)

class StateMap(val code: String, val roads: List<RoadLine>, val places: List<MapPlace>, val source: String)

/** Germany-wide topographic context: water, settlement areas, Autobahn overview. */
class BaseLayers(
    val roads: List<RoadLine>,
    val rivers: List<Pair<Float, DoubleArray>>,
    val lakes: List<List<DoubleArray>>,
    val urban: List<List<DoubleArray>>,
)

/**
 * Offline geography loader. All files ship inside the APK (see tools/build_geo_base.py and
 * tools/build_osm_roads.py); nothing is downloaded at runtime.
 */
object MapData {
    @Volatile private var base: BaseLayers? = null
    private val states = HashMap<String, StateMap>()

    @Synchronized
    fun base(context: Context?): BaseLayers {
        base?.let { return it }
        val parsed =
            try {
                parseBase(
                    context!!.assets.open("geo/germany-base.json").bufferedReader().use { it.readText() }
                )
            } catch (_: Exception) {
                BaseLayers(emptyList(), emptyList(), emptyList(), emptyList())
            }
        base = parsed
        return parsed
    }

    /** Main roads and towns of one state; null while no data is bundled for it. */
    @Synchronized
    fun state(context: Context?, code: String): StateMap? {
        states[code]?.let { return it }
        if (context == null) return null
        val parsed =
            try {
                parseState(
                    code,
                    context.assets.open("geo/roads-$code.json").bufferedReader().use { it.readText() },
                )
            } catch (_: Exception) {
                if (code == "DE-HH")
                    try {
                        parseLegacyHamburg(
                            context.assets.open("hamburg-roads.json").bufferedReader().use {
                                it.readText()
                            }
                        )
                    } catch (_: Exception) {
                        null
                    }
                else null
            } ?: return null
        states[code] = parsed
        return parsed
    }

    @Synchronized
    fun install(map: StateMap) {
        states[map.code] = map
    }

    @Synchronized
    fun loaded(code: String): StateMap? = states[code]

    @Synchronized
    fun clear() {
        states.clear()
        base = null
    }

    private fun flat(a: JSONArray): DoubleArray = DoubleArray(a.length()) { a.getDouble(it) }

    fun parseState(code: String, raw: String): StateMap {
        val root = JSONObject(raw)
        val r = root.getJSONArray("roads")
        val roads = ArrayList<RoadLine>(r.length())
        for (i in 0 until r.length()) {
            val row = r.getJSONArray(i)
            val pts = flat(row.getJSONArray(2))
            if (pts.size >= 4) roads.add(RoadLine(row.getInt(0), row.optString(1), pts))
        }
        val p = root.optJSONArray("places") ?: JSONArray()
        val places =
            (0 until p.length()).map {
                val row = p.getJSONArray(it)
                MapPlace(row.getString(0), row.getString(1), row.getInt(2), row.getDouble(3), row.getDouble(4))
            }
        return StateMap(code, roads, places, "OSM")
    }

    /** The official LGV Hamburg main-road file used before the OSM extract. */
    fun parseLegacyHamburg(raw: String): StateMap {
        val a = JSONArray(raw)
        val roads =
            (0 until a.length()).map { n ->
                val row = a.getJSONObject(n)
                val pts = row.getJSONArray("points")
                val flat = DoubleArray(pts.length() * 2)
                for (k in 0 until pts.length()) {
                    val v = pts.getJSONArray(k)
                    flat[2 * k] = v.getDouble(0)
                    flat[2 * k + 1] = v.getDouble(1)
                }
                RoadLine(
                    when (row.optString("class")) {
                        "A" -> 0
                        "B" -> 1
                        else -> 2
                    },
                    row.optString("name"),
                    flat,
                )
            }
        return StateMap("DE-HH", roads, emptyList(), "LGV")
    }

    fun parseBase(raw: String): BaseLayers {
        val root = JSONObject(raw)
        val roads = root.getJSONArray("roads")
        val rl =
            (0 until roads.length()).mapNotNull {
                val row = roads.getJSONArray(it)
                if (row.getInt(0) == 9) null
                else RoadLine(row.getInt(0), row.optString(1), flat(row.getJSONArray(2)))
            }
        val rivers = root.getJSONArray("rivers")
        val rv =
            (0 until rivers.length()).map {
                val row = rivers.getJSONArray(it)
                row.getDouble(0).toFloat() to flat(row.getJSONArray(1))
            }
        fun polys(name: String): List<List<DoubleArray>> {
            val a = root.getJSONArray(name)
            return (0 until a.length()).map { i ->
                val rings = a.getJSONArray(i)
                (0 until rings.length()).map { flat(rings.getJSONArray(it)) }
            }
        }
        return BaseLayers(rl, rv, polys("lakes"), polys("urban"))
    }

    /**
     * Places of a state for call generation and labels: bundled towns when present, otherwise the
     * hand-maintained city list in [GeoData].
     */
    fun places(context: Context?, region: GeoRegion): List<MapPlace> {
        val bundled = state(context, region.code)?.places.orEmpty().filter { region.contains(GeoPoint(it.lon, it.lat)) }
        if (bundled.size >= 3) return bundled
        return region.cities.map { MapPlace(it.name, "city", 80000, it.lon, it.lat) }
    }
}
