package de.einsatzleitstelle110

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast

/** Shared single and multiple-unit patrol orders, with map placement for point orders. */
class FleetOrders(
    private val activity: Activity,
    private val game: GameController,
    private val changed: () -> Unit,
    private val choosePoint: (String, (GeoPoint) -> Unit) -> Unit,
) {
    fun selectUnits() {
        val units = game.state.units.filter { it.region == game.state.region && game.dispatchable(it) }
        val selected = BooleanArray(units.size)
        AlertDialog.Builder(activity).setTitle("Streifenauftrag · Mehrfachauswahl")
            .setMultiChoiceItems(units.map { "${it.callsign} · FMS ${game.fms(it)}" }.toTypedArray(), selected) { _, i, checked -> selected[i] = checked }
            .setPositiveButton("Auftrag wählen") { _, _ -> order(units.filterIndexed { index, _ -> selected[index] }) }
            .setNegativeButton("Schließen", null).show()
    }

    fun order(units: List<UnitState>) {
        if (units.isEmpty()) return
        val modes = listOf("WATCH", "PATROL", "FOCUS", "CHECKPOINT")
        val names = arrayOf("Wache · wartet am Heimatstandort", "Streife im Gebiet · fährt Straßenrunden", "Schwerpunkt · Umgebung eines Punktes", "Kontrollstelle · hält an einem Punkt")
        AlertDialog.Builder(activity).setTitle("Auftrag für ${units.size} Fahrzeug(e)").setItems(names) { _, index ->
            fun apply(point: GeoPoint?) {
                val count = units.count { game.setPatrolMode(it, modes[index], point) }
                Toast.makeText(activity, if (count == units.size) "Auftrag übernommen. Streife kostet Kraft, Treibstoff und Verschleiß." else "$count/${units.size}: ${game.lastError}", Toast.LENGTH_LONG).show()
                changed()
            }
            if (index >= 2) choosePoint("Punkt für ${names[index].substringBefore('·')} durch langes Drücken setzen") { apply(it) }
            else apply(null)
        }.setNegativeButton("Schließen", null).show()
    }
}
