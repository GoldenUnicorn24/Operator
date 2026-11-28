package de.einsatzleitstelle110

import kotlin.math.roundToInt

/** Stable value projection for the open incident file. It does not compute routes or hold state references. */
internal data class DispatchPanelState(
    val phase: String,
    val budget: Int,
    val weather: String,
    val hour: Int,
    val status: String,
    val title: String,
    val location: String,
    val position: GeoPoint,
    val priority: Int,
    val description: String,
    val minutesLeft: Int,
    val decision: String,
    val decisionTime: Int,
    val outcome: String,
    val callerCount: Int,
    val disconnected: Boolean,
    val addressUncertain: Boolean,
    val callbackDone: Boolean,
    val notes: List<String>,
    val transcript: List<String>,
    val timing: List<Long>,
    val required: List<String>,
    val tasks: List<IncidentTask>,
    val assigned: List<UnitRow>,
    val candidates: List<UnitRow>,
) {
    data class UnitRow(
        val id: String,
        val name: String,
        val type: String,
        val scope: String,
        val status: String,
        val fms: Int,
        val crew: Int,
        val energy: Int,
        val eta: Int,
        val workLeft: Int,
        val departureDelay: Int,
        val positionBucket: Pair<Int, Int>,
        val speed: Double,
        val roles: List<String>,
        val boundTo: String,
    )

    companion object {
        fun capture(game: GameController, call: IncidentState, showBound: Boolean): DispatchPanelState {
            val state = game.state
            val actionable = state.phase == "RUNNING" && call.status in listOf("OPEN", "DRIVING", "WORKING")
            val assignedIds = call.assignedUnits.toSet()
            val regionalUnits = state.units.filter { it.region == call.region }
            val byId = regionalUnits.associateBy { it.id }
            val sourceCalls = if (actionable && showBound) state.incidents.associateBy { it.id } else emptyMap()

            fun row(unit: UnitState, assigned: Boolean = false, source: IncidentState? = null) =
                UnitRow(unit.id, unit.callsign, unit.type, unit.scope, unit.status, game.fms(unit), unit.officers.size,
                    unit.officers.minOfOrNull { it.energy } ?: 100, if (assigned) unit.travelLeft else 0,
                    if (assigned) unit.workLeft else 0, if (assigned) unit.readinessLeft else 0,
                    if (assigned) 0 to 0 else (unit.lon * 1000).roundToInt() to (unit.lat * 1000).roundToInt(),
                    unit.vehicleSpeed, call.requiredTypes.filter { game.canHandle(unit, it) },
                    source?.let { "${it.id} · ${it.title} · ${it.priority}" }.orEmpty())

            val free = if (actionable) regionalUnits.filter { it.id !in assignedIds && game.dispatchable(it) }.map { row(it) }
                else emptyList()
            val bound = if (sourceCalls.isEmpty()) emptyList() else regionalUnits.mapNotNull { u ->
                val source = sourceCalls[u.incidentId] ?: return@mapNotNull null
                if (source.id == call.id || source.priority < call.priority || u.status !in listOf("DRIVING", "ON_SCENE"))
                    return@mapNotNull null
                row(u, source = source)
            }
            return DispatchPanelState(
                state.phase, if (actionable) state.budget else 0, if (actionable) state.weather else "",
                if (actionable) game.hour() else 0,
                call.status, call.title, call.location, GeoPoint(call.lon, call.lat), call.priority, call.description,
                if (call.status in listOf("OPEN", "DRIVING")) (call.deadline - call.age).coerceAtLeast(0) else 0,
                call.pending, if (call.pending.isBlank()) 0 else call.age - call.pendingAge, call.outcome,
                call.duplicateCallers, call.disconnected, call.addressUncertain, call.callbackDone,
                call.notes.toList(), call.phoneTranscript.toList(),
                listOf(call.reportedAt, call.answeredAt, call.firstDispatchedAt, call.phoneClosedAt),
                call.requiredTypes.toList(), IncidentWork.tasks(call),
                call.assignedUnits.mapNotNull(byId::get).map { row(it, assigned = true) }, free + bound,
            )
        }
    }
}
