package de.einsatzleitstelle110

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool

/** Original packaged PCM signals, native sound engine with independent channels and focus. */
class NativeAudio(context: Context) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
    private val sounds = mutableMapOf<String, Int>()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableSetOf<Int>()
    private var active = true
    private var focused = false
    private var closed = false
    var muted = false
    private var master = .7f
    private var radio = .7f
    private var sfx = .7f
    private var ambient = 0f
    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                focused = change == AudioManager.AUDIOFOCUS_GAIN
                if (!focused) stop()
            }
            .build()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        for (file in
            listOf(
                "incoming_call",
                "p1_alarm",
                "p2_alarm",
                "radio_squelch",
                "dispatch_ack",
                "unit_departure",
                "incident_success",
                "incident_failure",
                "shift_end",
                "ambient_dispatch",
            )) try {
            context.assets.openFd("audio/$file.wav").use { sounds[file] = pool.load(it, 1) }
        } catch (_: Exception) {}
    }

    fun setVolumes(master: Float, radio: Float, sfx: Float, ambient: Float) {
        this.master = master.coerceIn(0f, 1f)
        this.radio = radio.coerceIn(0f, 1f)
        this.sfx = sfx.coerceIn(0f, 1f)
        this.ambient = ambient.coerceIn(0f, 1f)
        if (this.master == 0f) stop()
    }

    private val lastPlayed = HashMap<String, Long>()
    private val recent = ArrayList<Long>()

    fun cue(name: String) {
        if (!active || muted || closed) return
        if (!focused)
            focused = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focused) return
        val key =
            when (name) {
                "CALL" -> "incoming_call"
                "P1" -> "p1_alarm"
                "P2" -> "p2_alarm"
                "DISPATCH" -> "dispatch_ack"
                "RADIO" -> "radio_squelch"
                "DEPARTURE" -> "unit_departure"
                "SUCCESS" -> "incident_success"
                "FAILED" -> "incident_failure"
                "SHIFT" -> "shift_end"
                "AMBIENT" -> "ambient_dispatch"
                else -> name
            }
        val id = sounds[key] ?: return
        if (id !in loaded) return
        // No stacking or spam: each cue at most every 1.5 s (radio 2.5 s, alarms 4 s), and at
        // most three cues in any two seconds.
        val now = android.os.SystemClock.uptimeMillis()
        val gap = when (key) { "radio_squelch" -> 2500L; "p1_alarm", "p2_alarm" -> 4000L; else -> 1500L }
        if (now - (lastPlayed[key] ?: 0L) < gap) return
        recent.removeAll { now - it > 2000 }
        if (recent.size >= 3 && key != "p1_alarm") return
        lastPlayed[key] = now
        recent.add(now)
        val volume =
            master *
                when (key) {
                    "radio_squelch",
                    "dispatch_ack" -> radio
                    "ambient_dispatch" -> ambient
                    else -> sfx
                }
        if (volume <= 0) return
        if (streams.size >= 12) stop()
        val stream = pool.play(id, volume, volume, 1, 0, 1f)
        if (stream > 0) streams.add(stream)
    }

    private fun stop() {
        for (id in streams) pool.stop(id)
        streams.clear()
    }

    fun pause() {
        active = false
        stop()
        if (focused) manager.abandonAudioFocusRequest(focus)
        focused = false
    }

    fun resume() {
        if (!closed) active = true
    }

    fun close() {
        pause()
        closed = true
        pool.release()
    }
}
