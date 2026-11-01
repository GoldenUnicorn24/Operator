package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

class NativeSnapshotTest {
    @Test
    fun detachedSaveCannotChangeWhenSimulationOrEditorContinues() {
        val s = GameState()
        val u = UnitState("one", "Peter 1", "STREIFE", "DE-HH", 53.6, 10.1)
        u.reliefTeams.add(mutableListOf(Officer("Ablösung")))
        s.units.add(u)
        s.incidents.add(
            IncidentState(
                "call",
                "Ruf",
                "Ort",
                "DE-HH",
                53.6,
                10.1,
                3,
                "STREIFE",
                20,
                "Test",
                assignedUnits = mutableListOf(u.id),
            )
        )
        s.logs.add("Vorher")
        val copy = GameSnapshots.capture(s)
        u.officer.energy = 12
        u.officer.equipment.add("G36")
        u.reliefTeams[0][0].name = "Geändert"
        s.incidents[0].assignedUnits.clear()
        s.logs.clear()
        assertEquals(100, copy.units[0].officer.energy)
        assertFalse("G36" in copy.units[0].officer.equipment)
        assertEquals("Ablösung", copy.units[0].reliefTeams[0][0].name)
        assertEquals(listOf("one"), copy.incidents[0].assignedUnits)
        assertEquals(listOf("Vorher"), copy.logs)
        assertSame(copy.units[0].officer, copy.units[0].officers[0])
    }
}
