package de.einsatzleitstelle110

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Validated native saves. Originals remain untouched until a complete replacement is durable. */
object GameStore {
    val ALL_STATES =
        listOf("DE-BW", "DE-BY", "DE-BE", "DE-BB", "DE-HB", "DE-HH", "DE-HE", "DE-MV", "DE-NI", "DE-NW", "DE-RP", "DE-SL", "DE-SN", "DE-ST", "DE-SH", "DE-TH")

    private const val FILE = "einsatzleitstelle-native-v1.json"

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val pending =
        java.util.concurrent.atomic.AtomicReference<Pair<Context, GameState>?>(null)
    private val writing = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var latestSnapshot: GameState? = null

    /** Snapshot on the game thread; validation, fsync and backup run in one ordered worker. */
    fun saveAsync(context: Context, state: GameState, onError: (Exception) -> Unit) {
        val snapshot = GameSnapshots.capture(state)
        latestSnapshot = snapshot
        pending.set(context.applicationContext to snapshot)
        if (writing.compareAndSet(false, true)) writer.execute { drain(onError) }
    }

    private fun drain(onError: (Exception) -> Unit) {
        try {
            while (true) {
                val next = pending.getAndSet(null) ?: break
                try {
                    persist(next.first, toJson(next.second).toString())
                } catch (e: Exception) {
                    onError(e)
                }
            }
        } finally {
            writing.set(false)
            if (pending.get() != null && writing.compareAndSet(false, true))
                writer.execute { drain(onError) }
        }
    }

    /** Test/lifecycle verification barrier. Never wait for storage on Android's UI thread. */
    fun awaitSaves() {
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
        do {
            writer.submit {}.get(30, java.util.concurrent.TimeUnit.SECONDS)
        } while (writing.get() || pending.get() != null)
    }

    fun save(context: Context, state: GameState) = persist(context, toJson(state).toString())

    @Synchronized
    private fun persist(context: Context, raw: String) {
        fromJson(JSONObject(raw))
        val base = context.getFileStreamPath(FILE)
        val backup = context.getFileStreamPath(FILE + ".backup")
        val previous = try {
            val bytes = base.readBytes()
            fromJson(JSONObject(bytes.toString(Charsets.UTF_8)))
            bytes
        } catch (_: Exception) {
            null
        }
        if (previous != null) writeSaveAtomically(backup, previous)
        writeSaveAtomically(base, raw.toByteArray(Charsets.UTF_8))
    }

    fun load(context: Context): GameState? {
        // A recreation can precede the queued disk write. Prefer its exact immutable snapshot.
        if (writing.get() || pending.get() != null) {
            latestSnapshot?.let {
                return GameSnapshots.capture(it)
            }
        }
        for (name in listOf(FILE, FILE + ".backup")) {
            val f = context.getFileStreamPath(name)
            if (f.exists())
                try {
                    return fromJson(JSONObject(f.readText()))
                } catch (_: Exception) {
                    continue
                }
        }
        return null
    }

    private fun strings(values: Collection<String>) = JSONArray(values.toList())

    private fun jsonArray(values: Collection<*>, convert: (Any) -> JSONObject): JSONArray {
        val arr = JSONArray()
        values.forEach { arr.put(convert(it!!)) }
        return arr
    }

    private fun officer(o: Officer) =
        JSONObject()
            .put("name", o.name)
            .put("skill", o.skill)
            .put("energy", o.energy)
            .put("experience", o.experience)
            .put("portrait", o.portrait)
            .put("equipment", strings(o.equipment))
            .put("owned", strings(o.owned))
            .put("qualifications", strings(o.qualifications))
            .put("dutyMinutes", o.dutyMinutes)
            .put("restMinutes", o.restMinutes)
            .put("photo", o.photo)
            .put("personnelId", o.personnelId)

    private fun chain(n: ChainStage) =
        JSONObject()
            .put("id", n.id)
            .put("title", n.title)
            .put("required", n.required)
            .put("duration", n.duration)
            .put("priority", n.priority)
            .put("onSuccess", n.onSuccess)
            .put("onFailure", n.onFailure)
            .put("requiredTypes", strings(n.requiredTypes))
            .put("radio", n.radio)
            .put("deadline", n.deadline)

    private fun template(t: IncidentTemplate) =
        JSONObject()
            .put("title", t.title)
            .put("priority", t.priority)
            .put("required", t.required)
            .put("duration", t.duration)
            .put("description", t.description)
            .put("requiredTypes", strings(t.requiredTypes))
            .put("deadline", t.deadline)
            .put("reward", t.reward)
            .put("followUpType", t.followUpType)
            .put("followUp", t.followUp)
            .put("chain", jsonArray(t.chain) { chain(it as ChainStage) })
            .put("region", t.region)
            .put("lat", t.lat)
            .put("lon", t.lon)

