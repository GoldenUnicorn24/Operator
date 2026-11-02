package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

/** M3: visual editor model, validation, pack import/export, multi-branch chains, loadout rules. */
class NativeEditorTest {
    private fun region() =
        GeoRegion(
            "DE-HH",
            "Hamburg",
            listOf(listOf(GeoPoint(9.0, 53.0), GeoPoint(11.0, 53.0), GeoPoint(11.0, 54.0), GeoPoint(9.0, 54.0), GeoPoint(9.0, 53.0))),
            listOf(GeoCity("Mitte", 10.0, 53.5), GeoCity("Nord", 10.03, 53.63)),
        )

    private fun controller(mode: String = "SANDBOX"): GameController {
        val g = GameController(GameState(mode = mode), listOf(region()))
        g.seedUnitsIfNeeded()
        g.state.autoCalls = false
        return g
    }

    private fun st(id: String, ok: String = "", fail: String = "", types: List<String> = listOf("STREIFE"), radio: String = "") =
        ChainStage(id, "Stufe $id", types.first(), 5, 2, ok, fail, types, radio, 90)

    private fun tpl(name: String, chain: List<ChainStage>) =
        IncidentTemplate(name, 2, "STREIFE", 5, "Anrufer meldet etwas", deadline = 120, chain = chain)

    @Test
    fun deepBranchingChainIsValidAndPlaysBothBranches() {
        val g = controller()
        // a → (✓ b → ✓ d) / (✗ c → ✗ e); five stages, three levels.
        val t = tpl("Mehrstufiger Test", listOf(st("a", "b", "c"), st("b", "d"), st("c", fail = "e"), st("d", types = listOf("KRIPO"), radio = "Kripo übernimmt"), st("e")))
        assertEquals(emptyList<String>(), g.validateTemplate(t))
        assertTrue(g.addTemplate(t))
        g.startShift()
        val first = g.newIncident(t, GeoPoint(10.0, 53.5))
        assertEquals("a", first.stageId)
        // Force a success and check that stage b follows with its own requirements.
        first.status = "WORKING"
        val u = g.state.units.first { it.type == "STREIFE" }
        g.dispatch(first, u)
        var guard = 0
        while (first.status !in listOf("SOLVED", "FAILED") && guard++ < 400) g.advance(1)
        val next = g.state.incidents.first { it.id != first.id && it.chain.isNotEmpty() }
        assertEquals(if (first.status == "SOLVED") "b" else "c", next.stageId)
    }

    @Test
    fun validationExplainsCyclesUnreachableDuplicatesAndBadReferences() {
        val g = controller()
        val cycle = g.validateTemplate(tpl("Kreis", listOf(st("a", "b"), st("b", "a"))))
        assertTrue(cycle.any { it.startsWith("Schleife") && it.contains("→") })
        val orphan = g.validateTemplate(tpl("Waise", listOf(st("a"), st("x"))))
        assertTrue(orphan.any { it.contains("nicht erreichbar") })
        val missing = g.validateTemplate(tpl("Fehlt", listOf(st("a", "zz"))))
        assertTrue(missing.any { it.contains("fehlende Stufe") })
        val dup = g.validateTemplate(tpl("Ruhestörung", emptyList()))
        assertTrue(dup.any { it.contains("bereits") })
        assertTrue(g.validateTemplate(tpl("", emptyList())).any { it.contains("Namen") })
        assertFalse(g.addTemplate(tpl("Kreis", listOf(st("a", "b"), st("b", "a")))))
    }

