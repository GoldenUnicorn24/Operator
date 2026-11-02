package de.einsatzleitstelle110

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NativeConditionsTest {
    private val a = GeoPoint(10.0, 53.5)
    private val b = GeoPoint(10.002, 53.5)
    private val c = GeoPoint(10.002, 53.502)
    private val d = GeoPoint(10.0, 53.502)
    private fun region() = GeoRegion("DE-TEST", "Testrevier", listOf(listOf(GeoPoint(9.9, 53.4), GeoPoint(10.2, 53.4), GeoPoint(10.2, 53.7), GeoPoint(9.9, 53.7), GeoPoint(9.9, 53.4))), listOf(GeoCity("Teststadt", a.lon, a.lat)))
    private fun road(p: GeoPoint, q: GeoPoint, name: String) = RoadLine(3, "", doubleArrayOf(p.lon, p.lat, q.lon, q.lat), name)
    private fun map() = StateMap("DE-TEST", listOf(road(a, b, "Erster Weg"), road(b, c, "Zweiter Weg"), road(c, d, "Dritter Weg"), road(d, a, "Vierter Weg")), emptyList(), "OSM", version = 2)
    private fun game(hour: Int = 12, weather: String = "CLEAR"): GameController {
        NativeRouting.install(map())
        val g = GameController(GameState(region = "DE-TEST", phase = "RUNNING", autoCalls = false, absoluteMinute = hour * 60L, weather = weather), listOf(region()))
        g.state.units.add(UnitState("p", "Peter 31/1", "STREIFE", "DE-TEST", a.lat, a.lon, officers = mutableListOf(Officer("A"), Officer("B"))))
        return g
    }
    @After fun clear() { NativeRouting.clear(); MapData.clear() }
    private fun template() = IncidentTemplate("Meldung", 3, "STREIFE", 8, "Test", deadline = 100)

    @Test fun trafficAndWeatherChangeTravelAndPreviewMatchesDeparture() {
        assertTrue(SimulationConditions.trafficFactor(8) > SimulationConditions.trafficFactor(12))
        assertTrue(SimulationConditions.trafficFactor(23) < SimulationConditions.trafficFactor(12))
        assertTrue(SimulationConditions.weatherFactor("ICE") > SimulationConditions.weatherFactor("RAIN"))
        val g = game(8, "RAIN"); val u = g.state.units.first()
        val call = g.newIncident(template(), c)
        val preview = g.eta(u, call, false)
        val base = NativeRouting.routeFor(u, c).durationMinutes
        assertEquals(g.readiness(u) + g.travelMinutes(base), preview)
        assertTrue(g.dispatch(call, u, false))
        assertEquals(preview, u.travelTotal + u.readinessLeft)
        assertEquals(g.travelMinutes(base), u.travelTotal)
        g.state.absoluteMinute = 23 * 60L
        g.state.weather = "CLEAR"
        assertTrue(g.travelMinutes(100) < 100)
        g.state.absoluteMinute = 8 * 60L; g.state.weather = "ICE"
        assertTrue(g.travelMinutes(100) > 170)
    }

    @Test fun rainAndIceIncreaseAccidentWeightWithoutIncreasingEveryScenario() {
        val g = game()
        val accident = IncidentTemplate("Verkehrsunfall", 2, "STREIFE", 20, "Test")
        val burglary = IncidentTemplate("Einbruch", 2, "STREIFE", 20, "Test")
        val dryAccident = g.templateWeight(accident); val dryBurglary = g.templateWeight(burglary)
        g.state.weather = "RAIN"
        assertEquals(dryAccident * 1.5, g.templateWeight(accident), .0001)
        assertEquals(dryBurglary, g.templateWeight(burglary), .0001)
        g.state.weather = "ICE"
        assertEquals(dryAccident * 2.2, g.templateWeight(accident), .0001)
    }

    @Test fun duplicateCallersMergeOnlyNearbyActiveMatchingSituations() {
        val g = game()
        val first = g.reportCaller(template(), a, disconnected = true)
        val second = g.reportCaller(template(), GeoPoint(a.lon + .0001, a.lat))
        assertSame(first, second); assertEquals(2, first.duplicateCallers)
        assertFalse(first.disconnected); assertEquals(1, g.state.shiftIncoming)
        val distant = g.reportCaller(template(), c)
        assertNotEquals(first.id, distant.id)
        first.status = "SOLVED"
        val fresh = g.reportCaller(template(), a)
        assertNotEquals(first.id, fresh.id)
        assertEquals(3, g.state.shiftIncoming)
        assertTrue(g.state.logs.any { "Mehrfachanruf" in it })
    }

    @Test fun callbackCorrectsActualStreetReroutesAndCanOnlySucceedOnce() {
        val g = game(); val u = g.state.units.first()
        val call = g.reportCaller(template(), c, disconnected = true, addressUncertain = true)
        assertTrue(call.addressUncertain); assertTrue(call.disconnected)
        assertTrue(GeoData.km(GeoPoint(call.lon, call.lat), c) > .005)
        assertTrue(g.dispatch(call, u, false))
        val oldDeadline = call.deadline
        assertTrue(g.callback(call))
        assertEquals(c.lon, call.lon, 0.0); assertEquals(c.lat, call.lat, 0.0)
        assertEquals(call.actualLocation, call.location)
        assertEquals(call.lon, u.destLon, 0.0); assertEquals(call.lat, u.destLat, 0.0)
        assertEquals(oldDeadline + 10, call.deadline)
        assertTrue(call.callbackDone); assertFalse(call.disconnected); assertFalse(call.addressUncertain)
        assertFalse(g.callback(call)); assertEquals(oldDeadline + 10, call.deadline)
    }

    @Test fun manyMergedCallersAndCallbackStillProduceAValidSave() {
        val g = game()
        val call = g.reportCaller(template(), c, disconnected = true, addressUncertain = true)
        repeat(45) { g.reportCaller(template(), c, disconnected = true) }
        call.deadline = 1440
        assertTrue(g.callback(call))
        assertEquals(1440, call.deadline)
        val loaded = GameStore.fromJson(GameStore.toJson(g.state))
        assertEquals(46, loaded.incidents.first().duplicateCallers)
        assertTrue(loaded.incidents.first().callbackDone)
    }

    @Test fun fixedEditorPositionRemainsExactWhenReportedByACaller() {
        val g = game()
        val point = GeoPoint(a.lon + .0005, a.lat + .0005)
        val fixed = template().copy(lat = point.lat, lon = point.lon)
        val call = g.reportCaller(fixed, point)
        assertEquals(point.lon, call.lon, 0.0)
        assertEquals(point.lat, call.lat, 0.0)
        assertEquals(point.lon, call.actualLon, 0.0)
        assertEquals(point.lat, call.actualLat, 0.0)
    }

    @Test fun schemaSevenPersistsCallerStateAndSixMigratesWithDefaults() {
        val g = game(8, "ICE")
        val call = g.reportCaller(template(), c, disconnected = true, addressUncertain = true)
        g.reportCaller(template(), c, disconnected = true)
        val root = GameStore.toJson(g.state)
        assertEquals(7, root.getInt("schemaVersion"))
        val loaded = GameStore.fromJson(JSONObject(root.toString()))
        assertEquals("ICE", loaded.weather)
        val restored = loaded.incidents.first { it.id == call.id }
        assertEquals(2, restored.duplicateCallers); assertTrue(restored.disconnected); assertTrue(restored.addressUncertain)
        assertEquals(c.lat, restored.actualLat, 0.0); assertEquals(c.lon, restored.actualLon, 0.0)
        assertTrue(GameController(loaded, listOf(region())).callback(restored))
        assertTrue(GameStore.fromJson(GameStore.toJson(loaded)).incidents.first().callbackDone)
        val old = JSONObject(root.toString()).put("schemaVersion", 6)
        old.remove("weather")
        val incident = old.getJSONArray("incidents").getJSONObject(0)
        for (key in listOf("disconnected", "duplicateCallers", "addressUncertain", "callbackDone", "actualLat", "actualLon", "actualLocation")) incident.remove(key)
        val migrated = GameStore.fromJson(old)
        assertEquals("CLEAR", migrated.weather)
        assertFalse(migrated.incidents.first().disconnected)
        assertEquals(1, migrated.incidents.first().duplicateCallers)
        assertEquals(migrated.incidents.first().lat, migrated.incidents.first().actualLat, 0.0)
    }
}
