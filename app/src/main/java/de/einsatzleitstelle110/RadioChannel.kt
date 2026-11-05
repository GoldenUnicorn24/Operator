package de.einsatzleitstelle110

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

/**
 * The police radio that never stops: a mixer thread plays constant low static and every
 * transmission on top. Speech is rendered to a file by the device voice, then bent by [RadioDsp].
 */
class RadioChannel(private val context: Context) {
    @Volatile var volume = .7f
    /** Lowered during a phone call, as a real call taker would turn the radio down. */
    @Volatile var ducked = false
    @Volatile private var running = false
    private var thread: Thread? = null
    private val ready = ConcurrentLinkedQueue<ShortArray>()
    private val pending = ArrayDeque<RadioLine>()
    private val rnd = java.util.Random()
    private var tts: TextToSpeech? = null
    private var voiceOk = false
    private var busyUntil = 0L
    private var nextAmbient = 0L
    private var closed = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val r = engine.setLanguage(Locale.GERMANY)
                voiceOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) {}
                    override fun onDone(id: String) = finished(id)
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String) = finished(id)
                })
            }
        }
    }

    private fun file(id: String) = File(context.cacheDir, "funk-$id.wav")

    private fun finished(id: String) {
        val f = file(id)
        try {
            val pcm = if (f.isFile) RadioDsp.parseWav(f.readBytes()) else null
            if (pcm != null && pcm.samples.size > 800) ready.add(RadioDsp.transmission(RadioDsp.resample(pcm), rnd.nextLong()))
            else ready.add(RadioDsp.mumble(1.4, rnd.nextLong()))
        } catch (_: Exception) {
        } finally {
            f.delete()
            synchronized(pending) { busyUntil = 0 }
        }
    }

    fun start() {
        if (running || closed) return
        running = true
        nextAmbient = SystemClock.uptimeMillis() + 2500
        thread = Thread({ mix() }, "polizeifunk").apply { priority = Thread.NORM_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread = null
    }

    fun close() {
        closed = true
        stop()
        tts?.shutdown()
        tts = null
    }

    /** Real events go first; routine traffic is dropped when the channel is busy. */
    fun say(lines: List<RadioLine>) {
        if (lines.isEmpty()) return
        synchronized(pending) {
            while (pending.size > 5) pending.removeFirst()
            pending.addAll(lines)
        }
    }

    /** Called from the game clock: starts the next synthesis and keeps routine traffic flowing. */
    fun pump(units: () -> List<String>) {
        if (!running) return
        val now = SystemClock.uptimeMillis()
        synchronized(pending) {
            if (now < busyUntil || ready.size >= 2) return
            if (pending.isEmpty() && now >= nextAmbient) {
                nextAmbient = now + 5000 + rnd.nextInt(9000)
                pending.addAll(RadioScript.ambient({ rnd.nextDouble() }, units()))
            }
            val line = pending.removeFirstOrNull() ?: return
            val engine = tts
            if (engine == null || !voiceOk) {
                ready.add(RadioDsp.mumble(1.0 + rnd.nextDouble() * 1.6, rnd.nextLong()))
                return
            }
            val id = "${now}_${rnd.nextInt(9999)}"
            val pitchSeed = abs(line.speaker.hashCode())
            engine.setPitch(if (line.speaker == RadioScript.CONTROL) 1.08f else .78f + (pitchSeed % 30) / 100f)
            engine.setSpeechRate(1.12f + (pitchSeed % 15) / 100f)
            val code = engine.synthesizeToFile(RadioScript.spoken(line.text), Bundle(), file(id), id)
            busyUntil = if (code == TextToSpeech.SUCCESS) now + 7000 else 0
        }
    }

    private fun mix() {
        val rate = RadioDsp.RATE
        val size = (AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)).coerceAtLeast(rate / 4)
        val track =
            try {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(size * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                running = false
                return
            }
        val chunk = ShortArray(rate / 25)
        var current: ShortArray? = null
        var at = 0
        var gap = 0
        var noise = 0f
        try {
            track.play()
            while (running) {
                val gain = volume * if (ducked) .28f else 1f
                for (i in chunk.indices) {
                    if (current == null && gap <= 0) {
                        current = ready.poll()
                        at = 0
                    }
                    // Soft low-passed static between transmissions, so the channel is always audible.
                    noise = noise * .8f + (rnd.nextFloat() - .5f) * .2f
                    var v = noise * .11f
                    val c = current
                    if (c != null) {
                        v = c[at++] / 32768f
                        if (at >= c.size) {
                            current = null
                            gap = rate * 3 / 10
                        }
                    } else if (gap > 0) gap--
                    chunk[i] = (v * gain * 32000).toInt().coerceIn(-32767, 32767).toShort()
                }
                if (track.write(chunk, 0, chunk.size) < 0) break
            }
        } catch (_: Exception) {
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }
}