    @Test
    fun scenarioPackRoundTripAndRejectsForeignFiles() {
        val g = controller()
        val t = tpl("Exporttest", listOf(st("a", "b", radio = "Erster Funk"), st("b")))
        assertTrue(g.addTemplate(t))
        val raw = GameStore.exportTemplates(g.state.customTemplates).toString()
        val back = GameStore.importTemplates(raw)
        assertEquals(1, back.size)
        assertEquals("Erster Funk", back[0].chain[0].radio)
        assertEquals(90, back[0].chain[0].deadline)
        // Re-import of the same pack is caught as duplicate.
        assertTrue(g.validateTemplate(back[0]).any { it.contains("bereits") })
        try {
            GameStore.importTemplates("{\"foo\":1}")
            fail("foreign file accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Einsatzpaket"))
        }
        // Edited version replaces the old one.
        assertTrue(g.replaceTemplate("Exporttest", t.copy(title = "Exporttest 2")))
        assertEquals("Exporttest 2", g.state.customTemplates.single().title)
        // Save game keeps chain radio and timer.
        val loaded = GameStore.fromJson(GameStore.toJson(g.state))
        assertEquals("Erster Funk", loaded.customTemplates.single().chain[0].radio)
    }

    @Test
    fun fixedPlaceOutsideJurisdictionNeverCrashesRandomCalls() {
        val g = controller()
        assertTrue(g.addTemplate(tpl("Fester Ort", emptyList()).copy(region = "DE-HH", lat = 53.55, lon = 10.0)))
        g.state.customTemplates[0] = g.state.customTemplates[0].copy(lat = 60.0, lon = 20.0)
        g.startShift()
        repeat(200) { g.newIncident(g.pickTemplate()) }
    }

    @Test
    fun loadoutSlotsWeightCompatibilityAndDepot() {
        val g = controller("CAREER")
        val patrol = g.state.units.first { it.type == "STREIFE" }
        val o = patrol.officers[0]
        assertFalse("G36 not for patrol", g.equipOfficer(patrol, 0, "G36"))
        assertTrue(g.lastError.contains("passt nicht"))
        // Protection slot holds two items.
        o.equipment.removeAll { GameContent.slotOf[it] == "Schutz" }
        assertTrue(g.equipOfficer(patrol, 0, "Schutzweste"))
        assertTrue(g.equipOfficer(patrol, 0, "Schutzhelm"))
        assertFalse(g.equipOfficer(patrol, 0, "Schutzschild"))
        assertTrue(g.lastError.contains("belegt"))
        // Weight limit.
        assertTrue(GameContent.loadWeight(o.equipment) <= GameContent.MAX_WEIGHT)
        // Depot: buy once, issue without paying again.
        val budget = g.state.budget
        assertTrue(g.buyStock("Drohne"))
        assertEquals(budget - GameContent.prices.getValue("Drohne"), g.state.budget)
        assertTrue(g.equipOfficer(patrol, 0, "Drohne"))
        assertEquals(budget - GameContent.prices.getValue("Drohne"), g.state.budget)
        assertEquals(0, g.state.depot["Drohne"])
        assertTrue(g.returnToDepot(patrol, 0, "Drohne"))
        assertEquals(1, g.state.depot["Drohne"])
        assertFalse("Drohne" in o.equipment)
        // Equipment unlocks concrete decisions.
        assertTrue(GameContent.unlocks("Drohne").isNotEmpty())
        assertEquals(1, GameStore.fromJson(GameStore.toJson(g.state)).depot["Drohne"])
    }

    @Test
    fun careerGatingAndDifficulty() {
        val g = controller("CAREER")
        assertFalse(g.setRegion("DE-BY").also { })
        g.state.mode = "SANDBOX"
        // Legacy saves without the field keep every state open.
        val legacy = GameStore.toJson(GameState()).apply { remove("unlockedRegions") }
        assertEquals(16, GameStore.fromJson(legacy).unlockedRegions.size)
        val easy = controller()
        easy.state.difficulty = "LEICHT"
        assertTrue(easy.difficultyRate() < 1 && easy.difficultyDeadline() > 1)
    }

    @Test
    fun trainingRequirementForSpecialists() {
        val g = controller()
        g.state.trainingRequired = true
        g.startShift()
        val sek = g.state.units.first { it.type == "SEK" }
        val c = g.newIncident(NativeCatalog.events.first { it.title == "Geisellage" }, GeoPoint(10.0, 53.5))
        assertTrue("seeded SEK carry their course", g.dispatch(c, sek))
        val mek = g.state.units.first { it.type == "MEK" }
        mek.officers.forEach { it.qualifications.clear() }
        val o = g.newIncident(NativeCatalog.events.first { it.title == "Observation" }, GeoPoint(10.0, 53.5))
        assertFalse(g.dispatch(o, mek))
        assertTrue(g.lastError.contains("Ausbildungspflicht"))
    }
}
