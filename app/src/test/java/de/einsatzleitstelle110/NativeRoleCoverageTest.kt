package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

class NativeRoleCoverageTest {
    private fun game() = GameController(GameState(region = "DE-HH"), emptyList())
    private fun unit(id: String, type: String) = UnitState(id, id, type, "DE-HH", 53.60, 10.10)

    @Test fun customCapabilityOverridesVehicleBaseTypeWhenCountingCoverage() {
        val g = game()
        val u = unit("custom", "KRIPO")
        g.state.units.add(u)
        g.state.chief.unitTypes.add(ChiefUnitType("escort", "Begleitung", 0, "civil", 2, setOf("STREIFE")))
        g.state.chief.vehicles.add(ChiefVehicle("v", u.id, "civil", "s", customTypeId = "escort"))
        val call = IncidentState("c", "Test", "Test", "DE-HH", 53.60, 10.10, 2, "KRIPO", 10, "Test", requiredTypes = mutableListOf("KRIPO"), assignedUnits = mutableListOf(u.id))
        assertFalse(g.canHandle(u, "KRIPO"))
        assertEquals(listOf("KRIPO"), g.remainingTypes(call))
    }

    @Test fun flexibleUnitDoesNotConsumeTheOnlyRoleOfAnotherUnit() {
        val g = game()
        val flexible = unit("flex", "KRIPO")
        val patrol = unit("patrol", "SEK")
        g.state.units.addAll(listOf(flexible, patrol))
        // Only STREIFE remains for the escort; the investigator must cover KRIPO.
        g.state.chief.unitTypes.add(ChiefUnitType("escort", "Begleitung", 0, "special", 2, setOf("STREIFE")))
        g.state.chief.vehicles.add(ChiefVehicle("v", patrol.id, "special", "s", customTypeId = "escort"))
        val call = IncidentState("c", "Test", "Test", "DE-HH", 53.60, 10.10, 2, "STREIFE", 10, "Test", requiredTypes = mutableListOf("STREIFE", "KRIPO"), assignedUnits = mutableListOf(flexible.id, patrol.id))
        assertEquals(emptyList<String>(), g.remainingTypes(call))
        call.assignedUnits.reverse()
        assertEquals(emptyList<String>(), g.remainingTypes(call))
        call.requiredTypes.add("STREIFE")
        assertEquals(listOf("STREIFE"), g.remainingTypes(call))
    }
}
