package de.einsatzleitstelle110

import android.app.Activity
import android.app.Dialog
import android.widget.LinearLayout
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Native day report. Reads immutable report values and performs nextDay exactly once on tap. */
class ShiftReportScreen(
    private val activity: Activity,
    private val game: GameController,
    private val onNext: () -> Unit,
) {
    private val ui = UiKit(activity)
    private val format = NumberFormat.getIntegerInstance(Locale.GERMANY)
    private fun euro(value: Int) = "${format.format(value)} €"

    fun show(): Dialog? {
        val report = game.state.reports.firstOrNull() ?: return null
        val body = ui.column()
        body.addView(ui.text("Dienst abgeschlossen", 22f, ui.ink, true))
        body.addView(ui.text("TAG ${report.day} · ${report.duration / 60} Stunden", 13f, ui.cyan))
        val rows = listOf(
            "Notrufe" to "${report.served}",
            "Erfolgreich" to "${report.solved}",
            "Fehlgeschlagen" to "${report.failed}",
            "Übergeben" to "${report.handed}",
            "Einnahmen" to euro(report.income),
            "Personal / Betrieb" to euro(report.expenses),
            "Ansehen" to "${game.state.reputation}%",
            "Aktuelles Budget" to euro(game.state.budget),
            "Gehälter" to euro(report.wages),
            "Wartung" to euro(report.maintenance),
            "Treibstoff" to euro(report.fuel),
            "Gebäude" to euro(report.buildings),
            "Personalwechsel" to "${report.rotations}",
            "SEK-/MEK-Aufträge" to "${report.specialDispatches}",
            "Spezialkräfte-Kosten" to euro(report.specialCosts),
            "Eigene Entscheidungen" to "${report.decisions}",
            "Sondersignal-Vorfälle" to "${report.signalIncidents}",
            "Müdigkeit" to "${report.fatigue}%",
        )
        rows.forEach { (name, value) -> line(body, name, value) }
        val costs = game.state.chief.dailyCosts.firstOrNull { it.day == report.day }
        if (costs != null) {
            heading(body, "Eigene Dienststellen · ${euro(costs.total)}")
            note(body, "In den Gesamtkosten enthalten: Gehälter ${euro(costs.wages)}, Wartung ${euro(costs.maintenance)}, Treibstoff ${euro(costs.fuel)}, Gebäude ${euro(costs.buildings)}.")
        }
        if (game.state.chief.insolvencyWarning.isNotBlank()) {
            body.addView(ui.text(game.state.chief.insolvencyWarning, 14f, ui.red, true).apply { setPadding(0, ui.dp(10), 0, ui.dp(8)) })
        }
        note(body, "Fahrzeiten berücksichtigen Tageszeit und Wetter als Spielwerte. Berufsverkehr, Regen und Glätte verlängern die Anfahrt; Sonderrechte haben eigene Kosten und Risiken.")
        val closed = game.state.incidents.filter { it.region == report.region && it.closedAt in report.startAbsolute..report.endAbsolute }.sortedBy { it.closedAt }
        val arrivals = closed.mapNotNull { it.firstArrival.takeIf { arrival -> arrival >= 0 } }
        if (arrivals.isNotEmpty()) line(body, "Ø Eintreffzeit", "${arrivals.average().roundToInt()} Min")
        if (closed.isNotEmpty()) {
            heading(body, "Einsatzbilanz – warum es so ausging")
            closed.takeLast(20).forEach { incident ->
                body.addView(ui.text("${if (incident.status == "SOLVED") "✓" else "✗"} P${incident.priority} ${incident.title} · ${incident.location}", 13f,
                    if (incident.status == "SOLVED") ui.mint else ui.red, true).apply { setPadding(0, ui.dp(6), 0, ui.dp(2)) })
                if (incident.outcome.isNotBlank()) note(body, incident.outcome)
            }
        }
        if (game.state.mode == "CAREER") {
            val next = game.careerLevels.firstOrNull { it.level == game.state.careerLevel + 1 }
            note(body, if (next == null) "Karriere: höchste Stufe erreicht."
                else "Karriere: Stufe ${game.state.careerLevel}. Für „${next.name}“: ${next.shifts} Schichten (${game.state.completedShifts}), Ruf ≥ ${next.reputation} (${game.state.reputation}).")
        }
        if (report.trainingDone.isNotEmpty()) {
            heading(body, "Abgeschlossene Lehrgänge")
            note(body, report.trainingDone.joinToString("\n"))
        }
        note(body, "Ausrüstung, Personal, Lehrgänge und offene Einsatzlagen werden übernommen.")
        lateinit var dialog: Dialog
        var advanced = false
        var logDialog: Dialog? = null
        body.addView(ui.button("Funkprotokoll ansehen") {
            val log = ui.column()
            game.state.logs.take(150).forEach { log.addView(ui.text(it, 12f, ui.ink).apply { setPadding(0, ui.dp(5), 0, ui.dp(5)) }) }
            logDialog?.dismiss()
            logDialog = showShiftScreenDialog(activity, "Funkprotokoll", log)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
        body.addView(ui.button("Nächsten Tag vorbereiten", true) {
            if (advanced || game.state.phase != "DEBRIEF") return@button
            advanced = true
            game.nextDay()
            dialog.dismiss()
            onNext()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        dialog = showShiftScreenDialog(activity, "Tagesauswertung", body)
        dialog.setOnDismissListener { logDialog?.dismiss() }
        return dialog
    }

    private fun line(body: LinearLayout, label: String, value: String) {
        val row = ui.row()
        row.contentDescription = "$label: $value"
        row.addView(ui.text(label, 14f, ui.muted), LinearLayout.LayoutParams(0, ui.dp(40), 1f))
        row.addView(ui.text(value, 16f, ui.ink, true))
        body.addView(row)
    }
    private fun heading(body: LinearLayout, value: String) {
        body.addView(ui.text(value, 16f, ui.cyan, true).apply { setPadding(0, ui.dp(12), 0, ui.dp(6)) })
    }
    private fun note(body: LinearLayout, value: String) {
        body.addView(ui.text(value, 13f, ui.muted).apply { setPadding(0, ui.dp(5), 0, ui.dp(6)) })
    }
}
