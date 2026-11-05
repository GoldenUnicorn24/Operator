package de.einsatzleitstelle110

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small native widget kit shared by the map screen panels. */
class UiKit(private val activity: Activity) {
    val ink = Color.rgb(230, 240, 248)
    val muted = Color.rgb(155, 180, 198)
    val panel = Color.rgb(12, 26, 38)
    val edge = Color.rgb(40, 67, 86)
    val cyan = Color.rgb(80, 203, 237)
    val mint = Color.rgb(105, 229, 181)
    val amber = Color.rgb(255, 199, 111)
    val red = Color.rgb(255, 113, 132)
    val yellow = Color.rgb(245, 230, 99)

    fun dp(v: Int) = (activity.resources.displayMetrics.density * v).toInt()

    fun bg(color: Int, border: Int = edge, radius: Int = 12, stroke: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(stroke), border)
        }

    fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    fun row() =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun text(value: String, size: Float = 13f, color: Int = ink, bold: Boolean = false) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun button(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) =
        TextView(activity).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (!enabled) muted else if (primary) Color.rgb(6, 20, 28) else ink)
            background =
                bg(
                    if (!enabled) Color.rgb(24, 38, 50) else if (primary) cyan else Color.rgb(22, 44, 60),
                    if (primary && enabled) cyan else edge,
                    10,
                )
            minHeight = dp(44)
            setPadding(dp(12), 0, dp(12), 0)
            isEnabled = enabled
            contentDescription = label
            setOnClickListener { onClick() }
        }

    fun chip(label: String, color: Int, filled: Boolean = false) =
        TextView(activity).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (filled) Color.rgb(8, 18, 26) else color)
            background = bg(if (filled) color else Color.rgb(16, 34, 48), color, 8)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }

    fun roleName(type: String) =
        when (type) {
            "STREIFE" -> "Streife"
            "SEK" -> "SEK"
            "MEK" -> "MEK"
            "KRIPO" -> "Kripo"
            "BEREITSCHAFT" -> "Bereitschaftspolizei"
            "HUND" -> "Hundestaffel"
            "VERKEHR" -> "Verkehrspolizei"
            "HELI" -> "Hubschrauber"
            "RETTUNG" -> "Rettungsdienst"
            "FEUERWEHR" -> "Feuerwehr"
            else -> type
        }

    fun priorityColor(p: Int) = if (p == 1) red else if (p == 2) amber else yellow
}

/**
 * Incident file and dispatch directly over the live map: caller report, required forces,
 * multi-select of fitting units with routed ETA, live route preview, reinforcement, withdrawal
 * and re-dispatch of bound units. The map stays visible above it.
 */
