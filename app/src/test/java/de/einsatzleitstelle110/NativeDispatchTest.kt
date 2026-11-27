package de.einsatzleitstelle110

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** M1: routed statewide dispatch, multi-dispatch, withdrawal, re-dispatch and a full Hamburg shift. */
class NativeDispatchTest {
    @After
    fun cleanup() {
        NativeRouting.clear()
        MapData.clear()
    }

    private fun square() =
        GeoRegion(
            "DE-TEST",
            "Testland",
            listOf(listOf(GeoPoint(9.0, 53.0), GeoPoint(11.0, 53.0), GeoPoint(11.0, 54.0), GeoPoint(9.0, 54.0), GeoPoint(9.0, 53.0))),
            listOf(GeoCity("West", 9.5, 53.5), GeoCity("Ost", 10.5, 53.5), GeoCity("Nord", 10.0, 53.8)),
        )

    /** A ladder-shaped network: two long east-west roads joined by short rungs. */
    private fun ladder(): StateMap {
        val roads = mutableListOf<RoadLine>()
        for (lat in listOf(53.4, 53.6)) {
            val xs = (0..10).map { 9.0 + it * .2 }
            for (i in 0 until xs.size - 1)
                roads.add(RoadLine(if (lat == 53.4) 0 else 1, "L", doubleArrayOf(xs[i], lat, (xs[i] + xs[i + 1]) / 2, lat + .001, xs[i + 1], lat)))
        }
        for (i in listOf(0, 5, 10)) {
            val x = 9.0 + i * .2
            roads.add(RoadLine(2, "R", doubleArrayOf(x, 53.4, x, 53.5, x, 53.6)))
        }
        return StateMap("DE-TEST", roads, emptyList(), "OSM")
    }

    private fun controller(): GameController {
        val g = GameController(GameState(region = "DE-TEST", mode = "SANDBOX"), listOf(square()))
        g.seedUnitsIfNeeded()
        g.state.autoCalls = false
        return g
    }

    @Test
    fun graphRouteFollowsRoadsAndFieldMatchesRoute() {
        NativeRouting.install(ladder())
        val a = GeoPoint(9.01, 53.41)
        val b = GeoPoint(10.95, 53.59)
        val r = NativeRouting.route(a, b, 62.0, "DE-TEST")
        assertEquals("OSM_MAIN_ROADS_APPROXIMATION", r.basis)
        assertTrue("route uses road vertices", r.points.size > 10)
        // The road detour is longer than the straight line but plausible.
        val direct = GeoData.km(a, b)
        assertTrue(r.distanceKm > direct && r.distanceKm < direct * 1.8)
        val u = UnitState("u", "Test 1", "STREIFE", "DE-TEST", a.lat, a.lon)
        val eta = NativeRouting.eta(u, b)
        assertTrue("field ETA $eta vs route ${r.durationMinutes}", kotlin.math.abs(eta - r.durationMinutes) <= 2)
        // Twice the speed, half the time.
        val fast = NativeRouting.route(a, b, 124.0, "DE-TEST")
        assertTrue(kotlin.math.abs(r.durationMinutes - 2 * fast.durationMinutes) <= 1)
        // Helicopters fly direct.
        val heli = UnitState("h", "Heli", "HELI", "DE-TEST", a.lat, a.lon, vehicleSpeed = 180.0)
        val air = NativeRouting.routeFor(heli, b)
        assertEquals("AIR_DIRECT", air.basis)
        assertEquals(2, air.points.size)
    }

    @Test
    fun withoutRoadDataTheRouteIsHonestlyLabelled() {
        val r = NativeRouting.route(GeoPoint(9.1, 53.1), GeoPoint(10.9, 53.9), 62.0, "DE-TEST")
        assertEquals("DISTANCE_APPROXIMATION", r.basis)
        assertEquals(2, r.points.size)
    }

