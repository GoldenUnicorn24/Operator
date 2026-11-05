package de.einsatzleitstelle110

import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** M4: end-to-end checks along the final acceptance criteria, on the bundled real geography. */
class NativeAcceptanceTest {
    @After
    fun cleanup() {
        NativeRouting.clear()
        MapData.clear()
    }

    private fun asset(name: String) =
        listOf(File("src/main/native_assets/$name"), File("app/src/main/native_assets/$name")).firstOrNull { it.isFile }

    private fun world(code: String): Pair<List<GeoRegion>, StateMap>? {
        val states = asset("states.geojson") ?: return null
        val bin = asset("geo/roads-$code.bin") ?: return null
        val regions = GeoData.parseStates(states.readText())
        val map = MapData.parseBinary(code, bin.readBytes())
        NativeRouting.install(map)
        return regions to map
    }

    private fun controller(code: String, state: GameState = GameState(region = code, mode = "SANDBOX")): GameController? {
        val (regions, map) = world(code) ?: return null
        val region = regions.first { it.code == code }
        val g = GameController(state, regions)
        val places = map.places.filter { region.contains(GeoPoint(it.lon, it.lat)) }
        g.placeProvider = { r -> if (r.code == code) places else r.cities.map { MapPlace(it.name, "city", 80000, it.lon, it.lat) } }
        g.seedUnitsIfNeeded()
        return g
    }

    private fun play(g: GameController) {
        for (call in g.state.incidents.filter { it.region == g.state.region && it.status in listOf("OPEN", "DRIVING", "WORKING") }.sortedBy { it.priority }) {
            if (call.pending.isNotBlank()) {
                val st = g.pendingStage(call) ?: continue
                st.options.filter { g.optionAvailable(call, it).first && it.effect.cost <= g.state.budget }.minByOrNull { it.effect.risk }?.let { g.choose(call, it.id) }
            }
            if (call.status == "WORKING" && g.remainingTypes(call).isEmpty()) continue
            for (need in g.remainingTypes(call)) {
                val c = g.candidates(call).firstOrNull { (u, _) -> u.type == need } ?: g.candidates(call).firstOrNull { (u, _) -> g.canHandle(u, need) && u.type !in listOf("SEK", "MEK") }
                if (c != null) g.dispatch(call, c.first)
            }
        }
    }

    @Test
    fun fullShiftsInHamburgNiedersachsenAndBayern() {
        for ((code, start) in listOf("DE-HH" to 14, "DE-NI" to 6, "DE-BY" to 22)) {
            val g = controller(code) ?: return
            g.state.randomSeed = 20261102L + code.hashCode()
            g.setShift(start, 8)
            g.startShift()
            var routed = 0
            var drives = 0
            while (g.state.phase == "RUNNING") {
                val before = g.state.units.associate { it.id to it.status }
                play(g)
                for (u in g.state.units) if (before[u.id] == "FREE" && u.status == "DRIVING") {
                    drives++
                    if (u.routeBasis.endsWith("ROADS_APPROXIMATION") || u.routeBasis == "AIR_DIRECT") routed++
                }
                g.advance(1)
            }
            val r = g.state.reports.first()
            val region = g.regions.first { it.code == code }
            for (c in g.state.incidents.filter { it.status == "FAILED" }.take(8))
                println("   FAIL ${c.title} P${c.priority} ${c.requiredTypes} Ankunft ${c.firstArrival}/${c.deadline} – ${c.outcome.take(90)}")
            println("$code Schicht ab $start Uhr: Notrufe ${r.served}, gelöst ${r.solved}, gescheitert ${r.failed}, übergeben ${r.handed}, Fahrten $drives, auf Straßen/Luft $routed, Entscheidungen ${r.decisions}")
            assertEquals("DEBRIEF", g.state.phase)
            assertTrue("$code calls ${r.served}", r.served >= 6)
            assertTrue("$code solved ${r.solved}/${r.served}", r.solved * 10 >= r.served * 6)
            assertTrue("$code routed $routed/$drives", routed * 4 >= drives * 3)
            assertTrue(r.decisions > 0)
            assertTrue(g.state.incidents.all { region.contains(GeoPoint(it.lon, it.lat)) })
            cleanup()
        }
    }

    @Test
    fun ownScenarioCreatedExportedImportedSavedAndPlayedNextDay() {
        val author = controller("DE-HH") ?: return
        val t =
            IncidentTemplate(
                "Abnahme: Einbruch mit Folgeermittlung",
                2,
                "STREIFE",
                8,
                "Nachbar hört Glas splittern",
                deadline = 120,
                chain =
                    listOf(
                        ChainStage("s1", "Einbruch gemeldet", "STREIFE", 8, 2, "s2", "s3", listOf("STREIFE")),
                        ChainStage("s2", "Spurensicherung", "KRIPO", 10, 3, "", "", listOf("KRIPO"), "Kripo übernimmt", 180),
                        ChainStage("s3", "Fahndung", "STREIFE", 10, 2, "", "", listOf("STREIFE"), "Täter flüchtig", 60),
                    ),
            )
        assertTrue(author.addTemplate(t))
        val pack = GameStore.exportTemplates(author.state.customTemplates).toString()
        cleanup()
        // A different player imports the pack into a fresh career save.
        val g = controller("DE-HH", GameState(region = "DE-HH", mode = "CAREER")) ?: return
        GameStore.importTemplates(pack).forEach { assertTrue(g.validateTemplate(it).isEmpty()); assertTrue(g.addTemplate(it)) }
        g.setShift(8, 8)
        g.startShift()
        g.advance(480)
        g.nextDay()
        // Save and reload between days.
        val loaded = GameStore.fromJson(GameStore.toJson(g.state))
        assertEquals(2, loaded.day)
        assertEquals(1, loaded.customTemplates.size)
        val day2 = GameController(loaded, g.regions)
        day2.placeProvider = g.placeProvider
        day2.state.autoCalls = false
        day2.setShift(9, 8)
        day2.startShift()
        val first = day2.newIncident(loaded.customTemplates.single())
        assertEquals("s1", first.stageId)
        var guard = 0
        while (guard++ < 300 && day2.state.incidents.none { it.stageId in listOf("s2", "s3") }) {
            play(day2)
            day2.advance(1)
        }
        val next = day2.state.incidents.first { it.stageId in listOf("s2", "s3") }
        assertEquals(if (first.status == "SOLVED") "s2" else "s3", next.stageId)
        assertTrue(next.description == "Kripo übernimmt" || next.description == "Täter flüchtig")
    }

    @Test
    fun saveDuringTwentyFourHourShiftOverMidnightContinuesIdentically() {
        val g = controller("DE-HH") ?: return
        g.state.randomSeed = 77
        g.setShift(20, 24)
        g.startShift()
        repeat(300) {
            play(g)
            g.advance(1)
        }
        assertTrue("past midnight", g.state.absoluteMinute % 1440 < 20 * 60)
        val copy = GameController(GameStore.fromJson(GameStore.toJson(g.state)), g.regions)
        copy.placeProvider = g.placeProvider
        repeat(400) {
            play(g)
            g.advance(1)
            play(copy)
            copy.advance(1)
        }
        assertEquals(GameStore.toJson(g.state).toString(), GameStore.toJson(copy.state).toString())
        while (g.state.phase == "RUNNING") {
            play(g)
            g.advance(1)
        }
        assertEquals(1, g.state.reports.size)
        assertEquals(1440, g.state.reports.first().elapsed)
        assertEquals(1, g.state.completedShifts)
    }
}
