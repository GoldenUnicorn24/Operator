package de.einsatzleitstelle110

import android.content.Context
import java.util.Random
import org.json.JSONArray

/**
 * Official Hamburg district geometries, offline. Scope controls jurisdiction, never teleports
 * vehicles.
 */
object OperationsAreas {
    private data class Area(val id: String, val name: String, val rings: List<List<GeoPoint>>) {
        val geometry = GeoRegion(id, name, rings, emptyList())
    }

    private var areas: List<Area> = emptyList()

    fun load(context: Context) {
        if (areas.isNotEmpty()) return
        loadFromJson(
            context.assets.open("hamburg-districts.json").bufferedReader().use { it.readText() }
        )
    }

    fun loadFromJson(raw: String) {
        val all = JSONArray(raw)
        areas =
            (0 until all.length()).map { i ->
                val f = all.getJSONObject(i)
                val rs = f.getJSONArray("rings")
                Area(
                    f.getString("id"),
                    f.getString("name"),
                    (0 until rs.length()).map { j ->
                        val ring = rs.getJSONArray(j)
                        (0 until ring.length()).map { k ->
                            val p = ring.getJSONArray(k)
                            GeoPoint(p.getDouble(0), p.getDouble(1))
                        }
                    },
                )
            }
    }

    fun options(region: String): List<Pair<String, String>> =
        listOf("STATE" to if (region == "DE-HH") "Ganz Hamburg" else "Ganzes Bundesland") +
            if (region == "DE-HH") areas.map { it.id to it.name } else emptyList()

    fun bounds(region: String, scope: String): DoubleArray? =
        if (region == "DE-HH" && scope != "STATE")
            areas.firstOrNull { it.id == scope }?.geometry?.bounds
        else null

    fun outline(region: String, scope: String): List<List<GeoPoint>> =
        if (region == "DE-HH") areas.firstOrNull { it.id == scope }?.rings ?: emptyList()
        else emptyList()

    fun contains(region: String, scope: String, p: GeoPoint): Boolean {
        if (!p.lat.isFinite() || !p.lon.isFinite()) return false
        if (scope == "STATE")
            return GeoData.all.firstOrNull { it.code == region }?.contains(p) ?: true
        return region == "DE-HH" &&
            (areas.firstOrNull { it.id == scope }?.geometry?.contains(p) ?: false)
    }

    fun sample(region: GeoRegion, scope: String, rng: Random): GeoPoint {
        val b = bounds(region.code, scope) ?: region.bounds
        repeat(20000) {
            val p =
                GeoPoint(
                    b[0] + rng.nextDouble() * (b[2] - b[0]),
                    b[1] + rng.nextDouble() * (b[3] - b[1]),
                )
            if (region.contains(p) && contains(region.code, scope, p)) return p
        }
        return region.cities
            .map { GeoPoint(it.lon, it.lat) }
            .firstOrNull { contains(region.code, scope, it) }
            ?: throw IllegalArgumentException("Kein Standort im gewählten Zuständigkeitsgebiet")
    }
}
