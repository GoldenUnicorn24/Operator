package de.einsatzleitstelle110

/** One crew works one required role at a time. The incident retains work already done on each role. */
data class IncidentTask(
    val role: String,
    val minutesLeft: Int,
    val unitId: String = "",
    val requestedAtAge: Int = -1,
) {
    val done get() = minutesLeft == 0
}

internal object IncidentWork {
    fun enabled(call: IncidentState) = call.tasks.isNotEmpty() ||
        (call.requiredTypes.size > 1 && call.status in listOf("OPEN", "DRIVING", "WORKING", "HANDED"))

    /** Match role occurrences in order. Added requirements get their own arrival window. */
    fun tasks(call: IncidentState): List<IncidentTask> {
        if (!enabled(call)) return emptyList()
        val previous = call.tasks.groupBy(IncidentTask::role).mapValues { it.value.iterator() }
        val requested = if (call.tasks.isNotEmpty() && call.firstArrival >= 0) call.age else -1
        return call.requiredTypes.map { role ->
            val sameRole = previous[role]
            if (sameRole?.hasNext() == true) sameRole.next()
            else IncidentTask(role, call.duration, requestedAtAge = requested)
        }
    }

    fun pendingRoles(call: IncidentState): List<String> =
        if (enabled(call)) tasks(call).filterNot(IncidentTask::done).map(IncidentTask::role) else call.requiredTypes

    fun creditedUnits(call: IncidentState): Set<String> =
        if (call.tasks.isEmpty()) emptySet() else tasks(call).filter(IncidentTask::done).mapTo(mutableSetOf(), IncidentTask::unitId)

    fun arrivalDeadline(call: IncidentState, task: IncidentTask): Int =
        if (task.requestedAtAge >= 0) maxOf(call.deadline, task.requestedAtAge + 45) else call.deadline

    fun arrivalWindow(call: IncidentState): Int? =
        tasks(call).filter { !it.done && it.unitId.isBlank() }
            .minOfOrNull { (arrivalDeadline(call, it) - call.age).coerceAtLeast(0) }

    fun changeDuration(call: IncidentState, delta: Int) {
        call.tasks = call.tasks.map { task ->
            if (task.done) task else task.copy(minutesLeft = (task.minutesLeft + delta).coerceIn(1, 600))
        }
    }

    /** Arrived crews start their own tasks. Matching retains progress when a crew is replaced. */
    fun advance(call: IncidentState, scene: List<UnitState>, canHandle: (UnitState, String) -> Boolean): List<IncidentTask> {
        val before = tasks(call)
        val oldSave = call.tasks.isEmpty() && call.status == "WORKING"
        val credited = before.filter(IncidentTask::done).mapTo(mutableSetOf(), IncidentTask::unitId)
        val freeCrew = scene.filter { it.id !in credited }.sortedBy { it.id }
        val open = before.indices.filter { !before[it].done }
        val incumbents = open.map { before[it] }.groupBy(IncidentTask::role)
        val finishedNow = mutableListOf<IncidentTask>()
        val matches = RoleMatching.assign(open.map { before[it].role }, freeCrew, canHandle, preference = { unit, role ->
            when {
                incumbents[role]?.any { it.unitId == unit.id } == true -> 0
                unit.type == role -> 1
                else -> 2
            }
        }).associate { open[it.role] to freeCrew[it.unit] }
        call.tasks = before.mapIndexed { index, task ->
            val crew = matches[index]
            when {
                task.done -> task
                crew == null -> if (task.unitId.isEmpty()) task else task.copy(unitId = "")
                else -> {
                    val work = if (oldSave && crew.status == "WORKING") minOf(task.minutesLeft, crew.workLeft)
                        else task.minutesLeft
                    task.copy(minutesLeft = (work - 1).coerceAtLeast(0), unitId = crew.id).also {
                        if (work == 1) finishedNow.add(it)
                    }
                }
            }
        }
        val worked = call.tasks.filter { it.unitId.isNotEmpty() }.associateBy(IncidentTask::unitId)
        for (crew in scene) {
            val task = worked[crew.id]
            crew.status = if (task != null) "WORKING" else "ON_SCENE"
            crew.workLeft = task?.minutesLeft ?: 0
        }
        if (worked.isNotEmpty()) call.status = "WORKING"
        return finishedNow
    }

    fun label(role: String) = when (role) {
        "STREIFE" -> "Lage sichern"
        "VERKEHR" -> "Verkehr absichern"
        "RETTUNG" -> "Medizinische Versorgung"
        "FEUERWEHR" -> "Technische Hilfe"
        "KRIPO" -> "Sachverhalt ermitteln"
        "HUND", "HELI" -> "Personensuche und Erkundung"
        "BEREITSCHAFT" -> "Absicherung unterstützen"
        "SEK", "MEK" -> "Spezialauftrag bearbeiten"
        else -> "Einsatzauftrag bearbeiten"
    }
}
