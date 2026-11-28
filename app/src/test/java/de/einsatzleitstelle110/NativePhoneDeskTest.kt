package de.einsatzleitstelle110

import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** The 110 desk on real Hamburg data, plus a bot that plays whole shifts at different skill levels. */
class NativePhoneDeskTest {
    @After
    fun cleanup() {
        NativeRouting.clear()
        MapData.clear()
    }

    private fun game(seed: Long = 7L, code: String = "DE-HH"): GameController {
        val states = NativeTestAssets.bundled("states.geojson")
        val bin = NativeTestAssets.bundled("geo/roads-$code.bin")
        val regions = GeoData.parseStates(states.readText())
        val map = MapData.parseBinary(code, bin.readBytes())
        NativeRouting.install(map)
        val state = GameState(region = code, mode = "CAREER", phoneEnabled = true, randomSeed = seed)
        val g = GameController(state, regions)
        val region = regions.first { it.code == code }
        val places = map.places.filter { region.contains(GeoPoint(it.lon, it.lat)) }
        g.placeProvider = { r -> if (r.code == code) places else r.cities.map { MapPlace(it.name, "city", 80000, it.lon, it.lat) } }
        g.seedUnitsIfNeeded()
        return g
    }

    private fun script(id: String) = CallScripts.find(id)!!

    private fun started(id: String): Pair<GameController, PhoneCall> {
        val g = game()
        g.state.autoCalls = false
        g.setShift(10, 8)
        g.startShift()
        g.state.phones.clear()
        val c = PhoneDesk.ring(g, script(id))!!
        assertTrue(PhoneDesk.answer(g, c))
        return g to c
    }

    @Test fun everyScriptIsConsistent() {
        val g = GameController(GameState(), emptyList())
        assertTrue(CallScripts.all.size >= 50)
        assertEquals(CallScripts.all.size, CallScripts.all.map { it.id }.toSet().size)
        for (s in CallScripts.all) {
            assertTrue(s.id, s.opening.isNotBlank() && s.questions.isNotEmpty())
            assertEquals(s.id, s.questions.size, s.questions.map { it.id }.toSet().size)
            if (s.kind == "REAL") {
                val t = PhoneDesk.template(g, s.correct)
                assertNotNull("${s.id}: ${s.correct}", t)
                assertTrue(s.id, t!!.requiredTypes.all { it in GameController.TYPES })
                assertTrue(s.id, s.questions.any { it.id == "wo" } || s.questions.any { it.label.contains("Wo") } || s.vague)
                assertTrue("${s.id}: Stichwort doppelt", s.correct !in s.wrong)
            } else assertEquals(s.id, "", s.correct)
            s.wrong.forEach { assertNotNull("${s.id}: $it", PhoneDesk.template(g, it)) }
            (s.worseAdd + s.twistAdd).forEach { assertTrue(s.id, it in GameController.TYPES) }
            if (s.twistQuestion.isNotBlank()) {
                assertTrue(s.id, s.questions.any { it.id == s.twistQuestion })
                assertTrue(s.id, s.twistRadio.isNotBlank() && s.twistAdd.isNotEmpty())
            }
            if (s.advice.isNotEmpty()) assertTrue(s.id, s.advice.any { it.good })
        }
    }

    @Test fun completeCallIsRatedHighAndSendsRightForcesAtOnce() {
        val (g, c) = started("unfall_verletzt")
        for (q in script("unfall_verletzt").questions) PhoneDesk.ask(g, c, q.id)
        PhoneDesk.advise(g, c, "sichern")
        val result = PhoneDesk.classify(g, c, PhoneDesk.codes(g, c).first { it.correct })
        assertEquals("Vorbildlich", result.rating)
        val i = result.incident!!
        assertTrue("FEUERWEHR" in i.requiredTypes)
        assertTrue(i.surprise.isEmpty())
        assertEquals(1, i.priority)
        assertTrue(i.deadline <= 40)
    }

