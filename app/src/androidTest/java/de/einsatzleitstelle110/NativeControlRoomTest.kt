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
            scenario.onActivity { a -> ready = mapView(a).baseMapReady() }
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
