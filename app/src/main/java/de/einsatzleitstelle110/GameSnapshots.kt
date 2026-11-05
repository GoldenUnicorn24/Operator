package de.einsatzleitstelle110

/** Detached native value snapshot: no JSON, disk access or shared mutable collections. */
object GameSnapshots {
    private fun officer(o: Officer) =
        o.copy(
            equipment = o.equipment.toMutableSet(),
            owned = o.owned.toMutableSet(),
            qualifications = o.qualifications.toMutableSet(),
        )

    private fun stage(s: ChainStage) = s.copy(requiredTypes = s.requiredTypes.toList())

    fun capture(s: GameState): GameState =
        s.copy(
            units =
                s.units
                    .map { u ->
                        val roster = u.officers.map(::officer).toMutableList()
                        u.copy(
                            officer = roster.firstOrNull() ?: officer(u.officer),
                            officers = roster,
                            equipment = u.equipment.toMutableSet(),
                            routePoints = u.routePoints.toMutableList(),
                            reliefTeams =
                                u.reliefTeams
                                    .map { it.map(::officer).toMutableList() }
                                    .toMutableList(),
                        )
                    }
                    .toMutableList(),
            incidents =
                s.incidents
                    .map { i ->
                        i.copy(
                            requiredTypes = i.requiredTypes.toMutableList(),
                            assignedUnits = i.assignedUnits.toMutableList(),
                            chain = i.chain.map(::stage).toMutableList(),
                            stagesDone = i.stagesDone.toMutableList(),
                            flags = i.flags.toMutableSet(),
                            notes = i.notes.toMutableList(),
                            surprise = i.surprise.toMutableList(),
                        )
                    }
                    .toMutableList(),
            customTemplates =
                s.customTemplates
                    .map {
                        it.copy(
                            requiredTypes = it.requiredTypes.toList(),
                            chain = it.chain.map(::stage),
                        )
                    }
                    .toMutableList(),
            reports =
                s.reports.map { it.copy(trainingDone = it.trainingDone.toList()) }.toMutableList(),
            logs = s.logs.toMutableList(),
            phones = s.phones.map { it.copy(lines = it.lines.toMutableList(), asked = it.asked.toMutableSet(), advised = it.advised.toMutableSet()) }.toMutableList(),
            recentScripts = s.recentScripts.toMutableList(),
            unlockedRegions = s.unlockedRegions.toMutableSet(),
            depot = s.depot.toMutableMap(),
            chief = s.chief.deepCopy(),
        )
}
