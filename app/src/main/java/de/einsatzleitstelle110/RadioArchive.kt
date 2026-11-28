package de.einsatzleitstelle110

/** A transmitted simulation message, including its dispatch context at the time of transmission. */
data class RadioEntry(
    val day: Int,
    val clock: String,
    val title: String,
    val message: String,
    val priority: RadioPriority,
    val callsign: String? = null,
    val organisation: String? = null,
    val incidentId: String? = null,
) {
    fun lines() = RadioScript.event(title, message, callsign)
}

object RadioArchive {
    const val MAX_ENTRIES = 2_000

    fun filter(entries: List<RadioEntry>, query: String, priority: RadioPriority?): List<RadioEntry> {
        val terms = query.trim().splitToSequence(Regex("\\s+")).filter { it.isNotEmpty() }.toList()
        return entries.asReversed().filter { entry ->
            (priority == null || entry.priority == priority) && terms.all { term ->
                listOf(entry.title, entry.message, entry.clock, entry.callsign, entry.organisation, entry.incidentId)
                    .any { field -> field?.contains(term, ignoreCase = true) == true }
            }
        }
    }

    fun label(priority: RadioPriority) = when (priority) {
        RadioPriority.URGENT -> "Dringend"
        RadioPriority.INCIDENT -> "Einsatzbezogen"
        RadioPriority.OPERATIONS -> "Organisatorisch"
        RadioPriority.ROUTINE -> "Routine"
    }
}