    @Test fun unaskedQuestionComesBackAsSurpriseOnScene() {
        val (g, c) = started("unfall_verletzt")
        PhoneDesk.ask(g, c, "wo")
        val i = PhoneDesk.classify(g, c, PhoneDesk.codes(g, c).first { it.correct }).incident!!
        assertFalse("FEUERWEHR" in i.requiredTypes)
        assertEquals(listOf("FEUERWEHR"), i.surprise)
        for (need in g.remainingTypes(i)) {
            val u = g.candidates(i).first { (u, _) -> g.canHandle(u, need) }.first
            assertTrue(g.dispatch(i, u))
        }
        var guard = 0
        while (i.firstArrival < 0 && guard++ < 60) g.advance(1)
        assertTrue(i.firstArrival >= 0)
        assertTrue("FEUERWEHR" in i.requiredTypes)
        assertTrue(g.remainingTypes(i).isNotEmpty())
        assertTrue(g.state.logs.any { it.contains("Nachforderung") })
    }

    @Test fun panicNeedsCalmingFirst() {
        val (g, c) = started("herzstillstand")
        PhoneDesk.ask(g, c, "wo")
        assertFalse("wo" in c.asked)
        assertFalse(c.located)
        PhoneDesk.calm(g, c)
        PhoneDesk.ask(g, c, "wo")
        assertTrue(c.located)
    }

    @Test fun theCommonQuestionGuidePreservesScriptedRevelations() {
        val (g, c) = started("einbrecher_daheim")
        val script = script("einbrecher_daheim")
        val beat = script.beats.getValue(2)
        assertTrue(PhoneDesk.ask(g, c, "general.people"))
        assertFalse(c.located)
        assertTrue(c.lines.last().contains("nicht sicher"))
        assertTrue(PhoneDesk.ask(g, c, "wo"))
        assertTrue(c.lines.none { it.contains(beat) })
        assertTrue(PhoneDesk.ask(g, c, "wieviel"))
        assertEquals(1, c.lines.count { it.contains(beat) })
        assertTrue(PhoneDesk.ask(g, c, "general.development"))
        assertEquals(1, c.lines.count { it.contains(beat) })
        assertFalse(PhoneDesk.ask(g, c, "general.people"))
        PhoneDesk.hold(g, c)
        assertFalse(PhoneDesk.ask(g, c, "general.callback"))
    }

    @Test fun unnecessaryQuestionsAffectTheEvaluationAndTheInterviewSurvivesSaving() {
        val (focusedGame, focusedCall) = started("unfall_verletzt")
        val (longGame, longCall) = started("unfall_verletzt")
        for ((g, c) in listOf(focusedGame to focusedCall, longGame to longCall)) {
            PhoneDesk.calm(g, c)
            for (q in script("unfall_verletzt").questions) assertTrue(PhoneDesk.ask(g, c, q.id))
            PhoneDesk.advise(g, c, "sichern")
        }
        for (topic in QuestionTopic.entries) {
            val common = CallQuestions.forTopic(script("unfall_verletzt"), topic).last()
            assertTrue(PhoneDesk.ask(longGame, longCall, common.id))
        }
        val restored = GameStore.fromJson(GameStore.toJson(longGame.state))
        assertEquals(longCall.asked, PhoneDesk.active(restored)?.asked)
        assertEquals(longCall.lines, PhoneDesk.active(restored)?.lines)
        val focused = PhoneDesk.classify(focusedGame, focusedCall, PhoneDesk.codes(focusedGame, focusedCall).first { it.correct })
        val long = PhoneDesk.classify(longGame, longCall, PhoneDesk.codes(longGame, longCall).first { it.correct })
        assertEquals(focused.score - 15, long.score)
        assertTrue(long.feedback.any { it.contains("Zusatzfragen") })
        assertTrue(long.incident!!.requiredTypes.contains("FEUERWEHR"))
    }

    @Test fun vagueCallerNeedsMobileLocation() {
        val (g, c) = started("autobahn_falschfahrer")
        PhoneDesk.ask(g, c, "wo")
        assertFalse(c.located)
        PhoneDesk.locate(g, c)
        assertTrue(c.located)
    }

