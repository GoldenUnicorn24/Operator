package de.einsatzleitstelle110

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast

/** Native shift planning, kept separate from the map/activity lifecycle. */
class ShiftPlanningScreen(
    private val activity: Activity,
    private val game: GameController,
    private val scopeName: String,
    private val onChanged: () -> Unit,
    private val onReport: () -> Unit,
) {
    private val ui = UiKit(activity)

    fun show(): Dialog? {
        if (game.state.phase != "PLANNING") {
            if (game.state.phase == "DEBRIEF") onReport()
            else Toast.makeText(activity, "Die aktuelle Schicht läuft bereits.", Toast.LENGTH_LONG).show()
            return null
        }
        val body = ui.column()
        body.addView(ui.text("Schichtplanung", 22f, ui.ink, true))
        body.addView(ui.text(scopeName, 13f, ui.cyan))
        val mode = choice(body, "Spielmodus", listOf("Karriere", "Sandbox"), if (game.state.mode == "CAREER") 0 else 1)
        val duration = choice(body, "Dauer", listOf("8 Stunden", "12 Stunden", "24 Stunden"), listOf(480, 720, 1440).indexOf(game.state.duration))
        val start = choice(body, "Beginn", (0..23).map { "%02d:00".format(it) }, game.state.startMinute / 60)
        val weatherCodes = listOf("CLEAR", "RAIN", "ICE")
        val weather = choice(body, "Wetter", listOf("Trocken", "Regen", "Glätte"), weatherCodes.indexOf(game.state.weather))
        body.addView(ui.text("Berufsverkehr verlangsamt Anfahrten, nachts sind Straßen freier. Regen und Glätte verlangsamen die Fahrt und erhöhen das Unfallaufkommen. Alle Werte sind Spielnäherungen.", 13f, ui.muted).apply {
            setPadding(0, ui.dp(8), 0, ui.dp(8))
        })
        body.addView(ui.text("Ein Dienst zählt als ein Managementtag. Mitternacht löst keinen zusätzlichen Abschluss aus. Bei langen Diensten werden Personalwechsel und Ruhe berücksichtigt.", 13f, ui.muted))
        lateinit var dialog: Dialog
        fun applyPlan() {
            game.state.mode = if (mode.selectedItemPosition == 0) "CAREER" else "SANDBOX"
            game.setShift(start.selectedItemPosition, listOf(8, 12, 24)[duration.selectedItemPosition])
            game.setWeather(weatherCodes[weather.selectedItemPosition])
        }
        body.addView(ui.button("Schicht beginnen", true) {
            if (game.state.phase != "PLANNING") return@button
            applyPlan()
            game.startShift()
            dialog.dismiss()
            onChanged()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) })
        body.addView(ui.button("Nur Planung speichern") {
            if (game.state.phase != "PLANNING") return@button
            applyPlan()
            dialog.dismiss()
            onChanged()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        dialog = showShiftScreenDialog(activity, "Nächster Dienst", body)
        return dialog
    }

    private fun choice(body: LinearLayout, label: String, values: List<String>, selected: Int): Spinner {
        body.addView(ui.text(label, 13f, ui.muted).apply { setPadding(0, ui.dp(12), 0, ui.dp(3)) })
        return Spinner(activity).apply {
            contentDescription = label
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
            setSelection(selected.coerceIn(0, values.lastIndex))
            body.addView(this, LinearLayout.LayoutParams(-1, ui.dp(52)))
        }
    }
}

/** Shared shell returns the actual Dialog so MainActivity/test lifecycle ownership stays intact. */
internal fun showShiftScreenDialog(activity: Activity, title: String, content: View): Dialog {
    val ui = UiKit(activity)
    val dialog = Dialog(activity)
    val outer = ui.column().apply {
        background = ui.bg(ui.panel, ui.edge, 18)
        setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14))
    }
    val heading = ui.row()
    heading.addView(ui.text(title, 14f, ui.cyan, true), LinearLayout.LayoutParams(0, -2, 1f))
    heading.addView(ui.button("Schließen") { dialog.dismiss() }, LinearLayout.LayoutParams(-2, ui.dp(48)))
    outer.addView(heading)
    val scroll = ScrollView(activity).apply {
        contentDescription = title
        addView(content)
    }
    outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(10) })
    dialog.setContentView(outer)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setGravity(Gravity.BOTTOM)
    dialog.show()
    dialog.window?.setLayout(-1, (activity.resources.displayMetrics.heightPixels * .88).toInt())
    return dialog
}
