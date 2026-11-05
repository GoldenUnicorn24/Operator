package de.einsatzleitstelle110

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Fictional management values, never manufacturer specifications or real police locations. */
data class ChiefVehicleModel(
    val id: String, val name: String, val baseType: String, val price: Int, val seats: Int,
    val speed: Double, val cargoKg: Int, val maintenance: Int, val litresPer100Km: Double,
    val minCrew: Int = 2, val careerLevel: Int = 1, val icon: String = "CAR",
    val capabilities: Set<String> = setOf(baseType),
)

object ChiefCatalog {
    val liveries = listOf("BLUE_SILVER", "GREEN_WHITE")
    val groups = listOf("A", "B", "C", "D", "E")
    val ranks = listOf("Polizeimeister/in", "Polizeiobermeister/in", "Polizeihauptmeister/in", "Polizeikommissar/in", "Polizeioberkommissar/in")
    val salaryPerDay = listOf(130, 155, 180, 215, 250)
    val models = listOf(
        ChiefVehicleModel("sedan", "Nordlicht 110 Limousine", "STREIFE", 27000, 4, 62.0, 55, 38, 8.0),
        ChiefVehicleModel("estate", "Hanse 120 Kombi", "STREIFE", 32000, 5, 60.0, 90, 44, 8.5),
        ChiefVehicleModel("group", "Deich 8 Gruppenwagen", "BEREITSCHAFT", 55000, 8, 52.0, 180, 65, 12.0, capabilities = setOf("BEREITSCHAFT", "STREIFE")),
        ChiefVehicleModel("civil", "Kontor Z Zivilfahrzeug", "KRIPO", 30000, 4, 64.0, 50, 40, 7.5, capabilities = setOf("KRIPO", "STREIFE")),
        ChiefVehicleModel("motorcycle", "Alster M Motorrad", "VERKEHR", 16000, 1, 68.0, 15, 25, 4.5, minCrew = 1, icon = "MOTORCYCLE", capabilities = setOf("VERKEHR", "STREIFE")),
        ChiefVehicleModel("dog", "Waldpfad D Diensthundefahrzeug", "HUND", 42000, 3, 58.0, 70, 52, 9.0, capabilities = setOf("HUND", "STREIFE")),
        ChiefVehicleModel("traffic", "Elbspur V Unfallaufnahme", "VERKEHR", 58000, 4, 54.0, 160, 62, 10.0, capabilities = setOf("VERKEHR", "STREIFE")),
        ChiefVehicleModel("special", "Granit S Sonderwagen", "SEK", 140000, 6, 46.0, 220, 160, 21.0, minCrew = 4, careerLevel = 3, icon = "ARMORED", capabilities = setOf("SEK", "STREIFE")),
        ChiefVehicleModel("helicopter", "Küstenwind H Hubschrauber", "HELI", 1250000, 3, 180.0, 110, 900, 95.0, careerLevel = 4, icon = "HELICOPTER"),
    )
    fun model(id: String) = models.firstOrNull { it.id == id }
    /** Player chooses the digits, but the radio naming grammar remains predictable. */
    fun validCallsign(value: String) = Regex("Peter [1-9][0-9]{0,2}/[1-9][0-9]?").matches(value)
}

data class ChiefStation(
    val id: String, var name: String, val region: String, val scope: String,
    val lon: Double, val lat: Double, var parking: Int = 2, var lounge: Int = 0,
    var cells: Int = 0, var workshop: Int = 0,
) {
    val level: Int get() = 1 + (parking - 2) / 2 + lounge + cells + workshop
    val dailyCost: Int get() = 90 + parking * 12 + lounge * 25 + cells * 35 + workshop * 45
}

data class ChiefVehicle(
    val id: String, val unitId: String, val modelId: String, var stationId: String,
    var livery: String = "BLUE_SILVER", var customTypeId: String = "",
    var odometerKm: Double = 0.0, var unbilledKm: Double = 0.0,
)