    fun toJson(s: GameState): JSONObject {
        val o = JSONObject().put("schemaVersion", 7)
        o.put("region", s.region)
            .put("mode", s.mode)
            .put("day", s.day)
            .put("phase", s.phase)
            .put("startMinute", s.startMinute)
            .put("duration", s.duration)
            .put("elapsed", s.elapsed)
            .put("speed", s.speed)
            .put("paused", s.paused)
            .put("budget", s.budget)
            .put("reputation", s.reputation)
            .put("shiftIncoming", s.shiftIncoming)
            .put("shiftSolved", s.shiftSolved)
            .put("shiftFailed", s.shiftFailed)
            .put("shiftPartial", s.shiftPartial)
            .put("shiftHanded", s.shiftHanded)
            .put("shiftIncome", s.shiftIncome)
            .put("nextId", s.nextId)
            .put("autoCalls", s.autoCalls)
            .put("mute", s.mute)
            .put("scope", s.scope)
            .put("absoluteMinute", s.absoluteMinute)
            .put("shiftStartAbsolute", s.shiftStartAbsolute)
            .put("completedShifts", s.completedShifts)
            .put("totalHours", s.totalHours)
            .put("callInterval", s.callInterval)
            .put("fatigueEnabled", s.fatigueEnabled)
            .put("unlimitedBudget", s.unlimitedBudget)
            .put("customEnabled", s.customEnabled)
            .put("shiftRotations", s.shiftRotations)
            .put("shiftSpecial", s.shiftSpecial)
            .put("shiftSpecialCost", s.shiftSpecialCost)
            .put("shiftDecisions", s.shiftDecisions)
            .put("masterVolume", s.masterVolume.toDouble())
            .put("radioVolume", s.radioVolume.toDouble())
            .put("sfxVolume", s.sfxVolume.toDouble())
            .put("ambientVolume", s.ambientVolume.toDouble())
            .put("randomSeed", s.randomSeed)
            .put("legacySource", s.legacySource)
            .put("careerLevel", s.careerLevel)
            .put("difficulty", s.difficulty)
            .put("trainingRequired", s.trainingRequired)
            .put("tutorialStep", s.tutorialStep)
            .put("depot", JSONObject().also { d -> s.depot.forEach { (k, v) -> d.put(k, v) } })
            .put("unlockedRegions", strings(s.unlockedRegions))
            .put("logs", strings(s.logs))
            .put("radioArchive", JSONArray().also { archive ->
                s.radioArchive.forEach { entry ->
                    archive.put(JSONObject().put("day", entry.day).put("clock", entry.clock)
                        .put("title", entry.title).put("message", entry.message).put("priority", entry.priority.name)
                        .put("callsign", entry.callsign).put("organisation", entry.organisation).put("incidentId", entry.incidentId))
                }
            })
            .put("chief", PoliceChiefCodec.write(s.chief))
            .put("weather", s.weather)
            .put("shiftFuelCosts", s.shiftFuelCosts)
            .put("shiftSignalIncidents", s.shiftSignalIncidents)
            .put("phoneEnabled", s.phoneEnabled)
            .put("speechEnabled", s.speechEnabled)
            .put("recentScripts", strings(s.recentScripts))
            .put("shiftEvent", s.shiftEvent)
            .put("surgeStart", s.surgeStart)
            .put("shiftAnswered", s.shiftAnswered)
            .put("shiftMissed", s.shiftMissed)
            .put("shiftTakes", s.shiftTakes)
            .put("shiftTakeTotal", s.shiftTakeTotal)
            .put("takeStreak", s.takeStreak)
            .put("dispatchMode", s.dispatchMode.name)
            .put("phones", jsonArray(s.phones) { value ->
                val c = value as PhoneCall
                JSONObject().put("id", c.id).put("scriptId", c.scriptId).put("lat", c.lat).put("lon", c.lon)
                    .put("street", c.street).put("place", c.place).put("waited", c.waited)
                    .put("lines", strings(c.lines.takeLast(120))).put("asked", strings(c.asked)).put("advised", strings(c.advised))
                    .put("calmed", c.calmed).put("located", c.located).put("duplicateOf", c.duplicateOf)
                    .put("cprQuality", c.cprQuality).put("status", c.status)
                    .put("region", c.region).put("scope", c.scope)
                    .put("receivedAt", c.receivedAt).put("answeredAt", c.answeredAt)
                    .put("activeMinutes", c.activeMinutes).put("incidentId", c.incidentId)
            })
        o.put(
            "units",
            jsonArray(s.units) { value ->
                val u = value as UnitState
                JSONObject()
                    .put("id", u.id)
                    .put("callsign", u.callsign)
                    .put("type", u.type)
                    .put("region", u.region)
                    .put("lat", u.lat)
                    .put("lon", u.lon)
                    .put("crew", u.crew)
                    .put("level", u.level)
                    .put("experience", u.experience)
                    .put("officer", officer(u.officer))
                    .put("equipment", strings(u.equipment))
                    .put("status", u.status)
                    .put("incidentId", u.incidentId)
                    .put("startLat", u.startLat)
                    .put("startLon", u.startLon)
                    .put("destLat", u.destLat)
                    .put("destLon", u.destLon)
                    .put("travelTotal", u.travelTotal)
                    .put("travelLeft", u.travelLeft)
                    .put("workLeft", u.workLeft)
                    .put("operationType", u.operationType)
                    .put("training", u.training)
                    .put("trainingLeft", u.trainingLeft)
                    .put("officers", jsonArray(u.officers) { officer(it as Officer) })
                    .put(
                        "reliefTeams",
                        JSONArray().apply {
                            u.reliefTeams.forEach { put(jsonArray(it) { officer(it as Officer) }) }
                        },
                    )
                    .put("rosterIndex", u.rosterIndex)
                    .put("dutyMinutes", u.dutyMinutes)
                    .put("restUntil", u.restUntil)
                    .put("homeLat", u.homeLat)
                    .put("homeLon", u.homeLon)
                    .put("vehicleCondition", u.vehicleCondition)
                    .put("vehicleSpeed", u.vehicleSpeed)
                    .put("vehicleName", u.vehicleName)
                    .put("taskforceName", u.taskforceName)
                    .put("customCapabilities", JSONArray(u.customCapabilities.toList()))
                    .put("readinessLeft", u.readinessLeft)
                    .put("onDuty", u.onDuty)
                    .put("scope", u.scope)
                    .put("routeBasis", u.routeBasis)
                    .put("patrolMode", u.patrolMode)
                    .put("patrolScope", u.patrolScope)
                    .put("focusLon", u.focusLon)
                    .put("focusLat", u.focusLat)
                    .put("specialSignal", u.specialSignal)
                    .put("distanceDrivenKm", u.distanceDrivenKm)
                    .put("shiftDrivenKm", u.shiftDrivenKm)
                    .put(
                        "routePoints",
                        JSONArray().apply {
                            u.routePoints.forEach { put(JSONArray(listOf(it.lon, it.lat))) }
                        },
                    )
            },
        )
        o.put(
            "incidents",
            jsonArray(s.incidents) { value ->
                val i = value as IncidentState
                JSONObject()
                    .put("id", i.id)
                    .put("title", i.title)
                    .put("location", i.location)
                    .put("region", i.region)
                    .put("lat", i.lat)
                    .put("lon", i.lon)
                    .put("priority", i.priority)
                    .put("required", i.required)
                    .put("duration", i.duration)
                    .put("description", i.description)
                    .put("status", i.status)
                    .put("age", i.age)
                    .put("assignedUnit", i.assignedUnit)
                    .put("requiredTypes", strings(i.requiredTypes))
                    .put("assignedUnits", strings(i.assignedUnits))
                    .put("deadline", i.deadline)
                    .put("reward", i.reward)
                    .put("followUpType", i.followUpType)
                    .put("followUp", i.followUp)
                    .put("chain", jsonArray(i.chain) { chain(it as ChainStage) })
                    .put("stageId", i.stageId)
                    .put("followSpawned", i.followSpawned)
                    .put("decision", i.decision)
                    .put("riskDelta", i.riskDelta)
                    .put("handoffStatus", i.handoffStatus)
                    .put("scope", i.scope)
                    .put("outcome", i.outcome)
                    .put("firstArrival", i.firstArrival)
                    .put("closedAt", i.closedAt)
                    .put("scenario", i.scenario)
                    .put("stagesDone", strings(i.stagesDone))
                    .put("pending", i.pending)
                    .put("pendingAge", i.pendingAge)
                    .put("flags", strings(i.flags))
                    .put("notes", strings(i.notes.takeLast(40)))
                    .put("waitSince", i.waitSince)
                    .put("disconnected", i.disconnected)
                    .put("duplicateCallers", i.duplicateCallers)
                    .put("addressUncertain", i.addressUncertain)
                    .put("callbackDone", i.callbackDone)
                    .put("actualLat", i.actualLat)
                    .put("actualLon", i.actualLon)
                    .put("actualLocation", i.actualLocation)
                    .put("phoneScript", i.phoneScript)
                    .put("surprise", strings(i.surprise))
                    .put("surpriseText", i.surpriseText)
                    .put("worsened", i.worsened)
                    .put("takeScore", i.takeScore)
                    .put("reportedAt", i.reportedAt)
                    .put("answeredAt", i.answeredAt)
                    .put("firstDispatchedAt", i.firstDispatchedAt)
                    .put("phoneClosedAt", i.phoneClosedAt)
                    .put("phoneTranscript", strings(i.phoneTranscript))
                    .put("tasks", JSONArray().also { tasks ->
                        i.tasks.forEach { task ->
                            tasks.put(JSONObject().put("role", task.role).put("minutesLeft", task.minutesLeft)
                                .put("unitId", task.unitId).put("requestedAtAge", task.requestedAtAge))
                        }
                    })
            },
        )
        o.put("customTemplates", jsonArray(s.customTemplates) { template(it as IncidentTemplate) })
        o.put(
            "reports",
            jsonArray(s.reports) { value ->
                val r = value as ShiftReport
                JSONObject()
                    .put("day", r.day)
                    .put("region", r.region)
                    .put("duration", r.duration)
                    .put("served", r.served)
                    .put("solved", r.solved)
                    .put("failed", r.failed)
                    .put("partiallySolved", r.partiallySolved)
                    .put("handed", r.handed)
                    .put("income", r.income)
                    .put("expenses", r.expenses)
                    .put("repDelta", r.repDelta)
                    .put("elapsed", r.elapsed)
                    .put("startAbsolute", r.startAbsolute)
                    .put("endAbsolute", r.endAbsolute)
                    .put("rotations", r.rotations)
                    .put("wages", r.wages)
                    .put("maintenance", r.maintenance)
                    .put("specialDispatches", r.specialDispatches)
                    .put("trainingDone", strings(r.trainingDone))
                    .put("fatigue", r.fatigue)
                    .put("specialCosts", r.specialCosts)
                    .put("decisions", r.decisions)
                    .put("fuel", r.fuel)
                    .put("buildings", r.buildings)
                    .put("signalIncidents", r.signalIncidents)
                    .put("answered", r.answered)
                    .put("missed", r.missed)
                    .put("takeAverage", r.takeAverage)
                    .put("hilfsfrist", r.hilfsfrist)
                    .put("grade", r.grade)
                    .put("event", r.event)
                    .put("phoneHandoffs", jsonArray(r.phoneHandoffs) { entry ->
                        val handoff = entry as PhoneHandoff
                        JSONObject().put("callId", handoff.callId).put("active", handoff.active)
                    })
            },
        )
        return o
    }

