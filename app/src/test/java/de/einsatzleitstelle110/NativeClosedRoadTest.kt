package de.einsatzleitstelle110

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NativeClosedRoadTest {
    @After fun clear() { NativeRouting.clear(); MapData.clear() }
    @Test fun closedOneWayStreetSupportsInteriorDestinationsAndLegalWraparound() {
        val ring = RoadLine(3, "", doubleArrayOf(10.0, 53.5, 10.02, 53.5, 10.02, 53.52, 10.0, 53.52, 10.0, 53.5), "Ringstraße", 1)
        val access = RoadLine(2, "", doubleArrayOf(9.99, 53.5, 10.0, 53.5), "Zufahrt")
        NativeRouting.install(StateMap("DE-TEST", listOf(ring, access), emptyList(), "OSM", version = 2))
        val start = GeoPoint(10.01, 53.5)
        val goal = GeoPoint(10.005, 53.5)
        val route = NativeRouting.route(start, goal, 62.0, "DE-TEST")
        assertEquals("OSM_DETAIL_ROADS_APPROXIMATION", route.basis)
        assertTrue(route.points.contains(GeoPoint(10.02, 53.52)))
        assertTrue(route.points.contains(GeoPoint(10.0, 53.52)))
        assertTrue(route.distanceKm > 5)
        val unit = UnitState("test", "Peter 31/1", "STREIFE", "DE-TEST", start.lat, start.lon)
        assertEquals(route.durationMinutes, NativeRouting.eta(unit, goal))
        val forward = NativeRouting.route(goal, start, 62.0, "DE-TEST")
        assertTrue(forward.distanceKm < .5)
    }
    @Test fun bundledWandsbekOneWaySupportsAnObservableResidentialDrive() {
        val root = java.io.File("src/main/native_assets")
        val regions = GeoData.parseStates(java.io.File(root, "states.geojson").readText())
        OperationsAreas.loadFromJson(java.io.File(root, "hamburg-districts.json").readText())
        val map = MapData.parseBinary("DE-HH", java.io.File(root, "geo/roads-DE-HH.bin").readBytes())
        NativeRouting.install(map)
        val road = map.roads.filter { r -> r.size >= 3 && r.cls >= 3 && r.oneway != 0 && r.name.isNotBlank() &&
            (r.lon(0) != r.lon(r.size - 1) || r.lat(0) != r.lat(r.size - 1)) &&
            (0 until r.size).all { OperationsAreas.contains("DE-HH", "WANDSBEK", GeoPoint(r.lon(it), r.lat(it))) }
        }.sortedByDescending { r -> (0 until r.size - 1).sumOf { GeoData.km(GeoPoint(r.lon(it), r.lat(it)), GeoPoint(r.lon(it + 1), r.lat(it + 1))) } }.first { r ->
                    val from = if (r.oneway == 1) 0 else r.size - 1
                    val to = if (r.oneway == 1) r.size - 1 else 0
                    val route = NativeRouting.route(GeoPoint(r.lon(from), r.lat(from)), GeoPoint(r.lon(to), r.lat(to)), 62.0, "DE-HH")
                    route.durationMinutes >= 2 && route.points.contains(GeoPoint(r.lon(r.size / 2), r.lat(r.size / 2)))
                }
        val start = if (road.oneway == 1) 0 else road.size - 1
        val end = if (road.oneway == 1) road.size - 1 else 0
        val state = GameState(region = "DE-HH", scope = "WANDSBEK", phase = "RUNNING", autoCalls = false, absoluteMinute = 360)
        val game = GameController(state, regions)
        val unit = UnitState("test", "Peter 31/1", "STREIFE", "DE-HH", road.lat(start), road.lon(start), scope = "WANDSBEK", officers = mutableListOf(Officer("A"), Officer("B")))
        state.units.add(unit)
        val call = game.newIncident(IncidentTemplate("Wohnstraße prüfen", 1, "STREIFE", 20, "Fiktive Testlage"), GeoPoint(road.lon(end), road.lat(end)))
        assertTrue(game.dispatch(call, unit, false))
        assertTrue("${road.name}: ${unit.travelTotal} min", unit.travelTotal >= 2)
        val old = GeoPoint(unit.lon, unit.lat)
        game.advance(unit.readinessLeft + 1)
        assertTrue(old != GeoPoint(unit.lon, unit.lat))
        assertEquals("DRIVING", unit.status)
        assertEquals("OSM_DETAIL_ROADS_APPROXIMATION", unit.routeBasis)
    }
}
