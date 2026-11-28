package de.einsatzleitstelle110

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeGameEngineTest {
    private fun region() =
        GeoRegion(
            "DE-HH",
            "Hamburg",
            listOf(
                listOf(
                    GeoPoint(9.0, 53.0),
                    GeoPoint(11.0, 53.0),
                    GeoPoint(11.0, 54.0),
                    GeoPoint(9.0, 54.0),
                    GeoPoint(9.0, 53.0),
                )
            ),
            listOf(
                GeoCity("Mitte", 10.0, 53.5),
                GeoCity("Nord", 10.03, 53.63),
                GeoCity("Süd", 9.95, 53.42),
            ),
        )

    private fun controller(mode: String = "SANDBOX"): GameController {
        val s = GameState(mode = mode)
        val g = GameController(s, listOf(region()))
        g.seedUnitsIfNeeded()
        s.autoCalls = false
        return g
    }

    @Test
    fun bothModesAllDurationsAndMidnightOnce() {
        for (mode in listOf("CAREER", "SANDBOX")) for (hours in listOf(8, 12, 24)) {
            val g = controller(mode)
            g.setShift(22, hours)
            g.startShift()
            g.advance(hours * 60 - 1)
            assertEquals("RUNNING", g.state.phase)
            assertEquals(1, g.state.day)
            g.advance(1)
            assertEquals("DEBRIEF", g.state.phase)
            assertEquals(hours * 60, g.state.elapsed)
            assertEquals(1, g.state.completedShifts)
            assertEquals(1, g.state.reports.size)
            g.finishShift()
            g.advance(120)
            assertEquals(1, g.state.reports.size)
            g.nextDay()
            g.nextDay()
            assertEquals(2, g.state.day)
        }
    }

    @Test
    fun everyHourlyStartAndPause() {
        for (hour in 0..23) {
            val g = controller()
            g.setShift(hour, 24)
            g.startShift()
            assertEquals(hour * 60L, g.state.shiftStartAbsolute)
            g.state.paused = true
            val before = GameStore.toJson(g.state).toString()
            g.advance(120)
            assertEquals(before, GameStore.toJson(g.state).toString())
        }
    }

    @Test
    fun freeP99ForEveryOfficerAndMigrationNeverCharges() {
        val g = controller("CAREER")
        val before = g.state.budget
        for (u in g.state.units.filter { it.type !in listOf("RETTUNG", "FEUERWEHR") }) {
            assertEquals(u.crew, u.officers.size)
            for ((i, o) in u.officers.withIndex()) {
                assertTrue("P99" in o.equipment)
                assertTrue("P99" in o.owned)
                assertFalse(g.unequipOfficer(u, i, "P99"))
                assertFalse(g.equipOfficer(u, i, "P99"))
            }
        }
        assertEquals(before, g.state.budget)
        val legacy = GameStore.toJson(g.state).put("schemaVersion", 4)
        legacy.getJSONArray("units").getJSONObject(0).remove("officers")
        val restored = GameStore.fromJson(legacy)
        GameController(restored, listOf(region())).seedUnitsIfNeeded()
        assertTrue(restored.units[0].officers.all { "P99" in it.equipment })
        assertEquals(before, restored.budget)
    }

    @Test
    fun specialForcesCanHandlePatrolButPatrolCannotHandleSpecialist() {
        val g = controller()
        val police = g.state.units.first { it.type == "STREIFE" }
        val sek = g.state.units.first { it.type == "SEK" }
        val mek = g.state.units.first { it.type == "MEK" }
        assertTrue(g.canHandle(sek, "STREIFE"))
        assertTrue(g.canHandle(mek, "STREIFE"))
        assertFalse(g.canHandle(police, "SEK"))
        assertFalse(g.canHandle(police, "MEK"))
        g.startShift()
        val c = g.state.incidents.first()
        assertTrue(g.dispatch(c, sek))
        assertFalse(g.dispatch(c, sek))
    }

    @Test
    fun arrivalNotAssignmentStartsWorkAndReturnTripIsPhysical() {
        val g = controller()
        g.startShift()
        val c = g.state.incidents.first()
        val u = g.state.units.first { it.type == "STREIFE" }
        assertTrue(g.dispatch(c, u))
        assertEquals("DRIVING", c.status)
        val eta = u.travelLeft + u.readinessLeft
        g.advance(eta - 1)
        assertEquals("DRIVING", c.status)
        g.advance(1)
        assertEquals("WORKING", c.status)
        assertEquals(c.lat, u.lat, .000001)
        g.advance(c.duration)
        assertTrue(c.status in listOf("SOLVED", "FAILED"))
        assertEquals("RETURNING", u.status)
        g.advance(u.travelLeft)
        assertEquals("FREE", u.status)
        assertEquals(u.homeLat, u.lat, .000001)
    }

    @Test
    fun multipleRequirementsCountFlexibleUnitOnlyOnce() {
        val g = controller()
        g.startShift()
        val c =
            g.newIncident(
                IncidentTemplate(
                    "Mehrere Kräfte",
                    1,
                    "STREIFE",
                    20,
                    "Test",
                    requiredTypes = listOf("STREIFE", "SEK"),
                )
            )
        val sek = g.state.units.first { it.type == "SEK" }
        val p = g.state.units.first { it.type == "STREIFE" }
        assertTrue(g.dispatch(c, sek))
        assertEquals(listOf("STREIFE"), g.remainingTypes(c))
        assertTrue(g.dispatch(c, p))
        assertTrue(g.remainingTypes(c).isEmpty())
    }

    @Test
    fun crewsStartIndependentWorkBeforeTheOtherServiceArrives() {
        val game = controller()
        game.startShift()
        val patrol = game.state.units.first { it.type == "STREIFE" }
        val rescue = game.state.units.first { it.type == "RETTUNG" }
        val call = game.newIncident(
            IncidentTemplate("Verkehrsunfall mit Betreuung", 2, "STREIFE", 12, "Test",
                requiredTypes = listOf("STREIFE", "RETTUNG")),
            GeoPoint(patrol.lon, patrol.lat),
        )
        assertTrue(game.dispatch(call, patrol, false))
        game.advance(patrol.travelLeft + patrol.readinessLeft)
        assertEquals("WORKING", call.status)
        assertEquals(11, patrol.workLeft)
        game.advance(3)
        assertEquals(8, patrol.workLeft)
        assertTrue(game.dispatch(call, rescue, false))
        rescue.readinessLeft = 0
        rescue.travelLeft = 1
        game.advance(1)
        assertEquals(7, patrol.workLeft)
        assertEquals(11, rescue.workLeft)
        game.advance(7)
        assertEquals(0, patrol.workLeft)
        assertEquals(4, rescue.workLeft)
        assertEquals("WORKING", call.status)
        game.advance(4)
        assertEquals("SOLVED", call.status)
        val messages = game.state.radioArchive.filter { it.title == "Teilaufgabe erledigt" && it.incidentId == call.id }
        assertEquals(listOf(patrol.callsign, rescue.callsign), messages.map { it.callsign })
    }

    @Test
    fun completedLocalWorkCountsAsPartialWhenOtherServiceNeverArrives() {
        val game = controller()
        game.startShift()
        val patrol = game.state.units.first { it.type == "STREIFE" }
        val call = game.newIncident(
            IncidentTemplate("Verkehrsunfall mit Nachforderung", 2, "STREIFE", 3, "Test",
                requiredTypes = listOf("STREIFE", "RETTUNG"), deadline = 16),
            GeoPoint(patrol.lon, patrol.lat),
        )
        assertTrue(game.dispatch(call, patrol, false))
        game.advance(35)
        assertEquals("PARTIAL", call.status)
        assertTrue(call.outcome.contains("1 von 2"))
        assertEquals(0, game.state.shiftSolved)
        assertEquals(0, game.state.shiftFailed)
        game.finishShift()
        assertEquals(1, game.state.reports.single().partiallySolved)
        val restored = GameStore.fromJson(GameStore.toJson(game.state))
        assertEquals(1, restored.reports.single().partiallySolved)
        assertEquals(call.tasks, restored.incidents.first { it.id == call.id }.tasks)
    }

    @Test
    fun runningTaskSurvivesSavingRecallAndAReplacementCrew() {
        val original = controller()
        original.startShift()
        val patrol = original.state.units.first { it.type == "STREIFE" }
        val call = original.newIncident(
            IncidentTemplate("Unfall mit Ablösung", 2, "STREIFE", 14, "Test",
                requiredTypes = listOf("STREIFE", "RETTUNG")),
            GeoPoint(patrol.lon, patrol.lat),
        )
        assertTrue(original.dispatch(call, patrol, false))
        original.advance(patrol.travelLeft + patrol.readinessLeft + 4)
        val saved = GameSnapshots.capture(original.state)
        val restarted = GameController(GameStore.fromJson(GameStore.toJson(saved)), listOf(region()))
        val restored = restarted.state.incidents.first { it.id == call.id }
        val oldCrew = restarted.state.units.first { it.id == patrol.id }
        assertEquals(9, restored.tasks.first().minutesLeft)
        assertTrue(restarted.recall(oldCrew))
        assertEquals("", restored.tasks.first().unitId)
        assertEquals(9, restored.tasks.first().minutesLeft)
        val replacement = restarted.state.units.first { it.id != patrol.id && it.type == "STREIFE" }
        assertTrue(restarted.dispatch(restored, replacement, false))
        replacement.readinessLeft = 0
        replacement.travelLeft = 1
        restarted.advance(1)
        assertEquals(8, restored.tasks.first().minutesLeft)
        assertEquals(replacement.id, restored.tasks.first().unitId)
        assertEquals(9, saved.incidents.first { it.id == call.id }.tasks.first().minutesLeft)
    }

    @Test
    fun aLegacyWorkingMultiRoleIncidentResumesItsRemainingMinutes() {
        val game = controller()
        game.startShift()
        val patrol = game.state.units.first { it.type == "STREIFE" }
        val rescue = game.state.units.first { it.type == "RETTUNG" }
        val call = game.newIncident(
            IncidentTemplate("Alter laufender Einsatz", 2, "STREIFE", 15, "Test",
                requiredTypes = listOf("STREIFE", "RETTUNG")),
            GeoPoint(patrol.lon, patrol.lat),
        )
        for (crew in listOf(patrol, rescue)) {
            assertTrue(game.dispatch(call, crew, false))
            crew.status = "WORKING"
            crew.readinessLeft = 0
            crew.travelLeft = 0
            crew.workLeft = 4
        }
        call.status = "WORKING"
        val raw = GameStore.toJson(game.state)
        for (index in 0 until raw.getJSONArray("incidents").length())
            raw.getJSONArray("incidents").getJSONObject(index).remove("tasks")
        val restarted = GameController(GameStore.fromJson(raw), listOf(region()))
        val restored = restarted.state.incidents.first { it.id == call.id }
        assertTrue(restored.tasks.isEmpty())
        restarted.advance(1)
        assertEquals(listOf(3, 3), restored.tasks.map(IncidentTask::minutesLeft))
        restarted.advance(3)
        assertEquals("SOLVED", restored.status)
    }

    @Test
    fun aFlexibleCrewCannotClearTwoConcurrentRequirementsAlone() {
        val game = controller()
        game.startShift()
        val flexible = game.state.units.first { it.type == "SEK" }
        val call = game.newIncident(
            IncidentTemplate("Mehrere Aufgaben", 2, "STREIFE", 4, "Test",
                requiredTypes = listOf("STREIFE", "SEK")),
            GeoPoint(flexible.lon, flexible.lat),
        )
        assertTrue(game.dispatch(call, flexible, false))
        game.advance(flexible.travelLeft + flexible.readinessLeft + 10)
        assertEquals(listOf("SEK"), call.tasks.filter(IncidentTask::done).map(IncidentTask::role))
        assertEquals(listOf("STREIFE"), game.remainingTypes(call))
        assertEquals("WORKING", call.status)
    }

    @Test
    fun trainingExactlyTwoCompletedManagementDays() {
        for (h in listOf(8, 12, 24)) {
            val g = controller()
            g.setShift(6, h)
            val u = g.state.units.first()
            val before = u.officers.first().skill
            assertTrue(g.train(u, "Erste Hilfe"))
            g.startShift()
            g.advance(h * 60)
            assertEquals(1, u.trainingLeft)
            g.nextDay()
            g.startShift()
            g.advance(h * 60)
            assertEquals(0, u.trainingLeft)
            assertEquals(before + 8, u.officers.first().skill)
            assertTrue("Erste Hilfe" in u.officers.first().qualifications)
        }
    }

    @Test
    fun fullDayRotatesActualCrewsAndPreservesHandoff() {
        val g = controller()
        g.setShift(6, 24)
        g.startShift()
        val u = g.state.units.first { it.type == "STREIFE" }
        val first = u.officers.first()
        g.advance(1430)
        assertTrue(g.state.shiftRotations >= 32 * 2)
        assertNotSame(first, u.officers.first())
        val c =
            g.newIncident(
                IncidentTemplate("Lange Lage", 1, "STREIFE", 100, "Test"),
                GeoPoint(u.lon, u.lat),
            )
        assertTrue(g.dispatch(c, u))
        g.advance(10)
        assertEquals("HANDED", c.status)
        assertEquals(c.id, u.incidentId)
        val work = u.workLeft
        g.nextDay()
        assertTrue(g.state.incidents.any { it.id == c.id })
        g.startShift()
        assertEquals("WORKING", c.status)
        assertEquals(work, u.workLeft)
        g.advance(work)
        assertTrue(c.status in listOf("SOLVED", "FAILED"))
    }

    @Test
    fun noReliefRestCompletesInsteadOfBeingExtended() {
        val g = controller()
        g.setShift(6, 24)
        val u = g.state.units.first()
        u.reliefTeams.clear()
        g.startShift()
        g.advance(480)
        assertEquals("REST", u.status)
        val end = u.restUntil
        g.advance(1)
        assertEquals(end, u.restUntil)
        g.advance(240)
        assertEquals("FREE", u.status)
    }

    @Test
    fun inventoryPurchasesArePerOfficerAndNoDuplicateCharge() {
        val g = controller("CAREER")
        val u = g.state.units.first()
        val before = g.state.budget
        assertTrue(g.equipOfficer(u, 0, "MP5"))
        assertEquals(before - 1600, g.state.budget)
        assertFalse(g.equipOfficer(u, 0, "MP5"))
        assertTrue(g.unequipOfficer(u, 0, "MP5"))
        assertTrue(g.equipOfficer(u, 0, "MP5"))
        assertEquals(before - 1600, g.state.budget)
        assertFalse("MP5" in u.officers[1].equipment)
    }

    @Test
    fun chainStartsWithStageAndFailureBranchesOnce() {
        val g = controller()
        g.startShift()
        val t =
            IncidentTemplate(
                "Outer",
                3,
                "STREIFE",
                1,
                "Test",
                deadline = 10,
                chain =
                    listOf(
                        ChainStage("a", "Initial", "KRIPO", 100, 1, onFailure = "b"),
                        ChainStage("b", "Escalation", "SEK", 30, 1),
                    ),
            )
        assertTrue(g.addTemplate(t))
        val c = g.newIncident(t)
        assertEquals("Initial", c.title)
        assertEquals("KRIPO", c.required)
        assertEquals(100, c.duration)
        // Deadlines scale with the size of the territory; fail right after the actual deadline.
        assertTrue(c.deadline >= 10)
        g.advance(c.deadline + 1)
        assertEquals("FAILED", c.status)
        assertEquals(1, g.state.incidents.count { it.title == "Escalation" })
        g.advance(1)
        assertEquals(1, g.state.incidents.count { it.title == "Escalation" })
        assertFalse(
            g.addTemplate(
                t.copy(chain = listOf(ChainStage("a", "Loop", "STREIFE", 10, onSuccess = "a")))
            )
        )
    }

    @Test
    fun saveRoundTripAndInvalidReferencesFailBeforeUse() {
        val g = controller()
        g.startShift()
        val c = g.state.incidents.first()
        g.dispatch(c, g.state.units.first())
        g.advance(1)
        val json = GameStore.toJson(g.state)
        val restored = GameStore.fromJson(JSONObject(json.toString()))
        assertEquals(json.toString(), GameStore.toJson(restored).toString())
        val bad = JSONObject(json.toString())
        bad.getJSONArray("units").getJSONObject(0).put("vehicleSpeed", -2)
        try {
            GameStore.fromJson(bad)
            fail("Bad speed accepted")
        } catch (_: IllegalArgumentException) {}
        val badRef = JSONObject(json.toString())
        badRef
            .getJSONArray("incidents")
            .getJSONObject(0)
            .put("assignedUnits", org.json.JSONArray(listOf("MISSING")))
        try {
            GameStore.fromJson(badRef)
            fail("Bad reference accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun migrateWebSchemaPreservesProfilesBudgetEquipmentAssignments() {
        val old =
            JSONObject()
                .put("version", 3)
                .put("schemaVersion", 3)
                .put("mode", "career")
                .put("stage", "running")
                .put("region", "Hamburg")
                .put("regionId", "HH")
                .put("day", 4)
                .put("budget", 123456)
                .put("shiftType", "full24")
                .put("startHour", 22)
                .put("shiftDurationMinutes", 1440)
                .put("elapsed", 800)
                .put(
                    "units",
                    org.json
                        .JSONArray()
                        .put(
                            JSONObject()
                                .put("id", 11)
                                .put("name", "Mein Wagen")
                                .put("type", "STREIFE")
                                .put("position", org.json.JSONArray(listOf(10.02, 53.56)))
                                .put(
                                    "officers",
                                    org.json
                                        .JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("name", "Nora Alt")
                                                .put("avatar", 2)
                                                .put("skill", 80)
                                                .put("energy", 70)
                                                .put("xp", 47)
                                                .put(
                                                    "equipped",
                                                    org.json.JSONArray(listOf("radio", "vest")),
                                                )
                                                .put(
                                                    "owned",
                                                    org.json.JSONArray(
                                                        listOf("radio", "vest", "bodycam")
                                                    ),
                                                )
                                        ),
                                )
                        ),
                )
                .put("templates", org.json.JSONArray())
                .put("incidents", org.json.JSONArray())
        val s = GameStore.fromJson(old)
        assertEquals(123456, s.budget)
        assertEquals(1440, s.duration)
        assertEquals(1320, s.startMinute)
        assertEquals("Nora Alt", s.units[0].officer.name)
        assertEquals(47, s.units[0].officer.experience)
        assertEquals(10.02, s.units[0].lon, .00001)
        assertTrue("P99" in s.units[0].officer.equipment)
        assertTrue("Bodycam" in s.units[0].officer.owned)
        assertTrue(s.legacySource.contains("Nora Alt"))
    }
}