    private fun array(o: JSONObject, key: String, max: Int): JSONArray {
        val a = o.optJSONArray(key) ?: JSONArray()
        require(a.length() <= max) { "Zu viele $key" }
        return a
    }

    private fun texts(a: JSONArray): MutableList<String> {
        val out = mutableListOf<String>()
        for (i in 0 until a.length()) {
            val s = a.getString(i)
            require(s.length <= 4000)
            out.add(s)
        }
        return out
    }

    private fun n(
        o: JSONObject,
        key: String,
        default: Int,
        min: Int = 0,
        max: Int = 100000000,
    ): Int {
        val x = o.optInt(key, default)
        require(x in min..max) { "Ungültiger Wert: $key" }
        return x
    }

    private fun coordinate(o: JSONObject, key: String, fallback: Double): Double {
        val v = o.optDouble(key, fallback)
        require(v.isFinite() && v >= -180 && v <= 180) { "Ungültige Koordinate: $key" }
        return v
    }

    private fun readOfficer(o: JSONObject): Officer {
        val eq = texts(array(o, "equipment", 30)).toMutableSet()
        val owned = texts(array(o, "owned", 50)).toMutableSet()
        if (eq.isEmpty()) eq.addAll(listOf("Funkgerät", "Schutzweste"))
        eq.add("P99")
        owned.addAll(eq)
        val photo = o.optString("photo")
        require(
            photo.isEmpty() ||
                (photo.length <= 150000 &&
                    photo.matches(Regex("data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+")))
        ) {
            "Ungültiges Porträt"
        }
        return Officer(
            o.optString("name", "Beamter").take(90),
            n(o, "skill", 58, 0, 100),
            n(o, "energy", 100, 0, 100),
            n(o, "experience", 0),
            n(o, "portrait", 1, 1, 8),
            eq,
            owned,
            texts(array(o, "qualifications", 100)).toMutableSet(),
            n(o, "dutyMinutes", 0),
            n(o, "restMinutes", 960),
            o.optString("photo"),
            o.optString("personnelId").take(90),
        )
    }