class DispatchPanel(
    private val activity: Activity,
    private val game: GameController,
    private val map: TacticalMapView,
    private val ui: UiKit,
    private val onChanged: () -> Unit,
    private val onUnitDetails: (UnitState) -> Unit,
    private val toast: (String) -> Unit,
) {
    val root: LinearLayout = ui.column()
    private val scroll = ScrollView(activity)
    private val body = ui.column()
    private val footer = ui.row()
    var call: IncidentState? = null
        private set
    private val selected = LinkedHashSet<String>()
    private var specialRights = true
    private var showBound = false
    private var lastVersion = ""

    init {
        root.background = ui.bg(Color.rgb(10, 24, 35), ui.edge, 16)
        root.elevation = ui.dp(8).toFloat()
        root.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
        root.isClickable = true
        scroll.addView(body)
        scroll.isVerticalScrollBarEnabled = true
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    }

    val isOpen
        get() = call != null

    fun open(c: IncidentState) {
        if (call?.id != c.id) {
            specialRights = c.priority == 1
            selected.clear()
            showBound = false
            scroll.scrollTo(0, 0)
            // A proposal is only a selection; the player still confirms the actual radio order.
            if (c.status == "OPEN") selected.addAll(DispatchAdvisor.plan(game, c, specialRights).picks.map { it.unitId })
        }
        call = c
        map.selectedCall = c
        map.previewUnits = selected.toSet()
        lastVersion = ""
        render()
    }

    fun close() {
        call = null
        map.obstructedBottom = 0f
        selected.clear()
        map.selectedCall = null
        map.previewUnits = emptySet()
        map.invalidate()
    }

    /** Called every live tick: re-render only if the call or its units changed. */
    fun refresh() {
        val c = call ?: return
        if (c.status in listOf("SOLVED", "FAILED")) {
            render()
            return
        }
        if (version(c) != lastVersion) render()
    }

    /** Who can be sent, not where they are: rows and ETAs are only rebuilt when that changes. */
    private fun version(c: IncidentState) =
        c.status + c.pending + c.notes.size + c.assignedUnits.joinToString() + c.age / 3 + game.state.weather +
            game.state.units.count { it.region == c.region && game.dispatchable(it) } +
            c.assignedUnits.joinToString { id -> game.state.units.find { it.id == id }?.status.orEmpty() }

    private fun minutesLeft(c: IncidentState) = (c.deadline - c.age).coerceAtLeast(0)

    private fun render() {
        val c = call ?: return
        lastVersion = version(c)
        val y = scroll.scrollY
        body.removeAllViews()
        footer.removeAllViews()
        // Header.
        val head = ui.row()
        head.addView(ui.chip("P${c.priority}", ui.priorityColor(c.priority), true))
        head.addView(
            ui.text("  ${c.title}", 16f, ui.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        head.addView(ui.text("✕", 20f, ui.muted, true).apply {
            setPadding(ui.dp(12), ui.dp(2), ui.dp(4), ui.dp(2))
            contentDescription = "Einsatzakte schließen"
            setOnClickListener { close(); onChanged() }
        })
        body.addView(head)
        val status =
            when (c.status) {
                "OPEN" -> "offen · noch ${minutesLeft(c)} Min bis Fristablauf"
                "DRIVING" -> "Kräfte auf Anfahrt · Frist ${minutesLeft(c)} Min"
                "WORKING" -> "Kräfte arbeiten vor Ort"
                "HANDED" -> "an Folgeschicht übergeben"
                "SOLVED" -> "erfolgreich abgeschlossen"
                else -> "gescheitert"
            }
        body.addView(ui.text("${c.location} · ${c.id} · $status", 12f, ui.muted).apply { setPadding(0, ui.dp(4), 0, ui.dp(6)) })
        if (c.outcome.isNotBlank())
            body.addView(ui.text(c.outcome, 12f, if (c.status == "SOLVED") ui.mint else ui.red, true).apply { setPadding(0, 0, 0, ui.dp(6)) })
        body.addView(ui.text("Anrufmeldungen: ${c.duplicateCallers}" + (if (c.disconnected) " · Verbindung abgebrochen" else "") +
            (if (c.addressUncertain) " · Adresse unbestätigt" else ""), 12f, if (c.disconnected || c.addressUncertain) ui.amber else ui.muted))
        if ((c.disconnected || c.addressUncertain) && !c.callbackDone && c.status in listOf("OPEN", "DRIVING", "WORKING"))
            body.addView(ui.button("Rückruf · Adresse und Angaben klären") {
                if (game.callback(c)) { onChanged(); render() } else toast(game.lastError)
            })
        decisionCard(c)
        adviceCard(c)
        // Caller dialogue (scripted scenarios) or the original call description.
        val lines = Scenarios.find(c.scenario)?.caller ?: listOf("„${c.description}“")
        for (line in lines)
            body.addView(
                ui.text(line, 13f, ui.ink).apply {
                    setPadding(ui.dp(10), ui.dp(7), ui.dp(10), ui.dp(7))
                    background = ui.bg(Color.rgb(16, 34, 48), Color.rgb(16, 34, 48), 10)
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(4) },
            )
        if (c.notes.isNotEmpty()) {
            body.addView(ui.text("Entscheidungen", 12f, ui.muted, true).apply { setPadding(0, ui.dp(6), 0, ui.dp(2)) })
            for (n in c.notes) body.addView(ui.text("• $n", 12f, if (n.startsWith("⏱")) ui.amber else ui.muted))
        }
        // Requirements with coverage.
        val need = c.requiredTypes
        val stillOpen = game.remainingTypes(c)
        val left = stillOpen.toMutableList()
        val reqRow = ui.row().apply { setPadding(0, ui.dp(8), 0, ui.dp(4)) }
        reqRow.addView(ui.text("Benötigt  ", 12f, ui.muted, true))
        for (type in need) {
            val open = left.remove(type)
            reqRow.addView(
                ui.chip((if (open) "○ " else "✓ ") + ui.roleName(type), if (open) ui.amber else ui.mint, !open),
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) },
            )
        }
        body.addView(reqRow)
        // Assigned units.
        val assigned = c.assignedUnits.mapNotNull { id -> game.state.units.find { it.id == id } }
        if (assigned.isNotEmpty()) {
            body.addView(ui.text("Eingesetzte Kräfte", 13f, ui.mint, true).apply { setPadding(0, ui.dp(8), 0, ui.dp(4)) })
            for (u in assigned) {
                val r = ui.row().apply { setPadding(0, ui.dp(3), 0, ui.dp(3)) }
                val state =
                    when (u.status) {
                        "DRIVING" -> if (u.readinessLeft > 0) "rüstet aus · ${u.readinessLeft + u.travelLeft} Min" else "Anfahrt · ${u.travelLeft} Min"
                        "ON_SCENE" -> "vor Ort · wartet auf weitere Kräfte"
                        "WORKING" -> "arbeitet · noch ${u.workLeft} Min"
                        else -> u.status
                    }
                r.addView(ui.text("${u.callsign} · FMS ${game.fms(u)}\n${ui.roleName(u.type)} · $state", 12f, ui.ink).apply {
                    setOnClickListener { onUnitDetails(u) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (game.state.phase == "RUNNING" && c.status in listOf("DRIVING", "WORKING"))
                    r.addView(ui.button("Abziehen") {
                        if (game.recall(u)) {
                            toast("${u.callsign} rückt ab und ist wieder alarmierbar.")
                            onChanged()
                            render()
                        }
                    }, LinearLayout.LayoutParams(-2, ui.dp(40)))
                body.addView(r)
            }
        }
        // Candidates.
        val canDispatch = game.state.phase == "RUNNING" && c.status in listOf("OPEN", "DRIVING", "WORKING")
        if (canDispatch) {
            val title = if (stillOpen.isEmpty()) "Verstärkung (optional)" else "Kräfte auswählen"
            body.addView(ui.text(title, 13f, ui.cyan, true).apply { setPadding(0, ui.dp(10), 0, ui.dp(4)) })
            body.addView(ui.button(if (specialRights) "☑ Mit Sonderrechten · Blaulicht/Martinshorn" else "☐ Ohne Sonderrechte") {
                specialRights = !specialRights
                render()
            })
            body.addView(ui.text(if (specialRights) "Schneller; kleines Unfall- und Lärmrisiko, mehr Verschleiß." else "Ruhige Anfahrt, geringerer Verschleiß; längere Fahrzeit.", 11f, ui.muted))
            val candidates = game.candidates(c).map { it.first to game.eta(it.first, c, specialRights) }.sortedBy { it.second }
            selected.retainAll(candidates.map { it.first.id }.toSet() + bound(c).map { it.first.id }.toSet())
            if (candidates.isEmpty())
                body.addView(ui.text("Keine freie Einheit in diesem Land. Kräfte zurückholen oder gebundene Einheiten umdisponieren.", 12f, ui.amber))
            val needLeft = game.remainingTypes(c)
            for ((u, eta) in candidates.take(14)) body.addView(candidateRow(c, u, eta, needLeft, null))
            body.addView(ui.text(if (showBound) "▾ Gebundene Kräfte (umdisponieren)" else "▸ Gebundene Kräfte umdisponieren", 12f, ui.muted, true).apply {
                setPadding(0, ui.dp(10), 0, ui.dp(6))
                setOnClickListener {
                    showBound = !showBound
                    render()
                }
            })
            if (showBound) {
                val list = bound(c)
                if (list.isEmpty()) body.addView(ui.text("Keine gebundenen Einheiten mit niedrigerer oder gleicher Priorität.", 12f, ui.muted))
                for ((u, eta, from) in list.take(10)) body.addView(candidateRow(c, u, eta, needLeft, from))
            }
            // Footer: one radio order for all ticked units.
            val chosen = selected.mapNotNull { id -> game.state.units.find { it.id == id } }
            val slowest = chosen.maxOfOrNull { game.eta(it, c, specialRights) }
            footer.addView(
                ui.button(
                    if (chosen.isEmpty()) "Einheiten wählen" else "Entsenden (${chosen.size}) · ${slowest} Min",
                    primary = true,
                    enabled = chosen.isNotEmpty(),
                ) { send(c, chosen) },
                LinearLayout.LayoutParams(0, ui.dp(48), 1f),
            )
        } else {
            footer.addView(ui.button("Schließen") { close(); onChanged() }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        }
        footer.addView(ui.button("Ort") { map.focus(c.lon, c.lat) }.apply { contentDescription = "Auf Einsatzort zoomen" },
            LinearLayout.LayoutParams(ui.dp(64), ui.dp(48)).apply { marginStart = ui.dp(8) })
        map.previewUnits = selected.toSet()
        map.invalidate()
        scroll.post { scroll.scrollTo(0, y) }
    }

    /** Short, visible consequences of an option (no hidden numbers). */
    private fun summary(e: Effect): String {
        val parts = mutableListOf<String>()
        if (e.risk <= -.1) parts.add("Risiko ↓↓") else if (e.risk < 0) parts.add("Risiko ↓")
        if (e.risk >= .1) parts.add("Risiko ↑↑") else if (e.risk > 0) parts.add("Risiko ↑")
        if (e.work > 0) parts.add("+${e.work} Min vor Ort") else if (e.work < 0) parts.add("${e.work} Min vor Ort")
        if (e.time > 0) parts.add("Frist +${e.time}") else if (e.time < 0) parts.add("Frist ${e.time}")
        for (t in e.add) parts.add("+ ${ui.roleName(t)}")
        if (e.prio in 1..3) parts.add("→ P${e.prio}")
        if (e.cost > 0) parts.add("${e.cost} €")
        if (e.rep > 0) parts.add("Ruf +${e.rep}") else if (e.rep < 0) parts.add("Ruf ${e.rep}")
        if (e.flag.isNotBlank()) parts.add("mögliche Folgeermittlung")
        return parts.joinToString(" · ").ifEmpty { "ohne Zusatzaufwand" }
    }

    /** Pending decision stage with all options; locked options show why. */
    private fun decisionCard(c: IncidentState) {
        val stage = game.pendingStage(c) ?: return
        if (c.status !in listOf("OPEN", "DRIVING", "WORKING") || game.state.phase != "RUNNING") return
        val card = ui.column().apply {
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
            background = ui.bg(Color.rgb(48, 38, 14), ui.amber, 12)
        }
        val left = (GameController.DECISION_TIMEOUT - (c.age - c.pendingAge)).coerceAtLeast(0)
        val head =
            when (stage.trigger) {
                "CALL" -> "☎ Anrufannahme"
                "ARRIVAL" -> "📻 Lage vor Ort"
                else -> "📻 Lageänderung"
            }
        card.addView(ui.text("$head · Entscheidung in $left Min", 12f, ui.amber, true))
        card.addView(ui.text(stage.text, 14f, ui.ink, true).apply { setPadding(0, ui.dp(3), 0, ui.dp(6)) })
        for (o in stage.options) {
            val (ok, why) = game.optionAvailable(c, o)
            val b = ui.button(o.label + "\n" + if (ok) summary(o.effect) else "🔒 $why", enabled = ok) {
                if (game.choose(c, o.id)) {
                    toast(o.effect.note)
                    onChanged()
                    render()
                } else toast(game.lastError)
            }
            b.textSize = 12f
            b.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6))
            card.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(5) })
        }
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6); bottomMargin = ui.dp(4) })
    }

    /** Units bound to other calls of the same or lower urgency, eligible for re-dispatch. */
    private fun bound(c: IncidentState): List<Triple<UnitState, Int, IncidentState>> =
        game.state.units
            .filter { it.region == c.region && it.incidentId.isNotBlank() && it.incidentId != c.id && it.status in listOf("DRIVING", "ON_SCENE") }
            .mapNotNull { u ->
                val from = game.state.incidents.find { it.id == u.incidentId } ?: return@mapNotNull null
                if (from.priority < c.priority) null else Triple(u, game.eta(u, c, specialRights), from)
            }
            .sortedBy { it.second }

    private fun candidateRow(c: IncidentState, u: UnitState, eta: Int, need: List<String>, from: IncidentState?): View {
        val fit =
            when {
                need.contains(u.type) -> "passt" to ui.mint
                need.any { game.canHandle(u, it) } -> "kann übernehmen" to ui.cyan
                need.isEmpty() -> "Verstärkung" to ui.cyan
                else -> "ungeeignet" to ui.muted
            }
        val usable = fit.first != "ungeeignet"
        val isSel = u.id in selected
        val r = ui.row().apply {
            setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
            background = ui.bg(if (isSel) Color.rgb(34, 58, 30) else Color.rgb(14, 30, 42), if (isSel) ui.yellow else Color.rgb(28, 48, 62), 10)
            minimumHeight = ui.dp(52)
            alpha = if (usable) 1f else .55f
        }
        r.addView(ui.text(if (isSel) "☑" else "☐", 20f, if (isSel) ui.yellow else ui.muted, true), LinearLayout.LayoutParams(ui.dp(30), -2))
        val info = ui.column()
        info.addView(ui.text("${u.callsign} · FMS ${game.fms(u)}", 13f, ui.ink, true))
        val extra =
            when {
                from != null -> " · bindet ${from.title}"
                u.status == "RETURNING" -> " · auf Rückfahrt"
                !game.inScope(u.lat, u.lon) -> " · überörtlich"
                else -> ""
            }
        val crew = u.officers.size
        val fatigue = u.officers.minOfOrNull { it.energy } ?: 100
        val fee = game.deploymentCost(u.type)
        val special = if (fee > 0) " · ${fee} € · danach ${game.recoveryMinutes(u.type)} Min Erholung" else ""
        info.addView(ui.text("${ui.roleName(u.type)} · $crew Pers. · Kraft $fatigue%$extra$special", 11f, ui.muted))
        r.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val right = ui.column().apply { gravity = Gravity.END }
        right.addView(ui.text("$eta Min", 15f, if (eta <= 10) ui.mint else if (eta <= 25) ui.amber else ui.red, true))
        right.addView(ui.text(fit.first, 10f, fit.second, true))
        r.addView(right)
        r.setOnClickListener {
            if (!usable) {
                toast("${ui.roleName(u.type)} kann diese Anforderung nicht erfüllen.")
                return@setOnClickListener
            }
            if (!selected.remove(u.id)) selected.add(u.id)
            render()
        }
        r.setOnLongClickListener {
            onUnitDetails(u)
            true
        }
        return FrameLayout(activity).apply {
            setPadding(0, ui.dp(3), 0, ui.dp(3))
            addView(r)
        }
    }

    private fun adviceCard(c: IncidentState) {
        if (game.state.phase != "RUNNING" || c.status !in listOf("OPEN", "DRIVING", "WORKING")) return
        if (game.remainingTypes(c).isEmpty()) return
        val advice = DispatchAdvisor.plan(game, c, specialRights)
        val card = ui.column().apply {
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
            background = ui.bg(Color.rgb(14, 39, 48), ui.cyan, 10)
        }
        card.addView(ui.text("Kostenschonender Vorschlag", 13f, ui.cyan, true))
        val summary = if (advice.picks.isEmpty()) "Keine passende freie Besetzung verfügbar."
            else "${advice.picks.size} Kräfte · alle vorgeschlagenen vor Ort in ${advice.arrival} Min · ${advice.fee} € Einsatzkosten"
        card.addView(ui.text(summary, 12f, ui.ink))
        card.addView(ui.text("Zuerst Bedarf abdecken, dann Einsatzkosten sparen; bei gleichen Kosten kurze Anfahrt und passende Fachkräfte. Keine automatische Entsendung.", 11f, ui.muted))
        if (advice.missing.isNotEmpty()) card.addView(ui.text("Weiter offen: " + advice.missing.joinToString { ui.roleName(it) }, 12f, ui.amber, true))
        for (warning in advice.warnings) card.addView(ui.text(warning, 12f, ui.amber))
        if (advice.picks.isNotEmpty()) {
            val names = advice.picks.mapNotNull { p -> game.state.units.find { it.id == p.unitId }?.let { "${it.callsign}: ${p.eta} Min" } }
            card.addView(ui.text(names.joinToString(" · "), 11f, ui.muted))
            card.addView(ui.button("Vorschlag auswählen · ${advice.picks.size} Kräfte") {
                selected.clear()
                selected.addAll(DispatchAdvisor.plan(game, c, specialRights).picks.map { it.unitId })
                render()
            })
        }
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8); bottomMargin = ui.dp(8) })
    }

    private fun send(c: IncidentState, units: List<UnitState>) {
        var sent = 0
        for (u in units) {
            val ok = if (u.incidentId.isNotBlank()) game.redirect(u, c, specialRights) else game.dispatch(c, u, specialRights)
            if (ok) sent++
        }
        selected.clear()
        if (sent == 0) toast(game.lastError.ifBlank { "Keine der gewählten Einheiten konnte ausrücken." })
        else if (sent < units.size) toast("$sent von ${units.size} Einheiten unterwegs – die übrigen sind nicht mehr verfügbar.")
        onChanged()
        render()
    }
}
