package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

/** M2: data-driven multi-phase incidents, decisions with consequences, SEK/MEK, balancing. */
class NativeScenarioTest {
    private fun region() =
        GeoRegion(
            "DE-HH",
            "Hamburg",
            listOf(listOf(GeoPoint(9.0, 53.0), GeoPoint(11.0, 53.0), GeoPoint(11.0, 54.0), GeoPoint(9.0, 54.0), GeoPoint(9.0, 53.0))),
            listOf(GeoCity("Mitte", 10.0, 53.5), GeoCity("Nord", 10.03, 53.63), GeoCity("Süd", 9.95, 53.42)),
        )

    private fun controller(mode: String = "SANDBOX"): GameController {
        val g = GameController(GameState(mode = mode), listOf(region()))
        g.seedUnitsIfNeeded()
        g.state.autoCalls = false
        g.startShift()
        g.state.incidents.clear()
        return g
    }

    private fun template(title: String) = NativeCatalog.events.first { it.title == title }

    private fun arrive(g: GameController, c: IncidentState, types: List<String> = c.requiredTypes) {
        for (t in types) {
            val u = g.candidates(c).firstOrNull { (u, _) -> u.type == t || g.canHandle(u, t) }?.first ?: continue
            g.dispatch(c, u)
        }
        var guard = 0
        while (c.firstArrival < 0 && guard++ < 300) g.advance(1)
    }

    @Test
    fun catalogHasThirtyDistinctValidScenarios() {
        val titles = NativeCatalog.events.map { it.title }.toSet()
        assertTrue("≥30 scenarios: ${Scenarios.all.size}", Scenarios.all.size >= 30)
        assertEquals(Scenarios.all.size, Scenarios.all.map { it.id }.toSet().size)
        for (sc in Scenarios.all) {
            assertTrue("${sc.id} matches a template", sc.id in titles)
            assertTrue(sc.caller.isNotEmpty() && sc.success.isNotBlank() && sc.failure.isNotBlank())
            assertTrue("${sc.id} has decisions", sc.stages.isNotEmpty())
            for (st in sc.stages) {
                assertTrue(st.trigger in listOf("CALL", "ARRIVAL", "WORK"))
                assertTrue("${sc.id}/${st.id} ≥2 options", st.options.size >= 2)
                val def = st.options.firstOrNull { it.id == st.default }
                assertNotNull("${sc.id}/${st.id} default", def)
                assertTrue("default must always be possible", def!!.needs.isBlank())
                // No two buttons with the same consequences.
                assertEquals("${sc.id}/${st.id} distinct effects", st.options.size, st.options.map { it.effect.copy(note = "") }.toSet().size)
                for (o in st.options) {
                    assertTrue(o.effect.add.all { it in GameController.TYPES })
                    if (o.needs.isNotBlank()) {
                        val (k, v) = o.needs.split(':')
                        assertTrue(k in listOf("TYPE", "GEAR", "QUAL", "FLAG"))
                        when (k) {
                            "TYPE" -> assertTrue(v in GameController.TYPES)
                            "GEAR" -> assertTrue("$v is equipment", v in GameContent.equipment)
                            "QUAL" -> assertTrue("$v is a course", v in GameContent.courses)
                        }
                    }
                }
            }
            for (fu in sc.follow) assertTrue(fu.types.all { it in GameController.TYPES } && fu.types.isNotEmpty())
        }
        // All 48 templates still exist.
        assertEquals(48, NativeCatalog.events.size)
    }

    @Test
    fun phasesTriggerOnCallArrivalAndDuringWork() {
        val g = controller()
        val c = g.newIncident(template("Vermisste Person im Park"), GeoPoint(10.0, 53.5))
        assertEquals("Vermisste Person im Park", c.scenario)
        assertEquals("call", c.pending)
        assertTrue(g.choose(c, "dog"))
        assertTrue("HUND" in c.requiredTypes)
        assertEquals("", c.pending)
        arrive(g, c)
        var guard = 0
        while (c.pending != "work" && c.status != "SOLVED" && c.status != "FAILED" && guard++ < 400) g.advance(1)
        assertEquals("work", c.pending)
        assertTrue(g.choose(c, "both"))
        assertEquals(listOf("call", "work"), c.stagesDone)
        assertTrue(c.notes.size == 2)
    }

    @Test
    fun genericCallTakingForUnscriptedTemplates() {
        val g = controller()
        val c = g.newIncident(IncidentTemplate("Fundsache", 3, "STREIFE", 10, "Test"), GeoPoint(10.0, 53.5))
        assertEquals("", c.scenario)
        assertEquals("call", c.pending)
        val before = c.deadline
        assertTrue(g.choose(c, "ask"))
        assertTrue(c.deadline < before)
        assertTrue(c.riskDelta < 0)
    }

    @Test
    fun unansweredDecisionIsTakenByDefaultWithPenalty() {
        val g = controller()
        val c = g.newIncident(template("Ruhestörung"), GeoPoint(10.0, 53.5))
        g.advance(GameController.DECISION_TIMEOUT + 1)
        assertEquals("", c.pending)
        assertTrue(c.notes.first().startsWith("⏱"))
        assertTrue(c.riskDelta > 0)
    }

