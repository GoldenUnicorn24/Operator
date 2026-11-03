package de.einsatzleitstelle110

import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NativePatrolScopeTest {
    @After fun clear() { NativeRouting.clear(); MapData.clear() }
    private fun game(): Pair<GameController, UnitState> {
        val regions = GeoData.parseStates(File("src/main/native_assets/states.geojson").readText())
        OperationsAreas.loadFromJson(File("src/main/native_assets/hamburg-districts.json").readText())
        val a = GeoPoint(10.10, 53.60); val b = GeoPoint(10.11, 53.60)
        val c = GeoPoint(10.11, 53.61); val d = GeoPoint(10.10, 53.61)
        fun road(p: GeoPoint, q: GeoPoint) = RoadLine(3, "", doubleArrayOf(p.lon,p.lat,q.lon,q.lat), "Teststraße", 1)
        NativeRouting.install(StateMap("DE-HH", listOf(road(a,b),road(b,c),road(c,d),road(d,a)), emptyList(), "OSM", version = 2))
        val state = GameState(region = "DE-HH", scope = "WANDSBEK", phase = "RUNNING", mode = "SANDBOX", unlimitedBudget = true, autoCalls = false, fatigueEnabled = false, absoluteMinute = 360)
        val unit = UnitState("patrol", "Peter 31/1", "STREIFE", "DE-HH", a.lat, a.lon, scope = "WANDSBEK", officers = mutableListOf(Officer("A"),Officer("B")))
        state.units.add(unit)
        val game = GameController(state,regions)
        assertTrue(game.setPatrolMode(unit,"PATROL"))
        return game to unit
    }
    @Test fun patrolDiscoveryCreatesAndAssignsOneLocalIncident() {
        val (game, unit) = game()
        game.state.autoCalls = true
        game.state.randomSeed = 634785765L
        game.advance(1)
        val found = game.state.incidents.single { "patrol-discovery" in it.flags }
        assertEquals("Eigenfeststellung",found.title)
        assertTrue(game.inScope(GeoPoint(found.lon,found.lat)))
        assertTrue(found.assignedUnits.contains(unit.id))
        assertEquals(0,unit.readinessLeft)
        assertFalse(unit.specialSignal)
        assertEquals(3,game.fms(unit))
    }
    @Test fun retainedPatrolCannotGenerateOutOfScopeCallsAfterDistrictChange() {
        val (game, unit) = game()
        game.advance(30)
        game.finishShift()
        game.nextDay()
        assertTrue(game.setScope("MITTE"))
        game.startShift()
        assertFalse(game.inScope(GeoPoint(unit.lon,unit.lat)))
        game.state.autoCalls = true
        game.state.randomSeed = 634785765L
        game.advance(1)
        assertTrue(game.state.incidents.filter { "patrol-discovery" in it.flags }.all { game.inScope(GeoPoint(it.lon,it.lat)) })
        assertTrue(OperationsAreas.contains("DE-HH","WANDSBEK",GeoPoint(unit.lon,unit.lat)))
    }
}
