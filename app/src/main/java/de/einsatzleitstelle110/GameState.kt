package de.einsatzleitstelle110

import kotlin.math.*
import java.util.Random

/** Native game engine; completely independent from WebView and JavaScript. */
data class Officer(var name:String, var skill:Int=58, var energy:Int=100, var experience:Int=0, var portrait:Int=1)
data class UnitState(
 val id:String, var callsign:String, var type:String, var region:String,
 var lat:Double, var lon:Double, var crew:Int=2, var level:Int=1,
 var experience:Int=0, var officer:Officer=Officer("P. Müller"),
 var equipment:MutableSet<String> = mutableSetOf("Funkgerät","Schutzweste"),
 var status:String="FREE", var incidentId:String="",
 var startLat:Double=lat, var startLon:Double=lon, var destLat:Double=lat, var destLon:Double=lon,
 var travelTotal:Int=0, var travelLeft:Int=0, var workLeft:Int=0,
 var training:String="", var trainingLeft:Int=0
)
data class IncidentState(
 val id:String, var title:String, var location:String, var region:String,
 var lat:Double, var lon:Double, var priority:Int, var required:String,
 var duration:Int, var description:String, var status:String="OPEN",
 var age:Int=0, var assignedUnit:String=""
)
data class IncidentTemplate(val title:String,val priority:Int,val required:String,val duration:Int,val description:String)
data class ShiftReport(val day:Int,val region:String,val duration:Int,val served:Int,
 val solved:Int,val failed:Int,val handed:Int,val income:Int,val expenses:Int,val repDelta:Int)
data class GameState(
 var region:String="DE-HH", var mode:String="CAREER", var day:Int=1, var phase:String="PLANNING",
 var startMinute:Int=360, var duration:Int=480, var elapsed:Int=0, var speed:Int=4,
 var paused:Boolean=false, var budget:Int=240000, var reputation:Int=74,
 var shiftIncoming:Int=0, var shiftSolved:Int=0, var shiftFailed:Int=0,
 var shiftHanded:Int=0, var shiftIncome:Int=0, var nextId:Int=1,
 var autoCalls:Boolean=true, var mute:Boolean=false,
 val units:MutableList<UnitState> = mutableListOf(),
 val incidents:MutableList<IncidentState> = mutableListOf(),
 val customTemplates:MutableList<IncidentTemplate> = mutableListOf(),
 val reports:MutableList<ShiftReport> = mutableListOf()
)

object GameContent {
 val equipment = listOf("Funkgerät","Schutzweste","Bodycam","Erste-Hilfe","Taschenlampe",
 "Schutzhelm","Drohne","Schutzschild","Fahrzeugkamera","Funk-Repeater")
 val prices = mapOf("Funkgerät" to 400,"Schutzweste" to 900,"Bodycam" to 600,
 "Erste-Hilfe" to 250,"Taschenlampe" to 90,"Schutzhelm" to 950,
 "Drohne" to 3400,"Schutzschild" to 1750,"Fahrzeugkamera" to 900,"Funk-Repeater" to 1250)
 val courses = linkedMapOf("Deeskalation" to Pair(2,1600),"Erste Hilfe" to Pair(2,1400),
 "Fahndung" to Pair(3,2800),"Kommunikation" to Pair(2,1200),
 "Verkehrsdienst" to Pair(2,1750),"SEK-Fortbildung" to Pair(4,12000),
 "MEK-Observation" to Pair(4,9400),"Einsatzführung" to Pair(3,4800))
 val events = listOf(
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
 "Zeugenvernehmung|3|KRIPO|34|Ermittlungsauftrag"
 ).map {
   val p=it.split('|');IncidentTemplate(p[0],p[1].toInt(),p[2],p[3].toInt(),p[4])
 }
}

