package de.einsatzleitstelle110

import kotlin.math.*

/** One caller on the line. Lines are stored as "C|text" (caller), "D|text" (dispatcher) or "S|text" (system). */
data class PhoneCall(
    val id: String,
    val scriptId: String,
    val lat: Double,
    val lon: Double,
    val street: String,
    val place: String,
    var status: String = "RINGING",
    var waited: Int = 0,
    val lines: MutableList<String> = mutableListOf(),
    val asked: MutableSet<String> = mutableSetOf(),
    val advised: MutableSet<String> = mutableSetOf(),
    var calmed: Boolean = false,
    var located: Boolean = false,
    /** Second caller about a call already in progress. */
    var duplicateOf: String = "",
    var cprQuality: Int = -1,
    /** Spoken lines not played yet, as "voice|raw text". Not saved. */
    val spoken: MutableList<String> = mutableListOf(),
    val region: String = "",
    val scope: String = "",
    val receivedAt: Long = -1,
    var answeredAt: Long = -1,
    var activeMinutes: Int = 0,
    var incidentId: String = "",
)

data class CallCode(val title: String, val correct: Boolean, val merge: Boolean = false, val none: Boolean = false)

data class TakeResult(val score: Int, val rating: String, val feedback: List<String>, val incident: IncidentState?)

/** Busier phases announced in the shift briefing; they steer volume and the kind of calls. */
data class ShiftEvent(val id: String, val title: String, val briefing: String, val tags: Set<String>, val weather: String = "")

/**
 * The 110 desk: phones ring, the player answers and asks, then decides what to send. Only what
 * was asked ends up in the incident; everything else comes back as a surprise on scene.
 */
object PhoneDesk {
    const val MAX_RINGING = 4
    /** Sim-minutes a caller stays on the line while ringing. */
    const val PATIENCE = 8
    const val SURGE_LENGTH = 100
    const val SURGE_RATE = 2.3
    const val CALM_LINE = "Ich bin da und schicke Hilfe. Atmen Sie einmal tief durch und antworten Sie mir kurz."
    const val CALM_REPLY = "Ja… ja. Okay. Ich versuche es."
    const val PANIC_LINE = "Ich weiß nicht! Beeilen Sie sich doch! Bitte!"
    const val CPR_REPLY = "Ich mache weiter. Bitte, kommen Sie schnell."

    val events =
        listOf(
            ShiftEvent("DERBY", "Derby im Stadion", "Zehntausende Fans in der Stadt: mehr Schlägereien und Verkehr während der Abreise.", setOf("CROWD", "DRUNK", "TRAFFIC")),
            ShiftEvent("FEST", "Stadtfest", "Großes Stadtfest: verlorene Kinder, Taschendiebe und Alkohol.", setOf("CROWD", "DRUNK", "SEARCH")),
            ShiftEvent("STURM", "Sturmfront", "Der Wetterdienst warnt: Sturmböen, umgestürzte Bäume und viele unnötige Anrufe.", setOf("STORM", "TRAFFIC"), "RAIN"),
            ShiftEvent("GLATT", "Blitzeis", "Überfrierende Nässe: Unfälle häufen sich, Anfahrten dauern deutlich länger.", setOf("TRAFFIC", "MED"), "ICE"),
            ShiftEvent("WELLE", "Grippewelle", "Rettungsdienst stark gefordert: viele medizinische Notrufe.", setOf("MED", "HOME")),
        )

    fun roleLabel(type: String) = when (type) {
        "STREIFE" -> "Streife"
        "FEUERWEHR" -> "Feuerwehr"
        "RETTUNG" -> "Rettungsdienst"
        "HUND" -> "Diensthund"
        "HELI" -> "Hubschrauber"
        "VERKEHR" -> "Verkehrspolizei"
        "KRIPO" -> "Kripo"
        "BEREITSCHAFT" -> "Bereitschaftspolizei"
        else -> type
    }

    fun event(s: GameState) = events.firstOrNull { it.id == s.shiftEvent }

    fun surgeActive(s: GameState) = s.shiftEvent.isNotBlank() && s.elapsed in s.surgeStart until s.surgeStart + SURGE_LENGTH

