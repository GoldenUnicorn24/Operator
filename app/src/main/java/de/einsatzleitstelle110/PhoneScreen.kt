package de.einsatzleitstelle110

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher

/**
 * Native phone console. The player can open the linked dispatch file and resume the same call.
 */
class PhoneScreen(
    private val activity: Activity,
    private val game: GameController,
    private val onClosed: () -> Unit,
    private val onDispatch: (IncidentState) -> Unit,
    private val voice: VoicePlayer,
    private val speak: () -> Boolean,
) {
    private val ui = UiKit(activity)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dialog: Dialog
    private val transcript = ui.column()
    private val scroll = ScrollView(activity)
    private val actions = ui.column()
    private val actionScroll = ScrollView(activity).apply { addView(actions) }
    private val status = ui.text("", 12f, ui.muted)
    private var shownLines = 0
    private var call: PhoneCall? = null
    private var closing = false
    val isShowing get() = ::dialog.isInitialized && dialog.isShowing

    fun show(c: PhoneCall) {
        call = c
        val outer = ui.column().apply {
            setBackgroundColor(Color.rgb(6, 14, 22))
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
        }
        val script = PhoneDesk.script(game, c)
        val head = ui.row()
        val pulse = ui.chip("● 110", ui.red, true)
        head.addView(pulse)
        head.addView(ui.text("  ${script.caller}", 18f, ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(ui.chip(when (script.mood) {
            "PANISCH" -> "Panik"
            "AUFGEREGT" -> "Aufgeregt"
            "FLUESTERND" -> "Flüstert"
            else -> "Ruhig"
        }, if (script.mood == "RUHIG") ui.mint else ui.amber))
        outer.addView(head)
        outer.addView(status.apply { setPadding(0, ui.dp(4), 0, ui.dp(8)) })
        scroll.addView(transcript)
        val landscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val panels = if (landscape) ui.row() else ui.column()
        if (landscape) {
            panels.addView(scroll, LinearLayout.LayoutParams(0, -1, 1.15f).apply { marginEnd = ui.dp(12) })
            panels.addView(actionScroll, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            panels.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            panels.addView(actionScroll, LinearLayout.LayoutParams(-1, 0, 1.15f).apply { topMargin = ui.dp(8) })
        }
        outer.addView(panels, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(outer)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener { close() }
        dialog.show()
        render()
        updateStatus()
    }

    /** Called by the live ticker. */
    fun updateStatus() {
        val waiting = PhoneDesk.ringing(game.state).size
        status.text = game.clock() + (if (call != null) " · Gespräch läuft, die Zeit vergeht langsamer" else " · Gespräch beendet") +
            if (waiting > 0) " · $waiting weitere in der Leitung" else ""
        status.setTextColor(if (waiting > 0) ui.amber else ui.muted)
    }

    private fun bubble(line: String, animate: Boolean) {
        val who = line.substringBefore('|')
        val value = line.substringAfter('|')
        val t = ui.text("", if (who == "S") 12f else 15f, when (who) { "S" -> ui.amber; "D" -> ui.cyan; else -> ui.ink })
        t.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
        t.background = ui.bg(when (who) {
            "C" -> Color.rgb(20, 38, 52)
            "D" -> Color.rgb(10, 34, 46)
            else -> Color.rgb(36, 28, 12)
        }, Color.TRANSPARENT, 12)
        val lp = LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = ui.dp(6)
            gravity = if (who == "D") Gravity.END else if (who == "S") Gravity.CENTER_HORIZONTAL else Gravity.START
            if (who == "D") marginStart = ui.dp(44) else if (who == "C") marginEnd = ui.dp(44)
        }
        transcript.addView(t, lp)
        if (!animate || who != "C") {
            t.text = value
            return
        }
        // The caller "speaks": text appears at reading pace, a tap on the action area is never blocked.
        var i = 0
        val step = object : Runnable {
            override fun run() {
                i = (i + 2).coerceAtMost(value.length)
                t.text = value.substring(0, i)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                if (i < value.length && isShowing) handler.postDelayed(this, 22)
            }
        }
        handler.post(step)
    }

    private fun drain(animate: Boolean = true) {
        val c = call ?: return
        c.lines.drop(shownLines).forEach { bubble(it, animate) }
        val clips = c.spoken.flatMap { VoiceLines.clips(it.substringBefore('|'), it.substringAfter('|'), c.place) }
        c.spoken.clear()
        if (speak() && clips.isNotEmpty()) voice.play(clips, flush = true)
        shownLines = c.lines.size
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun section(label: String) = actions.addView(ui.text(label, 11f, ui.muted, true).apply { setPadding(0, ui.dp(6), 0, ui.dp(3)) })

    private fun action(label: String, primary: Boolean = false, click: () -> Unit) =
        actions.addView(ui.button(label, primary) { click() }.apply { setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6)) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })

    private fun render() {
        val c = call ?: return
        drain(shownLines > 0)
        actions.removeAllViews()
        val sc = PhoneDesk.script(game, c)
        if (PhoneDesk.needsCalming(game, c)) action("Beruhigen: „Ich bin da, atmen Sie tief durch.“") {
            PhoneDesk.calm(game, c)
            render()
        }
        if (!c.located) {
            val location = CallQuestions.forTopic(sc, QuestionTopic.LOCATION).first { it.id == "wo" || it.id == "general.location" }
            if (location.id !in c.asked) action(location.label) { PhoneDesk.ask(game, c, location.id); render() }
            if (sc.vague) action("Mobilfunkortung (AML) abrufen") { PhoneDesk.locate(game, c); render() }
        }
        addDispatchActions(c)
        section("FRAGEN")
        action("Fragen nach Thema") { showQuestionTopics(c) }
        val tips = sc.advice.filter { it.id !in c.advised }
        if (tips.isNotEmpty()) section("ANWEISUNG AN DEN ANRUFER")
        tips.forEach { a ->
            action(a.label) {
                PhoneDesk.advise(game, c, a.id)
                if (a.id == "cpr") showCpr(c) else render()
            }
        }
        section("GESPRÄCH")
        action("Einsatzstichwort wählen") { renderCodes() }
        action("In die Warteschleife legen") {
            PhoneDesk.hold(game, c)
            close()
        }
    }

    private fun addDispatchActions(c: PhoneCall) {
        val incident = PhoneDesk.linkedIncident(game, c)
        section("DISPOSITION")
        if (incident != null) {
            action("Einsatzakte öffnen · ${incident.id}", true) { close(); onDispatch(incident) }
            if (incident.status in listOf("OPEN", "DRIVING", "WORKING")) {
                action("Stichwort oder Priorität ändern") { renderDispatchCatalog(c) }
            }
        } else if (c.duplicateOf.isBlank()) {
            if (c.located) action("Während des Gesprächs alarmieren", true) { renderDispatchCatalog(c) }
            else actions.addView(ui.text("Für die Alarmierung zuerst den Einsatzort erfassen.", 12f, ui.muted))
        }
    }

    private fun showQuestionTopics(c: PhoneCall) {
        actions.removeAllViews()
        actionScroll.scrollTo(0, 0)
        action("Zurück zum Gespräch") { render() }
        section("FRAGETHEMEN")
        QuestionTopic.entries.forEach { topic ->
            action(topic.label) { showQuestions(c, topic) }
        }
    }

    private fun showQuestions(c: PhoneCall, topic: QuestionTopic, restoredScroll: Int = 0) {
        actions.removeAllViews()
        actionScroll.post { actionScroll.scrollTo(0, restoredScroll) }
        drain()
        action("Alle Fragethemen") { showQuestionTopics(c) }
        action("Zurück zum Gespräch") { render() }
        section(topic.label)
        val open = CallQuestions.forTopic(PhoneDesk.script(game, c), topic).filter { it.id !in c.asked }
        if (open.isEmpty()) actions.addView(ui.text("Alle Fragen in diesem Thema gestellt.", 12f, ui.muted))
        for (question in open) action(question.label) {
            val position = actionScroll.scrollY
            PhoneDesk.ask(game, c, question.id)
            showQuestions(c, topic, position)
        }
    }

    private fun renderDispatchCatalog(c: PhoneCall) {
        actions.removeAllViews()
        actionScroll.scrollTo(0, 0)
        section("EINSATZSTICHWORT")
        val search = EditText(activity).apply {
            hint = "Stichwort suchen"
            setSingleLine()
            setTextColor(ui.ink)
            setHintTextColor(ui.muted)
        }
        actions.addView(search, LinearLayout.LayoutParams(-1, -2))
        action("Zurück zum Gespräch") { render() }
        val results = ui.column()
        actions.addView(results)
        val suggested = PhoneDesk.codes(game, c).filter { !it.none && !it.merge }
            .mapNotNull { PhoneDesk.template(game, it.title) }
        val all = (game.allTemplates() + CallScripts.templates).distinctBy { it.title }.sortedBy { it.title }

        fun showResults(query: String) {
            results.removeAllViews()
            val matches = if (query.isBlank()) suggested else all.filter { it.title.contains(query, ignoreCase = true) }
            if (matches.isEmpty()) results.addView(ui.text("Kein Einsatzstichwort gefunden.", 13f, ui.muted))
            matches.take(20).forEach { template ->
                results.addView(ui.button(template.title) { renderDispatchPriority(c, template) })
            }
            if (matches.size > 20) results.addView(ui.text("Suchbegriff eingrenzen · ${matches.size} Treffer", 12f, ui.muted))
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = showResults(s.toString().trim())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        showResults("")
    }

    private fun renderDispatchPriority(c: PhoneCall, template: IncidentTemplate) {
        actions.removeAllViews()
        section("PRIORITÄT")
        actions.addView(ui.text("${template.title} · ${c.street}, ${c.place}", 15f, ui.ink, true))
        for ((priority, label) in listOf(1 to "Sofort", 2 to "Dringend", 3 to "Regulär")) {
            action("P$priority · $label", priority == (PhoneDesk.linkedIncident(game, c)?.priority ?: template.priority)) {
                val incident = PhoneDesk.startDispatch(game, c, template.title, priority)
                if (incident != null) {
                    close()
                    onDispatch(incident)
                } else render()
            }
        }
        action("Stichwort ändern") { renderDispatchCatalog(c) }
        action("Zurück zum Gespräch") { render() }
    }

    private fun renderCodes() {
        val c = call ?: return
        actions.removeAllViews()
        section("WAS SCHICKST DU?")
        for (code in PhoneDesk.codes(game, c)) {
            val detail = PhoneDesk.template(game, code.title)?.let { t -> "P${t.priority} · " + t.requiredTypes.groupingBy { ui.roleName(it) }.eachCount().entries.joinToString { "${it.value}× ${it.key}" } }
            action(code.title + if (detail != null) "\n$detail" else "") { finish(c, code) }
        }
        action("Zurück zum Gespräch") { render() }
    }

    private fun finish(c: PhoneCall, code: CallCode) {
        val result = PhoneDesk.classify(game, c, code)
        call = null
        voice.stop()
        // Fill the road fields now, so opening the dispatch panel does not stall.
        result.incident?.let { i ->
            val unit = game.state.units.firstOrNull { it.region == i.region }
            if (unit != null) NativeRouting.prewarmEta(unit, GeoPoint(i.lon, i.lat))
        }
        actions.removeAllViews()
        val color = if (result.score >= 65) ui.mint else if (result.score >= 40) ui.amber else ui.red
        actions.addView(ui.text("Notrufaufnahme: ${result.rating} · ${result.score} %", 18f, color, true))
        result.feedback.forEach { actions.addView(ui.text("• $it", 13f, ui.ink).apply { setPadding(0, ui.dp(3), 0, 0) }) }
        if (game.state.takeStreak >= 2) actions.addView(ui.text("Serie: ${game.state.takeStreak} saubere Notrufe", 13f, ui.cyan, true).apply { setPadding(0, ui.dp(5), 0, 0) })
        val incident = result.incident
        if (incident != null && code.merge.not()) action("Einsatz ${incident.id} disponieren", true) { close(); onDispatch(incident) }
        val next = PhoneDesk.ringing(game.state).firstOrNull()
        if (next != null) action("Nächsten Anruf annehmen") {
            if (PhoneDesk.answer(game, next)) {
                dialog.dismiss()
                val fresh = PhoneScreen(activity, game, onClosed, onDispatch, voice, speak)
                fresh.show(next)
                onReplace?.invoke(fresh)
            }
        }
        action("Auflegen") { close() }
    }

    /** MainActivity tracks the visible call screen; a chained call replaces it. */
    var onReplace: ((PhoneScreen) -> Unit)? = null

    /** Telephone CPR: the player presses along with the beat, 100–120 per minute. */
    private fun showCpr(c: PhoneCall) {
        actions.removeAllViews()
        drain()
        section("TELEFONREANIMATION")
        actions.addView(ui.text("Spielsimulation · keine Notfallausbildung", 14f, ui.ink))
        val counter = ui.text("Tippe im Takt: 0 / $CPR_PRESSES", 14f, ui.amber, true).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(8), 0, ui.dp(8)) }
        actions.addView(counter, LinearLayout.LayoutParams(-1, -2))
        val size = ui.dp(132)
        val pad = TextView(activity).apply {
            text = "DRÜCKEN"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(180, 40, 60)) }
            contentDescription = "Herzdruckmassage"
        }
        val box = FrameLayout(activity)
        box.addView(pad, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        actions.addView(box, LinearLayout.LayoutParams(-1, ui.dp(146)))
        val beat = object : Runnable {
            var big = false
            override fun run() {
                big = !big
                pad.animate().scaleX(if (big) 1.08f else .94f).scaleY(if (big) 1.08f else .94f).setDuration(CPR_BEAT_MS / 2 - 20).start()
                if (isShowing && call === c && c.cprQuality < 0) handler.postDelayed(this, CPR_BEAT_MS / 2)
            }
        }
        handler.post(beat)
        val presses = ArrayList<Long>()
        pad.setOnClickListener {
            presses.add(SystemClock.uptimeMillis())
            counter.text = "Tippe im Takt: ${presses.size} / $CPR_PRESSES"
            if (presses.size >= CPR_PRESSES) {
                pad.setOnClickListener(null)
                PhoneDesk.cprDone(game, c, PhoneDesk.cprQuality(presses.zipWithNext { a, b -> b - a }))
                render()
            }
        }
        action("Abbrechen, später weiter") { render() }
    }

    fun close() {
        if (closing) return
        closing = true
        voice.stop()
        handler.removeCallbacksAndMessages(null)
        if (::dialog.isInitialized) dialog.dismiss()
        onClosed()
    }

    /** Leaves the screen without further callbacks, for example when another save is loaded. */
    fun abandon() {
        closing = true
        voice.stop()
        handler.removeCallbacksAndMessages(null)
        if (::dialog.isInitialized) dialog.dismiss()
    }

    private companion object {
        const val CPR_PRESSES = 21
        const val CPR_BEAT_MS = 545L
    }
}
