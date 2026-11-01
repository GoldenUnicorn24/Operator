package de.einsatzleitstelle110

import org.json.JSONArray
import org.json.JSONObject

/** Migrates native4 and exported Alpha/Web schemas without executing any HTML or script. */
object NativeMigration {
    private val gear =
        mapOf(
            "radio" to "Funkgerät",
            "vest" to "Schutzweste",
            "medical" to "Erste-Hilfe",
            "bodycam" to "Bodycam",
            "helmet" to "Schutzhelm",
            "shield" to "Schutzschild",
            "camera" to "Fahrzeugkamera",
            "drone" to "Drohne",
            "torch" to "Taschenlampe",
            "service_weapon" to "P99",
        )
    private val courses =
        mapOf(
            "comms" to "Deeskalation",
            "firstaid" to "Erste Hilfe",
            "team" to "Einsatzführung",
            "sek" to "SEK-Fortbildung",
            "mek" to "MEK-Observation",
            "radio" to "Kommunikation",
            "investigation" to "Fahndung",
            "traffic" to "Verkehrsdienst",
            "leadership" to "Lageführung",
            "search" to "Vermisstensuche",
        )
    private val names =
        mapOf(
            "Hamburg" to "DE-HH",
            "Niedersachsen" to "DE-NI",
            "Bayern" to "DE-BY",
            "Berlin" to "DE-BE",
            "Bremen" to "DE-HB",
            "Brandenburg" to "DE-BB",
            "Baden-Württemberg" to "DE-BW",
            "Hessen" to "DE-HE",
            "Mecklenburg-Vorpommern" to "DE-MV",
            "Nordrhein-Westfalen" to "DE-NW",
            "Rheinland-Pfalz" to "DE-RP",
            "Saarland" to "DE-SL",
            "Sachsen" to "DE-SN",
            "Sachsen-Anhalt" to "DE-ST",
            "Schleswig-Holstein" to "DE-SH",
            "Thüringen" to "DE-TH",
        )

    private fun region(value: String) =
        names[value] ?: if (value.startsWith("DE-")) value else "DE-$value"

    private fun array(o: JSONObject, key: String) = o.optJSONArray(key) ?: JSONArray()

    private fun items(a: JSONArray, convert: (JSONObject) -> JSONObject): JSONArray =
        JSONArray().apply { for (i in 0 until a.length()) put(convert(a.getJSONObject(i))) }

    private fun strings(a: JSONArray, convert: (String) -> String): JSONArray =
        JSONArray().apply { for (i in 0 until a.length()) put(convert(a.optString(i))) }

    private fun id(o: JSONObject, key: String) =
        if (o.isNull(key)) "" else o.opt(key)?.toString() ?: ""

    private fun phase(value: String) =
        when (value.lowercase()) {
            "running" -> "RUNNING"
            "debrief" -> "DEBRIEF"
            else -> "PLANNING"
        }

    private fun status(value: String) =
        when (value.lowercase()) {
            "open" -> "OPEN"
            "dispatching",
            "enroute" -> "DRIVING"
            "working" -> "WORKING"
            "resolved" -> "SOLVED"
            "failed" -> "FAILED"
            "handover" -> "HANDED"
            else -> value.uppercase()
        }

    private fun officer(v: JSONObject): JSONObject {
        val eq = strings(array(v, "equipped")) { gear[it] ?: it }
        val owned = strings(array(v, "owned")) { gear[it] ?: it }
        return JSONObject()
            .put("name", v.optString("name", "Beamter"))
            .put("skill", v.optInt("skill", 58))
            .put("energy", v.optInt("energy", 100))
            .put("experience", v.optInt("xp", v.optInt("experience")))
            .put("portrait", v.optInt("avatar", 0) + 1)
            .put("photo", v.optString("photo").takeUnless { it == "null" } ?: "")
            .put("equipment", eq)
            .put("owned", owned)
            .put("qualifications", strings(array(v, "qualifications")) { courses[it] ?: it })
            .put("dutyMinutes", v.optInt("dutyMinutes"))
            .put("restMinutes", v.optInt("restMinutes", 960))
    }

