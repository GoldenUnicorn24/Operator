package de.einsatzleitstelle110

import java.security.MessageDigest
import kotlin.math.abs

/** One recorded clip: a voice plus the exact text. The file name is derived from both. */
data class VoiceClip(val voice: String, val text: String) {
    val id get() = VoiceLines.clipId(voice, text)
}

/**
 * Bridges the written calls and the voice clips bundled in the app. Clips are rendered ahead of
 * time from `tools/voices/lines.tsv`. Street names exist by the thousands and are only shown;
 * the spoken version names the district instead.
 */
object VoiceLines {
    const val DISPATCHER = "d"
    val FEMALE = (1..9).map { "f$it" }
    val MALE = (1..9).map { "m$it" }
    val RADIO = listOf("r0", "r1", "r2", "r3")
    private val FEMALE_CALLERS = listOf("Frau", "Dame", "Touristin")
    private val STAGE = Regex("\\([^)]*\\)")
    private val SPACE = Regex("\\s+")
    private val SPOKEN = listOf(
        "{strasse} in {ort}" to "In {ort}",
        "{strasse} bei {ort}" to "In {ort}",
        "{strasse}, Höhe {ort}" to "Höhe {ort}",
        "in die {strasse}, Hausnummer zwölf, {ort}" to "nach {ort}, Hausnummer zwölf",
        "an der {strasse} in {ort}" to "in {ort}",
        "an der {strasse}, {ort}" to "in {ort}",
        "in {ort}, an der {strasse}" to "in {ort}",
        "am Einkaufszentrum, {strasse}, {ort}" to "am Einkaufszentrum in {ort}",
        "Am Bahnhof {ort}, Zugang {strasse}" to "Am Bahnhof {ort}",
        "{strasse}, {ort}" to "In {ort}",
    )

    fun clipId(voice: String, text: String): String =
        MessageDigest.getInstance("SHA-1").digest("$voice|$text".toByteArray()).take(7).joinToString("") { "%02x".format(it) }

    fun callerVoice(script: CallScript): String {
        val pool = if (FEMALE_CALLERS.any { it in script.caller }) FEMALE else MALE
        val base = pool[(script.id.hashCode() and Int.MAX_VALUE) % pool.size]
        return base + when (script.mood) { "PANISCH" -> "p"; "AUFGEREGT" -> "a"; "FLUESTERND" -> "w"; else -> "n" }
    }

    /** Stage directions are read, not heard; the street is replaced by the district. */
    fun spoken(raw: String): String {
        var t = STAGE.replace(raw, " ")
        SPOKEN.forEach { (a, b) -> t = t.replace(a, b) }
        return SPACE.replace(t.replace("{strasse}", ""), " ").trim()
    }

    fun clips(voice: String, raw: String, place: String): List<VoiceClip> {
        val t = spoken(raw)
        if (t.isBlank() || t.none { it.isLetter() }) return emptyList()
        val parts = t.split("{ort}")
        val out = mutableListOf<VoiceClip>()
        parts.forEachIndexed { i, part ->
            val p = part.trim().trimStart(',', ' ')
            if (p.any { it.isLetter() }) out.add(VoiceClip(voice, p))
            if (i < parts.lastIndex && place.isNotBlank()) out.add(VoiceClip(voice.take(2), place))
        }
        return out
    }

    /** Every clip the game can ask for. The test keeps `lines.tsv` in step with this. */
    fun manifest(templates: List<IncidentTemplate>, places: List<String>): List<VoiceClip> {
        val out = linkedSetOf<VoiceClip>()
        fun add(voice: String, raw: String) = out.addAll(clips(voice, raw, "").filter { "{" !in it.text })
        val scripts = CallScripts.all + templates.map(CallScripts::generic)
        add(DISPATCHER, "Notruf, Polizei Hamburg. Wo genau ist der Notfallort?")
        add(DISPATCHER, PhoneDesk.CALM_LINE)
        for (s in scripts) {
            val v = callerVoice(s)
            add(v, s.opening)
            s.questions.forEach { add(DISPATCHER, it.label); add(v, it.answer) }
            s.advice.forEach { add(DISPATCHER, it.label); add(v, it.reply) }
            s.beats.values.forEach { add(v, it) }
            add(v, PhoneDesk.PANIC_LINE)
            add(v, PhoneDesk.CALM_REPLY)
            if (s.cpr) add(v, PhoneDesk.CPR_REPLY)
        }
        for (voice in FEMALE + MALE) places.forEach { out.add(VoiceClip(voice, it)) }
        out.addAll(RadioScript.allClips())
        return out.toList()
    }
}