    fun rate(s: GameState) = if (surgeActive(s)) SURGE_RATE else 1.0

    private fun atDesk(s: GameState, call: PhoneCall) =
        (call.region.isBlank() || call.region == s.region) &&
            (call.scope.isBlank() || call.scope == s.scope)

    fun ringing(s: GameState) = s.phones.filter { it.status == "RINGING" && atDesk(s, it) }

    fun active(s: GameState) = s.phones.firstOrNull { it.status == "ACTIVE" && atDesk(s, it) }

    fun handOver(g: GameController): List<PhoneHandoff> =
        g.state.phones.filter { atDesk(g.state, it) }.map { call ->
            call.lines.add("S|An Folgeschicht übergeben · Tag ${g.state.day}.")
            linkedIncident(g, call)?.let { syncTranscript(call, it) }
            PhoneHandoff(call.id, call.status == "ACTIVE")
        }

    fun script(g: GameController, c: PhoneCall): CallScript =
        CallScripts.find(c.scriptId)
            ?: template(g, c.scriptId.removePrefix("gen:"))?.let(CallScripts::generic)
            ?: CallScripts.all.first()

    fun template(g: GameController, title: String): IncidentTemplate? =
        g.allTemplates().firstOrNull { it.title == title } ?: CallScripts.templates.firstOrNull { it.title == title }

    private fun fill(c: PhoneCall, text: String) = text.replace("{strasse}", c.street).replace("{ort}", c.place)

    /** Rescue and fire services work with the player; without them half of the calls are unsolvable. */
    fun ensurePartnerForces(g: GameController) {
        val s = g.state
        val sites = s.units.filter { it.region == s.region && it.scope == s.scope && it.type == "STREIFE" }
            .map { GeoPoint(it.homeLon, it.homeLat) }.distinct()
        if (sites.isEmpty()) return
        val wanted = if (s.scope != "STATE") mapOf("RETTUNG" to 2, "FEUERWEHR" to 1, "HUND" to 1)
            else mapOf("RETTUNG" to 6, "FEUERWEHR" to 4)
        for ((type, count) in wanted) {
            val have = s.units.count { it.region == s.region && it.scope == s.scope && it.type == type }
            for (i in have until count) {
                val official = if (s.region == "DE-HH")
                    HamburgStations.matching(type).filter { s.scope == "STATE" || g.inScope(it.point) }
                else emptyList()
                val p = if (official.isNotEmpty()) official[i % official.size].point
                    else sites[(i * 3 + type.length) % sites.size]
                val name = when (type) { "RETTUNG" -> "RTW"; "FEUERWEHR" -> "HLF"; else -> "Hund" }
                val u = UnitState("PARTNER-${s.region}-${s.scope}-$type-$i", "$name ${i + 1}", type, s.region, p.lat, p.lon, crew = 2,
                    officer = Officer("Partnerkraft ${i + 1}", 62, portrait = i % 8 + 1), scope = s.scope,
                    vehicleName = when (type) { "RETTUNG" -> "Rettungswagen"; "FEUERWEHR" -> "Löschfahrzeug"; else -> "Diensthundewagen" })
                g.ensureRoster(u)
                repeat(2) { k -> u.reliefTeams.add(u.officers.map { Officer("${it.name} / ${k + 2}", it.skill, portrait = it.portrait) }.toMutableList()) }
                s.units.add(u)
            }
        }
    }

    fun beginShift(g: GameController) {
        val s = g.state
        ensurePartnerForces(g)
        s.shiftAnswered = 0; s.shiftMissed = 0; s.shiftTakeTotal = 0; s.shiftTakes = 0; s.takeStreak = 0
        s.shiftEvent = ""
        if (s.tutorialStep == 0 && g.rnd() < .7) {
            val e = events[(g.rnd() * events.size).toInt().coerceAtMost(events.lastIndex)]
            s.shiftEvent = e.id
            s.surgeStart = ((.25 + g.rnd() * .35) * s.duration).roundToInt()
            if (e.weather.isNotBlank() && s.weather == "CLEAR") s.weather = e.weather
            g.emit("Lagebild", "${e.title}: ${e.briefing}")
        }
    }

