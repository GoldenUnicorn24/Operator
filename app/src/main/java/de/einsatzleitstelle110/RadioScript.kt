package de.einsatzleitstelle110

/** Creates spoken transmissions from actual simulation events. */
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

    private fun callsign(voice: String, value: String): VoiceClip =
        number(value)?.let { sign(voice, it) } ?: VoiceClip(voice, value.replace("/", " Strich "))

    private fun unit(callsign: String, text: String, priority: RadioPriority): RadioLine {
        val v = voice(number(callsign) ?: (callsign.hashCode() and Int.MAX_VALUE))
        return RadioLine(listOf(callsign(v, callsign), VoiceClip(v, text)), priority)
    }

    private fun control(callsign: String, text: String, priority: RadioPriority) =
        RadioLine(listOf(VoiceClip(CONTROL, SHORT[0]), callsign(CONTROL, callsign), VoiceClip(CONTROL, text)), priority)

    private fun announcement(text: String, priority: RadioPriority) =
        listOf(RadioLine(listOf(VoiceClip(CONTROL, text)), priority))

    fun event(name: String, message: String, callsign: String? = null): List<RadioLine> {
        val who = callsign ?: message.substringBefore(" · ").substringBefore(" an Leitstelle").substringBefore(" verlässt ").trim()
        val incident = RadioPriority.INCIDENT
        val operations = RadioPriority.OPERATIONS
        return when (name) {
            "Einheit unterwegs" -> listOf(control(who, EVENT[4], incident), unit(who, EVENT[0], incident))
            "Am Einsatzort" -> listOf(unit(who, EVENT[1], incident))
            "Einheit abgezogen" -> listOf(control(who, EVENT[5], incident))
            "Nachforderung", "Lageverschärfung", "Einsatz fehlgeschlagen" ->
                announcement("$name. $message", RadioPriority.URGENT)
            "Funkmeldung", "Verstärkung eingetroffen", "Einsatz beendet" -> announcement("$name. $message", incident)
            "Schicht begonnen", "Schichtende" -> announcement("$name. $message", operations)
            "Funkstatus" -> when (message.substringAfter("Status ", "").trim()) {
                "1" -> listOf(unit(who, EVENT[2], operations))
                "2" -> listOf(unit(who, EVENT[3], operations))
                else -> emptyList()
            }
            else -> emptyList()
        }
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
