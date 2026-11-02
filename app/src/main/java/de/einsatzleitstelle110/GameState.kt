package de.einsatzleitstelle110

import java.util.Random
import kotlin.math.*

/** Persistent native-only management model. Weapons have abstract readiness values only. */
data class Officer(
    var name: String,
    var skill: Int = 58,
    var energy: Int = 100,
    var experience: Int = 0,
    var portrait: Int = 1,
    var equipment: MutableSet<String> = mutableSetOf("P99", "Funkgerät", "Schutzweste"),
    var owned: MutableSet<String> = equipment.toMutableSet(),
    var qualifications: MutableSet<String> = mutableSetOf(),
    var dutyMinutes: Int = 0,
    var restMinutes: Int = 960,
    var photo: String = "",
)

data class UnitState(
    val id: String,
    var callsign: String,
    var type: String,
    var region: String,
    var lat: Double,
    var lon: Double,
    var crew: Int = 2,
    var level: Int = 1,
    var experience: Int = 0,
    var officer: Officer = Officer("P. Müller"),
    var equipment: MutableSet<String> = mutableSetOf("P99", "Funkgerät", "Schutzweste"),
    var status: String = "FREE",
    var incidentId: String = "",
    var startLat: Double = lat,
    var startLon: Double = lon,
    var destLat: Double = lat,
    var destLon: Double = lon,
    var travelTotal: Int = 0,
    var travelLeft: Int = 0,
    var workLeft: Int = 0,
    var training: String = "",
    var trainingLeft: Int = 0,
    var officers: MutableList<Officer> = mutableListOf(officer),
    var reliefTeams: MutableList<MutableList<Officer>> = mutableListOf(),
    var rosterIndex: Int = 0,
    var dutyMinutes: Int = 0,
    var restUntil: Long = 0,
    var homeLat: Double = lat,
    var homeLon: Double = lon,
    var vehicleCondition: Int = 100,
    var vehicleSpeed: Double = 62.0,
    var vehicleName: String = "Dienstfahrzeug",
    var readinessLeft: Int = 0,
    var onDuty: Boolean = true,
    var scope: String = "STATE",
    var routePoints: MutableList<GeoPoint> = mutableListOf(),
    var routeBasis: String = "DISTANCE_APPROXIMATION",
)

data class IncidentState(
    val id: String,
    var title: String,
    var location: String,
    var region: String,
    var lat: Double,
    var lon: Double,
    var priority: Int,
    var required: String,
    var duration: Int,
    var description: String,
    var status: String = "OPEN",
    var age: Int = 0,
    var assignedUnit: String = "",
    var requiredTypes: MutableList<String> = mutableListOf(required),
    var assignedUnits: MutableList<String> = mutableListOf(),
    var deadline: Int = 180,
    var followUp: String = "",
    var chain: MutableList<ChainStage> = mutableListOf(),
    var stageId: String = "",
    var followSpawned: Boolean = false,
    var decision: String = "",
    var riskDelta: Double = 0.0,
    var handoffStatus: String = "",
    var scope: String = "STATE",
    var reward: Int = 1100,
    var followUpType: String = "KRIPO",
    /** Explanation of the result shown in the call file and the shift report. */
    var outcome: String = "",
    /** Call age when the first unit reached the scene (-1 = nobody yet). */
    var firstArrival: Int = -1,
    /** Absolute simulation minute when the call was solved or failed (-1 = still open). */
    var closedAt: Long = -1,
)

data class ChainStage(
    val id: String,
    val title: String,
    val required: String,
    val duration: Int,
    val priority: Int = 2,
    val onSuccess: String = "",
    val onFailure: String = "",
    val requiredTypes: List<String> = listOf(required),
)

data class IncidentTemplate(
    val title: String,
    val priority: Int,
    val required: String,
    val duration: Int,
    val description: String,
    val requiredTypes: List<String> = listOf(required),
    val deadline: Int = 180,
    val reward: Int = 1100,
    val followUp: String = "",
    val chain: List<ChainStage> = emptyList(),
    val region: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val followUpType: String = "KRIPO",
)

data class ShiftReport(
    val day: Int,
    val region: String,
    val duration: Int,
    val served: Int,
    val solved: Int,
    val failed: Int,
    val handed: Int,
    val income: Int,
    val expenses: Int,
    val repDelta: Int,
    val elapsed: Int = duration,
    val startAbsolute: Long = 0,
    val endAbsolute: Long = 0,
    val rotations: Int = 0,
    val wages: Int = 0,
    val maintenance: Int = 0,
    val specialDispatches: Int = 0,
    val trainingDone: List<String> = emptyList(),
    val fatigue: Int = 0,
)

data class GameState(
    var region: String = "DE-HH",
    var mode: String = "CAREER",
    var day: Int = 1,
    var phase: String = "PLANNING",
    var startMinute: Int = 360,
    var duration: Int = 480,
    var elapsed: Int = 0,
    var speed: Int = 4,
    var paused: Boolean = false,
    var budget: Int = 240000,
    var reputation: Int = 74,
    var shiftIncoming: Int = 0,
    var shiftSolved: Int = 0,
    var shiftFailed: Int = 0,
    var shiftHanded: Int = 0,
    var shiftIncome: Int = 0,
    var nextId: Int = 1,
    var autoCalls: Boolean = true,
    var mute: Boolean = false,
    val units: MutableList<UnitState> = mutableListOf(),
    val incidents: MutableList<IncidentState> = mutableListOf(),
    val customTemplates: MutableList<IncidentTemplate> = mutableListOf(),
    val reports: MutableList<ShiftReport> = mutableListOf(),
    var scope: String = "STATE",
    var absoluteMinute: Long = 0,
    var shiftStartAbsolute: Long = 360,
    var completedShifts: Int = 0,
    var totalHours: Double = 0.0,
    var callInterval: Int = 30,
    var fatigueEnabled: Boolean = true,
    var unlimitedBudget: Boolean = false,
    var customEnabled: Boolean = true,
    var shiftRotations: Int = 0,
    var shiftSpecial: Int = 0,
    var masterVolume: Float = .7f,
    var radioVolume: Float = .7f,
    var sfxVolume: Float = .7f,
    var ambientVolume: Float = 0f,
    var randomSeed: Long = 982341L,
    var legacySource: String = "",
    val logs: MutableList<String> = mutableListOf(),
)