    private fun usable(g: GameController, t: IncidentTemplate) =
        t.requiredTypes.all { type -> g.state.units.any { it.region == g.state.region && it.scope == g.state.scope && g.canHandle(it, type) } }

    fun pick(g: GameController, opener: Boolean = false): CallScript? {
        val s = g.state
        val h = g.hour()
        val night = h < 6 || h >= 21
        val surgeTags = if (surgeActive(s)) event(s)?.tags.orEmpty() else emptySet()
        val pool = CallScripts.all.filter { sc ->
            (sc.kind != "REAL" || template(g, sc.correct)?.let { usable(g, it) } == true) &&
                !(opener && (sc.kind != "REAL" || sc.mood == "PANISCH" || template(g, sc.correct)?.priority == 1)) &&
                !(night && "DAY" in sc.tags) && !(!night && "NIGHT" in sc.tags && h in 8..17) &&
                sc.id !in s.recentScripts &&
                (sc.place == null || (s.region == "DE-HH" && g.inScope(sc.place)))
        }
        if (pool.isEmpty()) return null
        val weights = pool.map { sc ->
            var w = if (sc.kind == "REAL") 1.0 else .55
            if (sc.tags.any { it in surgeTags }) w *= 4
            if (template(g, sc.correct)?.let { it.priority == 1 } == true) w *= .75
            if ("TRAFFIC" in sc.tags) w *= SimulationConditions.accidentWeight(s.weather)
            w
        }
        var x = g.rnd() * weights.sum()
        pool.forEachIndexed { i, sc ->
            x -= weights[i]
            if (x <= 0) return sc
        }
        return pool.last()
    }

    /** A phone starts ringing. With a template, that exact situation is reported (editor, follow-ups). */
    fun ring(g: GameController, script: CallScript?, at: GeoPoint? = null, duplicateOf: IncidentState? = null): PhoneCall? {
        val s = g.state
        val sc = script ?: return null
        if (ringing(s).size >= MAX_RINGING) return null
        val r = g.region()
        val p = duplicateOf?.let { GeoPoint(it.actualLon, it.actualLat) } ?: at ?: sc.place ?: g.samplePosition(r)
        val street = NativeRouting.loadedGraph(r.code)?.takeIf { it.map.version >= 2 }?.namedStreetPoint(p) { r.contains(it) && g.inScope(it) }
        val point = if (duplicateOf == null) street?.first ?: p else p
        val call = PhoneCall("T-${s.nextId++}", sc.id, point.lat, point.lon, street?.second ?: "Hauptstraße", g.locationName(r, point),
            duplicateOf = duplicateOf?.id ?: "", region = s.region, scope = s.scope,
            receivedAt = s.absoluteMinute)
        s.phones.add(call)
        s.recentScripts.add(sc.id)
        while (s.recentScripts.size > 8) s.recentScripts.removeAt(0)
        g.emit("Notruf klingelt", "${if (sc.mood == "PANISCH") "Aufgeregter " else ""}Anrufer in der Leitung")
        return call
    }

    /** Scheduled ring from the shift: sometimes a second caller about something already known. */
    fun ringAuto(g: GameController) {
        val s = g.state
        val active = s.incidents.firstOrNull {
            it.region == s.region && it.phoneScript.isNotBlank() && it.status in listOf("OPEN", "DRIVING") && it.age < 25 &&
                it.duplicateCallers < 3 && s.phones.none { p -> p.duplicateOf == it.id }
        }
        if (active != null && g.rnd() < .18) {
            ring(g, CallScripts.find(active.phoneScript) ?: pick(g), duplicateOf = active)
            return
        }
        // The whole catalog and the player's own scenarios reach the phone as well.
        if (g.rnd() < .3) {
            val t = g.pickTemplate()
            if (usable(g, t) && t.lat == null) {
                ring(g, CallScripts.generic(t))
                return
            }
        }
        ring(g, pick(g))
    }

    /** The player answers with "Notruf, Polizei Hamburg". */
    fun greeting(g: GameController) = "Notruf, Polizei ${g.region().name}. Wo genau ist der Notfallort?"

