package de.einsatzleitstelle110

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Explicit save-slot controls; file IO and validation never run on the Android UI thread. */
class SaveSlotsScreen(
    private val activity: Activity,
    private val game: GameController,
    private val onLoaded: (GameState) -> Unit,
) {
    private val ui = UiKit(activity)
    private val main = Handler(Looper.getMainLooper())
    private var dialog: AlertDialog? = null
    private var body: LinearLayout? = null

    fun show() {
        val content = ui.column().apply { setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(16)) }
        body = content
        content.addView(ui.text("Speicherplätze werden geladen …", 14f, ui.muted))
        dialog?.dismiss()
        dialog = AlertDialog.Builder(activity)
            .setTitle("Spielstände · fünf Speicherplätze")
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("Schließen", null)
            .create().also { it.show() }
        refresh()
    }

    private fun alive() = !activity.isFinishing && !activity.isDestroyed && dialog?.isShowing == true

    private fun refresh() {
        worker.execute {
            try {
                val slots = SaveSlots.list(activity.applicationContext)
                main.post { if (alive()) render(slots) }
            } catch (e: Exception) { error(e) }
        }
    }

    private fun render(slots: List<SlotInfo>) {
        val content = body ?: return
        content.removeAllViews()
        content.addView(ui.text("Die laufende automatische Sicherung bleibt erhalten. Laden setzt die Leitstelle auf den gewählten Stand zurück.", 13f, ui.muted))
        for (slot in slots) {
            val row = ui.column().apply {
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
                background = ui.bg(ui.panel)
            }
            row.addView(ui.text("${slot.slot} · ${slot.name}", 16f, ui.ink, true))
            val description = when {
                slot.hasSave -> "Tag ${slot.day} · ${slot.region}" + (slot.savedAt?.let { " · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" } ?: "")
                slot.corrupt -> "Beschädigt · keine lesbare Sicherung"
                else -> "Leer"
            }
            row.addView(ui.text(description, 12f, if (slot.corrupt) ui.red else ui.muted))
            if (slot.recoveredFromBackup) row.addView(ui.text("Letzte gültige Sicherung wird verwendet.", 12f, ui.amber))
            row.addView(ui.button("Auf Platz ${slot.slot} speichern") { nameAndSave(slot) }, buttonParams())
            val actions = ui.row()
            actions.addView(ui.button("Laden", enabled = slot.hasSave) {
                AlertDialog.Builder(activity)
                    .setTitle("${slot.name} laden?")
                    .setMessage("Der aktuelle Dienst wird auf diesen gespeicherten Stand zurückgesetzt.")
                    .setPositiveButton("Laden") { _, _ -> load(slot.slot) }
                    .setNegativeButton("Abbrechen", null).show()
            }, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { marginEnd = ui.dp(6) })
            actions.addView(ui.button("Löschen", enabled = slot.hasSave || slot.corrupt) {
                AlertDialog.Builder(activity).setTitle("Speicherplatz ${slot.slot} löschen?")
                    .setMessage("Dieser Speicherplatz und seine Sicherung werden entfernt.")
                    .setPositiveButton("Löschen") { _, _ ->
                        operation({ SaveSlots.delete(activity.applicationContext, slot.slot) }) { refresh() }
                    }.setNegativeButton("Abbrechen", null).show()
            }, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
            row.addView(actions, buttonParams())
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
        }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) }

    private fun nameAndSave(slot: SlotInfo) {
        val input = EditText(activity).apply {
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(48))
            setText(if (slot.hasSave) slot.name else "Tag ${game.state.day} · ${game.region().name}")
            selectAll()
        }
        val prompt = AlertDialog.Builder(activity)
            .setTitle(if (slot.hasSave || slot.corrupt) "Platz ${slot.slot} überschreiben" else "Platz ${slot.slot} speichern")
            .setView(input)
            .setMessage("Name des Spielstands (bis 48 Zeichen)")
            .setPositiveButton("Speichern", null)
            .setNegativeButton("Abbrechen", null).create()
        prompt.setOnShowListener {
            prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank() || name.any { it.isISOControl() }) {
                    input.error = "Bitte einen Namen ohne Steuerzeichen eingeben."
                    return@setOnClickListener
                }
                // Detach on the game/UI thread before asynchronous disk work starts.
                val frozen = GameSnapshots.capture(game.state)
                prompt.dismiss()
                operation({ SaveSlots.save(activity.applicationContext, slot.slot, name, frozen) }) {
                    Toast.makeText(activity, "Spielstand gespeichert.", Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }
        prompt.show()
    }

    private fun load(slot: Int) {
        operation({ SaveSlots.load(activity.applicationContext, slot) }) { state ->
            dialog?.dismiss()
            onLoaded(state)
        }
    }

    private fun <T> operation(block: () -> T, done: (T) -> Unit) {
        worker.execute {
            try {
                val result = block()
                main.post { if (alive()) done(result) }
            } catch (e: Exception) { error(e) }
        }
    }

    private fun error(e: Exception) {
        main.post {
            if (alive()) Toast.makeText(activity, e.message ?: "Spielstand konnte nicht verarbeitet werden.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "save-slots").apply { isDaemon = true } }
    }
}
