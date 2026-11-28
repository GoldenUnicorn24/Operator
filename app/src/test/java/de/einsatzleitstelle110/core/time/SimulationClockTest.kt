package de.einsatzleitstelle110.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationClockTest {
    private fun tempo(speed: Int = 1, talking: Boolean = false, waiting: Boolean = false, decision: Boolean = false) =
        SimulationTempo.from(speed, talking, waiting, decision)

    @Test fun displayedTempoAndPerMinuteDurationUseTheSameSpeed() {
        val cases = listOf(
            Triple(tempo(16), "×16", 312.5),
            Triple(tempo(16, talking = true, waiting = true, decision = true), "×0,5", 10_000.0),
            Triple(tempo(16, waiting = true, decision = true), "×2", 2_500.0),
            Triple(tempo(16, decision = true), "×4", 1_250.0),
            Triple(tempo(1, waiting = true, decision = true), "×1", 5_000.0),
        )
        for ((speed, label, interval) in cases) {
            assertEquals(label, speed.label)
            assertEquals(interval, speed.millisecondsPerMinute, 0.0)
        }
    }

    @Test fun unevenCadenceStillAdvancesAtTheSelectedRate() {
        val clock = SimulationClock()
        val speed = tempo(4)
        var minutes = 0
        for (time in listOf(0L, 220, 499, 1001, 1249)) minutes += clock.poll(time, true, speed)
        assertEquals(0, minutes)
        assertEquals(1, clock.poll(1250, true, speed))
        assertEquals(2, clock.poll(2000, true, tempo(16)))
    }

    @Test fun pauseStopsElapsedTimeAndNewGameClearsFraction() {
        val clock = SimulationClock()
        val speed = tempo(4)
        assertEquals(0, clock.poll(0, true, speed))
        assertEquals(0, clock.poll(950, true, speed))
        assertEquals(0, clock.poll(1000, false, speed))
        assertEquals(0, clock.poll(60200, true, speed))
        assertEquals(1, clock.poll(60500, true, speed))
        assertEquals(0, clock.poll(61450, true, speed))
        clock.reset()
        assertEquals(0, clock.poll(61500, true, speed))
        assertEquals(0, clock.poll(61800, true, speed))
    }

    @Test fun delayedTickLimitsTheCatchUpWork() {
        val clock = SimulationClock()
        val speed = tempo(16)
        assertEquals(0, clock.poll(0, true, speed))
        assertEquals(3, clock.poll(8000, true, speed))
        assertEquals(1, clock.poll(8250, true, speed))
    }
}