object GameContent {
    val equipment =
        listOf(
            "P99",
            "MP5",
            "G36",
            "Elektroimpulsgerät",
            "Einsatzgürtel",
            "Schutzhandschuhe",
            "Funkgerät",
            "Schutzweste",
            "Bodycam",
            "Erste-Hilfe",
            "Taschenlampe",
            "Schutzhelm",
            "Drohne",
            "Schutzschild",
            "Fahrzeugkamera",
            "Funk-Repeater",
        )
    val prices =
        mapOf(
            "P99" to 0,
            "MP5" to 1600,
            "Elektroimpulsgerät" to 1100,
            "Einsatzgürtel" to 240,
            "Schutzhandschuhe" to 85,
            "G36" to 2400,
            "Funkgerät" to 400,
            "Schutzweste" to 900,
            "Bodycam" to 600,
            "Erste-Hilfe" to 250,
            "Taschenlampe" to 90,
            "Schutzhelm" to 950,
            "Drohne" to 3400,
            "Schutzschild" to 1750,
            "Fahrzeugkamera" to 900,
            "Funk-Repeater" to 1250,
        )

    fun equipmentBonus(item: String): Int =
        when (item) {
            "P99" -> 0
            "Schutzweste",
            "Schutzhelm",
            "Schutzschild" -> 5
            "MP5",
            "Erste-Hilfe",
            "Fahrzeugkamera" -> 3
            "G36" -> 4
            "Drohne" -> 7
            "Schutzhandschuhe",
            "Einsatzgürtel" -> 1
            else -> 2
        }

    val courses =
        linkedMapOf(
            "Deeskalation" to Pair(2, 1600),
            "Erste Hilfe" to Pair(2, 1400),
            "Fahndung" to Pair(3, 2800),
            "Kommunikation" to Pair(2, 1200),
            "Verkehrsdienst" to Pair(2, 1750),
            "SEK-Fortbildung" to Pair(4, 12000),
            "MEK-Observation" to Pair(4, 9400),
            "Einsatzführung" to Pair(3, 4800),
            "Lageführung" to Pair(2, 1800),
            "Vermisstensuche" to Pair(3, 2900),
        )
    val events =
        listOf(
                "Ruhestörung|3|STREIFE|10|Lärmbeschwerde aus Wohngebiet",
                "Verkehrsunfall|2|STREIFE|22|Unfallmeldung auf einer Hauptstraße",
                "Einbruchalarm|2|STREIFE|18|Alarmauslösung in einem Geschäftsgebäude",
                "Ladendiebstahl|3|STREIFE|12|Warenhaus meldet verdächtige Person",
                "Vermisste Person|2|STREIFE|25|Suchmeldung nach einer vermissten Person",
                "Streit in Gaststätte|2|STREIFE|16|Mehrere Personen geraten in Streit",
                "Falschparker|3|STREIFE|8|Zufahrt blockiert",
                "Fahrraddiebstahl|3|STREIFE|14|Diebstahl am Bahnhof",
                "Verdächtiges Fahrzeug|2|STREIFE|13|Anwohner melden verdächtiges Fahrzeug",
                "Betrugsverdacht|2|KRIPO|28|Hinweis auf organisierten Betrug",
                "Einbruchserie|2|KRIPO|35|Ermittlungsmaßnahmen zu Tatserie",
                "Tatortaufnahme|2|KRIPO|30|Spurensicherung erforderlich",
                "Geldwäschehinweis|2|KRIPO|42|Ermittlungsansatz eingegangen",
                "Cybercrime-Anzeige|3|KRIPO|34|Onlinebetrugsanzeige bearbeiten",
                "Gefährdungslage|1|SEK|38|Spezialkräfte zur Absicherung angefordert",
                "Geisellage|1|SEK|50|Schwere Gefahrenlage, spezialisierte Einheit notwendig",
                "Bewaffnete Bedrohung|1|SEK|44|Meldung über Bedrohung mit einer Waffe",
                "Zugriffsbereitschaft|1|SEK|37|Spezialeinheit in Bereitschaft",
                "Observation|2|MEK|45|Mobile Observation angeordnet",
                "Zielperson verfolgen|2|MEK|42|Verdeckte Beobachtung im Stadtgebiet",
                "Verdeckte Ermittlung|2|MEK|48|Beobachtungsauftrag",
                "Fahndungslage|2|MEK|32|Mobile Einsatzkräfte angefordert",
                "Brandmeldung|1|FEUERWEHR|29|Rauchentwicklung gemeldet",
                "Kellerbrand|1|FEUERWEHR|35|Brandalarm im Kellerbereich",
                "Verkehrsunfall Verletzte|1|RETTUNG|25|Notruf wegen verletzter Person",
                "Medizinischer Notfall|1|RETTUNG|22|Rettungsdienst alarmiert",
                "Häuslicher Streit|2|STREIFE|18|Lautstarker Streit gemeldet",
                "Schlägerei|1|STREIFE|20|Mehrere Beteiligte an Rangelei",
                "Sachbeschädigung|3|STREIFE|15|Beschädigung an Fahrzeug gemeldet",
                "Diebstahl auf Markt|3|STREIFE|18|Verkäufer meldet Diebstahl",
                "Unbefugter Zutritt|2|STREIFE|16|Störung in einem Gebäude",
                "Demonstration|2|STREIFE|32|Versammlungslage überwachen",
                "Fundsache|3|STREIFE|10|Wertgegenstand gefunden",
                "Gewässerverunreinigung|2|STREIFE|30|Hinweis aus Hafenbereich",
                "Banküberfall|1|SEK|46|Schwerer Raubverdacht",
                "Gefahrstoffaustritt|1|FEUERWEHR|43|Gefahrstoffmeldung",
                "Rettung aus Wasser|1|RETTUNG|31|Person im Wasser gemeldet",
                "Ladungsdiebstahl|2|KRIPO|37|Ermittlungsgruppe angefordert",
                "Schwerer Autodiebstahl|2|KRIPO|35|Fahndung und Ermittlungen",
                "Fahndung nach Ausreißer|2|STREIFE|23|Suchauftrag im Wohngebiet",
                "Verkehrskontrolle|3|STREIFE|16|Kontrollstelle einrichten",
                "Überwachung Sonderlage|2|MEK|42|Mobiles Team benötigt",
                "Person auf Brücke|1|STREIFE|25|Notruf meldet Person in Gefahr",
                "Unbekanntes Paket|2|STREIFE|20|Verdächtiger Gegenstand",
                "Einbruch in Gewerbepark|2|STREIFE|17|Sicherheitsdienst meldet Alarm",
                "Explosion gemeldet|1|FEUERWEHR|41|Mehrere Notrufe über lauten Knall",
                "Körperverletzung|2|STREIFE|20|Verletzung nach Auseinandersetzung",
                "Zeugenvernehmung|3|KRIPO|34|Ermittlungsauftrag",
            )
            .map {
                val p = it.split('|')
                IncidentTemplate(p[0], p[1].toInt(), p[2], p[3].toInt(), p[4])
            }
}

