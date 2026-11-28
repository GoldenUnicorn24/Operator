package de.einsatzleitstelle110

internal enum class RadioAction(val label: String) {
    ACKNOWLEDGE("Meldung quittieren"),
    REQUEST_STATUS("Aktuellen Status anfordern"),
    OPEN_INCIDENT("Einsatzakte öffnen"),
}

/** Commands are resolved against current assignments. Old radio traffic cannot address a reassigned vehicle. */
internal object RadioOperations {
    fun incident(game: GameController, entry: RadioEntry): IncidentState? =
        game.state.incidents.firstOrNull { it.id == entry.incidentId && it.region == game.state.region }

    fun actions(game: GameController, entry: RadioEntry): List<RadioAction> {
        val call = incident(game, entry) ?: return emptyList()
        val allowed = mutableListOf(RadioAction.OPEN_INCIDENT)
        if (game.state.phase != "RUNNING" || call.status !in listOf("OPEN", "DRIVING", "WORKING") ||
            game.state.radioArchive.none { it === entry }
        ) return allowed
        if (!entry.acknowledged && entry.title != "Funk quittiert") allowed.add(RadioAction.ACKNOWLEDGE)
        if (assignedUnit(game, entry, call) != null && !hasStatusThisMinute(game, entry))
            allowed.add(RadioAction.REQUEST_STATUS)
        return allowed
    }

    fun acknowledge(game: GameController, entry: RadioEntry): Boolean {
        if (RadioAction.ACKNOWLEDGE !in actions(game, entry)) return false
        val entries = game.state.radioArchive
        val position = entries.indexOfFirst { it === entry }
        entries[position] = entry.copy(acknowledged = true)
        val call = requireNotNull(incident(game, entry))
        game.emit("Funk quittiert", "${call.id} · ${entry.callsign ?: "Leitstelle"} · ${entry.title}", incident = call)
        return true
    }

    fun requestStatus(game: GameController, entry: RadioEntry): Boolean {
        if (RadioAction.REQUEST_STATUS !in actions(game, entry)) return false
        val call = requireNotNull(incident(game, entry))
        val unit = requireNotNull(assignedUnit(game, entry, call))
        val report = when (unit.status) {
            "DRIVING" -> if (unit.readinessLeft > 0) "Ausrücken läuft · ${unit.readinessLeft + unit.travelLeft} Min bis vor Ort"
                else "Auf Anfahrt · ${unit.travelLeft} Min bis vor Ort"
            "WORKING" -> {
                val task = call.tasks.firstOrNull { it.unitId == unit.id }
                when {
                    task?.done == true -> "${IncidentWork.label(task.role)} erledigt · vor Ort"
                    task != null -> "${IncidentWork.label(task.role)} · noch ${task.minutesLeft} Min"
                    else -> "In Bearbeitung · noch ${unit.workLeft} Min"
                }
            }
            else -> "Vor Ort · bereit für weitere Aufgaben"
        }
        game.emit("Statusrückmeldung", "${unit.callsign} · FMS ${game.fms(unit)} · $report", unit = unit, incident = call)
        return true
    }

    private fun assignedUnit(game: GameController, entry: RadioEntry, call: IncidentState): UnitState? =
        game.state.units.firstOrNull {
            it.id == entry.unitId && it.incidentId == call.id && it.id in call.assignedUnits &&
                it.status in listOf("DRIVING", "ON_SCENE", "WORKING")
        }

    private fun hasStatusThisMinute(game: GameController, entry: RadioEntry): Boolean {
        val today = game.state.day
        val time = game.clock()
        return game.state.radioArchive.asReversed().takeWhile { it.day == today && it.clock == time }.any {
            it.title == "Statusrückmeldung" && it.unitId == entry.unitId && it.incidentId == entry.incidentId
        }
    }
}