    @Test
    fun multiDispatchRecallAndRedirect() {
        NativeRouting.install(ladder())
        val g = controller()
        g.startShift()
        val big = g.newIncident(IncidentTemplate("Großlage", 1, "STREIFE", 30, "Test", requiredTypes = listOf("STREIFE", "STREIFE")), GeoPoint(10.0, 53.5))
        val small = g.newIncident(IncidentTemplate("Kleinlage", 3, "STREIFE", 10, "Test"), GeoPoint(9.6, 53.45))
        val patrols = g.state.units.filter { it.type == "STREIFE" }.take(3)
        assertEquals(2, g.dispatchAll(big, patrols.take(2)))
        assertTrue(g.remainingTypes(big).isEmpty())
        assertTrue(patrols.take(2).all { it.status == "DRIVING" && it.routeBasis != "" })
        // Candidates exclude units already on this call.
        assertTrue(g.candidates(big).none { (u, _) -> u.id in big.assignedUnits })
        g.advance(6)
        // Withdraw one: requirement re-opens, unit drives home and stays alarmable.
        val first = patrols[0]
        assertTrue(g.recall(first))
        assertFalse(first.id in big.assignedUnits)
        assertEquals(listOf("STREIFE"), g.remainingTypes(big))
        assertTrue(first.status in listOf("RETURNING", "FREE"))
        assertTrue(g.dispatchable(first))
        // Re-dispatch the returning unit directly to the small call (no readiness delay).
        assertTrue(g.dispatch(small, first))
        assertEquals(0, first.readinessLeft)
        // Redirect the second unit of the big call to the small one.
        val second = patrols[1]
        assertTrue(g.redirect(second, small))
        assertEquals(small.id, second.incidentId)
        assertEquals("OPEN", big.status)
        assertTrue(big.assignedUnits.isEmpty())
    }

    @Test
    fun reinforcementShortensWorkOnScene() {
        val g = controller()
        g.startShift()
        val c = g.newIncident(IncidentTemplate("Lange Lage", 2, "STREIFE", 80, "Test", deadline = 400), GeoPoint(10.0, 53.5))
        val units = g.state.units.filter { it.type == "STREIFE" }.sortedBy { g.eta(it, c) }
        assertTrue(g.dispatch(c, units[0]))
        var guard = 0
        while (c.status != "WORKING" && guard++ < 400) g.advance(1)
        assertEquals("WORKING", c.status)
        val before = units[0].workLeft
        assertTrue("reinforcement to a working call", g.dispatch(c, units[1]))
        val t0 = g.state.elapsed
        guard = 0
        while (units[1].status != "WORKING" && guard++ < 400) g.advance(1)
        assertEquals("WORKING", c.status)
        val withoutHelp = before - (g.state.elapsed - t0)
        assertTrue("${units[0].workLeft} < $withoutHelp", units[0].workLeft < withoutHelp)
        assertEquals(units[0].workLeft, units[1].workLeft)
    }

    @Test
    fun callVolumeFollowsTimeOfDay() {
        fun calls(startHour: Int): Int {
            val g = controller()
            g.state.autoCalls = true
            g.state.randomSeed = 4242
            g.setShift(startHour, 8)
            g.startShift()
            g.advance(480)
            return g.state.reports.first().served
        }
        val night = calls(0)
        val evening = calls(14)
        assertTrue("8h shifts produce a playable number of calls: $night / $evening", night in 6..30 && evening in 8..40)
        assertTrue("late afternoon busier than night ($evening vs $night)", evening > night)
    }

    @Test
    fun openerIsFairAndVaried() {
        val titles = HashSet<String>()
        for (seed in 1L..12L) {
            val g = controller()
            g.state.randomSeed = seed * 7919
            g.setShift((seed % 24).toInt(), 8)
            g.startShift()
            val first = g.state.incidents.first()
            assertTrue(first.priority >= 2)
            assertEquals(listOf("STREIFE"), first.requiredTypes)
            titles.add(first.title)
        }
        assertTrue("different opening calls: $titles", titles.size >= 3)
    }