class GameController(val state: GameState, val regions: List<GeoRegion>) {
    var notify: ((String, String) -> Unit)? = null

    /** Real towns of a state (population-weighted call locations). Set by the UI; tests use cities. */
    var placeProvider: (GeoRegion) -> List<MapPlace> = { r ->
        r.cities.map { MapPlace(it.name, "city", 80000, it.lon, it.lat) }
    }

    private fun rnd(): Double {
        state.randomSeed = (state.randomSeed * 1664525L + 1013904223L) and 0xffffffffL
        return state.randomSeed.toDouble() / 4294967296.0
    }

    private val rng =
        object : Random() {
            override fun nextDouble() = rnd()

            override fun nextInt(bound: Int) = (rnd() * bound).toInt().coerceIn(0, bound - 1)
        }

    fun region() = regions.first { it.code == state.region }

    fun inScope(p: GeoPoint) = OperationsAreas.contains(state.region, state.scope, p)

    fun inScope(lat: Double, lon: Double) = inScope(GeoPoint(lon, lat))

    fun available(u: UnitState): Boolean =
        u.status == "FREE" &&
            u.onDuty &&
            u.vehicleCondition >= 20 &&
            state.absoluteMinute >= u.restUntil &&
            (!state.fatigueEnabled || u.officers.all { it.energy >= 15 })

    /** Free units, plus units on their way back to the station (911-style re-dispatch). */
    fun dispatchable(u: UnitState): Boolean =
        available(u) ||
            (u.status == "RETURNING" &&
                u.incidentId.isBlank() &&
                u.onDuty &&
                u.vehicleCondition >= 20 &&
                (!state.fatigueEnabled || u.officers.all { it.energy >= 15 }))

    fun readiness(u: UnitState): Int =
        if (u.status == "RETURNING") 0
        else
            when (u.type) {
                "SEK" -> 12
                "MEK" -> 9
                "HELI" -> 6
                else -> 2
            }

    /** Total minutes until a unit could be on scene: readiness plus routed travel time. */
    fun eta(u: UnitState, call: IncidentState): Int =
        readiness(u) + NativeRouting.eta(u, GeoPoint(call.lon, call.lat))

    /** Units that may be sent to [call], best first (fitting role, then ETA). */
    fun candidates(call: IncidentState): List<Pair<UnitState, Int>> {
        val need = remainingTypes(call)
        return state.units
            .filter { it.region == call.region && dispatchable(it) && it.id !in call.assignedUnits }
            .map { it to eta(it, call) }
            .sortedWith(
                compareBy<Pair<UnitState, Int>>(
                    { (u, _) ->
                        when {
                            need.contains(u.type) -> 0
                            need.any { canHandle(u, it) } -> 1
                            else -> 2
                        }
                    },
                    { it.second },
                )
            )
    }

    fun freeUnits() =
        state.units.count {
            it.region == state.region && available(it) && inScope(GeoPoint(it.lon, it.lat))
        }

    fun openIncidents() =
        state.incidents.count {
            it.region == state.region &&
                inScope(it.lat, it.lon) &&
                it.status in listOf("OPEN", "DRIVING", "WORKING")
        }

    fun allTemplates(): List<IncidentTemplate> =
        (NativeCatalog.events + (if (state.customEnabled) state.customTemplates else emptyList()))
            .filter {
                it.region.isBlank() ||
                    it.region == state.region ||
                    "DE-${it.region}" == state.region
            }

    fun canHandle(u: UnitState, required: String) =
        u.type == required ||
            (required == "STREIFE" &&
                u.type in listOf("SEK", "MEK", "KRIPO", "BEREITSCHAFT", "HUND", "VERKEHR"))

    private fun police(type: String) = type !in listOf("RETTUNG", "FEUERWEHR")

    fun ensureRoster(u: UnitState) {
        if (u.officers.isEmpty()) u.officers.add(u.officer)
        while (u.officers.size < u.crew) u.officers.add(
            Officer(
                "${listOf("N. Weber","A. König","T. Scholz","M. Yilmaz","J. Fischer","L. Brandt")[u.officers.size%6]} ${u.callsign}",
                portrait = (u.officer.portrait + u.officers.size - 1) % 8 + 1,
            )
        )
        for (o in (u.officers + u.reliefTeams.flatten())) if (police(u.type)) {
            o.owned.add("P99")
            o.equipment.add("P99")
        } else {
            o.owned.remove("P99")
            o.equipment.remove("P99")
        }
        if (police(u.type)) u.equipment.add("P99") else u.equipment.remove("P99")
        u.officer = u.officers.first()
    }