class GameController(val state:GameState,val regions:List<GeoRegion>) {
 private val random=Random(982341L)
 var notify:((String,String)->Unit)?=null
 fun region()=regions.first {it.code==state.region}
 fun freeUnits()=state.units.count {it.region==state.region&&it.status=="FREE"}
 fun openIncidents()=state.incidents.count {it.region==state.region&&it.status in listOf("OPEN","DRIVING","WORKING")}
 fun allTemplates():List<IncidentTemplate> = GameContent.events+state.customTemplates
 fun seedUnitsIfNeeded(){
  val r=region()
  if(state.units.any{it.region==r.code})return
  if(r.cities.isEmpty())return
  val types=listOf("STREIFE","STREIFE","KRIPO","STREIFE","MEK","STREIFE","SEK","STREIFE",
    "RETTUNG","FEUERWEHR","STREIFE","STREIFE","STREIFE","KRIPO")
  for(i in types.indices){
   val city=r.cities[i%r.cities.size];val t=types[i]
   val callsign=when(t){"STREIFE"->"Peter "+(12+i)+"/1";"KRIPO"->"K "+(22+i)+"/1"
    "SEK"->"SEK "+r.code.takeLast(2)+" 1";"MEK"->"MEK "+r.code.takeLast(2)+" 1"
    "RETTUNG"->"RTW "+(12+i);else->"LF "+(12+i)}
   state.units.add(UnitState(id=r.code+"-"+(i+1),callsign=callsign,type=t,region=r.code,
    lat=city.lat,lon=city.lon,crew=when(t){"SEK"->8;"MEK"->5;"FEUERWEHR"->6;else->2},
    officer=Officer(listOf("L. Becker","M. Krüger","T. Hansen","S. Fischer","A. Lange","K. Schmidt")[i%6],
        skill=(55+i*2).coerceAtMost(90),portrait=i%8+1)))
  }
 }
 fun setRegion(code:String){
  if(state.phase=="RUNNING"||regions.none{it.code==code})return
  state.region=code;seedUnitsIfNeeded()
 }
 fun setShift(startHour:Int,hours:Int){
  if(state.phase!="PLANNING"||hours !in listOf(8,12,24))return
  state.startMinute=startHour.mod(24)*60;state.duration=hours*60
 }
 fun startShift(){
  if(state.phase!="PLANNING")return
  seedUnitsIfNeeded();state.phase="RUNNING";state.elapsed=0;state.paused=false
  state.shiftIncoming=0;state.shiftSolved=0;state.shiftFailed=0;state.shiftHanded=0;state.shiftIncome=0
  state.incidents.removeAll{it.region==state.region&&it.status in listOf("SOLVED","FAILED","HANDED")}
  newIncident(GameContent.events[1])
  notify?.invoke("Schicht begonnen","Neuer Notruf eingegangen")
 }
 fun newIncident(template:IncidentTemplate = allTemplates()[random.nextInt(allTemplates().size)],
  position:GeoPoint?=null):IncidentState{
  val r=region();val city=r.cities[random.nextInt(r.cities.size)]
  val d=if(r.code=="DE-HH")0.016 else 0.065
  val candidate=GeoPoint(city.lon+(random.nextDouble()-.5)*d,city.lat+(random.nextDouble()-.5)*d)
  val p=position?:if(r.contains(candidate))candidate else GeoPoint(city.lon,city.lat)
  val call=IncidentState("E-"+(state.nextId++),template.title,city.name,r.code,
   p.lat,p.lon,template.priority,template.required,template.duration,template.description)
  state.incidents.add(0,call);state.shiftIncoming++
  notify?.invoke("Neuer Notruf P"+template.priority,template.title)
  return call
 }
 fun dispatch(call:IncidentState,u:UnitState):Boolean {
  if(state.phase!="RUNNING"||call.status!="OPEN"||u.status!="FREE"
   ||u.region!=call.region||u.type!=call.required)return false
  val km=GeoData.km(GeoPoint(u.lon,u.lat),GeoPoint(call.lon,call.lat))
  val speed=when(u.type){"SEK","MEK"->85.0;"STREIFE"->75.0;else->65.0}
  val eta=max(2,ceil(km*1.24/speed*60).toInt())
  call.status="DRIVING";call.assignedUnit=u.id
  u.status="DRIVING";u.incidentId=call.id
  u.startLat=u.lat;u.startLon=u.lon;u.destLat=call.lat;u.destLon=call.lon
  u.travelTotal=eta;u.travelLeft=eta;u.workLeft=call.duration
  notify?.invoke("Einheit unterwegs",u.callsign+" · "+eta+" Sim.-Min.")
  return true
 }
 fun advance(minutes:Int){
  if(state.phase!="RUNNING"||state.paused)return
  for(i in 0 until minutes.coerceIn(0,120)){
   if(state.elapsed>=state.duration){finishShift();return}
   state.elapsed++
   for(call in state.incidents){
    if(call.region!=state.region)continue
    if(call.status=="OPEN"){
     call.age++
     val deadline=when(call.priority){1->48;2->78;else->112}
     if(call.age>deadline){call.status="FAILED";state.shiftFailed++
      state.reputation=(state.reputation-2).coerceAtLeast(0)
      notify?.invoke("Frist überschritten",call.title)}
    }
   }
   for(u in state.units) {
    if(u.region!=state.region)continue
    when(u.status){
     "DRIVING"->{
      u.travelLeft=(u.travelLeft-1).coerceAtLeast(0)
      val t=if(u.travelTotal<=0)1.0 else 1.0-u.travelLeft.toDouble()/u.travelTotal
      u.lat=u.startLat+(u.destLat-u.startLat)*t
      u.lon=u.startLon+(u.destLon-u.startLon)*t
      if(u.travelLeft==0){u.status="WORKING"
       state.incidents.firstOrNull{it.id==u.incidentId}?.status="WORKING"
       notify?.invoke("Am Einsatzort",u.callsign)}
     }
     "WORKING"->{
      u.workLeft--
      if(u.workLeft<=0){
       val call=state.incidents.firstOrNull{it.id==u.incidentId}
       if(call!=null){
        val prob=(0.73+u.level*.018+u.equipment.size*.008-
          (if(call.priority==1).055 else 0.0)).coerceIn(.45,.98)
        val success=random.nextDouble()<prob
        call.status=if(success)"SOLVED" else "FAILED"
        if(success){
         state.shiftSolved++;state.reputation=(state.reputation+1).coerceAtMost(100)
         val reward=when(call.priority){1->1900;2->1100;else->450}
         state.shiftIncome+=reward
         if(state.mode=="CAREER")state.budget+=reward
         u.experience+=12
        }else{state.shiftFailed++;state.reputation=(state.reputation-1).coerceAtLeast(0)}
        notify?.invoke(if(success)"Einsatz beendet" else "Einsatz fehlgeschlagen",call.title)
       }
       u.status="FREE";u.incidentId="";u.officer.energy=(u.officer.energy-9).coerceAtLeast(0)
      }
     }
    }
   }
   if(state.autoCalls&&state.elapsed%36==0&&openIncidents()<8)newIncident()
   if(state.elapsed>=state.duration)finishShift()
  }
 }
 fun finishShift(){
  if(state.phase!="RUNNING")return
  val handed=state.incidents.count{it.region==state.region&&it.status in listOf("OPEN","DRIVING","WORKING")}
  state.incidents.filter{it.region==state.region&&it.status in listOf("OPEN","DRIVING","WORKING")}
   .forEach{it.status="HANDED"}
  state.shiftHanded=handed
  val expenses=state.units.filter{it.region==state.region}.sumOf{190*it.crew}
  if(state.mode=="CAREER")state.budget-=expenses
  val repDelta=(state.shiftSolved-state.shiftFailed*2)/3
  state.reputation=(state.reputation+repDelta).coerceIn(0,100)
  for(u in state.units.filter{it.region==state.region}){
   if(u.status=="DRIVING"||u.status=="WORKING"){u.status="FREE";u.incidentId=""}
   val drain=when(state.duration){480->8;720->14;else->22}
   u.officer.energy=(u.officer.energy-drain+(if(u.status=="TRAINING")0 else 7)).coerceIn(0,100)
   if(u.trainingLeft>0){
    u.trainingLeft--
    if(u.trainingLeft==0){u.status="FREE";u.training="";u.level++
      u.officer.skill=(u.officer.skill+8).coerceAtMost(100)
      notify?.invoke("Lehrgang abgeschlossen",u.callsign)}
   }
  }
  state.reports.add(0,ShiftReport(state.day,state.region,state.duration,state.shiftIncoming,
    state.shiftSolved,state.shiftFailed,handed,state.shiftIncome,expenses,repDelta))
  state.phase="DEBRIEF";state.paused=true
  notify?.invoke("Schichtende","Tag "+state.day+" abgeschlossen")
 }
 fun nextDay(){
  if(state.phase!="DEBRIEF")return
  state.day++;state.phase="PLANNING";state.paused=false;state.elapsed=0
  state.incidents.removeAll{it.status in listOf("SOLVED","FAILED","HANDED")}
  state.units.forEach{it.officer.energy=(it.officer.energy+14).coerceAtMost(100)}
 }
 fun train(u:UnitState,course:String):Boolean{
  if(state.phase!="PLANNING"||u.trainingLeft>0||course !in GameContent.courses)return false
  if(course.startsWith("SEK")&&u.type!="SEK")return false
  if(course.startsWith("MEK")&&u.type!="MEK")return false
  val cfg=GameContent.courses.getValue(course)
  if(state.mode=="CAREER"&&state.budget<cfg.second)return false
  if(state.mode=="CAREER")state.budget-=cfg.second
  u.training=course;u.trainingLeft=cfg.first;u.status="TRAINING";return true
 }
 fun buyEquipment(u:UnitState,item:String):Boolean{
  val price=GameContent.prices[item]?:return false
  if(item in u.equipment||u.equipment.size>=6)return false
  if(state.mode=="CAREER"&&state.budget<price)return false
  if(state.mode=="CAREER")state.budget-=price
  u.equipment.add(item);return true
 }
 fun addUnit(callsign:String,type:String,city:GeoCity):Boolean{
  if(callsign.isBlank()||type !in listOf("STREIFE","KRIPO","SEK","MEK","RETTUNG","FEUERWEHR"))return false
  if(state.mode=="CAREER"&&state.budget<25000)return false
  if(state.mode=="CAREER")state.budget-=25000
  state.units.add(UnitState("CUSTOM-"+(state.nextId++),callsign.trim().take(40),
    type,state.region,city.lat,city.lon,crew=if(type=="SEK")8 else 2))
  return true
 }
 fun addTemplate(t:IncidentTemplate):Boolean{
  if(t.title.isBlank()||t.duration !in 1..240||t.priority !in 1..3)return false
  if(t.required !in listOf("STREIFE","SEK","MEK","KRIPO","FEUERWEHR","RETTUNG"))return false
  state.customTemplates.add(t);return true
 }
 fun clock():String{
  val min=(state.startMinute+state.elapsed).mod(1440)
  return "%02d:%02d".format(min/60,min%60)
 }
}
