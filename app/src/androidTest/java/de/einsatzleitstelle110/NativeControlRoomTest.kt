package de.einsatzleitstelle110

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeControlRoomTest {
    private val instrument
        get() = InstrumentationRegistry.getInstrumentation()

    private fun game(activity: MainActivity): GameController =
        MainActivity::class
            .java
            .getDeclaredField("game")
            .apply { isAccessible = true }
            .get(activity) as GameController

    private fun invoke(activity: MainActivity, name: String, vararg values: Any) {
        val types =
            values
                .map { if (it is Int) Int::class.javaPrimitiveType!! else it.javaClass }
                .toTypedArray()
        MainActivity::class
            .java
            .getDeclaredMethod(name, *types)
            .apply { isAccessible = true }
            .invoke(activity, *values)
    }

    private fun reset() {
        MainActivity.phoneDeskDefault = false
        GameStore.awaitSaves()
        instrument.targetContext.deleteFile("einsatzleitstelle-native-v1.json")
        instrument.targetContext.deleteFile("einsatzleitstelle-native-v1.json.backup")
    }

    private fun dismiss(a: MainActivity) {
        (MainActivity::class.java.getDeclaredField("sheet").apply { isAccessible = true }.get(a)
                as? android.app.Dialog)
            ?.dismiss()
    }

    private fun screenshot(name: String) {
        instrument.waitForIdleSync()
        Thread.sleep(300)
        val bmp = instrument.uiAutomation.takeScreenshot()
        assertNotNull(bmp)
        FileOutputStream(File(instrument.targetContext.getExternalFilesDir(null), name)).use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bmp.recycle()
    }

    private fun tapText(view: View, value: String): Boolean {
        if (view is android.widget.TextView && view.text.toString() == value) {
            view.performClick()
            return true
        }
        if (view is ViewGroup)
            for (i in 0 until view.childCount) if (tapText(view.getChildAt(i), value)) return true
        return false
    }

    private fun tapPrefix(view: View, prefix: String): Boolean {
        if (view is android.widget.TextView && view.text.toString().startsWith(prefix) && view.isEnabled) {
            view.performClick()
            return true
        }
        if (view is ViewGroup)
            for (i in 0 until view.childCount) if (tapPrefix(view.getChildAt(i), prefix)) return true
        return false
    }

    private fun mapView(a: MainActivity) =
        MainActivity::class.java.getDeclaredField("map").apply { isAccessible = true }.get(a) as TacticalMapView

    /** The topographic base map renders on a background thread; wait for it before evidence shots. */
    private fun waitForBaseMap(scenario: ActivityScenario<MainActivity>) {
        var ready = false
        val until = System.currentTimeMillis() + 15000
        while (!ready && System.currentTimeMillis() < until) {
            scenario.onActivity { a ->
                ready = mapView(a).baseMapReady()
                if (game(a).state.phase == "RUNNING") {
                    val dialog = MainActivity::class.java.getDeclaredField("sheet").apply { isAccessible = true }.get(a) as? android.app.Dialog
                    val name = MainActivity::class.java.getDeclaredField("sheetName").apply { isAccessible = true }.get(a) as String
                    assertFalse("Startup welcome covers the running map", dialog?.isShowing == true && name == "Einsatzleitstelle 110")
                }
            }
            if (!ready) Thread.sleep(250)
        }
        assertTrue("base map rendered", ready)
        Thread.sleep(400)
    }

    private fun autoDispatch(g: GameController) {
        for (call in g.state.incidents.filter { it.region == g.state.region && it.status in listOf("OPEN", "DRIVING") }.sortedBy { it.priority })
            for (need in g.remainingTypes(call)) {
                val pick = g.candidates(call).firstOrNull { (u, _) -> g.canHandle(u, need) } ?: continue
                g.dispatch(call, pick.first)
            }
    }

    private fun assertNative(view: View) {
        assertFalse(view.javaClass.name.startsWith("android.webkit."))
        if (view is ViewGroup) for (i in 0 until view.childCount) assertNative(view.getChildAt(i))
    }

    /** Measured on the emulator (not a physical device): written next to the screenshots. */
    private fun perf(line: String) {
        android.util.Log.i("NativePerf", line)
        File(instrument.targetContext.getExternalFilesDir(null), "perf.txt").appendText(line + "\n")
    }

    private fun overview(
        region: String,
        names: List<String>,
        points: List<GeoPoint>,
        file: String,
    ) {
        reset()
        var t0 = 0L
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                g.state.mode = "SANDBOX"
                g.state.unlimitedBudget = true
                g.state.autoCalls = false
                t0 = System.nanoTime()
                g.setRegion(region)
                val tg = System.nanoTime()
                val graph = NativeRouting.graph(region)
                perf("$region Straßengraph: ${(System.nanoTime() - tg) / 1_000_000} ms, ${graph?.nodeCount} Knoten")
                g.startShift()
                for (i in names.indices) g.newIncident(
                    IncidentTemplate(
                        names[i],
                        if (i == 0) 1 else 2,
                        "STREIFE",
                        20,
                        "Landesweite Einsatzlage",
                    ),
                    points[i],
                )
                g.state.paused = true
                invoke(a, "showTab", "MAP")
            }
            waitForBaseMap(scenario)
            perf("$region Region gewählt bis Grundkarte fertig: ${(System.nanoTime() - t0) / 1_000_000} ms")
            scenario.onActivity { a ->
                val g = game(a)
                g.state.paused = false
                val v = mapView(a)
                val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                v.draw(canvas)
                val n = 30
                val ts = System.nanoTime()
                repeat(n) { v.draw(canvas) }
                perf("$region Live-Overlay (Software-Canvas, ${g.state.units.count { it.region == region }} Einheiten, ${g.state.incidents.size} Lagen): Ø ${(System.nanoTime() - ts) / n / 100_000 / 10.0} ms je Bild")
                bmp.recycle()
                g.state.paused = true
            }
            screenshot(file)
        }
    }

    /**
     * M1 vertical slice on a device: Hamburg career shift, call opened from the live map,
     * multi-unit dispatch from the map panel, vehicles driving on the routed roads, withdrawal,
     * a full 8-hour day and the shift report.
     */
    @Test
    fun phoneCallFromRingingToIncident() {
        reset()
        MainActivity.phoneDeskDefault = true
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForBaseMap(scenario)
            lateinit var call: PhoneCall
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                g.state.autoCalls = false
                g.setShift(14, 8)
                g.startShift()
                g.state.phones.clear()
                g.state.paused = true
                call = PhoneDesk.ring(g, CallScripts.find("unfall_verletzt"))!!
            }
            Thread.sleep(800)
            screenshot("native-phone-ringing.png")
            scenario.onActivity { a -> invoke(a, "answerNext") }
            instrument.waitForIdleSync()
            Thread.sleep(800)
            scenario.onActivity { a ->
                val g = game(a)
                PhoneDesk.ask(g, call, "wo")
                PhoneDesk.ask(g, call, "eingeklemmt")
                val screen = MainActivity::class.java.getDeclaredField("phoneScreen").apply { isAccessible = true }.get(a) as PhoneScreen
                assertTrue(screen.isShowing)
                PhoneScreen::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(screen)
            }
            Thread.sleep(1600)
            screenshot("native-phone-call.png")
            scenario.onActivity { a ->
                val g = game(a)
                val screen = MainActivity::class.java.getDeclaredField("phoneScreen").apply { isAccessible = true }.get(a) as PhoneScreen
                val finish = PhoneScreen::class.java.getDeclaredMethod("finish", PhoneCall::class.java, CallCode::class.java).apply { isAccessible = true }
                finish.invoke(screen, call, PhoneDesk.codes(g, call).first { it.correct })
                assertTrue(g.state.incidents.first().requiredTypes.contains("FEUERWEHR"))
            }
            Thread.sleep(600)
            screenshot("native-phone-result.png")
            scenario.onActivity { a ->
                (MainActivity::class.java.getDeclaredField("phoneScreen").apply { isAccessible = true }.get(a) as PhoneScreen).close()
            }
        }
        MainActivity.phoneDeskDefault = false
    }

    @Test
    fun incidentDeskProposalAndConfirmedDispatchUseRealStreets() {
        reset()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            var callId = ""
            var chosen = emptySet<String>()
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                g.state.autoCalls = false
                assertTrue(g.setScope("WANDSBEK"))
                g.startShift(); g.state.paused = true
                g.state.incidents.clear()
                val graph = NativeRouting.graph("DE-HH")!!
                val target = graph.namedStreetPoint(GeoPoint(10.10,53.60)) { g.inScope(it) }!!.first
                val call = g.newIncident(IncidentTemplate("Lagezentrum: Nachbarschaftshilfe",2,"STREIFE",8,
                    "Zwei fiktive Streifen werden für Betreuung und Aufnahme benötigt.",requiredTypes = listOf("STREIFE","STREIFE")),target)
                callId = call.id
                invoke(a,"showTab","CALLS")
                assertTrue(tapText(a.window.decorView,"Alle aktiven"))
                assertTrue(call.assignedUnits.isEmpty())
            }
            screenshot("native-incident-desk.png")
            scenario.onActivity { a ->
                val g = game(a)
                assertTrue(tapText(a.window.decorView,"Einsatzakte öffnen"))
                val call = g.state.incidents.first { it.id == callId }
                assertTrue(IncidentDesk.attention(g,call).reason.startsWith("Entscheidung"))
                assertTrue("Resolve call-taking through the real UI",tapPrefix(a.window.decorView,"Sofort alarmieren"))
                assertTrue(call.pending.isBlank())
                val started = System.nanoTime()
                val advice = DispatchAdvisor.plan(g,call,false)
                perf("Lagezentrum Wandsbek: Vorschlag für ${call.requiredTypes.size} Rollen / ${g.state.units.size} Fahrzeuge in ${(System.nanoTime() - started) / 1_000_000.0} ms (warmer Routingcache)")
                assertEquals(2,advice.picks.size)
                chosen = advice.picks.map { it.unitId }.toSet()
                val budget = g.state.budget
                assertTrue(tapPrefix(a.window.decorView,"Vorschlag auswählen ·"))
                assertTrue("A proposal must not dispatch automatically",call.assignedUnits.isEmpty())
                assertEquals(budget,g.state.budget)
            }
            waitForBaseMap(scenario)
            Thread.sleep(3500) // Let the real decision acknowledgement toast finish before evidence.
            screenshot("native-dispatch-advice.png")
            scenario.onActivity { a ->
                val g = game(a)
                val call = g.state.incidents.first { it.id == callId }
                assertTrue(tapPrefix(a.window.decorView,"Entsenden (2)"))
                assertEquals(chosen,call.assignedUnits.toSet())
                assertTrue(g.remainingTypes(call).isEmpty())
                for (id in chosen) {
                    val u = g.state.units.first { it.id == id }
                    assertEquals(3,g.fms(u))
                    assertFalse(u.specialSignal)
                    assertNotEquals("DISTANCE_APPROXIMATION",u.routeBasis)
                    assertTrue(u.routePoints.size > 2)
                }
                invoke(a,"showTab","CALLS")
                assertTrue(IncidentDesk.attention(g,call).needsAction.not())
                assertTrue(tapText(a.window.decorView,"Alle aktiven"))
            }
            scenario.onActivity { a ->
                val g = game(a)
                val before = g.state.elapsed
                g.state.paused = false
                try { g.advance(1) } finally { g.state.paused = true }
                assertEquals("The live-filter check must advance a real simulation minute",before + 1,g.state.elapsed)
                invoke(a,"updateLive")
                val matches = arrayListOf<View>()
                a.window.decorView.findViewsWithText(matches,g.state.incidents.first { it.id == callId }.title,View.FIND_VIEWS_WITH_TEXT)
                assertTrue("Live updates keep the active filter and its dispatched call visible",matches.isNotEmpty())
            }
            screenshot("native-incident-desk-dispatched.png")
        }
    }

    @Test
    fun weatherCallerFollowUpSaveSlotsAndLandscapeDispatch() {
        reset()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            var snapshot: GameState? = null
            var slotsScreen: SaveSlotsScreen? = null
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                g.state.autoCalls = false
                assertTrue(g.setScope("WANDSBEK"))
                assertTrue(g.setWeather("RAIN"))
                g.startShift(); g.state.paused = true
                val target = NativeRouting.graph("DE-HH")!!.namedStreetPoint(GeoPoint(10.10, 53.60)) { g.inScope(it) }!!.first
                val template = IncidentTemplate("Rückruf bei Nachbarschaftslage", 3, "STREIFE", 8, "Fiktiver Anrufer ist sich bei der Adresse unsicher.")
                val call = g.reportCaller(template, target, disconnected = true, addressUncertain = true)
                assertSame(call, g.reportCaller(template, target, disconnected = true))
                assertEquals(2, call.duplicateCallers)
                invoke(a, "openCall", call)
            }
            waitForBaseMap(scenario)
            screenshot("native-caller-callback.png")
            scenario.onActivity { a ->
                assertTrue(tapPrefix(a.window.decorView, "Rückruf ·"))
                val g = game(a)
                val call = g.state.incidents.first { it.title == "Rückruf bei Nachbarschaftslage" }
                assertTrue(call.callbackDone); assertFalse(call.addressUncertain)
                snapshot = GameSnapshots.capture(g.state)
                invoke(a, "save")
            }
            SaveSlots.save(instrument.targetContext, 1, "Wandsbek Regen", snapshot!!)
            assertEquals("RAIN", SaveSlots.load(instrument.targetContext, 1).weather)
            assertEquals(2, SaveSlots.load(instrument.targetContext, 1).incidents.first { it.title == "Rückruf bei Nachbarschaftslage" }.duplicateCallers)
            scenario.onActivity { a -> slotsScreen = SaveSlotsScreen(a, game(a)) {}.also { it.show() } }
            instrument.waitForIdleSync()
            Thread.sleep(800)
            screenshot("native-save-slots.png")
            scenario.onActivity { a ->
                val dialog = SaveSlotsScreen::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(slotsScreen) as android.app.Dialog
                dialog.dismiss()
                a.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            var landscape = false
            val until = System.currentTimeMillis() + 15000
            while (!landscape && System.currentTimeMillis() < until) {
                scenario.onActivity { a -> landscape = a.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                if (!landscape) Thread.sleep(200)
            }
            assertTrue(landscape)
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                invoke(a, "showTab", "MAP")
                invoke(a, "openCall", g.state.incidents.first { it.title == "Rückruf bei Nachbarschaftslage" })
            }
            waitForBaseMap(scenario)
            scenario.onActivity { a -> assertTrue(mapView(a).obstructedRight > 0f) }
            screenshot("native-landscape-dispatch.png")
            scenario.onActivity { a -> a.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    @Test
    fun policeChiefStationFleetStaffDispatchAndDurableReload() {
        reset()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                assertTrue(g.setScope("WANDSBEK"))
                g.state.autoCalls = false
                g.state.fatigueEnabled = false
                g.state.units.clear()
                invoke(a, "showChief")
            }
            instrument.waitForIdleSync()
            val create = instrument.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Dienststelle gründen · 30.000 €").first()
            assertTrue(create.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            instrument.waitForIdleSync()
            val name = instrument.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Revier Wandsbek").first()
            assertTrue(name.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Nordgarten · fiktiv")
            }))
            val place = instrument.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Standort auf Karte").first()
            assertTrue(place.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            var pressX = 0f; var pressY = 0f
            scenario.onActivity { a ->
                val v = mapView(a)
                v.focus(10.10, 53.60)
                val location = IntArray(2); v.getLocationOnScreen(location)
                val density = a.resources.displayMetrics.density
                pressX = location[0] + v.width / 2f
                pressY = location[1] + (62 * density + v.height - 168 * density) / 2
            }
            instrument.waitForIdleSync()
            val downAt = android.os.SystemClock.uptimeMillis()
            instrument.sendPointerSync(android.view.MotionEvent.obtain(downAt, downAt, android.view.MotionEvent.ACTION_DOWN, pressX, pressY, 0))
            Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 200)
            instrument.sendPointerSync(android.view.MotionEvent.obtain(downAt, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, pressX, pressY, 0))
            instrument.waitForIdleSync()
            scenario.onActivity { a ->
                val g = game(a)
                assertEquals(1, g.state.chief.stations.size)
                val station = g.state.chief.stations.single()
                assertEquals("Nordgarten · fiktiv", station.name)
                assertTrue(g.inScope(GeoPoint(station.lon, station.lat)))
                val screen = MainActivity::class.java.getDeclaredField("chiefScreen").apply { isAccessible = true }.get(a) as PoliceChiefScreen
                screen.close()
                val cars = (1..2).map { number -> g.buyChiefVehicle("sedan", station.id, "Peter 31/$number")!! }
                repeat(4) { index ->
                    val applicant = g.chiefApplicants().first()
                    val employee = g.hireChiefOfficer(applicant.id)!!
                    assertTrue(g.assignChiefOfficer(employee.id, cars[index / 2].unitId))
                }
                assertEquals(4, g.state.units.sumOf { it.officers.size })
                invoke(a, "showChief")
            }
            screenshot("native-chief-station.png")
            scenario.onActivity { a ->
                val screen = MainActivity::class.java.getDeclaredField("chiefScreen").apply { isAccessible = true }.get(a) as PoliceChiefScreen
                val dialog = PoliceChiefScreen::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(screen) as android.app.Dialog
                assertTrue(tapText(dialog.window!!.decorView, "Fuhrpark"))
            }
            screenshot("native-chief-fleet.png")
            scenario.onActivity { a ->
                val screen = MainActivity::class.java.getDeclaredField("chiefScreen").apply { isAccessible = true }.get(a) as PoliceChiefScreen
                val dialog = PoliceChiefScreen::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(screen) as android.app.Dialog
                assertTrue(tapText(dialog.window!!.decorView, "Personal"))
            }
            screenshot("native-chief-personnel.png")
            scenario.onActivity { a ->
                val screen = MainActivity::class.java.getDeclaredField("chiefScreen").apply { isAccessible = true }.get(a) as PoliceChiefScreen
                screen.close()
                val g = game(a)
                g.startShift()
                val station = g.state.chief.stations.single()
                val call = g.newIncident(IncidentTemplate("Nachbarschaftshilfe", 3, "STREIFE", 2, "Fiktive kleine Hilfestellung"), GeoPoint(station.lon + .0001, station.lat + .0001))
                call.pending = ""; call.riskDelta = -.4
                assertTrue(g.dispatch(call, g.state.units.first(), false))
                g.state.randomSeed = 0
                g.advance(25)
                assertEquals("SOLVED", call.status)
                g.state.paused = true
                invoke(a, "showTab", "MAP")
                invoke(a, "save")
            }
            GameStore.awaitSaves()
            val disk = org.json.JSONObject(instrument.targetContext.openFileInput("einsatzleitstelle-native-v1.json").bufferedReader().use { it.readText() })
            val stored = GameStore.fromJson(disk)
            assertEquals(2, stored.chief.vehicles.size)
            assertEquals(4, stored.chief.employees.size)
            assertTrue(stored.incidents.any { it.title == "Nachbarschaftshilfe" && it.status == "SOLVED" })
            scenario.recreate()
            scenario.onActivity { a ->
                val g = game(a)
                assertEquals(1, g.state.chief.stations.size)
                assertEquals(2, g.state.chief.vehicles.size)
                assertEquals(4, g.state.units.sumOf { it.officers.size })
                invoke(a, "showChief")
            }
            screenshot("native-chief-reloaded.png")
        }
    }

    @Test
    fun patrolVehiclesSpecialSignalAndRadioStatusOnRealWandsbekRoads() {
        reset()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                g.state.mode = "SANDBOX"; g.state.unlimitedBudget = true
                g.state.autoCalls = false; g.state.fatigueEnabled = false
                assertTrue(g.setScope("WANDSBEK"))
                val graph = NativeRouting.graph("DE-HH")!!
                g.startShift()
                val units = g.state.units.filter { it.scope == "WANDSBEK" && it.type == "STREIFE" }.take(3)
                assertEquals(3, units.size)
                units.forEachIndexed { index, u ->
                    val point = graph.namedStreetPoint(GeoPoint(10.09 + index * .015, 53.60 + index * .015)) { g.inScope(it) }!!.first
                    u.lon = point.lon; u.lat = point.lat
                    assertTrue(g.lastError, g.setPatrolMode(u, "PATROL"))
                    assertEquals(1, g.fms(u)); assertFalse(u.specialSignal)
                }
                g.advance(2)
                assertTrue(units.all { g.inScope(GeoPoint(it.lon, it.lat)) })
                g.state.paused = true
                invoke(a, "showTab", "MAP")
            }
            waitForBaseMap(scenario)
            screenshot("native-wandsbek-patrol.png")
            scenario.onActivity { a ->
                val g = game(a)
                val units = g.state.units.filter { it.scope == "WANDSBEK" && it.type == "STREIFE" }.take(3)
                val target = NativeRouting.graph("DE-HH")!!.namedStreetPoint(GeoPoint(10.13, 53.63)) { g.inScope(it) }!!.first
                val p1 = g.newIncident(IncidentTemplate("Dringende Hilfe", 1, "STREIFE", 20, "Fiktive Lage"), target)
                val p3 = g.newIncident(IncidentTemplate("Ruhige Anfahrt", 3, "STREIFE", 20, "Fiktive Lage"), target)
                assertTrue(g.dispatch(p1, units[0], true)); assertTrue(g.dispatch(p3, units[1], false))
                assertTrue(units[0].specialSignal); assertFalse(units[1].specialSignal)
                assertEquals(3, g.fms(units[0]))
                assertTrue(g.state.logs.any { "an Leitstelle: Status 3" in it })
                mapView(a).focus((units[0].lon + units[1].lon) / 2, (units[0].lat + units[1].lat) / 2)
            }
            waitForBaseMap(scenario)
            screenshot("native-signal-comparison.png")
            scenario.onActivity { a ->
                val g = game(a)
                g.state.paused = false
                val travel = g.state.units.filter { it.status == "DRIVING" }.maxOfOrNull { it.travelLeft + it.readinessLeft } ?: 1
                g.advance(travel + 1)
                g.state.paused = true
                assertTrue(g.state.logs.any { "an Leitstelle: Status 4" in it })
                val c = android.widget.LinearLayout(a).apply { orientation = android.widget.LinearLayout.VERTICAL }
                c.addView(android.widget.TextView(a).apply { text = g.state.logs.take(24).joinToString("\n\n"); setTextColor(android.graphics.Color.WHITE); textSize = 13f })
                invoke(a, "showSheet", "Funkprotokoll", c)
            }
            screenshot("native-fms-radio.png")
        }
    }

    @Test
    fun wandsbekDetailStreetsMaskNamesAndOneWayDrive() {
        reset()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            var startNanos = 0L
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                g.state.mode = "SANDBOX"
                g.state.unlimitedBudget = true
                g.state.autoCalls = false
                assertTrue(g.setScope("WANDSBEK"))
                val mapData = MapData.state(a, "DE-HH")!!
                assertEquals(2, mapData.version)
                assertTrue(mapData.roads.count { it.cls >= 3 } > 10000)
                assertTrue(mapData.areas.any { it.kind == 0 })
                assertTrue(mapData.areas.any { it.kind == 1 })
                assertTrue(mapData.areas.any { it.kind == 2 })
                g.startShift()
                g.state.paused = true
                invoke(a, "showTab", "MAP")
                startNanos = System.nanoTime()
            }
            waitForBaseMap(scenario)
            perf("P1 Wandsbek Übersicht bis Grundkarte: ${(System.nanoTime() - startNanos) / 1_000_000} ms")
            screenshot("native-wandsbek-overview.png")
            scenario.onActivity { a ->
                val g = game(a)
                val mapData = MapData.state(a, "DE-HH")!!
                val road = mapData.roads.filter { r ->
                    r.size >= 3 && r.cls >= 3 && r.oneway != 0 && r.name.isNotBlank() &&
                        (r.lon(0) != r.lon(r.size - 1) || r.lat(0) != r.lat(r.size - 1)) &&
                        (0 until r.size).all { OperationsAreas.contains("DE-HH", "WANDSBEK", GeoPoint(r.lon(it), r.lat(it))) }
                }.sortedByDescending { r -> (0 until r.size - 1).sumOf { GeoData.km(GeoPoint(r.lon(it), r.lat(it)), GeoPoint(r.lon(it + 1), r.lat(it + 1))) } }.first { r ->
                    val from = if (r.oneway == 1) 0 else r.size - 1
                    val to = if (r.oneway == 1) r.size - 1 else 0
                    val route = NativeRouting.route(GeoPoint(r.lon(from), r.lat(from)), GeoPoint(r.lon(to), r.lat(to)), 62.0, "DE-HH")
                    route.durationMinutes >= 2 && route.points.contains(GeoPoint(r.lon(r.size / 2), r.lat(r.size / 2)))
                }
                val start = if (road.oneway == 1) 0 else road.size - 1
                val end = if (road.oneway == 1) road.size - 1 else 0
                val u = g.state.units.first { it.scope == "WANDSBEK" && it.type == "STREIFE" }
                u.lon = road.lon(start); u.lat = road.lat(start)
                val call = g.newIncident(IncidentTemplate("Wohnstraße prüfen", 1, "STREIFE", 20, "Fiktive Testlage"), GeoPoint(road.lon(end), road.lat(end)))
                assertTrue(call.location.contains("Höhe"))
                assertTrue(g.dispatch(call, u, false))
                assertEquals("OSM_DETAIL_ROADS_APPROXIMATION", u.routeBasis)
                assertTrue(u.routePoints.size >= 2)
                val v = mapView(a)
                v.focus((road.lon(start) + road.lon(end)) / 2, (road.lat(start) + road.lat(end)) / 2)
                v.zoomBy(2f)
                perf("P1 Einbahn-Wohnstraße: ${road.name}, Richtung ${road.oneway}, ${u.routePoints.size} Routenpunkte, ${u.travelTotal} min")
                startNanos = System.nanoTime()
            }
            waitForBaseMap(scenario)
            perf("P1 Straßenebene bis Grundkarte: ${(System.nanoTime() - startNanos) / 1_000_000} ms")
            scenario.onActivity { a ->
                val v = mapView(a)
                val bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val times = (0 until 60).map {
                    val before = System.nanoTime(); v.draw(canvas); (System.nanoTime() - before) / 1_000_000.0
                }.sorted()
                perf("P1 Detail-Overlay 60 Bilder: Ø ${times.average()} ms, p95 ${times[56]} ms, max ${times.last()} ms; Bitmap ${bitmap.byteCount} bytes")
                bitmap.recycle()
            }
            screenshot("native-wandsbek-street-names.png")
            scenario.onActivity { a ->
                val g = game(a)
                val u = g.state.units.first { it.status == "DRIVING" && it.scope == "WANDSBEK" }
                assertTrue(u.travelTotal >= 2)
                val old = GeoPoint(u.lon, u.lat)
                g.state.paused = false
                g.advance(u.readinessLeft + u.travelTotal / 2)
                g.state.paused = true
                assertTrue(old != GeoPoint(u.lon, u.lat))
                assertEquals("DRIVING", u.status)
                invoke(a, "updateLive")
                mapView(a).invalidate()
            }
            screenshot("native-wandsbek-oneway-drive.png")
        }
    }

    @Test
    fun hamburgLiveMapDispatchAndEightHourShift() {
        reset()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { a ->
                dismiss(a)
                assertNative(a.window.decorView)
                val g = game(a)
                assertEquals("DE-HH", g.state.region)
                g.state.mode = "CAREER"
                g.state.randomSeed = 20261102
                g.setShift(14, 8)
                g.startShift()
                g.state.paused = true
                invoke(a, "showTab", "MAP")
            }
            waitForBaseMap(scenario)
            screenshot("native-hamburg-live.png")
            scenario.onActivity { a ->
                // Tutorial banner on the live map (then skipped again for the rest of the test).
                val g = game(a)
                g.state.tutorialStep = 1
                invoke(a, "showTab", "MAP")
                assertTrue(g.tutorialHint().isNotBlank())
            }
            instrument.waitForIdleSync()
            Thread.sleep(400)
            screenshot("native-tutorial.png")
            scenario.onActivity { a ->
                game(a).state.tutorialStep = 0
                invoke(a, "showTab", "MAP")
            }
            var callId = ""
            scenario.onActivity { a ->
                val g = game(a)
                val call = g.state.incidents.first { it.status == "OPEN" }
                callId = call.id
                invoke(a, "openCall", call)
            }
            instrument.waitForIdleSync()
            Thread.sleep(500)
            screenshot("native-dispatch-panel.png")
            scenario.onActivity { a ->
                val g = game(a)
                val call = g.state.incidents.first { it.id == callId }
                assertTrue("dispatch button in the map panel", tapPrefix(a.window.decorView, "Entsenden ("))
                assertEquals("DRIVING", call.status)
                assertTrue(call.assignedUnits.isNotEmpty())
                val u = g.state.units.first { it.id == call.assignedUnits.first() }
                assertNotEquals("DISTANCE_APPROXIMATION", u.routeBasis)
                assertTrue(u.routePoints.size > 4)
                // A second call across the city: a patrol drives a long road route.
                val far =
                    g.newIncident(
                        IncidentTemplate("Verkehrsunfall", 2, "STREIFE", 22, "Zusammenstoß auf einer Hauptstraße", deadline = 90),
                        GeoPoint(9.985, 53.462),
                    )
                val driver =
                    g.candidates(far).filter { (c, eta) -> c.type == "STREIFE" && c.status == "FREE" && eta >= 10 }.minByOrNull { it.second }?.first
                        ?: g.candidates(far).first { (c, _) -> c.type == "STREIFE" }.first
                assertTrue(g.dispatch(far, driver))
                assertNotEquals("DISTANCE_APPROXIMATION", driver.routeBasis)
                g.state.paused = false
                g.advance(driver.readinessLeft + (driver.travelTotal / 2).coerceAtLeast(1))
                g.state.paused = true
                assertEquals("DRIVING", driver.status)
                invoke(a, "openCall", far)
                mapView(a).zoom = 1f
                mapView(a).focus(driver.lon, driver.lat)
            }
            waitForBaseMap(scenario)
            screenshot("native-vehicle-route.png")
            scenario.onActivity { a ->
                val g = game(a)
                // Withdraw and re-dispatch: the unit turns around and stays alarmable.
                val call = g.state.incidents.first { it.id == callId }
                val u = g.state.units.first { it.id == call.assignedUnits.first() }
                g.state.paused = false
                assertTrue(g.recall(u))
                assertTrue(g.dispatchable(u))
                assertTrue(g.dispatch(call, u))
                var guard = 0
                while (g.state.phase == "RUNNING" && guard++ < 600) {
                    autoDispatch(g)
                    g.advance(1)
                }
                assertEquals("DEBRIEF", g.state.phase)
                val r = g.state.reports.first()
                assertEquals(1, g.state.reports.size)
                assertTrue("calls ${r.served}", r.served >= 8)
                assertTrue("solved ${r.solved}/${r.served}", r.solved >= r.served / 2)
                invoke(a, "showDebrief")
            }
            screenshot("native-hamburg-8h-report.png")
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                g.nextDay()
                invoke(a, "showTab", "MANAGE")
            }
            instrument.waitForIdleSync()
            Thread.sleep(400)
            screenshot("native-career.png")
            scenario.onActivity { a ->
                val g = game(a)
                val t =
                    IncidentTemplate(
                        "Einbruch mit Folgeermittlung",
                        2,
                        "STREIFE",
                        15,
                        "Nachbar hört Glas splittern",
                        chain =
                            listOf(
                                ChainStage("s1", "Einbruch gemeldet", "STREIFE", 15, 2, "s2", "s3", listOf("STREIFE")),
                                ChainStage("s2", "Spurensicherung", "KRIPO", 25, 3, "s4", "", listOf("KRIPO"), "Kripo übernimmt", 180),
                                ChainStage("s3", "Fahndung", "STREIFE", 20, 2, "", "", listOf("STREIFE", "HUND"), "Täter flüchtig", 60),
                                ChainStage("s4", "Observation der Hehler", "MEK", 40, 2, "", "", listOf("MEK"), "Hinweis auf Hehler", 240),
                            ),
                    )
                assertEquals(emptyList<String>(), g.validateTemplate(t))
                assertTrue(g.addTemplate(t))
                ChainEditor(a, g, UiKit(a), t) {}.show()
            }
            instrument.waitForIdleSync()
            Thread.sleep(600)
            screenshot("native-editor.png")
        }
    }

    @Test
    fun niedersachsenWholeStateHasThreeSimultaneousCities() {
        overview(
            "DE-NI",
            listOf("Hannover", "Oldenburg", "Lüneburg"),
            listOf(GeoPoint(9.732, 52.3759), GeoPoint(8.2146, 53.1435), GeoPoint(10.407, 53.2487)),
            "native-niedersachsen.png",
        )
    }

    @Test
    fun bayernWholeStateShowsMunichAndNuremberg() {
        overview(
            "DE-BY",
            listOf("München", "Nürnberg"),
            listOf(GeoPoint(11.5761, 48.1372), GeoPoint(11.0767, 49.4521)),
            "native-bayern.png",
        )
    }

    @Test
    fun nativeJurisdictionInventoryAndFullDayWithRealImages() {
        reset()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { a ->
                (MainActivity::class
                        .java
                        .getDeclaredField("sheet")
                        .apply { isAccessible = true }
                        .get(a) as? android.app.Dialog)
                    ?.dismiss()
                assertNative(a.window.decorView)
                val permissions =
                    a.packageManager
                        .getPackageInfo(
                            a.packageName,
                            android.content.pm.PackageManager.GET_PERMISSIONS,
                        )
                        .requestedPermissions
                        .orEmpty()
                assertFalse(permissions.contains(android.Manifest.permission.INTERNET))
                val g = game(a)
                g.state.mode = "SANDBOX"
                g.state.unlimitedBudget = true
                g.state.autoCalls = false
            }
            // Region changes are separate user actions; let Android process window focus and
            // frames.
            for (code in GeoData.all.map { it.code }) {
                scenario.onActivity { a ->
                    val g = game(a)
                    val region = g.regions.first { it.code == code }
                    android.util.Log.i("NativeCheck", "region $code")
                    g.setRegion(region.code)
                    assertTrue(g.state.units.count { it.region == region.code } >= 32)
                    val view =
                        MainActivity::class
                            .java
                            .getDeclaredField("map")
                            .apply { isAccessible = true }
                            .get(a) as TacticalMapView
                    // "Land" shows the complete state including exclaves (Neuwerk, islands).
                    view.fitWholeState()
                    val transform =
                        TacticalMapView::class
                            .java
                            .getDeclaredMethod(
                                "makeTransform",
                                Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType,
                            )
                            .apply { isAccessible = true }
                            .invoke(view, 1f, 0f, 0f) as TacticalMapView.Transform
                    val points = region.rings.flatten().map { transform.point(it.lon, it.lat) }
                    assertTrue(
                        points.all {
                            it.x >= 0 && it.x <= view.width && it.y >= 0 && it.y <= view.height
                        }
                    )
                }
                instrument.waitForIdleSync()
            }
            scenario.onActivity { a ->
                android.util.Log.i("NativeCheck", "Wandsbek")
                val g = game(a)
                g.setRegion("DE-HH")
                assertTrue(g.setScope("WANDSBEK"))
                g.state.paused = true
                invoke(a, "showTab", "MAP")
            }
            screenshot("native-wandsbek.png")
            scenario.onActivity { a ->
                val g = game(a)
                val u = g.state.units.first { it.scope == "WANDSBEK" && it.type == "STREIFE" }
                assertTrue(u.officers.all { "P99" in it.equipment && "P99" in it.owned })
                assertNotNull(EquipmentArt.load(a, EquipmentArt.equipmentImage("P99")))
                assertNotNull(EquipmentArt.portrait(a, 1))
                invoke(a, "showOfficer", u, 0)
            }
            screenshot("native-officer.png")
            scenario.onActivity { a ->
                val g = game(a)
                invoke(
                    a,
                    "showEquipment",
                    g.state.units.first { it.scope == "WANDSBEK" && it.type == "STREIFE" },
                    0,
                )
            }
            screenshot("native-equipment.png")
            scenario.onActivity { a ->
                val g = game(a)
                g.setShift(22, 24)
                g.startShift()
                g.state.paused = false
                val sek = g.state.units.first { it.scope == "WANDSBEK" && it.type == "SEK" }
                val call =
                    g.newIncident(
                        IncidentTemplate("Normale Polizeilage", 3, "STREIFE", 20, "Unterstützung"),
                        GeoPoint(10.1, 53.6),
                    )
                val origin = GeoPoint(sek.lon, sek.lat)
                assertTrue(g.dispatch(call, sek))
                g.advance(20)
                assertNotEquals(origin, GeoPoint(sek.lon, sek.lat))
                assertTrue(call.assignedUnits.contains(sek.id))
                g.state.paused = true
            }
            var remaining = 1410
            while (remaining > 0) {
                val step = remaining.coerceAtMost(60)
                scenario.onActivity { a ->
                    val g = game(a)
                    g.state.paused = false
                    g.advance(step)
                    g.state.paused = true
                }
                instrument.waitForIdleSync()
                remaining -= step
            }
            scenario.onActivity { a ->
                val g = game(a)
                g.state.paused = false
                g.newIncident(IncidentTemplate("Übergabe", 1, "STREIFE", 100, "Bleibt erhalten"))
                g.advance(10)
                assertEquals("DEBRIEF", g.state.phase)
                assertEquals(1440, g.state.elapsed)
                assertEquals(1, g.state.completedShifts)
                assertTrue(g.state.reports.first().rotations > 0)
                assertTrue(g.state.reports.first().handed > 0)
                invoke(a, "showDebrief")
            }
            screenshot("native-24h-report.png")
            scenario.onActivity { a ->
                val g = game(a)
                val dialog =
                    MainActivity::class
                        .java
                        .getDeclaredField("sheet")
                        .apply { isAccessible = true }
                        .get(a) as android.app.Dialog
                assertTrue(tapText(dialog.window!!.decorView, "Nächsten Tag vorbereiten"))
                assertEquals(2, g.state.day)
                dismiss(a)
                invoke(a, "showTab", "MAP")
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a ->
                assertTrue(game(a).state.paused)
                // onPause persists through the production background writer.
            }
        }
        GameStore.awaitSaves()
        val durable =
            GameStore.fromJson(
                org.json.JSONObject(
                    instrument.targetContext
                        .getFileStreamPath("einsatzleitstelle-native-v1.json")
                        .readText()
                )
            )
        assertEquals(2, durable.day)
        assertEquals("WANDSBEK", durable.scope)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val g = game(a)
                assertEquals(2, g.state.day)
                assertEquals("WANDSBEK", g.state.scope)
                assertTrue(g.state.incidents.any { it.status == "HANDED" })
            }
        }
    }
}
