package de.einsatzleitstelle110

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativePoliceChiefTest {
    private fun game(budget: Int = 240000): GameController {
        val regions = GeoData.parseStates(File("src/main/native_assets/states.geojson").readText())
        OperationsAreas.loadFromJson(File("src/main/native_assets/hamburg-districts.json").readText())
        return GameController(GameState(region = "DE-HH", scope = "WANDSBEK", budget = budget, autoCalls = false, fatigueEnabled = false), regions)
    }
    private fun station(g: GameController) = g.createChiefStation("Revier Nordgarten (fiktiv)", GeoPoint(10.10, 53.60))!!
    private fun car(g: GameController, station: ChiefStation, number: Int = 1, model: String = "sedan") = g.buyChiefVehicle(model, station.id, "Peter 31/$number")!!
    private fun crew(g: GameController, car: ChiefVehicle, count: Int = 2): List<ChiefEmployee> = (0 until count).map {
        g.hireChiefOfficer(g.chiefApplicants().first().id)!!.also { e -> assertTrue(g.assignChiefOfficer(e.id, car.unitId)) }
    }

    @Test fun wandsbekStationTwoCarsFourOfficersDispatchAndSave() {
        val g = game(); val s = station(g); val c1 = car(g, s); val c2 = car(g, s, 2)
        val people = crew(g, c1) + crew(g, c2)
        assertEquals(2, g.state.units.size)
        assertEquals(4, people.size)
        assertTrue(g.state.units.all { it.officers.size == 2 && g.available(it) })
        g.startShift()
        val call = g.newIncident(IncidentTemplate("Personaltest", 3, "STREIFE", 2, "Fiktive kleine Hilfestellung", deadline = 180), GeoPoint(10.1001, 53.6001))
        call.pending = ""; call.riskDelta = -.4
        val unit = g.state.units.first { it.id == c1.unitId }
        assertTrue(g.dispatch(call, unit, false))
        g.state.randomSeed = 0
        g.advance(25)
        assertEquals("SOLVED", call.status)
        val loaded = GameStore.fromJson(GameStore.toJson(g.state))
        assertEquals(1, loaded.chief.stations.size)
        assertEquals(2, loaded.chief.vehicles.size)
        assertEquals(4, loaded.chief.employees.size)
        assertEquals(4, loaded.units.sumOf { it.officers.size })
        assertEquals(call.status, loaded.incidents.first { it.id == call.id }.status)
        assertEquals(people.map { it.id }.toSet(), loaded.units.flatMap { it.officers }.map { it.personnelId }.toSet())
    }

    @Test fun unstaffedCarRemainsEmptyAcrossStartAndSave() {
        val g = game(); val v = car(g, station(g)); val u = g.state.units.first()
        assertFalse(g.available(u)); assertEquals(0, u.officers.size)
        g.startShift(); assertEquals(0, u.officers.size)
        val loaded = GameStore.fromJson(GameStore.toJson(g.state))
        assertEquals(v.unitId, loaded.chief.vehicles.single().unitId)
        assertTrue(loaded.units.first { it.id == v.unitId }.officers.isEmpty())
    }

    @Test fun stationBoundariesParkingAndCostsAreEnforced() {
        val g = game(); val before = g.state.budget
        assertNull(g.createChiefStation("Außerhalb", GeoPoint(9.9357, 53.5493)))
        assertEquals(before, g.state.budget)
        val s = station(g); car(g, s); car(g, s, 2)
        assertNull(g.buyChiefVehicle("sedan", s.id, "Peter 31/3"))
        assertTrue(g.upgradeChiefStation(s.id, "PARKING"))
        assertNotNull(g.buyChiefVehicle("sedan", s.id, "Peter 31/3"))
        assertEquals(4, s.parking)
        assertTrue(g.state.budget < before - 30000)
    }

    @Test fun duplicateCallsignCareerGateAndLiveryValidation() {
        val g = game(3000000); val s = station(g); val c = car(g, s)
        assertNull(g.buyChiefVehicle("sedan", s.id, "Peter 31/1"))
        assertNull(g.buyChiefVehicle("sedan", s.id, "Frei erfunden"))
        assertNull(g.buyChiefVehicle("helicopter", s.id, "Peter 31/3"))
        assertTrue(g.configureChiefVehicle(c.id, "Peter 32/1", "GREEN_WHITE"))
        assertEquals("GREEN_WHITE", c.livery)
        g.state.careerLevel = 4
        assertNotNull(g.buyChiefVehicle("helicopter", s.id, "Peter 31/3"))
    }

    @Test fun separateStaffReserveTransferSeatsPromotionAndDismissal() {
        val g = game(); val s = station(g); val c1 = car(g, s); val c2 = car(g, s, 2)
        val people = crew(g, c1)
        assertTrue(g.assignChiefOfficer(people[0].id, c2.unitId))
        assertEquals(1, g.state.units.first { it.id == c1.unitId }.officers.size)
        assertTrue(g.assignChiefOfficer(people[0].id, null))
        assertTrue(g.state.units.first { it.id == c2.unitId }.officers.isEmpty())
        people[0].officer.experience = 100; people[0].officer.skill = 90
        val salary = people[0].salary
        assertTrue(g.promoteChiefOfficer(people[0].id)); assertTrue(people[0].salary > salary)
        assertTrue(g.fireChiefOfficer(people[1].id)); assertEquals(1, g.state.chief.employees.size)
        assertTrue(g.state.units.first { it.id == c1.unitId }.officers.isEmpty())
    }

    @Test fun scheduleGroupsNightWindowsAndSaleKeepPersonnel() {
        val g = game(); val c = car(g, station(g)); val people = crew(g, c)
        people.forEach { assertTrue(g.setChiefSchedule(it.id, "B", 22, 8)) }
        assertTrue(g.state.units.single().officers.isEmpty())
        g.state.startMinute = 23 * 60
        assertTrue(g.setChiefDutyGroup("B")); assertEquals(2, g.state.units.single().officers.size)
        val budget = g.state.budget
        assertTrue(g.sellChiefVehicle(c.id)); assertTrue(g.state.budget > budget)
        assertEquals(2, g.state.chief.employees.size); assertTrue(people.all { it.assignedUnitId.isBlank() })
    }

    @Test fun customTypesCannotInventVehicleCapabilitiesAndMinimumCrewMatters() {
        val g = game(); val s = station(g)
        assertNull(g.defineChiefUnitType("Alles", 0xff112233.toInt(), "sedan", 1, setOf("SEK")))
        val type = g.defineChiefUnitType("Dreierstreife", 0xff4477aa.toInt(), "sedan", 3, setOf("STREIFE"))!!
        val c = g.buyChiefVehicle("sedan", s.id, "Peter 31/1", customTypeId = type.id)!!
        crew(g, c, 2); val u = g.state.units.single()
        assertEquals(3, g.chiefMinCrew(u)); assertFalse(g.available(u))
        crew(g, c, 1); assertTrue(g.available(u))
        assertEquals(setOf("STREIFE"), g.chiefCapabilities(u))
    }

    @Test fun dailyCostsIncludeReserveFuelBuildingsAndWorkshopAndBillOnce() {
        val g = game(); val s = station(g); val c = car(g, s); crew(g, c)
        g.hireChiefOfficer(g.chiefApplicants().first().id)
        val u = g.state.units.single(); g.recordChiefTravel(u, 100.0)
        val expectedWages = g.state.chief.employees.sumOf { it.salary }
        val before = g.state.budget; val costs = g.closeChiefDay()
        assertEquals(expectedWages, costs.wages)
        assertEquals(15, costs.fuel) // 8 litres * 1.85 €, rounded up
        assertEquals(s.dailyCost, costs.buildings)
        assertEquals(before - costs.total, g.state.budget)
        assertEquals(costs, g.closeChiefDay()); assertEquals(before - costs.total, g.state.budget)
        g.state.day++; s.workshop = 1
        val reduced = g.closeChiefDay()
        assertTrue(reduced.maintenance < costs.maintenance)
        assertEquals(0, reduced.fuel)
        g.state.day++; g.state.budget = 0
        g.closeChiefDay()
        assertTrue(g.state.budget < 0)
        assertTrue(g.state.chief.insolvencyWarning.contains("Insolvenz"))
    }

    @Test fun codecDefaultsDetachedSnapshotAndMalformedRows() {
        assertTrue(PoliceChiefCodec.read(null).stations.isEmpty())
        val g = game(); val s = station(g); val c = car(g, s); val e = crew(g, c).first()
        val copy = PoliceChiefCodec.read(PoliceChiefCodec.write(g.state.chief))
        assertEquals(c.modelId, copy.vehicles.single().modelId)
        assertEquals(e.officer.personnelId, copy.employees.first().officer.personnelId)
        val snapshot = g.state.chief.deepCopy()
        e.officer.equipment.add("Bodycam"); s.parking += 2
        assertFalse("Bodycam" in snapshot.employees.first().officer.equipment)
        assertEquals(2, snapshot.stations.first().parking)
        val malformed = JSONObject("""{"stations":[{"id":"x","name":"X","lon":200,"lat":53}],"vehicles":[{"id":"v","modelId":"made-up"}]}""")
        assertTrue(PoliceChiefCodec.read(malformed).stations.isEmpty())
        assertTrue(PoliceChiefCodec.read(malformed).vehicles.isEmpty())
    }
}
