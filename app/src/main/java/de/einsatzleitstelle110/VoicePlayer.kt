package de.einsatzleitstelle110

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** Plays bundled voice clips one after another. Clips that are not bundled are skipped. */
class VoicePlayer(context: Context, private val onIdle: () -> Unit = {}) {
    private val assets = context.applicationContext.assets
    private val available: Set<String> =
        try { assets.list("voices").orEmpty().toSet() } catch (_: Exception) { emptySet() }
    private val queue = ArrayDeque<String>()
    private var player: MediaPlayer? = null
    var volume = 1f
        set(value) {
            field = value
            try { player?.setVolume(value, value) } catch (_: IllegalStateException) {}
        }
    val isPlaying get() = player != null || queue.isNotEmpty()

    fun has(clip: VoiceClip) = "${clip.id}.ogg" in available

    fun play(clips: List<VoiceClip>, flush: Boolean = false) {
        if (flush) stop()
        queue.addAll(clips.map { "${it.id}.ogg" }.filter { it in available })
        if (player == null) next()
    }

    private fun next() {
        release()
        val name = queue.removeFirstOrNull()
        if (name == null) {
            onIdle()
            return
        }
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            assets.openFd("voices/$name").use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { next() }
            mp.setOnErrorListener { _, _, _ -> next(); true }
            mp.prepareAsync()
        } catch (_: Exception) {
            release()
            next()
        }
    }

    private fun release() {
        val mp = player ?: return
        player = null
        try { mp.reset() } catch (_: IllegalStateException) {}
        mp.release()
    }

    fun stop() {
        queue.clear()
        release()
    }
}
