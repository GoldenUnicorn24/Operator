package de.einsatzleitstelle110

import android.content.Context
import org.json.JSONObject

/** A real authority-sourced location or a clearly labelled player-created game station. */
data class DispatchStation(
    val id: String,
    val name: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val address: String = "",
    val source: String = "",
    val custom: Boolean = false,
)

/** Real Hamburg positions are bundled and require no connectivity or device location. */
object HamburgStations {
    fun load(context: Context): List<DispatchStation> {
        val root = JSONObject(context.assets.open("hamburg-stations.json").bufferedReader().use { it.readText() })
        val values = root.getJSONArray("stations")
        return (0 until values.length()).map { index ->
            val o = values.getJSONObject(index)
            DispatchStation(
                id = o.getString("id"),
                name = o.getString("name"),
                type = o.getString("type"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                address = o.optString("address"),
                source = o.optString("source"),
            )
        }
    }
}
