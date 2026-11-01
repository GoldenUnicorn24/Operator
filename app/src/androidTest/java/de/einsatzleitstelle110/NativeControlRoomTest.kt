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

    private fun assertNative(view: View) {
        assertFalse(view.javaClass.name.startsWith("android.webkit."))
        if (view is ViewGroup) for (i in 0 until view.childCount) assertNative(view.getChildAt(i))
    }

    private fun overview(
        region: String,
        names: List<String>,
        points: List<GeoPoint>,
        file: String,
    ) {
        reset()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { a ->
                dismiss(a)
                val g = game(a)
                g.state.mode = "SANDBOX"
                g.state.unlimitedBudget = true
                g.state.autoCalls = false
                g.setRegion(region)
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
            screenshot(file)
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
        instrument.targetContext.deleteFile("einsatzleitstelle-native-v1.json")
        instrument.targetContext.deleteFile("einsatzleitstelle-native-v1.json.backup")
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
                g.nextDay()
                assertEquals(2, g.state.day)
                invoke(a, "showTab", "MAP")
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a ->
                assertTrue(game(a).state.paused)
                GameStore.save(a, game(a).state)
            }
        }
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
