package de.einsatzleitstelle110

import android.content.Context
import android.os.SystemClock

/** The police radio: bundled transmissions with pauses in between, and no background noise. */
class RadioChannel(context: Context) {
    private val player = VoicePlayer(context)
    private val pending = ArrayDeque<RadioLine>()
    private val rnd = java.util.Random()
    private var running = false
    private var nextLine = 0L
    private var nextAmbient = 0L
    var volume = .7f
    /** Lowered during a phone call. */
    var ducked = false

    fun start() {
        running = true
        nextAmbient = SystemClock.uptimeMillis() + 2500
    }

    fun stop() {
        running = false
        player.stop()
    }

    fun close() = stop()

    fun say(lines: List<RadioLine>) {
        while (pending.size > 4) pending.removeFirst()
        pending.addAll(lines)
    }

    /** Called from the game clock on the main thread. */
    fun pump(units: () -> List<String>) {
        player.volume = volume * if (ducked) .25f else 1f
        if (!running || player.isPlaying) return
        val now = SystemClock.uptimeMillis()
        if (now < nextLine) return
        if (pending.isEmpty() && now >= nextAmbient) {
            nextAmbient = now + 4000 + rnd.nextInt(8000)
            pending.addAll(RadioScript.ambient({ rnd.nextDouble() }, units()))
        }
        val line = pending.removeFirstOrNull() ?: return
        player.play(line.clips)
        nextLine = now + 500
    }
}
