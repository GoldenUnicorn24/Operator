package de.einsatzleitstelle110

enum class RadioPriority { URGENT, INCIDENT, OPERATIONS, ROUTINE }

data class RadioLine(val clips: List<VoiceClip>, val priority: RadioPriority = RadioPriority.ROUTINE)

/** Preserve FIFO within each priority. Only routine traffic may be replaced. */
class RadioQueue(private val routineLimit: Int = 12) {
    private val lanes = RadioPriority.entries.associateWith { ArrayDeque<RadioLine>() }

    init {
        require(routineLimit > 0)
    }

    fun add(line: RadioLine) {
        val lane = lanes.getValue(line.priority)
        if (line.priority == RadioPriority.ROUTINE && lane.size >= routineLimit) lane.removeFirst()
        lane.addLast(line)
    }

    fun requeue(line: RadioLine) {
        val lane = lanes.getValue(line.priority)
        if (line.priority == RadioPriority.ROUTINE && lane.size >= routineLimit) lane.removeLast()
        lane.addFirst(line)
    }

    fun poll(): RadioLine? =
        RadioPriority.entries.firstNotNullOfOrNull { lanes.getValue(it).removeFirstOrNull() }

    fun clear() = lanes.values.forEach { it.clear() }
}