    private fun readChain(a: JSONArray): MutableList<ChainStage> {
        val out = mutableListOf<ChainStage>()
        for (j in 0 until a.length()) {
            val v = a.getJSONObject(j)
            val requirements = texts(array(v, "requiredTypes", 8))
            if (requirements.isEmpty()) requirements.add(v.getString("required"))
            val stage =
                ChainStage(
                    v.getString("id"),
                    v.getString("title"),
                    v.getString("required"),
                    n(v, "duration", 15, 1, 360),
                    n(v, "priority", 2, 1, 3),
                    v.optString("onSuccess"),
                    v.optString("onFailure"),
                    requirements,
                    v.optString("radio").take(600),
                    n(v, "deadline", 0, 0, 1440),
                )
            out.add(stage)
        }
        return out
    }

    /** Shareable scenario pack (editor export). */
    fun exportTemplates(list: List<IncidentTemplate>): JSONObject =
        JSONObject().put("format", "einsatzleitstelle110-scenarios").put("version", 1).put("templates", jsonArray(list) { template(it as IncidentTemplate) })

    /** Parse a scenario pack; throws IllegalArgumentException with a readable message. */
    fun importTemplates(raw: String): List<IncidentTemplate> {
        require(raw.length <= 2 * 1024 * 1024) { "Datei größer als 2 MB" }
        val root = try { JSONObject(raw) } catch (e: Exception) { throw IllegalArgumentException("Keine gültige JSON-Datei") }
        require(root.optString("format") == "einsatzleitstelle110-scenarios") { "Kein Einsatzpaket dieses Spiels" }
        val a = array(root, "templates", 200)
        return (0 until a.length()).map {
            try { readTemplate(a.getJSONObject(it)) } catch (e: Exception) { throw IllegalArgumentException("Einsatz ${it + 1} ist beschädigt: ${e.message}") }
        }
    }

    private fun readTemplate(v: JSONObject) =
        IncidentTemplate(
            v.getString("title"),
            n(v, "priority", 3, 1, 3),
            v.getString("required"),
            n(v, "duration", 15, 1, 360),
            v.optString("description"),
            texts(array(v, "requiredTypes", 8)).ifEmpty { mutableListOf(v.getString("required")) },
            n(v, "deadline", 180, 10, 1440),
            n(v, "reward", 1100, 0, 100000),
            v.optString("followUp"),
            readChain(array(v, "chain", 20)),
            v.optString("region"),
            if (v.has("lat")) coordinate(v, "lat", 0.0) else null,
            if (v.has("lon")) coordinate(v, "lon", 0.0) else null,
            v.optString("followUpType", "KRIPO"),
        )

