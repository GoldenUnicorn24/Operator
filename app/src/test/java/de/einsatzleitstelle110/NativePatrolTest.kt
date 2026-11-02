package de.einsatzleitstelle110

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NativePatrolTest {
    private val a = GeoPoint(10.0, 53.5)
    private val b = GeoPoint(10.02, 53.5)
    private val c = GeoPoint(10.02, 53.51)
    private val d = GeoPoint(10.0, 53.51)
    private fun region() = GeoRegion("DE-TEST", "Testrevier", listOf(listOf(GeoPoint(9.9, 53.4), GeoPoint(10.1, 53.4), GeoPoint(10.1, 53.6), GeoPoint(9.9, 53.6), GeoPoint(9.9, 53.4))), listOf(GeoCity("Teststadt", a.lon, a.lat)))
    private fun road(from: GeoPoint, to: GeoPoint, direction: Int = 1) = RoadLine(3, "", doubleArrayOf(from.lon, from.lat, to.lon, to.lat), "Wohnstraße", direction)
    private fun map() = StateMap("DE-TEST", listOf(road(a, b), road(b, c), road(c, d), road(d, a)), emptyList(), "OSM", version = 2)
    private fun unit(id: String = "p", point: GeoPoint = a) = UnitState(id, "Peter 31/${if (id == "p") 1 else 2}", "STREIFE", "DE-TEST", point.lat, point.lon,
        officers = mutableListOf(Officer("A"), Officer("B")))
    private fun game(): GameController {
        NativeRouting.install(map())
        val s = GameState(region = "DE-TEST", phase = "RUNNING", autoCalls = false, absoluteMinute = 360)
        s.units.add(unit())
        return GameController(s, listOf(region()))
    }
    @After fun clear() { NativeRouting.clear(); MapData.clear() }

    @Test fun patrolCircuitStaysInAreaHonorsOneWayAndNeverUTurns() {
        val map = map()
        NativeRouting.install(map)
        val route = PatrolRouting.circuit(map, a, 62.0, 1L, region()::contains)!!
        val pts = route.points.filterIndexed { i, p -> i == 0 || p != route.points[i - 1] }
        assertEquals(a, pts.first()); assertEquals(a, pts.last())
        assertEquals(listOf(a, b, c, d, a), pts)
        assertTrue(route.points.all { region().contains(it) })
        assertFalse(pts.windowed(3).any { it[0] == it[2] })
        // Cutting the scope across the cycle must reject it instead of leaving the jurisdiction.
        assertNull(PatrolRouting.circuit(map, a, 62.0, 2L, { it.lon <= 10.01 }))
        val tree = StateMap("DE-TEST", listOf(road(a, b, 0), road(b, c, 0)), emptyList(), "OSM", version = 2)
        NativeRouting.install(tree)
        assertNull(PatrolRouting.circuit(tree, a, 62.0, 1L, region()::contains))
    }

    @Test fun movingPatrolCanBeDispatchedFromItsCurrentPositionWithoutReadiness() {
        val g = game(); val u = g.state.units.first()
        assertEquals(2, g.fms(u))
        assertTrue(g.setPatrolMode(u, "PATROL"))
        assertEquals("PATROLLING", u.status); assertEquals(1, g.fms(u)); assertFalse(u.specialSignal)
        assertTrue(g.patrolPresence() > 0)
        val crime = IncidentTemplate("Sachbeschädigung", 3, "STREIFE", 8, "Test")
        val preventedWeight = g.templateWeight(crime)
        g.advance(2)
        val current = GeoPoint(u.lon, u.lat)
        assertTrue(current != a); assertTrue(g.available(u))
        val call = g.newIncident(IncidentTemplate("Testlage", 3, "STREIFE", 30, "Test"), c)
        assertTrue(g.dispatch(call, u))
        assertEquals(current.lon, u.startLon, 0.0); assertEquals(current.lat, u.startLat, 0.0)
        assertEquals(0, u.readinessLeft); assertEquals(3, g.fms(u)); assertFalse(u.specialSignal)
        assertTrue(g.templateWeight(crime) > preventedWeight)
        assertTrue(g.state.logs.any { "Peter 31/1 an Leitstelle: Status 3" in it })
        while (u.status == "DRIVING") g.advance(1)
        assertEquals(4, g.fms(u))
        assertTrue(g.state.logs.any { "Status 4" in it })
    }

    @Test fun specialRightsAreFasterAndPriorityDefaultsAreVisible() {
        val g = game(); val fast = g.state.units.first(); val slow = unit("q")
        g.state.units.add(slow)
        val p1 = g.newIncident(IncidentTemplate("Dringend", 1, "STREIFE", 20, "Test"), c)
        val p3 = g.newIncident(IncidentTemplate("Routine", 3, "STREIFE", 20, "Test"), c)
        assertTrue(g.dispatch(p1, fast)); assertTrue(g.dispatch(p3, slow))
        assertTrue(fast.specialSignal); assertFalse(slow.specialSignal)
        assertTrue("${fast.travelTotal} vs ${slow.travelTotal}", fast.travelTotal < slow.travelTotal)
        assertEquals(3, g.fms(fast))
        assertTrue(g.state.logs.any { "mit Sonderrechten" in it })
        g.advance(3)
        assertTrue(fast.distanceDrivenKm > 0)
        assertTrue(g.recall(fast)); assertFalse(fast.specialSignal); assertEquals(1, g.fms(fast))
        fast.onDuty = false; assertEquals(6, g.fms(fast))
    }

    @Test fun specialRightsHaveDeterministicAccidentDelayAndExtraWear() {
        val g = game(); val u = g.state.units.first()
        u.status = "DRIVING"; u.specialSignal = true
        u.startLon = a.lon; u.startLat = a.lat; u.destLon = c.lon; u.destLat = c.lat
        u.routePoints = mutableListOf(a, b, c); u.travelTotal = 30; u.travelLeft = 30
        // The persisted generator's next value is exactly zero for this fixture seed.
        g.state.randomSeed = 634785765L
        g.advance(1)
        assertEquals(1, g.state.shiftSignalIncidents)
        assertEquals(2, u.readinessLeft)
        assertEquals(92, u.vehicleCondition)
        val pausedPosition = GeoPoint(u.lon, u.lat)
        g.advance(2)
        assertEquals(pausedPosition, GeoPoint(u.lon, u.lat))
        assertTrue(g.state.logs.any { "Fahrzwischenfall" in it })
        u.specialSignal = false
        val incidents = g.state.shiftSignalIncidents
        g.state.randomSeed = 634785765L
        g.advance(1)
        assertEquals(incidents, g.state.shiftSignalIncidents)
        assertEquals(0, u.readinessLeft)
    }

    @Test fun schemaSixRoundtripPreservesPatrolAndLegacyFiveGetsDefaults() {
        val g = game(); val u = g.state.units.first()
        assertTrue(g.setPatrolMode(u, "PATROL")); g.advance(1)
        val json = GameStore.toJson(g.state)
        assertEquals(6, json.getInt("schemaVersion"))
        val restored = GameStore.fromJson(JSONObject(json.toString()))
        val v = restored.units.first()
        assertEquals("PATROLLING", v.status); assertEquals("PATROL", v.patrolMode)
        assertEquals(u.routePoints, v.routePoints); assertEquals(u.travelLeft, v.travelLeft)
        assertEquals(u.distanceDrivenKm, v.distanceDrivenKm, .0000001)
        val legacy = JSONObject(json.toString()).put("schemaVersion", 5)
        legacy.remove("chief")
        val old = legacy.getJSONArray("units").getJSONObject(0).put("status", "FREE")
        for (key in listOf("patrolMode", "patrolScope", "specialSignal", "focusLon", "focusLat", "distanceDrivenKm", "shiftDrivenKm")) old.remove(key)
        val migrated = GameStore.fromJson(legacy)
        assertEquals("WATCH", migrated.units.first().patrolMode)
        assertFalse(migrated.units.first().specialSignal)
        assertTrue(migrated.chief.vehicles.isEmpty())
        assertEquals(6, GameStore.toJson(migrated).getInt("schemaVersion"))
    }
}
