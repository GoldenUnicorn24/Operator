package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

class NativeMapAlgorithmsTest {
    @Test
    fun clustersKeepNeighbouringBucketMarkersAccessible() {
        val result =
            MapClustering.cluster(
                listOf(
                    MapClustering.Marker(37f, 0f, "patrol"),
                    MapClustering.Marker(39f, 0f, "SEK"),
                    MapClustering.Marker(300f, 300f, "call"),
                ),
                38f,
            )
        assertEquals(2, result.size)
        assertEquals(setOf("patrol", "SEK"), result.first().members.map { it.value }.toSet())
    }

    @Test
    fun hundredMarkerAccountingNeverDropsObjects() {
        val markers = (0..299).map { MapClustering.Marker((it % 30) * 40f, (it / 30) * 40f, it) }
        val clustered = MapClustering.cluster(markers, 38f)
        assertEquals((0..299).toSet(), clustered.flatMap { it.members }.map { it.value }.toSet())
    }

    @Test
    fun crossDistrictRouteHasPhysicalDistanceAndSpeed() {
        val a = GeoPoint(8.2146, 53.1435)
        val b = GeoPoint(9.732, 52.3759)
        val slow = NativeRouting.route(a, b, 60.0)
        val fast = NativeRouting.route(a, b, 120.0)
        assertTrue(slow.distanceKm > 100)
        assertTrue(slow.durationMinutes > 100)
        assertTrue(kotlin.math.abs(slow.durationMinutes - 2 * fast.durationMinutes) <= 1)
        assertEquals("DISTANCE_APPROXIMATION", slow.basis)
    }

    @Test
    fun movementFollowsDistanceNotVertexCount() {
        val points = listOf(GeoPoint(10.0, 53.5), GeoPoint(10.01, 53.5), GeoPoint(10.1, 53.5))
        val halfway = NativeRouting.position(points, .5)
        assertEquals(10.05, halfway.lon, .0001)
        assertEquals(53.5, halfway.lat, 0.0)
        assertEquals(points.first(), NativeRouting.position(points, 0.0))
        assertEquals(points.last(), NativeRouting.position(points, 1.0))
    }

    @Test
    fun replacingRouteWithSameEndpointsDropsTheOldGeometry() {
        val start = GeoPoint(10.0, 53.5)
        val end = GeoPoint(10.1, 53.5)
        val detour = GeoPoint(10.05, 53.56)
        val u = UnitState("patrol", "Peter 1", "STREIFE", "DE-HH", start.lat, start.lon)
        u.routePoints = listOf(start, start, end)
        val old = u.routeGeometry()
        assertSame(old, u.routeGeometry())
        assertEquals(10.05, old.position(.5).lon, .0001)

        u.routePoints = listOf(start, detour, end)
        val next = u.routeGeometry()
        assertNotSame(old, next)
        assertTrue(next.lengthKm > old.lengthKm)
        assertEquals(detour.lat, next.position(.5).lat, .0001)
        assertEquals(end, next.position(1.0))
    }

    @Test
    fun oldRouteWithNoUsablePolylineUsesSavedEndpoints() {
        val start = GeoPoint(10.0, 53.5)
        val end = GeoPoint(10.1, 53.5)
        val u = UnitState("patrol", "Peter 1", "STREIFE", "DE-HH", start.lat, start.lon,
            destLat = end.lat, destLon = end.lon, routePoints = listOf(start))
        val route = u.routeGeometry()
        assertEquals(listOf(start, end), route.points)
        assertSame(route, u.routeGeometry())

        u.destLon = 10.2
        assertEquals(10.2, u.routeGeometry().position(1.0).lon, 0.0)
    }

    @Test
    fun bundledHamburgRoadGraphProducesRealPolyline() {
        val file =
            listOf(
                    java.io.File("src/main/native_assets/hamburg-roads.json"),
                    java.io.File("app/src/main/native_assets/hamburg-roads.json"),
                )
                .first { it.isFile }
        NativeRouting.loadRoadsJson(file.readText())
        val route = NativeRouting.route(GeoPoint(9.9937, 53.5511), GeoPoint(9.982, 53.461), 60.0)
        assertEquals("HAMBURG_MAIN_ROADS_APPROXIMATION", route.basis)
        assertTrue(route.points.size > 20)
        assertTrue(route.distanceKm > 10)
        assertTrue(route.distanceKm < 40)
    }
}