    private fun template(v: JSONObject): JSONObject {
        val needs =
            array(v, "needs").let {
                if (it.length() == 0) JSONArray(listOf(v.optString("type", "STREIFE"))) else it
            }
        val stages =
            items(array(v, "chain")) { n ->
                val req =
                    array(n, "needs").let {
                        if (it.length() == 0) JSONArray(listOf("STREIFE")) else it
                    }
                JSONObject()
                    .put("id", n.getString("id"))
                    .put("title", n.getString("name"))
                    .put("required", req.getString(0))
                    .put("requiredTypes", req)
                    .put("duration", n.optInt("duration", 15))
                    .put("priority", n.optInt("priority", 2))
                    .put("onSuccess", n.optString("onSuccess"))
                    .put("onFailure", n.optString("onFailure"))
            }
        val out =
            JSONObject()
                .put("title", v.optString("name", "Eigener Einsatz"))
                .put("priority", v.optInt("priority", 3))
                .put("required", needs.getString(0))
                .put("requiredTypes", needs)
                .put("duration", v.optInt("duration", 15))
                .put("description", v.optString("description", "Importierter Einsatz"))
                .put("deadline", v.optInt("deadlineMinutes", 180))
                .put("reward", v.optInt("reward", 1100))
                .put("followUpType", v.optJSONObject("followUp")?.optString("type") ?: "KRIPO")
                .put("followUp", v.optJSONObject("followUp")?.optString("name") ?: "")
                .put("chain", stages)
                .put("region", v.optString("regionId").let { if (it.isBlank()) "" else region(it) })
        v.optJSONArray("position")?.let {
            out.put("lon", it.getDouble(0)).put("lat", it.getDouble(1))
        }
        return out
    }