data class ChiefEmployee(
    val id: String, var officer: Officer, var rank: Int = 0,
    var group: String = "A", var startHour: Int = 0, var dutyHours: Int = 24,
    var assignedUnitId: String = "",
) {
    val salary: Int get() = ChiefCatalog.salaryPerDay[rank.coerceIn(0, ChiefCatalog.salaryPerDay.lastIndex)]
}

data class ChiefApplicant(val id: String, val name: String, val rank: Int, val skill: Int, val portrait: Int, val hiringCost: Int = 1500)
data class ChiefUnitType(val id: String, var name: String, var color: Int, val modelId: String, val minCrew: Int, val capabilities: Set<String>)
data class ChiefDailyCosts(val day: Int, val wages: Int, val maintenance: Int, val fuel: Int, val buildings: Int) {
    val total: Int get() = wages + maintenance + fuel + buildings
}

data class PoliceChiefState(
    val stations: MutableList<ChiefStation> = mutableListOf(),
    val vehicles: MutableList<ChiefVehicle> = mutableListOf(),
    val employees: MutableList<ChiefEmployee> = mutableListOf(),
    val unitTypes: MutableList<ChiefUnitType> = mutableListOf(),
    val dailyCosts: MutableList<ChiefDailyCosts> = mutableListOf(),
    val hiredApplicants: MutableSet<String> = mutableSetOf(),
    var activeGroup: String = "A", var nextId: Int = 1,
    var lastBilledDay: Int = 0, var insolvencyWarning: String = "", var accruedFuel: Double = 0.0,
) {
    fun deepCopy() = copy(
        stations = stations.map { it.copy() }.toMutableList(),
        vehicles = vehicles.map { it.copy() }.toMutableList(),
        employees = employees.map { it.copy(officer = it.officer.copy(equipment = it.officer.equipment.toMutableSet(), owned = it.officer.owned.toMutableSet(), qualifications = it.officer.qualifications.toMutableSet())) }.toMutableList(),
        unitTypes = unitTypes.map { it.copy(capabilities = it.capabilities.toSet()) }.toMutableList(),
        dailyCosts = dailyCosts.toMutableList(), hiredApplicants = hiredApplicants.toMutableSet(),
    )
}

private fun GameController.chiefReject(reason: String): Boolean { lastError = reason; return false }
private fun GameController.chiefPay(cost: Int): Boolean {
    if (cost < 0 || !state.unlimitedBudget && state.budget < cost) return chiefReject("Budget reicht nicht: $cost € erforderlich.")
    if (!state.unlimitedBudget) state.budget -= cost
    return true
}
private fun GameController.chiefLog(message: String) {
    state.logs.add(0, "[Tag ${state.day}] Dienststellenverwaltung: $message")
    while (state.logs.size > 250) state.logs.removeAt(state.logs.lastIndex)
}
private fun GameController.chiefPlanning(): Boolean = state.phase == "PLANNING" || chiefReject("Verwaltung nur in der Schichtplanung.")
private fun GameController.chiefId(prefix: String) = "$prefix-${state.chief.nextId++}"
private fun GameController.chiefUnitFree(u: UnitState): Boolean = u.status == "FREE" && u.incidentId.isBlank() && u.trainingLeft == 0

fun GameController.createChiefStation(name: String, point: GeoPoint): ChiefStation? {
    lastError = ""
    if (!chiefPlanning()) return null
    if (name.isBlank() || name.trim().length > 48 || !point.lon.isFinite() || !point.lat.isFinite() || !region().contains(point) || !inScope(point)) {
        chiefReject("Name (1–48 Zeichen) und Standort innerhalb der Zuständigkeit erforderlich."); return null
    }
    if (state.chief.stations.size >= 40 || state.chief.stations.any { it.name.equals(name.trim(), true) }) {
        chiefReject("Höchstens 40 Dienststellen; jeder Name muss eindeutig sein."); return null
    }
    if (!chiefPay(30000)) return null
    return ChiefStation(chiefId("station"), name.trim(), state.region, state.scope, point.lon, point.lat).also {
        state.chief.stations.add(it); chiefLog("${it.name} gegründet (fiktive Dienststelle), 30.000 €.")
    }
}

