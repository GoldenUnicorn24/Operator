package de.einsatzleitstelle110

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRadioArchiveTest {
    @Test fun transmittedEventsPreserveDispatchContextAndArchiveReplay() {
        val state = GameState(absoluteMinute = 493, phase = "RUNNING")
        val game = GameController(state, emptyList())
        val unit = UnitState("unit", "Peter 24/1", "RETTUNG", "DE-HH", 53.55, 10.0)
        val incident = IncidentState("lage-7", "Unfall", "Hamburg", "DE-HH", 53.55, 10.0, 1, "RETTUNG", 14, "Meldung")
        var transmitted = emptyList<RadioLine>()
        game.transmit = { transmitted = it }
        game.emit("Am Einsatzort", "Rückmeldung vor Ort", unit, incident)
        val entry = state.radioArchive.single()
        assertEquals("08:13", entry.clock)
        assertEquals("Peter 24/1", entry.callsign)
        assertEquals("Rettungsdienst", entry.organisation)
        assertEquals("lage-7", entry.incidentId)
        assertEquals(transmitted, entry.lines())
        assertEquals("Peter 24 Strich 1", transmitted.single().clips.first().text)

        game.emit("Nachforderung", "Unfall · Weitere Kräfte", incident = incident)
        assertEquals(listOf(RadioPriority.URGENT, RadioPriority.INCIDENT), RadioArchive.filter(state.radioArchive, "lage-7", null).map { it.priority })
        assertEquals(listOf(entry), RadioArchive.filter(state.radioArchive, "peter rettungsDienst", RadioPriority.INCIDENT))
        assertTrue(RadioArchive.filter(state.radioArchive, "Peter", RadioPriority.URGENT).isEmpty())

        val saved = GameSnapshots.capture(state)
        state.radioArchive.clear()
        assertEquals(saved.radioArchive, GameStore.fromJson(GameStore.toJson(saved)).radioArchive)
    }

    @Test fun radioCommandsArePersistedAndDoNotControlAReassignedVehicle() {
        val state = GameState(absoluteMinute = 493, phase = "RUNNING")
        val game = GameController(state, emptyList())
        val incident = IncidentState("lage-17", "Unfall", "Hamburg", "DE-HH", 53.55, 10.0, 1, "RETTUNG", 14, "Meldung",
            status = "DRIVING", assignedUnits = mutableListOf("r1"))
        val unit = UnitState("r1", "RTW 5", "RETTUNG", "DE-HH", 53.55, 10.0,
            status = "DRIVING", incidentId = incident.id, travelTotal = 7, travelLeft = 7, readinessLeft = 2)
        state.incidents.add(incident)
        state.units.add(unit)
        game.emit("Einheit unterwegs", "RTW 5 · Einsatzauftrag", unit, incident)
        val original = state.radioArchive.single()
        assertEquals(setOf(RadioAction.ACKNOWLEDGE, RadioAction.REQUEST_STATUS, RadioAction.OPEN_INCIDENT),
            RadioOperations.actions(game, original).toSet())
        assertTrue(RadioOperations.acknowledge(game, original))
        assertFalse(RadioOperations.acknowledge(game, original))
        val entry = state.radioArchive.first()
        assertTrue(entry.acknowledged)
        assertEquals("Funk quittiert", state.radioArchive.last().title)

        assertTrue(RadioOperations.requestStatus(game, entry))
        val reply = state.radioArchive.last()
        assertEquals("Statusrückmeldung", reply.title)
        assertTrue(reply.message.contains("9 Min bis vor Ort"))
        assertFalse(RadioOperations.requestStatus(game, entry))
        assertEquals(state.radioArchive, GameStore.fromJson(GameStore.toJson(state)).radioArchive)

        state.absoluteMinute++
        unit.incidentId = "andere-lage"
        assertEquals(listOf(RadioAction.OPEN_INCIDENT), RadioOperations.actions(game, entry))
        assertFalse(RadioOperations.requestStatus(game, entry))
    }

    @Test fun legacySaveKeepsLogAndStartsAnEmptyStructuredArchive() {
        val state = GameState(logs = mutableListOf("[Tag 1 · 06:00] Schicht begonnen"))
        val json = GameStore.toJson(state).apply { remove("radioArchive") }
        val restored = GameStore.fromJson(json)
        assertEquals(state.logs, restored.logs)
        assertTrue(restored.radioArchive.isEmpty())
    }
}
