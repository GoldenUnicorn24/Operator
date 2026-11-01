package de.einsatzleitstelle110

import java.io.File
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class NativeOperationsAreasTest {
    private fun load() {
        OperationsAreas.loadFromJson(
            File("src/main/native_assets/hamburg-districts.json").readText()
        )
    }

    @Test
    fun sevenOfficialDistrictsAndWandsbekJurisdiction() {
        load()
        assertEquals(8, OperationsAreas.options("DE-HH").size)
        assertEquals(1, OperationsAreas.options("DE-BY").size)
        assertTrue(OperationsAreas.contains("DE-HH", "WANDSBEK", GeoPoint(10.10, 53.60)))
        assertFalse(OperationsAreas.contains("DE-HH", "WANDSBEK", GeoPoint(9.9357, 53.5493)))
        val b = OperationsAreas.bounds("DE-HH", "WANDSBEK")!!
        assertTrue(b[0] > 10.0)
        assertTrue(b[2] < 10.5)
        assertTrue(b[1] > 53.5)
        assertTrue(b[3] < 54.0)
    }

    @Test
    fun everyGeneratedDistrictIncidentIsInsideItsRealBoundary() {
        load()
        val region =
            GeoRegion(
                "DE-HH",
                "Hamburg",
                listOf(
                    listOf(
                        GeoPoint(8.0, 53.0),
                        GeoPoint(11.0, 53.0),
                        GeoPoint(11.0, 54.5),
                        GeoPoint(8.0, 54.5),
                    )
                ),
                emptyList(),
            )
        val rng = Random(110)
        for ((scope, _) in OperationsAreas.options("DE-HH").drop(1)) repeat(200) {
            val p = OperationsAreas.sample(region, scope, rng)
            assertTrue(OperationsAreas.contains("DE-HH", scope, p))
        }
    }

    @Test
    fun changingToDistrictKeepsUnrelatedHandoversPausedAndPreservesFleetPositions() {
        load()
        val region =
            GeoRegion(
                "DE-HH",
                "Hamburg",
                listOf(
                    listOf(
                        GeoPoint(8.0, 53.0),
                        GeoPoint(11.0, 53.0),
                        GeoPoint(11.0, 54.5),
                        GeoPoint(8.0, 54.5),
                    )
                ),
                listOf(GeoCity("Hamburg", 10.0, 53.55)),
            )
        val state = GameState(mode = "SANDBOX", autoCalls = false)
        val game = GameController(state, listOf(region))
        game.seedUnitsIfNeeded()
        game.startShift()
        val outside =
            game.newIncident(
                IncidentTemplate("Altona", 3, "STREIFE", 30, "Test"),
                GeoPoint(9.9357, 53.5493),
            )
        val unit = state.units.first { it.type == "STREIFE" }
        assertTrue(game.dispatch(outside, unit))
        game.advance(30)
        game.finishShift()
        val position = GeoPoint(unit.lon, unit.lat)
        game.nextDay()
        assertTrue(game.setScope("WANDSBEK"))
        game.startShift()
        game.advance(60)
        assertEquals("HANDED", outside.status)
        assertEquals(position, GeoPoint(unit.lon, unit.lat))
        assertTrue(
            state.incidents
                .filter { it.scope == "WANDSBEK" }
                .all { OperationsAreas.contains("DE-HH", "WANDSBEK", GeoPoint(it.lon, it.lat)) }
        )
    }
}
