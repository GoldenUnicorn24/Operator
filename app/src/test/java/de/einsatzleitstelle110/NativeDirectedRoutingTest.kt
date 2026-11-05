package de.einsatzleitstelle110

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Directed streets: the route and candidate ETA must describe the same legal journey. */
class NativeDirectedRoutingTest {
    private val a = GeoPoint(10.0, 53.5)
    private val b = GeoPoint(10.001, 53.5)
    private val c = GeoPoint(10.001, 53.501)

    @After
    fun cleanup() {
        NativeRouting.clear()
        MapData.clear()
    }

    private fun road(from: GeoPoint, to: GeoPoint, oneway: Int = 0, name: String = "Wohnstraße") =
        RoadLine(3, "", doubleArrayOf(from.lon, from.lat, to.lon, to.lat), name, oneway)

    private fun map(roads: List<RoadLine>) = StateMap("DE-TEST", roads, emptyList(), "OSM", version = 2)

    @Test
    fun shortOneWayStreetRequiresDetourAndReverseEtaMatchesIt() {
        val map = map(listOf(road(a, b, 1), road(b, c), road(c, a)))
        NativeRouting.install(map)
        val g = NativeRouting.graph(map.code)!!
        val ia = g.nearest(a)
        val ib = g.nearest(b)
        val ic = g.nearest(c)
        assertArrayEquals(intArrayOf(ia, ib), g.path(ia, ib)!!.first)
        assertArrayEquals(intArrayOf(ib, ic, ia), g.path(ib, ia)!!.first)
        for (goal in 0 until g.nodeCount) {
            val field = g.field(goal)
            for (start in 0 until g.nodeCount) {
                val path = g.path(start, goal)!!
                assertEquals(path.second.sumOf { g.edgeMinutes(it).toDouble() }, field[start].toDouble(), .00001)
            }
        }
        val reverse = NativeRouting.route(b, a, 25.0, map.code)
        assertEquals("OSM_DETAIL_ROADS_APPROXIMATION", reverse.basis)
        assertTrue("short trips must still follow one-way detours", c in reverse.points)
        val u = UnitState("u", "Peter 31/1", "STREIFE", map.code, b.lat, b.lon, vehicleSpeed = 25.0)
        assertEquals(reverse.durationMinutes, NativeRouting.eta(u, a))
        assertTrue(reverse.distanceKm > NativeRouting.route(a, b, 25.0, map.code).distanceKm)
    }

    @Test
    fun reverseOnewayAndUnreachableFieldAreRespected() {
        val g = RoadGraph("DE-TEST", map(listOf(road(a, b, -1))))
        val ia = g.nearest(a)
        val ib = g.nearest(b)
        assertNull(g.path(ia, ib))
        assertArrayEquals(intArrayOf(ib, ia), g.path(ib, ia)!!.first)
        assertTrue(g.field(ib)[ia].isInfinite())
        assertTrue(g.field(ia)[ib].isFinite())
        val geometry = mutableListOf(b)
        g.path(ib, ia)!!.second.forEach { g.appendGeometry(it, geometry) }
        assertEquals(listOf(b, a), geometry)
    }

    @Test
    fun weakComponentKeepsOneWayEntrancesAndExcludesIsolatedFragments() {
        val d = GeoPoint(10.1, 53.5)
        val e = GeoPoint(10.101, 53.5)
        // Vertex insertion order starts at the sink. Outgoing-only traversal would incorrectly
        // see three size-one components and choose the isolated two-node fragment.
        val g = RoadGraph("DE-TEST", map(listOf(road(a, b, -1), road(b, c, -1), road(d, e))))
        for (p in listOf(a, b, c)) assertEquals(p, g.point(g.nearest(p)))
        assertNotEquals(d, g.point(g.nearest(d)))
        assertNotEquals(e, g.point(g.nearest(e)))
    }

    @Test
    fun streetPointHasRealNameAndStaysInsideJurisdiction() {
        val g = RoadGraph("DE-TEST", map(listOf(road(a, b, name = "Wandsbeker Wohnweg"), road(b, c))))
        val result = g.namedStreetPoint(a) { it.lon > 10.0005 }!!
        assertEquals(b, result.first)
        assertEquals("Wandsbeker Wohnweg", result.second)
        assertNull(g.namedStreetPoint(a) { false })
    }

