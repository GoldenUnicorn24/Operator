package de.einsatzleitstelle110

import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NativeChiefScheduleTest {
    @After fun clear() { NativeRouting.clear(); MapData.clear() }
    private fun game(start: Int, dutyStart: Int): Pair<GameController, UnitState> {
        val regions = GeoData.parseStates(File("src/main/native_assets/states.geojson").readText())
        OperationsAreas.loadFromJson(File("src/main/native_assets/hamburg-districts.json").readText())
        val g = GameController(GameState(region = "DE-HH", scope = "WANDSBEK", autoCalls = false,
            fatigueEnabled = false, unlimitedBudget = true), regions)
        val station = g.createChiefStation("Zeitfenster", GeoPoint(10.10, 53.60))!!
        val car = g.buyChiefVehicle("sedan", station.id, "Peter 31/1")!!
        repeat(2) {
            val e = g.hireChiefOfficer(g.chiefApplicants().first().id)!!
            assertTrue(g.assignChiefOfficer(e.id, car.unitId))
            assertTrue(g.setChiefSchedule(e.id, "A", dutyStart, 8))
        }
        g.setShift(start, 24)
        g.startShift()
        return g to g.state.units.first { it.id == car.unitId }
    }

    @Test fun nightStaffStartsAndFinishesInsideTwentyFourHourShift() {
        val (g, u) = game(20, 22)
        assertTrue(u.officers.isEmpty())
        assertFalse(g.available(u))
        g.advance(120) // 22:00, within the running 24-hour shift
        assertEquals(22, g.hour())
        assertEquals(2, u.officers.size)
        assertTrue(g.available(u))
        g.advance(480) // 06:00 next day, end of the eight-hour duty window
        assertEquals(6, g.hour())
        assertTrue(u.officers.isEmpty())
        assertFalse(g.available(u))
        assertEquals(2, g.state.chief.employees.size)
        val loaded = GameStore.fromJson(GameStore.toJson(g.state))
        assertEquals(2, loaded.chief.employees.size)
        assertTrue(loaded.units.first { it.id == u.id }.officers.isEmpty())
    }

    @Test fun dutyWindowDoesNotRemoveBusyCrewAndEndedCrewCannotBeRedispatched() {
        val (g, u) = game(6, 6)
        val ids = u.officers.map { it.personnelId }
        val call = g.newIncident(IncidentTemplate("Langer Verwaltungsauftrag", 3, "STREIFE", 600,
            "Abstrakter langer Auftrag", deadline = 1200), GeoPoint(10.1001, 53.6001))
        call.pending = ""; call.riskDelta = -.4
        assertTrue(g.dispatch(call, u, false))
        g.advance(480)
        assertEquals(14, g.hour())
        assertEquals("WORKING", u.status)
        assertEquals(ids, u.officers.map { it.personnelId })
        // The completed crew returns safely; no expired schedule permits another assignment.
        g.advance(180)
        assertEquals("FREE", u.status)
        assertTrue(u.officers.isEmpty())
        assertFalse(g.dispatchable(u))
        assertEquals(ids.toSet(), g.state.chief.employees.map { it.id }.toSet())
    }
}
