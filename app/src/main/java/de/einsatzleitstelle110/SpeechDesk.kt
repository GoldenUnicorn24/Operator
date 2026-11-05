package de.einsatzleitstelle110

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlin.math.abs

/** Reads the phone call aloud with the German voice installed on the device, offline where possible. */
class SpeechDesk(context: Context) {
    private var tts: TextToSpeech? = null
    private var voices: List<Voice> = emptyList()
    var available = false
        private set
    var enabled = true
    private var counter = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val r = engine.setLanguage(Locale.GERMANY)
                available = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                voices = try {
                    engine.voices.orEmpty().filter { it.locale.language == "de" && !it.isNetworkConnectionRequired }.sortedBy { it.name }
                } catch (_: Exception) { emptyList() }
            }
        }
    }

    /** Stage directions in brackets are shown, not spoken. */
    fun clean(text: String) = text.replace(Regex("\\([^)]*\\)"), " ").replace(Regex("\\s+"), " ").trim()

    /** who: "D" dispatcher, otherwise the caller. Lines queue up in order. */
    fun say(line: String, script: CallScript, flush: Boolean = false) {
        val engine = tts ?: return
        if (!enabled || !available) return
        val who = line.substringBefore('|')
        if (who == "S") return
        val text = clean(line.substringAfter('|'))
        if (text.isBlank()) return
        val female = script.caller.startsWith("Frau") || script.caller.contains("Dame") || script.caller.contains("Frau") || script.caller.startsWith("Touristin")
        if (who == "D") {
            voices.firstOrNull()?.let { engine.voice = it }
            engine.setPitch(.95f)
            engine.setSpeechRate(1.1f)
        } else {
            if (voices.size > 1) engine.voice = voices[abs(script.id.hashCode()) % voices.size]
            engine.setPitch((if (female) 1.18f else .82f) + (abs(script.id.hashCode()) % 12) / 100f)
            engine.setSpeechRate(when (script.mood) { "PANISCH" -> 1.3f; "AUFGEREGT" -> 1.15f; "FLUESTERND" -> .92f; else -> 1f })
        }
        engine.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "ruf${counter++}")
    }

    fun stop() { tts?.stop() }

    fun close() {
        tts?.shutdown()
        tts = null
    }
}