    @Test fun prankIsNotDispatchedWhenRecognised() {
        val (g, c) = started("fake_unfall_prank")
        val before = g.state.incidents.size
        val r = PhoneDesk.classify(g, c, PhoneDesk.codes(g, c).first { it.none })
        assertEquals(100, r.score)
        assertEquals(before, g.state.incidents.size)
    }

    @Test fun prankBelievedWastesAPatrol() {
        val (g, c) = started("fake_unfall_prank")
        val r = PhoneDesk.classify(g, c, PhoneDesk.codes(g, c).first { !it.none })
        assertEquals("Blinder Alarm", r.incident!!.title)
        assertTrue(r.score < 40)
    }

    @Test fun rejectingARealEmergencyCostsTrustAndComesBack() {
        val (g, c) = started("schlaegerei")
        val rep = g.state.reputation
        val r = PhoneDesk.classify(g, c, PhoneDesk.codes(g, c).first { it.none })
        assertEquals(0, r.score)
        assertNotNull(r.incident)
        assertTrue(g.state.reputation < rep)
    }

    @Test fun duplicateCallerIsMergedWithoutNewIncident() {
        val (g, c) = started("schlaegerei")
        PhoneDesk.ask(g, c, "wo")
        val first = PhoneDesk.classify(g, c, PhoneDesk.codes(g, c).first { it.correct }).incident!!
        val second = PhoneDesk.ring(g, script("schlaegerei"), duplicateOf = first)!!
        PhoneDesk.answer(g, second)
        val merge = PhoneDesk.codes(g, second).first { it.merge }
        assertTrue(merge.correct)
        assertEquals(1, PhoneDesk.codes(g, second).count { it.correct })
        val before = g.state.incidents.size
        assertEquals(100, PhoneDesk.classify(g, second, merge).score)
        assertEquals(before, g.state.incidents.size)
        assertEquals(2, first.duplicateCallers)
    }

    @Test fun unansweredPhoneHangsUpAndBecomesVagueIncident() {
        val (g, c) = started("schlaegerei")
        PhoneDesk.hold(g, c)
        g.advance(PhoneDesk.PATIENCE + 1)
        assertTrue(g.state.phones.isEmpty())
        assertEquals(1, g.state.shiftMissed)
        val i = g.state.incidents.first()
        assertTrue(i.disconnected && i.addressUncertain)
    }

    @Test fun waitingEscalatesAndAddsForces() {
        val (g, c) = started("schlaegerei")
        PhoneDesk.ask(g, c, "wo"); PhoneDesk.ask(g, c, "wer")
        val i = PhoneDesk.classify(g, c, PhoneDesk.codes(g, c).first { it.correct }).incident!!
        val needed = i.requiredTypes.size
        g.advance(i.deadline / 2 + 2)
        assertTrue(i.worsened)
        assertTrue(i.requiredTypes.size > needed)
        assertTrue(g.state.logs.any { it.contains("Lageverschärfung") })
    }

    @Test fun playerAnswersAsPoliceOfTheState() {
        val (_, c) = started("gas")
        assertTrue(c.lines.first().substringAfter('|').startsWith("Notruf, Polizei Hamburg"))
    }

    @Test fun hamburgCallsRingAtTheirPlace() {
        val (g, c) = started("hh_kiez_flasche")
        assertTrue(GeoData.km(GeoPoint(c.lon, c.lat), CallScripts.find("hh_kiez_flasche")!!.place!!) < 1.5)
        assertTrue(g.region().contains(GeoPoint(c.lon, c.lat)))
    }

    @Test fun standardRadioClipsStayRecordedAndCustomCallsignsGetDynamicLines() {
        val all = RadioScript.allClips().toSet()
        val heard = RadioScript.event("Einheit unterwegs", "Peter HH 4 · 6 Sim.-Min.") + RadioScript.event("Am Einsatzort", "Peter HH 4 · Test") +
            RadioScript.event("Funkstatus", "Peter HH 4 an Leitstelle: Status 2")
        assertEquals(4, heard.size)
        assertTrue(heard.all { it.clips.isNotEmpty() && all.containsAll(it.clips) })
        val own = RadioScript.event("Einheit unterwegs", "Peter 24/1 · 6")
        assertEquals(2, own.size)
        assertTrue(own.all { line -> line.clips.any { it.text == "Peter 24 Strich 1" } })
        assertEquals("SEK HH 4", RadioScript.event("Am Einsatzort", "SEK HH 4 · 6").single().clips.first().text)
    }

