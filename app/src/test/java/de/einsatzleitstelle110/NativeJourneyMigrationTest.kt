package de.einsatzleitstelle110

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeJourneyMigrationTest {
    private fun source(status: String): JSONObject {
        val officer =
            JSONObject()
                .put("name", "Nora")
                .put("equipped", JSONArray(listOf("radio", "vest")))
                .put("owned", JSONArray(listOf("radio", "vest")))
        val unit =
            JSONObject()
                .put("id", 11)
                .put("name", "Mein Wagen")
                .put("type", "STREIFE")
                .put("position", JSONArray(listOf(10.04, 53.55)))
                .put("basePosition", JSONArray(listOf(10.0, 53.55)))
                .put("officers", JSONArray().put(officer))
                .put("incidentId", 4)
                .put("status", if (status == "working") "onScene" else "responding")
        if (status != "working")
            unit.put(
                "route",
                JSONObject()
                    .put(
                        "points",
                        JSONArray()
                            .put(JSONArray(listOf(10.0, 53.55)))
                            .put(JSONArray(listOf(10.1, 53.55))),
                    )
                    .put("durationMinutes", 10)
                    .put("elapsedMinutes", 4)
                    .put("departureAt", 360),
            )
        val call =
            JSONObject()
                .put("id", 4)
                .put("name", "Laufende Lage")
                .put("priority", 3)
                .put("needs", JSONArray(listOf("STREIFE")))
                .put("duration", 20)
                .put("description", "Original")
                .put("position", JSONArray(listOf(10.1, 53.55)))
                .put("status", status)
                .put("assignedIds", JSONArray(listOf(11)))
                .put("completeAt", 368)
                .put("created", 0)
        return JSONObject()
            .put("version", 3)
            .put("schemaVersion", 3)
            .put("mode", "sandbox")
            .put("stage", "running")
            .put("regionId", "HH")
            .put("day", 1)
            .put("startHour", 6)
            .put("elapsed", 4)
            .put("absoluteSimMinutes", 364)
            .put("units", JSONArray().put(unit))
            .put("incidents", JSONArray().put(call))
            .put("templates", JSONArray())
    }

    private fun game(s: GameState) =
        GameController(
            s,
            listOf(
                GeoRegion(
                    "DE-HH",
                    "Hamburg",
                    listOf(
                        listOf(
                            GeoPoint(9.0, 53.0),
                            GeoPoint(11.0, 53.0),
                            GeoPoint(11.0, 54.0),
                            GeoPoint(9.0, 54.0),
                        )
                    ),
                    listOf(GeoCity("Mitte", 10.0, 53.55)),
                )
            ),
        )

    @Test
    fun routeProgressSurvivesNativeImportWithoutTeleport() {
        val state = GameStore.fromJson(source("dispatching"))
        val u = state.units.first()
        assertEquals("DRIVING", u.status)
        assertEquals(6, u.travelLeft)
        assertEquals(10.04, u.lon, .00001)
        val g = game(state)
        state.paused = false
        g.advance(1)
        assertEquals(10.05, u.lon, .00001)
        assertEquals("DRIVING", state.incidents.first().status)
    }

    @Test
    fun workRemainingSurvivesNativeImport() {
        val state = GameStore.fromJson(source("working"))
        assertEquals(4, state.units.first().workLeft)
        state.paused = false
        game(state).advance(4)
        assertTrue(state.incidents.first().status in listOf("SOLVED", "FAILED"))
    }

    @Test
    fun sandboxBudgetRulesHaveAnEffect() {
        val s = GameState(mode = "SANDBOX", budget = 100)
        val g = game(s)
        g.seedUnitsIfNeeded()
        assertFalse(g.equipOfficer(s.units.first(), 0, "MP5"))
        s.unlimitedBudget = true
        assertTrue(g.equipOfficer(s.units.first(), 0, "MP5"))
        assertEquals(100, s.budget)
    }
}