    fun seedUnitsIfNeeded() {
        state.units.forEach { ensureRoster(it) }
        val r = region()
        if (state.units.any { it.region == r.code && it.scope == state.scope }) return
        val types =
            listOf(
                "STREIFE",
                "STREIFE",
                "KRIPO",
                "STREIFE",
                "MEK",
                "STREIFE",
                "SEK",
                "STREIFE",
                "HUND",
                "BEREITSCHAFT",
                "VERKEHR",
                "HELI",
                "RETTUNG",
                "FEUERWEHR",
            )
        // Fictional game stations at the largest real towns of the jurisdiction; specialist
        // forces (SEK, MEK, helicopter) are based centrally.
        val sites =
            placeProvider(r)
                .filter { r.contains(GeoPoint(it.lon, it.lat)) && inScope(GeoPoint(it.lon, it.lat)) }
                .sortedByDescending { it.population }
                .take(if (state.scope == "STATE") 10 else 3)
                .map { GeoPoint(it.lon, it.lat) }
        repeat(if (state.scope == "STATE") 32 else 8) { i ->
            val t =
                if (state.scope != "STATE")
                    listOf(
                        "STREIFE",
                        "STREIFE",
                        "STREIFE",
                        "STREIFE",
                        "SEK",
                        "MEK",
                        "KRIPO",
                        "VERKEHR",
                    )[i]
                else types[i % types.size]
            val city = r.cities[i % r.cities.size]
            val p =
                if (sites.isNotEmpty())
                    if (t in listOf("SEK", "MEK", "HELI")) sites[0] else sites[i % sites.size]
                else if (state.scope != "STATE") OperationsAreas.sample(r, state.scope, rng)
                else if (i < r.cities.size) GeoPoint(city.lon, city.lat) else sampleState(r)
            val u =
                UnitState(
                    r.code + "-" + state.scope + "-" + (i + 1),
                    "${if(t=="STREIFE")"Peter" else t} ${r.code.takeLast(2)} ${i+1}",
                    t,
                    r.code,
                    p.lat,
                    p.lon,
                    crew = if (t == "SEK") 4 else 2,
                    officer =
                        Officer(
                            "${listOf("L. Becker","N. Krüger","T. Hansen","S. Fischer","A. Lange","K. Schmidt")[i%6]} ${i+1}",
                            55 + i % 30,
                            portrait = i % 8 + 1,
                        ),
                    scope = state.scope,
                )
            ensureRoster(u)
            repeat(2) { team ->
                u.reliefTeams.add(
                    u.officers
                        .mapIndexed { j, o ->
                            Officer(
                                o.name + " / ${team+2}",
                                o.skill,
                                portrait = (i + j + team) % 8 + 1,
                            )
                        }
                        .toMutableList()
                )
            }
            u.vehicleName =
                when (t) {
                    "SEK" -> "Geschütztes Einsatzfahrzeug"
                    "MEK" -> "Zivilfahrzeug"
                    "HELI" -> "Polizeihubschrauber"
                    else -> "Dienstwagen"
                }
            u.vehicleSpeed = if (t == "HELI") 180.0 else if (t == "SEK") 58.0 else 62.0
            ensureRoster(u)
            state.units.add(u)
        }
    }

    private fun sampleState(r: GeoRegion): GeoPoint {
        val b = r.bounds
        repeat(20000) {
            val p = GeoPoint(b[0] + rnd() * (b[2] - b[0]), b[1] + rnd() * (b[3] - b[1]))
            if (r.contains(p)) return p
        }
        return GeoPoint(r.cities.first().lon, r.cities.first().lat)
    }

    fun setRegion(code: String) {
        if (state.phase == "RUNNING" || regions.none { it.code == code }) return
        state.region = code
        state.scope = "STATE"
        seedUnitsIfNeeded()
    }

    fun setScope(code: String): Boolean {
        if (state.phase != "PLANNING") return false
        if (code !in OperationsAreas.options(state.region).map { it.first }) return false
        state.scope = code
        seedUnitsIfNeeded()
        return true
    }

    fun setShift(startHour: Int, hours: Int) {
        if (state.phase != "PLANNING" || hours !in listOf(8, 12, 24)) return
        state.startMinute = startHour.mod(24) * 60
        state.duration = hours * 60
    }

    fun startShift() {
        if (state.phase != "PLANNING") return
        seedUnitsIfNeeded()
        var start = state.absoluteMinute / 1440 * 1440 + state.startMinute
        if (start < state.absoluteMinute) start += 1440
        val rest = (start - state.absoluteMinute).toInt()
        state.shiftStartAbsolute = start
        state.absoluteMinute = start
        state.phase = "RUNNING"
        state.elapsed = 0
        state.paused = false
        state.shiftRotations = 0
        state.shiftSpecial = 0
        state.shiftIncoming = 0
        state.shiftSolved = 0
        state.shiftFailed = 0
        state.shiftHanded = 0
        state.shiftIncome = 0
        state.units.forEach { u ->
            (u.officers + u.reliefTeams.flatten()).forEach { o ->
                o.energy = (o.energy + rest / 10).coerceAtMost(100)
                o.restMinutes += rest
                if (o.restMinutes >= 480) o.dutyMinutes = 0
            }
        }
        state.incidents
            .filter {
                it.region == state.region && inScope(it.lat, it.lon) && it.status == "HANDED"
            }
            .forEach {
                it.status = it.handoffStatus.ifBlank { "OPEN" }
                it.handoffStatus = ""
            }
        // Varied but fair opener: a single-patrol call fitting the time of day, never a P1 at minute 0.
        newIncident(pickTemplate(opener = true))
        emit("Schicht begonnen", "Neuer Notruf eingegangen")
    }

    /** Hour of day of the simulation (0–23). */
    fun hour(): Int = ((state.absoluteMinute % 1440) / 60).toInt()

    /** Relative call volume by hour: quiet early morning, rush hours, busy evenings. */
    fun hourFactor(h: Int = hour()): Double =
        doubleArrayOf(
            .75, .62, .5, .42, .38, .45, .7, .95, 1.1, 1.0, .95, 1.0,
            1.05, 1.0, 1.0, 1.1, 1.25, 1.3, 1.25, 1.2, 1.15, 1.1, 1.0, .9,
        )[h.mod(24)]

    /** Time-of-day weight of a scenario, so a night shift does not feel like a lunch shift. */
    fun templateWeight(t: IncidentTemplate, h: Int = hour()): Double {
        val title = t.title.lowercase()
        val night = h < 6 || h >= 22
        val rush = h in 7..9 || h in 16..19
        var w = 1.0
        if (title.contains("verkehr") || title.contains("unfall") || title.contains("falschpark"))
            w *= if (rush) 2.2 else if (night) .5 else 1.0
        if (title.contains("ruhestörung") || title.contains("gaststätte") || title.contains("schlägerei"))
            w *= if (night) 2.4 else .6
        if (title.contains("einbruch")) w *= if (night) 2.0 else .8
        if (title.contains("laden") || title.contains("markt") || title.contains("demonstration") || title.contains("veranstaltung"))
            w *= if (h in 9..19) 1.8 else .25
        if (t.priority == 1) w *= .55
        return w
    }

    fun pickTemplate(h: Int = hour(), opener: Boolean = false): IncidentTemplate {
        val all =
            allTemplates()
                .filter {
                    !opener ||
                        (it.priority >= 2 &&
                            it.requiredTypes.size == 1 &&
                            it.requiredTypes[0] == "STREIFE" &&
                            it.chain.isEmpty())
                }
                .ifEmpty { allTemplates() }
        val weights = all.map { templateWeight(it, h) }
        var x = rnd() * weights.sum()
        for (i in all.indices) {
            x -= weights[i]
            if (x <= 0) return all[i]
        }
        return all.last()
    }