    fun migrate(input: JSONObject): JSONObject {
        val version = input.optInt("schemaVersion")
        if (version in 3..4 && input.has("phase"))
            return JSONObject(input.toString()).put("schemaVersion", 5).apply {
                put(
                    "absoluteMinute",
                    (input.optInt("day", 1) - 1) * 1440L +
                        input.optInt("startMinute", 360) +
                        input.optInt("elapsed"),
                )
                put(
                    "shiftStartAbsolute",
                    (input.optInt("day", 1) - 1) * 1440L + input.optInt("startMinute", 360),
                )
                put("legacySource", input.toString())
                put(
                    "unlimitedBudget",
                    input.optBoolean("unlimitedBudget", input.optString("mode") == "SANDBOX"),
                )
                val units = array(this, "units")
                for (i in 0 until units.length()) {
                    val u = units.getJSONObject(i)
                    if (!u.has("officers")) {
                        val lead =
                            JSONObject((u.optJSONObject("officer") ?: JSONObject()).toString())
                        val equipment =
                            u.optJSONArray("equipment")
                                ?: JSONArray(listOf("Funkgerät", "Schutzweste"))
                        lead.put("equipment", equipment).put("owned", equipment)
                        val crew = JSONArray()
                        repeat(u.optInt("crew", 2).coerceIn(1, 8)) { j ->
                            val o = JSONObject(lead.toString())
                            if (j > 0)
                                o.put("name", lead.optString("name", "Beamter") + " / Team ${j+1}")
                            crew.put(o)
                        }
                        u.put("officers", crew).put("officer", lead)
                    }
                }
            }
        require(input.optInt("version") in listOf(2, 3)) { "Unbekanntes Speicherformat" }
        val r = region(input.optString("regionId", input.optString("region", "Hamburg")))
        val preset = input.optString("shiftType", "early")
        val duration =
            input.optInt(
                "shiftDurationMinutes",
                when (preset) {
                    "day12",
                    "night12" -> 720
                    "full24" -> 1440
                    else -> 480
                },
            )
        val start =
            input.optInt("startHour", -1).takeIf { it >= 0 }?.times(60)
                ?: input.optInt(
                    "shiftStart",
                    when (preset) {
                        "night" -> 1320
                        "night12" -> 1080
                        "late" -> 840
                        else -> 360
                    },
                )
        val day = input.optInt("day", 1)
        val elapsed = input.optInt("elapsed")
        val rules = input.optJSONObject("rules") ?: JSONObject()
        val o =
            JSONObject()
                .put("schemaVersion", 5)
                .put("region", r)
                .put("mode", input.optString("mode", "career").uppercase())
                .put("day", day)
                .put("phase", phase(input.optString("stage")))
                .put("startMinute", start)
                .put("duration", duration)
                .put("elapsed", elapsed)
                .put("paused", true)
                .put("budget", input.optInt("budget", 240000))
                .put("reputation", input.optInt("reputation", 60))
                .put("nextId", input.optInt("seq", 1) + 1)
                .put("speed", input.optInt("speed", 1).takeIf { it in listOf(1, 4, 8, 16) } ?: 1)
                .put("autoCalls", input.optBoolean("autoCalls"))
                .put(
                    "absoluteMinute",
                    input.optLong("absoluteSimMinutes", (day - 1) * 1440L + start + elapsed),
                )
                .put(
                    "shiftStartAbsolute",
                    input.optLong("shiftStartAbsoluteMinute", (day - 1) * 1440L + start),
                )
                .put(
                    "completedShifts",
                    input.optInt("completedShifts", array(input, "history").length()),
                )
                .put("totalHours", input.optDouble("totalHours", 0.0))
                .put("unlimitedBudget", !rules.optBoolean("budgetLimited", true))
                .put("fatigueEnabled", rules.optBoolean("fatigueEnabled", true))
                .put("customEnabled", rules.optBoolean("customEnabled", true))
                .put("callInterval", rules.optInt("callInterval", 30))
                .put("randomSeed", input.optLong("rngSeed", 982341))
                .put("legacySource", input.toString())
        val defaultLon = if (r == "DE-NI") 9.732 else if (r == "DE-BY") 11.576 else 10.0
        val defaultLat = if (r == "DE-NI") 52.376 else if (r == "DE-BY") 48.137 else 53.55
        o.put(
            "units",
            items(array(input, "units")) { v ->
                val pos = v.optJSONArray("position")
                val base = v.optJSONArray("basePosition")
                val officers = items(array(v, "officers")) { officer(it) }
                val lead = officers.optJSONObject(0) ?: officer(JSONObject())
                val training = v.optJSONObject("training")
                val vehicle = v.optJSONObject("vehicle") ?: JSONObject()
                val assigned = id(v, "incidentId")
                val u =
                    JSONObject()
                        .put("id", id(v, "id"))
                        .put("callsign", v.optString("name", "Einheit"))
                        .put("type", v.optString("type", "STREIFE"))
                        .put(
                            "region",
                            v.optString("regionId").let { if (it.isBlank()) r else region(it) },
                        )
                        .put("lat", pos?.optDouble(1) ?: defaultLat)
                        .put("lon", pos?.optDouble(0) ?: defaultLon)
                        .put("homeLat", base?.optDouble(1) ?: pos?.optDouble(1) ?: defaultLat)
                        .put("homeLon", base?.optDouble(0) ?: pos?.optDouble(0) ?: defaultLon)
                        .put("crew", officers.length().coerceAtLeast(1))
                        .put("officer", lead)
                        .put("officers", officers)
                        .put("equipment", lead.getJSONArray("equipment"))
                        .put("incidentId", assigned)
                        .put(
                            "status",
                            if (training != null) "TRAINING"
                            else if (assigned.isNotBlank()) "ON_SCENE" else "FREE",
                        )
                        .put(
                            "training",
                            courses[training?.optString("courseId")]
                                ?: training?.optString("courseId")
                                ?: "",
                        )
                        .put("trainingLeft", training?.optInt("remainingDays") ?: 0)
                        .put(
                            "vehicleSpeed",
                            vehicle.optDouble("speedKmh", vehicle.optDouble("speed", 62.0)),
                        )
                        .put("vehicleCondition", vehicle.optInt("condition", 100))
                        .put("vehicleName", vehicle.optString("name", "Dienstfahrzeug"))
                        .put("onDuty", v.optBoolean("shiftDuty", true))
                val teams = JSONArray()
                val reserve = array(v, "reserveOfficers")
                for (k in 0 until reserve.length()) teams.put(
                    items(reserve.getJSONArray(k)) { officer(it) }
                )
                u.put("reliefTeams", teams)
                u
            },
        )
        o.put(
            "incidents",
            items(array(input, "incidents")) { v ->
                val t = template(v)
                val pos = v.optJSONArray("position")
                val assigned = strings(array(v, "assignedIds")) { it }
                val stat = status(v.optString("status", "open"))
                val hand = v.optJSONObject("handover")
                t.put("id", id(v, "id"))
                    .put("location", v.optString("city", "Importierter Ort"))
                    .put(
                        "region",
                        v.optString("regionId").let { if (it.isBlank()) r else region(it) },
                    )
                    .put("lat", pos?.optDouble(1) ?: defaultLat)
                    .put("lon", pos?.optDouble(0) ?: defaultLon)
                    .put("status", stat)
                    .put("assignedUnits", assigned)
                    .put("assignedUnit", assigned.optString(0))
                    .put("age", (input.optInt("elapsed") - v.optInt("created")).coerceAtLeast(0))
                    .put("stageId", v.optString("chainNode"))
                    .put("followSpawned", v.optBoolean("followUpSpawned"))
                    .put("decision", v.optString("decisionId"))
                    .put("riskDelta", v.optDouble("riskDelta", 0.0))
                    .put("handoffStatus", status(hand?.optString("status") ?: "open"))
                t
            },
        )
        val incidentById = mutableMapOf<String, JSONObject>()
        val calls = o.getJSONArray("incidents")
        for (i in 0 until calls.length()) {
            val c = calls.getJSONObject(i)
            incidentById[c.getString("id")] = c
        }
        val sourceUnits = array(input, "units")
        val sources = mutableMapOf<String, JSONObject>()
        for (i in 0 until sourceUnits.length()) {
            val v = sourceUnits.getJSONObject(i)
            sources[id(v, "id")] = v
        }
        val units = o.getJSONArray("units")
        val now = o.getLong("absoluteMinute")
        for (i in 0 until units.length()) {
            val u = units.getJSONObject(i)
            val source = sources[u.getString("id")] ?: continue
            val call = incidentById[u.optString("incidentId")]
            val sourceRoute = source.optJSONObject("route")
            if (
                sourceRoute != null &&
                    sourceRoute.optJSONArray("points")?.length()?.let { it >= 2 } == true &&
                    call?.optString("status") != "WORKING"
            ) {
                val points = sourceRoute.getJSONArray("points")
                val first = points.getJSONArray(0)
                val last = points.getJSONArray(points.length() - 1)
                val total =
                    kotlin.math
                        .ceil(sourceRoute.optDouble("durationMinutes", 1.0))
                        .toInt()
                        .coerceAtLeast(1)
                val spent = sourceRoute.optInt("elapsedMinutes", 0).coerceIn(0, total)
                u.put(
                        "status",
                        if (source.optString("status") == "returning") "RETURNING" else "DRIVING",
                    )
                    .put("routePoints", points)
                    .put("startLon", first.getDouble(0))
                    .put("startLat", first.getDouble(1))
                    .put("destLon", last.getDouble(0))
                    .put("destLat", last.getDouble(1))
                    .put("travelTotal", total)
                    .put("travelLeft", total - spent)
                    .put(
                        "readinessLeft",
                        (sourceRoute.optLong("departureAt", now) - now).coerceAtLeast(0),
                    )
                    .put("routeBasis", sourceRoute.optString("basis", "DISTANCE_APPROXIMATION"))
            } else if (call?.optString("status") == "DRIVING") {
                val from = GeoPoint(u.getDouble("lon"), u.getDouble("lat"))
                val to = GeoPoint(call.getDouble("lon"), call.getDouble("lat"))
                val route = NativeRouting.route(from, to, u.optDouble("vehicleSpeed", 62.0))
                u.put("status", "DRIVING")
                    .put(
                        "routePoints",
                        JSONArray().apply {
                            route.points.forEach { put(JSONArray(listOf(it.lon, it.lat))) }
                        },
                    )
                    .put("startLon", from.lon)
                    .put("startLat", from.lat)
                    .put("destLon", to.lon)
                    .put("destLat", to.lat)
                    .put("travelTotal", route.durationMinutes)
                    .put("travelLeft", route.durationMinutes)
            } else if (call?.optString("status") in listOf("WORKING", "HANDED")) {
                val originalCalls = array(input, "incidents")
                var original: JSONObject? = null
                for (j in 0 until originalCalls.length()) if (
                    id(originalCalls.getJSONObject(j), "id") == call?.optString("id")
                )
                    original = originalCalls.getJSONObject(j)
                val hand = original?.optJSONObject("handover")
                val work =
                    hand?.optInt("remainingWork", -1)?.takeIf { it >= 0 }
                        ?: original
                            ?.optLong("completeAt", now + call!!.optInt("duration", 15))
                            ?.minus(now)
                            ?.toInt()
                        ?: call!!.optInt("duration", 15)
                u.put("workLeft", work.coerceAtLeast(1))
                if (
                    call?.optString("status") == "WORKING" ||
                        call?.optString("handoffStatus") == "WORKING"
                )
                    u.put("status", "WORKING")
            }
        }

        o.put("logs", array(input, "logs"))
        o.put("customTemplates", items(array(input, "templates")) { template(it) })
        val st = input.optJSONObject("shiftStats") ?: JSONObject()
        for ((source, target) in
            mapOf(
                "total" to "shiftIncoming",
                "solved" to "shiftSolved",
                "failed" to "shiftFailed",
                "transferred" to "shiftHanded",
                "income" to "shiftIncome",
            )) o.put(target, st.optInt(source))
        o.put(
            "reports",
            items(array(input, "history")) { h ->
                JSONObject()
                    .put("day", h.optInt("day", 1))
                    .put("region", region(h.optString("region", r)))
                    .put("duration", h.optInt("durationMinutes", h.optInt("duration", 480)))
                    .put("elapsed", h.optInt("elapsed", 480))
                    .put("served", h.optInt("total"))
                    .put("solved", h.optInt("solved"))
                    .put("failed", h.optInt("failed"))
                    .put("handed", h.optInt("transferred"))
                    .put("income", h.optInt("income"))
                    .put("expenses", h.optInt("expenses"))
                    .put("rotations", h.optInt("rotations"))
                    .put("trainingDone", array(h, "trainingDone"))
            },
        )
        val audio = input.optJSONObject("audio")
        if (audio != null) {
            o.put("masterVolume", audio.optDouble("master", .7))
                .put("radioVolume", audio.optDouble("radio", .7))
                .put("sfxVolume", audio.optDouble("sfx", .7))
                .put("ambientVolume", audio.optDouble("ambient", 0.0))
                .put("mute", audio.optBoolean("mute"))
        }
        return o
    }
}
