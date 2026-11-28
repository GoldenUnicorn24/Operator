package de.einsatzleitstelle110

import org.junit.Assert.assertEquals
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

    @Test fun legacySaveKeepsLogAndStartsAnEmptyStructuredArchive() {
        val state = GameState(logs = mutableListOf("[Tag 1 · 06:00] Schicht begonnen"))
        val json = GameStore.toJson(state).apply { remove("radioArchive") }
        val restored = GameStore.fromJson(json)
        assertEquals(state.logs, restored.logs)
        assertTrue(restored.radioArchive.isEmpty())
    }
}
