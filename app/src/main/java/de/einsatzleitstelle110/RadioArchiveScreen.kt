package de.einsatzleitstelle110

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.text.Editable
import android.text.TextWatcher

/** Native, virtualized history. Live traffic remains on the regular prioritized audio channel. */
internal class RadioArchiveScreen(
    activity: Activity,
    private val state: GameState,
    private val onReplay: (RadioEntry) -> Unit,
) {
    private val ui = UiKit(activity)
    val dialog = Dialog(activity)
    private val search = EditText(activity).apply {
        hint = "Rufname, Meldung oder Einsatz suchen"
        setTextColor(ui.ink)
        setHintTextColor(ui.muted)
        inputType = InputType.TYPE_CLASS_TEXT
        minHeight = ui.dp(48)
        isSingleLine = true
    }
    private val noResults = ui.text("", 14f, ui.muted).apply { gravity = Gravity.CENTER }
    private val adapter = RadioRows()
    private var priority: RadioPriority? = null
    private var lastEntry: RadioEntry? = null
    private val list = ListView(activity).apply {
        adapter = this@RadioArchiveScreen.adapter
        contentDescription = "Funkmeldungen"
    }

    init {
        val root = ui.column().apply {
            background = ui.bg(ui.panel, ui.edge, 18)
            setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
            isFocusableInTouchMode = true
        }
        val header = ui.row()
        header.addView(ui.text("Funkarchiv", 17f, ui.cyan, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ui.button("Schließen") { dialog.dismiss() })
        root.addView(header)
        root.addView(search)
        val filters = ui.row()
        val filter = ui.button("Alle Prioritäten") {
            val options = listOf(null) + RadioPriority.entries
            val labels = options.map { it?.let(RadioArchive::label) ?: "Alle Prioritäten" }
            AlertDialog.Builder(activity).setTitle("Priorität")
                .setSingleChoiceItems(labels.toTypedArray(), options.indexOf(priority)) { picker, selected ->
                    priority = options[selected]
                    (filters.getChildAt(0) as android.widget.TextView).apply {
                        text = labels[selected]
                        contentDescription = "Prioritätsfilter · ${labels[selected]}"
                    }
                    picker.dismiss()
                    updateResults(true)
                }.setNegativeButton("Schließen", null).show()
        }
        filters.addView(filter)
        if (state.logs.isNotEmpty()) {
            filters.addView(ui.button("Tagesprotokoll") {
                AlertDialog.Builder(activity).setTitle("Tagesprotokoll")
                    .setMessage(state.logs.joinToString("\n\n"))
                    .setPositiveButton("Schließen", null).show()
            })
        }
        root.addView(filters)
        val results = android.widget.FrameLayout(activity)
        results.addView(noResults, android.widget.FrameLayout.LayoutParams(-1, -1))
        results.addView(list, android.widget.FrameLayout.LayoutParams(-1, -1))
        root.addView(results, LinearLayout.LayoutParams(-1, 0, 1f))
        list.emptyView = noResults
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateResults(true)
        })
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setGravity(Gravity.BOTTOM)
        root.requestFocus()
        updateResults(true)
        dialog.show()
        dialog.window?.setLayout(-1, (activity.resources.displayMetrics.heightPixels * .9).toInt())
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun refresh() {
        if (dialog.isShowing && lastEntry !== state.radioArchive.lastOrNull()) updateResults(false)
    }

    private fun updateResults(resetScroll: Boolean) {
        lastEntry = state.radioArchive.lastOrNull()
        val firstVisible = if (resetScroll) null else adapter.entries.getOrNull(list.firstVisiblePosition)
        val offset = list.getChildAt(0)?.top ?: 0
        adapter.entries = RadioArchive.filter(state.radioArchive, search.text.toString(), priority)
        noResults.text = if (state.radioArchive.isEmpty()) "Noch keine Funkmeldungen" else "Keine passenden Funkmeldungen"
        adapter.notifyDataSetChanged()
        if (resetScroll) list.setSelection(0)
        else if (firstVisible != null) {
            val position = adapter.entries.indexOfFirst { it === firstVisible }
            if (position >= 0) list.setSelectionFromTop(position, offset)
        }
    }

    private inner class RadioRows : BaseAdapter() {
        var entries = emptyList<RadioEntry>()
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val row = (recycled as? LinearLayout) ?: ui.column().apply {
                setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                minimumHeight = ui.dp(56)
                addView(ui.row().apply {
                    addView(ui.text("", 12f, ui.muted), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(ui.button("Anhören") {})
                })
                addView(ui.text("", 14f, ui.ink, true))
                addView(ui.text("", 13f, ui.ink))
            }
            val entry = getItem(position)
            val heading = row.getChildAt(0) as LinearLayout
            (heading.getChildAt(0) as android.widget.TextView).apply {
                text = listOfNotNull("Tag ${entry.day} · ${entry.clock}", RadioArchive.label(entry.priority), entry.incidentId).joinToString(" · ")
                setTextColor(if (entry.priority == RadioPriority.URGENT) ui.red else ui.muted)
            }
            (row.getChildAt(1) as android.widget.TextView).text = listOfNotNull(entry.callsign, entry.organisation, entry.title).joinToString(" · ")
            (row.getChildAt(2) as android.widget.TextView).text = entry.message
            heading.getChildAt(1).apply {
                contentDescription = "Anhören · ${entry.title}"
                setOnClickListener { onReplay(entry) }
            }
            return row
        }
    }
}
