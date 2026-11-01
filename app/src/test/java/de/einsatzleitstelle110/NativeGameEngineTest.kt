package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

class NativeGameEngineTest {
    private fun testRegion():GeoRegion {
        val ring=listOf(GeoPoint(9.0,53.0),GeoPoint(11.0,53.0),GeoPoint(11.0,54.0),
            GeoPoint(9.0,54.0),GeoPoint(9.0,53.0))
        return GeoRegion("DE-HH","Hamburg",listOf(ring),listOf(
            GeoCity("Mitte",10.0,53.5),GeoCity("Nord",10.03,53.63),GeoCity("Süd",9.95,53.42)))
    }
    private fun controller():GameController {
        val s=GameState();val g=GameController(s,listOf(testRegion()))
        g.seedUnitsIfNeeded();s.autoCalls=false;return g
    }
    @Test fun allDurationsAvailable() {
        val g=controller()
        for(h in listOf(8,12,24)) {
            g.setShift(22,h)
            assertEquals(h*60,g.state.duration)
        }
    }
    @Test fun fullDay24HoursAndExactlyOneDayTransition() {
        val g=controller();g.setShift(6,24);g.startShift()
        repeat(12){g.advance(120)}
        assertEquals("DEBRIEF",g.state.phase)
        assertEquals(1440,g.state.elapsed)
        assertEquals(1,g.state.reports.size)
        assertEquals(1440,g.state.reports[0].duration)
        g.finishShift();assertEquals(1,g.state.reports.size)
        g.nextDay();assertEquals(2,g.state.day);assertEquals("PLANNING",g.state.phase)
    }
    @Test fun nightShiftEndsOnlyAtDurationNotMidnight(){
        val g=controller();g.setShift(22,12);g.startShift()
        repeat(4){g.advance(120)}
        assertEquals("RUNNING",g.state.phase)
        assertEquals(1,g.state.day)
        repeat(2){g.advance(120)}
        assertEquals("DEBRIEF",g.state.phase)
        assertEquals(720,g.state.elapsed)
        assertEquals(1,g.state.day)
    }
    @Test fun dispatchMovesUnitOnNativeGameClock(){
        val g=controller();g.startShift()
        val call=g.state.incidents.first()
        val u=g.state.units.first{it.type=="STREIFE"&&it.status=="FREE"}
        assertTrue(g.dispatch(call,u))
        assertEquals("DRIVING",u.status)
        val eta=u.travelLeft;g.advance(eta)
        assertEquals("WORKING",u.status)
        assertEquals(call.lat,u.lat,.00001)
        assertEquals(call.lon,u.lon,.00001)
        g.advance(call.duration)
        assertTrue(call.status in listOf("SOLVED","FAILED"))
        assertEquals("FREE",u.status)
    }
    @Test fun trainingFinishesAfterTwoCompletedShifts(){
        val g=controller();val u=g.state.units.first{it.type=="STREIFE"}
        assertTrue(g.train(u,"Erste Hilfe"))
        assertEquals(2,u.trainingLeft)
        g.startShift();g.finishShift();assertEquals(1,u.trainingLeft)
        g.nextDay();g.startShift();g.finishShift()
        assertEquals(0,u.trainingLeft);assertEquals(2,u.level)
    }
    @Test fun customTemplatesAndEquipment(){
        val g=controller()
        val t=IncidentTemplate("Eigener Einsatz",2,"MEK",30,"Eigene Lage")
        assertTrue(g.addTemplate(t))
        assertTrue(g.allTemplates().contains(t))
        val u=g.state.units.first()
        assertTrue(g.buyEquipment(u,"Bodycam"))
        assertFalse(g.buyEquipment(u,"Bodycam"))
    }
    @Test fun geometryBoundsContainCities(){
        val region=testRegion()
        for(city in region.cities) assertTrue(region.contains(GeoPoint(city.lon,city.lat)))
    }
}