    @Test fun spokenLinesNameTheDistrictNotTheStreet() {
        for (s in CallScripts.all) for (q in s.questions) assertFalse(q.answer, VoiceLines.spoken(q.answer).contains("{strasse}"))
        val c = VoiceLines.clips("m1n", "{strasse} in {ort}, Eingang B.", "Barmbek-Nord")
        assertEquals(listOf(VoiceClip("m1n", "In"), VoiceClip("m1", "Barmbek-Nord"), VoiceClip("m1n", "Eingang B.")), c)
        assertTrue(VoiceLines.clips("m1n", "(Rascheln.)", "").isEmpty())
    }

    /** Set VOICE_MANIFEST=write once after changing any text, then render the clips. */
    @Test fun voiceManifestIsInStep() {
        val states = NativeTestAssets.bundled("states.geojson")
        val bin = NativeTestAssets.bundled("geo/roads-DE-HH.bin")
        val region = GeoData.parseStates(states.readText()).first { it.code == "DE-HH" }
        val places = MapData.parseBinary("DE-HH", bin.readBytes()).places
            .filter { region.contains(GeoPoint(it.lon, it.lat)) }.map { it.name }.distinct().sorted()
        val g = GameController(GameState(), emptyList())
        val lines = VoiceLines.manifest(g.allTemplates(), places).map { "${it.id}\t${it.voice}\t${it.text}" }.sorted()
        val file = listOf(File("../tools/voices/lines.tsv"), File("tools/voices/lines.tsv")).firstOrNull { it.isFile } ?: error("Pflicht-Testdatei fehlt: tools/voices/lines.tsv")
        if (System.getenv("VOICE_MANIFEST") == "write") file.writeText(lines.joinToString("\n") + "\n")
        assertEquals(lines, file.readLines())
    }

    @Test fun cprRhythmIsMeasured() {
        assertEquals(100, PhoneDesk.cprQuality(List(20) { 545L }))
        assertEquals(0, PhoneDesk.cprQuality(List(20) { 300L }))
        assertEquals(50, PhoneDesk.cprQuality(List(10) { 550L } + List(10) { 900L }))
        assertEquals(0, PhoneDesk.cprQuality(emptyList()))
    }

    @Test fun cprSavesRiskOnlyWhenPerformed() {
        val (g, c) = started("herzstillstand")
        PhoneDesk.calm(g, c)
        PhoneDesk.ask(g, c, "wo")
        PhoneDesk.advise(g, c, "cpr")
        PhoneDesk.cprDone(g, c, 90)
        val i = PhoneDesk.classify(g, c, PhoneDesk.codes(g, c).first { it.correct }).incident!!
        assertTrue(i.riskDelta < 0)
    }

    @Test fun phoneStateSurvivesSaveRoundtrip() {
        val (g, c) = started("pizza")
        PhoneDesk.ask(g, c, "hinweis")
        val restored = GameStore.fromJson(org.json.JSONObject(GameStore.toJson(g.state).toString()))
        val p = restored.phones.single()
        assertEquals(c.scriptId, p.scriptId)
        assertEquals(c.lines, p.lines)
        assertEquals(setOf("hinweis"), p.asked)
        assertEquals("ACTIVE", p.status)
        assertTrue(restored.phoneEnabled)
    }