    /** Population-weighted call position near a real town inside the jurisdiction. */
    fun samplePosition(r: GeoRegion = region()): GeoPoint {
        val places = placeProvider(r).filter { state.scope == "STATE" || inScope(GeoPoint(it.lon, it.lat)) }
        if (places.isNotEmpty() && rnd() < .88) {
            val weights = places.map { it.population.coerceAtLeast(1000).toDouble().pow(.8) }
            repeat(30) {
                var x = rnd() * weights.sum()
                var pick = places.last()
                for (i in places.indices) {
                    x -= weights[i]
                    if (x <= 0) {
                        pick = places[i]
                        break
                    }
                }
                val spread =
                    when {
                        pick.population > 500000 -> .06
                        pick.population > 100000 -> .035
                        pick.kind == "suburb" -> .012
                        else -> .018
                    }
                val a = rnd() * 2 * PI
                val d = sqrt(rnd()) * spread
                val p = GeoPoint(pick.lon + cos(a) * d * 1.6, pick.lat + sin(a) * d)
                if (r.contains(p) && inScope(p)) return p
            }
        }
        return OperationsAreas.sample(r, state.scope, rng)
    }

    fun locationName(r: GeoRegion, p: GeoPoint): String {
        val places = placeProvider(r)
        val near = places.minByOrNull { GeoData.km(GeoPoint(it.lon, it.lat), p) }
        if (near != null) return near.name
        return r.cities.minByOrNull { GeoData.km(GeoPoint(it.lon, it.lat), p) }?.name ?: r.name
    }

    fun newIncident(
        template: IncidentTemplate = pickTemplate(),
        position: GeoPoint? = null,
    ): IncidentState {
        val r = region()
        val p =
            position
                ?: if (template.lat != null && template.lon != null)
                    GeoPoint(template.lon, template.lat)
                else samplePosition(r)
        require(r.contains(p) && inScope(p)) { "Einsatzort außerhalb des Zuständigkeitsgebiets" }
        val cityName = locationName(r, p)
        val first = template.chain.firstOrNull()
        val call =
            IncidentState(
                "E-${state.nextId++}",
                first?.title ?: template.title,
                cityName,
                r.code,
                p.lat,
                p.lon,
                first?.priority ?: template.priority,
                first?.required ?: template.required,
                first?.duration ?: template.duration,
                template.description,
                requiredTypes =
                    (if (first != null) first.requiredTypes else template.requiredTypes)
                        .toMutableList(),
                deadline = template.deadline,
                followUp = template.followUp,
                chain = template.chain.toMutableList(),
                stageId = first?.id ?: "",
                scope = state.scope,
                reward = template.reward,
                followUpType = template.followUpType,
            )
        state.incidents.add(0, call)
        state.shiftIncoming++
        emit("Neuer Notruf P${call.priority}", call.title)
        return call
    }

    fun remainingTypes(call: IncidentState): List<String> {
        val left = call.requiredTypes.toMutableList()
        for (id in call.assignedUnits) {
            val u = state.units.find { it.id == id } ?: continue
            val exact = left.indexOf(u.type)
            val index = if (exact >= 0) exact else left.indexOfFirst { canHandle(u, it) }
            if (index >= 0) left.removeAt(index)
        }
        return left
    }

    fun dispatch(call: IncidentState, u: UnitState): Boolean {
        if (
            state.phase != "RUNNING" ||
                call.status !in listOf("OPEN", "DRIVING", "WORKING") ||
                !dispatchable(u) ||
                u.id in call.assignedUnits ||
                u.region != call.region ||
                u.vehicleCondition < 20 ||
                call.requiredTypes.none { canHandle(u, it) } ||
                // Once all roles are covered, up to three extra units may reinforce the scene.
                (remainingTypes(call).none { canHandle(u, it) } &&
                    call.assignedUnits.size >= call.requiredTypes.size + 3)
        )
            return false
        ensureRoster(u)
        if (state.fatigueEnabled && u.officers.any { it.energy < 15 }) return false
        val readiness = readiness(u)
        val route = NativeRouting.routeFor(u, GeoPoint(call.lon, call.lat))
        val eta = route.durationMinutes
        u.routePoints = route.points.toMutableList()
        u.routeBasis = route.basis
        if (call.status == "OPEN") call.status = "DRIVING"
        call.assignedUnit = u.id
        call.assignedUnits.add(u.id)
        u.status = "DRIVING"
        u.incidentId = call.id
        u.startLat = u.lat
        u.startLon = u.lon
        u.destLat = call.lat
        u.destLon = call.lon
        u.travelTotal = eta
        u.travelLeft = eta
        u.workLeft = call.duration
        u.readinessLeft = readiness
        if (u.type in listOf("SEK", "MEK")) state.shiftSpecial++
        emit("Einheit unterwegs", "${u.callsign} · ${eta+u.readinessLeft} Sim.-Min.")
        return true
    }

    /** Send several units in one radio order. Returns how many actually departed. */
    fun dispatchAll(call: IncidentState, units: List<UnitState>): Int = units.count { dispatch(call, it) }

    /**
     * Withdraw a unit from its incident; it drives back to its station and is immediately
     * re-dispatchable. A call left without forces goes back to the open queue.
     */
    fun recall(u: UnitState): Boolean {
        if (state.phase != "RUNNING" || u.incidentId.isBlank()) return false
        val call = state.incidents.find { it.id == u.incidentId } ?: return false
        if (call.status !in listOf("DRIVING", "WORKING")) return false
        call.assignedUnits.remove(u.id)
        if (call.assignedUnit == u.id) call.assignedUnit = call.assignedUnits.lastOrNull() ?: ""
        sendHome(u)
        val remaining = call.assignedUnits.mapNotNull { id -> state.units.find { it.id == id } }
        if (remaining.isEmpty()) {
            call.status = "OPEN"
        } else if (call.status == "WORKING" && remainingTypes(call).isNotEmpty()) {
            // Without the required forces the work pauses until a replacement arrives.
            call.status = "DRIVING"
            remaining.forEach { if (it.status == "WORKING") it.status = "ON_SCENE" }
        }
        emit("Einheit abgezogen", "${u.callsign} verlässt ${call.title}")
        return true
    }

    /** Re-route a bound unit directly to another, more urgent call. */
    fun redirect(u: UnitState, call: IncidentState): Boolean {
        if (u.incidentId == call.id) return false
        if (u.incidentId.isNotBlank() && !recall(u)) return false
        return dispatch(call, u)
    }