fun GameController.upgradeChiefStation(stationId: String, upgrade: String): Boolean {
    if (!chiefPlanning()) return false
    val s = state.chief.stations.find { it.id == stationId } ?: return chiefReject("Dienststelle fehlt.")
    val cost = when (upgrade) {
        "PARKING" -> if (s.parking < 20) 12000 + s.parking * 1000 else return chiefReject("Maximal 20 Stellplätze.")
        "LOUNGE" -> if (s.lounge < 3) 14000 * (s.lounge + 1) else return chiefReject("Aufenthaltsräume vollständig ausgebaut.")
        "CELLS" -> if (s.cells < 3) 18000 * (s.cells + 1) else return chiefReject("Arrestbereich vollständig ausgebaut.")
        "WORKSHOP" -> if (s.workshop < 3) 22000 * (s.workshop + 1) else return chiefReject("Werkstatt vollständig ausgebaut.")
        else -> return chiefReject("Unbekannter Ausbau.")
    }
    if (!chiefPay(cost)) return false
    when (upgrade) { "PARKING" -> s.parking += 2; "LOUNGE" -> s.lounge++; "CELLS" -> s.cells++; "WORKSHOP" -> s.workshop++ }
    chiefLog("${s.name}: $upgrade ausgebaut, $cost €."); return true
}

fun GameController.buyChiefVehicle(modelId: String, stationId: String, callsign: String, livery: String = "BLUE_SILVER", customTypeId: String = ""): ChiefVehicle? {
    lastError = ""
    if (!chiefPlanning()) return null
    val model = ChiefCatalog.model(modelId) ?: run { chiefReject("Fahrzeugmodell fehlt."); return null }
    val station = state.chief.stations.find { it.id == stationId } ?: run { chiefReject("Dienststelle fehlt."); return null }
    if (station.region != state.region || station.scope != state.scope) { chiefReject("Dienststelle liegt außerhalb der aktuellen Zuständigkeit."); return null }
    if (state.careerLevel < model.careerLevel && state.mode != "SANDBOX") { chiefReject("Karrierestufe ${model.careerLevel} erforderlich."); return null }
    if (!ChiefCatalog.validCallsign(callsign) || state.units.any { it.callsign == callsign } || livery !in ChiefCatalog.liveries) {
        chiefReject("Eindeutiger Funkrufname nach Schema Peter 31/1 und gültige Lackierung erforderlich."); return null
    }
    if (state.chief.vehicles.count { it.stationId == station.id } >= station.parking) { chiefReject("Keine freien Stellplätze. Dienststelle ausbauen."); return null }
    if (customTypeId.isNotBlank() && state.chief.unitTypes.none { it.id == customTypeId && it.modelId == modelId }) { chiefReject("Einheitentyp passt nicht zum Fahrzeug."); return null }
    if (state.units.size >= 500 || !chiefPay(model.price)) return null
    val unitId = chiefId("chief-unit")
    val vacant = Officer("Unbesetzt")
    val unit = UnitState(unitId, callsign, model.baseType, station.region, station.lat, station.lon,
        crew = 0, officer = vacant, officers = mutableListOf(), vehicleSpeed = model.speed, vehicleName = model.name,
        scope = station.scope, onDuty = true)
    state.units.add(unit)
    return ChiefVehicle(chiefId("vehicle"), unitId, modelId, station.id, livery, customTypeId).also {
        state.chief.vehicles.add(it); chiefLog("${model.name} als $callsign gekauft, ${model.price} €; unbesetzt.")
    }
}

