package de.einsatzleitstelle110

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeRadioQueueTest {
    @Test
    fun routineOverflowDoesNotDiscardUrgentOrIncidentTraffic() {
        val queue = RadioQueue(routineLimit = 2)
        val first = RadioScript.event("Nachforderung", "Erste Lage").single()
        val second = RadioScript.event("Lageverschärfung", "Zweite Lage").single()
        val departure = RadioScript.event("Einheit unterwegs", "Peter 24/1 · 8")
        queue.add(first)
        departure.forEach(queue::add)
        val routine = (1..40).map { RadioLine(listOf(VoiceClip("r0", "Routine $it"))) }
        routine.forEach(queue::add)
        queue.add(second)

        assertEquals(first, queue.poll())
        assertEquals(second, queue.poll())
        val interrupted = queue.poll()!!
        queue.requeue(interrupted)
        departure.forEach { assertEquals(it, queue.poll()) }
        routine.takeLast(2).forEach { assertEquals(it, queue.poll()) }
        assertNull(queue.poll())
    }
}