    private fun sendHome(u: UnitState) {
        u.incidentId = ""
        u.startLat = u.lat
        u.startLon = u.lon
        u.destLat = u.homeLat
        u.destLon = u.homeLon
        u.readinessLeft = 0
        val route = NativeRouting.routeFor(u, GeoPoint(u.homeLon, u.homeLat))
        u.routePoints = route.points.toMutableList()
        u.routeBasis = route.basis
        u.travelTotal = route.durationMinutes
        u.travelLeft = u.travelTotal
        u.status = if (u.travelTotal <= 0 || GeoData.km(GeoPoint(u.lon, u.lat), GeoPoint(u.homeLon, u.homeLat)) < .05) "FREE" else "RETURNING"
    }

    private fun rotate(u: UnitState) {
        if (u.status in listOf("TRAINING", "REST") || !u.onDuty || state.duration <= 480) return
        if (u.officers.none { it.dutyMinutes >= 480 }) return
        val next =
            u.reliefTeams.indices
                .filter { u.reliefTeams[it].all { o -> o.restMinutes >= 480 && o.energy >= 50 } }
                .maxByOrNull { u.reliefTeams[it].minOf { o -> o.restMinutes } }
        if (next != null) {
            val previous = u.officers
            u.officers = u.reliefTeams[next]
            u.reliefTeams[next] = previous
            u.officer = u.officers.first()
            u.officers.forEach {
                it.dutyMinutes = 0
                it.restMinutes = 0
            }
            u.rosterIndex++
            state.shiftRotations++
        } else if (u.incidentId.isEmpty()) {
            u.status = "REST"
            u.restUntil = state.absoluteMinute + 240
        }
    }

    private fun move(u: UnitState) {
        if (u.readinessLeft > 0) {
            u.readinessLeft--
            return
        }
        u.travelLeft = (u.travelLeft - 1).coerceAtLeast(0)
        val t = if (u.travelTotal <= 0) 1.0 else 1.0 - u.travelLeft.toDouble() / u.travelTotal
        val pos =
            NativeRouting.position(
                u.routePoints.ifEmpty {
                    listOf(GeoPoint(u.startLon, u.startLat), GeoPoint(u.destLon, u.destLat))
                },
                t,
            )
        u.lat = pos.lat
        u.lon = pos.lon
        if (u.travelLeft % 15 == 0) u.vehicleCondition = (u.vehicleCondition - 1).coerceAtLeast(0)
        if (u.travelLeft == 0) {
            if (u.status == "RETURNING") {
                u.status = "FREE"
                u.lat = u.homeLat
                u.lon = u.homeLon
            } else {
                u.status = "ON_SCENE"
                val call = state.incidents.find { it.id == u.incidentId }
                if (call != null) {
                    if (call.firstArrival < 0) call.firstArrival = call.age
                    emit("Am Einsatzort", "${u.callsign} · ${call.title}")
                }
            }
        }
    }

    private fun release(call: IncidentState) {
        for (id in call.assignedUnits) {
            val u = state.units.find { it.id == id } ?: continue
            if (u.incidentId != call.id) continue
            sendHome(u)
        }
    }

    private fun branch(call: IncidentState, success: Boolean) {
        if (call.followSpawned) return
        call.followSpawned = true
        val current = call.chain.find { it.id == call.stageId }
        val nextId = if (success) current?.onSuccess else current?.onFailure
        val next = call.chain.find { it.id == nextId }
        if (next != null) {
            val t =
                IncidentTemplate(
                    next.title,
                    next.priority,
                    next.required,
                    next.duration,
                    "Folgeereignis von ${call.title}",
                    requiredTypes = next.requiredTypes,
                    chain = emptyList(),
                )
            val follow = newIncident(t, GeoPoint(call.lon, call.lat))
            follow.chain = call.chain
            follow.stageId = next.id
        } else if (success && call.followUp.isNotBlank())
            newIncident(
                IncidentTemplate(
                    call.followUp,
                    2,
                    call.followUpType,
                    20,
                    "Folgeereignis von ${call.title}",
                ),
                GeoPoint(call.lon, call.lat),
            )
    }

    fun decide(call: IncidentState, choice: String): Boolean {
        if (call.decision.isNotEmpty() || call.status !in listOf("OPEN", "DRIVING", "WORKING"))
            return false
        val cost = if (choice == "SUPPORT") 180 else 0
        if (!spend(cost)) return false
        call.decision = choice
        call.riskDelta = -.1
        call.duration += if (choice == "SUPPORT") 3 else 10
        state.units
            .filter { it.incidentId == call.id }
            .forEach { it.workLeft += if (choice == "SUPPORT") 3 else 10 }
        return true
    }

    /**
     * Explainable success chance: response time against the deadline, fitting forces, training
     * and equipment, fatigue, reinforcement, danger and management decisions. Chance remains only
     * a small uncertainty on top of these factors.
     */
    fun assess(call: IncidentState, units: List<UnitState>): Pair<Double, List<String>> {
        val reasons = mutableListOf<String>()
        var p = .86
        val arrival = if (call.firstArrival >= 0) call.firstArrival else call.age
        val ratio = arrival.toDouble() / call.deadline.coerceAtLeast(1)
        when {
            ratio <= .3 -> {
                p += .08
                reasons.add("schnell vor Ort ($arrival Min)")
            }
            ratio > .75 -> {
                p -= .14
                reasons.add("späte Ankunft ($arrival von ${call.deadline} Min)")
            }
            ratio > .5 -> {
                p -= .05
                reasons.add("Anfahrt lang ($arrival Min)")
            }
        }
        val need = call.requiredTypes.toMutableList()
        var substitutes = 0
        for (u in units) {
            if (need.remove(u.type)) continue
            val i = need.indexOfFirst { canHandle(u, it) }
            if (i >= 0) {
                need.removeAt(i)
                substitutes++
            }
        }
        if (substitutes > 0) {
            p -= .04 * substitutes
            reasons.add("$substitutes× Ersatzkraft statt Fachkraft")
        } else reasons.add("passende Kräfte")
        val extra = units.size - call.requiredTypes.size
        if (extra > 0) {
            p += min(.08, .04 * extra)
            reasons.add("Verstärkung (+$extra)")
        }
        val staff = units.flatMap { it.officers }
        if (staff.isNotEmpty()) {
            val skill = staff.map { it.skill + it.equipment.sumOf { item -> GameContent.equipmentBonus(item) } }.average()
            val delta = ((skill - 66) * .006).coerceIn(-.12, .12)
            p += delta
            if (delta >= .03) reasons.add("erfahrene, gut ausgestattete Besatzung")
            if (delta <= -.03) reasons.add("wenig Erfahrung/Ausrüstung")
            val energy = staff.map { it.energy }.average()
            if (state.fatigueEnabled && energy < 60) {
                p -= (60 - energy) * .004
                reasons.add("erschöpfte Besatzung (${energy.roundToInt()} %)")
            }
        }
        if (call.priority == 1) {
            p -= .06
            reasons.add("hohe Gefahrenlage")
        }
        if (call.riskDelta != 0.0) {
            p -= call.riskDelta
            reasons.add(if (call.decision == "SUPPORT") "Betreuung verstärkt" else "Gespräch verlängert")
        }
        return p.coerceIn(.3, .97) to reasons
    }

