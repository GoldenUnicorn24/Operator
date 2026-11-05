package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

/** Python-produced binary fixtures exercise the actual language boundary, not a Kotlin re-encoder. */
class NativeMapFormatTest {
    private fun fixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/map-format/$name")!!.use { it.readBytes() }

    @Test fun legacyR1RetainsRoadReferenceGeometryAndDefaultDirection() {
        val map = MapData.parseBinary("DE-HH", fixture("legacy-r1.bin"))
        assertEquals(1, map.version)
        assertTrue(map.areas.isEmpty())
        assertEquals("Altstadt", map.places.single().name)
        assertEquals("town", map.places.single().kind)
        assertEquals(12345, map.places.single().population)
        assertEquals(10.0, map.places.single().lon, 0.0)
        val road = map.roads.single()
        assertEquals(2, road.cls)
        assertEquals("B 75", road.ref)
        assertEquals("", road.name)
        assertEquals(0, road.oneway)
        assertArrayEquals(doubleArrayOf(10.0, 53.5, 10.001, 53.49999, 10.002, 53.50001), road.points, 0.0)
    }

    @Test fun r2KeepsUtf8NamesSeparateFromRefsAndReadsEveryDirection() {
        val map = MapData.parseBinary("DE-HH", fixture("detail-r2.bin"))
        assertEquals(2, map.version)
        assertEquals("Wandsbek", map.places.single().name)
        assertEquals("suburb", map.places.single().kind)
        assertEquals(47228, map.places.single().population)
        assertEquals(10.066, map.places.single().lon, 0.0)
        assertEquals(53.5731, map.places.single().lat, 0.0)
        assertEquals(listOf("K 3", "", "B 75"), map.roads.map { it.ref })
        assertEquals(listOf("Ölmühlenweg", "Wohnhof", "Wandsbeker Chaussee"), map.roads.map { it.name })
        assertEquals(listOf(-1, 0, 1), map.roads.map { it.oneway })
        assertEquals(listOf(3, 5, 2), map.roads.map { it.cls })
        assertArrayEquals(doubleArrayOf(10.0, 53.5, 10.001, 53.49999, 10.002, 53.50001), map.roads[0].points, 0.0)
        assertEquals(listOf(0, 1, 2), map.areas.map { it.kind })
        assertEquals(2, map.areas[0].rings.size)
        assertArrayEquals(doubleArrayOf(10.002,53.502, 10.003,53.502, 10.003,53.503, 10.002,53.503, 10.002,53.502),
            map.areas[0].rings[1], 0.0)
    }

    @Test fun jsonAndPythonBinaryHaveTheSamePublicData() {
        for (name in listOf("legacy-r1", "detail-r2", "empty-r2")) {
            val a = MapData.parseState("DE-HH", String(fixture("$name.json"), Charsets.UTF_8))
            val b = MapData.parseBinary("DE-HH", fixture("$name.bin"))
            assertEquals(a.version, b.version)
            assertEquals(a.places, b.places)
            assertEquals(a.roads.size, b.roads.size)
            for (i in a.roads.indices) {
                assertEquals(a.roads[i].cls, b.roads[i].cls)
                assertEquals(a.roads[i].ref, b.roads[i].ref)
                assertEquals(a.roads[i].name, b.roads[i].name)
                assertEquals(a.roads[i].oneway, b.roads[i].oneway)
                assertArrayEquals(a.roads[i].points, b.roads[i].points, 0.0)
            }
            assertEquals(a.areas.size, b.areas.size)
            for (i in a.areas.indices) {
                assertEquals(a.areas[i].kind, b.areas[i].kind)
                assertEquals(a.areas[i].rings.size, b.areas[i].rings.size)
                for (j in a.areas[i].rings.indices)
                    assertArrayEquals(a.areas[i].rings[j], b.areas[i].rings[j], 0.0)
            }
        }
    }

    @Test fun version2AllowsNoPlacesRoadsOrOptionalAreas() {
        val map = MapData.parseBinary("DE-HH", fixture("empty-r2.bin"))
        assertEquals(2, map.version)
        assertTrue(map.places.isEmpty())
        assertTrue(map.roads.isEmpty())
        assertTrue(map.areas.isEmpty())
    }

    @Test fun corruptHeaderUnsupportedVersionTruncationAndTrailingBytesAreRejected() {
        val valid = fixture("detail-r2.bin")
        val badMagic = valid.copyOf().apply { this[0] = 'X'.code.toByte() }
        val unsupportedVersion = valid.copyOf().apply { this[6] = '3'.code.toByte() }
        val badTerminator = valid.copyOf().apply { this[7] = 1 }
        for (invalid in listOf(badMagic, unsupportedVersion, badTerminator, valid + byteArrayOf(0))) {
            assertThrows(RuntimeException::class.java) { MapData.parseBinary("DE-HH", invalid) }
        }
        for (length in valid.indices) {
            assertThrows("prefix length=$length", RuntimeException::class.java) {
                MapData.parseBinary("DE-HH", valid.copyOf(length))
            }
        }
    }
}
