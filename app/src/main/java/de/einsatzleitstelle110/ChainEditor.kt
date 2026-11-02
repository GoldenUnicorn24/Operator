package de.einsatzleitstelle110

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Editable copy of a chain stage. */
class DraftStage(
    var id: String,
    var title: String,
    var radio: String = "",
    var types: MutableList<String> = mutableListOf("STREIFE"),
    var duration: Int = 20,
    var priority: Int = 2,
    var deadline: Int = 120,
    var onSuccess: String = "",
    var onFailure: String = "",
)

/**
 * Graph of the chain: stages as boxes by depth, green arrows for "success", red for "failure".
 * Tapping a box selects it.
 */
class ChainGraphView(context: Context, private val stages: () -> List<DraftStage>, private val selected: () -> String, private val onSelect: (String) -> Unit) : View(context) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boxes = HashMap<String, RectF>()
    private val boxW = 132 * d
    private val boxH = 58 * d
    private val gapX = 18 * d
    private val gapY = 46 * d

    private fun layout(): Map<String, Pair<Int, Int>> {
        val list = stages()
        if (list.isEmpty()) return emptyMap()
        val byId = list.associateBy { it.id }
        val depth = LinkedHashMap<String, Int>()
        val queue = ArrayDeque<String>()
        depth[list.first().id] = 0
        queue.add(list.first().id)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val n = byId[id] ?: continue
            for (next in listOf(n.onSuccess, n.onFailure)) if (next.isNotBlank() && next !in depth && next in byId) {
                depth[next] = depth.getValue(id) + 1
                queue.add(next)
            }
        }
        val maxDepth = (depth.values.maxOrNull() ?: 0) + 1
        list.filter { it.id !in depth }.forEach { depth[it.id] = maxDepth }
        val col = HashMap<Int, Int>()
        return depth.mapValues { (_, lvl) -> lvl to col.merge(lvl, 1, Int::plus)!! - 1 }
    }

    override fun onMeasure(w: Int, h: Int) {
        val pos = layout()
        val levels = (pos.values.maxOfOrNull { it.first } ?: 0) + 1
        val cols = pos.values.groupBy { it.first }.values.maxOfOrNull { it.size } ?: 1
        setMeasuredDimension((cols * (boxW + gapX) + gapX).toInt().coerceAtLeast(MeasureSpec.getSize(w)), (levels * (boxH + gapY) + gapY / 2).toInt())
    }

    override fun onDraw(c: Canvas) {
        val pos = layout()
        val list = stages().associateBy { it.id }
        boxes.clear()
        for ((id, lc) in pos) {
            val x = gapX + lc.second * (boxW + gapX)
            val y = gapY / 3 + lc.first * (boxH + gapY)
            boxes[id] = RectF(x, y, x + boxW, y + boxH)
        }
        // Edges first.
        for ((id, n) in list) {
            val from = boxes[id] ?: continue
            for ((target, color, label) in listOf(Triple(n.onSuccess, 0xff6ee7b7.toInt(), "✓"), Triple(n.onFailure, 0xffff7184.toInt(), "✗"))) {
                val to = boxes[target] ?: continue
                p.reset()
                p.isAntiAlias = true
                p.color = color
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2.4f * d
                val sx = from.centerX() + if (label == "✓") -18 * d else 18 * d
                val sy = from.bottom
                val ex = to.centerX()
                val ey = to.top
                val path = Path().apply {
                    moveTo(sx, sy)
                    cubicTo(sx, sy + gapY * .6f, ex, ey - gapY * .6f, ex, ey)
                }
                c.drawPath(path, p)
                p.style = Paint.Style.FILL
                c.drawCircle(ex, ey, 3.5f * d, p)
                p.textSize = 13 * d
                p.typeface = Typeface.DEFAULT_BOLD
                c.drawText(label, (sx + ex) / 2 + 4 * d, (sy + ey) / 2, p)
            }
        }
        for ((id, r) in boxes) {
            val n = list[id] ?: continue
            val sel = id == selected()
            val first = id == stages().first().id
            p.reset()
            p.isAntiAlias = true
            p.color = if (sel) 0xff3a3410.toInt() else 0xff10263a.toInt()
            c.drawRoundRect(r, 10 * d, 10 * d, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = (if (sel) 2.6f else 1.4f) * d
            p.color = if (sel) 0xfff5e663.toInt() else if (first) 0xff50cbed.toInt() else 0xff3a5a70.toInt()
            c.drawRoundRect(r, 10 * d, 10 * d, p)
            p.style = Paint.Style.FILL
            p.color = 0xffe6f0f8.toInt()
            p.textSize = 12 * d
            p.typeface = Typeface.DEFAULT_BOLD
            var t = (if (first) "▶ " else "") + "P${n.priority} " + n.title.ifBlank { "(ohne Titel)" }
            while (p.measureText(t) > boxW - 14 * d && t.length > 4) t = t.dropLast(2) + "…"
            c.drawText(t, r.left + 7 * d, r.top + 20 * d, p)
            p.typeface = Typeface.DEFAULT
            p.textSize = 10 * d
            p.color = 0xff9bb4c6.toInt()
            var s = n.types.joinToString("+") { it.take(4) } + " · ${n.duration}′ · Frist ${n.deadline}′"
            while (p.measureText(s) > boxW - 14 * d && s.length > 4) s = s.dropLast(2) + "…"
            c.drawText(s, r.left + 7 * d, r.top + 38 * d, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            boxes.entries.firstOrNull { it.value.contains(e.x, e.y) }?.let {
                onSelect(it.key)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/**
 * Visual scenario editor: any number of stages (up to 20) with directed success/failure branches,
 * required forces, durations, timers, priorities, caller and radio texts, a real place on the
 * map, live validation with readable errors, and saving into the game.
 */
class ChainEditor(
    private val activity: Activity,
    private val game: GameController,
    private val ui: UiKit,
    private val existing: IncidentTemplate?,
    private val onSaved: () -> Unit,
) {
    private val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private var name = existing?.title ?: ""
    private var caller = existing?.description ?: ""
    private var deadline = existing?.deadline ?: 120
    private var reward = existing?.reward ?: 1100
    private var place: MapPlace? = null
    private val stages = mutableListOf<DraftStage>()
    private var selected = ""
    private val root = ui.column()
    private val form = ui.column()
    private val errors = ui.column()
    private lateinit var graph: ChainGraphView
    private var counter = 1

    init {
        if (existing != null && existing.chain.isNotEmpty())
            existing.chain.forEach {
                stages.add(DraftStage(it.id, it.title, it.radio, it.requiredTypes.toMutableList(), it.duration, it.priority, if (it.deadline > 0) it.deadline else 120, it.onSuccess, it.onFailure))
            }
        else if (existing != null)
            stages.add(DraftStage("s1", existing.title, "", existing.requiredTypes.toMutableList(), existing.duration, existing.priority, existing.deadline))
        else stages.add(DraftStage("s1", "", "", mutableListOf("STREIFE"), 20, 2, 120))
        counter = stages.size + 1
        selected = stages.first().id
        if (existing?.lat != null && existing.lon != null) place = MapPlace("Gespeicherter Ort", "fixed", 0, existing.lon, existing.lat)
    }

    fun show() {
        val scroll = ScrollView(activity)
        root.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(24))
        root.setBackgroundColor(Color.rgb(7, 16, 26))
        scroll.addView(root)
        dialog.setContentView(scroll)
        build()
        dialog.show()
    }

    private fun field(label: String, value: String, numeric: Boolean = false, lines: Int = 1, onChange: (String) -> Unit): View {
        val box = ui.column()
        box.addView(ui.text(label, 12f, ui.muted))
        val e = EditText(activity).apply {
            setText(value)
            setTextColor(ui.ink)
            setHintTextColor(ui.muted)
            textSize = 14f
            minLines = lines
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = ui.bg(Color.rgb(16, 32, 46), ui.edge, 8)
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onChange(s.toString())
                    refreshErrors()
                    graph.invalidate()
                }
            })
        }
        box.addView(e, LinearLayout.LayoutParams(-1, -2))
        return box.apply { setPadding(0, ui.dp(4), 0, ui.dp(4)) }
    }

    private fun spinner(values: List<String>, index: Int, onPick: (Int) -> Unit): Spinner =
        Spinner(activity).apply {
            adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, values) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (super.getView(position, convertView, parent) as TextView).apply { setTextColor(ui.ink) }
            }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(index.coerceIn(0, values.size - 1))
            background = ui.bg(Color.rgb(16, 32, 46), ui.edge, 8)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) {
                    onPick(i)
                    refreshErrors()
                    if (::graph.isInitialized) graph.invalidate()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

    private fun build() {
        root.removeAllViews()
        val head = ui.row()
        head.addView(ui.text(if (existing == null) "Neuer Einsatz" else "Einsatz bearbeiten", 20f, ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(ui.button("Schließen") { dialog.dismiss() })
        root.addView(head)
        root.addView(ui.text("Stufen mit Verzweigungen bei Erfolg (✓) und Fehlschlag (✗). Eigene Einsätze werden im Spiel ausgelöst – zufällig oder gezielt.", 12f, ui.muted))
        root.addView(field("Name des Einsatzes", name) { name = it.trim() })
        root.addView(field("Notruftext (was der Anrufer sagt)", caller, lines = 2) { caller = it })
        val row = ui.row()
        row.addView(field("Frist erste Stufe (Min)", deadline.toString(), numeric = true) { deadline = it.toIntOrNull() ?: 0 }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(field("Belohnung (€)", reward.toString(), numeric = true) { reward = it.toIntOrNull() ?: 0 }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(8) })
        root.addView(row)
        val region = game.region()
        val places = MapData.places(activity, region).sortedByDescending { it.population }.take(80)
        root.addView(ui.text("Ort in ${region.name}", 12f, ui.muted).apply { setPadding(0, ui.dp(6), 0, ui.dp(2)) })
        val options = listOf("Zufällig im Zuständigkeitsgebiet") + places.map { it.name } + (place?.takeIf { it.kind == "fixed" }?.let { listOf("Gespeicherter Ort") } ?: emptyList())
        val current = place?.let { pl -> if (pl.kind == "fixed") options.size - 1 else places.indexOfFirst { it.name == pl.name } + 1 } ?: 0
        root.addView(spinner(options, current) { i -> place = if (i == 0) null else if (i <= places.size) places[i - 1] else place }, LinearLayout.LayoutParams(-1, ui.dp(48)))
        root.addView(ui.text("Ablauf", 15f, ui.cyan, true).apply { setPadding(0, ui.dp(12), 0, ui.dp(4)) })
        graph = ChainGraphView(activity, { stages }, { selected }) {
            selected = it
            buildForm()
            graph.invalidate()
        }
        val hs = HorizontalScrollView(activity).apply {
            background = ui.bg(Color.rgb(11, 22, 33), ui.edge, 12)
            addView(graph)
        }
        root.addView(hs, LinearLayout.LayoutParams(-1, -2))
        root.addView(form)
        root.addView(errors)
        val actions = ui.row().apply { setPadding(0, ui.dp(10), 0, 0) }
        actions.addView(ui.button("Speichern", primary = true) { save(false) }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        actions.addView(ui.button("Speichern & auslösen") { save(true) }, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { marginStart = ui.dp(8) })
        root.addView(actions)
        buildForm()
        refreshErrors()
    }

    private fun stage() = stages.first { it.id == selected }

    private fun buildForm() {
        form.removeAllViews()
        val s = stage()
        val first = s.id == stages.first().id
        form.addView(ui.text(if (first) "Erste Stufe (Notrufannahme)" else "Folgestufe", 14f, ui.amber, true).apply { setPadding(0, ui.dp(10), 0, 0) })
        form.addView(field("Titel der Stufe", s.title) { s.title = it.trim() })
        form.addView(field(if (first) "Funkhinweis an die Kräfte (optional)" else "Funk-/Notruftext beim Start dieser Stufe", s.radio, lines = 2) { s.radio = it })
        form.addView(ui.text("Benötigte Kräfte", 12f, ui.muted).apply { setPadding(0, ui.dp(6), 0, 0) })
        val grid = ui.column()
        for (rowTypes in GameController.TYPES.chunked(2)) {
            val r = ui.row()
            for (t in rowTypes) {
                val count = s.types.count { it == t }
                r.addView(ui.button("${ui.roleName(t)}${if (count > 0) " ×$count" else ""}", primary = count > 0) {
                    // Tap cycles 0 → 1 → 2 → 0 units of this type.
                    if (count >= 2 || (count == 1 && t !in listOf("STREIFE", "BEREITSCHAFT"))) s.types.removeAll { it == t } else s.types.add(t)
                    buildForm()
                    refreshErrors()
                    graph.invalidate()
                }.apply { textSize = 11f }, LinearLayout.LayoutParams(0, ui.dp(40), 1f).apply { setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2)) })
            }
            grid.addView(r)
        }
        form.addView(grid)
        val nums = ui.row()
        nums.addView(field("Dauer vor Ort", s.duration.toString(), numeric = true) { s.duration = it.toIntOrNull() ?: 0 }, LinearLayout.LayoutParams(0, -2, 1f))
        if (!first) nums.addView(field("Frist (Timer)", s.deadline.toString(), numeric = true) { s.deadline = it.toIntOrNull() ?: 0 }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(6) })
        form.addView(nums)
        form.addView(spinner(listOf("P1 · akute Gefahr", "P2 · dringend", "P3 · normal"), s.priority - 1) { s.priority = it + 1 }, LinearLayout.LayoutParams(-1, ui.dp(46)))
        fun label(id: String) = stages.firstOrNull { it.id == id }?.title?.ifBlank { "(ohne Titel)" } ?: "–"
        form.addView(ui.text("Bei Erfolg → ${if (s.onSuccess.isBlank()) "Ende" else label(s.onSuccess)}   ·   Bei Fehlschlag → ${if (s.onFailure.isBlank()) "Ende" else label(s.onFailure)}", 12f, ui.ink).apply { setPadding(0, ui.dp(8), 0, ui.dp(4)) })
        val b1 = ui.row()
        b1.addView(ui.button("+ Folge bei Erfolg") { addChild(s, true) }.apply { textSize = 12f }, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
        b1.addView(ui.button("+ Folge bei Fehlschlag") { addChild(s, false) }.apply { textSize = 12f }, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { marginStart = ui.dp(6) })
        form.addView(b1)
        val b2 = ui.row().apply { setPadding(0, ui.dp(6), 0, 0) }
        b2.addView(ui.button("Verbinden…") { connect(s) }.apply { textSize = 12f }, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
        b2.addView(ui.button("Löschen", enabled = !first) { remove(s) }.apply { textSize = 12f }, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { marginStart = ui.dp(6) })
        form.addView(b2)
    }

    private fun addChild(s: DraftStage, success: Boolean) {
        if (stages.size >= 20) {
            Toast.makeText(activity, "Höchstens 20 Stufen.", Toast.LENGTH_SHORT).show()
            return
        }
        val id = "s${counter++}"
        stages.add(DraftStage(id, if (success) "Folgeermittlung" else "Nachsorge nach Fehlschlag", "", mutableListOf(if (success) "KRIPO" else "STREIFE"), 20, if (success) 2 else 2, 120))
        if (success) s.onSuccess = id else s.onFailure = id
        selected = id
        graph.requestLayout()
        buildForm()
        refreshErrors()
    }

    private fun connect(s: DraftStage) {
        val targets = stages.filter { it.id != s.id }
        if (targets.isEmpty()) return
        val labels = targets.flatMap { listOf("✓ Erfolg → ${it.title}", "✗ Fehlschlag → ${it.title}") } + listOf("Beide Verbindungen lösen")
        AlertDialog.Builder(activity)
            .setTitle("Verbindung von „${s.title}“")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i == labels.lastIndex) {
                    s.onSuccess = ""
                    s.onFailure = ""
                } else {
                    val t = targets[i / 2]
                    if (i % 2 == 0) s.onSuccess = t.id else s.onFailure = t.id
                }
                graph.requestLayout()
                buildForm()
                refreshErrors()
            }
            .show()
    }

    private fun remove(s: DraftStage) {
        stages.remove(s)
        stages.forEach {
            if (it.onSuccess == s.id) it.onSuccess = ""
            if (it.onFailure == s.id) it.onFailure = ""
        }
        selected = stages.first().id
        graph.requestLayout()
        buildForm()
        refreshErrors()
    }

    private fun template(): IncidentTemplate {
        val first = stages.first()
        val chain =
            if (stages.size == 1 && first.onSuccess.isBlank() && first.onFailure.isBlank()) emptyList()
            else stages.map { ChainStage(it.id, it.title.ifBlank { name }, it.types.firstOrNull() ?: "STREIFE", it.duration, it.priority, it.onSuccess, it.onFailure, it.types.toList(), it.radio, it.deadline) }
        return IncidentTemplate(
            name,
            first.priority,
            first.types.firstOrNull() ?: "STREIFE",
            first.duration,
            caller.ifBlank { first.radio },
            requiredTypes = first.types.toList(),
            deadline = deadline,
            reward = reward.coerceIn(0, 100000),
            chain = chain,
            region = place?.let { game.state.region } ?: "",
            lat = place?.lat,
            lon = place?.lon,
        )
    }

    private fun problems(): List<String> {
        val t = template()
        return game.validateTemplate(t, existing?.title ?: "") + if (reward !in 0..100000) listOf("Belohnung 0–100.000 €.") else emptyList()
    }

    private fun refreshErrors() {
        errors.removeAllViews()
        val list = problems()
        errors.addView(
            ui.text(if (list.isEmpty()) "✓ Einsatz ist gültig und kann gespeichert werden." else "Bitte noch prüfen:", 13f, if (list.isEmpty()) ui.mint else ui.amber, true).apply { setPadding(0, ui.dp(10), 0, ui.dp(2)) }
        )
        list.distinct().take(8).forEach { errors.addView(ui.text("• $it", 12f, ui.red)) }
    }

    private fun save(trigger: Boolean) {
        val list = problems()
        if (list.isNotEmpty()) {
            Toast.makeText(activity, list.first(), Toast.LENGTH_LONG).show()
            return
        }
        val t = template()
        val ok = if (existing != null && game.state.customTemplates.any { it.title == existing.title }) game.replaceTemplate(existing.title, t) else game.addTemplate(t)
        if (!ok) {
            Toast.makeText(activity, "Speichern nicht möglich.", Toast.LENGTH_LONG).show()
            return
        }
        if (trigger) {
            if (game.state.phase == "RUNNING") {
                try {
                    game.newIncident(t)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(activity, "Ort liegt außerhalb der aktuellen Zuständigkeit – Einsatz gespeichert, aber nicht ausgelöst.", Toast.LENGTH_LONG).show()
                }
            } else Toast.makeText(activity, "Gespeichert. Auslösen ist während einer laufenden Schicht möglich.", Toast.LENGTH_LONG).show()
        }
        dialog.dismiss()
        onSaved()
    }
}