    @Test fun earlyDispatchKeepsTheCallerOnTheLineAndReusesTheSameIncident() {
        val (g, phone) = started("unfall_verletzt")
        val template = PhoneDesk.template(g, script(phone.scriptId).correct)!!
        assertNull(PhoneDesk.startDispatch(g, phone, template.title, 1))

        PhoneDesk.calm(g, phone)
        assertTrue(PhoneDesk.ask(g, phone, "wo"))
        g.advance(2)
        val incident = PhoneDesk.startDispatch(g, phone, template.title, 1)!!
        val firstAlarm = g.state.absoluteMinute
        assertSame(phone, PhoneDesk.active(g.state))
        assertEquals(2, incident.age)
        assertTrue(incident.reportedAt <= incident.answeredAt)
        assertTrue(incident.answeredAt < firstAlarm)
        val unit = g.state.units.first { g.dispatchable(it) && g.canHandle(it, incident.required) }
        assertTrue(g.dispatch(incident, unit))
        assertEquals(firstAlarm, incident.firstDispatchedAt)

        val saved = GameStore.fromJson(GameStore.toJson(g.state))
        val next = GameController(saved, g.regions)
        val resumed = PhoneDesk.active(saved)!!
        val linked = PhoneDesk.linkedIncident(next, resumed)!!
        assertEquals(incident.id, linked.id)
        val detail = script(phone.scriptId).twistQuestion
        assertTrue(PhoneDesk.ask(next, resumed, detail))
        assertTrue(linked.requiredTypes.containsAll(script(phone.scriptId).twistAdd))
        assertTrue(linked.phoneTranscript.contains(resumed.lines.last()))
        val roles = linked.requiredTypes.toList()
        val result = PhoneDesk.classify(next, resumed, PhoneDesk.codes(next, resumed).first { it.correct })
        assertSame(linked, result.incident)
        assertEquals(1, saved.incidents.size)
        assertEquals(listOf(unit.id), linked.assignedUnits)
        assertEquals(roles, linked.requiredTypes)
        assertTrue(saved.phones.isEmpty())
        assertTrue(linked.phoneClosedAt >= firstAlarm)
    }

    @Test fun activeAndRingingCallsSurviveHandoffAndSave() {
        val (g, active) = started("unfall_verletzt")
        PhoneDesk.ask(g, active, "wo")
        g.advance(30)
        val ringing = PhoneDesk.ring(g, script("schlaegerei"))!!
        g.advance(2)
        assertEquals(2, ringing.waited)

        g.finishShift()
        val report = g.state.reports.first()
        assertEquals(listOf(PhoneHandoff(active.id, true), PhoneHandoff(ringing.id, false)), report.phoneHandoffs)
        val saved = GameStore.fromJson(GameStore.toJson(g.state))
        assertEquals("DEBRIEF", saved.phase)
        assertEquals(report.phoneHandoffs, saved.reports.first().phoneHandoffs)

        val next = GameController(saved, g.regions)
        next.nextDay()
        next.startShift()
        assertEquals(listOf(ringing.id), PhoneDesk.ringing(saved).map { it.id })
        val resumed = PhoneDesk.active(saved)!!
        assertEquals(active.id, resumed.id)
        assertEquals(active.lines, resumed.lines)
        assertEquals(active.asked, resumed.asked)
        assertEquals(active.located, resumed.located)
        assertTrue(resumed.lines.last().contains("An Folgeschicht übergeben"))
        assertEquals(2, PhoneDesk.ringing(saved).single().waited)
        assertEquals(0, saved.shiftAnswered)
    }

    @Test fun olderPhoneEntriesWithoutStatusRemainAnswerable() {
        val (g, call) = started("pizza")
        val old = GameStore.toJson(g.state)
        old.getJSONArray("phones").getJSONObject(0).apply {
            remove("status")
            remove("region")
            remove("scope")
        }
        val restored = GameStore.fromJson(old)
        assertEquals(listOf(call.id), PhoneDesk.ringing(restored).map { it.id })
        assertEquals(call.lines, PhoneDesk.ringing(restored).single().lines)
    }

    @Test fun snapshotDoesNotShareCallTranscript() {
        val (g, c) = started("pizza")
        val copy = GameSnapshots.capture(g.state)
        PhoneDesk.ask(g, c, "hinweis")
        assertTrue(copy.phones.single().asked.isEmpty())
    }