    fun answer(g: GameController, c: PhoneCall): Boolean {
        val s = g.state
        if (s.phase != "RUNNING" || c.status != "RINGING" || !atDesk(s, c) || active(s) != null) return false
        c.status = "ACTIVE"
        if (c.answeredAt < 0) c.answeredAt = s.absoluteMinute
        if (c.lines.none { it.startsWith("D|") }) {
            s.shiftAnswered++
            val sc = script(g, c)
            c.lines.add("D|${greeting(g)}")
            c.spoken.add("${VoiceLines.DISPATCHER}|${greeting(g)}")
            c.lines.add("C|" + fill(c, if (c.duplicateOf.isNotBlank()) "Hallo? Hier ist auch etwas bei der ${c.street}! ${sc.opening}" else sc.opening))
            c.spoken.add("${VoiceLines.callerVoice(sc)}|${sc.opening}")
            if (sc.mood == "PANISCH") c.lines.add("S|Anrufer ist in Panik. Erst beruhigen, sonst bleiben die Antworten unbrauchbar.")
            if (sc.mood == "FLUESTERND") c.lines.add("S|Anrufer flüstert.")
        }
        return true
    }

    private fun talk(g: GameController, c: PhoneCall, who: String, raw: String) {
        c.lines.add("$who|" + fill(c, raw))
        c.spoken.add((if (who == "D") VoiceLines.DISPATCHER else VoiceLines.callerVoice(script(g, c))) + "|" + raw)
    }

    /** Put the caller back into the queue, for example when a worse call comes in. */
    fun hold(g: GameController, c: PhoneCall) {
        if (c.status == "ACTIVE") {
            c.status = "RINGING"
            c.lines.add("S|In der Warteschleife.")
        }
    }

    fun needsCalming(g: GameController, c: PhoneCall) = script(g, c).mood == "PANISCH" && !c.calmed

    fun calm(g: GameController, c: PhoneCall) {
        if (c.calmed) return
        c.calmed = true
        talk(g, c, "D", CALM_LINE)
        talk(g, c, "C", CALM_REPLY)
        linkedIncident(g, c)?.let { syncTranscript(c, it) }
    }

    fun ask(g: GameController, c: PhoneCall, id: String): Boolean {
        val sc = script(g, c)
        val q = sc.questions.firstOrNull { it.id == id } ?: return false
        if (id in c.asked) return false
        talk(g, c, "D", q.label)
        if (needsCalming(g, c)) {
            talk(g, c, "C", PANIC_LINE)
            return true
        }
        c.asked.add(id)
        talk(g, c, "C", q.answer)
        if (id == "wo" && !sc.vague) c.located = true
        if (id == "wo" && sc.vague) c.lines.add("S|Keine verwertbare Ortsangabe. Mobilfunkortung (AML) anfordern.")
        sc.beats[c.asked.size]?.let { talk(g, c, "C", it) }
        linkedIncident(g, c)?.let { incident ->
            if (id == sc.twistQuestion && incident.title == sc.correct && incident.status in openStatuses)
                addKnownRoles(incident, template(g, sc.correct)?.requiredTypes.orEmpty() + sc.twistAdd)
            syncTranscript(c, incident)
        }
        return true
    }

    /** Advanced Mobile Location: the phone reports its own position. */
    fun locate(g: GameController, c: PhoneCall) {
        if (c.located) return
        c.located = true
        c.lines.add("S|AML-Ortung: ${c.street}, ${c.place} · Genauigkeit etwa 40 m")
        linkedIncident(g, c)?.let { syncTranscript(c, it) }
    }

    fun advise(g: GameController, c: PhoneCall, id: String): Advice? {
        val a = script(g, c).advice.firstOrNull { it.id == id } ?: return null
        if (id in c.advised) return null
        c.advised.add(id)
        talk(g, c, "D", a.label)
        talk(g, c, "C", a.reply)
        linkedIncident(g, c)?.let { syncTranscript(c, it) }
        return a
    }