fun GameController.chiefVehicle(unit: UnitState) = state.chief.vehicles.find { it.unitId == unit.id }
fun GameController.chiefMinCrew(unit: UnitState): Int {
    val v = chiefVehicle(unit) ?: return minCrew(unit.type)
    return state.chief.unitTypes.find { it.id == v.customTypeId }?.minCrew ?: ChiefCatalog.model(v.modelId)?.minCrew ?: minCrew(unit.type)
}
fun GameController.chiefMaxCrew(unit: UnitState): Int = chiefVehicle(unit)?.let { ChiefCatalog.model(it.modelId)?.seats } ?: maxCrew(unit.type)
fun GameController.chiefCapabilities(unit: UnitState): Set<String>? {
    val v = chiefVehicle(unit) ?: return null
    return state.chief.unitTypes.find { it.id == v.customTypeId }?.capabilities ?: ChiefCatalog.model(v.modelId)?.capabilities.orEmpty()
}

fun GameController.assignChiefVehicle(vehicleId: String, stationId: String): Boolean {
    if (!chiefPlanning()) return false
    val v = state.chief.vehicles.find { it.id == vehicleId } ?: return false
    val station = state.chief.stations.find { it.id == stationId } ?: return false
    val u = state.units.find { it.id == v.unitId } ?: return false
    if (!chiefUnitFree(u) || station.region != u.region || station.scope != state.scope) return chiefReject("Fahrzeug muss frei sein; Ziel muss in der Zuständigkeit liegen.")
    if (v.stationId == stationId) return true
    if (state.chief.vehicles.count { it.stationId == stationId } >= station.parking) return chiefReject("Keine freien Stellplätze.")
    v.stationId = stationId
    u.homeLon = station.lon; u.homeLat = station.lat; u.lon = station.lon; u.lat = station.lat; u.scope = station.scope
    chiefLog("${u.callsign} nach ${station.name} versetzt."); return true
}
fun GameController.configureChiefVehicle(vehicleId: String, callsign: String, livery: String): Boolean {
    if (!chiefPlanning()) return false
    val v = state.chief.vehicles.find { it.id == vehicleId } ?: return false
    val u = state.units.find { it.id == v.unitId } ?: return false
    if (!chiefUnitFree(u) || !ChiefCatalog.validCallsign(callsign) || state.units.any { it.id != u.id && it.callsign == callsign } || livery !in ChiefCatalog.liveries) return chiefReject("Freies Fahrzeug, gültiger eindeutiger Funkrufname und Lackierung erforderlich.")
    u.callsign = callsign; v.livery = livery; return true
}
fun GameController.sellChiefVehicle(vehicleId: String): Boolean {
    if (!chiefPlanning()) return false
    val v = state.chief.vehicles.find { it.id == vehicleId } ?: return false
    val u = state.units.find { it.id == v.unitId } ?: return false
    if (!chiefUnitFree(u)) return chiefReject("Gebundene Fahrzeuge können nicht verkauft werden.")
    if (u.officers.any { it.personnelId.isBlank() }) return chiefReject("Zuerst vorhandenes Bestandspersonal versetzen.")
    val price = ((ChiefCatalog.model(v.modelId)?.price ?: 0) * .6 * u.vehicleCondition.coerceIn(0, 100) / 100).roundToInt()
    state.chief.employees.filter { it.assignedUnitId == u.id }.forEach { e -> e.officer = chiefOfficer(e); e.assignedUnitId = "" }
    state.chief.accruedFuel += v.unbilledKm / 100 * (ChiefCatalog.model(v.modelId)?.litresPer100Km ?: 0.0) * 1.85
    state.units.remove(u); state.chief.vehicles.remove(v)
    if (!state.unlimitedBudget) state.budget += price
    chiefLog("${u.callsign} verkauft, $price € Erlös; Besatzung in Personalreserve."); return true
}

