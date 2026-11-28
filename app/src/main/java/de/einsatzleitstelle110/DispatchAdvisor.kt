package de.einsatzleitstelle110

enum class DispatchMode(val label: String) {
    FASTEST("Schnellste Hilfe"),
    BALANCED("Ausgewogen"),
    ECONOMICAL("Wirtschaftlich"),
    MANUAL("Manuell");

    fun forPriority(priority: Int): DispatchMode =
        if (priority == 1 && this != MANUAL) FASTEST else this
}

data class DispatchPick(val unitId: String, val role: String, val eta: Int, val fee: Int)
data class DispatchAdvice(val picks: List<DispatchPick>, val missing: List<String>, val warnings: List<String>) {
    val fee: Int get() = picks.sumOf { it.fee }
    val arrival: Int? get() = picks.maxOfOrNull { it.eta }
}

/** Read-only planning: never reserves forces, changes the RNG, spends money or redirects a unit. */
object DispatchAdvisor {
    private data class Assignment(val roleIndex: Int, val unit: UnitState)

    fun plan(
        game: GameController,
        call: IncidentState,
        specialRights: Boolean = call.priority == 1,
        mode: DispatchMode = game.state.dispatchMode,
    ): DispatchAdvice {
        val strategy = mode.forPriority(call.priority)
        if (strategy == DispatchMode.MANUAL || game.state.phase != "RUNNING" ||
            call.status !in listOf("OPEN", "DRIVING", "WORKING") || game.remainingTypes(call).isEmpty()
        ) return DispatchAdvice(emptyList(), emptyList(), emptyList())

        val roles = IncidentWork.pendingRoles(call)
        val credited = IncidentWork.creditedUnits(call)
        val fleet = game.state.units.filter { it.region == call.region && it.id !in credited }.distinctBy { it.id }
        val byId = fleet.associateBy { it.id }
        val assigned = call.assignedUnits.distinct().mapNotNull(byId::get)
        val assignedIds = assigned.map { it.id }.toSet()
        val free = fleet.filter { it.id !in assignedIds && game.dispatchable(it) }
        val available = free.filter { u -> roles.any { game.declaresCapability(u, it) } }
        val eligible = available.filter { u -> roles.any { game.canHandle(u, it) } }.sortedBy { it.id }
        val etas = eligible.associate { it.id to game.eta(it, call, specialRights) }
        val fees = eligible.associate { it.id to game.deploymentCost(game.deploymentType(it, call)) }
        val budget = if (game.state.unlimitedBudget) Long.MAX_VALUE else game.state.budget.toLong().coerceAtLeast(0)
        // Prefer using already assigned resources before adding extra free vehicles at the same fee.
        val extraPenalty = (((etas.values.maxOrNull() ?: 0).coerceAtMost(1_000_000) + 6L) *
            minOf(roles.size, assigned.size + eligible.size) + 1).coerceAtMost(1_000_000_000).toInt()

        fun match(etaLimit: Int): List<Assignment> {
            val candidates = assigned + eligible.filter { etas.getValue(it.id) <= etaLimit }
            return RoleMatching.assign(roles, candidates, game::canHandle,
                preference = { u, role ->
                    if (u.id in assignedIds) 0
                    else extraPenalty + etas.getValue(u.id).coerceAtMost(1_000_000) + if (u.type == role) 0 else 6
                },
                fee = { fees[it.id] ?: 0 },
                budget = budget,
            ).map { Assignment(it.role, candidates[it.unit]) }
        }

        val maximumCoverage = match(Int.MAX_VALUE)
        val warnings = mutableListOf<String>()
        if (available.size != eligible.size)
            warnings.add("Ausbildung, Besatzung oder Ausstattung fehlt bei ${available.size - eligible.size} verfügbaren Einheiten.")

        val timeLeft = when {
            IncidentWork.enabled(call) -> IncidentWork.arrivalWindow(call) ?: Int.MAX_VALUE
            call.status != "WORKING" -> (call.deadline - call.age).coerceAtLeast(0)
            call.waitSince >= 0 -> (45 - (call.age - call.waitSince)).coerceAtLeast(0)
            else -> Int.MAX_VALUE
        }
        val matches = when (strategy) {
            DispatchMode.FASTEST -> quickestMatch(etas.values, maximumCoverage, ::match)
            DispatchMode.BALANCED -> {
                val onTime = if (timeLeft == Int.MAX_VALUE) maximumCoverage else match(timeLeft)
                if (onTime.size == maximumCoverage.size) onTime
                else quickestMatch(etas.values, maximumCoverage, ::match)
            }
            DispatchMode.ECONOMICAL, DispatchMode.MANUAL -> maximumCoverage
        }
        val picks = matches.filter { it.unit.id !in assignedIds }.map {
            DispatchPick(it.unit.id, roles[it.roleIndex], etas.getValue(it.unit.id), fees.getValue(it.unit.id))
        }
        val covered = matches.map { it.roleIndex }.toSet()
        val missing = roles.filterIndexed { i, _ -> i !in covered }
        if (missing.isNotEmpty()) {
            val withoutBudget = RoleMatching.assign(roles, assigned + eligible, game::canHandle)
            if (withoutBudget.size > matches.size) warnings.add("Budget begrenzt den Vorschlag. Verfügbar sind $budget €.")
            if (withoutBudget.size < roles.size) warnings.add("Nicht alle benötigten Kräfte sind frei und passend ausgebildet.")
        }
        if (picks.any { it.eta > timeLeft })
            warnings.add("Ankunft nach Frist. Schnellere Kräfte oder Sonderrechte prüfen.")
        if (call.addressUncertain) warnings.add("Adresse unbestätigt. Ein Rückruf kann den Anfahrtsweg ändern.")
        if (picks.isNotEmpty()) {
            val proposed = picks.map { it.unitId }.toSet()
            val patrols = free.filter { game.canHandle(it, "STREIFE") }
            if (patrols.isNotEmpty() && patrols.all { it.id in proposed }) {
                warnings.add("Nach der Entsendung bleibt in der Zuständigkeit keine freie Streife.")
            } else {
                val areaNames = OperationsAreas.options(call.region).toMap()
                patrols.filter { it.scope != "STATE" }.groupBy { it.scope }.forEach { (scope, units) ->
                    val name = areaNames[scope]
                    if (name != null && units.all { it.id in proposed })
                        warnings.add("$name: Die letzte für den Bezirk eingeteilte freie Streife würde ausrücken.")
                }
            }
        }
        return DispatchAdvice(picks, missing, warnings)
    }