    /** Share of presses in the target corridor of 100–120 per minute (500–600 ms apart). */
    fun cprQuality(intervalsMs: List<Long>): Int {
        if (intervalsMs.isEmpty()) return 0
        return (intervalsMs.count { it in 470..630 } * 100.0 / intervalsMs.size).roundToInt()
    }

    fun cprDone(g: GameController, c: PhoneCall, quality: Int) {
        c.cprQuality = quality.coerceIn(0, 100)
        c.lines.add("S|Herzdruckmassage angeleitet · Rhythmus ${c.cprQuality} %")
        talk(g, c, "C", CPR_REPLY)
    }

    /** All possible keywords in stable order for this call. */
    fun codes(g: GameController, c: PhoneCall): List<CallCode> {
        val sc = script(g, c)
        val out = mutableListOf<CallCode>()
        if (c.duplicateOf.isNotBlank()) {
            val known = g.state.incidents.firstOrNull { it.id == c.duplicateOf }
            if (known != null && known.status in listOf("OPEN", "DRIVING", "WORKING"))
                out.add(CallCode("Bereits bekannt: ${known.title} · ${known.id}", true, merge = true))
        }
        val titles = (listOf(sc.correct) + sc.wrong).filter { it.isNotBlank() && template(g, it) != null }
        val rotate = abs(c.id.hashCode()) % titles.size.coerceAtLeast(1)
        (titles.drop(rotate) + titles.take(rotate)).forEach { out.add(CallCode(it, it == sc.correct && out.none { o -> o.merge })) }
        if (sc.kind == "REAL" && titles.size < 2) {
            g.allTemplates().filter { it.title != sc.correct }.sortedBy { abs((it.title + c.id).hashCode()) }.take(2)
                .forEach { out.add(CallCode(it.title, false)) }
        }
        out.add(CallCode(CallScripts.NO_ACTION, sc.kind != "REAL" && c.duplicateOf.isBlank(), none = true))
        return out
    }

    private fun rating(score: Int) = when {
        score >= 85 -> "Vorbildlich"
        score >= 65 -> "Solide"
        score >= 40 -> "Lückenhaft"
        else -> "Mangelhaft"
    }

    private fun addStat(g: GameController, score: Int) {
        val s = g.state
        s.shiftTakes++
        s.shiftTakeTotal += score
        if (score >= 65) {
            s.takeStreak++
            if (s.takeStreak % 5 == 0) {
                s.reputation = (s.reputation + 1).coerceAtMost(100)
                g.emit("Serie", "${s.takeStreak} saubere Notrufe in Folge: Ruf +1")
            }
        } else s.takeStreak = 0
    }

    private val openStatuses = setOf("OPEN", "DRIVING", "WORKING")

    fun linkedIncident(g: GameController, call: PhoneCall): IncidentState? =
        call.incidentId.takeIf { it.isNotBlank() }?.let { id -> g.state.incidents.firstOrNull { it.id == id } }

    /** Create an actual dispatchable incident while the caller stays connected. */
    fun startDispatch(g: GameController, call: PhoneCall, keyword: String, priority: Int): IncidentState? {
        if (g.state.phase != "RUNNING" || active(g.state) !== call || !call.located ||
            call.duplicateOf.isNotBlank() || priority !in 1..3) return null
        val t = template(g, keyword) ?: return null
        val sc = script(g, call)
        val existing = linkedIncident(g, call)
        val incident = existing ?: create(g, call, sc, t.title, uncertain = false)
        if (incident.status !in openStatuses) return null
        if (existing != null && incident.title != t.title) updateKeyword(incident, t)
        incident.priority = priority
        val factor = g.regionDeadlineFactor() * g.difficultyDeadline()
        incident.deadline = min(incident.deadline, (phoneDeadline(g, priority, t.deadline) * factor).roundToInt().coerceAtLeast(12))
        call.incidentId = incident.id
        if (incident.title == sc.correct && sc.twistQuestion in call.asked)
            addKnownRoles(incident, t.requiredTypes + sc.twistAdd)
        call.lines.add("S|Einsatz ${incident.id} · P$priority · ${incident.title}")
        syncTranscript(call, incident)
        return incident
    }