    fun advance(minutes: Int) {
        if (state.phase != "RUNNING" || state.paused) return
        for (tick in 0 until minutes.coerceIn(0, 1440)) {
            if (state.phase != "RUNNING") break
            state.elapsed++
            state.absoluteMinute++
            for (u in state.units.filter { it.region == state.region }) {
                if (
                    u.incidentId.isNotBlank() &&
                        state.incidents.any { it.id == u.incidentId && it.status == "HANDED" }
                )
                    continue
                ensureRoster(u)
                if (u.status != "TRAINING") {
                    val duty =
                        u.onDuty &&
                            u.status != "REST" &&
                            (u.scope == state.scope || u.incidentId.isNotBlank())
                    for (o in u.officers) if (duty) {
                        o.dutyMinutes++
                        o.restMinutes = 0
                        if (state.fatigueEnabled && state.elapsed % 20 == 0)
                            o.energy = (o.energy - 1).coerceAtLeast(0)
                    } else {
                        o.restMinutes++
                        if (state.elapsed % 10 == 0) o.energy = (o.energy + 1).coerceAtMost(100)
                    }
                    for (o in u.reliefTeams.flatten()) {
                        o.restMinutes++
                        if (state.elapsed % 10 == 0) o.energy = (o.energy + 1).coerceAtMost(100)
                    }
                    if (u.status == "REST" && state.absoluteMinute >= u.restUntil) {
                        u.status = "FREE"
                        u.officers.forEach { it.dutyMinutes = 0 }
                    }
                    rotate(u)
                }
                if (u.status in listOf("DRIVING", "RETURNING")) move(u)
            }
            for (call in state.incidents.toList().filter { it.region == state.region }) {
                if (call.status in listOf("OPEN", "DRIVING")) {
                    call.age++
                    if (call.age > call.deadline) {
                        call.outcome =
                            if (call.assignedUnits.isEmpty()) "Fehlschlag: Frist abgelaufen, keine Kräfte entsandt"
                            else "Fehlschlag: Frist abgelaufen, benötigte Kräfte nicht rechtzeitig vollständig vor Ort"
                        call.status = "FAILED"
                        call.closedAt = state.absoluteMinute
                        state.shiftFailed++
                        state.reputation = (state.reputation - 2).coerceAtLeast(0)
                        release(call)
                        branch(call, false)
                        continue
                    }
                }
                val assigned =
                    call.assignedUnits.mapNotNull { id -> state.units.find { it.id == id } }
                if (
                    call.status == "DRIVING" &&
                        remainingTypes(call).isEmpty() &&
                        assigned.all { it.status == "ON_SCENE" }
                ) {
                    call.status = "WORKING"
                    assigned.forEach {
                        it.status = "WORKING"
                        it.workLeft = call.duration
                    }
                }
                if (call.status == "WORKING") {
                    val joining = assigned.filter { it.status == "ON_SCENE" }
                    if (joining.isNotEmpty()) {
                        // Reinforcement on scene shortens the remaining work for everybody.
                        val left =
                            assigned.filter { it.status == "WORKING" }.maxOfOrNull { it.workLeft }
                                ?: call.duration
                        val shortened = (left * .75).roundToInt().coerceAtLeast(1)
                        assigned
                            .filter { it.status == "WORKING" || it.status == "ON_SCENE" }
                            .forEach {
                                it.status = "WORKING"
                                it.workLeft = shortened
                            }
                        emit("Verstärkung eingetroffen", call.title)
                    }
                    val working = assigned.filter { it.status == "WORKING" }
                    working.forEach { it.workLeft-- }
                    if (working.isNotEmpty() && working.all { it.workLeft <= 0 }) {
                        val staff = working.flatMap { it.officers }
                        val (probability, reasons) = assess(call, working)
                        val success = rnd() < probability
                        call.outcome =
                            "${if (success) "Erfolg" else "Fehlschlag"} bei ${(probability * 100).roundToInt()} % Chance: " +
                                reasons.joinToString(" · ").ifEmpty { "Standardlage" }
                        call.status = if (success) "SOLVED" else "FAILED"
                        call.closedAt = state.absoluteMinute
                        if (success) {
                            state.shiftSolved++
                            state.reputation = (state.reputation + 1).coerceAtMost(100)
                            val reward = call.reward
                            state.shiftIncome += reward
                            if (limited()) state.budget += reward
                        } else {
                            state.shiftFailed++
                            state.reputation = (state.reputation - 2).coerceAtLeast(0)
                        }
                        staff.forEach {
                            it.experience += if (success) 14 else 5
                            if (it.experience >= 100) {
                                it.experience -= 100
                                it.skill = (it.skill + 1).coerceAtMost(100)
                            }
                        }
                        release(call)
                        branch(call, success)
                        emit(
                            if (success) "Einsatz beendet" else "Einsatz fehlgeschlagen",
                            "${call.title} – ${call.outcome}",
                        )
                    }
                }
            }
            if (
                state.autoCalls &&
                    openIncidents() < 30 &&
                    state.elapsed < state.duration - 25 &&
                    rnd() < hourFactor() / state.callInterval.coerceIn(10, 240)
            )
                newIncident()
            if (state.elapsed >= state.duration) finishShift()
        }
    }