    @Test
    fun outcomeReasonsSurviveSaveAndLoad() {
        val g = controller()
        g.startShift()
        val c = g.newIncident(IncidentTemplate("Prüffall", 2, "STREIFE", 5, "Test", deadline = 300), GeoPoint(10.0, 53.5))
        val u = g.state.units.filter { it.type == "STREIFE" }.minByOrNull { g.eta(it, c) }!!
        assertTrue(g.dispatch(c, u))
        var guard = 0
        while (c.status !in listOf("SOLVED", "FAILED") && guard++ < 600) g.advance(1)
        assertTrue(c.outcome.contains("Chance"))
        assertTrue(c.firstArrival >= 0 && c.closedAt > 0)
        val restored = GameStore.fromJson(GameStore.toJson(g.state)).incidents.first { it.id == c.id }
        assertEquals(c.outcome, restored.outcome)
        assertEquals(c.firstArrival, restored.firstArrival)
        assertEquals(c.closedAt, restored.closedAt)
    }

    @Test
    fun failedRedirectPreservesOriginVehicleBudgetAndLogs() {
        val failures: List<Pair<String, (GameController, UnitState, IncidentState) -> Unit>> = listOf(
            "Schicht beendet" to { g, _, _ -> g.state.elapsed = 30; g.finishShift() },
            "Zieleinsatz beendet" to { _, _, target -> target.status = "SOLVED" },
            "Ungeeignete Kräfte" to { _, _, target -> target.requiredTypes = mutableListOf("RETTUNG") },
            "Fremde Region" to { _, _, target -> target.region = "DE-HH" },
            "Außer Dienst" to { _, u, _ -> u.onDuty = false },
            "Fahrzeug defekt" to { _, u, _ -> u.vehicleCondition = 10 },
            "Besatzung erschöpft" to { _, u, _ -> u.officers.first().energy = 10 },
            "Budget fehlt" to { g, _, _ -> g.state.budget = 0 },
            "Route ungültig" to { _, u, _ -> u.vehicleSpeed = 0.0 },
            "Ausbildung fehlt" to { g, u, _ ->
                g.state.trainingRequired = true
                u.officers.forEach { it.qualifications.clear() }
            },
        )
        for ((label, setup) in failures) {
            val g = controller()
            g.startShift()
            val unit = g.state.units.first { it.type == "SEK" }
            val old = g.newIncident(IncidentTemplate("Einsatz A", 2, "SEK", 30, ""), GeoPoint(10.0, 53.5))
            val target = g.newIncident(IncidentTemplate("Einsatz B", 2, "SEK", 15, ""), GeoPoint(10.2, 53.6))
            assertTrue(label, g.dispatch(old, unit))
            setup(g, unit, target)
            val before = GameStore.toJson(g.state).toString()
            assertFalse(label, g.redirect(unit, target))
            assertEquals(label, before, GameStore.toJson(g.state).toString())
        }
    }

    /** The compact APK format carries exactly the data of the open JSON copy. */
    @Test
    fun binaryMapAssetsMatchTheOpenJsonSource() {
        for (code in listOf("DE-HH", "DE-SL")) {
            val bin = NativeTestAssets.bundled("geo/roads-$code.bin")
            val json = NativeTestAssets.osm("roads-$code.json")
            val a = MapData.parseBinary(code, bin.readBytes())
            val b = MapData.parseState(code, json.readText())
            assertEquals(b.roads.size, a.roads.size)
            assertEquals(b.places.size, a.places.size)
            assertEquals(b.places.first().name, a.places.first().name)
            for (k in listOf(0, b.roads.size / 2, b.roads.size - 1)) {
                assertEquals(b.roads[k].cls, a.roads[k].cls)
                assertArrayEquals(b.roads[k].points, a.roads[k].points, 1e-6)
            }
        }
        try {
            MapData.parseBinary("X", ByteArray(20))
            fail("garbage accepted")
        } catch (e: IllegalArgumentException) {}
    }

