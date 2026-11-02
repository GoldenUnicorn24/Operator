package de.einsatzleitstelle110

import org.json.JSONArray
import org.json.JSONObject

/** Optional schema-6 section; missing chief data in old saves means an untouched legacy fleet. */
object PoliceChiefCodec {
    private fun officer(o: Officer) = JSONObject().apply {
        put("name", o.name); put("skill", o.skill); put("energy", o.energy); put("experience", o.experience)
        put("portrait", o.portrait); put("equipment", JSONArray(o.equipment.toList())); put("owned", JSONArray(o.owned.toList()))
        put("qualifications", JSONArray(o.qualifications.toList())); put("dutyMinutes", o.dutyMinutes); put("restMinutes", o.restMinutes)
        put("photo", o.photo); put("personnelId", o.personnelId)
    }
    private fun strings(a: JSONArray?): MutableSet<String> = (0 until (a?.length() ?: 0)).mapNotNull { a?.optString(it)?.takeIf { v -> v.isNotBlank() } }.toMutableSet()
    private fun officer(j: JSONObject, id: String) = Officer(
        name = j.optString("name", "Fiktive Einsatzkraft").take(80), skill = j.optInt("skill", 58).coerceIn(0, 100),
        energy = j.optInt("energy", 100).coerceIn(0, 100), experience = j.optInt("experience", 0).coerceAtLeast(0),
        portrait = j.optInt("portrait", 1).coerceIn(1, 8),
        equipment = strings(j.optJSONArray("equipment")).also { it.add("P99") },
        owned = strings(j.optJSONArray("owned")).also { it.add("P99") },
        qualifications = strings(j.optJSONArray("qualifications")), dutyMinutes = j.optInt("dutyMinutes").coerceAtLeast(0),
        restMinutes = j.optInt("restMinutes", 960).coerceAtLeast(0), photo = j.optString("photo"), personnelId = id,
    )
    fun write(state: PoliceChiefState): JSONObject = JSONObject().apply {
        put("accruedFuel", state.accruedFuel); put("activeGroup", state.activeGroup); put("nextId", state.nextId); put("lastBilledDay", state.lastBilledDay)
        put("insolvencyWarning", state.insolvencyWarning); put("hiredApplicants", JSONArray(state.hiredApplicants.toList()))
        put("stations", JSONArray().apply { state.stations.forEach { s -> put(JSONObject().apply {
            put("id", s.id); put("name", s.name); put("region", s.region); put("scope", s.scope); put("lon", s.lon); put("lat", s.lat)
            put("parking", s.parking); put("lounge", s.lounge); put("cells", s.cells); put("workshop", s.workshop)
        }) } })
        put("vehicles", JSONArray().apply { state.vehicles.forEach { v -> put(JSONObject().apply {
            put("id", v.id); put("unitId", v.unitId); put("modelId", v.modelId); put("stationId", v.stationId)
            put("livery", v.livery); put("customTypeId", v.customTypeId); put("odometerKm", v.odometerKm); put("unbilledKm", v.unbilledKm)
        }) } })
        put("employees", JSONArray().apply { state.employees.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("officer", officer(e.officer)); put("rank", e.rank); put("group", e.group)
            put("startHour", e.startHour); put("dutyHours", e.dutyHours); put("assignedUnitId", e.assignedUnitId)
        }) } })
        put("unitTypes", JSONArray().apply { state.unitTypes.forEach { t -> put(JSONObject().apply {
            put("id", t.id); put("name", t.name); put("color", t.color); put("modelId", t.modelId)
            put("minCrew", t.minCrew); put("capabilities", JSONArray(t.capabilities.toList()))
        }) } })
        put("dailyCosts", JSONArray().apply { state.dailyCosts.forEach { d -> put(JSONObject().apply {
            put("day", d.day); put("wages", d.wages); put("maintenance", d.maintenance); put("fuel", d.fuel); put("buildings", d.buildings)
        }) } })
    }

    fun read(root: JSONObject?): PoliceChiefState {
        val state = PoliceChiefState()
        if (root == null) return state
        fun rows(key: String, max: Int): List<JSONObject> {
            val a = root.optJSONArray(key) ?: return emptyList()
            return (0 until a.length().coerceAtMost(max)).mapNotNull { a.optJSONObject(it) }
        }
        state.accruedFuel = root.optDouble("accruedFuel", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0
        state.activeGroup = root.optString("activeGroup", "A").takeIf { it in ChiefCatalog.groups } ?: "A"
        state.nextId = root.optInt("nextId", 1).coerceIn(1, 1_000_000)
        state.lastBilledDay = root.optInt("lastBilledDay").coerceAtLeast(0)
        state.insolvencyWarning = root.optString("insolvencyWarning").take(500)
        state.hiredApplicants.addAll(strings(root.optJSONArray("hiredApplicants")))
        for (j in rows("stations", 40)) {
            val id = j.optString("id"); val name = j.optString("name").take(48)
            val lon = j.optDouble("lon", Double.NaN); val lat = j.optDouble("lat", Double.NaN)
            if (id.isBlank() || name.isBlank() || !lon.isFinite() || !lat.isFinite() || lon !in -180.0..180.0 || lat !in -85.0..85.0 || state.stations.any { it.id == id }) continue
            state.stations.add(ChiefStation(id, name, j.optString("region", "DE-HH"), j.optString("scope", "STATE"), lon, lat,
                j.optInt("parking", 2).coerceIn(2, 20), j.optInt("lounge").coerceIn(0, 3), j.optInt("cells").coerceIn(0, 3), j.optInt("workshop").coerceIn(0, 3)))
        }
        for (j in rows("unitTypes", 40)) {
            val model = ChiefCatalog.model(j.optString("modelId")) ?: continue
            val id = j.optString("id"); val name = j.optString("name").take(40)
            val capabilities = strings(j.optJSONArray("capabilities")).intersect(model.capabilities)
            if (id.isBlank() || name.isBlank() || capabilities.isEmpty() || state.unitTypes.any { it.id == id }) continue
            state.unitTypes.add(ChiefUnitType(id, name, j.optInt("color", 0xff4fc3ff.toInt()) or (0xff shl 24), model.id,
                j.optInt("minCrew", model.minCrew).coerceIn(model.minCrew, model.seats), capabilities))
        }
        for (j in rows("vehicles", 500)) {
            val id = j.optString("id"); val unitId = j.optString("unitId"); val model = ChiefCatalog.model(j.optString("modelId")) ?: continue
            val station = state.stations.find { it.id == j.optString("stationId") } ?: continue
            if (id.isBlank() || unitId.isBlank() || state.vehicles.any { it.id == id || it.unitId == unitId }) continue
            val custom = j.optString("customTypeId").takeIf { value -> state.unitTypes.any { it.id == value && it.modelId == model.id } } ?: ""
            val odometer = j.optDouble("odometerKm", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0
            val unbilled = j.optDouble("unbilledKm", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0
            state.vehicles.add(ChiefVehicle(id, unitId, model.id, station.id, j.optString("livery").takeIf { it in ChiefCatalog.liveries } ?: "BLUE_SILVER", custom, odometer, unbilled))
        }
        for (j in rows("employees", 400)) {
            val id = j.optString("id")
            if (id.isBlank() || state.employees.any { it.id == id }) continue
            val o = j.optJSONObject("officer") ?: JSONObject()
            val unitId = j.optString("assignedUnitId").takeIf { v -> state.vehicles.any { it.unitId == v } } ?: ""
            state.employees.add(ChiefEmployee(id, officer(o, id), j.optInt("rank").coerceIn(0, ChiefCatalog.ranks.lastIndex),
                j.optString("group", "A").takeIf { it in ChiefCatalog.groups } ?: "A", j.optInt("startHour").coerceIn(0, 23),
                j.optInt("dutyHours", 24).takeIf { it in listOf(8, 12, 24) } ?: 24, unitId))
        }
        for (j in rows("dailyCosts", 120)) state.dailyCosts.add(ChiefDailyCosts(j.optInt("day").coerceAtLeast(1),
            j.optInt("wages").coerceAtLeast(0), j.optInt("maintenance").coerceAtLeast(0), j.optInt("fuel").coerceAtLeast(0), j.optInt("buildings").coerceAtLeast(0)))
        // Survive old sections that did not store an ID counter without colliding with existing rows.
        val ids = state.stations.map { it.id } + state.vehicles.flatMap { listOf(it.id, it.unitId) } + state.employees.map { it.id } + state.unitTypes.map { it.id }
        state.nextId = maxOf(state.nextId, (ids.mapNotNull { it.substringAfterLast('-').toIntOrNull() }.maxOrNull() ?: 0) + 1)
        return state
    }
}
