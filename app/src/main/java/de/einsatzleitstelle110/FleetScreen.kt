package de.einsatzleitstelle110

import android.app.Activity
import android.view.View
import android.widget.LinearLayout

/** Fleet overview; profile/editor actions stay attached to the activity lifecycle. */
class FleetScreen(
    activity: Activity,
    private val game: GameController,
    private val scopeName: String,
    private val vehicleImage: (UnitState) -> View,
    private val profile: (UnitState) -> Unit,
    private val chief: () -> Unit,
    private val patrol: () -> Unit,
    private val editor: () -> Unit,
) {
    private val ui = UiKit(activity)
    fun render(parent: LinearLayout) {
        fun card() = ui.column().apply {
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(14))
            background = ui.bg(ui.panel)
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
        }
        fun LinearLayout.space() = addView(View(context), LinearLayout.LayoutParams(1, ui.dp(8)))
        val header = card()
        header.addView(ui.text("Personal & Fahrzeuge", 21f, ui.ink, true))
        header.addView(ui.text("$scopeName · ${game.freeUnits()} Kräfte verfügbar", 12f, ui.cyan))
        header.space()
        header.addView(ui.button("Polizeichef · Dienststellen, Fuhrpark & Bewerber", true, onClick = chief))
        header.space()
        header.addView(ui.button("Streifenauftrag · mehrere Fahrzeuge", onClick = patrol))
        header.space()
        header.addView(ui.button("Bestehenden Einheiteneditor öffnen", onClick = editor))
        for (unit in game.state.units.filter { it.region == game.state.region && game.inScope(it.lat, it.lon) }) {
            val body = card()
            body.addView(vehicleImage(unit), LinearLayout.LayoutParams(-1, ui.dp(135)))
            body.space()
            body.addView(ui.text(unit.callsign, 18f, ui.ink, true))
            body.addView(ui.text(ui.roleName(unit.type), 12f, ui.cyan))
            val status = when (unit.status) {
                "FREE" -> "Verfügbar"
                "PATROLLING" -> "Auf Streife"
                "CHECKPOINT" -> "Kontrollstelle"
                "DRIVING" -> "Auf Anfahrt"
                "WORKING", "ON_SCENE" -> "Am Einsatzort"
                "RETURNING" -> "Rückfahrt"
                "REST" -> "Ruhezeit"
                "TRAINING" -> "Fortbildung"
                "OFF_DUTY" -> "Dienstfrei"
                else -> unit.status
            }
            body.addView(ui.text("FMS ${game.fms(unit)} · $status · ${unit.officers.size} Beamte · Stufe ${unit.level}", 13f,
                if (game.available(unit)) ui.mint else ui.amber))
            body.space()
            body.addView(ui.button("Personal, Ausstattung & Fortbildung") { profile(unit) })
        }
    }
}