fun GameController.chiefApplicants(): List<ChiefApplicant> {
    val first = listOf("Mara", "Jonas", "Nele", "Elias", "Aylin", "Tom", "Lea", "Noah")
    val last = listOf("Hansen", "Peters", "Becker", "Yilmaz", "Fischer", "König", "Weber", "Adler")
    return (0 until 8).map { n ->
        val serial = state.day * 8 + n
        ChiefApplicant("applicant-${state.day}-$n", "${first[n]} ${last[(n + state.day) % last.size]} ($serial)", n % 2, 56 + (serial * 7 % 27), n + 1)
    }.filter { it.id !in state.chief.hiredApplicants }
}
fun GameController.hireChiefOfficer(applicantId: String): ChiefEmployee? {
    if (!chiefPlanning()) return null
    val applicant = chiefApplicants().find { it.id == applicantId } ?: run { chiefReject("Bewerbung nicht mehr verfügbar."); return null }
    if (state.chief.employees.size >= 400 || !chiefPay(applicant.hiringCost)) return null
    val id = chiefId("employee")
    return ChiefEmployee(id, Officer(applicant.name, skill = applicant.skill, portrait = applicant.portrait, personnelId = id), rank = applicant.rank).also {
        state.chief.employees.add(it); state.chief.hiredApplicants.add(applicant.id); chiefLog("${applicant.name} eingestellt; ${it.salary} € Gehalt pro Spieltag.")
    }
}
/** An assigned unit's officer is authoritative after a save round-trip or old personnel transfer. */
fun GameController.chiefOfficer(employee: ChiefEmployee): Officer = state.units.asSequence().flatMap { (it.officers + it.reliefTeams.flatten()).asSequence() }.firstOrNull { it.personnelId == employee.id } ?: employee.officer

fun GameController.assignChiefOfficer(employeeId: String, unitId: String?): Boolean {
    if (!chiefPlanning()) return false
    val employee = state.chief.employees.find { it.id == employeeId } ?: return false
    val target = unitId?.let { id -> state.units.find { it.id == id } ?: return false }
    val old = state.units.firstOrNull { u -> u.officers.any { it.personnelId == employeeId } || u.reliefTeams.any { team -> team.any { it.personnelId == employeeId } } }
    if (old != null && !chiefUnitFree(old) || target != null && !chiefUnitFree(target)) return chiefReject("Versetzung nur zwischen freien Fahrzeugen.")
    if (target != null && chiefVehicle(target) == null) return chiefReject("Ziel muss ein eigenes Fahrzeug sein.")
    if (target != null && (state.chief.employees.count { it.assignedUnitId == target.id && it.id != employeeId && it.group == employee.group } + target.officers.count { it.personnelId.isBlank() }) >= chiefMaxCrew(target)) return chiefReject("Alle Sitzplätze der Dienstgruppe belegt.")
    employee.officer = chiefOfficer(employee)
    if (old != null) {
        old.officers.removeAll { it.personnelId == employeeId }; old.reliefTeams.forEach { it.removeAll { o -> o.personnelId == employeeId } }
        old.crew = old.officers.size
        old.officer = old.officers.firstOrNull() ?: Officer("Unbesetzt")
    }
    employee.assignedUnitId = target?.id ?: ""
    if (target != null && chiefScheduled(employee)) {
        target.officers.add(employee.officer); target.crew = target.officers.size; target.officer = target.officers.first()
    }
    return true
}

