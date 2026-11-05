package de.einsatzleitstelle110

import kotlin.math.abs

data class RadioLine(val clips: List<VoiceClip>)

/** What is heard on the police radio: events of the shift and fictional routine traffic. */
object RadioScript {
    private const val CONTROL = "r0"
    const val MAX_NUMBER = 24

    private val EVENT = listOf(
        "hat verstanden, wir fahren an.", "am Einsatzort.", "wieder frei über Funk.", "auf Wache.", "Einsatz für Sie, kommen.", "Einsatz abgebrochen, rücken Sie ein.",
    )
    private val UNIT = listOf(
        "Verkehrskontrolle Reeperbahn, zwei Personen, kommen.", "Personenüberprüfung am Hauptbahnhof, kommen.", "Streifenfahrt Jungfernstieg, keine Besonderheiten.",
        "Fußstreife Landungsbrücken beendet, frei über Funk.", "Tankstopp, zehn Minuten nicht einsatzbereit.", "Sprechwunsch.", "Halterabfrage bitte, Hamburg Anton Berta 412, kommen.",
        "Wir stehen an den Elbbrücken, kommen.", "Kontrolle Wandsbek Markt beendet.", "Sind im Bereich Altona, alles ruhig.", "Hafenstraße überprüft, nichts festgestellt.",
        "Fahren jetzt die Stresemannstraße ab.", "Person angetroffen, geht es ihr gut, kein Einsatz nötig.", "Nehmen den Sachverhalt auf, dauert noch zehn Minuten.",
    )
    private val CONTROL_LINES = listOf(
        "Verstanden, Ende.", "Verstanden, Fahrzeug liegt nichts vor.", "Person ohne Erkenntnisse.", "Sprechen Sie, kommen.", "Wie ist Ihr Standort, kommen.",
        "An alle Wagen im Bereich Reeperbahn: Hinweis auf dunklen Transporter, Ausschau halten.", "An alle: Veranstaltung am Rathausmarkt beendet, erhöhtes Verkehrsaufkommen.",
        "An alle: Ampelausfall an der Max-Brauer-Allee.", "Für alle Wagen Barmbek: Hilflose Person gemeldet, Augen offen halten.",
    )
    private val SHORT = listOf("Leitstelle für")

    private fun number(callsign: String) =
        Regex("^Peter (?:HH )?(\\d+)$").find(callsign)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..MAX_NUMBER }

    private fun voice(n: Int) = VoiceLines.RADIO[1 + n % 3]

    private fun sign(voice: String, n: Int) = VoiceClip(voice, "Peter $n")

    private fun unit(callsign: String, text: String): RadioLine? {
        val n = number(callsign) ?: return null
        val v = voice(n)
        return RadioLine(listOf(sign(v, n), VoiceClip(v, text)))
    }

    private fun control(callsign: String, text: String): RadioLine? {
        val n = number(callsign) ?: return null
        return RadioLine(listOf(VoiceClip(CONTROL, SHORT[0]), sign(CONTROL, n), VoiceClip(CONTROL, text)))
    }

    /** A game event as radio traffic; only patrol cars have recorded call signs. */
    fun event(name: String, message: String): List<RadioLine> {
        val who = message.substringBefore(" · ").substringBefore(" an Leitstelle").trim()
        return when (name) {
            "Einheit unterwegs" -> listOfNotNull(control(who, EVENT[4]), unit(who, EVENT[0]))
            "Am Einsatzort" -> listOfNotNull(unit(who, EVENT[1]))
            "Einheit abgezogen" -> listOfNotNull(control(who, EVENT[5]))
            "Funkstatus" -> when (message.substringAfter("Status ", "").trim()) {
                "1" -> listOfNotNull(unit(who, EVENT[2]))
                "2" -> listOfNotNull(unit(who, EVENT[3]))
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    /** One short piece of routine traffic. */
    fun ambient(rnd: () -> Double, units: List<String>): List<RadioLine> {
        fun <T> any(l: List<T>) = l[(rnd() * l.size).toInt().coerceAtMost(l.lastIndex)]
        val a = units.filter { number(it) != null }.ifEmpty { listOf("Peter 12") }.let(::any)
        return if (rnd() < .2) listOf(RadioLine(listOf(VoiceClip(CONTROL, any(CONTROL_LINES.drop(5))))))
        else listOfNotNull(unit(a, any(UNIT)), RadioLine(listOf(VoiceClip(CONTROL, any(CONTROL_LINES.take(3))))))
    }

    fun allClips(): List<VoiceClip> {
        val out = mutableListOf<VoiceClip>()
        for (n in 1..MAX_NUMBER) {
            out.add(sign(voice(n), n))
            out.add(sign(CONTROL, n))
        }
        for (v in VoiceLines.RADIO.drop(1)) (UNIT + EVENT.take(4)).forEach { out.add(VoiceClip(v, it)) }
        (CONTROL_LINES + EVENT.drop(4) + SHORT).forEach { out.add(VoiceClip(CONTROL, it)) }
        return out
    }
}
