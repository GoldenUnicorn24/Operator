package de.einsatzleitstelle110

data class DispatchPick(val unitId: String, val role: String, val eta: Int, val fee: Int)
data class DispatchAdvice(val picks: List<DispatchPick>, val missing: List<String>, val warnings: List<String>) {
    val fee: Int get() = picks.sumOf { it.fee }
    val arrival: Int? get() = picks.maxOfOrNull { it.eta }
}

/** Read-only planning: never reserves forces, changes the RNG, spends money or redirects a unit. */
object DispatchAdvisor {
    fun plan(game: GameController, call: IncidentState, specialRights: Boolean = call.priority == 1): DispatchAdvice {
        if (call.status !in listOf("OPEN", "DRIVING", "WORKING") || game.state.phase != "RUNNING")
            return DispatchAdvice(emptyList(), emptyList(), emptyList())
        val needs = game.remainingTypes(call)
        if (needs.isEmpty()) return DispatchAdvice(emptyList(), emptyList(), emptyList())
        val roles = call.requiredTypes
        val assigned = call.assignedUnits.distinct().mapNotNull { id -> game.state.units.find { it.id == id } }
        val assignedIds = assigned.map { it.id }.toSet()
        val available = game.state.units.filter { u ->
            u.region == call.region && u.id !in assignedIds && game.dispatchable(u) && roles.any { game.declaresCapability(u, it) }
        }.distinctBy { it.id }.sortedBy { it.id }
        val eligible = available.filter { u -> roles.any { game.canHandle(u, it) } }
        val units = assigned + eligible
        val etas = eligible.associate { it.id to game.eta(it, call, specialRights) }
        val budget = if (game.state.unlimitedBudget) Long.MAX_VALUE else game.state.budget.toLong().coerceAtLeast(0)
        // Prefer using already assigned resources before adding extra free vehicles at the same fee.
        val extraPenalty = (((etas.values.maxOrNull() ?: 0).coerceAtMost(1_000_000) + 6L) * minOf(roles.size, units.size) + 1)
            .coerceAtMost(1_000_000_000).toInt()
        fun fee(u: UnitState) = if (u.id in assignedIds) 0 else game.deploymentCost(game.deploymentType(u, call))
        val matches = RoleMatching.assign(roles, units, game::canHandle,
            { u, role -> if (u.id in assignedIds) 0 else extraPenalty + etas.getValue(u.id).coerceAtMost(1_000_000) + if (u.type == role) 0 else 6 },
            ::fee, budget)
        val picks = matches.filter { units[it.unit].id !in assignedIds }.map { m ->
            val u = units[m.unit]
            DispatchPick(u.id, roles[m.role], etas.getValue(u.id), fee(u))
        }
        val covered = matches.map { it.role }.toSet()
        val missing = roles.filterIndexed { i, _ -> i !in covered }
        val warnings = mutableListOf<String>()
        if (available.size != eligible.size) warnings.add("Ausbildung, Besatzung oder Ausstattung fehlt bei ${available.size - eligible.size} verfügbaren Einheiten.")
        if (missing.isNotEmpty()) {
            val withoutBudget = RoleMatching.assign(roles, units, game::canHandle)
            if (withoutBudget.size > matches.size) warnings.add("Budget begrenzt den Vorschlag; verfügbare Mittel ${budget} €.")
            if (withoutBudget.size < roles.size) warnings.add("Nicht alle benötigten Kräfte sind frei und passend ausgebildet.")
        }
        if (call.status != "WORKING" && picks.any { it.eta > call.deadline - call.age })
            warnings.add("Ankunft nach Frist: schnellere Kräfte oder Sonderrechte prüfen.")
        if (call.addressUncertain) warnings.add("Adresse unbestätigt: ein Rückruf kann den Anfahrtsweg ändern.")
        return DispatchAdvice(picks, missing, warnings)
    }
}

data class IncidentAttention(val order: Int, val minutes: Int, val reason: String) {
    val needsAction: Boolean get() = order < 2
}

/** One ordering for the map queue and incident desk; no routing work in this hot path. */
object IncidentDesk {
    fun attention(game: GameController, call: IncidentState): IncidentAttention {
        if (call.status !in listOf("OPEN", "DRIVING", "WORKING")) return IncidentAttention(3, 0, "Abgeschlossen")
        if (call.pending.isNotBlank()) {
            val left = (GameController.DECISION_TIMEOUT - (call.age - call.pendingAge)).coerceAtLeast(0)
            return IncidentAttention(0, left, "Entscheidung · noch $left Min")
        }
        val left = (call.deadline - call.age).coerceAtLeast(0)
        val missing = game.remainingTypes(call).size
        val arrival = call.assignedUnits.mapNotNull { id -> game.state.units.find { it.id == id } }
            .filter { it.status == "DRIVING" }.maxOfOrNull { it.travelLeft + it.readinessLeft }
        if (call.status != "WORKING" && (left == 0 || arrival != null && arrival > left))
            return IncidentAttention(0, left, "Frist gefährdet · noch $left Min")
        if (missing > 0) return IncidentAttention(1, left,
            if (call.assignedUnits.isEmpty()) "$missing Kräfte fehlen · noch $left Min" else "Verstärkung fehlt · $missing Kräfte")
        return IncidentAttention(2, left, if (call.status == "WORKING") "Kräfte arbeiten vor Ort" else "Kräfte auf Anfahrt · ${arrival ?: 0} Min")
    }

    fun ordered(game: GameController, calls: List<IncidentState>): List<IncidentState> {
        val attention = calls.associate { it.id to attention(game, it) }
        return calls.sortedWith(compareBy<IncidentState>({ attention.getValue(it.id).order }, { it.priority },
            { attention.getValue(it.id).minutes }, { it.id }))
    }
}
