package de.einsatzleitstelle110

import android.app.Activity
import android.widget.LinearLayout
import android.widget.ScrollView

/** Live native incident desk. Filtering and scroll position survive minute-by-minute updates. */
class IncidentDeskScreen(
    activity: Activity, private val game: GameController, private val ui: UiKit,
    private val open: (IncidentState) -> Unit, private val simulate: () -> Unit,
) {
    val root = ScrollView(activity)
    private val body = ui.column().apply { setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)) }
    private var filter = "ACTION"
    private var version = ""
    init { root.addView(body); refresh() }

    fun refresh() {
        val calls = IncidentDesk.ordered(game, game.state.incidents.filter { it.region == game.state.region && game.inScope(it.lat, it.lon) })
        val next = "$filter:${game.state.elapsed}:${game.state.phase}:" + calls.joinToString {
            "${it.id}:${it.status}:${it.pending}:${it.age}:${it.assignedUnits}:${game.remainingTypes(it)}"
        }
        if (next == version) return
        version = next
        val y = root.scrollY
        body.removeAllViews()
        body.addView(ui.text("Lagezentrum", 24f, ui.ink, true))
        body.addView(ui.text("${game.region().name} · Priorität, Fristen und fehlende Kräfte", 12f, ui.muted))
        val active = calls.filter { it.status in listOf("OPEN", "DRIVING", "WORKING") }
        val action = active.count { IncidentDesk.attention(game, it).needsAction }
        body.addView(ui.text("Handlungsbedarf: $action · Aktive Lagen: ${active.size}", 15f,
            if (action > 0) ui.amber else ui.mint, true).apply { setPadding(0, ui.dp(12), 0, ui.dp(8)) })
        val filters = ui.row()
        for ((key, label) in listOf("ACTION" to "Handlungsbedarf", "ACTIVE" to "Alle aktiven", "ARCHIVE" to "Archiv"))
            filters.addView(ui.button(label, filter == key) { filter = key; refresh() },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(4) })
        body.addView(filters)
        body.addView(ui.button("Notruf simulieren") { simulate(); refresh() }.apply {
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        })
        val visible = calls.filter {
            when (filter) {
                "ACTION" -> IncidentDesk.attention(game, it).needsAction
                "ACTIVE" -> it.status in listOf("OPEN", "DRIVING", "WORKING")
                else -> it.status !in listOf("OPEN", "DRIVING", "WORKING")
            }
        }
        if (visible.isEmpty()) body.addView(ui.text(when (filter) {
            "ACTION" -> "Kein Handlungsbedarf. Laufende Einsätze findest du unter Alle aktiven."
            "ACTIVE" -> "Keine aktiven Lagen. Die nächste Meldung erscheint hier."
            else -> "Noch keine abgeschlossenen Einsätze."
        }, 14f, ui.mint).apply { setPadding(0, ui.dp(20), 0, ui.dp(20)) })
        for (call in visible.take(100)) {
            val a = IncidentDesk.attention(game, call)
            val card = ui.column().apply {
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
                background = ui.bg(ui.panel, if (a.order == 0) ui.red else ui.edge)
            }
            card.addView(ui.text("P${call.priority} · ${call.title}", 17f, ui.priorityColor(call.priority), true))
            card.addView(ui.text(a.reason, 13f, if (a.needsAction) ui.amber else ui.mint, true))
            card.addView(ui.text(call.location, 12f, ui.muted))
            if (a.needsAction) {
                val missing = game.remainingTypes(call).groupingBy { it }.eachCount()
                if (missing.isNotEmpty()) card.addView(ui.text("Fehlt: " + missing.entries.joinToString { "${it.value}× ${ui.roleName(it.key)}" }, 12f, ui.muted))
            }
            if (call.outcome.isNotBlank()) card.addView(ui.text(call.outcome, 12f, if (call.status == "SOLVED") ui.mint else ui.red))
            card.addView(ui.button("Einsatzakte öffnen", a.needsAction) { open(call) })
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        }
        root.post { root.scrollTo(0, y) }
    }
}