private fun GameController.chiefScheduled(employee: ChiefEmployee): Boolean {
    val hour = if (state.phase == "RUNNING") hour() else state.startMinute / 60
    return employee.group == state.chief.activeGroup && (hour - employee.startHour + 24) % 24 < employee.dutyHours
}
fun GameController.setChiefSchedule(employeeId: String, group: String, startHour: Int, hours: Int): Boolean {
    if (!chiefPlanning()) return false
    val e = state.chief.employees.find { it.id == employeeId } ?: return false
    if (group !in ChiefCatalog.groups || startHour !in 0..23 || hours !in listOf(8, 12, 24)) return chiefReject("Dienstgruppe A–E, Startstunde 0–23 und 8/12/24 Stunden erforderlich.")
    val u = state.units.find { it.id == e.assignedUnitId }
    if (u != null && !chiefUnitFree(u)) return chiefReject("Dienstplan erst bei freiem Fahrzeug ändern.")
    if (u != null && state.chief.employees.count { it.assignedUnitId == u.id && it.id != e.id && it.group == group } >= chiefMaxCrew(u)) return chiefReject("Die Dienstgruppe ist im Fahrzeug bereits vollständig besetzt.")
    e.group = group; e.startHour = startHour; e.dutyHours = hours
    syncChiefRoster(); return true
}
fun GameController.setChiefDutyGroup(group: String): Boolean {
    if (!chiefPlanning() || group !in ChiefCatalog.groups) return false
    if (state.chief.vehicles.any { v -> state.units.find { it.id == v.unitId }?.let { !chiefUnitFree(it) } == true }) return chiefReject("Dienstgruppenwechsel erst nach Rückkehr aller eigenen Fahrzeuge.")
    state.chief.activeGroup = group; syncChiefRoster(); return true
}
/** Scheduled staff may replace a crew only after its current movement/incident has finished. */
fun GameController.chiefDutyEligible(unit: UnitState): Boolean {
    if (chiefVehicle(unit) == null) return true
    return unit.officers.all { officer ->
        officer.personnelId.isBlank() || state.chief.employees.find { it.id == officer.personnelId }?.let { chiefScheduled(it) } == true
    }
}
fun GameController.syncChiefUnitRoster(unit: UnitState) {
    if (chiefVehicle(unit) == null || unit.status !in listOf("FREE", "REST", "OFF_DUTY") || unit.incidentId.isNotBlank() || unit.trainingLeft > 0) return
    val previousCrew = unit.officers.map { it.personnelId }
    val staff = state.chief.employees.filter { it.assignedUnitId == unit.id }
    staff.forEach { it.officer = chiefOfficer(it) }
    val legacy = unit.officers.filter { it.personnelId.isBlank() }
    unit.officers = (legacy + staff.filter { chiefScheduled(it) }.map { it.officer }).take(chiefMaxCrew(unit)).toMutableList()
    unit.crew = unit.officers.size
    unit.officer = unit.officers.firstOrNull() ?: Officer("Unbesetzt")
    if (unit.officers.isNotEmpty() && previousCrew != unit.officers.map { it.personnelId }) {
        // A fresh roster does not inherit the previous crew's personal recovery timer.
        unit.restUntil = 0
        unit.status = "FREE"
    }
}
fun GameController.syncChiefRoster() {
    for (v in state.chief.vehicles) {
        val unit = state.units.find { it.id == v.unitId } ?: continue
        syncChiefUnitRoster(unit)
    }
}
fun GameController.promoteChiefOfficer(employeeId: String): Boolean {
    if (!chiefPlanning()) return false
    val e = state.chief.employees.find { it.id == employeeId } ?: return false
    val officer = chiefOfficer(e)
    if (e.rank >= ChiefCatalog.ranks.lastIndex || officer.experience < 60 || officer.skill < 60 + e.rank * 5) return chiefReject("Beförderung benötigt 60 Erfahrung und ausreichende Fähigkeiten.")
    if (!chiefPay(1800)) return false
    e.officer = officer; officer.experience -= 60; officer.skill = (officer.skill + 2).coerceAtMost(100); e.rank++
    chiefLog("${officer.name} befördert: ${ChiefCatalog.ranks[e.rank]}, ${e.salary} €/Tag."); return true
}
fun GameController.fireChiefOfficer(employeeId: String): Boolean {
    if (!chiefPlanning()) return false
    val e = state.chief.employees.find { it.id == employeeId } ?: return false
    if (!assignChiefOfficer(employeeId, null)) return false
    state.chief.employees.remove(e); chiefLog("${e.officer.name} entlassen."); return true
}
fun GameController.defineChiefUnitType(name: String, color: Int, modelId: String, minimumCrew: Int, capabilities: Set<String>): ChiefUnitType? {
    if (!chiefPlanning()) return null
    val model = ChiefCatalog.model(modelId) ?: return null
    if (name.isBlank() || name.length > 40 || minimumCrew !in model.minCrew..model.seats || capabilities.isEmpty() || !model.capabilities.containsAll(capabilities) || state.chief.unitTypes.size >= 40 || state.chief.unitTypes.any { it.name.equals(name.trim(), true) }) {
        chiefReject("Eindeutiger Name, passende Mindestbesatzung und Fähigkeiten des Fahrzeugmodells erforderlich."); return null
    }
    return ChiefUnitType(chiefId("unit-type"), name.trim(), color or (0xff shl 24), modelId, minimumCrew, capabilities.toSet()).also { state.chief.unitTypes.add(it) }
}
fun GameController.setChiefVehicleType(vehicleId: String, customTypeId: String): Boolean {
    if (!chiefPlanning()) return false
    val v = state.chief.vehicles.find { it.id == vehicleId } ?: return false
    val u = state.units.find { it.id == v.unitId } ?: return false
    if (!chiefUnitFree(u) || customTypeId.isNotBlank() && state.chief.unitTypes.none { it.id == customTypeId && it.modelId == v.modelId }) return chiefReject("Fahrzeug muss frei sein und zum Einheitentyp passen.")
    v.customTypeId = customTypeId; return true
}

