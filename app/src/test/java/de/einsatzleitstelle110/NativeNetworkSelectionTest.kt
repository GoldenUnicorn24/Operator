package de.einsatzleitstelle110

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NativeNetworkSelectionTest {
    @After fun clear() { NativeRouting.clear(); MapData.clear() }
    @Test fun statewideFleetUsesMainRoadsAndDistrictFleetUsesResidentialDetail() {
        val a = GeoPoint(10.0, 53.5)
        val b = GeoPoint(10.03, 53.5)
        val c = GeoPoint(10.015, 53.54)
        fun road(cls: Int, from: GeoPoint, to: GeoPoint) = RoadLine(cls, "", doubleArrayOf(from.lon, from.lat, to.lon, to.lat), "Straße", 0)
        NativeRouting.install(StateMap("DE-HH", listOf(road(2, a, c), road(2, c, b), road(3, a, b)), emptyList(), "OSM", version = 2))
        val state = UnitState("state", "Peter 1/1", "STREIFE", "DE-HH", a.lat, a.lon, scope = "STATE")
        val district = state.copy(id = "district", scope = "WANDSBEK")
        val main = NativeRouting.routeFor(state, b)
        val detail = NativeRouting.routeFor(district, b)
        assertEquals("OSM_MAIN_ROADS_APPROXIMATION", main.basis)
        assertEquals("OSM_DETAIL_ROADS_APPROXIMATION", detail.basis)
        assertTrue(main.points.contains(c))
        assertFalse(detail.points.contains(c))
        assertTrue(main.distanceKm > detail.distanceKm * 2)
        assertEquals(main.durationMinutes, NativeRouting.eta(state, b))
        assertEquals(detail.durationMinutes, NativeRouting.eta(district, b))
        // Installing refreshed data must invalidate the separate statewide graph too.
        NativeRouting.install(StateMap("DE-HH", listOf(road(2, a, b)), emptyList(), "OSM", version = 2))
        assertFalse(NativeRouting.routeFor(state, b).points.contains(c))
    }
}
