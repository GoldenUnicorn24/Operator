package de.einsatzleitstelle110

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlin.math.max
import org.json.JSONObject

/** Native Android control room. Views, Canvas and private JSON storage only. */
class MainActivity : Activity() {
    private val ink = Color.rgb(230, 240, 248)
    private val muted = Color.rgb(155, 180, 198)
    private val dark = Color.rgb(7, 16, 26)
    private val panel = Color.rgb(14, 30, 44)
    private val edge = Color.rgb(40, 67, 86)
    private val cyan = Color.rgb(80, 203, 237)
    private val mint = Color.rgb(105, 229, 181)
    private val amber = Color.rgb(255, 199, 111)
    private val red = Color.rgb(255, 113, 132)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var game: GameController
    private lateinit var map: TacticalMapView
    private lateinit var audio: NativeAudio
    private lateinit var body: FrameLayout
    private lateinit var navigation: LinearLayout
    private lateinit var clockView: TextView
    private var mapCounter: TextView? = null
    private var mapAction: TextView? = null
    private var incomingView: TextView? = null
    private var tab = "MAP"
    private var saveTick = 0
    private var sheet: Dialog? = null
    private var sheetScroll: ScrollView? = null
    private var sheetName = ""
    private var pageVersion = ""
    private var foreground = true
    private var lastReportDay = -1
    private var lastRosterStatus = ""
    private val SAVE = 701
    private val LOAD = 702
    private val PORTRAIT = 703
    private var pendingOfficer: Officer? = null
    private val images =
        object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private fun rounded(color: Int, border: Int = edge, radius: Int = 14) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), border)
        }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun row() =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun text(value: String, size: Float = 14f, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            setLineSpacing(dp(3).toFloat(), 1f)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

    private fun action(label: String, primary: Boolean = false, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(if (primary) dark else ink)
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            minHeight = dp(48)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background =
                rounded(
                    if (primary) cyan else Color.rgb(25, 48, 65),
                    if (primary) cyan else edge,
                    10,
                )
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener {
                try {
                    onClick()
                } catch (e: IllegalArgumentException) {
                    toast(e.message ?: "Eingabe prüfen")
                }
            }
        }

    private fun title(parent: LinearLayout, heading: String, detail: String = "") {
        parent.addView(text(heading, 23f, ink, true))
        if (detail.isNotBlank()) parent.addView(text(detail, 13f, muted))
    }

    private fun card(parent: LinearLayout): LinearLayout =
        column().also { v ->
            v.background = rounded(panel)
            v.setPadding(dp(16), dp(14), dp(16), dp(14))
            parent.addView(
                v,
                LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(6), dp(12), dp(6)) },
            )
        }

    private fun gap(parent: LinearLayout, height: Int = 10) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun page(): LinearLayout {
        val content = column().apply { setPadding(0, dp(8), 0, dp(20)) }
        body.addView(
            ScrollView(this).apply {
                addView(content)
                isFillViewport = true
            },
            FrameLayout.LayoutParams(-1, -1),
        )
        return content
    }

    private fun statusName(status: String) =
        when (status) {
            "FREE" -> "Verfügbar"
            "DRIVING" -> "Auf Anfahrt"
            "WORKING" -> "Am Einsatzort"
            "ON_SCENE" -> "Vor Ort · wartet auf Unterstützung"
            "OFF_DUTY" -> "Dienstfrei"
            "TRAINING" -> "Fortbildung"
            "REST" -> "Ruhezeit"
            "RETURNING" -> "Rückfahrt"
            "HANDED" -> "Übergeben"
            "OPEN" -> "Notruf offen"
            "SOLVED" -> "Abgeschlossen"
            "FAILED" -> "Fehlgeschlagen"
            else -> status
        }

    private fun choices(values: List<String>) =
        Spinner(this).apply {
            minimumHeight = dp(52)
            adapter =
                object :
                    ArrayAdapter<String>(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        values,
                    ) {
                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup,
                    ): View =
                        (super.getView(position, convertView, parent) as TextView).apply {
                            setTextColor(ink)
                            setBackgroundColor(panel)
                            setPadding(dp(12), dp(12), dp(12), dp(12))
                            textSize = 16f
                        }

                    override fun getDropDownView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup,
                    ): View =
                        (super.getDropDownView(position, convertView, parent) as TextView).apply {
                            setTextColor(ink)
                            setBackgroundColor(panel)
                            setPadding(dp(14), dp(14), dp(14), dp(14))
                            minimumHeight = dp(52)
                        }
                }
        }

    private fun roleName(type: String) =
        when (type) {
            "STREIFE" -> "Streifenpolizei"
            "SEK" -> "SEK"
            "MEK" -> "MEK"
            "KRIPO" -> "Kriminalpolizei"
            "BEREITSCHAFT" -> "Bereitschaftspolizei"
            "HUND" -> "Diensthundestaffel"
            "VERKEHR" -> "Verkehrspolizei"
            "HELI" -> "Luftunterstützung"
            "RETTUNG" -> "Rettungsdienst"
            "FEUERWEHR" -> "Feuerwehr"
            else -> type
        }

    private fun euro(amount: Int) = "%,d €".format(java.util.Locale.GERMANY, amount)

    private fun scopeName() =
        if (game.state.scope == "STATE") game.region().name
        else
            when (game.state.scope) {
                "WANDSBEK" -> "Hamburg · Wandsbek"
                "MITTE" -> "Hamburg · Mitte"
                "ALTONA" -> "Hamburg · Altona"
                "NORD" -> "Hamburg · Nord"
                "EIMSBÜTTEL" -> "Hamburg · Eimsbüttel"
                "HARBURG" -> "Hamburg · Harburg"
                "BERGEDORF" -> "Hamburg · Bergedorf"
                else -> game.state.scope
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        val regions = GeoData.load(this)
        val saved = GameStore.load(this) ?: GameState()
        if (saved.phase == "RUNNING") saved.paused = true
        game = GameController(saved, regions, HamburgStations.load(this))
        game.seedUnitsIfNeeded()
        audio = NativeAudio(this)
        audio.muted = saved.mute
        audio.setVolumes(
            saved.masterVolume,
            saved.radioVolume,
            saved.sfxVolume,
            saved.ambientVolume,
        )
        installNotifications()
        map = TacticalMapView(this, game, { showIncident(it) }, { showUnit(it) })
        val root = column().apply { setBackgroundColor(dark) }
        val header =
            row().apply {
                setPadding(dp(16), dp(9), dp(8), dp(9))
                setBackgroundColor(panel)
            }
        val brand = column()
        brand.addView(text("EINSATZLEITSTELLE 110", 15f, ink, true))
        brand.addView(text("DEUTSCHLAND / DIENSTZENTRALE", 9f, cyan, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        clockView = text("", 12f, mint, true)
        header.addView(clockView)
        header.addView(action("☰") { showSettings() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(68)))
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = row().apply { setBackgroundColor(panel) }
        root.addView(navigation, LinearLayout.LayoutParams(-1, dp(64)))
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        showTab("MAP")
        handler.post(ticker)
        if (saved.phase == "DEBRIEF") handler.post { showDebrief() }
        else if (saved.phase == "PLANNING" && saved.day == 1) handler.post { showWelcome() }
    }

    private fun installNotifications() {
        game.notify = { name, _ ->
            when {
                name.startsWith("Neuer Notruf") -> {
                    audio.cue("CALL")
                    if (name.contains("P1")) audio.cue("P1")
                    else if (name.contains("P2")) audio.cue("P2")
                }
                name == "Einheit unterwegs" -> {
                    audio.cue("RADIO")
                    audio.cue("DISPATCH")
                    audio.cue("DEPARTURE")
                }
                name == "Schicht begonnen" -> {
                    audio.cue("RADIO")
                    audio.cue("AMBIENT")
                }
                name == "Einsatz beendet" -> audio.cue("SUCCESS")
                name == "Einsatz fehlgeschlagen" -> audio.cue("FAILED")
                name == "Schichtende" -> audio.cue("SHIFT")
            }
        }
    }

    private val ticker =
        object : Runnable {
            override fun run() {
                if (isFinishing) return
                val old = game.state.phase
                if (foreground && old == "RUNNING" && !game.state.paused)
                    game.advance(game.state.speed)
                if (old != game.state.phase) save()
                updateLive()
                if (old == "RUNNING" && !game.state.paused && ++saveTick % 15 == 0) save()
                if (
                    old == "RUNNING" &&
                        game.state.phase == "DEBRIEF" &&
                        lastReportDay != game.state.day
                ) {
                    lastReportDay = game.state.day
                    showDebrief()
                }
                if (foreground) handler.postDelayed(this, 1000)
            }
        }

    override fun onPause() {
        if (::audio.isInitialized) audio.pause()
        foreground = false
        handler.removeCallbacks(ticker)
        if (::game.isInitialized) {
            game.state.paused = true
            save()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::audio.isInitialized) audio.resume()
        if (!foreground) {
            foreground = true
            handler.post(ticker)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        sheet?.dismiss()
        if (::audio.isInitialized) audio.close()
        if (::map.isInitialized) map.close()
        images.evictAll()
        super.onDestroy()
    }

    private fun save() {
        try {
            GameStore.saveAsync(this, game.state) {
                handler.post { toast("Speichern fehlgeschlagen: bitte Spielstand exportieren.") }
            }
        } catch (e: Exception) {
            toast("Speichern fehlgeschlagen: bitte Spielstand exportieren.")
        }
    }

    private fun refresh() {
        save()
        showTab(tab)
    }

    private fun updateLive() {
        clockView.text =
            "TAG ${game.state.day}\n${game.clock()} ${if(game.state.phase=="RUNNING")if(game.state.paused)"Pause" else "×${game.state.speed}" else "Planung"}"
        mapCounter?.text =
            "${game.openIncidents()} aktive Notrufe  ·  ${game.freeUnits()} freie Kräfte"
        mapAction?.text =
            when (game.state.phase) {
                "PLANNING" -> "Schicht planen"
                "RUNNING" -> if (game.state.paused) "Fortsetzen" else "Pause"
                else -> "Tagesbericht"
            }
        incomingView?.text =
            game.state.incidents
                .firstOrNull { it.region == game.state.region && it.status == "OPEN" }
                ?.let { "P${it.priority}  ${it.title} · ${it.location}" } ?: "Keine offenen Notrufe"
        if (tab == "MAP") map.invalidate()
        val version =
            game.state.incidents.joinToString { it.id + it.status } +
                game.state.units.joinToString { it.id + it.status + it.trainingLeft }
        if (tab in listOf("CALLS", "UNITS") && sheet?.isShowing != true && version != pageVersion) {
            val old = (body.getChildAt(0) as? ScrollView)?.scrollY ?: 0
            pageVersion = version
            showTab(tab)
            (body.getChildAt(0) as? ScrollView)?.post {
                (body.getChildAt(0) as? ScrollView)?.scrollTo(0, old)
            }
        }
    }

    private fun showTab(next: String) {
        tab = next
        body.removeAllViews()
        navigation.removeAllViews()
        mapCounter = null
        mapAction = null
        incomingView = null
        val labels =
            listOf(
                "MAP" to "Karte",
                "CALLS" to "Notrufe",
                "UNITS" to "Personal",
                "MANAGE" to "Dienst",
                "EDITOR" to "Editor",
            )
        for ((key, name) in labels) {
            val b = action(name) { showTab(key) }
            b.textSize = 11f
            b.background =
                rounded(
                    if (key == tab) Color.rgb(21, 73, 93) else panel,
                    if (key == tab) cyan else panel,
                    0,
                )
            navigation.addView(b, LinearLayout.LayoutParams(0, -1, 1f))
        }
        when (tab) {
            "MAP" -> showMap()
            "CALLS" -> showCalls()
            "UNITS" -> showUnits()
            "MANAGE" -> showManage()
            "EDITOR" -> showEditor()
        }
        updateLive()
    }

    private fun showMap() {
        (map.parent as? ViewGroup)?.removeView(map)
        body.addView(map, FrameLayout.LayoutParams(-1, -1))
        map.setContentInsets(126f, 150f)
        val top = column().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val selectors = row()
        selectors.addView(
            action(scopeName()) { chooseArea() },
            LinearLayout.LayoutParams(0, dp(48), 1f),
        )
        selectors.addView(
            action("⊕") { map.resetCamera() },
            LinearLayout.LayoutParams(dp(50), dp(48)),
        )
        top.addView(selectors)
        gap(top, 6)
        val tools = row()
        tools.addView(
            action("Filter · ${map.filter}") { chooseFilter() },
            LinearLayout.LayoutParams(0, dp(48), 1f),
        )
        tools.addView(
            action("Land") { map.fitWholeState() },
            LinearLayout.LayoutParams(dp(64), dp(48)),
        )
        tools.addView(
            action("−") {
                map.zoom = (map.zoom / 1.5f).coerceAtLeast(.8f)
                map.invalidate()
            },
            LinearLayout.LayoutParams(dp(48), dp(48)),
        )
        tools.addView(
            action("+") {
                map.zoom = (map.zoom * 1.5f).coerceAtMost(10f)
                map.invalidate()
            },
            LinearLayout.LayoutParams(dp(48), dp(48)),
        )
        top.addView(tools)
        body.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val bottom =
            column().apply {
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = rounded(panel)
            }
        mapCounter = text("", 12f, mint, true)
        bottom.addView(mapCounter)
        incomingView =
            text("", 14f, ink, true).apply {
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener {
                    game.state.incidents
                        .firstOrNull { it.region == game.state.region && it.status == "OPEN" }
                        ?.let { showIncident(it) }
                }
            }
        bottom.addView(incomingView)
        val actions = row()
        mapAction =
            action("", true) {
                when (game.state.phase) {
                    "PLANNING" -> showShiftDialog()
                    "RUNNING" -> {
                        game.state.paused = !game.state.paused
                        updateLive()
                        save()
                    }
                    else -> showDebrief()
                }
            }
        actions.addView(mapAction, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(
            action("Notrufliste") { showTab("CALLS") },
            LinearLayout.LayoutParams(0, dp(48), 1f),
        )
        bottom.addView(actions)
        body.addView(
            bottom,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(dp(10), 0, dp(10), dp(8))
            },
        )
    }

    private fun chooseFilter() {
        val values =
            arrayOf(
                "ALLE",
                "FREI",
                "GEBUNDEN",
                "P1",
                "P2",
                "P3",
                "WACHEN",
                "EINSÄTZE",
                "STREIFE",
                "SEK",
                "MEK",
                "KRIPO",
                "BEREITSCHAFT",
                "HUND",
                "VERKEHR",
                "HELI",
                "RETTUNG",
                "FEUERWEHR",
            )
        AlertDialog.Builder(this)
            .setTitle("Kartenebenen")
            .setItems(values) { _, i ->
                map.filter = values[i]
                map.invalidate()
            }
            .setNeutralButton("Darstellung") { _, _ -> showMapOptions() }
            .show()
    }

    private fun showMapOptions() {
        val c = column()
        title(c, "Kartenansicht")
        val mode = choices(listOf("Tag/Nacht automatisch", "Tageskarte", "Nachtkarte"))
        mode.setSelection(
            when (map.nightMode) {
                false -> 1
                true -> 2
                else -> 0
            }
        )
        c.addView(mode)
        gap(c)
        for ((name, current) in
            listOf(
                "Einsatzdichte" to map.showHeatmap,
                "Simulierte Abdeckung" to map.showCoverage,
                "Übersichtskarte" to map.showMinimap,
                "Fahrtrouten" to map.showRoutes,
            )) {
            c.addView(
                CheckBox(this).apply {
                    text = name
                    setTextColor(ink)
                    isChecked = current
                    minHeight = dp(48)
                    setOnCheckedChangeListener { _, v ->
                        when (name) {
                            "Einsatzdichte" -> map.showHeatmap = v
                            "Simulierte Abdeckung" -> map.showCoverage = v
                            "Übersichtskarte" -> map.showMinimap = v
                            else -> map.showRoutes = v
                        }
                        map.invalidate()
                    }
                }
            )
        }
        gap(c)
        c.addView(
            action("Ansicht übernehmen", true) {
                map.nightMode =
                    when (mode.selectedItemPosition) {
                        1 -> false
                        2 -> true
                        else -> null
                    }
                map.invalidate()
                sheet?.dismiss()
            }
        )
        showSheet("Kartenebenen", c)
    }

    private fun chooseArea() {
        if (game.state.phase == "RUNNING") {
            toast("Zuständigkeit nach dieser Schicht ändern. Auf der Karte frei zoomen.")
            return
        }
        val panel = column()
        title(
            panel,
            "Dein Zuständigkeitsgebiet",
            "Ganzes Bundesland oder ein überschaubares Hamburger Gebiet.",
        )
        if (game.state.region == "DE-HH") {
            for ((id, name) in
                listOf(
                    "STATE" to "Ganz Hamburg",
                    "WANDSBEK" to "Nur Wandsbek",
                    "MITTE" to "Hamburg-Mitte",
                    "ALTONA" to "Altona",
                    "NORD" to "Hamburg-Nord",
                    "EIMSBÜTTEL" to "Eimsbüttel",
                    "HARBURG" to "Harburg",
                    "BERGEDORF" to "Bergedorf",
                )) {
                gap(panel, 6)
                panel.addView(
                    action((if (game.state.scope == id) "✓  " else "") + name) {
                        if (game.setScope(id)) {
                            sheet?.dismiss()
                            map.resetCamera()
                            refresh()
                        }
                    }
                )
            }
        }
        gap(panel)
        panel.addView(
            action("Anderes Bundesland wählen") {
                sheet?.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Bundesland")
                    .setItems(game.regions.map { it.name }.toTypedArray()) { _, i ->
                        game.setRegion(game.regions[i].code)
                        map.resetCamera()
                        refresh()
                        if (game.state.region == "DE-HH") chooseArea()
                    }
                    .show()
            }
        )
        showSheet("Zuständigkeit", panel)
    }

    private fun showCalls() {
        val list = page()
        val head = card(list)
        title(head, "Notrufe", scopeName())
        gap(head)
        head.addView(
            action("Notruf simulieren", true) {
                if (game.state.phase == "RUNNING") {
                    game.newIncident()
                    refresh()
                } else showShiftDialog()
            }
        )
        for (call in
            game.state.incidents
                .filter { it.region == game.state.region && game.inScope(it.lat, it.lon) }
                .sortedBy { if (it.status == "OPEN") it.priority else 10 }
                .take(100)) {
            val c = card(list)
            c.addView(
                text(
                    "P${call.priority}  ${call.title}",
                    17f,
                    when (call.priority) {
                        1 -> red
                        2 -> amber
                        else -> cyan
                    },
                    true,
                )
            )
            c.addView(text("${call.location} · ${statusName(call.status)}", 13f, muted))
            gap(c, 8)
            c.addView(text(call.description))
            gap(c)
            c.addView(
                action(if (call.status == "OPEN") "Kräfte auswählen" else "Einsatzdetails") {
                    showIncident(call)
                }
            )
        }
    }

    private fun eta(u: UnitState, call: IncidentState): Int =
        NativeRouting.route(GeoPoint(u.lon, u.lat), GeoPoint(call.lon, call.lat), u.vehicleSpeed)
            .durationMinutes +
            when (u.type) {
                "SEK" -> 12
                "MEK" -> 9
                else -> 2
            }

    private fun showIncident(call: IncidentState) {
        val content = column()
        title(
            content,
            call.title,
            "P${call.priority} · ${call.location} · ${statusName(call.status)}",
        )
        gap(content)
        content.addView(text(call.description, 15f))
        gap(content)
        content.addView(
            text(
                "Noch benötigt: ${game.remainingTypes(call).joinToString{roleName(it)}.ifEmpty{"alle Kräfte zugewiesen"}}",
                14f,
                cyan,
                true,
            )
        )
        content.addView(
            text(
                "SEK und MEK können auch allgemeine Polizeiaufgaben übernehmen. Spezialeinheiten bleiben bei besonderen Lagen wichtig.",
                12f,
                muted,
            )
        )
        gap(content)
        if (
            call.status in listOf("OPEN", "DRIVING") &&
                game.remainingTypes(call).isNotEmpty() &&
                game.state.phase == "RUNNING"
        ) {
            val units =
                game.state.units
                    .filter {
                        it.region == call.region &&
                            game.available(it) &&
                            game.remainingTypes(call).any { need -> game.canHandle(it, need) }
                    }
                    .sortedBy { eta(it, call) }
            if (units.isEmpty())
                content.addView(
                    text(
                        "Zurzeit keine passende freie Einheit. Prüfe Ausbildung, Ruhe und laufende Einsätze.",
                        14f,
                        amber,
                    )
                )
            for (u in units) {
                val r = row()
                r.addView(unitImage(u), LinearLayout.LayoutParams(dp(92), dp(64)))
                val detail = column()
                detail.addView(text(u.callsign, 15f, ink, true))
                detail.addView(
                    text(
                        "${roleName(u.type)} · ca. ${eta(u,call)} Min${if(!game.inScope(u.lat,u.lon))" · Überörtlich"else""}",
                        12f,
                        muted,
                    )
                )
                r.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
                content.addView(r)
                content.addView(
                    action("${u.callsign} entsenden", true) {
                        if (game.dispatch(call, u)) {
                            sheet?.dismiss()
                            showTab("MAP")
                            map.focus(call.lon, call.lat)
                            save()
                        } else toast("Einheit ist inzwischen nicht mehr verfügbar.")
                    }
                )
                gap(content, 12)
            }
        } else if (call.assignedUnit.isNotBlank()) {
            content.addView(
                text(
                    "Zugewiesen: " +
                        (game.state.units.find { it.id == call.assignedUnit }?.callsign
                            ?: call.assignedUnit),
                    14f,
                    mint,
                )
            )
        }
        if (call.decision.isBlank() && call.status in listOf("OPEN", "DRIVING", "WORKING")) {
            gap(content)
            content.addView(text("Managemententscheidung", 16f, ink, true))
            content.addView(
                action("Zusätzliche Gesprächszeit · +10 Min") {
                    if (game.decide(call, "DIALOG")) {
                        save()
                        showIncident(call)
                    }
                }
            )
            gap(content, 6)
            content.addView(
                action("Betreuung verstärken · 180 € / +3 Min") {
                    if (game.decide(call, "SUPPORT")) {
                        save()
                        showIncident(call)
                    } else toast("Budget reicht nicht aus.")
                }
            )
            gap(content)
        }
        if (call.assignedUnits.isNotEmpty()) {
            content.addView(text("Zugewiesene Kräfte", 15f, mint, true))
            for (id in call.assignedUnits) {
                val assigned = game.state.units.find { it.id == id } ?: continue
                content.addView(
                    text(
                        "${assigned.callsign} · ${statusName(assigned.status)}${if(assigned.status=="DRIVING")" · ca.${assigned.travelLeft+assigned.readinessLeft} Min"else""}",
                        13f,
                        muted,
                    )
                )
            }
            gap(content)
        }
        content.addView(
            action("Auf Karte ansehen") {
                sheet?.dismiss()
                showTab("MAP")
                map.focus(call.lon, call.lat)
            }
        )
        showSheet("Einsatz disponieren", content)
    }

    private fun image(path: String, height: Int = 150, fit: Boolean = false): ImageView =
        ImageView(this).apply {
            scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(9, 22, 34))
            minimumHeight = dp(height)
            contentDescription = path.substringAfterLast('/').substringBefore('.')
            try {
                var bm = images.get(path)
                if (bm == null) {
                    bm = EquipmentArt.load(this@MainActivity, path)
                }
                if (bm == null) {
                    val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    assets.open(path).use { BitmapFactory.decodeStream(it, null, opt) }
                    var scale = 1
                    while (opt.outWidth / scale > 1200) scale *= 2
                    bm =
                        assets.open(path).use {
                            BitmapFactory.decodeStream(
                                it,
                                null,
                                BitmapFactory.Options().apply { inSampleSize = scale },
                            )
                        }
                    if (bm != null) images.put(path, bm)
                }
                if (bm != null) images.put(path, bm)
                setImageBitmap(bm)
            } catch (_: Exception) {
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }

    private fun unitImage(u: UnitState) =
        image(
                when (u.type) {
                    "SEK" -> "sek_team.webp"
                    "MEK" -> "art/mek_team.webp"
                    else -> "patrol_car.webp"
                },
                110,
                true,
            )
            .apply { EquipmentArt.vehicle(this@MainActivity, u.type)?.let { setImageBitmap(it) } }

    private fun portrait(o: Officer) =
        image("officer_${o.portrait.coerceIn(1,8).toString().padStart(2,'0')}.webp", 100).apply {
            contentDescription = "Porträt von ${o.name}"
            val custom =
                if (o.photo.startsWith("data:image/"))
                    try {
                        val raw =
                            android.util.Base64.decode(
                                o.photo.substringAfter(','),
                                android.util.Base64.DEFAULT,
                            )
                        BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    } catch (_: Exception) {
                        null
                    }
                else null
            if (custom != null) setImageBitmap(custom)
            else EquipmentArt.portrait(this@MainActivity, o.portrait)?.let { setImageBitmap(it) }
        }

    private fun showUnits() {
        val list = page()
        val head = card(list)
        title(head, "Personal & Fahrzeuge", "${scopeName()} · ${game.freeUnits()} Kräfte verfügbar")
        gap(head)
        head.addView(action("Neue Einheit einstellen") { editUnit() })
        for (u in
            game.state.units.filter {
                it.region == game.state.region && game.inScope(it.lat, it.lon)
            }) {
            val c = card(list)
            c.addView(unitImage(u), LinearLayout.LayoutParams(-1, dp(135)))
            gap(c)
            title(c, u.callsign, roleName(u.type))
            c.addView(
                text(
                    "${statusName(u.status)} · ${u.officers.size} Beamte · Stufe ${u.level}",
                    13f,
                    if (u.status == "FREE") mint else amber,
                )
            )
            gap(c)
            c.addView(action("Personal, Ausstattung & Fortbildung") { showUnit(u) })
        }
    }

    private fun showUnit(u: UnitState) {
        val c = column()
        c.addView(unitImage(u), LinearLayout.LayoutParams(-1, dp(175)))
        gap(c)
        title(c, u.callsign, "${roleName(u.type)} · ${statusName(u.status)}")
        gap(c)
        for ((index, o) in u.officers.withIndex()) {
            val r = row()
            r.addView(portrait(o), LinearLayout.LayoutParams(dp(84), dp(96)))
            val info = column()
            info.setPadding(dp(12), 0, 0, 0)
            info.addView(text(o.name, 16f, ink, true))
            info.addView(text("Kompetenz ${o.skill} · Erfahrung ${o.experience}", 12f, muted))
            info.addView(
                text(
                    "Ausdauer ${o.energy}% · ${if("P99" in o.owned)"P99 inklusive"else"Grundausstattung"}",
                    12f,
                    mint,
                )
            )
            r.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(r)
            gap(c, 6)
            c.addView(action("${o.name}: Profil & Inventar") { showOfficer(u, index) })
            gap(c, 14)
        }
        c.addView(text("Fahrzeug: ${u.vehicleName} · Zustand ${u.vehicleCondition}%", 14f, muted))
        game.stations().find { it.id == u.stationId }?.let {
            c.addView(text("Heimatwache: ${it.name}", 13f, muted))
        }
        if (u.capabilities.isNotEmpty()) {
            c.addView(text("Zusätzliche Fähigkeiten: ${u.capabilities.joinToString(", ") }", 13f, mint))
        }
        gap(c)
        c.addView(
            action("Fahrzeug warten · ${euro((100-u.vehicleCondition)*25)}") {
                if (game.maintain(u)) {
                    save()
                    showUnit(u)
                } else toast("Wartung nur frei in der Planung mit ausreichendem Budget.")
            }
        )
        gap(c)
        c.addView(action("Dienstplan / Fahrzeug ändern") { editVehicle(u) })
        gap(c)
        c.addView(action("Freiwillige Fortbildung") { trainUnit(u) })
        if (u.id.startsWith("CUSTOM-")) {
            gap(c, 8)
            c.addView(action("Eigene Einheit / Fahrzeug löschen") {
                AlertDialog.Builder(this)
                    .setTitle("Einheit endgültig löschen?")
                    .setMessage("${u.callsign} und das zugehörige Fahrzeug entfernen? Nur möglich, wenn die Einheit frei und keine Schicht aktiv ist.")
                    .setPositiveButton("Löschen") { _, _ ->
                        if (game.removeCustomUnit(u.id)) {
                            sheet?.dismiss()
                            save()
                            refresh()
                        } else toast("Nur freie eigene Einheiten in der Planung löschbar.")
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            })
        }
        gap(c, 8)
        c.addView(
            action("Fahrzeug auf Karte") {
                sheet?.dismiss()
                showTab("MAP")
                map.focus(u.lon, u.lat)
            }
        )
        showSheet("Einheitenprofil", c)
    }

    private fun showOfficer(u: UnitState, index: Int) {
        val o = u.officers.getOrNull(index) ?: return
        val c = column()
        c.addView(portrait(o), LinearLayout.LayoutParams(-1, dp(240)))
        gap(c)
        title(c, o.name, "${u.callsign} · ${roleName(u.type)}")
        gap(c)
        c.addView(
            text(
                "Kompetenz ${o.skill}/100  ·  Ausdauer ${o.energy}%  ·  Erfahrung ${o.experience}",
                14f,
                mint,
                true,
            )
        )
        gap(c)
        c.addView(
            text(
                if ("P99" in o.owned)
                    "P99, Funk und Schutzweste gehören zur persönlichen Grundausstattung. Die P99 ist ohne Einzelkauf vorhanden."
                else
                    "Persönliche Grundausstattung und zusätzliche Ausrüstung im Inventar verwalten.",
                13f,
                muted,
            )
        )
        gap(c)
        c.addView(action("Ausrüstung & Waffen ansehen", true) { showEquipment(u, index) })
        gap(c)
        c.addView(
            action("Eigenes Porträt auswählen") {
                pendingOfficer = o
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    },
                    PORTRAIT,
                )
            }
        )
        gap(c)
        c.addView(action("Zur Einheit") { showUnit(u) })
        showSheet("Beamtenprofil", c)
    }

    private fun showEquipment(u: UnitState, index: Int) {
        val officer = u.officers.getOrNull(index) ?: return
        val c = column()
        title(c, "Persönliche Ausstattung", officer.name + " · " + u.callsign)
        c.addView(
            text(
                "Waffen sind ausschließlich abstrahierte Bereitschafts- und Schutzwerte. Keine Bedienung oder taktischen Abläufe.",
                12f,
                muted,
            )
        )
        gap(c)
        for (item in
            GameContent.equipment.filter {
                u.type !in listOf("RETTUNG", "FEUERWEHR") ||
                    it !in listOf("P99", "MP5", "HK416", "G36", "Elektroimpulsgerät")
            }) {
            val owned = item in officer.owned
            val equipped = item in officer.equipment
            val group =
                column().apply {
                    background = rounded(panel)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
            group.addView(
                image(EquipmentArt.equipmentImage(item), 170, true).apply {
                    contentDescription = "Vorschau: $item"
                },
                LinearLayout.LayoutParams(-1, dp(170)),
            )
            gap(group, 8)
            group.addView(text(item, 17f, ink, true))
            group.addView(
                text(
                    if (item == "P99") "Persönliche Dienstwaffe · serienmäßig · kostenlos"
                    else if (owned) "Im persönlichen Inventar"
                    else "Beschaffung: ${euro(GameContent.prices[item]?:0)}",
                    13f,
                    if (item == "P99") mint else muted,
                )
            )
            gap(group)
            if (item == "P99")
                group.addView(text("✓ Persönliche Grundausstattung", 14f, mint, true))
            else
                group.addView(
                    action(
                        if (equipped) "Ablegen" else if (owned) "Anlegen" else "Kaufen und anlegen",
                        !equipped,
                    ) {
                        val ok =
                            if (equipped) game.unequipOfficer(u, index, item)
                            else game.equipOfficer(u, index, item)
                        if (ok) {
                            save()
                            showEquipment(u, index)
                        } else
                            toast("Nicht möglich: laufender Einsatz, Budget oder belegte Plätze.")
                    }
                )
            c.addView(group, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        c.addView(action("Zurück zum Profil") { showOfficer(u, index) })
        showSheet("Inventar & Dienstmittel", c)
    }

    private fun trainUnit(u: UnitState) {
        val c = column()
        title(c, "Freiwillige Fortbildung", u.callsign)
        c.addView(
            text(
                "Lehrgänge dauern mehrere abgeschlossene Spieltage. Die Einheit ist währenddessen nicht disponierbar.",
                13f,
                muted,
            )
        )
        gap(c)
        if (u.trainingLeft > 0)
            c.addView(text("${u.training}: noch ${u.trainingLeft} Tage", 15f, mint, true))
        for ((course, cfg) in GameContent.courses) {
            c.addView(text(course, 16f, ink, true))
            c.addView(text("${cfg.first} Tage · ${euro(cfg.second)}", 13f, muted))
            c.addView(
                action("Lehrgang beginnen") {
                    if (game.train(u, course)) {
                        save()
                        showUnit(u)
                    } else
                        toast(
                            "Nur in der Planung, mit freier geeigneter Einheit und ausreichendem Budget."
                        )
                }
            )
            gap(c, 14)
        }
        showSheet("Ausbildung", c)
    }

    private fun showManage() {
        val list = page()
        val s = game.state
        val c = card(list)
        title(c, "Dienstleitung", scopeName())
        gap(c)
        c.addView(text("TAG ${s.day} · ${s.duration/60}h · ${game.clock()}", 18f, mint, true))
        c.addView(
            text(
                "${s.elapsed} von ${s.duration} Spielminuten · ${if(s.mode=="CAREER")"Karriere" else "Sandbox"}",
                13f,
                muted,
            )
        )
        gap(c)
        c.addView(
            action(
                when (s.phase) {
                    "PLANNING" -> "8 / 12 / 24h-Schicht planen"
                    "RUNNING" -> if (s.paused) "Fortsetzen" else "Pause"
                    else -> "Tagesbericht ansehen"
                },
                true,
            ) {
                when (s.phase) {
                    "PLANNING" -> showShiftDialog()
                    "RUNNING" -> {
                        s.paused = !s.paused
                        refresh()
                    }
                    else -> showDebrief()
                }
            }
        )
        gap(c)
        c.addView(action("Zuständigkeitsgebiet ändern") { chooseArea() })
        gap(c)
        c.addView(text("Budget ${euro(s.budget)}", 18f, ink, true))
        c.addView(
            text(
                "Reputation ${s.reputation}/100 · ${game.openIncidents()} aktive Lagen",
                14f,
                muted,
            )
        )
        if (s.phase == "RUNNING") {
            val controls = card(list)
            title(controls, "Spieltempo")
            val speeds = row()
            for (n in listOf(1, 4, 8, 16)) speeds.addView(
                action("${n}×") {
                    s.speed = n
                    refresh()
                },
                LinearLayout.LayoutParams(0, dp(48), 1f),
            )
            controls.addView(speeds)
            gap(controls)
            controls.addView(
                action("Schicht vorzeitig übergeben") {
                    if (s.elapsed < 30) {
                        toast("Eine vorzeitige Übergabe ist ab30 Spielminuten möglich.")
                        return@action
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Schichtübergabe?")
                        .setMessage(
                            "${game.openIncidents()} aktive Lagen bleiben für die Folgeschicht erhalten."
                        )
                        .setPositiveButton("Übergeben") { _, _ ->
                            game.finishShift()
                            refresh()
                            showDebrief()
                        }
                        .setNegativeButton("Weiterarbeiten", null)
                        .show()
                }
            )
        }
        val h = card(list)
        title(h, "Dienstverlauf")
        for (r in s.reports.take(20)) {
            gap(h)
            h.addView(text("Tag ${r.day} · ${r.duration/60}h", 15f, ink, true))
            h.addView(
                text(
                    "${r.solved} Erfolge · ${r.failed} Fehlschläge · ${r.handed} Übergaben",
                    13f,
                    muted,
                )
            )
        }
    }

    private fun showShiftDialog() {
        if (game.state.phase != "PLANNING") {
            if (game.state.phase == "DEBRIEF") showDebrief()
            else toast("Die aktuelle Schicht läuft bereits.")
            return
        }
        val c = column()
        title(c, "Schichtplanung", scopeName())
        gap(c)
        val mode = choices(listOf("Karriere", "Sandbox"))
        mode.setSelection(if (game.state.mode == "CAREER") 0 else 1)
        c.addView(text("Spielmodus", 13f, muted))
        c.addView(mode, LinearLayout.LayoutParams(-1, dp(52)))
        val duration = choices(listOf("8 Stunden", "12 Stunden", "24 Stunden"))
        duration.setSelection(listOf(480, 720, 1440).indexOf(game.state.duration).coerceAtLeast(0))
        gap(c)
        c.addView(text("Dauer", 13f, muted))
        c.addView(duration, LinearLayout.LayoutParams(-1, dp(52)))
        val start = choices((0..23).map { "%02d:00".format(it) })
        start.setSelection(game.state.startMinute / 60)
        gap(c)
        c.addView(text("Beginn", 13f, muted))
        c.addView(start, LinearLayout.LayoutParams(-1, dp(52)))
        gap(c)
        c.addView(
            text(
                "Ein Dienst zählt als ein Managementtag. Mitternacht löst keinen zusätzlichen Abschluss aus. Bei langen Diensten werden Personalwechsel und Ruhe berücksichtigt.",
                13f,
                muted,
            )
        )
        gap(c)
        c.addView(
            action("Schicht beginnen", true) {
                game.state.mode = if (mode.selectedItemPosition == 0) "CAREER" else "SANDBOX"
                game.setShift(
                    start.selectedItemPosition,
                    listOf(8, 12, 24)[duration.selectedItemPosition],
                )
                game.startShift()
                sheet?.dismiss()
                refresh()
            }
        )
        gap(c)
        c.addView(
            action("Nur Planung speichern") {
                game.setShift(
                    start.selectedItemPosition,
                    listOf(8, 12, 24)[duration.selectedItemPosition],
                )
                sheet?.dismiss()
                refresh()
            }
        )
        showSheet("Nächster Dienst", c)
    }

    private fun showDebrief() {
        val r = game.state.reports.firstOrNull() ?: return
        val c = column()
        title(c, "Dienst abgeschlossen", "TAG ${r.day} · ${r.duration/60} Stunden")
        gap(c)
        for ((name, value) in
            listOf(
                "Notrufe" to "${r.served}",
                "Erfolgreich" to "${r.solved}",
                "Fehlgeschlagen" to "${r.failed}",
                "Übergeben" to "${r.handed}",
                "Einnahmen" to euro(r.income),
                "Personal / Betrieb" to euro(r.expenses),
                "Ansehen" to "${game.state.reputation}%",
                "Aktuelles Budget" to euro(game.state.budget),
                "Gehälter" to euro(r.wages),
                "Wartung" to euro(r.maintenance),
                "Personalwechsel" to "${r.rotations}",
                "SEK-/MEK-Aufträge" to "${r.specialDispatches}",
                "Müdigkeit" to "${r.fatigue}%",
            )) {
            val line = row()
            line.addView(text(name, 14f, muted), LinearLayout.LayoutParams(0, dp(40), 1f))
            line.addView(text(value, 16f, ink, true))
            c.addView(line)
        }
        gap(c)
        if (r.trainingDone.isNotEmpty()) {
            c.addView(text("Abgeschlossene Lehrgänge", 16f, mint, true))
            c.addView(text(r.trainingDone.joinToString("\n"), 13f, muted))
            gap(c)
        }
        c.addView(
            text(
                "Ausrüstung, Personal, Lehrgänge und offene Einsatzlagen werden übernommen.",
                13f,
                muted,
            )
        )
        gap(c)
        c.addView(
            action("Nächsten Tag vorbereiten", true) {
                game.nextDay()
                sheet?.dismiss()
                refresh()
                showShiftDialog()
            }
        )
        showSheet("Tagesauswertung", c)
    }

    private fun editVehicle(u: UnitState) {
        val c = column()
        title(c, "Dienstplan & Fahrzeug", u.callsign)
        val name = field("Funkrufname", u.callsign)
        val vehicle = field("Fahrzeugname", u.vehicleName)
        val speed =
            field("Simulationsgeschwindigkeit km/h", u.vehicleSpeed.toInt().toString(), true)
        for (v in listOf(name, vehicle, speed)) c.addView(v)
        val duty =
            CheckBox(this).apply {
                text = "In der nächsten Schicht im Dienst"
                setTextColor(ink)
                isChecked = u.onDuty
                minHeight = dp(48)
            }
        c.addView(duty)
        gap(c)
        c.addView(
            action("Änderungen speichern", true) {
                if (game.state.phase != "PLANNING" || u.incidentId.isNotBlank()) {
                    toast("Nur in der Schichtplanung mit freier Einheit.")
                    return@action
                }
                val n = speed.text.toString().toDoubleOrNull()
                if (
                    name.text.isBlank() || vehicle.text.isBlank() || n == null || n !in 20.0..250.0
                ) {
                    toast("Name und Fahrtempo20–250km/h prüfen.")
                    return@action
                }
                u.callsign = name.text.toString().take(60)
                u.vehicleName = vehicle.text.toString().take(90)
                u.vehicleSpeed = n
                u.onDuty = duty.isChecked
                save()
                showUnit(u)
            }
        )
        showSheet("Einheitenverwaltung", c)
    }

    private fun showEditor() {
        val list = page()
        val c = card(list)
        title(c, "Eigene Inhalte", "Einsätze und Einheiten werden tatsächlich im Spiel verwendet.")
        gap(c)
        c.addView(action("Einsatzvorlage erstellen", true) { editScenario() })
        gap(c)
        c.addView(action("Neue Einheit / Taskforce erstellen") { editUnit() })
        if (game.state.region == "DE-HH") {
            gap(c)
            c.addView(action("Eigene Wache bauen") { editStation() })
        }
        for (station in game.state.customStations) {
            if (game.state.region != "DE-HH") break
            val item = card(list)
            title(item, station.name, "Eigener Standort · ${station.lat}, ${station.lon}")
            item.addView(action("Wache löschen") {
                AlertDialog.Builder(this)
                    .setTitle("Eigene Wache löschen?")
                    .setMessage("Die Wache kann nur entfernt werden, wenn keine Einheit dort stationiert ist.")
                    .setPositiveButton("Löschen") { _, _ ->
                        if (game.removeStation(station.id)) {
                            save()
                            refresh()
                        } else toast("Erst stationierte Einheiten entfernen. Nur in der Planung möglich.")
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            })
        }
        for (t in game.allTemplates()) {
            val p = card(list)
            p.addView(text(t.title, 16f, ink, true))
            p.addView(
                text("P${t.priority} · ${roleName(t.required)} · ${t.duration} Min", 13f, muted)
            )
            gap(p)
            p.addView(
                action("In dieser Schicht auslösen") {
                    if (game.state.phase == "RUNNING") {
                        game.newIncident(t)
                        refresh()
                    } else toast("Zuerst eine Schicht starten.")
                }
            )
        }
        val data = card(list)
        title(data, "Spielstand")
        gap(data)
        data.addView(action("Spielstand exportieren") { exportSave() })
        gap(data)
        data.addView(action("Spielstand importieren") { importSave() })
    }

    private fun field(label: String, initial: String = "", numeric: Boolean = false) =
        EditText(this).apply {
            hint = label
            setText(initial)
            setTextColor(ink)
            setHintTextColor(muted)
            minimumHeight = dp(52)
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

    private fun editScenario() {
        val c = column()
        title(c, "Einsatzvorlage")
        val name = field("Name")
        val desc = field("Notrufbeschreibung")
        val duration = field("Dauer in Minuten", "20", true)
        val priority = choices(listOf("P1 Kritisch", "P2 Hoch", "P3 Normal"))
        val types =
            listOf(
                "STREIFE",
                "SEK",
                "MEK",
                "KRIPO",
                "BEREITSCHAFT",
                "HUND",
                "VERKEHR",
                "HELI",
                "RETTUNG",
                "FEUERWEHR",
            )
        val type = choices(types.map { roleName(it) })
        for (v in listOf<View>(name, desc, priority, type, duration)) c.addView(v)
        gap(c)
        val follow = field("Folgestufe (optional)")
        c.addView(follow)
        val followType = choices(types.map { roleName(it) })
        c.addView(text("Anforderung der Folgestufe", 13f, muted))
        c.addView(followType)
        val branch = choices(listOf("Folgestufe bei Erfolg", "Folgestufe bei Fehlschlag"))
        c.addView(branch)
        gap(c)
        c.addView(
            action("Vorlage speichern", true) {
                val first = name.text.toString()
                val needed = types[type.selectedItemPosition]
                val mins = duration.text.toString().toIntOrNull() ?: 0
                val prio = priority.selectedItemPosition + 1
                val next = follow.text.toString().trim()
                val chain =
                    if (next.isBlank()) emptyList()
                    else
                        listOf(
                            ChainStage(
                                "initial",
                                first,
                                needed,
                                mins,
                                prio,
                                if (branch.selectedItemPosition == 0) "follow" else "",
                                if (branch.selectedItemPosition == 1) "follow" else "",
                            ),
                            ChainStage(
                                "follow",
                                next,
                                types[followType.selectedItemPosition],
                                20,
                                2,
                            ),
                        )
                if (
                    game.addTemplate(
                        IncidentTemplate(
                            first,
                            prio,
                            needed,
                            mins,
                            desc.text.toString(),
                            chain = chain,
                        )
                    )
                ) {
                    sheet?.dismiss()
                    refresh()
                } else toast("Name, Anforderungen und Dauer prüfen.")
            }
        )
        showSheet("Einsatzeditor", c)
    }

    private fun editStation() {
        val c = column()
        title(c, "Eigene Wache errichten", "Nur in Hamburg · 35.000 € Baukosten")
        val name = field("Name der Wache")
        val types = listOf("POLIZEI", "BERUFSFEUERWEHR", "RETTUNG", "SONSTIGE")
        val type = choices(listOf("Polizeiwache", "Feuerwehrwache", "Rettungswache", "Spezialwache"))
        val lat = field("Breitengrad, z. B. 53.55")
        val lon = field("Längengrad, z. B. 10.00")
        c.addView(name)
        c.addView(type)
        c.addView(lat)
        c.addView(lon)
        c.addView(text("Position auf der Hamburger Karte angeben. Eigene Standorte sind deutlich von amtlichen Wachen getrennt.", 12f, muted))
        gap(c)
        c.addView(action("Wache erstellen", true) {
            val a = lat.text.toString().replace(',', '.').toDoubleOrNull()
            val b = lon.text.toString().replace(',', '.').toDoubleOrNull()
            if (a != null && b != null && game.addStation(name.text.toString(), types[type.selectedItemPosition], a, b)) {
                sheet?.dismiss()
                save()
                refresh()
            } else toast("Name, Position, Budget und Schichtplanung prüfen.")
        })
        showSheet("Wachenbau", c)
    }

    private fun editUnit() {
        val c = column()
        title(c, "Eigene Einheit & Taskforce", "Fahrzeug, Besatzung und verfügbare Einsatzarten festlegen")
        val callsign = field("Funkrufname, z. B. Peter 21/1")
        val vehicle = field("Fahrzeug / Typbezeichnung", "Einsatzfahrzeug")
        val speed = field("Simuliertes Fahrzeugtempo (20–250 km/h)", "62", true)
        val crew = field("Besatzung (1–8)", "2", true)
        val types = GameController.TYPES
        val type = choices(types.map { roleName(it) })
        c.addView(callsign)
        c.addView(type)
        c.addView(vehicle)
        c.addView(speed)
        c.addView(crew)
        val stations = game.stations().filter { game.inScope(it.lat, it.lon) }
        val cities = if (game.state.scope == "STATE") game.region().cities else
            listOf(OperationsAreas.sample(game.region(), game.state.scope, java.util.Random()).let {
                GeoCity(scopeName(), it.lon, it.lat)
            })
        val stationSelector = if (game.state.region == "DE-HH")
            choices(stations.map { "${it.name} · ${if (it.custom) "eigene" else "amtlich"}" })
        else choices(cities.map { it.name })
        c.addView(text("Heimatwache / Startstandort", 13f, muted))
        c.addView(stationSelector)
        if (game.state.region == "DE-HH" && stations.isEmpty())
            c.addView(text("Keine Wache in der gewählten Zuständigkeit. Wechsle den Bereich oder baue eine Wache.", 13f, amber))
        gap(c)
        c.addView(text("Zusätzliche Fähigkeiten für gemischte Taskforces", 14f, ink, true))
        val capabilityBoxes = types.filter { it != "TASKFORCE" }.map { category ->
            category to CheckBox(this).apply {
                text = roleName(category)
                setTextColor(ink)
                minHeight = dp(40)
                isChecked = false
                c.addView(this)
            }
        }
        gap(c)
        c.addView(action("Einheit und Fahrzeug erstellen · 25.000 €", true) {
            val cap = capabilityBoxes.filter { it.second.isChecked }.map { it.first }.toSet()
            val kmh = speed.text.toString().toDoubleOrNull()
            val people = crew.text.toString().toIntOrNull()
            val success = if (kmh == null || people == null) false
                else if (game.state.region == "DE-HH") {
                    val base = stations.getOrNull(stationSelector.selectedItemPosition)
                    base != null && game.addCustomUnit(
                        callsign.text.toString(), types[type.selectedItemPosition], base,
                        vehicle.text.toString(), people, kmh, cap,
                    )
                } else {
                    val place = cities.getOrNull(stationSelector.selectedItemPosition)
                    place != null && game.addUnit(
                        callsign.text.toString(), types[type.selectedItemPosition], place,
                    ).also { ok ->
                        if (ok) game.state.units.lastOrNull()?.let { u ->
                            u.vehicleName = vehicle.text.toString().take(90)
                            u.vehicleSpeed = kmh.coerceIn(20.0, 250.0)
                            u.capabilities.addAll(cap)
                        }
                    }
                }
            if (success) {
                sheet?.dismiss()
                save()
                refresh()
            } else toast("Schichtplanung, Name, Fahrzeug, Wache, Besatzung und Budget prüfen.")
        })
        showSheet("Einheiteneditor", c)
    }

    private fun showSettings() {
        val c = column()
        title(c, "Einstellungen")
        gap(c)
        val s = game.state
        if (s.mode == "SANDBOX") {
            val unlimited =
                CheckBox(this).apply {
                    text = "Unbegrenztes Budget"
                    setTextColor(ink)
                    isChecked = s.unlimitedBudget
                    minHeight = dp(48)
                    setOnCheckedChangeListener { _, v ->
                        s.unlimitedBudget = v
                        save()
                    }
                }
            c.addView(unlimited)
            val fatigue =
                CheckBox(this).apply {
                    text = "Personalermüdung simulieren"
                    setTextColor(ink)
                    isChecked = s.fatigueEnabled
                    minHeight = dp(48)
                    setOnCheckedChangeListener { _, v ->
                        s.fatigueEnabled = v
                        save()
                    }
                }
            c.addView(fatigue)
            val timing = text("Notrufabstand: ${s.callInterval} Spielminuten", 14f, muted)
            c.addView(timing)
            c.addView(
                SeekBar(this).apply {
                    max = 230
                    progress = s.callInterval - 10
                    minimumHeight = dp(48)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onStartTrackingTouch(v: SeekBar) {}

                            override fun onStopTrackingTouch(v: SeekBar) {
                                save()
                            }

                            override fun onProgressChanged(v: SeekBar, p: Int, user: Boolean) {
                                if (user) {
                                    s.callInterval = p + 10
                                    timing.text = "Notrufabstand: ${s.callInterval} Spielminuten"
                                }
                            }
                        }
                    )
                }
            )
        }

        for ((label, initial) in
            listOf(
                "Gesamtlautstärke" to s.masterVolume,
                "Funk" to s.radioVolume,
                "Alarme & Fahrzeuge" to s.sfxVolume,
                "Leitstellenambiente" to s.ambientVolume,
            )) {
            c.addView(text(label, 14f, muted))
            val slider =
                SeekBar(this).apply {
                    max = 100
                    progress = (initial * 100).toInt()
                    minimumHeight = dp(48)
                }
            slider.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(v: SeekBar) {}

                    override fun onStopTrackingTouch(v: SeekBar) {
                        save()
                    }

                    override fun onProgressChanged(v: SeekBar, p: Int, user: Boolean) {
                        if (!user) return
                        val n = p / 100f
                        when (label) {
                            "Gesamtlautstärke" -> s.masterVolume = n
                            "Funk" -> s.radioVolume = n
                            "Alarme & Fahrzeuge" -> s.sfxVolume = n
                            else -> s.ambientVolume = n
                        }
                        audio.setVolumes(
                            s.masterVolume,
                            s.radioVolume,
                            s.sfxVolume,
                            s.ambientVolume,
                        )
                    }
                }
            )
            c.addView(slider)
        }
        gap(c)
        c.addView(
            action(if (game.state.mute) "Sound einschalten" else "Sound stummschalten") {
                game.state.mute = !game.state.mute
                audio.muted = game.state.mute
                save()
                showSettings()
            }
        )
        gap(c)
        c.addView(
            action(
                if (game.state.autoCalls) "Automatische Notrufe ausschalten"
                else "Automatische Notrufe einschalten"
            ) {
                game.state.autoCalls = !game.state.autoCalls
                save()
                showSettings()
            }
        )
        gap(c)
        c.addView(action("Zuständigkeit auswählen") { chooseArea() })
        gap(c)
        c.addView(action("Speichern & Exportieren") { exportSave() })
        gap(c)
        c.addView(action("Spielstand importieren") { importSave() })
        gap(c)
        c.addView(
            action("Funkprotokoll") {
                val log = column()
                title(log, "Funkprotokoll")
                gap(log)
                log.addView(
                    text(game.state.logs.joinToString("\n\n"), 13f, muted).apply {
                        setTextIsSelectable(true)
                    }
                )
                showSheet("Textfunk", log)
            }
        )
        gap(c)
        c.addView(
            action("Quellen und Lizenzen") {
                val credits = column()
                title(credits, "Quellen und Lizenzen")
                gap(credits)
                credits.addView(
                    text(
                            assets.open("licenses.txt").bufferedReader().use { it.readText() },
                            12f,
                            muted,
                        )
                        .apply { setTextIsSelectable(true) }
                )
                showSheet("Über dieses Spiel", credits)
            }
        )
        gap(c)
        c.addView(
            text(
                "Native Android-App. Sämtliche Grundkarten und Spielinhalte funktionieren offline. Fahrtwege und Fahrzeiten sind gekennzeichnete Simulationen.",
                13f,
                muted,
            )
        )
        showSheet("Leitstellenoptionen", c)
    }

    private fun showWelcome() {
        val c = column()
        c.addView(image("dispatch_center.webp", 170), LinearLayout.LayoutParams(-1, dp(170)))
        gap(c)
        title(
            c,
            "Deine Leitstelle. Dein Dienst.",
            "Wähle ein überschaubares Zuständigkeitsgebiet oder übernimm das ganze Bundesland.",
        )
        gap(c)
        c.addView(
            text(
                "Disponiere Kräfte auf der Live-Karte, verwalte Personal und Ausstattung und entwickle deine Dienstgruppe über mehrere Tage.",
                14f,
                muted,
            )
        )
        gap(c)
        c.addView(action("Zuständigkeit wählen", true) { chooseArea() })
        gap(c)
        c.addView(action("Schicht planen") { showShiftDialog() })
        showSheet("Einsatzleitstelle 110", c)
    }

    private fun showSheet(name: String, content: LinearLayout) {
        val scrollPosition = if (name == sheetName) sheetScroll?.scrollY ?: 0 else 0
        sheetName = name
        sheet?.dismiss()
        val dialog = Dialog(this)
        val outer =
            column().apply {
                background = rounded(dark, edge, 18)
                setPadding(dp(18), dp(14), dp(18), dp(14))
            }
        val heading = row()
        heading.addView(text(name, 14f, cyan, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(
            action("Schließen") { dialog.dismiss() },
            LinearLayout.LayoutParams(-2, dp(48)),
        )
        outer.addView(heading)
        gap(outer)
        val scroll = ScrollView(this)
        sheetScroll = scroll
        scroll.addView(content)
        scroll.post { scroll.scrollTo(0, scrollPosition) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(outer)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.show()
        dialog.window?.setLayout(-1, (resources.displayMetrics.heightPixels * .88).toInt())
        sheet = dialog
    }

    private fun exportSave() {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "Einsatzleitstelle110-Spielstand.json")
            },
            SAVE,
        )
    }

    private fun importSave() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            },
            LOAD,
        )
    }

    @Deprecated("Native SAF compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == PORTRAIT) {
                val officer = pendingOfficer ?: error("Profil erneut öffnen")
                val bytes = readLimited(uri, 3 * 1024 * 1024)
                require(bytes.size <= 3 * 1024 * 1024) { "Bild maximal3MB" }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Ungültiges Bild" }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
                val bitmap =
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    ) ?: error("Bildfehler")
                val side = minOf(bitmap.width, bitmap.height)
                val crop =
                    Bitmap.createBitmap(
                        bitmap,
                        (bitmap.width - side) / 2,
                        (bitmap.height - side) / 2,
                        side,
                        side,
                    )
                val small = Bitmap.createScaledBitmap(crop, 192, 192, true)
                val output = java.io.ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, 78, output)
                officer.photo =
                    "data:image/jpeg;base64," +
                        android.util.Base64.encodeToString(
                            output.toByteArray(),
                            android.util.Base64.NO_WRAP,
                        )
                pendingOfficer = null
                save()
                toast("Porträt gespeichert")
                sheet?.dismiss()
                showTab("UNITS")
            } else if (requestCode == SAVE) {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(GameStore.toJson(game.state).toString(2).toByteArray(Charsets.UTF_8))
                }
                toast("Spielstand exportiert")
            } else if (requestCode == LOAD) {
                val raw = String(readLimited(uri, 15 * 1024 * 1024), Charsets.UTF_8)
                val restored = GameStore.fromJson(JSONObject(raw))
                require(GeoData.all.any { it.code == restored.region }) { "Unbekanntes Bundesland" }
                val next = GameController(restored, GeoData.all)
                next.seedUnitsIfNeeded()
                map.close()
                game = next
                installNotifications()
                map = TacticalMapView(this, game, { showIncident(it) }, { showUnit(it) })
                audio.muted = restored.mute
                audio.setVolumes(
                    restored.masterVolume,
                    restored.radioVolume,
                    restored.sfxVolume,
                    restored.ambientVolume,
                )
                sheet?.dismiss()
                refresh()
                toast("Spielstand übernommen")
            }
        } catch (e: Exception) {
            toast("Datei nicht übernommen: ${e.message}")
        }
    }

    private fun readLimited(uri: android.net.Uri, limit: Int): ByteArray =
        contentResolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val block = ByteArray(8192)
            while (true) {
                val count = stream.read(block)
                if (count < 0) break
                require(output.size() + count <= limit) { "Datei zu groß" }
                output.write(block, 0, count)
            }
            output.toByteArray()
        } ?: error("Datei nicht lesbar")

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    @Deprecated("Native back compatibility")
    override fun onBackPressed() {
        if (sheet?.isShowing == true) sheet?.dismiss()
        else if (tab != "MAP") showTab("MAP")
        else
            AlertDialog.Builder(this)
                .setTitle("Dienst unterbrechen?")
                .setMessage("Der aktuelle Stand wird gespeichert.")
                .setPositiveButton("Beenden") { _, _ ->
                    save()
                    finish()
                }
                .setNegativeButton("Weiterspielen", null)
                .show()
    }
}