    fun fromJson(input: JSONObject): GameState {
        require(input.toString().length <= 15 * 1024 * 1024) { "Datei größer als 15 MB" }
        require(input.optInt("schemaVersion") <= 7) { "Spielstand einer neueren Version" }
        val migrated = if (input.optInt("schemaVersion") in 5..7) input else NativeMigration.migrate(input)
        val root = if (migrated.optInt("schemaVersion") in 5..6) JSONObject(migrated.toString()).put("schemaVersion", 7) else migrated
        require(root.getInt("schemaVersion") == 7) { "Ungültiger Spielstand" }
        val s =
            GameState(
                region = root.optString("region", "DE-HH"),
                mode = root.optString("mode", "CAREER"),
                day = n(root, "day", 1, 1, 20000),
                phase = root.optString("phase", "PLANNING"),
                startMinute = n(root, "startMinute", 360, 0, 1439),
                duration = n(root, "duration", 480, 480, 1440),
                elapsed = n(root, "elapsed", 0, 0, 1440),
                speed = n(root, "speed", 4, 1, 16),
                paused = root.optBoolean("paused", true),
                budget = root.optInt("budget", 240000),
                reputation = n(root, "reputation", 74, 0, 100),
                shiftIncoming = n(root, "shiftIncoming", 0),
                shiftSolved = n(root, "shiftSolved", 0),
                shiftFailed = n(root, "shiftFailed", 0),
                shiftPartial = n(root, "shiftPartial", 0),
                shiftHanded = n(root, "shiftHanded", 0),
                shiftIncome = n(root, "shiftIncome", 0),
                nextId = n(root, "nextId", 1, 1),
                autoCalls = root.optBoolean("autoCalls", true),
                mute = root.optBoolean("mute"),
                scope = root.optString("scope", "STATE"),
                absoluteMinute = root.optLong("absoluteMinute"),
                shiftStartAbsolute = root.optLong("shiftStartAbsolute", 360),
                completedShifts = n(root, "completedShifts", 0),
                totalHours = root.optDouble("totalHours", 0.0),
                callInterval = n(root, "callInterval", 30, 10, 240),
                fatigueEnabled = root.optBoolean("fatigueEnabled", true),
                unlimitedBudget = root.optBoolean("unlimitedBudget"),
                customEnabled = root.optBoolean("customEnabled", true),
                shiftRotations = n(root, "shiftRotations", 0),
                shiftSpecial = n(root, "shiftSpecial", 0),
                shiftSpecialCost = n(root, "shiftSpecialCost", 0),
                shiftDecisions = n(root, "shiftDecisions", 0),
                masterVolume = root.optDouble("masterVolume", .7).toFloat(),
                radioVolume = root.optDouble("radioVolume", .7).toFloat(),
                sfxVolume = root.optDouble("sfxVolume", .7).toFloat(),
                ambientVolume = root.optDouble("ambientVolume", 0.0).toFloat(),
                randomSeed = root.optLong("randomSeed", 982341),
                legacySource = root.optString("legacySource"),
                careerLevel = root.optInt("careerLevel", 4).coerceIn(1, 4),
                difficulty = root.optString("difficulty", "NORMAL").takeIf { it in listOf("LEICHT", "NORMAL", "SCHWER") } ?: "NORMAL",
                trainingRequired = root.optBoolean("trainingRequired", false),
                tutorialStep = root.optInt("tutorialStep", 0).coerceIn(0, 4),
                logs = texts(array(root, "logs", 250)),
                chief = PoliceChiefCodec.read(root.optJSONObject("chief")),
                weather = root.optString("weather", "CLEAR"),
                shiftFuelCosts = n(root, "shiftFuelCosts", 0),
                shiftSignalIncidents = n(root, "shiftSignalIncidents", 0),
                phoneEnabled = root.optBoolean("phoneEnabled", true),
                speechEnabled = root.optBoolean("speechEnabled", true),
                recentScripts = texts(array(root, "recentScripts", 8)),
                shiftEvent = root.optString("shiftEvent").takeIf { id -> PhoneDesk.events.any { it.id == id } } ?: "",
                surgeStart = n(root, "surgeStart", 0, 0, 1440),
                shiftAnswered = n(root, "shiftAnswered", 0),
                shiftMissed = n(root, "shiftMissed", 0),
                shiftTakes = n(root, "shiftTakes", 0),
                shiftTakeTotal = n(root, "shiftTakeTotal", 0),
                takeStreak = n(root, "takeStreak", 0),
                dispatchMode = DispatchMode.entries.firstOrNull { it.name == root.optString("dispatchMode") }
                    ?: DispatchMode.BALANCED,
            )
        val archive = array(root, "radioArchive", RadioArchive.MAX_ENTRIES)
        val validClock = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
        for (index in 0 until archive.length()) {
            val entry = archive.getJSONObject(index)
            val clock = entry.getString("clock")
            require(validClock.matches(clock)) { "Ungültige Funkzeit" }
            s.radioArchive.add(RadioEntry(
                n(entry, "day", 1, 1), clock, entry.getString("title").take(100),
                entry.getString("message").take(4_000), RadioPriority.valueOf(entry.getString("priority")),
                entry.optString("callsign").take(100).ifBlank { null },
                entry.optString("organisation").take(100).ifBlank { null },
                entry.optString("incidentId").take(30).ifBlank { null },
            ))
        }
        val phones = array(root, "phones", 128)
        for (j in 0 until phones.length()) {
            val v = phones.getJSONObject(j)
            val status = v.optString("status", "RINGING")
            require(status == "RINGING" || status == "ACTIVE") { "Ungültiger Notrufstatus" }
            s.phones.add(PhoneCall(v.getString("id").take(30), v.getString("scriptId").take(120), coordinate(v, "lat", 53.55), coordinate(v, "lon", 10.0),
                v.optString("street", "Hauptstraße").take(90), v.optString("place").take(90), status, n(v, "waited", 0, 0, 60),
                texts(array(v, "lines", 120)).map { it.take(400) }.toMutableList(),
                texts(array(v, "asked", 12)).toMutableSet(), texts(array(v, "advised", 12)).toMutableSet(),
                v.optBoolean("calmed"), v.optBoolean("located"), v.optString("duplicateOf").take(30), n(v, "cprQuality", -1, -1, 100),
                region = v.optString("region").ifBlank { s.region }.take(12),
                scope = v.optString("scope").ifBlank { s.scope }.take(50),
                receivedAt = v.optLong("receivedAt", -1), answeredAt = v.optLong("answeredAt", -1),
                activeMinutes = n(v, "activeMinutes", 0, 0, 1000000),
                incidentId = v.optString("incidentId").take(30)))
        }
        require(s.phones.filter { it.status == "ACTIVE" }.groupBy { it.region to it.scope }.values.none { it.size > 1 }) {
            "Mehrere aktive Notrufe an derselben Leitstelle"
        }
        require(s.weather in SimulationConditions.weatherOptions) { "Unbekanntes Wetter" }
        root.optJSONObject("depot")?.let { d ->
            for (k in d.keys()) if (k in GameContent.equipment && k != "P99") s.depot[k] = d.optInt(k, 0).coerceIn(0, 999)
        }
        // Older saves had no career gating: keep every state open for them.
        s.unlockedRegions.clear()
        val unlocked = root.optJSONArray("unlockedRegions")
        if (unlocked == null) s.unlockedRegions.addAll(ALL_STATES)
        else s.unlockedRegions.addAll(texts(array(root, "unlockedRegions", 16)).filter { it in ALL_STATES })
        s.unlockedRegions.add(s.region)
        require(
            s.mode in listOf("CAREER", "SANDBOX") &&
                s.phase in listOf("PLANNING", "RUNNING", "DEBRIEF") &&
                s.duration in listOf(480, 720, 1440) &&
                s.speed in listOf(1, 4, 8, 16) &&
                s.elapsed <= s.duration &&
                s.startMinute % 60 == 0
        )
        require(
            s.scope == "STATE" ||
                (s.region == "DE-HH" &&
                    s.scope in
                        listOf(
                            "WANDSBEK",
                            "MITTE",
                            "ALTONA",
                            "NORD",
                            "EIMSBÜTTEL",
                            "HARBURG",
                            "BERGEDORF",
                        ))
        ) {
            "Unbekanntes Zuständigkeitsgebiet"
        }
        require(
            s.absoluteMinute >= 0 &&
                s.shiftStartAbsolute >= 0 &&
                s.totalHours.isFinite() &&
                s.totalHours >= 0 &&
                listOf(s.masterVolume, s.radioVolume, s.sfxVolume, s.ambientVolume).all {
                    it.isFinite() && it in 0f..1f
                }
        )
        val units = array(root, "units", 2000)
        val ids = mutableSetOf<String>()
        for (j in 0 until units.length()) {
            val v = units.getJSONObject(j)
            val id = v.getString("id")
            require(id.isNotBlank() && ids.add(id))
            val type = v.getString("type")
            require(type in GameController.TYPES)
            val o = readOfficer(v.optJSONObject("officer") ?: JSONObject())
            val list = array(v, "officers", 8)
            val officers = mutableListOf<Officer>()
            for (k in 0 until list.length()) officers.add(readOfficer(list.getJSONObject(k)))
            val managed = s.chief.vehicles.any { it.unitId == id }
            if (officers.isEmpty() && !managed) officers.add(o)
            val relief = mutableListOf<MutableList<Officer>>()
            val teams = array(v, "reliefTeams", 2)
            for (k in 0 until teams.length()) {
                val a = teams.getJSONArray(k)
                require(a.length() in 1..8)
                relief.add(
                    (0 until a.length()).map { readOfficer(a.getJSONObject(it)) }.toMutableList()
                )
            }
            val lat = coordinate(v, "lat", 53.55)
            val lon = coordinate(v, "lon", 10.0)
            val u =
                UnitState(
                    id,
                    v.optString("callsign").take(90),
                    type,
                    v.optString("region", s.region),
                    lat,
                    lon,
                    n(v, "crew", officers.size, if (managed) 0 else 1, 8),
                    n(v, "level", 1, 1, 100),
                    n(v, "experience", 0),
                    officers.firstOrNull() ?: o,
                    texts(array(v, "equipment", 30)).toMutableSet(),
                    v.optString("status", "FREE"),
                    v.optString("incidentId"),
                    coordinate(v, "startLat", lat),
                    coordinate(v, "startLon", lon),
                    coordinate(v, "destLat", lat),
                    coordinate(v, "destLon", lon),
                    n(v, "travelTotal", 0),
                    n(v, "travelLeft", 0),
                    n(v, "workLeft", 0),
                    v.optString("training"),
                    n(v, "trainingLeft", 0, 0, 100),
                    officers,
                    relief,
                    n(v, "rosterIndex", 0),
                    n(v, "dutyMinutes", 0),
                    v.optLong("restUntil"),
                    coordinate(v, "homeLat", lat),
                    coordinate(v, "homeLon", lon),
                    n(v, "vehicleCondition", 100, 0, 100),
                    v.optDouble("vehicleSpeed", 62.0),
                    v.optString("vehicleName", "Dienstfahrzeug"),
                    n(v, "readinessLeft", 0),
                    v.optBoolean("onDuty", true),
                    v.optString("scope", "STATE"),
                    emptyList(),
                    v.optString("routeBasis", "DISTANCE_APPROXIMATION"),
                )
            u.taskforceName = v.optString("taskforceName", "")
            u.customCapabilities.addAll(texts(array(v, "customCapabilities", 30)))
            u.operationType = v.optString("operationType").takeIf { it in GameController.TYPES }.orEmpty()
            u.patrolMode = v.optString("patrolMode", "WATCH")
            u.patrolScope = v.optString("patrolScope", u.scope)
            u.focusLon = coordinate(v, "focusLon", lon)
            u.focusLat = coordinate(v, "focusLat", lat)
            u.specialSignal = v.optBoolean("specialSignal", u.status == "DRIVING")
            u.distanceDrivenKm = v.optDouble("distanceDrivenKm", 0.0)
            u.shiftDrivenKm = v.optDouble("shiftDrivenKm", 0.0)
            require(u.patrolMode in listOf("WATCH", "PATROL", "FOCUS", "CHECKPOINT") &&
                (u.patrolScope == "STATE" || u.region == "DE-HH" && u.patrolScope in listOf("WANDSBEK", "MITTE", "ALTONA", "NORD", "EIMSBÜTTEL", "HARBURG", "BERGEDORF")) &&
                u.distanceDrivenKm.isFinite() && u.distanceDrivenKm >= 0 && u.shiftDrivenKm.isFinite() && u.shiftDrivenKm >= 0)
            val points = array(v, "routePoints", 5000)
            u.routePoints = List(points.length()) { index ->
                val point = points.getJSONArray(index)
                require(point.length() == 2 && point.getDouble(0).isFinite() && point.getDouble(1).isFinite())
                GeoPoint(point.getDouble(0), point.getDouble(1))
            }
            require(
                u.status in
                    listOf(
                        "FREE",
                        "DRIVING",
                        "ON_SCENE",
                        "WORKING",
                        "RETURNING",
                        "PATROLLING",
                        "CHECKPOINT",
                        "TRAINING",
                        "REST",
                        "OFF_DUTY",
                    ) &&
                    u.vehicleSpeed.isFinite() &&
                    u.vehicleSpeed in 20.0..250.0 &&
                    u.travelLeft <= u.travelTotal &&
                    u.restUntil >= 0
            )
            require(u.trainingLeft == 0 || u.training in GameContent.courses)
            if (type !in listOf("RETTUNG", "FEUERWEHR")) {
                u.equipment.add("P99")
            } else {
                u.equipment.remove("P99")
                (u.officers + u.reliefTeams.flatten()).forEach {
                    it.equipment.remove("P99")
                    it.owned.remove("P99")
                }
            }
            s.units.add(u)
        }
        val incidents = array(root, "incidents", 2000)
        val calls = mutableSetOf<String>()
        for (j in 0 until incidents.length()) {
            val v = incidents.getJSONObject(j)
            val id = v.getString("id")
            require(id.isNotBlank() && calls.add(id))
            val required = v.optString("required", "STREIFE")
            require(required in GameController.TYPES)
            val i =
                IncidentState(
                    id,
                    v.optString("title").take(90),
                    v.optString("location").take(120),
                    v.optString("region", s.region),
                    coordinate(v, "lat", 53.55),
                    coordinate(v, "lon", 10.0),
                    n(v, "priority", 3, 1, 3),
                    required,
                    n(v, "duration", 15, 1, 600),
                    v.optString("description").take(4000),
                    v.optString("status", "OPEN"),
                    n(v, "age", 0),
                    v.optString("assignedUnit"),
                    texts(array(v, "requiredTypes", 8)).ifEmpty { mutableListOf(required) },
                    texts(array(v, "assignedUnits", 8)),
                    n(v, "deadline", 180, 10, 1440),
                    v.optString("followUp"),
                    readChain(array(v, "chain", 20)),
                    v.optString("stageId"),
                    v.optBoolean("followSpawned"),
                    v.optString("decision"),
                    v.optDouble("riskDelta", 0.0),
                    v.optString("handoffStatus"),
                    v.optString("scope", "STATE"),
                )
            i.reward =
                n(
                    v,
                    "reward",
                    when (i.priority) {
                        1 -> 1900
                        2 -> 1100
                        else -> 600
                    },
                    0,
                    100000,
                )
            i.followUpType = v.optString("followUpType", "KRIPO")
            i.outcome = v.optString("outcome").take(400)
            i.firstArrival = v.optInt("firstArrival", -1).coerceIn(-1, 1440)
            i.closedAt = v.optLong("closedAt", -1L).coerceAtLeast(-1L)
            i.scenario = v.optString("scenario").take(120)
            i.stagesDone = texts(array(v, "stagesDone", 20))
            i.pending = v.optString("pending").take(40)
            i.pendingAge = v.optInt("pendingAge", 0).coerceIn(0, 1440)
            i.flags = texts(array(v, "flags", 40)).toMutableSet()
            i.notes = texts(array(v, "notes", 40))
            i.waitSince = v.optInt("waitSince", -1).coerceIn(-1, 1440)
            i.disconnected = v.optBoolean("disconnected", false)
            i.duplicateCallers = n(v, "duplicateCallers", 1, 1, 999)
            i.addressUncertain = v.optBoolean("addressUncertain", false)
            i.callbackDone = v.optBoolean("callbackDone", false)
            i.actualLat = coordinate(v, "actualLat", i.lat)
            i.actualLon = coordinate(v, "actualLon", i.lon)
            i.actualLocation = v.optString("actualLocation", i.location).take(120)
            i.phoneScript = v.optString("phoneScript").take(120)
            i.reportedAt = v.optLong("reportedAt", -1)
            i.answeredAt = v.optLong("answeredAt", -1)
            i.firstDispatchedAt = v.optLong("firstDispatchedAt", -1)
            i.phoneClosedAt = v.optLong("phoneClosedAt", -1)
            i.phoneTranscript.addAll(texts(array(v, "phoneTranscript", 120)).map { it.take(400) })
            val savedTasks = array(v, "tasks", 8)
            i.tasks = List(savedTasks.length()) { index ->
                val task = savedTasks.getJSONObject(index)
                val role = task.getString("role")
                val work = task.getInt("minutesLeft")
                val unitId = task.optString("unitId").take(120)
                require(role in GameController.TYPES && work in 0..600 && (work > 0 || unitId.isNotBlank())) {
                    "Ungültige Einsatzaufgabe"
                }
                IncidentTask(role, work, unitId, task.optInt("requestedAtAge", -1).coerceIn(-1, i.age.coerceAtMost(Int.MAX_VALUE - 45)))
            }
            require(i.tasks.mapNotNull { it.unitId.takeIf(String::isNotEmpty) }.let { it.size == it.toSet().size }) {
                "Einsatzaufgaben verwenden dieselbe Einheit mehrfach"
            }
            i.surprise = texts(array(v, "surprise", 12)).filter { it in GameController.TYPES }.toMutableList()
            i.surpriseText = v.optString("surpriseText").take(300)
            i.worsened = v.optBoolean("worsened")
            i.takeScore = n(v, "takeScore", -1, -1, 100)
            require(i.followUpType in GameController.TYPES)
            if (i.assignedUnits.isEmpty() && i.assignedUnit.isNotBlank())
                i.assignedUnits.add(i.assignedUnit)
            require(
                i.status in listOf("OPEN", "DRIVING", "WORKING", "SOLVED", "PARTIAL", "FAILED", "HANDED") &&
                    i.requiredTypes.all { it in GameController.TYPES } &&
                    i.assignedUnits.all { it in ids } &&
                    i.riskDelta.isFinite() &&
                    i.riskDelta in -0.5..0.5
            )
            s.incidents.add(i)
        }
        require(s.units.all { it.incidentId.isBlank() || it.incidentId in calls })
        val templates = array(root, "customTemplates", 500)
        for (j in 0 until templates.length()) {
            val t = readTemplate(templates.getJSONObject(j))
            require(GameController(GameState(), emptyList()).addTemplate(t)) {
                "Ungültige Einsatzkette"
            }
            s.customTemplates.add(t)
        }
        val reports = array(root, "reports", 1000)
        for (j in 0 until reports.length()) {
            val v = reports.getJSONObject(j)
            s.reports.add(
                ShiftReport(
                    n(v, "day", 1, 1),
                    v.optString("region", s.region),
                    n(v, "duration", 480, 480, 1440),
                    n(v, "served", 0),
                    n(v, "solved", 0),
                    n(v, "failed", 0),
                    n(v, "handed", 0),
                    n(v, "income", 0),
                    n(v, "expenses", 0),
                    v.optInt("repDelta"),
                    n(v, "elapsed", v.optInt("duration", 480), 0, 1440),
                    v.optLong("startAbsolute"),
                    v.optLong("endAbsolute"),
                    n(v, "rotations", 0),
                    n(v, "wages", 0),
                    n(v, "maintenance", 0),
                    n(v, "specialDispatches", 0),
                    texts(array(v, "trainingDone", 2000)),
                    n(v, "fatigue", 0, 0, 100),
                    n(v, "specialCosts", 0, 0, 10000000),
                    n(v, "decisions", 0, 0, 10000),
                    n(v, "fuel", 0),
                    n(v, "buildings", 0),
                    n(v, "signalIncidents", 0),
                    n(v, "answered", 0),
                    n(v, "missed", 0),
                    n(v, "takeAverage", -1, -1, 100),
                    n(v, "hilfsfrist", -1, -1, 100),
                    n(v, "grade", 0, 0, 6),
                    v.optString("event").take(90),
                    array(v, "phoneHandoffs", 8).let { handoffs ->
                        List(handoffs.length()) { index ->
                            val entry = handoffs.getJSONObject(index)
                            PhoneHandoff(entry.getString("callId").take(30), entry.optBoolean("active"))
                        }
                    },
                    n(v, "partiallySolved", 0),
                )
            )
        }
        require(s.phase != "DEBRIEF" || s.reports.isNotEmpty())
        return s
    }
}
