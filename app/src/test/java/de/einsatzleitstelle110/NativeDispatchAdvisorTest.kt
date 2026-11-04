package de.einsatzleitstelle110

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NativeDispatchAdvisorTest {
    private fun game(): GameController {
        val r = GeoRegion("DE-TEST", "Testrevier", listOf(listOf(GeoPoint(9.0,53.0),GeoPoint(11.0,53.0),GeoPoint(11.0,54.0),GeoPoint(9.0,54.0),GeoPoint(9.0,53.0))), emptyList())
        return GameController(GameState(region = r.code, phase = "RUNNING", fatigueEnabled = false, autoCalls = false, absoluteMinute = 360), listOf(r))
    }
    private fun unit(g: GameController, id: String, type: String = "STREIFE", status: String = "FREE", lon: Double = 10.10): UnitState =
        UnitState(id,id,type,g.state.region,53.60,lon,status = status,officers = mutableListOf(Officer("A"),Officer("B"),Officer("C"),Officer("D"))).also { g.state.units.add(it) }
    private fun call(g: GameController, vararg roles: String) =
        IncidentState("c", "Test", "Teststraße",g.state.region,53.60,10.11,2,roles.first(),10,"Test",requiredTypes = roles.toMutableList()).also { g.state.incidents.add(it) }
    @After fun cleanup() { NativeRouting.clear(); MapData.clear() }

    @Test fun proposalUsesPatrolAndReturnersWithoutChangingGameOrRandomness() {
        val g = game()
        unit(g,"patrol",status = "PATROLLING")
        unit(g,"return",status = "RETURNING")
        unit(g,"busy",status = "WORKING")
        val c = call(g,"STREIFE","STREIFE")
        val seed = g.state.randomSeed; val budget = g.state.budget
        val proposal = DispatchAdvisor.plan(g,c,false)
        assertEquals(setOf("patrol","return"),proposal.picks.map { it.unitId }.toSet())
        assertTrue(proposal.missing.isEmpty())
        assertTrue(c.assignedUnits.isEmpty()); assertEquals("OPEN",c.status)
        assertEquals(seed,g.state.randomSeed); assertEquals(budget,g.state.budget)
        assertTrue(g.state.logs.isEmpty())
        for (p in proposal.picks) assertEquals(g.eta(g.state.units.first { it.id == p.unitId },c,false),p.eta)
    }
    @Test fun keepsScarceInvestigatorForInvestigationAndNeverDoubleBooks() {
        val g = game(); unit(g,"kripo","KRIPO"); unit(g,"patrol")
        val c = call(g,"STREIFE","KRIPO","STREIFE")
        val p = DispatchAdvisor.plan(g,c)
        assertEquals(2,p.picks.size); assertEquals(2,p.picks.map { it.unitId }.distinct().size)
        assertEquals("kripo",p.picks.single { it.role == "KRIPO" }.unitId)
        assertEquals(listOf("STREIFE"),p.missing)
    }
    @Test fun existingVersatileCrewCanCoverAnotherRoleWhenReinforcementArrives() {
        val g = game()
        val existing = unit(g,"existing",status = "DRIVING")
        unit(g,"dog","HUND")
        val c = call(g,"STREIFE","VERKEHR")
        c.assignedUnits.add(existing.id); c.status = "DRIVING"
        val p = DispatchAdvisor.plan(g,c,false)
        assertEquals("dog",p.picks.single().unitId)
        assertTrue(p.missing.isEmpty())
        assertTrue(g.dispatch(c,g.state.units.first { it.id == "dog" },false))
        assertTrue(g.remainingTypes(c).isEmpty())
    }
    @Test fun wholeProposalRespectsBudgetAndDeploymentStillAcceptsIt() {
        val g = game(); g.state.budget = 4200
        unit(g,"sek1","SEK"); unit(g,"sek2","SEK"); unit(g,"patrol")
        val c = call(g,"SEK","SEK","STREIFE")
        val p = DispatchAdvisor.plan(g,c,false)
        assertEquals(2,p.picks.size); assertEquals(4200,p.fee)
        assertEquals(listOf("SEK"),p.missing)
        assertTrue(p.warnings.any { it.contains("Budget") })
        for (pick in p.picks) assertTrue(g.dispatch(c,g.state.units.first { it.id == pick.unitId },false))
        assertEquals(0,g.state.budget)
        assertEquals(listOf("SEK"),g.remainingTypes(c))
    }
    @Test fun qualificationCrewConditionAndRegionGatesMatchManualDispatch() {
        val g = game(); g.state.trainingRequired = true
        val untrained = unit(g,"untrained","SEK")
        val trained = unit(g,"trained","SEK").also { it.officers.first().qualifications.add("SEK-Fortbildung") }
        unit(g,"broken","SEK").vehicleCondition = 19
        unit(g,"understaffed","SEK").officers.clear()
        unit(g,"other","SEK").region = "DE-NI"
        val c = call(g,"SEK")
        val p = DispatchAdvisor.plan(g,c)
        assertEquals(listOf(trained.id),p.picks.map { it.unitId })
        assertTrue(p.warnings.any { it.contains("Ausbildung") })
        assertFalse(g.dispatch(c,untrained))
        assertTrue(g.dispatch(c,trained))
    }
    @Test fun signalAffectsForecastAndDeadlineWarningAndAssignedRolesAreNotRepeated() {
        val g = game(); unit(g,"far",lon = 9.1)
        val c = call(g,"STREIFE"); c.deadline = 1; c.addressUncertain = true
        val quiet = DispatchAdvisor.plan(g,c,false); val signal = DispatchAdvisor.plan(g,c,true)
        assertTrue(signal.arrival!! < quiet.arrival!!)
        assertTrue(quiet.warnings.any { it.contains("Frist") }); assertTrue(quiet.warnings.any { it.contains("Adresse") })
        assertTrue(g.dispatch(c,g.state.units.first(),false))
        assertTrue(DispatchAdvisor.plan(g,c).picks.isEmpty())
    }
    @Test fun lowerDeploymentFeeWinsBeforeTravelTimeButLateArrivalIsExplained() {
        val g = game(); unit(g,"costly","SEK"); unit(g,"free",lon = 9.1)
        val c = call(g,"STREIFE"); c.deadline = 1
        val p = DispatchAdvisor.plan(g,c)
        assertEquals("free",p.picks.single().unitId); assertEquals(0,p.fee)
        assertTrue(p.warnings.any { it.contains("Frist") })
    }
    @Test fun deskPrioritizesDecisionAndMissingForcesAndRemovesCompletedFromAction() {
        val g = game()
        val working = call(g,"STREIFE").copy(id = "working",status = "WORKING",assignedUnits = mutableListOf("scene"))
        unit(g,"scene",status = "WORKING")
        val open = call(g,"STREIFE").copy(id = "open",priority = 1)
        val decision = working.copy(id = "decision",pending = "callback",age = 11,pendingAge = 0)
        val solved = open.copy(id = "solved",status = "SOLVED")
        assertEquals(listOf("decision","open","working","solved"),IncidentDesk.ordered(g,listOf(solved,working,open,decision)).map { it.id })
        assertEquals(1,IncidentDesk.attention(g,decision).minutes)
        assertFalse(IncidentDesk.attention(g,working).needsAction)
        assertFalse(IncidentDesk.attention(g,solved).needsAction)
        val late = working.copy(id = "late",status = "DRIVING",age = 179,deadline = 180)
        g.state.units.first().travelLeft = 5; g.state.units.first().status = "DRIVING"
        assertEquals(0,IncidentDesk.attention(g,late).order)
    }
}
