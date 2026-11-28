package de.einsatzleitstelle110.core.time

/** Shared rules for the displayed tempo, simulation steps and per-minute animation. */
internal class SimulationTempo private constructor(val halfSteps: Int, val limit: Limit?) {
    enum class Limit(val description: String) {
        CONVERSATION("Laufendes Notrufgespräch"),
        RINGING("Wartender Notruf"),
        DECISION("Offene Entscheidung"),
    }

    val label: String
        get() = if (halfSteps == 1) "×0,5" else "×${halfSteps / 2}"

    val millisecondsPerMinute: Double
        get() = MILLIS_AT_HALF_SPEED.toDouble() / halfSteps

    companion object {
        /** One simulated minute takes five real seconds when playing at ×1. */
        const val MILLIS_AT_HALF_SPEED = 10_000L
        val speeds = listOf(1, 4, 8, 16)

        fun from(selected: Int, inConversation: Boolean, callWaiting: Boolean, decisionPending: Boolean): SimulationTempo {
            val chosen = selected.coerceIn(1, speeds.last())
            return when {
                inConversation -> SimulationTempo(1, Limit.CONVERSATION)
                callWaiting && chosen > 2 -> SimulationTempo(4, Limit.RINGING)
                decisionPending && chosen > 4 -> SimulationTempo(8, Limit.DECISION)
                else -> SimulationTempo(chosen * 2, null)
            }
        }
    }
}

/** Accumulates elapsed monotonic time. Long UI stalls never replay an unbounded burst of work. */
internal class SimulationClock {
    private var lastRunningUptimeMs: Long? = null
    private var fraction = 0L

    fun poll(uptimeMs: Long, running: Boolean, tempo: SimulationTempo): Int {
        val previous = lastRunningUptimeMs
        lastRunningUptimeMs = uptimeMs.takeIf { running }
        if (!running || previous == null) return 0

        val deltaMs = (uptimeMs - previous).coerceIn(0, MAX_CATCH_UP_MS)
        val progress = fraction + deltaMs * tempo.halfSteps
        fraction = progress % SimulationTempo.MILLIS_AT_HALF_SPEED
        return (progress / SimulationTempo.MILLIS_AT_HALF_SPEED).toInt()
    }

    fun pause() {
        lastRunningUptimeMs = null
    }

    fun reset() {
        pause()
        fraction = 0
    }

    private companion object {
        const val MAX_CATCH_UP_MS = 1_000L
    }
}