    private fun syncTranscript(call: PhoneCall, incident: IncidentState) {
        incident.phoneTranscript.clear()
        incident.phoneTranscript.addAll(call.lines.takeLast(120))
    }

    private fun addKnownRoles(incident: IncidentState, desired: List<String>) {
        val stillMissing = desired.toMutableList()
        incident.requiredTypes.forEach { stillMissing.remove(it) }
        incident.requiredTypes.addAll(stillMissing.take((7 - incident.requiredTypes.size).coerceAtLeast(0)))
        stillMissing.forEach { incident.surprise.remove(it) }
    }

    private fun updateKeyword(incident: IncidentState, template: IncidentTemplate) {
        if (incident.status !in openStatuses || incident.title == template.title) return
        incident.notes.add("Stichwort: ${incident.title} → ${template.title}")
        incident.title = template.title
        incident.required = template.required
        incident.requiredTypes = template.requiredTypes.toMutableList()
        incident.description = template.description
        incident.duration = template.duration
        incident.scenario = Scenarios.find(template.title)?.id.orEmpty()
    }

    private fun reuseOrCreate(g: GameController, c: PhoneCall, sc: CallScript, title: String, uncertain: Boolean): IncidentState {
        val existing = linkedIncident(g, c) ?: return create(g, c, sc, title, uncertain)
        template(g, title)?.let { updateKeyword(existing, it) }
        return existing
    }

    /** Score the completed intake and extend any dispatch already in progress. */
    fun classify(g: GameController, c: PhoneCall, code: CallCode): TakeResult {
        val s = g.state
        val sc = script(g, c)
        s.phones.remove(c)
        val notes = mutableListOf<String>()
        var score: Int
        var incident: IncidentState? = null
        if (code.merge) {
            incident = s.incidents.first { it.id == c.duplicateOf }
            incident.duplicateCallers++
            incident.notes.add("Weiterer Anrufer der Einsatzakte zugeordnet.")
            score = 100
            notes.add("Doppelmeldung erkannt, Kräfte bleiben für andere Lagen frei.")
        } else if (sc.kind != "REAL") {
            if (code.none) {
                score = 100
                incident = linkedIncident(g, c)
                if (incident != null) {
                    score = 70
                    incident.notes.add("Nach Gesprächsabschluss als Fehlalarm erkannt.")
                    template(g, "Blinder Alarm")?.let { updateKeyword(incident, it) }
                }
                notes.add("Kein Notfall erkannt, Leitung schnell wieder frei.")
            } else {
                score = 15
                notes.add("Kein echter Notfall: eine Streife fährt umsonst.")
                incident = reuseOrCreate(g, c, sc, "Blinder Alarm", uncertain = false)
                incident.phoneScript = sc.id
            }
        } else if (code.none) {
            score = 0
            notes.add("Echter Notfall abgewiesen! Die Lage wird nun ohne Vorwarnung nachgemeldet.")
            s.reputation = (s.reputation - 4).coerceAtLeast(0)
            incident = reuseOrCreate(g, c, sc, sc.correct, false)
            incident.deadline = (incident.deadline / 2).coerceAtLeast(incident.age + 5)
            incident.notes.add("Der erste Notruf wurde abgewiesen.")
        } else {
            score = if (c.located) 25 else 0
            notes.add(if (c.located) "Einsatzort bestätigt." else "Einsatzort nicht geklärt: Die Kräfte werden suchen müssen.")
            if (code.correct) { score += 35; notes.add("Einsatzstichwort passt.") } else notes.add("Einsatzstichwort falsch: richtig wäre „${sc.correct}“.")
            val keys = sc.questions.filter { it.key }
            val found = keys.count { it.id in c.asked }
            score += if (keys.isEmpty()) 25 else 25 * found / keys.size
            keys.filter { it.id !in c.asked && it.id != "wo" }.take(2).forEach { notes.add("Nicht gefragt: „${it.label}“") }
            val good = sc.advice.filter { it.good && it.id in c.advised }
            val bad = sc.advice.filter { !it.good && it.id in c.advised }
            if (sc.advice.isEmpty() || good.isNotEmpty()) score += 15
            else notes.add("Keine hilfreiche Anweisung gegeben.")
            if (bad.isNotEmpty()) { score -= 25; notes.add("Gefährliche Anweisung: „${bad.first().label}“") }
            if (c.waited > 4) { score -= 5; notes.add("Anrufer wartete ${c.waited} Minuten in der Leitung.") }
            incident = reuseOrCreate(g, c, sc, code.title, !c.located)
            if (sc.cpr) {
                if (c.cprQuality >= 60) { incident.riskDelta -= .25; notes.add("Telefonreanimation hielt den Kreislauf in Gang (${c.cprQuality} %).") }
                else if (c.cprQuality >= 0) notes.add("Telefonreanimation unregelmäßig (${c.cprQuality} %): Ziel 100–120 Stöße pro Minute.")
                else { incident.riskDelta += .15; notes.add("Keine Telefonreanimation angeleitet.") }
            }
            val truth = template(g, sc.correct)?.requiredTypes.orEmpty()
            val hiddenTwist = if (sc.twistQuestion.isBlank()) emptyList() else sc.twistAdd
            val known = sc.twistQuestion in c.asked
            val want = truth + hiddenTwist
            val missing = want.toMutableList()
            incident.requiredTypes.forEach { missing.remove(it) }
            if (known && code.correct) {
                addKnownRoles(incident, want)
                if (hiddenTwist.isNotEmpty()) notes.add("Durch Nachfragen gleich die richtigen Kräfte: ${hiddenTwist.joinToString { roleLabel(it) }}.")
            } else if (missing.isNotEmpty()) {
                incident.surprise.addAll(missing)
                incident.surpriseText = if (code.correct) sc.twistRadio else "Lage vor Ort anders als gemeldet: ${sc.correct}."
            }
            incident.riskDelta += ((60 - score).coerceIn(-40, 60)) * .0015
            incident.takeScore = score
            if (score >= 85) s.reputation = (s.reputation + 1).coerceAtMost(100)
        }
        score = score.coerceIn(0, 100)
        incident?.let {
            it.notes.add("Notrufaufnahme: ${rating(score)} ($score %).")
            it.phoneClosedAt = s.absoluteMinute
            if (!code.merge) syncTranscript(c, it)
        }
        addStat(g, score)
        g.emit("Notruf beendet", "${rating(score)} ($score %)")
        return TakeResult(score, rating(score), notes, incident)
    }

