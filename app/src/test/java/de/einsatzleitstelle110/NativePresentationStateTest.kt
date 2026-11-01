package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

class NativePresentationStateTest {
    @Test
    fun textRadioAndDistinctEquipmentValuesSurvivePersistence() {
        val r =
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
        val s = GameState(autoCalls = false)
        val g = GameController(s, listOf(r))
        g.seedUnitsIfNeeded()
        g.startShift()
        assertTrue(s.logs.any { it.contains("Notruf") })
        assertTrue(GameContent.equipmentBonus("G36") > GameContent.equipmentBonus("MP5"))
        assertEquals(0, GameContent.equipmentBonus("P99"))
        val copy = GameStore.fromJson(GameStore.toJson(s))
        assertEquals(s.logs, copy.logs)
    }
}
