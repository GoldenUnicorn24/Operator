package de.einsatzleitstelle110

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Validated native saves. Originals remain untouched until a complete replacement is durable. */
object GameStore {
    private const val FILE = "einsatzleitstelle-native-v1.json"

    fun save(context: Context, state: GameState) {
        val raw = toJson(state).toString()
        fromJson(JSONObject(raw))
        val temp = context.getFileStreamPath("einsatzleitstelle-native.tmp")
        java.io.FileOutputStream(temp).use {
            it.write(raw.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        val base = context.getFileStreamPath(FILE)
        val backup = context.getFileStreamPath(FILE + ".backup")
        if (base.exists()) {
            try {
                fromJson(JSONObject(base.readText()))
                base.copyTo(backup, true)
            } catch (_: Exception) {
                /* preserve last valid backup */
            }
        }
        check(temp.renameTo(base)) { "Spielstand konnte nicht atomar gesichert werden" }
    }

    fun load(context: Context): GameState? {
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
        val o = JSONObject().put("schemaVersion", 5)
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
            .put("masterVolume", s.masterVolume.toDouble())
            .put("radioVolume", s.radioVolume.toDouble())
            .put("sfxVolume", s.sfxVolume.toDouble())
            .put("ambientVolume", s.ambientVolume.toDouble())
            .put("randomSeed", s.randomSeed)
            .put("legacySource", s.legacySource)
            .put("logs", strings(s.logs))
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
                    .put("readinessLeft", u.readinessLeft)
                    .put("onDuty", u.onDuty)
                    .put("scope", u.scope)
                    .put("routeBasis", u.routeBasis)
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
                )
            out.add(stage)
        }
        return out
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
        val root = if (input.optInt("schemaVersion") == 5) input else NativeMigration.migrate(input)
        require(root.getInt("schemaVersion") == 5) { "Ungültiger Spielstand" }
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
                masterVolume = root.optDouble("masterVolume", .7).toFloat(),
                radioVolume = root.optDouble("radioVolume", .7).toFloat(),
                sfxVolume = root.optDouble("sfxVolume", .7).toFloat(),
                ambientVolume = root.optDouble("ambientVolume", 0.0).toFloat(),
                randomSeed = root.optLong("randomSeed", 982341),
                legacySource = root.optString("legacySource"),
                logs = texts(array(root, "logs", 250)),
            )
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
            if (officers.isEmpty()) officers.add(o)
            val relief = mutableListOf<MutableList<Officer>>()
            val teams = array(v, "reliefTeams", 2)
            for (k in 0 until teams.length()) {
                val a = teams.getJSONArray(k)
                require(a.length() == officers.size)
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
                    n(v, "crew", officers.size, 1, 8),
                    n(v, "level", 1, 1, 100),
                    n(v, "experience", 0),
                    officers.first(),
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
                    mutableListOf(),
                    v.optString("routeBasis", "DISTANCE_APPROXIMATION"),
                )
            val points = array(v, "routePoints", 5000)
            for (k in 0 until points.length()) {
                val a = points.getJSONArray(k)
                require(a.length() == 2 && a.getDouble(0).isFinite() && a.getDouble(1).isFinite())
                u.routePoints.add(GeoPoint(a.getDouble(0), a.getDouble(1)))
            }
            require(
                u.status in
                    listOf(
                        "FREE",
                        "DRIVING",
                        "ON_SCENE",
                        "WORKING",
                        "RETURNING",
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
            require(i.followUpType in GameController.TYPES)
            if (i.assignedUnits.isEmpty() && i.assignedUnit.isNotBlank())
                i.assignedUnits.add(i.assignedUnit)
            require(
                i.status in listOf("OPEN", "DRIVING", "WORKING", "SOLVED", "FAILED", "HANDED") &&
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
                )
            )
        }
        require(s.phase != "DEBRIEF" || s.reports.isNotEmpty())
        return s
    }
}
