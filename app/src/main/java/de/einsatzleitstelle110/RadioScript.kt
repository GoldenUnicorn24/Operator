package de.einsatzleitstelle110

import kotlin.math.*

data class RadioLine(val speaker: String, val text: String)

/** What is heard on the police radio: real events of the shift and fictional routine traffic. */
object RadioScript {
    const val CONTROL = "Leitstelle"
    private val PLACES = listOf("Reeperbahn", "Jungfernstieg", "Hauptbahnhof", "Landungsbrücken", "Elbbrücken", "Wandsbek Markt",
        "Harburger Ring", "Stresemannstraße", "Hafenstraße", "Max-Brauer-Allee", "Fuhlsbüttler Straße", "Barmbek Nord", "Altona Bahnhof")
    private val PLATES = listOf("Hamburg Anton Berta", "Hamburg Cäsar Dora", "Pinneberg Emil", "Stormarn Friedrich", "Harburg Gustav")

    /** Speakable call sign; slashes and the like are read aloud badly. */
    fun spoken(text: String) = text.replace(" HH ", " ").replace("/", " ").replace("·", ",").replace("(", "").replace(")", "").replace("  ", " ").trim()

    /** A game event as radio traffic; empty when the event is not radio traffic. */
    fun event(name: String, message: String): List<RadioLine> {
        val first = message.substringBefore(" · ").trim()
        return when (name) {
            "Einheit unterwegs" -> listOf(RadioLine(CONTROL, "$CONTROL für $first, Einsatz für Sie, kommen."), RadioLine(first, "$first hat verstanden, wir fahren an, Status 3."))
            "Am Einsatzort" -> listOf(RadioLine(first, "$first, am Einsatzort, Status 4."))
            "Funkstatus" -> {
                val who = message.substringBefore(" an Leitstelle")
                val status = message.substringAfter("Status ", "").trim()
                when (status) {
                    "1" -> listOf(RadioLine(who, "$who, wieder frei über Funk."))
                    "2" -> listOf(RadioLine(who, "$who, auf Wache, Status 2."))
                    else -> emptyList()
                }
            }
            "Verstärkung eingetroffen" -> listOf(RadioLine("Verstärkung", "Verstärkung vor Ort."))
            "Funkmeldung", "Nachforderung", "Lageverschärfung" -> listOf(RadioLine(message.substringBefore(":"), spoken(message.substringAfter(": ", message))))
            "Einheit abgezogen" -> listOf(RadioLine(CONTROL, "$CONTROL für $first, Einsatz abgebrochen, rücken Sie ein."))
            else -> emptyList()
        }
    }

    /** One short exchange of routine traffic. */
    fun ambient(rnd: () -> Double, units: List<String>): List<RadioLine> {
        fun <T> any(l: List<T>) = l[(rnd() * l.size).toInt().coerceAtMost(l.lastIndex)]
        val a = if (units.isEmpty()) "Peter 12" else any(units)
        val place = any(PLACES)
        val number = (rnd() * 900 + 100).toInt()
        return when ((rnd() * 10).toInt()) {
            0 -> listOf(RadioLine(a, "$a für $CONTROL, Halterabfrage, ${any(PLATES)} $number, kommen."), RadioLine(CONTROL, "Verstanden, Fahrzeug liegt nichts vor."))
            1 -> listOf(RadioLine(a, "$a, Verkehrskontrolle ${place}, zwei Personen, kommen."), RadioLine(CONTROL, "Verstanden, Ende."))
            2 -> listOf(RadioLine(CONTROL, "An alle Wagen im Bereich ${place}: Hinweis auf dunklen Transporter, Ausschau halten."))
            3 -> listOf(RadioLine(a, "$a, Personenüberprüfung ${place}, kommen."), RadioLine(CONTROL, "Person ohne Erkenntnisse."))
            4 -> listOf(RadioLine(a, "$a, Sprechwunsch."), RadioLine(CONTROL, "$a, sprechen Sie."), RadioLine(a, "Hat sich erledigt, danke, Ende."))
            5 -> listOf(RadioLine(a, "$a, kurze Streifenfahrt ${place}, keine Besonderheiten."))
            6 -> listOf(RadioLine(CONTROL, "$CONTROL für $a, wie ist Ihr Standort, kommen."), RadioLine(a, "Wir stehen ${place}, kommen."), RadioLine(CONTROL, "Verstanden, Ende."))
            7 -> listOf(RadioLine(a, "$a, Fußstreife ${place} beendet, frei über Funk."))
            8 -> listOf(RadioLine(CONTROL, "An alle: Veranstaltung ${place} beendet, erhöhtes Verkehrsaufkommen."))
            else -> listOf(RadioLine(a, "$a, Tankstopp, zehn Minuten nicht einsatzbereit."), RadioLine(CONTROL, "Verstanden."))
        }
    }
}