/** Called with actual distance travelled by the movement engine, including patrol travel. */
fun GameController.recordChiefTravel(unit: UnitState, distanceKm: Double) {
    if (!distanceKm.isFinite() || distanceKm <= 0) return
    chiefVehicle(unit)?.let { it.odometerKm += distanceKm; it.unbilledKm += distanceKm }
}
/** Once per completed management day. Salary includes unassigned reserve personnel. */
fun GameController.closeChiefDay(): ChiefDailyCosts {
    val chief = state.chief
    if (chief.lastBilledDay >= state.day) return chief.dailyCosts.firstOrNull { it.day == state.day } ?: ChiefDailyCosts(state.day, 0, 0, 0, 0)
    var maintenance = 0.0; var fuel = chief.accruedFuel
    chief.accruedFuel = 0.0
    for (v in chief.vehicles) {
        val model = ChiefCatalog.model(v.modelId) ?: continue
        val station = chief.stations.find { it.id == v.stationId }
        maintenance += (model.maintenance + v.unbilledKm * .08) * (1 - .15 * (station?.workshop ?: 0))
        fuel += v.unbilledKm / 100 * model.litresPer100Km * 1.85
        v.unbilledKm = 0.0
    }
    // Arrest rooms lower abstract processing expenses, never describe real detention procedures.
    val cells = chief.stations.sumOf { it.cells }.coerceAtMost(6)
    maintenance *= 1 - cells * .02
    val costs = ChiefDailyCosts(state.day, chief.employees.sumOf { it.salary }, ceil(maintenance).toInt(), ceil(fuel).toInt(), chief.stations.sumOf { it.dailyCost })
    if (!state.unlimitedBudget) state.budget -= costs.total
    chief.lastBilledDay = state.day; chief.dailyCosts.add(0, costs)
    while (chief.dailyCosts.size > 120) chief.dailyCosts.removeAt(chief.dailyCosts.lastIndex)
    // Lounge effects recover personnel after the shift, bounded independently of shift duration.
    chief.employees.forEach { e ->
        val station = chief.vehicles.find { it.unitId == e.assignedUnitId }?.let { v -> chief.stations.find { it.id == v.stationId } }
        val officer = chiefOfficer(e); e.officer = officer
        officer.energy = (officer.energy + (station?.lounge ?: 0) * 5).coerceAtMost(100)
    }
    chief.insolvencyWarning = if (!state.unlimitedBudget && state.budget < costs.total * 3) {
        if (state.budget < 0) "Insolvenz-Warnung: Budget ${state.budget} €. Kosten reduzieren oder Fahrzeuge verkaufen. Die Simulation läuft weiter."
        else "Liquiditätswarnung: Budget reicht für weniger als drei Tage eigener laufender Kosten."
    } else ""
    if (costs.total > 0) chiefLog("Tageskosten ${costs.total} €: Gehälter ${costs.wages}, Wartung ${costs.maintenance}, Treibstoff ${costs.fuel}, Gebäude ${costs.buildings}.")
    if (chief.insolvencyWarning.isNotBlank()) chiefLog(chief.insolvencyWarning)
    return costs
}