    @Test fun gradeOrdersOutcomes() {
        assertEquals(1, PhoneDesk.grade(20, 0, 0, 95, 100))
        assertTrue(PhoneDesk.grade(10, 10, 4, 40, 30) > PhoneDesk.grade(18, 2, 1, 75, 80))
        assertEquals(6, PhoneDesk.grade(0, 10, 8, 0, 0))
    }

    /** skill 0–1: how complete the calls are; poor players also answer late and dispatch less. */
    private fun playShift(g: GameController, skill: Double, rng: java.util.Random): ShiftReport {
        g.setShift(14, 8)
        g.startShift()
        var guard = 0
        while (g.state.phase == "RUNNING" && guard++ < 700) {
            for (c in PhoneDesk.ringing(g.state).toList()) {
                if (rng.nextDouble() > .5 + skill / 2) continue
                PhoneDesk.answer(g, c)
                val sc = PhoneDesk.script(g, c)
                if (sc.mood == "PANISCH" && rng.nextDouble() < skill) PhoneDesk.calm(g, c)
                for (q in sc.questions) if (rng.nextDouble() < skill) PhoneDesk.ask(g, c, q.id)
                if (sc.vague && rng.nextDouble() < skill) PhoneDesk.locate(g, c)
                if (sc.cpr && rng.nextDouble() < skill) { PhoneDesk.advise(g, c, "cpr"); PhoneDesk.cprDone(g, c, (skill * 100).toInt()) }
                else sc.advice.firstOrNull { it.good == (rng.nextDouble() < skill) }?.let { PhoneDesk.advise(g, c, it.id) }
                val codes = PhoneDesk.codes(g, c)
                PhoneDesk.classify(g, c, if (rng.nextDouble() < .4 + skill * .6) codes.first { it.correct } else codes.first { !it.correct && !it.none })
            }
            for (i in g.state.incidents.filter { it.region == g.state.region && it.status in listOf("OPEN", "DRIVING", "WORKING") }.sortedBy { it.priority }) {
                if (i.pending.isNotBlank()) g.pendingStage(i)?.options?.filter { g.optionAvailable(i, it).first && it.effect.cost <= g.state.budget }?.minByOrNull { it.effect.risk }?.let { g.choose(i, it.id) }
                if (i.addressUncertain && rng.nextDouble() < skill) g.callback(i)
                if (rng.nextDouble() > .35 + skill * .6) continue
                for (need in g.remainingTypes(i)) {
                    val c = g.candidates(i).firstOrNull { (u, _) -> u.type == need } ?: g.candidates(i).firstOrNull { (u, _) -> g.canHandle(u, need) }
                    if (c != null) g.dispatch(i, c.first)
                }
            }
            g.advance(1)
        }
        val report = g.state.reports.first()
        g.nextDay()
        return report
    }

    @Test fun shiftIsBusyFairAndSkillDecidesTheGrade() {
        val lines = mutableListOf<String>()
        val grades = mutableMapOf<Double, MutableList<Int>>()
        for (skill in listOf(.2, .6, 1.0)) for (seed in 1L..6L) {
            val g = game(seed * 7919)
            val r = playShift(g, skill, java.util.Random(seed))
            grades.getOrPut(skill) { mutableListOf() }.add(r.grade)
            lines.add("skill=$skill seed=$seed calls=${r.served} answered=${r.answered} missed=${r.missed} ok=${r.solved} bad=${r.failed} handed=${r.handed} take=${r.takeAverage} hf=${r.hilfsfrist} note=${r.grade} ev=${r.event} rep=${g.state.reputation}")
            NativeRouting.clear(); MapData.clear()
            assertTrue(lines.last(), r.served >= 8)
        }
        println(lines.joinToString("\n"))
        val avg = grades.mapValues { it.value.average() }
        println(avg)
        assertTrue("guter Spieler muss besser sein: $avg", avg.getValue(1.0) + .8 < avg.getValue(.2))
        assertTrue("perfektes Spiel muss gut sein: $avg", avg.getValue(1.0) <= 2.6)
    }
}
