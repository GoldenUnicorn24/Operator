package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

class NativeAvailabilityTest {
    @Test
    fun unavailableCarsAreNotAdvertisedAsReady() {
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
        val g = GameController(GameState(), listOf(r))
        g.seedUnitsIfNeeded()
        val u = g.state.units.first()
        assertTrue(g.available(u))
        val free = g.freeUnits()
        u.vehicleCondition = 19
        assertFalse(g.available(u))
        assertEquals(free - 1, g.freeUnits())
        u.vehicleCondition = 100
        u.officers[0].energy = 14
        assertFalse(g.available(u))
        g.state.fatigueEnabled = false
        assertTrue(g.available(u))
        u.onDuty = false
        assertFalse(g.available(u))
    }
}