    /** Realistic reaction targets for spoken calls. Fair travel times stay guaranteed by the area factor. */
    fun phoneDeadline(g: GameController, priority: Int, base: Int) =
        min(base, when (priority) { 1 -> 25; 2 -> 45; else -> 90 })

    private fun create(g: GameController, c: PhoneCall, sc: CallScript, title: String, uncertain: Boolean): IncidentState {
        val t = template(g, title) ?: template(g, sc.correct) ?: CallScripts.templates.first()
        val before = g.state.incidents.size
        val incident = g.reportCaller(t, GeoPoint(c.lon, c.lat), disconnected = false, addressUncertain = uncertain)
        if (g.state.tutorialStep in 1..3) incident.flags.add("tutorial")
        if (g.state.incidents.size > before) {
            val factor = g.regionDeadlineFactor() * g.difficultyDeadline()
            incident.deadline = (phoneDeadline(g, incident.priority, t.deadline) * factor).roundToInt().coerceAtLeast(12)
            incident.age = c.waited + c.activeMinutes
            incident.reportedAt = c.receivedAt.takeIf { it >= 0 } ?: g.state.absoluteMinute
            incident.answeredAt = c.answeredAt
        }
        incident.phoneScript = sc.id
        incident.location = incident.location.replace(" (ungefähr)", "")
        return incident
    }

    /** Runs once per simulated minute. */
    fun tick(g: GameController) {
        val s = g.state
        active(s)?.let { it.activeMinutes++ }
        for (c in ringing(s)) {
            c.waited++
            if (c.waited >= PATIENCE) hangUp(g, c)
        }
    }

