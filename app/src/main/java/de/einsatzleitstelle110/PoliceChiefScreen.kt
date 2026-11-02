package de.einsatzleitstelle110

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import java.text.NumberFormat
import java.util.Locale

/** Native management screen. Every action goes through the same validated engine API as saves/tests. */
class PoliceChiefScreen(
    private val activity: Activity,
    private val game: GameController,
    private val onChanged: () -> Unit,
    private val onPlaceStation: (String) -> Unit,
) {
    private val ui = UiKit(activity)
    private var dialog: AlertDialog? = null
    private val windows = mutableListOf<AlertDialog>()
    private var section = "Dienststellen"
    private val money = NumberFormat.getIntegerInstance(Locale.GERMANY)
    private fun euro(value: Int) = "${money.format(value)} €"
    private fun notice(value: String) = Toast.makeText(activity, value, Toast.LENGTH_LONG).show()
    private fun failed() = notice(game.lastError.ifBlank { "Aktion im aktuellen Zustand nicht möglich." })
    private fun changed(success: Boolean) {
        if (!success) { failed(); return }
        onChanged()
        show()
    }
    private fun addText(c: LinearLayout, value: String, muted: Boolean = true, large: Boolean = false) {
        c.addView(ui.text(value, if (large) 16f else 12f, if (muted) ui.muted else ui.ink, large).apply {
            setPadding(0, ui.dp(4), 0, ui.dp(5))
        })
    }
    private fun button(c: LinearLayout, label: String, primary: Boolean = false, action: () -> Unit) {
        c.addView(ui.button(label, primary, onClick = action), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = ui.dp(5); bottomMargin = ui.dp(3)
        })
    }
    private fun card(c: LinearLayout): LinearLayout = ui.column().apply {
        setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(12))
        background = ui.bg(ui.panel)
        c.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    }
    private fun field(c: LinearLayout, label: String, value: String = "", numeric: Boolean = false): EditText {
        addText(c, label)
        return EditText(activity).apply {
            setText(value); setTextColor(ui.ink); setHintTextColor(ui.muted); setSingleLine()
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
            c.addView(this, LinearLayout.LayoutParams(-1, ui.dp(48)))
        }
    }
    private fun choice(c: LinearLayout, label: String, values: List<String>, selected: Int = 0): Spinner {
        addText(c, label)
        return Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
            setSelection(selected.coerceIn(0, (values.size - 1).coerceAtLeast(0)))
            c.addView(this, LinearLayout.LayoutParams(-1, ui.dp(48)))
        }
    }
    private fun sheet(title: String, content: LinearLayout, positive: String? = null, action: (() -> Boolean)? = null): AlertDialog {
        val scroll = ScrollView(activity).apply { addView(content); setBackgroundColor(ui.panel) }
        content.setPadding(ui.dp(15), ui.dp(8), ui.dp(15), ui.dp(16))
        val d = AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("Schließen", null)
            .apply { if (positive != null) setPositiveButton(positive, null) }.create()
        d.setOnShowListener {
            if (positive != null) d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (action?.invoke() == true) { d.dismiss(); onChanged(); show() } else failed()
            }
        }
        windows.add(d)
        d.setOnDismissListener { windows.remove(d) }
        d.show()
        return d
    }

    fun close() {
        windows.toList().forEach { it.dismiss() }
        windows.clear()
        dialog = null
    }

    fun show() {
        close()
        val body = ui.column()
        addText(body, "Budget ${euro(game.state.budget)} · Dienstgruppe ${game.state.chief.activeGroup}", false, true)
        addText(body, if (game.state.phase == "PLANNING") "Dienststellen, Fahrzeuge und Personen sind fiktiv. Änderungen gelten für die nächste Schicht."
            else "Verwaltung ansehen. Käufe, Personal und Ausbau sind in der Schichtplanung möglich.")
        if (game.state.chief.insolvencyWarning.isNotBlank()) addText(body, game.state.chief.insolvencyWarning, false)
        val nav = ui.row()
        listOf("Dienststellen", "Fuhrpark", "Personal", "Typen").forEach { name ->
            nav.addView(ui.button(name, section == name) { section = name; show() }.apply { textSize = 11f; setPadding(ui.dp(4), 0, ui.dp(4), 0) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(3) })
        }
        body.addView(nav)
        when (section) {
            "Dienststellen" -> stations(body)
            "Fuhrpark" -> fleet(body)
            "Personal" -> personnel(body)
            else -> types(body)
        }
        val costs = game.state.chief.dailyCosts.firstOrNull()
        if (costs != null) {
            val c = card(body)
            addText(c, "Tageskosten · Tag ${costs.day}: ${euro(costs.total)}", false, true)
            addText(c, "Gehälter ${euro(costs.wages)} · Wartung ${euro(costs.maintenance)}\nTreibstoff ${euro(costs.fuel)} · Gebäude ${euro(costs.buildings)}")
        }
        dialog = sheet("Polizeichef", body)
    }

    private fun stations(body: LinearLayout) {
        button(body, "Dienststelle gründen · 30.000 €", true) {
            val content = ui.column()
            val name = field(content, "Name der fiktiven Dienststelle", "Revier Wandsbek")
            addText(content, "Danach den gewünschten Standort auf der Karte lange drücken. Er muss innerhalb der aktuellen Zuständigkeit liegen. Zwei Stellplätze sind enthalten.")
            val d = AlertDialog.Builder(activity).setTitle("Neue Dienststelle").setView(content)
                .setPositiveButton("Standort auf Karte", null).setNegativeButton("Abbrechen", null).create()
            d.setOnShowListener {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = name.text.toString().trim()
                    if (value.isEmpty() || value.length > 48) notice("Bitte einen Namen mit 1–48 Zeichen vergeben.")
                    else { d.dismiss(); dialog?.dismiss(); onPlaceStation(value) }
                }
            }
            windows.add(d)
            d.setOnDismissListener { windows.remove(d) }
            d.show()
        }
        if (game.state.chief.stations.isEmpty()) addText(body, "Noch keine eigene Dienststelle. Gründen, Fahrzeuge kaufen und Personal zuweisen, bevor der Dienst beginnt.")
        for (station in game.state.chief.stations.filter { it.region == game.state.region }) {
            val c = card(body)
            val count = game.state.chief.vehicles.count { it.stationId == station.id }
            addText(c, "${station.name} · Stufe ${station.level}", false, true)
            addText(c, "Fiktiv · ${station.scope} · %.5f, %.5f".format(Locale.GERMANY, station.lat, station.lon))
            addText(c, "$count/${station.parking} Stellplätze · Räume ${station.lounge}/3 · Arrest ${station.cells}/3 · Werkstatt ${station.workshop}/3\nGebäude: ${euro(station.dailyCost)} pro Tag")
            val upgrades = listOf(
                Triple("PARKING", "2 Stellplätze", if (station.parking < 20) 12000 + station.parking * 1000 else null),
                Triple("LOUNGE", "Aufenthaltsraum · +5 Erholung", if (station.lounge < 3) 14000 * (station.lounge + 1) else null),
                Triple("CELLS", "Arrestbereich · −2 % Verarbeitungskosten", if (station.cells < 3) 18000 * (station.cells + 1) else null),
                Triple("WORKSHOP", "Werkstatt · −15 % Wartung", if (station.workshop < 3) 22000 * (station.workshop + 1) else null),
            )
            upgrades.forEach { (id, label, price) ->
                if (price != null) button(c, "$label · ${euro(price)}") { changed(game.upgradeChiefStation(station.id, id)) }
            }
        }
    }

    private fun fleet(body: LinearLayout) {
        button(body, "Fahrzeugkatalog öffnen", true) { catalog() }
        if (game.state.chief.vehicles.isEmpty()) addText(body, "Noch keine eigenen Fahrzeuge. Der Katalog zeigt Preis, Kapazitäten und laufende Kosten.")
        game.state.chief.vehicles.forEach { vehicle ->
            val unit = game.state.units.find { it.id == vehicle.unitId } ?: return@forEach
            if (unit.region != game.state.region) return@forEach
            val model = ChiefCatalog.model(vehicle.modelId) ?: return@forEach
            val c = card(body)
            c.addView(VehicleIcon(activity, model, vehicle.livery), LinearLayout.LayoutParams(-1, ui.dp(64)))
            addText(c, "${unit.callsign} · ${model.name}", false, true)
            val station = game.state.chief.stations.find { it.id == vehicle.stationId }
            val type = game.state.chief.unitTypes.find { it.id == vehicle.customTypeId }
            addText(c, "${station?.name ?: "Dienststelle fehlt"} · ${type?.name ?: ui.roleName(unit.type)}\nBesatzung ${unit.officers.size}/${model.seats}, mindestens ${game.chiefMinCrew(unit)} · FMS ${game.fms(unit)}\nZustand ${unit.vehicleCondition}% · %.1f km".format(Locale.GERMANY, vehicle.odometerKm))
            button(c, "Rufname, Lackierung und Einheitentyp") { configureVehicle(vehicle, unit) }
            button(c, "Anderer Dienststelle zuweisen") {
                val available = game.state.chief.stations.filter { it.region == unit.region && it.scope == game.state.scope }
                AlertDialog.Builder(activity).setTitle("Dienststelle wählen").setItems(available.map { it.name }.toTypedArray()) { _, index ->
                    changed(game.assignChiefVehicle(vehicle.id, available[index].id))
                }.setNegativeButton("Abbrechen", null).show()
            }
            button(c, "Verkaufen · ca. ${euro((model.price * .6 * unit.vehicleCondition / 100).toInt())}") {
                AlertDialog.Builder(activity).setTitle("${unit.callsign} verkaufen?")
                    .setMessage("Die Besatzung kehrt in die Personalreserve zurück. Der Erlös richtet sich nach dem Fahrzeugzustand.")
                    .setPositiveButton("Verkaufen") { _, _ -> changed(game.sellChiefVehicle(vehicle.id)) }
                    .setNegativeButton("Abbrechen", null).show()
            }
        }
    }

    private fun catalog() {
        val body = ui.column()
        addText(body, "Fiktive Modelle und Spielwerte. Kauf benötigt einen freien Stellplatz; Fahrzeuge werden zunächst ohne Besatzung geliefert.")
        ChiefCatalog.models.forEach { model ->
            val c = card(body)
            c.addView(VehicleIcon(activity, model, "BLUE_SILVER"), LinearLayout.LayoutParams(-1, ui.dp(64)))
            addText(c, model.name, false, true)
            addText(c, "${euro(model.price)} · ${model.seats} Plätze · ${model.speed.toInt()} km/h Spielwert\n${model.cargoKg} kg Laderaum · Wartung ${euro(model.maintenance)}/Tag\nVerbrauch ${model.litresPer100Km} l/100 km · Mindestbesatzung ${model.minCrew}\nKarrierestufe ${model.careerLevel} · ${model.capabilities.joinToString { ui.roleName(it) }}")
            button(c, "${model.name} kaufen") { purchase(model) }
        }
        sheet("Fuhrparkkatalog", body)
    }

    private fun purchase(model: ChiefVehicleModel) {
        val stations = game.state.chief.stations.filter { it.region == game.state.region && it.scope == game.state.scope }
        if (stations.isEmpty()) { notice("Zuerst eine Dienststelle in dieser Zuständigkeit gründen."); return }
        val c = ui.column()
        addText(c, "${model.name} · ${euro(model.price)}", false, true)
        val station = choice(c, "Dienststelle", stations.map { s -> "${s.name} · ${game.state.chief.vehicles.count { it.stationId == s.id }}/${s.parking} Plätze" })
        val callsign = field(c, "Funkrufname · Schema Peter 31/1", nextCallsign())
        val livery = choice(c, "Lackierung", listOf("Blau-silber", "Grün-weiß klassisch"))
        val types = game.state.chief.unitTypes.filter { it.modelId == model.id }
        val type = choice(c, "Einheitentyp", listOf("Standard · ${ui.roleName(model.baseType)}") + types.map { it.name })
        sheet("Fahrzeug kaufen", c, "Kaufen") {
            game.buyChiefVehicle(model.id, stations[station.selectedItemPosition].id, callsign.text.toString().trim(),
                ChiefCatalog.liveries[livery.selectedItemPosition], types.getOrNull(type.selectedItemPosition - 1)?.id ?: "") != null
        }
    }
    private fun nextCallsign(): String = (1..99).map { "Peter 31/$it" }.firstOrNull { call -> game.state.units.none { it.callsign == call } } ?: "Peter 32/1"

    private fun configureVehicle(vehicle: ChiefVehicle, unit: UnitState) {
        val c = ui.column()
        val name = field(c, "Funkrufname · Schema Peter 31/1", unit.callsign)
        val livery = choice(c, "Lackierung", listOf("Blau-silber", "Grün-weiß klassisch"), ChiefCatalog.liveries.indexOf(vehicle.livery))
        val types = game.state.chief.unitTypes.filter { it.modelId == vehicle.modelId }
        val type = choice(c, "Einheitentyp", listOf("Standard") + types.map { it.name }, types.indexOfFirst { it.id == vehicle.customTypeId } + 1)
        sheet("Fahrzeug konfigurieren", c, "Speichern") {
            val selectedType = types.getOrNull(type.selectedItemPosition - 1)?.id ?: ""
            game.configureChiefVehicle(vehicle.id, name.text.toString().trim(), ChiefCatalog.liveries[livery.selectedItemPosition]) &&
                game.setChiefVehicleType(vehicle.id, selectedType)
        }
    }

    private fun personnel(body: LinearLayout) {
        button(body, "Bewerbungen ansehen & einstellen", true) { applicants() }
        button(body, "Aktive Dienstgruppe: ${game.state.chief.activeGroup}") {
            AlertDialog.Builder(activity).setTitle("Dienstgruppe für nächste Schicht")
                .setItems(ChiefCatalog.groups.toTypedArray()) { _, index -> changed(game.setChiefDutyGroup(ChiefCatalog.groups[index])) }
                .setNegativeButton("Abbrechen", null).show()
        }
        if (game.state.chief.employees.isEmpty()) addText(body, "Personal wird getrennt vom Fuhrpark eingestellt. Weise mindestens die erforderliche Besatzung zu.")
        game.state.chief.employees.forEach { employee ->
            val o = game.chiefOfficer(employee)
            val c = card(body)
            val head = ui.row()
            head.addView(ImageView(activity).apply { setImageBitmap(EquipmentArt.portrait(activity, o.portrait)); scaleType = ImageView.ScaleType.CENTER_CROP },
                LinearLayout.LayoutParams(ui.dp(60), ui.dp(72)))
            val info = ui.column().apply { setPadding(ui.dp(10), 0, 0, 0) }
            addText(info, o.name, false, true)
            addText(info, ChiefCatalog.ranks[employee.rank.coerceIn(0, ChiefCatalog.ranks.lastIndex)])
            head.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); c.addView(head)
            val assigned = game.state.units.find { it.id == employee.assignedUnitId }
            addText(c, "${euro(employee.salary)}/Tag · Fähigkeit ${o.skill} · Erfahrung ${o.experience} · Kraft ${o.energy}%\nGruppe ${employee.group} · %02d:00 / ${employee.dutyHours}h\n${assigned?.callsign ?: "Personalreserve"}".format(Locale.GERMANY, employee.startHour))
            button(c, "Fahrzeug zuweisen / Personalreserve") { assign(employee) }
            button(c, "Dienstgruppe und Dienstplan") { schedule(employee) }
            button(c, "Befördern · 1.800 €") { changed(game.promoteChiefOfficer(employee.id)) }
            button(c, "Entlassen") {
                AlertDialog.Builder(activity).setTitle("${o.name} entlassen?")
                    .setPositiveButton("Entlassen") { _, _ -> changed(game.fireChiefOfficer(employee.id)) }
                    .setNegativeButton("Abbrechen", null).show()
            }
        }
    }
    private fun applicants() {
        val c = ui.column()
        val applicants = game.chiefApplicants()
        addText(c, "${applicants.size} Bewerbungen heute. Alle Namen und Porträts sind fiktiv. Gehälter werden auch für Reservepersonal täglich berechnet.")
        applicants.forEach { applicant ->
            val item = card(c)
            item.addView(ImageView(activity).apply { setImageBitmap(EquipmentArt.portrait(activity, applicant.portrait)); scaleType = ImageView.ScaleType.CENTER_CROP }, LinearLayout.LayoutParams(ui.dp(72), ui.dp(84)))
            addText(item, applicant.name, false, true)
            addText(item, "${ChiefCatalog.ranks[applicant.rank]} · Fähigkeit ${applicant.skill}\n${euro(ChiefCatalog.salaryPerDay[applicant.rank])}/Tag · Einstellung ${euro(applicant.hiringCost)}")
            button(item, "${applicant.name} einstellen") {
                if (game.hireChiefOfficer(applicant.id) != null) {
                    onChanged(); show(); notice("Eingestellt. Im Personalbereich einem Fahrzeug zuweisen.")
                } else failed()
            }
        }
        sheet("Bewerbungen", c)
    }
    private fun assign(employee: ChiefEmployee) {
        val vehicles = game.state.chief.vehicles.mapNotNull { v -> game.state.units.find { it.id == v.unitId && it.region == game.state.region && it.scope == game.state.scope } }
        AlertDialog.Builder(activity).setTitle("${employee.officer.name}: Zuweisung")
            .setItems((listOf("Personalreserve") + vehicles.map { "${it.callsign} · ${it.officers.size}/${game.chiefMaxCrew(it)}" }).toTypedArray()) { _, index ->
                changed(game.assignChiefOfficer(employee.id, vehicles.getOrNull(index - 1)?.id))
            }.setNegativeButton("Abbrechen", null).show()
    }
    private fun schedule(employee: ChiefEmployee) {
        val c = ui.column()
        val group = choice(c, "Dienstgruppe", ChiefCatalog.groups, ChiefCatalog.groups.indexOf(employee.group))
        val start = choice(c, "Dienstbeginn", (0..23).map { "%02d:00".format(it) }, employee.startHour)
        val hours = choice(c, "Dauer", listOf("8 Stunden", "12 Stunden", "24 Stunden"), listOf(8, 12, 24).indexOf(employee.dutyHours))
        addText(c, "Nur Personen der aktiven Gruppe und mit passendem Zeitfenster besetzen die geplanten Fahrzeuge.")
        sheet("Dienstplan", c, "Speichern") {
            game.setChiefSchedule(employee.id, ChiefCatalog.groups[group.selectedItemPosition], start.selectedItemPosition, listOf(8, 12, 24)[hours.selectedItemPosition])
        }
    }

    private fun types(body: LinearLayout) {
        button(body, "Eigenen Einheitentyp definieren", true) {
            AlertDialog.Builder(activity).setTitle("Fahrzeugbasis wählen")
                .setItems(ChiefCatalog.models.map { it.name }.toTypedArray()) { _, index -> defineType(ChiefCatalog.models[index]) }
                .setNegativeButton("Abbrechen", null).show()
        }
        addText(body, "Ein eigener Typ schränkt die Fähigkeiten seines Fahrzeugmodells ein. Mindestbesatzung und Anforderungen wirken bei der Disposition.")
        game.state.chief.unitTypes.forEach { type ->
            val c = card(body)
            c.addView(ui.chip(type.name, type.color, true))
            addText(c, "${ChiefCatalog.model(type.modelId)?.name}\nMindestens ${type.minCrew} Personen · ${type.capabilities.joinToString { ui.roleName(it) }}")
        }
    }
    private fun defineType(model: ChiefVehicleModel) {
        val c = ui.column()
        val name = field(c, "Name", "${ui.roleName(model.baseType)} Plus")
        val minimum = choice(c, "Mindestbesatzung", (model.minCrew..model.seats).map { "$it Personen" })
        val colors = listOf(ui.cyan, ui.mint, ui.amber, ui.red, 0xffbe9df5.toInt())
        val color = choice(c, "Symbolfarbe", listOf("Cyan", "Mint", "Bernstein", "Rot", "Violett"))
        addText(c, "Zulässige Anforderungen")
        val checks = model.capabilities.sorted().map { capability ->
            capability to CheckBox(activity).apply {
                text = ui.roleName(capability); setTextColor(ui.ink); isChecked = true
                c.addView(this, LinearLayout.LayoutParams(-1, ui.dp(44)))
            }
        }
        sheet("Einheitentyp · ${model.name}", c, "Anlegen") {
            game.defineChiefUnitType(name.text.toString().trim(), colors[color.selectedItemPosition], model.id,
                model.minCrew + minimum.selectedItemPosition, checks.filter { it.second.isChecked }.map { it.first }.toSet()) != null
        }
    }

    /** Original Canvas silhouette, intentionally fictional and shared by every catalog variant. */
    private class VehicleIcon(activity: Activity, private val model: ChiefVehicleModel, private val livery: String) : View(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val save = canvas.save()
            canvas.translate(width / 2f, height / 2f)
            val scale = height / 50f
            canvas.scale(scale, scale)
            paint.color = 0xff091824.toInt()
            canvas.drawOval(RectF(-53f, 13f, 53f, 22f), paint)
            if (model.icon == "HELICOPTER") {
                paint.color = 0xffd9e5ed.toInt(); canvas.drawOval(RectF(-24f, -10f, 21f, 13f), paint)
                paint.strokeWidth = 4f; canvas.drawLine(18f, 1f, 53f, -9f, paint); canvas.drawLine(-42f, -18f, 40f, -18f, paint)
                canvas.drawLine(0f, -18f, 0f, -7f, paint); canvas.drawLine(48f, -18f, 55f, -2f, paint)
            } else if (model.icon == "MOTORCYCLE") {
                paint.color = Color.DKGRAY; canvas.drawCircle(-22f, 12f, 11f, paint); canvas.drawCircle(22f, 12f, 11f, paint)
                paint.color = 0xffb8d4df.toInt(); paint.strokeWidth = 5f
                canvas.drawLine(-22f, 12f, -5f, -7f, paint); canvas.drawLine(-5f, -7f, 22f, 12f, paint)
                canvas.drawLine(16f, -13f, 22f, 12f, paint); canvas.drawLine(13f, -14f, 26f, -14f, paint)
            } else {
                val van = model.seats > 5 || model.id in listOf("estate", "dog", "traffic", "special")
                paint.color = if (model.icon == "ARMORED") 0xff596472.toInt() else 0xffdce6e9.toInt()
                canvas.drawRoundRect(RectF(-49f, if (van) -17f else -10f, 49f, 16f), 6f, 6f, paint)
                if (!van) canvas.drawRoundRect(RectF(-24f, -22f, 20f, 4f), 7f, 7f, paint)
                paint.color = 0xff234a5c.toInt(); canvas.drawRoundRect(RectF(-22f, if (van) -13f else -18f, 18f, -3f), 3f, 3f, paint)
                paint.color = if (livery == "GREEN_WHITE") 0xff2b7a54.toInt() else 0xff2876ac.toInt()
                canvas.drawRect(-46f, 4f, 46f, 11f, paint)
                paint.color = 0xff142432.toInt(); canvas.drawCircle(-30f, 15f, 9f, paint); canvas.drawCircle(30f, 15f, 9f, paint)
                if (model.id != "civil") { paint.color = 0xff50bdec.toInt(); canvas.drawRoundRect(RectF(-7f, if (van) -21f else -26f, 7f, if (van) -16f else -21f), 2f, 2f, paint) }
            }
            canvas.restoreToCount(save)
        }
    }
}
