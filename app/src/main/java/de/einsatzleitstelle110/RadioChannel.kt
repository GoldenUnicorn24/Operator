package de.einsatzleitstelle110

import android.content.Context
import android.os.SystemClock

/** Playback follows the real simulation events, with a pause between transmissions. */
class RadioChannel(context: Context) {
    private val player = VoicePlayer(context)
    private val pending = RadioQueue()
    private var current: RadioLine? = null
    private var running = false
    private var nextLine = 0L
    var volume = .7f
    /** Lowered during a phone call. */
    var ducked = false

    fun start() {
        running = true
    }

    fun stop() {
        running = false
        if (player.isPlaying) current?.let(pending::requeue)
        current = null
        player.stop()
    }

    fun reset() {
        player.stop()
        current = null
        pending.clear()
        nextLine = 0L
    }

    fun close() {
        running = false
        reset()
        player.close()
    }

    fun say(lines: List<RadioLine>) = lines.forEach(pending::add)

    /** Called from the game clock on the main thread. */
    fun pump() {
        player.volume = volume * if (ducked) .45f else 1f
        if (!running || player.isPlaying) return
        val now = SystemClock.uptimeMillis()
        if (now < nextLine) return
        val line = pending.poll() ?: return
        current = line
        player.play(line.clips)
        nextLine = now + 500
    }
}
