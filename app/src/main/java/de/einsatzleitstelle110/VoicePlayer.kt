package de.einsatzleitstelle110

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Bundled clips take precedence. Dynamic lines can use an installed offline German voice. */
class VoicePlayer(context: Context) {
    private val app = context.applicationContext
    private val assets = app.assets
    private val main = Handler(Looper.getMainLooper())
    private val available = try { assets.list("voices").orEmpty().toSet() } catch (_: Exception) { emptySet() }
    private val queue = ArrayDeque<VoiceClip>()
    private var player: MediaPlayer? = null
    private var speech: TextToSpeech? = null
    private var voiceStatus = VoiceStatus.NOT_STARTED
    private var utterance: String? = null
    private var nextUtterance = 0L
    private var closed = false

    private enum class VoiceStatus { NOT_STARTED, STARTING, READY, UNAVAILABLE }

    var volume = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            try { player?.setVolume(field, field) } catch (_: IllegalStateException) {}
        }
    val isPlaying get() = player != null || utterance != null || queue.isNotEmpty()

    fun play(clips: List<VoiceClip>, flush: Boolean = false) {
        if (closed) return
        if (flush) stop()
        queue.addAll(clips)
        pump()
    }

    private fun pump() {
        if (player != null || utterance != null) return
        if (volume == 0f) {
            queue.clear()
            return
        }
        while (queue.isNotEmpty()) {
            val clip = queue.first()
            val name = "${clip.id}.ogg"
            if (name in available && playAsset(name)) {
                queue.removeFirst()
                return
            }
            if (voiceStatus == VoiceStatus.NOT_STARTED) startOfflineVoice()
            if (voiceStatus == VoiceStatus.STARTING) return
            queue.removeFirst()
            if (voiceStatus == VoiceStatus.READY && speak(clip.text)) return
        }
    }

    private fun playAsset(name: String): Boolean {
        return try {
            val audio = MediaPlayer()
            player = audio
            audio.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            assets.openFd("voices/$name").use { audio.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            audio.setVolume(volume, volume)
            audio.setOnPreparedListener { if (player === it) it.start() }
            audio.setOnCompletionListener(::assetFinished)
            audio.setOnErrorListener { media, _, _ -> assetFinished(media); true }
            audio.prepareAsync()
            true
        } catch (_: Exception) {
            releaseAsset()
            false
        }
    }

    private fun assetFinished(audio: MediaPlayer) {
        if (player !== audio) return
        releaseAsset()
        pump()
    }

    private fun startOfflineVoice() {
        voiceStatus = VoiceStatus.STARTING
        try {
            speech = TextToSpeech(app) { result ->
                main.post {
                    if (closed || voiceStatus != VoiceStatus.STARTING) return@post
                    val engine = speech
                    val offline = if (result == TextToSpeech.SUCCESS) engine?.voices.orEmpty()
                        .filter { !it.isNetworkConnectionRequired && it.locale.language == Locale.GERMAN.language }
                        .sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale == Locale.GERMANY }
                            .thenByDescending { it.quality }.thenBy { it.name })
                        .firstOrNull() else null
                    voiceStatus = if (offline != null && engine?.setVoice(offline) == TextToSpeech.SUCCESS)
                        VoiceStatus.READY else VoiceStatus.UNAVAILABLE
                    pump()
                }
            }
            speech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = voiceFinished(id)
                @Deprecated("Legacy Android callback")
                override fun onError(id: String?) = voiceFinished(id)
            })
            main.postDelayed({
                if (voiceStatus == VoiceStatus.STARTING) {
                    voiceStatus = VoiceStatus.UNAVAILABLE
                    pump()
                }
            }, 3000)
        } catch (_: RuntimeException) {
            voiceStatus = VoiceStatus.UNAVAILABLE
        }
    }

    private fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        val id = "line-${nextUtterance++}"
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        if (speech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) return false
        utterance = id
        return true
    }

    private fun voiceFinished(id: String?) {
        main.post {
            if (utterance != null && utterance == id) {
                utterance = null
                pump()
            }
        }
    }

    private fun releaseAsset() {
        val media = player ?: return
        player = null
        media.release()
    }

    fun stop() {
        queue.clear()
        utterance = null
        speech?.stop()
        releaseAsset()
    }

    fun close() {
        closed = true
        stop()
        main.removeCallbacksAndMessages(null)
        speech?.shutdown()
        speech = null
    }
}
