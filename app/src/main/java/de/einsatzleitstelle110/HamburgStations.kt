package de.einsatzleitstelle110

import android.content.Context
import org.json.JSONObject

/** Official Hamburg open-data locations, bundled for fully offline play. */
data class HamburgStation(
    val id: String,
    val name: String,
    val category: String,
    val lon: Double,
    val lat: Double,
    val address: String,
) {
    val point get() = GeoPoint(lon, lat)
}

object HamburgStations {
    @Volatile private var sites: List<HamburgStation> = emptyList()

    val all: List<HamburgStation> get() = sites

    @Synchronized
    fun load(context: Context): List<HamburgStation> {
        if (sites.isNotEmpty()) return sites
        val data = context.assets.open("geo/hamburg-stations.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val items = JSONObject(data).getJSONArray("stations")
        val parsed = ArrayList<HamburgStation>(items.length())
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val lon = o.getDouble("lon")
            val lat = o.getDouble("lat")
            if (lon !in 8.3..10.6 || lat !in 53.3..54.1) continue
            parsed.add(
                HamburgStation(
                    o.getString("id"), o.getString("name"), o.getString("type"),
                    lon, lat, o.optString("address"),
                )
            )
        }
        require(parsed.any { it.category == "POLIZEI" } && parsed.any { it.category == "BERUFSFEUERWEHR" }) {
            "Hamburger Wachendaten fehlen"
        }
        sites = parsed
        return sites
    }

    fun matching(type: String): List<HamburgStation> = when (type) {
        "FEUERWEHR" -> all.filter { it.category == "BERUFSFEUERWEHR" || it.category == "FREIWILLIGE_FEUERWEHR" }
        "RETTUNG" -> all.filter { it.category == "BERUFSFEUERWEHR" }
        else -> all.filter { it.category == "POLIZEI" }
    }

    fun nearest(type: String, point: GeoPoint): HamburgStation? =
        matching(type).minByOrNull { GeoData.km(it.point, point) }
}
