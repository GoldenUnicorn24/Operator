package de.einsatzleitstelle110

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeStationsAndTaskforceTest {
    private fun hamburg() = GeoRegion(
        "DE-HH", "Hamburg",
        listOf(listOf(
            GeoPoint(8.0, 53.0), GeoPoint(11.0, 53.0),
            GeoPoint(11.0, 54.5), GeoPoint(8.0, 54.5),
        )),
        listOf(GeoCity("Hamburg", 10.0, 53.55)),
    )

    private fun official(): List<DispatchStation> {
        val root = JSONObject(File("src/main/native_assets/hamburg-stations.json").readText())
        val a = root.getJSONArray("stations")
        return (0 until a.length()).map {
            val j = a.getJSONObject(it)
            DispatchStation(j.getString("id"), j.getString("name"), j.getString("type"),
                j.getDouble("lat"), j.getDouble("lon"), j.optString("address"), j.optString("source"))
        }
    }

    @Test fun offlineHamburgFacilitiesAreGenuinelyLocatedAndAreNotRandomCityPins() {
        val locations = official()
        assertEquals(140, locations.size)
        assertEquals(36, locations.count { it.type == "POLIZEI" })
        assertEquals(18, locations.count { it.type == "BERUFSFEUERWEHR" })
        assertEquals(86, locations.count { it.type == "FREIWILLIGE_FEUERWEHR" })
        assertTrue(locations.any { it.name.contains("Neuwerk") })
        assertFalse(locations.any { it.name.contains("Cuxhaven") })
        assertTrue(locations.all { it.source.isNotBlank() && it.name.isNotBlank() })
        val g = GameController(GameState(), listOf(hamburg()), locations)
        g.seedUnitsIfNeeded()
        assertEquals(32, g.state.units.size)
        assertTrue(g.state.units.all { u ->
            locations.any { st -> st.id == u.stationId && st.lat == u.homeLat && st.lon == u.homeLon }
        })
    }

    @Test fun editableTaskforceAndPlayerStationAreSavedAndCanBeRemovedSafely() {
        val g = GameController(GameState(), listOf(hamburg()), official())
        val station = g.stations().first { it.type == "POLIZEI" }
        assertTrue(g.addStation("Eigene Zentrale", "POLIZEI", 53.55, 10.0))
        val home = g.state.customStations.single()
        val caps = GameController.TYPES.filter { it != "TASKFORCE" }.toSet()
        assertTrue(g.addCustomUnit("TASKFORCE 01", "TASKFORCE", home, "Sonderfahrzeug",
            6, 95.0, caps))
        val u = g.state.units.single()
        assertEquals(home.id, u.stationId)
        assertTrue(caps.all { g.canHandle(u, it) })
        assertFalse(g.removeStation(home.id)) // An assigned vehicle prevents deleting a base.
        val raw = GameStore.toJson(g.state)
        val saved = GameStore.fromJson(JSONObject(raw.toString()))
        assertEquals(home.id, saved.units.single().stationId)
        assertEquals(caps, saved.units.single().capabilities)
        assertEquals(home, saved.customStations.single())
        val snapshot = GameSnapshots.capture(g.state)
        g.state.units.single().capabilities.clear()
        assertEquals(caps, snapshot.units.single().capabilities)
        assertTrue(g.removeCustomUnit(u.id))
        assertTrue(g.removeStation(home.id))
        assertFalse(g.removeStation(station.id)) // Official station cannot be removed.
    }
}