/** Turns clean speech into a transmission: narrow band, overdriven, framed by the key clicks. */
object RadioDsp {
    const val RATE = 16000

    data class Pcm(val samples: ShortArray, val rate: Int)

    /** Minimal RIFF reader: mono or stereo PCM16. Some engines leave a zero data length. */
    fun parseWav(b: ByteArray): Pcm? {
        if (b.size < 44 || String(b, 0, 4, Charsets.ISO_8859_1) != "RIFF") return null
        fun int(at: Int) = (b[at].toInt() and 255) or ((b[at + 1].toInt() and 255) shl 8) or ((b[at + 2].toInt() and 255) shl 16) or ((b[at + 3].toInt() and 255) shl 24)
        fun short(at: Int) = (b[at].toInt() and 255) or (b[at + 1].toInt() shl 8)
        var at = 12
        var rate = 0
        var channels = 1
        var bits = 16
        while (at + 8 <= b.size) {
            val id = String(b, at, 4, Charsets.ISO_8859_1)
            var len = int(at + 4)
            if (id == "fmt ") {
                channels = short(at + 10).coerceAtLeast(1)
                rate = int(at + 12)
                bits = short(at + 22)
            } else if (id == "data") {
                if (rate <= 0 || bits != 16) return null
                if (len <= 0 || at + 8 + len > b.size) len = b.size - at - 8
                val n = len / 2 / channels
                val out = ShortArray(n) { i -> short(at + 8 + i * 2 * channels).toShort() }
                return Pcm(out, rate)
            }
            if (len < 0) return null
            at += 8 + len + (len and 1)
        }
        return null
    }

    fun resample(p: Pcm): FloatArray {
        if (p.samples.isEmpty()) return FloatArray(0)
        val n = (p.samples.size.toLong() * RATE / p.rate).toInt().coerceAtLeast(1)
        return FloatArray(n) { i ->
            val x = i.toDouble() * p.rate / RATE
            val a = x.toInt().coerceAtMost(p.samples.lastIndex)
            val b = (a + 1).coerceAtMost(p.samples.lastIndex)
            ((p.samples[a] + (p.samples[b] - p.samples[a]) * (x - a)) / 32768.0).toFloat()
        }
    }

    private fun bandPass(x: FloatArray, low: Double, high: Double): FloatArray {
        val center = sqrt(low * high)
        val q = center / (high - low)
        val w = 2 * PI * center / RATE
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        val b0 = alpha / a0
        val a1 = -2 * cos(w) / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        return FloatArray(x.size) { i ->
            val y = b0 * x[i] - b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x[i].toDouble(); y2 = y1; y1 = y
            y.toFloat()
        }
    }

    private fun burst(ms: Int, level: Float, rnd: java.util.Random) =
        FloatArray(RATE * ms / 1000) { i -> (rnd.nextFloat() * 2 - 1) * level * (1f - i.toFloat() * 1000 / (RATE * ms)) }

    /** Speech → transmission. Output is ready to mix at [RATE]. */
    fun transmission(speech: FloatArray, seed: Long): ShortArray {
        val rnd = java.util.Random(seed)
        val peak = speech.maxOfOrNull { abs(it) }?.coerceAtLeast(.02f) ?: 1f
        val filtered = bandPass(FloatArray(speech.size) { speech[it] / peak }, 320.0, 2900.0)
        val fp = filtered.maxOfOrNull { abs(it) }?.coerceAtLeast(.02f) ?: 1f
        val head = burst(70, .28f, rnd)
        val tail = burst(170, .4f, rnd)
        val out = ShortArray(head.size + filtered.size + tail.size)
        head.forEachIndexed { i, v -> out[i] = (v * 32000).toInt().toShort() }
        filtered.forEachIndexed { i, v ->
            val driven = tanh((v / fp * 2.6f).toDouble()).toFloat() * .78f + (rnd.nextFloat() * 2 - 1) * .035f
            out[head.size + i] = (driven * 32000).toInt().coerceIn(-32767, 32767).toShort()
        }
        tail.forEachIndexed { i, v -> out[head.size + filtered.size + i] = (v * 32000).toInt().toShort() }
        return out
    }

    /** Fallback without a German voice: unintelligible speech rhythm from filtered pulses. */
    fun mumble(seconds: Double, seed: Long): ShortArray {
        val rnd = java.util.Random(seed)
        val n = (seconds * RATE).toInt()
        var phase = 0.0
        var pitch = 120.0
        val raw = FloatArray(n) { i ->
            val syllable = sin(2 * PI * i / RATE * (3.2 + sin(i / 9000.0))).coerceAtLeast(0.0)
            if (i % 1600 == 0) pitch = 95 + rnd.nextDouble() * 70
            phase += pitch / RATE
            val pulse = if (phase % 1.0 < .08) 1f else -.08f
            (pulse * syllable + (rnd.nextFloat() - .5f) * .1f * syllable).toFloat()
        }
        return transmission(raw, seed)
    }
}