    private fun hangUp(g: GameController, c: PhoneCall) {
        val s = g.state
        val sc = script(g, c)
        s.phones.remove(c)
        linkedIncident(g, c)?.let {
            it.disconnected = true
            it.phoneClosedAt = s.absoluteMinute
            c.lines.add("S|Verbindung abgebrochen.")
            syncTranscript(c, it)
            g.emit("Anrufer aufgelegt", "${it.location}: bestehender Einsatz ${it.id}.")
            return
        }
        if (sc.kind != "REAL" || c.duplicateOf.isNotBlank()) return
        s.shiftMissed++
        s.takeStreak = 0
        s.reputation = (s.reputation - 1).coerceAtLeast(0)
        val t = template(g, sc.correct) ?: return
        val incident = g.reportCaller(t, GeoPoint(c.lon, c.lat), disconnected = true, addressUncertain = true)
        incident.phoneScript = sc.id
        incident.deadline = (phoneDeadline(g, incident.priority, t.deadline) * g.regionDeadlineFactor()).roundToInt().coerceAtLeast(12)
        if (s.tutorialStep in 1..3) incident.flags.add("tutorial")
        val hidden = t.requiredTypes.drop(1)
        if (hidden.isNotEmpty() && incident.assignedUnits.isEmpty()) {
            incident.requiredTypes = t.requiredTypes.take(1).toMutableList()
            incident.surprise.addAll(hidden)
            incident.surpriseText = "Lage vor Ort größer als die abgebrochene Meldung."
        }
        g.emit("Anrufer aufgelegt", "${incident.location}: Notruf nicht angenommen, Rückruf möglich.")
    }

    /** Arrival: what the call taker did not find out, the crew finds out now. */
    fun onArrival(g: GameController, call: IncidentState) {
        if (call.addressUncertain && !call.callbackDone && call.phoneScript.isNotBlank()) {
            call.riskDelta += .06
            call.duration += 6
            call.notes.add("Adresse war ungenau: Kräfte mussten suchen.")
            g.emit("Funkmeldung", "${call.title}: Einsatzort nicht gefunden, Nachsuche läuft (+6 Min.)", incident = call)
        }
        if (call.surprise.isEmpty()) return
        val add = call.surprise.toList()
        call.surprise.clear()
        call.requiredTypes.addAll(add.take((7 - call.requiredTypes.size).coerceAtLeast(0)))
        call.riskDelta += .08
        call.deadline = (call.deadline + 15).coerceAtMost(1440)
        call.notes.add("Nachforderung vor Ort: ${add.joinToString { roleLabel(it) }}")
        g.emit("Nachforderung", "${call.title}: ${call.surpriseText.ifBlank { add.joinToString { roleLabel(it) } }}", incident = call)
    }

    /** Waiting makes it worse: at half of the deadline an unattended situation escalates. */
    fun pressure(g: GameController, call: IncidentState) {
        if (call.worsened || call.phoneScript.isBlank() || call.firstArrival >= 0) return
        if (call.status !in listOf("OPEN", "DRIVING") || call.age * 2 < call.deadline) return
        call.worsened = true
        val sc = CallScripts.find(call.phoneScript) ?: return
        if (sc.worse.isBlank() || sc.kind != "REAL") return
        call.priority = (call.priority - 1).coerceAtLeast(1)
        call.requiredTypes.addAll(sc.worseAdd.take((8 - call.requiredTypes.size).coerceAtLeast(0)))
        call.riskDelta += .05
        call.notes.add("Lageverschärfung: ${sc.worse}")
        g.emit("Lageverschärfung", "${call.title}: ${sc.worse}", incident = call)
    }

    /** Graded like a German school mark: 1 is the best, 6 the worst. */
    fun grade(solved: Int, failed: Int, missed: Int, takeAverage: Int, hilfsfrist: Int, partial: Int = 0): Int {
        val done = solved + failed + partial
        val success = if (done == 0) 70.0 else (solved * 100.0 + partial * 50.0) / done
        val points = success * .5 + takeAverage * .3 + hilfsfrist * .2 - missed * 3
        return when {
            points >= 85 -> 1
            points >= 76 -> 2
            points >= 62 -> 3
            points >= 48 -> 4
            points >= 32 -> 5
            else -> 6
        }
    }
}
