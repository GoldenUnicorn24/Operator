package de.einsatzleitstelle110

import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** M3: ten consecutive career days with planning decisions on real Hamburg geography. */
class NativeCareerTest {
    @After
    fun cleanup() {
        NativeRouting.clear()
        MapData.clear()
    }

    private fun asset(name: String) =
        listOf(File("src/main/native_assets/$name"), File("app/src/main/native_assets/$name")).firstOrNull { it.isFile }

    /** Simple player: dispatches fitting units, answers decisions with the first available option. */
    private fun play(g: GameController, decide: Boolean) {
        for (call in g.state.incidents.filter { it.region == g.state.region && it.status in listOf("OPEN", "DRIVING", "WORKING") }.sortedBy { it.priority }) {
            if (decide && call.pending.isNotBlank()) {
                val st = g.pendingStage(call)
                val pick = st?.options?.filter { g.optionAvailable(call, it).first }?.minByOrNull { it.effect.risk }
                if (pick != null) g.choose(call, pick.id)
            }
            if (call.status == "WORKING") continue
            for (need in g.remainingTypes(call)) {
                val c = g.candidates(call).firstOrNull { (u, _) -> u.type == need } ?: g.candidates(call).firstOrNull { (u, _) -> g.canHandle(u, need) && u.type !in listOf("SEK", "MEK") }
                if (c != null) g.dispatch(call, c.first)
            }
        }
    }

    private fun career(decide: Boolean, days: Int = 10): Pair<GameController, List<String>> {
        val states = asset("states.geojson")!!
        val regions = GeoData.parseStates(states.readText())
        val hamburg = regions.first { it.code == "DE-HH" }
        val map = MapData.parseBinary("DE-HH", asset("geo/roads-DE-HH.bin")!!.readBytes())
        NativeRouting.install(map)
        val g = GameController(GameState(region = "DE-HH", mode = "CAREER"), regions)
        g.placeProvider = { r -> if (r.code == "DE-HH") map.places.filter { hamburg.contains(GeoPoint(it.lon, it.lat)) } else r.cities.map { MapPlace(it.name, "city", 80000, it.lon, it.lat) } }
        g.state.randomSeed = 1102
        g.seedUnitsIfNeeded()
        val log = mutableListOf<String>()
        repeat(days) { day ->
            // Planning phase: maintain worn vehicles, train one unit when affordable.
            g.state.units.filter { it.region == "DE-HH" && it.vehicleCondition < 70 && it.incidentId.isBlank() }.forEach { g.maintain(it) }
            if (g.state.budget > 80000)
                g.state.units.firstOrNull { it.type == "STREIFE" && it.trainingLeft == 0 && it.incidentId.isBlank() && "Deeskalation" !in it.officer.qualifications }?.let { g.train(it, "Deeskalation") }
            g.setShift(if (day % 2 == 0) 6 else 14, 8)
            g.startShift()
            while (g.state.phase == "RUNNING") {
                play(g, decide)
                g.advance(1)
            }
            val r = g.state.reports.first()
            log.add("Tag ${r.day}: Notrufe ${r.served}, gelöst ${r.solved}, gescheitert ${r.failed}, Budget ${g.state.budget}, Ruf ${g.state.reputation}, Stufe ${g.state.careerLevel}, Länder ${g.state.unlockedRegions.sorted()}")
            g.nextDay()
        }
        return g to log
    }

    @Test
    fun tenCareerDaysAreSustainableButNotAMoneyRain() {
        if (asset("geo/roads-DE-HH.bin") == null) return
        val (g, log) = career(decide = true)
        log.forEach(::println)
        assertEquals(11, g.state.day)
        assertEquals(10, g.state.reports.size)
        assertEquals(10, g.state.completedShifts)
        assertTrue("budget stays positive", g.state.budget > 0)
        assertTrue("no money rain: ${g.state.budget}", g.state.budget < 240000 + 10 * 30000)
        assertTrue("career progresses", g.state.careerLevel >= 2)
        assertTrue("expansion unlocked", g.state.unlockedRegions.size > 1)
        assertTrue("training completed", g.state.reports.any { it.trainingDone.isNotEmpty() })
    }

    @Test
    fun engagedPlayBeatsPassivePlay() {
        if (asset("geo/roads-DE-HH.bin") == null) return
        val (active, _) = career(decide = true, days = 4)
        NativeRouting.clear()
        val (passive, _) = career(decide = false, days = 4)
        val a = active.state.reports.sumOf { it.solved }.toDouble() / active.state.reports.sumOf { it.served }
        val p = passive.state.reports.sumOf { it.solved }.toDouble() / passive.state.reports.sumOf { it.served }
        println("aktive Quote $a, passive Quote $p, Ruf ${active.state.reputation} vs ${passive.state.reputation}")
        assertTrue(active.state.reputation >= passive.state.reputation)
    }
}
