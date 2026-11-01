package de.einsatzleitstelle110
import org.junit.Assert.*
import org.junit.Test

class NativeEngineTest {
 @Test fun catalogHasDiverseDispatchCalls(){assertTrue(Catalog.templates.size>=48);assertTrue(Catalog.templates.any{it.second=="SEK"});assertTrue(Catalog.templates.any{it.second=="MEK"})}
 @Test fun eightCoursesAndSevenTypes(){assertEquals(8,Catalog.courses.size);assertEquals(7,Catalog.unitTypes.size)}
 @Test fun geographicDistanceIsRealistic(){val km=SimEngine.haversine(52.374,9.735,53.143,8.2146);assertTrue(km>100 && km<170)}
}