    /** Find the earliest arrival cutoff that retains maximum coverage within the real budget. */
    private fun quickestMatch(
        etas: Collection<Int>,
        maximum: List<Assignment>,
        match: (Int) -> List<Assignment>,
    ): List<Assignment> {
        if (maximum.isEmpty() || etas.isEmpty()) return maximum
        val limits = etas.distinct().sorted()
        var lower = 0
        var upper = limits.lastIndex
        var best = maximum
        while (lower <= upper) {
            val index = (lower + upper) / 2
            val candidate = match(limits[index])
            if (candidate.size == maximum.size) {
                best = candidate
                upper = index - 1
            } else lower = index + 1
        }
        return best
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
        val arrivalWindow = IncidentWork.arrivalWindow(call)
        val left = arrivalWindow ?: (call.deadline - call.age).coerceAtLeast(0)
        val missing = game.remainingTypes(call).size
        val arrival = call.assignedUnits.mapNotNull { id -> game.state.units.find { it.id == id } }
            .filter { it.status == "DRIVING" }.maxOfOrNull { it.travelLeft + it.readinessLeft }
        if ((call.status != "WORKING" || arrivalWindow != null) && (left == 0 || arrival != null && arrival > left))
            return IncidentAttention(0, left, "Frist gefährdet · noch $left Min")
        if (missing > 0) return IncidentAttention(1, left,
            if (call.assignedUnits.isEmpty()) "$missing Kräfte fehlen · noch $left Min" else "Verstärkung fehlt · $missing Kräfte" + (arrivalWindow?.let { " · $it Min" } ?: ""))
        val tasks = IncidentWork.tasks(call)
        val status = when {
            call.status != "WORKING" -> "Kräfte auf Anfahrt · ${arrival ?: 0} Min"
            tasks.isNotEmpty() -> "${tasks.count(IncidentTask::done)}/${tasks.size} Aufgaben erledigt"
            else -> "Kräfte arbeiten vor Ort"
        }
        return IncidentAttention(2, left, status)
    }

    fun ordered(game: GameController, calls: List<IncidentState>): List<IncidentState> {
        val attention = calls.associate { it.id to attention(game, it) }
        return calls.sortedWith(compareBy<IncidentState>({ attention.getValue(it.id).order }, { it.priority },
            { attention.getValue(it.id).minutes }, { it.id }))
    }
}
