package de.einsatzleitstelle110

import android.app.Activity
import kotlin.math.roundToInt
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
    private lateinit var dispatch: DispatchPanel
    private val ui by lazy { UiKit(this) }
    private var queueView: LinearLayout? = null
    private var speedView: TextView? = null
    private var queueVersion = ""
    private var mapCounter: TextView? = null
    private var mapAction: TextView? = null
    private var incomingView: TextView? = null
    private var pendingMapPoint: ((GeoPoint) -> Unit)? = null
    private var chiefScreen: PoliceChiefScreen? = null
    private var tab = "MAP"
    private var saveTick = 0
    private var sheet: Dialog? = null
    private var sheetScroll: ScrollView? = null
    private var sheetName = ""
    private var pageVersion = ""
    private var foreground = true
    private var lastReportDay = -1
    private var lastRosterStatus = ""
    private var phoneScreen: PhoneScreen? = null
    private var phoneQueue: LinearLayout? = null
    private var phoneQueueVersion: List<String> = emptyList()
    private lateinit var phoneBar: TextView
    private var simCarry = 0.0
    private var liveTick = 0
    private var ringTick = 0
    private var radioView: TextView? = null
    private lateinit var radio: RadioChannel
    private lateinit var voice: VoicePlayer
    private val SAVE = 701
    private val LOAD = 702
    private val PORTRAIT = 703
    private val PACK_SAVE = 704
    private val PACK_LOAD = 705
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
            "PATROLLING" -> "Auf Streife"
            "CHECKPOINT" -> "Kontrollstelle"
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
        HamburgStations.load(this)
        val saved = GameStore.load(this) ?: GameState(phoneEnabled = phoneDeskDefault)
        if (saved.phase == "RUNNING") saved.paused = true
        moveToHamburg(saved)
        game = GameController(saved, regions)
        game.placeProvider = { MapData.places(this, it) }
        game.seedUnitsIfNeeded()
        audio = NativeAudio(this)
        radio = RadioChannel(this)
        voice = VoicePlayer(this)
        audio.muted = saved.mute
        audio.setVolumes(
            saved.masterVolume,
            saved.radioVolume,
            saved.sfxVolume,
            saved.ambientVolume,
        )
        installNotifications()
        createMap()
        val root = column().apply { setBackgroundColor(dark) }
        val header =
            row().apply {
                val vertical = if (wideMap()) 3 else 9
                setPadding(dp(16), dp(vertical), dp(8), dp(vertical))
                setBackgroundColor(panel)
            }
        val brand = column()
        brand.addView(text("EINSATZLEITSTELLE 110", 15f, ink, true))
        brand.addView(text("DEUTSCHLAND / DIENSTZENTRALE", 9f, cyan, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        clockView = text("", 12f, mint, true)
        header.addView(clockView)
        header.addView(action("☰") { showSettings() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(if (wideMap()) 54 else 68)))
        phoneBar = text("", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
            contentDescription = "Notruf annehmen"
            setOnClickListener { answerNext() }
        }
        root.addView(phoneBar, LinearLayout.LayoutParams(-1, -2))
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = row().apply { setBackgroundColor(panel) }
        root.addView(navigation, LinearLayout.LayoutParams(-1, dp(if (wideMap()) 48 else 64)))
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        showTab("MAP")
        handler.post(ticker)
        if (saved.phase == "DEBRIEF") handler.post { showDebrief() }
        else if (saved.phase == "PLANNING" && saved.day == 1) handler.post {
            // A queued startup sheet must not cover a shift already started by a fast action.
            if (game.state.phase == "PLANNING" && game.state.day == 1 && !isFinishing && !isDestroyed) showWelcome()
        }
    }

    /** Live map plus the dispatch panel that sits on top of it. */
    private fun createMap() {
        game.placeProvider = { MapData.places(this, it) }
        map = TacticalMapView(this, game, { openCall(it) }, { showUnit(it) })
        dispatch =
            DispatchPanel(
                this,
                game,
                map,
                ui,
                onChanged = {
                    save()
                    if (tab == "MAP") rebuildMapChrome() else updateLive()
                },
                onUnitDetails = { showUnit(it) },
                toast = { toast(it) },
                onResize = ::rebuildMapChrome,
            )
        map.onEmptyTap = {
            if (dispatch.isOpen) {
                dispatch.close()
                rebuildMapChrome()
            }
        }
        map.onLongPressPoint = { point ->
            if (!game.region().contains(point) || !game.inScope(point)) toast("Standort liegt außerhalb der Zuständigkeit.")
            else pendingMapPoint?.let { action -> pendingMapPoint = null; action(point) }
        }
        map.prepareRegion()
    }

    private fun requestMapPoint(hint: String, action: (GeoPoint) -> Unit) {
        sheet?.dismiss()
        dispatch.close()
        pendingMapPoint = action
        showTab("MAP")
        toast(hint)
    }

    private fun fleetOrders() = FleetOrders(this, game, { save(); updateLive(); map.invalidate() }, ::requestMapPoint)

    private fun showChief() {
        chiefScreen?.close()
        chiefScreen = PoliceChiefScreen(this, game, { save(); updateLive(); map.invalidate() }) { name ->
            requestMapPoint("Fiktive Dienststelle: Standort im Gebiet lange drücken (30.000 €).") { point ->
                if (game.createChiefStation(name, point) == null) toast(game.lastError)
                else { save(); map.invalidate(); showChief() }
            }
        }.also { it.show() }
    }

    private fun restoreState(restored: GameState) {
        moveToHamburg(restored)
        val next = GameController(restored, GeoData.all)
        next.placeProvider = { MapData.places(this, it) }
        next.seedUnitsIfNeeded()
        chiefScreen?.close()
        pendingMapPoint = null
        phoneScreen?.abandon()
        phoneScreen = null
        map.close()
        game = next
        radio.reset()
        installNotifications()
        createMap()
        audio.muted = restored.mute
        audio.setVolumes(restored.masterVolume, restored.radioVolume, restored.sfxVolume, restored.ambientVolume)
        sheet?.dismiss()
        refresh()
        save()
    }

    /** Open a call's file on the live map (from a marker, the queue or the call list). */
    private fun openCall(call: IncidentState) {
        sheet?.dismiss()
        if (tab != "MAP") showTab("MAP")
        dispatch.open(call)
        rebuildMapChrome()
    }

    private fun installNotifications() {
        game.notify = { name, message ->
            if (::radio.isInitialized) radio.say(RadioScript.event(name, message))
            when {
                name == "Tutorial abgeschlossen" -> {
                    audio.cue("SUCCESS")
                    toast("Tutorial geschafft! $message")
                }
                name == "Beförderung" -> {
                    audio.cue("SUCCESS")
                    toast("Beförderung! $message")
                }
                name == "Notruf klingelt" -> audio.cue("CALL")
                name == "Lageverschärfung" || name == "Anrufer aufgelegt" -> audio.cue("P2")
                name == "Lagebild" -> { audio.cue("RADIO"); toast(message) }
                name.startsWith("Neuer Notruf") -> {
                    if (!game.state.phoneEnabled) audio.cue("CALL")
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
                name == "Am Einsatzort" || name == "Verstärkung eingetroffen" || name == "Einheit abgezogen" ||
                    name == "Funkmeldung" || name == "Nachforderung" ->
                    audio.cue("RADIO")
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
                var moved = false
                if (foreground && old == "RUNNING" && !game.state.paused) {
                    val steps = simulationRate() * TICK_MS / 1000.0 + simCarry
                    val whole = steps.toInt()
                    simCarry = steps - whole
                    if (whole > 0) {
                        game.advance(whole)
                        moved = true
                    }
                    map.glideMs = 1000.0 / simulationRate()
                }
                if (old != game.state.phase) save()
                updatePhone()
                updateRadio()
                if (moved || ++liveTick % 4 == 0) updateLive()
                if (old == "RUNNING" && !game.state.paused && ++saveTick % 60 == 0) save()
                if (
                    old == "RUNNING" &&
                        game.state.phase == "DEBRIEF" &&
                        lastReportDay != game.state.day
                ) {
                    lastReportDay = game.state.day
                    showDebrief()
                }
                if (foreground) handler.postDelayed(this, TICK_MS)
            }
        }

    /** Simulated minutes per real second. ×8 is the old minute per second, ×4 an 8h shift in 16 minutes. */
    private fun simulationRate(): Double {
        val s = game.state
        if (PhoneDesk.active(s) != null) return 1 / 10.0
        if (PhoneDesk.ringing(s).isNotEmpty()) return minOf(s.speed, 2) / SPEED_DIVISOR
        return (if (game.pendingDecisions() > 0) minOf(s.speed, 4) else s.speed) / SPEED_DIVISOR
    }

    /** Saves from a state that is still locked continue in Hamburg between shifts. */
    private fun moveToHamburg(s: GameState) {
        if (!hamburgOnly || s.region == "DE-HH") return
        s.region = "DE-HH"
        s.scope = "STATE"
        s.phase = "PLANNING"
        s.unlockedRegions.add("DE-HH")
    }

    private fun updateRadio() {
        if (!::radio.isInitialized) return
        radio.volume = if (game.state.mute) 0f else game.state.masterVolume * game.state.radioVolume
        voice.volume = game.state.masterVolume
        radio.ducked = PhoneDesk.active(game.state) != null
        radio.pump()
    }

    private fun answerNext() {
        if (phoneScreen?.isShowing == true) return
        val s = game.state
        if (s.phase != "RUNNING") return
        PhoneDesk.active(s)?.let { current ->
            sheet?.dismiss()
            openPhone(current)
            return
        }
        val next = PhoneDesk.ringing(s).firstOrNull() ?: return
        if (!PhoneDesk.answer(game, next)) return
        sheet?.dismiss()
        openPhone(next)
    }

    private fun openPhone(call: PhoneCall) {
        val screen = PhoneScreen(this, game, { phoneScreen = null; save(); updateLive() }, { openCall(it) }, voice, { game.state.speechEnabled && !game.state.mute })
        screen.onReplace = { phoneScreen = it; it.onReplace = screen.onReplace }
        phoneScreen = screen
        screen.show(call)
    }

    /** Ringing banner above every tab; the call screen stays up to date while someone talks. */
    private fun updatePhone() {
        val s = game.state
        val waiting = PhoneDesk.ringing(s)
        val active = PhoneDesk.active(s)
        phoneScreen?.takeIf { it.isShowing }?.updateStatus()
        if (s.phase != "RUNNING" || (waiting.isEmpty() && active == null) || phoneScreen?.isShowing == true) {
            phoneBar.visibility = View.GONE
            return
        }
        ringTick++
        phoneBar.visibility = View.VISIBLE
        if (active != null) {
            val waitingText = if (waiting.isEmpty()) "" else " · ${waiting.size} wartend"
            phoneBar.text = "GESPRÄCH FORTSETZEN$waitingText"
            phoneBar.setBackgroundColor(Color.rgb(20, 64, 82))
        } else {
            val left = (PhoneDesk.PATIENCE - waiting.first().waited).coerceAtLeast(0)
            phoneBar.text = "NOTRUF ANNEHMEN" + (if (waiting.size > 1) " · ${waiting.size} in der Leitung" else "") + " · legt auf in ~$left Min"
            phoneBar.setBackgroundColor(if (ringTick % 4 < 2) Color.rgb(196, 42, 62) else Color.rgb(140, 26, 44))
        }
        if (waiting.isNotEmpty() && ringTick % 16 == 1) audio.cue("CALL")
    }

    override fun onPause() {
        if (::audio.isInitialized) audio.pause()
        if (::radio.isInitialized) radio.stop()
        if (::voice.isInitialized) voice.stop()
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
        if (::radio.isInitialized && !game.state.mute) radio.start()
        if (!foreground) {
            foreground = true
            handler.post(ticker)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        sheet?.dismiss()
        chiefScreen?.close()
        pendingMapPoint = null
        phoneScreen?.abandon()
        phoneScreen = null
        if (::audio.isInitialized) audio.close()
        if (::radio.isInitialized) radio.close()
        if (::voice.isInitialized) voice.close()
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
        clockView.text = "TAG ${game.state.day}\n${game.clock()} " + when (game.state.phase) {
            "RUNNING" -> if (game.state.paused) "Pause" else effectiveTempo()
            "DEBRIEF" -> "Übergabe"
            else -> "Planung"
        }
        val left = (game.state.duration - game.state.elapsed).coerceAtLeast(0)
        val workload = "${game.openIncidents()} Einsätze · ${game.freeUnits()} frei"
        val waiting = PhoneDesk.ringing(game.state).size
        val shiftLeft = if (game.state.phase == "RUNNING") " · Schichtende in %d:%02d".format(left / 60, left % 60) else ""
        mapCounter?.text = if (wideMap()) "$workload\n$waiting wartend$shiftLeft" else
            "$workload · $waiting wartend$shiftLeft\n" +
                "${SimulationConditions.weatherOptions[game.state.weather]} · " +
                (if (SimulationConditions.trafficFactor(game.hour()) > 1) "Berufsverkehr" else if (SimulationConditions.trafficFactor(game.hour()) < 1) "Freie Straßen" else "Normalverkehr") +
                (PhoneDesk.event(game.state)?.let { "\n${if (PhoneDesk.surgeActive(game.state)) "Hochphase" else "Lagebild"} · ${it.title}" } ?: "")
        radioView?.text = game.state.logs.firstOrNull()?.substringAfter("] ") ?: ""
        mapAction?.text = mapActionLabel()
        incomingView?.text =
            game.state.incidents
                .firstOrNull { it.region == game.state.region && it.status == "OPEN" }
                ?.let { "P${it.priority}  ${it.title} · ${it.location}" } ?: "Keine offenen Notrufe"
        if (tab == "MAP") {
            if (game.state.tutorialStep != lastTutorialStep && !(::dispatch.isInitialized && dispatch.isOpen)) rebuildMapChrome()
            map.invalidate()
            if (::dispatch.isInitialized && dispatch.isOpen) dispatch.refresh()
            updateQueue()
            speedView?.text = speedLabel()
        }
        val version =
            game.state.incidents.joinToString { it.id + it.status } +
                game.state.units.joinToString { it.id + it.status + it.trainingLeft }
        if (tab == "CALLS" && sheet?.isShowing != true) incidentDesk?.refresh()
        if (tab == "PHONE") updatePhoneQueue()
        if (tab == "UNITS" && sheet?.isShowing != true && version != pageVersion) {
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
        incidentDesk = null
        phoneQueue = null
        phoneQueueVersion = emptyList()
        body.removeAllViews()
        navigation.removeAllViews()
        mapCounter = null
        mapAction = null
        incomingView = null
        val labels =
            listOf(
                "MAP" to "Leitstelle",
                "PHONE" to "Telefon",
                "UNITS" to "Einheiten",
                "MANAGE" to "Präsidium",
                "MORE" to "Mehr",
            )
        for ((key, name) in labels) {
            val b = action(name) { showTab(key) }.apply {
                setPadding(dp(3), dp(8), dp(3), dp(8))
                isSelected = key == tab || (key == "MORE" && tab in listOf("CALLS", "EDITOR"))
            }
            b.textSize = 12f
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
            "PHONE" -> showPhoneDesk()
            "CALLS" -> showCalls()
            "UNITS" -> showUnits()
            "MANAGE" -> showManage()
            "EDITOR" -> showEditor()
            "MORE" -> showMore()
        }
        updateLive()
    }

    private fun showPhoneDesk() {
        val content = page()
        val panel = card(content)
        title(panel, "Telefon")
        gap(panel)
        phoneQueue = column().also { panel.addView(it) }
        updatePhoneQueue()
    }

    private fun updatePhoneQueue() {
        val queue = phoneQueue ?: return
        val state = game.state
        val active = PhoneDesk.active(state)
        val ringing = PhoneDesk.ringing(state)
        val nextVersion = listOf(state.phase, state.phoneEnabled.toString(), active?.id.orEmpty()) +
            ringing.map { "${it.id}:${it.waited}" }
        if (nextVersion == phoneQueueVersion) return
        phoneQueueVersion = nextVersion
        queue.removeAllViews()
        if (state.phase != "RUNNING") {
            val pending = ringing.size + if (active == null) 0 else 1
            queue.addView(text(if (pending > 0) "$pending Notrufe für die Folgeschicht" else "Außer Dienst", 14f, muted))
            return
        }
        if (active != null) {
            queue.addView(action("Gespräch fortsetzen · ${active.id}", true) { answerNext() })
            gap(queue)
        }
        if (ringing.isEmpty()) {
            queue.addView(text(if (active == null) "Keine offenen Notrufe" else "Keine weiteren Anrufe", 14f, muted))
            return
        }
        title(queue, "Warteschlange")
        ringing.forEach { call ->
            queue.addView(text("${call.id} · ${call.waited} Min. Wartezeit", 14f, ink))
            if (active == null) queue.addView(action("Annehmen · ${call.id}", true) {
                if (PhoneDesk.answer(game, call)) openPhone(call)
            })
            gap(queue)
        }
    }

    private fun showMore() {
        val content = page()
        val panel = card(content)
        title(panel, "Mehr")
        gap(panel)
        val entries = listOf<Pair<String, () -> Unit>>(
            "Einsätze" to { showTab("CALLS") },
            "Funkprotokoll" to ::showRadioLog,
            "Speicherstände" to { SaveSlotsScreen(this, game, ::restoreState).show() },
            "Kreativmodus" to { showTab("EDITOR") },
            "Einstellungen" to ::showSettings,
            "Hauptmenü" to { game.state.paused = true; save(); showWelcome() },
        )
        entries.forEach { (label, open) ->
            panel.addView(action(label, onClick = open))
            gap(panel)
        }
    }

    private var mapChrome: FrameLayout? = null
    private var lastTutorialStep = 0

    private fun wideMap() =
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.screenWidthDp >= 620

    private fun showMap() {
        (map.parent as? ViewGroup)?.removeView(map)
        body.addView(map, FrameLayout.LayoutParams(-1, -1).apply {
            if (wideMap()) leftMargin = dp(194)
        })
        map.setContentInsets(62f, 168f)
        map.prepareRegion()
        val chrome = FrameLayout(this)
        mapChrome = chrome
        body.addView(chrome, FrameLayout.LayoutParams(-1, -1))
        rebuildMapChrome()
    }

    private fun effectiveTempo(): String {
        val multiplier = simulationRate() * SPEED_DIVISOR
        return if (multiplier == .5) "×0,5" else "×${multiplier.roundToInt()}"
    }

    private fun speedLabel() =
        if (game.state.phase == "RUNNING") effectiveTempo() else "×${game.state.speed}"

    private fun mapActionLabel() = when (game.state.phase) {
        "PLANNING" -> "Schicht planen"
        "RUNNING" -> if (game.state.paused) "Fortsetzen" else "Pause"
        else -> "Tagesbericht"
    }

    private fun wideFileWidth(): Int {
        val width = body.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return minOf(dp(390), width * 40 / 100)
    }

    /** Compact controls around the map: top toolbar, zoom, and either queue or dispatch panel. */
    private fun rebuildMapChrome() {
        val chrome = mapChrome ?: return
        if (tab != "MAP") return
        chrome.removeAllViews()
        mapCounter = null
        mapAction = null
        incomingView = null
        queueView = null
        radioView = null
        speedView = null
        queueVersion = ""
        val top = row().apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
        fun tool(label: String, desc: String, w: Int, onClick: () -> Unit) =
            ui.button(label) { onClick() }.apply { contentDescription = desc; textSize = 12f }
                .also { top.addView(it, LinearLayout.LayoutParams(if (w == 0) 0 else dp(w), dp(44), if (w == 0) 1f else 0f).apply { marginEnd = dp(5) }) }
        val wide = wideMap()
        val split = wide && dispatch.isOpen
        tool("▾ ${scopeName()}", "Zuständigkeit wählen", 0) { chooseArea() }
        tool(if (map.filter == "ALLE") "Filter" else map.filter, "Kartenebenen filtern", 74) { chooseFilter() }
        if (!split) {
            tool("Land", "Ganzes Bundesland zeigen", 54) { map.fitWholeState() }
            tool(if (map.showLegend) "Legende ✓" else "Legende", "Legende ein- oder ausblenden", 82) {
                map.showLegend = !map.showLegend
                rebuildMapChrome()
            }
        }
        chrome.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            if (wide) leftMargin = dp(194)
            if (split) rightMargin = wideFileWidth() + dp(8)
        })
        lastTutorialStep = game.state.tutorialStep
        if (game.state.tutorialStep in 1..3 && game.state.phase == "RUNNING") {
            val tip = column().apply {
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = ui.bg(Color.rgb(48, 38, 14), ui.amber, 12)
                isClickable = true
            }
            tip.addView(text(game.tutorialHint(), 13f, ink, true))
            tip.addView(text("Tutorial überspringen", 12f, ui.amber, true).apply {
                setPadding(0, dp(6), 0, 0)
                setOnClickListener {
                    game.state.tutorialStep = 4
                    game.state.autoCalls = true
                    save()
                    rebuildMapChrome()
                }
            })
            chrome.addView(tip, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(dp(8), dp(58), dp(8), 0) })
        }
        val zoom = column().apply { setPadding(0, 0, dp(8), 0) }
        zoom.addView(ui.button("+") { map.zoomBy(1.6f) }.apply { textSize = 20f; contentDescription = "Hineinzoomen" }, LinearLayout.LayoutParams(dp(46), dp(46)))
        zoom.addView(View(this), LinearLayout.LayoutParams(1, dp(6)))
        zoom.addView(ui.button("−") { map.zoomBy(1 / 1.6f) }.apply { textSize = 20f; contentDescription = "Herauszoomen" }, LinearLayout.LayoutParams(dp(46), dp(46)))
        zoom.addView(View(this), LinearLayout.LayoutParams(1, dp(6)))
        zoom.addView(ui.button("⌂") { map.resetCamera() }.apply { textSize = 17f; contentDescription = "Zurück zur Übersicht" }, LinearLayout.LayoutParams(dp(46), dp(46)))
        if (wide) {
            buildWideMap(chrome, zoom)
            return
        }
        if (dispatch.isOpen) {
            (dispatch.root.parent as? ViewGroup)?.removeView(dispatch.root)
            map.obstructedRight = 0f
            val available = body.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val h = (available * dispatch.heightFraction).roundToInt().coerceIn(dp(106), max(dp(106), available - dp(110)))
            map.obstructedBottom = h.toFloat() + dp(6)
            // Zoom controls sit just above the panel so nothing overlaps.
            chrome.addView(row().apply {
                addView(ui.button("+") { map.zoomBy(1.6f) }.apply { textSize = 18f; contentDescription = "Hineinzoomen" }, LinearLayout.LayoutParams(dp(46), dp(42)))
                addView(ui.button("−") { map.zoomBy(1 / 1.6f) }.apply { textSize = 18f; contentDescription = "Herauszoomen" }, LinearLayout.LayoutParams(dp(46), dp(42)).apply { marginStart = dp(6) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, dp(10), h + dp(14)) })
            chrome.addView(mapControls(includeQueue = false), FrameLayout.LayoutParams(dp(194), -2, Gravity.START or Gravity.BOTTOM).apply {
                setMargins(dp(6), 0, 0, h + dp(14))
            })
            chrome.addView(dispatch.root, FrameLayout.LayoutParams(-1, h, Gravity.BOTTOM).apply { setMargins(dp(6), 0, dp(6), dp(6)) })
            map.reveal(dispatch.call!!.lon, dispatch.call!!.lat, (body.height - h).toFloat())
            return
        }
        map.obstructedBottom = 0f
        map.obstructedRight = 0f
        chrome.addView(zoom, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { bottomMargin = dp(60) })
        val bottom = column().apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = ui.bg(Color.argb(240, 10, 24, 35), edge, 16)
            isClickable = true
        }
        val head = row()
        mapCounter = text("", 12f, mint, true)
        head.addView(mapCounter, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(head)
        radioView = text("", 11f, ui.cyan).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
            contentDescription = "Letzte Funkmeldung"
            setOnClickListener { showRadioLog() }
        }
        bottom.addView(radioView)
        val queue = column().apply { setPadding(0, dp(4), 0, dp(6)) }
        queueView = queue
        bottom.addView(queue)
        bottom.addView(mapControls(includeQueue = true))
        chrome.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(6), 0, dp(6), dp(6)) })
        updateQueue()
        updateLive()
    }

    private fun mapControls(includeQueue: Boolean): LinearLayout {
        val actions = row()
        mapAction =
            ui.button(mapActionLabel(), true) {
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
        actions.addView(mapAction, LinearLayout.LayoutParams(0, dp(46), 1f))
        speedView =
            ui.button(speedLabel()) {
                val speeds = listOf(1, 4, 8, 16)
                game.state.speed = speeds[(speeds.indexOf(game.state.speed) + 1).mod(speeds.size)]
                updateLive()
                save()
            }.apply { contentDescription = "Spieltempo wechseln" }
        actions.addView(speedView, LinearLayout.LayoutParams(dp(84), dp(46)).apply { marginStart = dp(6) })
        if (includeQueue) actions.addView(ui.button("Liste") { showTab("CALLS") }.apply { contentDescription = "Notrufliste" }, LinearLayout.LayoutParams(dp(70), dp(46)).apply { marginStart = dp(6) })
        return actions
    }

    private fun buildWideMap(chrome: FrameLayout, zoom: LinearLayout) {
        val left = column().apply {
            setPadding(dp(9), dp(9), dp(9), dp(8))
            background = ui.bg(panel, edge, 12)
        }
        title(left, "Einsätze")
        mapCounter = text("", 12f, muted).also { left.addView(it) }
        left.addView(ui.button("Telefon") { showTab("PHONE") })
        queueView = column().also { left.addView(it) }
        left.addView(ui.button("Einsatzliste") { showTab("CALLS") })
        if (dispatch.isOpen) left.addView(ui.button("Karte") { showMapTools() })
        val rail = ScrollView(this).apply { addView(left) }
        chrome.addView(rail, FrameLayout.LayoutParams(dp(188), -1, Gravity.START).apply {
            setMargins(dp(6), dp(6), 0, dp(6))
        })

        val controls = row().apply {
            setPadding(dp(10), dp(5), dp(6), dp(5))
            background = ui.bg(panel, edge, 12)
            isClickable = true
        }
        radioView = text("", 12f, cyan).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription = "Letzte Funkmeldung"
            setOnClickListener { showRadioLog() }
        }
        controls.addView(radioView, LinearLayout.LayoutParams(0, dp(44), 1f))
        controls.addView(mapControls(includeQueue = false), LinearLayout.LayoutParams(dp(218), -2))
        chrome.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(199), 0, dp(6), dp(6))
        })

        map.obstructedBottom = dp(64).toFloat()
        map.setContentInsets(62f, 64f)
        if (dispatch.isOpen) {
            (dispatch.root.parent as? ViewGroup)?.removeView(dispatch.root)
            val panelWidth = wideFileWidth()
            map.obstructedRight = panelWidth.toFloat() + dp(12)
            chrome.addView(dispatch.root, FrameLayout.LayoutParams(panelWidth, -1, Gravity.END).apply {
                setMargins(0, dp(6), dp(6), dp(64))
            })
            dispatch.call?.let { map.reveal(it.lon, it.lat) }
        } else {
            map.obstructedRight = 0f
            chrome.addView(zoom, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        }
        updateQueue()
        updateLive()
    }

    private fun showMapTools() {
        val tools = column()
        tools.addView(action("Landesübersicht") {
            sheet?.dismiss()
            map.fitWholeState()
        })
        gap(tools)
        tools.addView(action(if (map.showLegend) "Legende ausblenden" else "Legende einblenden") {
            map.showLegend = !map.showLegend
            sheet?.dismiss()
            rebuildMapChrome()
        })
        showSheet("Karte", tools)
    }

    private fun showRadioLog() {
        val log = ui.column()
        game.state.logs.take(150).forEach { log.addView(text(it, 12f, ink).apply { setPadding(0, dp(5), 0, dp(5)) }) }
        sheet?.dismiss()
        sheet = showShiftScreenDialog(this, "Funkprotokoll", log)
    }

    /** Up to three most urgent calls as tappable rows under the map. */
    private fun updateQueue() {
        val q = queueView ?: return
        val calls = IncidentDesk.ordered(game, game.state.incidents
            .filter { it.region == game.state.region && game.inScope(it.lat, it.lon) && it.status in listOf("OPEN", "DRIVING", "WORKING") })
        val version = calls.take(3).joinToString { it.id + it.status + IncidentDesk.attention(game, it).reason } + calls.size + game.state.phase
        if (version == queueVersion) return
        queueVersion = version
        q.removeAllViews()
        if (calls.isEmpty()) {
            q.addView(text(if (game.state.phase == "RUNNING") "Keine offenen Notrufe – Kräfte bereit." else "Schicht planen, dann gehen die Notrufe ein.", 13f, muted))
            return
        }
        for (c in calls.take(3)) {
            val r = row().apply {
                setPadding(dp(6), dp(5), dp(6), dp(5))
                background = ui.bg(Color.rgb(14, 30, 42), Color.rgb(28, 48, 62), 10)
                minimumHeight = dp(44)
                setOnClickListener { openCall(c) }
                contentDescription = "Notruf ${c.title} öffnen"
            }
            r.addView(ui.chip("P${c.priority}", ui.priorityColor(c.priority), c.status == "OPEN"))
            if (c.pending.isNotBlank()) r.addView(ui.chip("⚠", ui.amber, true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            val attention = IncidentDesk.attention(game, c)
            r.addView(text("  ${c.title} · ${c.location}", 13f, ink, c.status == "OPEN").apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
            if (!wideMap()) r.addView(text(attention.reason, 11f, if (attention.order == 0) red else muted, true).apply {
                maxWidth = dp(150)
                maxLines = 2
            })
            q.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        if (calls.size > 3) q.addView(text("+ ${calls.size - 3} weitere Lagen · Liste", 11f, muted).apply { setPadding(dp(4), dp(4), 0, 0); setOnClickListener { showTab("CALLS") } })
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
                rebuildMapChrome()
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
            action("Andere Bundesländer: Coming Soon") {
                if (hamburgOnly) {
                    toast("Wir bauen erst Hamburg aus. Die anderen Bundesländer folgen.")
                    return@action
                }
                sheet?.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Bundesland")
                    .setItems(
                        game.regions
                            .map { (if (game.state.mode == "CAREER" && it.code !in game.state.unlockedRegions) "🔒 " else "") + it.name }
                            .toTypedArray()
                    ) { _, i ->
                        if (!game.setRegion(game.regions[i].code)) {
                            toast(game.lastError.ifBlank { "Bundesland jetzt nicht wählbar." })
                            return@setItems
                        }
                        map.resetCamera()
                        refresh()
                        if (game.state.region == "DE-HH") chooseArea()
                    }
                    .show()
            }
        )
        showSheet("Zuständigkeit", panel)
    }

    private var incidentDesk: IncidentDeskScreen? = null

    private fun showCalls() {
        incidentDesk = IncidentDeskScreen(this, game, ui, ::openCall) {
            if (game.state.phase == "RUNNING") {
                if (game.state.phoneEnabled) PhoneDesk.ringAuto(game) else game.newIncident()
                save(); updateLive()
            }
            else showShiftDialog()
        }.also { body.addView(it.root, FrameLayout.LayoutParams(-1, -1)) }
    }

    private fun showIncident(call: IncidentState) = openCall(call)

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
        FleetScreen(this, game, scopeName(), ::unitImage, ::showUnit, ::showChief,
            { fleetOrders().selectUnits() }, { editUnit() }).render(page())
    }

    private fun showUnit(u: UnitState) {
        val c = column()
        c.addView(unitImage(u), LinearLayout.LayoutParams(-1, dp(175)))
        gap(c)
        title(c, u.callsign, "${roleName(u.type)} · FMS ${game.fms(u)} · ${statusName(u.status)}")
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
                    "Ausdauer ${o.energy}% · ${if ("P99" in o.owned) "P99 inklusive" else "Grundausstattung"}",
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
        if (u.taskforceName.isNotBlank()) c.addView(text("Taskforce: ${u.taskforceName} · ${u.customCapabilities.joinToString(" + ") { roleName(it) }}", 13f, mint))
        if (u.id.startsWith("CUSTOM-")) {
            gap(c)
            c.addView(action("Taskforce / Fähigkeiten bearbeiten") { editTaskforce(u) })
            gap(c)
            c.addView(action("Eigene Einheit löschen") {
                AlertDialog.Builder(this)
                    .setTitle("${u.callsign} endgültig löschen?")
                    .setMessage("Nur selbst erstellte und nicht eingesetzte Fahrzeuge können während der Planung gelöscht werden.")
                    .setPositiveButton("Löschen") { _, _ ->
                        if (game.removeCustomVehicle(u.id)) {
                            sheet?.dismiss()
                            refresh()
                        } else toast("Einheit ist noch im Einsatz oder die Schicht läuft.")
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            })
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
        c.addView(action("Streifenmodus · ${u.patrolMode}") { sheet?.dismiss(); fleetOrders().order(listOf(u)) })
        gap(c)
        c.addView(text("Streife wirkt vorbeugend; Kraft, Treibstoff und Fahrzeugzustand sinken durch gefahrene Kilometer.", 12f, muted))
        gap(c)
        c.addView(action("Dienstplan / Fahrzeug ändern") { editVehicle(u) })
        gap(c)
        c.addView(action("Freiwillige Fortbildung") { trainUnit(u) })
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
        c.addView(action("Versetzen in anderes Fahrzeug") {
            if (game.chiefVehicle(u) != null) { sheet?.dismiss(); showChief(); return@action }
            val targets = game.state.units.filter { it.region == u.region && it.id != u.id && game.chiefVehicle(it) == null && it.type !in listOf("RETTUNG", "FEUERWEHR") }
            AlertDialog.Builder(this)
                .setTitle("${o.name} versetzen")
                .setItems(targets.map { "${it.callsign} · ${roleName(it.type)} · ${it.officers.size}/${game.maxCrew(it.type)}" + if (game.understaffed(it)) " · unterbesetzt" else "" }.toTypedArray()) { _, i ->
                    if (game.transferOfficer(u, index, targets[i])) {
                        save()
                        toast("${o.name} fährt jetzt auf ${targets[i].callsign}." + if (game.understaffed(u)) " ${u.callsign} ist unterbesetzt!" else "")
                        showUnit(targets[i])
                    } else toast(game.lastError.ifBlank { "Versetzung nicht möglich." })
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        })
        gap(c)
        c.addView(action("Zur Einheit") { showUnit(u) })
        showSheet("Beamtenprofil", c)
    }

    /**
     * Loadout: portrait with weight bar, visible slots per category and an inventory grid with
     * images, price, depot stock and the concrete decisions each item unlocks.
     */
    private fun showEquipment(u: UnitState, index: Int) {
        val officer = u.officers.getOrNull(index) ?: return
        val c = column()
        val head = row()
        head.addView(portrait(officer), LinearLayout.LayoutParams(dp(84), dp(100)))
        val info = column().apply { setPadding(dp(12), 0, 0, 0) }
        info.addView(text(officer.name, 17f, ink, true))
        info.addView(text("${u.callsign} · ${roleName(u.type)}", 12f, muted))
        val kg = GameContent.loadWeight(officer.equipment)
        info.addView(text("Traglast %.1f / %d kg".format(kg, GameContent.MAX_WEIGHT.toInt()), 13f, if (kg > GameContent.MAX_WEIGHT * .85) amber else mint, true))
        info.addView(
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = (kg / GameContent.MAX_WEIGHT * 100).toInt().coerceIn(0, 100)
            },
            LinearLayout.LayoutParams(-1, dp(10)),
        )
        if (officer.qualifications.isNotEmpty()) info.addView(text("Lehrgänge: " + officer.qualifications.joinToString(), 11f, cyan))
        head.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        c.addView(head)
        gap(c)
        // Slots by category.
        for ((slot, limit) in GameContent.slotLimits) {
            val r = row().apply { setPadding(0, dp(3), 0, dp(3)) }
            r.addView(text(slot, 12f, muted, true), LinearLayout.LayoutParams(dp(104), -2))
            val worn = officer.equipment.filter { GameContent.slotOf[it] == slot }
            for (k in 0 until limit) {
                val item = worn.getOrNull(k)
                val box = column().apply {
                    gravity = Gravity.CENTER
                    background = rounded(if (item != null) Color.rgb(20, 52, 44) else Color.rgb(16, 30, 42), if (item != null) mint else edge, 10)
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    contentDescription = item ?: "$slot frei"
                }
                if (item != null) {
                    box.addView(image(EquipmentArt.equipmentImage(item), 40, true), LinearLayout.LayoutParams(-1, dp(40)))
                    box.addView(text(item, 9f, ink, true).apply { gravity = Gravity.CENTER; maxLines = 1 })
                    box.setOnClickListener { equipmentAction(u, index, item) }
                } else box.addView(text("frei", 10f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(56)))
                r.addView(box, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginEnd = dp(4) })
            }
            for (k in limit until 3) r.addView(View(this), LinearLayout.LayoutParams(0, dp(64), 1f))
            c.addView(r)
        }
        gap(c)
        c.addView(text("Inventar & Beschaffung", 15f, ink, true))
        c.addView(text("Antippen für Anlegen, Ablegen, Lager oder Kauf. Waffen sind abstrakte Spielwerte.", 11f, muted))
        val items =
            GameContent.equipment.filter {
                u.type !in listOf("RETTUNG", "FEUERWEHR") || it !in listOf("P99", "MP5", "HK416", "G36", "Elektroimpulsgerät")
            }
        for (rowItems in items.chunked(3)) {
            val r = row().apply { setPadding(0, dp(4), 0, 0) }
            for (item in rowItems) {
                val equipped = item in officer.equipment
                val owned = item in officer.owned
                val ok = GameContent.compatible(u.type, item)
                val tile = column().apply {
                    background = rounded(if (equipped) Color.rgb(20, 52, 44) else panel, if (equipped) mint else edge, 10)
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    alpha = if (ok) 1f else .45f
                    contentDescription = item
                    setOnClickListener { equipmentAction(u, index, item) }
                }
                tile.addView(image(EquipmentArt.equipmentImage(item), 56, true), LinearLayout.LayoutParams(-1, dp(56)))
                tile.addView(text(item, 11f, ink, true).apply { maxLines = 1 })
                val stock = game.state.depot[item] ?: 0
                tile.addView(
                    text(
                        when {
                            item == "P99" -> "serienmäßig"
                            equipped -> "angelegt"
                            owned -> "im Besitz"
                            stock > 0 -> "Lager: $stock"
                            else -> euro(GameContent.prices[item] ?: 0)
                        } + " · %.1f kg".format(GameContent.weight[item] ?: 0.0),
                        9f,
                        if (equipped) mint else muted,
                    )
                )
                r.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            }
            for (k in rowItems.size until 3) r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            c.addView(r)
        }
        gap(c)
        c.addView(action("Zurück zum Profil") { showOfficer(u, index) })
        showSheet("Loadout", c)
    }

    private fun equipmentAction(u: UnitState, index: Int, item: String) {
        val o = u.officers.getOrNull(index) ?: return
        val equipped = item in o.equipment
        val owned = item in o.owned
        val unlocks = GameContent.unlocks(item)
        val msg =
            "Platz: ${GameContent.slotOf[item]} · Gewicht %.1f kg · Preis ${euro(GameContent.prices[item] ?: 0)} · Lager ${game.state.depot[item] ?: 0}\n\n".format(GameContent.weight[item] ?: 0.0) +
                (if (!GameContent.compatible(u.type, item)) "Nicht kompatibel mit ${roleName(u.type)}.\n\n" else "") +
                if (unlocks.isEmpty()) "Wirkt allgemein als kleiner Bonus auf die Erfolgschance."
                else "Schaltet Entscheidungen frei:\n• " + unlocks.joinToString("\n• ")
        val b = AlertDialog.Builder(this).setTitle(item).setMessage(msg)
        fun done(ok: Boolean) {
            if (ok) {
                save()
                showEquipment(u, index)
            } else toast(game.lastError.ifBlank { "Nicht möglich (laufender Einsatz?)." })
        }
        if (item == "P99") b.setPositiveButton("OK", null)
        else {
            if (equipped) b.setPositiveButton("Ablegen") { _, _ -> done(game.unequipOfficer(u, index, item)) }
            else b.setPositiveButton(if (owned) "Anlegen" else if ((game.state.depot[item] ?: 0) > 0) "Aus Lager anlegen" else "Kaufen & anlegen") { _, _ ->
                game.lastError = ""
                done(game.equipOfficer(u, index, item))
            }
            if (owned) b.setNeutralButton("Ins Lager") { _, _ -> done(game.returnToDepot(u, index, item)) }
            else b.setNeutralButton("1× ins Lager kaufen") { _, _ -> done(game.buyStock(item)) }
            b.setNegativeButton("Schließen", null)
        }
        b.show()
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
        title(c, "Präsidium", scopeName())
        gap(c)
        c.addView(action("Polizeichef · Dienststellen, Fuhrpark & Personal", true) { showChief() })
        gap(c)
        c.addView(action("Spielstände · fünf Speicherplätze") { SaveSlotsScreen(this, game, ::restoreState).show() })
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
        if (s.mode == "CAREER") {
            val k = card(list)
            val now = game.careerLevels.first { it.level == s.careerLevel }
            title(k, "Karriere · Stufe ${s.careerLevel}", now.name)
            val next = game.careerLevels.firstOrNull { it.level == s.careerLevel + 1 }
            if (next == null) k.addView(text("Höchste Stufe erreicht – alle 16 Länder freigeschaltet.", 13f, mint))
            else {
                k.addView(text("Nächste Stufe: ${next.name}", 15f, ink, true))
                k.addView(text("${if (s.completedShifts >= next.shifts) "✓" else "○"} ${next.shifts} abgeschlossene Schichten (bisher ${s.completedShifts})", 13f, if (s.completedShifts >= next.shifts) mint else muted))
                k.addView(text("${if (s.reputation >= next.reputation) "✓" else "○"} Ruf mindestens ${next.reputation} am Tagesende (jetzt ${s.reputation}; sinkt über Nacht Richtung 72)", 13f, if (s.reputation >= next.reputation) mint else muted))
                k.addView(text("${if (s.budget > 0) "✓" else "○"} ausgeglichenes Budget", 13f, if (s.budget > 0) mint else muted))
                k.addView(text("Schaltet frei: " + (if (next.regions.isEmpty()) "alle übrigen Länder" else next.regions.mapNotNull { code -> game.regions.find { it.code == code }?.name }.joinToString()), 13f, cyan))
            }
            k.addView(text("Freigeschaltet: " + s.unlockedRegions.mapNotNull { code -> game.regions.find { it.code == code }?.name }.sorted().joinToString(), 12f, muted))
        }
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
        sheet?.dismiss()
        sheetName = "Nächster Dienst"
        sheet = ShiftPlanningScreen(this, game, scopeName(), { refresh() }, ::showDebrief).show()
    }

    private fun showDebrief() {
        phoneScreen?.abandon()
        phoneScreen = null
        sheet?.dismiss()
        sheetName = "Tagesauswertung"
        sheet = ShiftReportScreen(this, game) { refresh(); showShiftDialog() }.show()
    }

    private fun editVehicle(u: UnitState) {
        if (game.chiefVehicle(u) != null) { sheet?.dismiss(); showChief(); return }
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
        c.addView(action("Einsatz mit Ablauf erstellen", true) { ChainEditor(this, game, ui, null) { save(); refresh() }.show() })
        gap(c)
        c.addView(action("Neue Einheit erstellen") { editUnit() })
        gap(c)
        val packs = row()
        packs.addView(action("Einsatzpaket exportieren") {
            if (game.state.customTemplates.isEmpty()) toast("Noch keine eigenen Einsätze.")
            else startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "Einsatzleitstelle110-Einsaetze.json")
                },
                PACK_SAVE,
            )
        }, LinearLayout.LayoutParams(0, -2, 1f))
        packs.addView(action("importieren") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }, PACK_LOAD)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        c.addView(packs)
        for (t in game.allTemplates().sortedByDescending { it in game.state.customTemplates }) {
            val custom = t in game.state.customTemplates
            val p = card(list)
            p.addView(text((if (custom) "✎ " else "") + t.title, 16f, ink, true))
            p.addView(
                text(
                    "P${t.priority} · ${t.requiredTypes.joinToString(" + ") { roleName(it) }} · ${t.duration} Min" +
                        (if (t.chain.isNotEmpty()) " · ${t.chain.size} Stufen" else "") +
                        (if (Scenarios.find(t.title) != null) " · mit Entscheidungen" else ""),
                    13f,
                    muted,
                )
            )
            gap(p)
            if (custom) {
                val r = row()
                r.addView(action("Bearbeiten") { ChainEditor(this, game, ui, t) { save(); refresh() }.show() }, LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(action("Löschen") {
                    AlertDialog.Builder(this).setTitle("„${t.title}“ löschen?")
                        .setPositiveButton("Löschen") { _, _ -> game.state.customTemplates.remove(t); save(); refresh() }
                        .setNegativeButton("Abbrechen", null).show()
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
                p.addView(r)
                gap(p, 6)
            }
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

    private fun editUnit() {
        val c = column()
        title(c, "Eigenes Einsatzfahrzeug", "Fahrzeug, Personal und Heimatwache selbst festlegen.")
        gap(c)
        val name = field("Funkrufname, z. B. Peter 24/1")
        val vehicle = field("Fahrzeugbezeichnung, z. B. BMW 5er FuStW")
        c.addView(name)
        c.addView(vehicle)
        val types = listOf("STREIFE", "SEK", "MEK", "KRIPO", "BEREITSCHAFT", "HUND", "VERKEHR", "HELI", "RETTUNG", "FEUERWEHR")
        val type = choices(types.map { roleName(it) })
        c.addView(text("Primäre Einheit", 13f, muted))
        c.addView(type)
        val speed = field("Fahrtempo in km/h", "65", true)
        val crew = field("Besatzungsgröße (1–8)", "2", true)
        c.addView(speed)
        c.addView(crew)
        val locations: List<Pair<String, GeoCity>> =
            if (game.state.region == "DE-HH") {
                HamburgStations.all.filter { game.inScope(it.point) }.map {
                    it.category to GeoCity(it.name, it.lon, it.lat)
                } + game.state.chief.stations.filter { it.region == "DE-HH" && game.inScope(GeoPoint(it.lon, it.lat)) }.map {
                    "CUSTOM" to GeoCity(it.name + " (eigene Dienststelle)", it.lon, it.lat)
                }
            } else game.region().cities.map { "SIMULATION" to it }
        val station = choices(locations.map { it.second.name })
        c.addView(text("Heimatwache · Hamburg: amtlich geprüfte Standorte oder eigene Wache", 13f, muted))
        c.addView(station)
        c.addView(text("Polizei muss an eine Polizeiwache, Rettungsdienst an eine Berufsfeuerwehr, Feuerwehr an eine Feuerwehrwache. Eine selbst gegründete Wache ist frei nutzbar.", 12f, muted))
        gap(c)
        c.addView(action("Eigene Dienststelle gründen") { sheet?.dismiss(); showChief() })
        gap(c)
        c.addView(action("Fahrzeug und Einheit erstellen · 25.000 €", true) {
            val location = locations.getOrNull(station.selectedItemPosition)
            val selectedType = types[type.selectedItemPosition]
            val selectedCrew = crew.text.toString().toIntOrNull()
            val selectedSpeed = speed.text.toString().toDoubleOrNull()
            if (location == null || selectedCrew == null || selectedSpeed == null) {
                toast("Bitte alle Fahrzeugdaten und den Standort angeben.")
                return@action
            }
            val category = location.first
            val compatible = when (selectedType) {
                "RETTUNG" -> category == "BERUFSFEUERWEHR"
                "FEUERWEHR" -> category in listOf("BERUFSFEUERWEHR", "FREIWILLIGE_FEUERWEHR")
                else -> category == "POLIZEI"
            }
            if (category != "CUSTOM" && category != "SIMULATION" && !compatible) {
                toast("Diese Dienststelle passt nicht zur gewählten Einheit.")
                return@action
            }
            if (game.addCustomVehicle(
                    name.text.toString(), selectedType, location.second, vehicle.text.toString(),
                    selectedSpeed, selectedCrew,
                )) {
                val created = game.state.units.last()
                save()
                showUnit(created)
            } else toast("Erstellung nicht möglich: Planungsphase, Personal, Werte oder Budget prüfen.")
        })
        showSheet("Eigene Einheit erstellen", c)
    }

    private fun editTaskforce(u: UnitState) {
        if (!u.id.startsWith("CUSTOM-")) {
            toast("Taskforces lassen sich nur für selbst erstellte Einheiten festlegen.")
            return
        }
        val c = column()
        title(c, "Taskforce konfigurieren", u.callsign)
        c.addView(text("Eine kombinierte Einheit kann mehrere Einsatzarten gleichzeitig abdecken. Die gewählten Fähigkeiten beeinflussen die Alarmierung.", 13f, muted))
        val title = field("Name der Taskforce", u.taskforceName.ifBlank { "Taskforce ${u.callsign}" })
        c.addView(title)
        val types = listOf("STREIFE", "SEK", "MEK", "KRIPO", "BEREITSCHAFT", "HUND", "VERKEHR", "HELI", "RETTUNG", "FEUERWEHR")
        val checks = types.map { type ->
            CheckBox(this).apply {
                text = roleName(type)
                setTextColor(ink)
                isChecked = type in u.customCapabilities || (u.customCapabilities.isEmpty() && type == u.type)
                minHeight = dp(44)
                c.addView(this)
            }
        }
        gap(c)
        c.addView(action("Alle Fähigkeiten wählen") {
            checks.forEach { it.isChecked = true }
        })
        gap(c)
        c.addView(action("Taskforce speichern", true) {
            val selected = types.filterIndexed { i, _ -> checks[i].isChecked }.toSet()
            if (game.configureTaskforce(u.id, title.text.toString(), selected)) {
                save()
                showUnit(u)
            } else toast("Taskforce kann nur während der Planung mit freier eigener Einheit geändert werden.")
        })
        showSheet("Taskforce", c)
    }

    private fun showSettings() {
        val c = column()
        title(c, "Einstellungen")
        gap(c)
        val s = game.state
        c.addView(text("Schwierigkeit (Notrufaufkommen und Fristen)", 14f, muted))
        val diff = choices(listOf("Leicht", "Normal", "Schwer"))
        diff.setSelection(listOf("LEICHT", "NORMAL", "SCHWER").indexOf(s.difficulty).coerceAtLeast(0))
        diff.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, i: Int, id: Long) {
                    s.difficulty = listOf("LEICHT", "NORMAL", "SCHWER")[i]
                    save()
                }

                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        c.addView(diff, LinearLayout.LayoutParams(-1, dp(52)))
        gap(c)
        if (s.mode == "SANDBOX") {
            c.addView(
                CheckBox(this).apply {
                    text = "Ausbildungspflicht für SEK/MEK"
                    setTextColor(ink)
                    isChecked = s.trainingRequired
                    minHeight = dp(48)
                    setOnCheckedChangeListener { _, v ->
                        s.trainingRequired = v
                        save()
                    }
                }
            )
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
                if (game.state.mute) radio.stop() else radio.start()
                save()
                showSettings()
            }
        )
        gap(c)
        c.addView(
            action(if (game.state.speechEnabled) "Anrufer sprechen hörbar: an" else "Anrufer sprechen hörbar: aus") {
                game.state.speechEnabled = !game.state.speechEnabled
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
        c.addView(
            action(if (game.state.phoneEnabled) "Notrufe selbst annehmen: an" else "Notrufe selbst annehmen: aus") {
                if (game.state.phase == "RUNNING") {
                    toast("Nur zwischen den Schichten umstellbar.")
                    return@action
                }
                game.state.phoneEnabled = !game.state.phoneEnabled
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
        val menu = column()
        menu.addView(ImageView(this).apply {
            setImageResource(applicationInfo.icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(72), dp(72)))
        title(menu, "EINSATZLEITSTELLE 110", "DEUTSCHLAND")
        gap(menu, 16)
        val actions = listOf<Pair<String, () -> Unit>>(
            "Spiel fortsetzen" to {
                sheet?.dismiss()
                showTab("MAP")
                when (game.state.phase) {
                    "PLANNING" -> showShiftDialog()
                    "DEBRIEF" -> showDebrief()
                }
            },
            "Neue Karriere" to { startNewGame("CAREER") },
            "Sandbox" to { startNewGame("SANDBOX") },
            "Speicherstände" to {
                sheet?.dismiss()
                SaveSlotsScreen(this, game, ::restoreState).show()
            },
            "Kreativmodus" to { sheet?.dismiss(); showTab("EDITOR") },
            "Einstellungen" to ::showSettings,
        )
        actions.forEachIndexed { index, (label, open) ->
            menu.addView(action(label, index == 0, open))
            gap(menu)
        }
        if (game.state.phase == "PLANNING" && game.state.day == 1) {
            menu.addView(action("Tutorial in Hamburg") {
                sheet?.dismiss()
                game.startTutorial()
                refresh()
            })
            gap(menu)
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        menu.addView(text("Version $version", 12f, muted))
        showSheet("Hauptmenü", menu)
    }

    private fun startNewGame(mode: String) {
        fun start() {
            val state = GameState(mode = mode, phoneEnabled = phoneDeskDefault)
            tab = "MAP"
            restoreState(state)
            showShiftDialog()
        }
        AlertDialog.Builder(this)
            .setTitle(if (mode == "CAREER") "Neue Karriere beginnen?" else "Neue Sandbox beginnen?")
            .setMessage("Der neue Spielstand ersetzt die automatische Fortsetzung. Du kannst den laufenden Stand vorher in einem Speicherplatz sichern.")
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Speicherplätze") { _, _ -> SaveSlotsScreen(this, game, ::restoreState).show() }
            .setPositiveButton("Neues Spiel") { _, _ -> start() }
            .show()
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
            if (requestCode == PACK_SAVE) {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(GameStore.exportTemplates(game.state.customTemplates).toString(2).toByteArray(Charsets.UTF_8))
                }
                toast("${game.state.customTemplates.size} Einsätze exportiert")
                return
            }
            if (requestCode == PACK_LOAD) {
                val list = GameStore.importTemplates(String(readLimited(uri, 2 * 1024 * 1024), Charsets.UTF_8))
                var added = 0
                val problems = mutableListOf<String>()
                for (t in list) {
                    val errs = game.validateTemplate(t)
                    if (errs.isEmpty() && game.addTemplate(t)) added++ else problems.add("„${t.title}“: ${errs.firstOrNull() ?: "ungültig"}")
                }
                save()
                refresh()
                if (problems.isEmpty()) toast("$added Einsätze importiert")
                else AlertDialog.Builder(this).setTitle("$added von ${list.size} importiert").setMessage(problems.take(10).joinToString("\n")).setPositiveButton("OK", null).show()
                return
            }
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
                restoreState(restored)
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
        else if (dispatch.isOpen) {
            dispatch.close()
            rebuildMapChrome()
        } else {
            game.state.paused = true
            save()
            showWelcome()
        }
    }

    companion object {
        const val TICK_MS = 250L
        /** Speed ÷ this = simulated minutes per real second. ×4 plays an 8h shift in 10 minutes. */
        const val SPEED_DIVISOR = 5.0
        /** Only Hamburg can be played for now; device tests of other states switch this off. */
        @JvmField var hamburgOnly = true
        /** New games take calls by phone. Device tests of the map flow switch this off. */
        @JvmField var phoneDeskDefault = true
    }
}