    @Test
    fun optionsUnlockedByTrainingEquipmentAndForces() {
        val g = controller()
        val c = g.newIncident(template("Ruhestörung"), GeoPoint(10.0, 53.5))
        g.choose(c, "low")
        arrive(g, c)
        assertEquals("arrival", c.pending)
        val calm = g.pendingStage(c)!!.options.first { it.id == "calm" }
        assertFalse(g.optionAvailable(c, calm).first)
        assertFalse(g.choose(c, "calm"))
        assertTrue(g.lastError.contains("Deeskalation"))
        val u = g.state.units.first { it.id == c.assignedUnits.first() }
        u.officers.first().qualifications.add("Deeskalation")
        assertTrue(g.optionAvailable(c, calm).first)
        assertTrue(g.choose(c, "calm"))
    }

    @Test
    fun decisionsChangeTheOddsVisibly() {
        fun odds(choice: String): Double {
            val g = controller()
            val c = g.newIncident(template("Bewaffnete Bedrohung"), GeoPoint(10.0, 53.5))
            g.choose(c, "cordon")
            arrive(g, c)
            g.choose(c, choice)
            val units = c.assignedUnits.mapNotNull { id -> g.state.units.find { it.id == id } }
            return g.assess(c, units).first
        }
        val careful = odds("negotiate")
        val fast = odds("sek")
        assertTrue("negotiation safer ($careful vs $fast)", careful > fast + .08)
    }

    @Test
    fun requestedForcesReopenTheCall() {
        val g = controller()
        val c = g.newIncident(template("Verkehrsunfall"), GeoPoint(10.0, 53.5))
        g.choose(c, "police")
        arrive(g, c)
        assertTrue(g.choose(c, "vpi"))
        assertEquals(listOf("VERKEHR"), g.remainingTypes(c))
        // Without the traffic police the scene cannot close normally.
        g.advance(c.duration + 5)
        assertTrue(c.status in listOf("DRIVING", "WORKING"))
    }

    @Test
    fun sekIsScarceExpensiveAndNeedsRecovery() {
        val g = controller("CAREER")
        val sek = g.state.units.first { it.type == "SEK" }
        val c = g.newIncident(template("Geisellage"), GeoPoint(10.0, 53.5))
        val budget = g.state.budget
        assertTrue(g.dispatch(c, sek))
        assertEquals(budget - g.deploymentCost("SEK"), g.state.budget)
        assertTrue(g.state.units.count { it.type == "SEK" } <= 3)
        // Finish the call, then the SEK is unavailable during recovery even on its way home.
        g.choose(c, "cordon")
        for (t in g.remainingTypes(c)) g.candidates(c).firstOrNull { (u, _) -> g.canHandle(u, t) }?.let { g.dispatch(c, it.first) }
        var guard = 0
        while (c.status !in listOf("SOLVED", "FAILED") && guard++ < 600) g.advance(1)
        assertTrue(c.status in listOf("SOLVED", "FAILED"))
        assertTrue(sek.restUntil > g.state.absoluteMinute + 60)
        assertFalse(g.dispatchable(sek))
        val report = g.state.shiftSpecialCost
        assertTrue(report >= g.deploymentCost("SEK"))
        // Budget refusal is explained.
        g.state.budget = 100
        val other = g.state.units.first { it.type == "MEK" }
        val obs = g.newIncident(template("Observation"), GeoPoint(10.0, 53.5))
        assertFalse(g.dispatch(obs, other))
        assertTrue(g.lastError.contains("Budget"))
    }

    @Test
    fun specialistsOnRoutineCallsAreNoted() {
        val g = controller()
        val c = g.newIncident(IncidentTemplate("Fundsache", 3, "STREIFE", 10, "Test"), GeoPoint(10.0, 53.5))
        val sek = g.state.units.first { it.type == "SEK" }
        assertTrue(g.dispatch(c, sek))
        assertTrue(g.misusedSpecialists(c, listOf(sek)))
        assertTrue(g.assess(c, listOf(sek)).second.any { it.contains("Spezialkräfte") })
    }

    @Test
    fun flagsSpawnFollowUpsAndPriorityChangesTheStakes() {
        val g = controller()
        g.state.randomSeed = 7
        val c = g.newIncident(template("Observation"), GeoPoint(10.0, 53.5))
        arrive(g, c)
        assertTrue(g.choose(c, "watch"))
        assertTrue("intel" in c.flags)
        val before = g.state.incidents.size
        var guard = 0
        while (c.status !in listOf("SOLVED", "FAILED") && guard++ < 600) g.advance(1)
        assertTrue("follow-up spawned", g.state.incidents.any { it.title == "Auswertung der Beobachtung" } || g.state.incidents.size > before)
        val p1 = IncidentState("a", "x", "", "DE-HH", 0.0, 0.0, 1, "STREIFE", 1, "")
        val p3 = IncidentState("b", "x", "", "DE-HH", 0.0, 0.0, 3, "STREIFE", 1, "")
        assertTrue(g.failurePenalty(p1) > g.failurePenalty(p3) * 3)
    }

    @Test
    fun scenarioStateSurvivesSaveAndLoad() {
        val g = controller()
        val c = g.newIncident(template("Einbruchserie"), GeoPoint(10.0, 53.5))
        arrive(g, c)
        g.choose(c, "pattern")
        val r = GameStore.fromJson(GameStore.toJson(g.state)).incidents.first { it.id == c.id }
        assertEquals(c.scenario, r.scenario)
        assertEquals(c.stagesDone, r.stagesDone)
        assertEquals(c.flags, r.flags)
        assertEquals(c.notes, r.notes)
        assertEquals(c.riskDelta, r.riskDelta, 1e-9)
        val snap = GameSnapshots.capture(g.state).incidents.first { it.id == c.id }
        c.notes.clear()
        assertEquals(1, snap.notes.size)
    }
}