    fun finishShift() {
        if (state.phase != "RUNNING" || state.elapsed < 30) return
        val active =
            state.incidents.filter {
                it.region == state.region &&
                    inScope(it.lat, it.lon) &&
                    it.status in listOf("OPEN", "DRIVING", "WORKING")
            }
        active.forEach {
            it.handoffStatus = it.status
            it.status = "HANDED"
        }
        state.shiftHanded = active.size
        val duty =
            state.units.filter {
                it.region == state.region &&
                    it.onDuty &&
                    (it.scope == state.scope || it.incidentId.isNotBlank())
            }
        val wages = (duty.sumOf { it.crew } * 22.5 * state.elapsed / 60.0).roundToInt()
        val maintenance = duty.sumOf { (100 - it.vehicleCondition) * 2 }
        val funding = if (state.mode == "CAREER") (9000 * state.elapsed / 480.0).roundToInt() else 0
        if (limited()) state.budget += funding - wages - maintenance
        val done = mutableListOf<String>()
        for (u in state.units.filter { it.region == state.region }) if (u.trainingLeft > 0) {
            u.trainingLeft--
            if (u.trainingLeft == 0) {
                u.status = "FREE"
                u.level++
                (u.officers + u.reliefTeams.flatten()).forEach {
                    it.skill = (it.skill + 8).coerceAtMost(100)
                    it.qualifications.add(u.training)
                }
                done.add("${u.callsign} – ${u.training}")
                u.training = ""
            }
        }
        state.shiftIncome += funding
        state.totalHours += state.elapsed / 60.0
        state.completedShifts++
        val fatigue =
            if (duty.isEmpty()) 0
            else duty.flatMap { it.officers }.map { 100 - it.energy }.average().roundToInt()
        state.reports.add(
            0,
            ShiftReport(
                state.day,
                state.region,
                state.duration,
                state.shiftIncoming,
                state.shiftSolved,
                state.shiftFailed,
                state.shiftHanded,
                state.shiftIncome,
                wages + maintenance,
                0,
                state.elapsed,
                state.shiftStartAbsolute,
                state.absoluteMinute,
                state.shiftRotations,
                wages,
                maintenance,
                state.shiftSpecial,
                done,
                fatigue,
            ),
        )
        state.phase = "DEBRIEF"
        state.paused = true
        emit("Schichtende", "Tag ${state.day} abgeschlossen")
    }

    fun nextDay() {
        if (state.phase != "DEBRIEF") return
        state.day++
        state.phase = "PLANNING"
        state.paused = true
        state.elapsed = 0
    }

    private fun limited() = !state.unlimitedBudget

    private fun spend(cost: Int): Boolean {
        if (cost < 0 || limited() && state.budget < cost) return false
        if (limited()) state.budget -= cost
        return true
    }

    fun train(u: UnitState, course: String): Boolean {
        if (
            state.phase != "PLANNING" ||
                u.trainingLeft > 0 ||
                u.incidentId.isNotBlank() ||
                course !in GameContent.courses
        )
            return false
        if (
            course.startsWith("SEK") && u.type != "SEK" ||
                course.startsWith("MEK") && u.type != "MEK"
        )
            return false
        val cfg = GameContent.courses.getValue(course)
        if (!spend(cfg.second)) return false
        u.training = course
        u.trainingLeft = cfg.first
        u.status = "TRAINING"
        return true
    }

    fun equipOfficer(u: UnitState, index: Int, item: String): Boolean {
        ensureRoster(u)
        val o = u.officers.getOrNull(index) ?: return false
        if (
            u.incidentId.isNotBlank() ||
                item !in GameContent.equipment ||
                item in o.equipment ||
                (!police(u.type) &&
                    item in listOf("P99", "MP5", "HK416", "G36", "Elektroimpulsgerät"))
        )
            return false
        if (item == "P99") {
            o.owned.add(item)
            o.equipment.add(item)
            return true
        }
        if (o.equipment.count { it != "P99" } >= 6) return false
        if (item !in o.owned) {
            if (!spend(GameContent.prices[item] ?: return false)) return false
            o.owned.add(item)
        }
        o.equipment.add(item)
        if (index == 0) u.equipment = o.equipment
        return true
    }

    fun unequipOfficer(u: UnitState, index: Int, item: String): Boolean {
        ensureRoster(u)
        if (item == "P99" || u.incidentId.isNotBlank()) return false
        val o = u.officers.getOrNull(index) ?: return false
        return o.equipment.remove(item)
    }

    fun buyEquipment(u: UnitState, item: String) = equipOfficer(u, 0, item)

    fun maintain(u: UnitState): Boolean {
        if (state.phase != "PLANNING" || u.incidentId.isNotBlank() || u.status == "RETURNING")
            return false
        if (!spend((100 - u.vehicleCondition) * 25)) return false
        u.vehicleCondition = 100
        return true
    }

    fun addUnit(callsign: String, type: String, city: GeoCity): Boolean {
        if (state.phase != "PLANNING" || callsign.isBlank() || type !in TYPES) return false
        if (!inScope(city.lat, city.lon) || !spend(25000)) return false
        val u =
            UnitState(
                "CUSTOM-${state.nextId++}",
                callsign.trim().take(60),
                type,
                state.region,
                city.lat,
                city.lon,
                crew = if (type == "SEK") 4 else 2,
                scope = state.scope,
            )
        ensureRoster(u)
        repeat(2) { k ->
            u.reliefTeams.add(
                u.officers
                    .map { Officer(it.name + " / Ablösung ${k+1}", portrait = it.portrait) }
                    .toMutableList()
            )
        }
        state.units.add(u)
        return true
    }

    fun addTemplate(t: IncidentTemplate): Boolean {
        if (
            t.title.isBlank() ||
                t.duration !in 1..360 ||
                t.priority !in 1..3 ||
                t.requiredTypes.isEmpty() ||
                t.requiredTypes.any { it !in TYPES } ||
                t.chain.size > 20
        )
            return false
        val ids = t.chain.map { it.id }.toSet()
        if (
            ids.size != t.chain.size ||
                t.chain.any {
                    it.required !in TYPES ||
                        it.duration !in 1..360 ||
                        it.onSuccess.isNotBlank() && it.onSuccess !in ids ||
                        it.onFailure.isNotBlank() && it.onFailure !in ids
                }
        )
            return false
        fun visit(id: String, path: Set<String>): Boolean {
            if (id in path) return false
            val n = t.chain.first { it.id == id }
            return listOf(n.onSuccess, n.onFailure)
                .filter { it.isNotBlank() }
                .all { visit(it, path + id) }
        }
        if (t.chain.any { !visit(it.id, emptySet()) }) return false
        state.customTemplates.add(t)
        return true
    }

    private fun emit(title: String, message: String) {
        state.logs.add(0, "[Tag ${state.day} · ${clock()}] $title · $message")
        while (state.logs.size > 250) state.logs.removeAt(state.logs.lastIndex)
        notify?.invoke(title, message)
    }

    fun clock(): String {
        val min =
            if (state.phase == "PLANNING") state.startMinute
            else (state.absoluteMinute % 1440).toInt()
        return "%02d:%02d".format(min / 60, min % 60)
    }

    companion object {
        val TYPES =
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
    }
}