    @Test
    fun interiorOneWayPositionsUseLegalPartialEdgesAndMatchingEta() {
        val aa = GeoPoint(10.0, 53.5)
        val bb = GeoPoint(10.01, 53.5)
        val cc = GeoPoint(10.01, 53.51)
        val dd = GeoPoint(10.0, 53.51)
        val map = map(listOf(road(aa, bb, 1), road(bb, cc), road(cc, dd), road(dd, aa)))
        NativeRouting.install(map)
        val start = GeoPoint(10.0075, 53.5)
        val target = GeoPoint(10.0025, 53.5)
        val reverse = NativeRouting.route(start, target, 25.0, map.code)
        assertEquals("OSM_DETAIL_ROADS_APPROXIMATION", reverse.basis)
        assertEquals("must first leave the one-way street in its legal direction", bb,
            reverse.points.first { GeoData.km(it, start) > .001 })
        assertTrue(cc in reverse.points)
        assertTrue(dd in reverse.points)
        assertTrue(aa in reverse.points)
        assertEquals(target, reverse.points.last())
        val finalApproach = reverse.points.last { GeoData.km(it, target) > .001 }
        assertEquals("target must be approached from the allowed entrance", aa, finalApproach)
        val u = UnitState("u", "Peter 31/1", "STREIFE", map.code, start.lat, start.lon, vehicleSpeed = 25.0)
        assertEquals(reverse.durationMinutes, NativeRouting.eta(u, target))
        val forward = NativeRouting.route(target, start, 25.0, map.code)
        assertTrue(forward.points.none { it == aa || it == bb || it == cc || it == dd })
        assertTrue(reverse.distanceKm > forward.distanceKm * 3)
        assertTrue(reverse.durationMinutes > forward.durationMinutes)
    }

    @Test
    fun partialSameRoadKeepsBendsAndReverseOnewayDirection() {
        val bend = GeoPoint(a.lon, a.lat + .01)
        val finish = GeoPoint(a.lon + .01, a.lat + .01)
        val map = map(listOf(RoadLine(3, "", doubleArrayOf(a.lon, a.lat, bend.lon, bend.lat, finish.lon, finish.lat), "Bogenweg", -1)))
        NativeRouting.install(map)
        val start = GeoPoint(a.lon + .005, a.lat + .01)
        val end = GeoPoint(a.lon, a.lat + .005)
        val legal = NativeRouting.route(start, end, 25.0, map.code)
        assertEquals("OSM_DETAIL_ROADS_APPROXIMATION", legal.basis)
        assertEquals(start, legal.points.first())
        assertTrue("a partial edge must retain the real road bend", bend in legal.points)
        assertEquals(end, legal.points.last())
        assertEquals("DISTANCE_APPROXIMATION", NativeRouting.route(end, start, 25.0, map.code).basis)
    }

    @Test
    fun unreachableInteriorOneWayTripIsExplicitDistanceApproximation() {
        val map = map(listOf(road(a, b, 1)))
        NativeRouting.install(map)
        val start = GeoPoint(10.0007, 53.5)
        val target = GeoPoint(10.0002, 53.5)
        val route = NativeRouting.route(start, target, 25.0, map.code)
        assertEquals("DISTANCE_APPROXIMATION", route.basis)
        assertEquals(listOf(start, target), route.points)
    }

    @Test
    fun oldMapsKeepBidirectionalRoadsAndOriginalBasis() {
        val old = StateMap("DE-TEST", listOf(RoadLine(2, "B 1", doubleArrayOf(a.lon, a.lat, b.lon, b.lat))), emptyList(), "OSM")
        val g = RoadGraph(old.code, old)
        assertNotNull(g.path(g.nearest(a), g.nearest(b)))
        assertNotNull(g.path(g.nearest(b), g.nearest(a)))
        assertEquals("OSM_MAIN_ROADS_APPROXIMATION", g.basis)
        assertNull(g.namedStreetPoint(a) { true })
    }
}