    /** Statewide routing on the bundled OSM extracts: Hannover–Oldenburg, München–Nürnberg. */
    @Test
    fun statewideRoutesUseTheRealRoadNetwork() {
        for ((code, a, b, maxKm) in listOf(
            listOf("DE-NI", GeoPoint(9.732, 52.3759), GeoPoint(8.2146, 53.1435), 230.0),
            listOf("DE-BY", GeoPoint(11.5761, 48.1372), GeoPoint(11.0767, 49.4521), 240.0),
        )) {
            val file = NativeTestAssets.bundled("geo/roads-$code.bin")
            val t0 = System.nanoTime()
            NativeRouting.install(MapData.parseBinary(code as String, file.readBytes()))
            val built = (System.nanoTime() - t0) / 1e6
            val t1 = System.nanoTime()
            val r = NativeRouting.route(a as GeoPoint, b as GeoPoint, 62.0, code)
            val ms = (System.nanoTime() - t1) / 1e6
            println("$code route ${"%.0f".format(r.distanceKm)} km, ${r.durationMinutes} min, ${r.points.size} pts, basis ${r.basis}; load+graph ${"%.0f".format(built)} ms, A* ${"%.0f".format(ms)} ms, nodes ${NativeRouting.loadedGraph(code)!!.nodeCount}")
            assertEquals("OSM_MAIN_ROADS_APPROXIMATION", r.basis)
            assertTrue(r.distanceKm > GeoData.km(a, b) && r.distanceKm < maxKm as Double)
            assertTrue(r.points.size > 50)
            NativeRouting.clear()
        }
    }

    /**
     * A complete 8-hour Hamburg day on the real state border and bundled road network, played by a
     * simple automatic dispatcher. Proves the vertical slice end to end without a device.
     */
    @Test
    fun fullHamburgEightHourShiftOnRealRoads() {
        val states = NativeTestAssets.bundled("states.geojson")
        val hamburg = GeoData.parseStates(states.readText()).first { it.code == "DE-HH" }
        val map = MapData.parseBinary("DE-HH", NativeTestAssets.bundled("geo/roads-DE-HH.bin").readBytes())
        NativeRouting.install(map)
        val g = GameController(GameState(region = "DE-HH", mode = "CAREER"), listOf(hamburg))
        val places = map.places.filter { hamburg.contains(GeoPoint(it.lon, it.lat)) }
        if (places.size >= 3) g.placeProvider = { places }
        g.seedUnitsIfNeeded()
        g.state.randomSeed = 20261102
        g.setShift(14, 8)
        g.startShift()
        var routed = 0
        var dispatched = 0
        while (g.state.phase == "RUNNING") {
            for (call in g.state.incidents.filter { it.region == "DE-HH" && it.status in listOf("OPEN", "DRIVING") }.sortedBy { it.priority }) {
                for (need in g.remainingTypes(call)) {
                    val pick = g.candidates(call).firstOrNull { (u, _) -> g.canHandle(u, need) } ?: continue
                    if (g.dispatch(call, pick.first)) {
                        dispatched++
                        if (pick.first.routeBasis != "DISTANCE_APPROXIMATION") routed++
                    }
                }
            }
            g.advance(1)
        }
        val report = g.state.reports.first()
        for (c in g.state.incidents.filter { it.status == "FAILED" })
            println("  failed ${c.title} P${c.priority} need=${c.requiredTypes} age=${c.age}/${c.deadline} assigned=${c.assignedUnits.size}")
        println("Hamburg 8h: calls=${report.served} solved=${report.solved} failed=${report.failed} handed=${report.handed} dispatches=$dispatched routed=$routed graph=${map.source}")
        assertEquals("DEBRIEF", g.state.phase)
        assertTrue(report.served >= 8)
        assertTrue(report.solved >= report.served / 2)
        assertTrue("most journeys use the road network ($routed/$dispatched)", routed >= dispatched * 3 / 4)
        // Every call happened inside Hamburg.
        assertTrue(g.state.incidents.all { hamburg.contains(GeoPoint(it.lon, it.lat)) })
    }
}
