package de.einsatzleitstelle110

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.Random
import kotlin.math.*

data class GeoPoint(val lon: Double, val lat: Double)
data class City(val name: String, val pos: GeoPoint)
data class Region(val code: String, val name: String, val outlines: List<List<GeoPoint>>, val cities: List<City>, val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    val center get() = GeoPoint((minLon+maxLon)*0.5,(minLat+maxLat)*0.5)
    fun contains(p:GeoPoint):Boolean {
        if(p.lon<minLon || p.lon>maxLon || p.lat<minLat || p.lat>maxLat)return false
        for(path in outlines){var inside=false;var j=path.lastIndex
            for(i in path.indices){val a=path[i];val b=path[j];
                if((a.lat>p.lat)!=(b.lat>p.lat) && p.lon<(b.lon-a.lon)*(p.lat-a.lat)/(b.lat-a.lat)+a.lon)inside=!inside
                j=i
            };if(inside)return true
        };return false
    }
}

data class Officer(
    val id: Int, var name: String, var skill: Int=60, var energy: Int=100,
    var portrait: String="officer_01.webp", var equipment: MutableList<String> = mutableListOf("Funk"),
    var trainingDays:Int=0, var trainingCourse:String=""
)
data class VehicleUnit(
    val id:Int, var callsign:String, var type:String, var region:String,
    var lon:Double, var lat:Double, var baseLon:Double=lon, var baseLat:Double=lat,
    var state:String="FREI", var incidentId:Int=-1,
    var routeLon:Double=lon,var routeLat:Double=lat,
    var routeStartLon:Double=lon,var routeStartLat:Double=lat,
    var routeStartMinute:Int=0,var routeEndMinute:Int=0,var workEndMinute:Int=0,
    var officers:MutableList<Officer> = mutableListOf(),var vehicleCondition:Int=100
)
data class Incident(
    val id:Int,var title:String,var region:String,var lon:Double,var lat:Double,
    var priority:Int,var requiredType:String,var minutesNeeded:Int,
    var createdMinute:Int,var status:String="OFFEN", var assignedUnit:Int=-1,
    var deadlineMinute:Int=createdMinute+45,var description:String="",var reward:Int=700
)
data class Course(val name:String,val duration:Int,val price:Int,val benefit:Int)
data class ShiftReport(val day:Int,val region:String,val length:Int,val startHour:Int,val total:Int,val success:Int,val failed:Int,val handovers:Int,val moneyDelta:Int,val reputationDelta:Int)

object Catalog {
    val courses=listOf(
        Course("Digitalfunk & Kommunikation",2,900,6),Course("Deeskalation",2,1400,7),
        Course("Erste Hilfe",2,1250,7),Course("Verkehrsunfallmanagement",3,1800,8),
        Course("Kriminalistische Ermittlungen",4,4200,10),Course("Einsatzführung",4,5700,12),
        Course("SEK-Fortbildung",5,9600,13),Course("MEK-Observation",4,7900,12)
    )
    // 52 original, game-only situations. Specialist deployment remains strategic and abstract.
    private val texts = """Ruhestörung|STREIFE|3;Verkehrsunfall|VERKEHR|2;Ladendiebstahl|STREIFE|3;Vermisstenmeldung|STREIFE|2;Einbruchalarm|STREIFE|2;Auseinandersetzung|STREIFE|2;Verdächtiger Gegenstand|STREIFE|1;Großveranstaltung|STREIFE|2;Zeugenhinweis|KRIPO|3;Einbruchserie|KRIPO|2;Tatortaufnahme|KRIPO|2;Ermittlungsauftrag|KRIPO|3;Observationsauftrag|MEK|2;Fahndung nach Fahrzeug|MEK|2;Verdächtige Fahrzeugbewegung|MEK|2;Sicherheitsgefährdung|SEK|1;Bewaffnete Bedrohung|SEK|1;Geisellage|SEK|1;Gefährdungslage|SEK|1;Koordinierte Suche|MEK|2;Verkehrskontrolle|VERKEHR|3;Fahrerflucht|VERKEHR|2;Unfall mit Verletzten|VERKEHR|1;Straßensperrung|VERKEHR|2;Schwerlasttransport|VERKEHR|3;Stau nach Unfall|VERKEHR|3;Gefährlicher Eingriff in Verkehr|VERKEHR|2;Wassernotfall|WASSER|1;Hafenstörung|WASSER|2;Vermisstes Boot|WASSER|2;Kollision auf Wasserstraße|WASSER|1;Schiffskontrolle|WASSER|3;Suchflug anfordern|HUBSCHRAUBER|1;Vermisstensuche aus Luft|HUBSCHRAUBER|2;Luftunterstützung|HUBSCHRAUBER|2;Waldgebiet durchsuchen|HUBSCHRAUBER|2;Störung im Einkaufszentrum|STREIFE|2;Bedrohliche Streitigkeit|STREIFE|2;Fahrraddiebstahl|STREIFE|3;Verdächtige Geräusche|STREIFE|3;Nachbarschaftsstreit|STREIFE|3;Haustüralarm|STREIFE|2;Diebstahl auf Wochenmarkt|STREIFE|3;Lage an Bahnhof|STREIFE|2;Störung in öffentlichem Verkehr|STREIFE|2;Falscher Notruf|STREIFE|3;Verdächtiges Gepäck|STREIFE|2;Brandstiftungsverdacht|KRIPO|2;Vandalismusserie|KRIPO|3;Betrugsanzeige|KRIPO|3;Zeugenbefragung|KRIPO|3;Großlage koordinieren|SEK|1"""
    val templates=texts.split(';').map { val t=it.split('|'); Triple(t[0],t[1],t[2].toInt()) }
    val gear=mapOf("Funk" to 500,"Schutzweste" to 950,"Helm" to 1350,"Bodycam" to 720,"Erste Hilfe" to 340,"Taschenlampe" to 100,"Lagebildkamera" to 470,"Spezialschutz" to 1800)
    val unitTypes=listOf("STREIFE","VERKEHR","KRIPO","SEK","MEK","WASSER","HUBSCHRAUBER")
    val names=listOf("Lukas Becker","Mia Krüger","Alex Hansen","Lena Schmitz","Jonas Richter","Nina König","Ben Seidel","Eva Wagner","Tom Fischer","Sarah Brandt","Paul Werner","Marie Peters","Daniel Winter","Jasmin Koch")
}

class SimEngine(private val context:Context) {
    val regions:List<Region> = loadRegions()
    var activeRegion:String="HH"; private set
    var mode:String="KARRIERE"; private set
    var day:Int=1;private set
    var stage:String="WILLKOMMEN";private set
    var startHour:Int=6;private set
    var duration:Int=480;private set
    var elapsed:Int=0;private set
    var paused:Boolean=false;private set
    var speed:Int=1;private set
    var budget:Int=650000;private set
    var reputation:Int=70;private set
    var successes:Int=0;private set
    var failures:Int=0;private set
    var selectedUnit:Int=-1
    var selectedIncident:Int=-1
    var autoCalls:Boolean=true
    private var seq=1000
    private var nextCallAt=10
    val units= mutableListOf<VehicleUnit>()
    val incidents= mutableListOf<Incident>()
    val reports=mutableListOf<ShiftReport>()
    val logs=mutableListOf<String>()
    val customTemplates=mutableListOf<Triple<String,String,Int>>()
    private val random=Random(1102026L)
    val region get() = regions.find {it.code==activeRegion} ?: regions.first()
    val available get() = units.count {it.region==activeRegion&&it.state=="FREI"}
    val liveIncidents get()=incidents.filter{it.region==activeRegion&&it.status in setOf("OFFEN","ANFAHRT","BEARBEITUNG")}
    val activeUnits get()=units.filter{it.region==activeRegion}
    val clock:String get(){val min=(startHour*60+elapsed)%1440;return "%02d:%02d".format(Locale.GERMANY,min/60,min%60)}
    val elapsedHours get()="%02d:%02d".format(Locale.GERMANY,elapsed/60,elapsed%60)
    val shiftEndHour get()=(startHour+duration/60)%24
    val isRunning get()=stage=="LAUFEND"
    val isDebrief get()=stage=="AUSWERTUNG"
    val isPlanning get()=stage=="PLANUNG"
    init {if(!restore())initGame("KARRIERE",false)}
    fun log(message:String){logs.add(0,"[$clock] $message");while(logs.size>120)logs.removeAt(logs.lastIndex)}
    fun startNewGame(selectedMode:String){initGame(selectedMode,true)}
    private fun initGame(selectedMode:String,save:Boolean){
        activeRegion="HH";mode=if(selectedMode=="SANDBOX")"SANDBOX" else "KARRIERE";day=1;stage="PLANUNG";startHour=6;duration=480;elapsed=0;paused=false;speed=1;budget=650000;reputation=70;successes=0;failures=0;seq=1000;nextCallAt=10;autoCalls=true
        units.clear();incidents.clear();reports.clear();logs.clear();customTemplates.clear()
        var id=1
        for(r in regions){val cities=r.cities.ifEmpty{listOf(City(r.name,r.center))};val n=max(8,min(20,cities.size*2))
            for(i in 0 until n){val city=cities[i%cities.size]; val type=when {i==n-1->"SEK";i==n-2->"MEK";i==n-3->"KRIPO";i==n-4->"VERKEHR";i==n-5->"HUBSCHRAUBER";r.code in setOf("HH","HB","SH","MV")&&i==n-6->"WASSER";else->"STREIFE"};
                val name=when(type){"SEK"->"SEK ${r.code} 1";"MEK"->"MEK ${r.code} 1";"KRIPO"->"K ${r.code} ${i+1}";"HUBSCHRAUBER"->"POLHUB ${r.code}";"WASSER"->"WSP ${r.code}";else->"P ${r.code} ${i+1}/1"}
                val jitterLon=if(i<cities.size)0.0 else ((i*31%11)-5)*0.010
                val jitterLat=if(i<cities.size)0.0 else ((i*17%9)-4)*0.008
                val pt=GeoPoint(city.pos.lon+jitterLon,city.pos.lat+jitterLat)
                val inside=if(r.contains(pt))pt else city.pos
                val team=(if(type=="SEK")5 else if(type=="MEK")3 else 2)
                val staff=(0 until team).map{j-> Officer(id*10+j,Catalog.names[(id+j)%Catalog.names.size],skill=53+(id*11+j*7)%37,portrait="officer_%02d.webp".format(Locale.US,(id+j)%8+1),equipment=mutableListOf("Funk",if(type=="SEK")"Schutzweste" else "Taschenlampe"))}.toMutableList()
                units.add(VehicleUnit(id,name,type,r.code,inside.lon,inside.lat,officers=staff));id++
            }
        }
        log("Leitstelle bereit. ${regions.size} Bundesländer, ${units.size} Einsatzfahrzeuge geladen.")
        if(save)persist()
    }
    fun setRegion(code:String){if(stage=="LAUFEND")return; if(regions.none{it.code==code})return;activeRegion=code;selectedIncident=-1;selectedUnit=-1;log("Leitstellenbezirk: ${region.name}");persist()}
    fun planShift(hours:Int,startingHour:Int){if(stage=="LAUFEND")return;duration=when(hours){12->720;24->1440;else->480};startHour=startingHour.coerceIn(0,23);persist()}
    fun startShift(){if(stage!="PLANUNG")return;elapsed=0;paused=false;speed=1;stage="LAUFEND";nextCallAt=6;log("Schichtbeginn ${region.name}: ${duration/60} Stunden · $clock");persist()}
    fun togglePause(){if(isRunning){paused=!paused;persist()}}
    fun setSpeed(value:Int){if(isRunning)speed=when(value){4,8,16->value;else->1}}
    fun simulateCall(){if(!isRunning)return;val templates=Catalog.templates+customTemplates;val t=templates[random.nextInt(templates.size)];createIncident(t.first,t.second,t.third)}
    fun createIncident(title:String,type:String,priority:Int,point:GeoPoint?=null):Incident{
        val cities=region.cities.ifEmpty{listOf(City(region.name,region.center))}
        val location=point ?: cities[random.nextInt(cities.size)].pos
        val required=if(type in Catalog.unitTypes)type else "STREIFE"
        val p=priority.coerceIn(1,3)
        val inc=Incident(++seq,title.take(72),activeRegion,location.lon,location.lat,p,required,15+random.nextInt(31),elapsed,deadlineMinute=elapsed+if(p==1)35 else if(p==2)60 else 90,description="Eingehende Meldung aus ${region.name}. Lageeinschätzung erforderlich.",reward=(4-p)*460)
        incidents.add(0,inc);log("NOTRUF P$p: ${inc.title} – ${required}");persist();return inc
    }
    fun addCustomTemplate(title:String,type:String,priority:Int):Boolean{
        if(title.isBlank()||type !in Catalog.unitTypes)return false
        customTemplates.add(Triple(title.take(72),type,priority.coerceIn(1,3)));log("Einsatzvorlage: ${title.take(36)}");persist();return true
    }
    fun addUnit(name:String,type:String,pos:GeoPoint?=null):Boolean{
        if(name.isBlank()||type !in Catalog.unitTypes)return false
        if(mode=="KARRIERE"&&budget<18000)return false
        val p=pos?:region.cities.firstOrNull()?.pos?:region.center
        val id=++seq;val staff=mutableListOf(Officer(id*10,Catalog.names[id%Catalog.names.size],portrait="officer_%02d.webp".format(Locale.US,id%8+1)))
        units.add(VehicleUnit(id,name.take(40),type,activeRegion,p.lon,p.lat,officers=staff))
        if(mode=="KARRIERE")budget-=18000;log("Neue Einheit: ${name.take(40)}");persist();return true
    }
    fun dispatch(incidentId:Int,unitId:Int):Boolean{
        val inc=incidents.find{it.id==incidentId}?:return false
        val unit=units.find{it.id==unitId}?:return false
        if(!isRunning||inc.status!="OFFEN"||unit.state!="FREI"||unit.region!=inc.region)return false
        // Substitution is possible with diminished success rate; specialized requests are kept distinct.
        if(unit.type!=inc.requiredType&&!(inc.requiredType in setOf("STREIFE","VERKEHR")&&unit.type in setOf("STREIFE","VERKEHR")))return false
        val kilometers=haversine(unit.lat,unit.lon,inc.lat,inc.lon)
        val kmh=when(unit.type){"HUBSCHRAUBER"->185;"MEK"->75;"SEK"->72;else->60}
        val travelMinutes=(3+kilometers*60/kmh).roundToInt().coerceIn(3,240)
        unit.routeStartLon=unit.lon;unit.routeStartLat=unit.lat;unit.routeLon=inc.lon;unit.routeLat=inc.lat
        unit.routeStartMinute=elapsed;unit.routeEndMinute=elapsed+travelMinutes;unit.workEndMinute=unit.routeEndMinute+inc.minutesNeeded
        unit.incidentId=inc.id;unit.state="ANFAHRT";inc.assignedUnit=unit.id;inc.status="ANFAHRT"
        log("${unit.callsign} → ${inc.title} · ETA ~${travelMinutes} Min (simuliert)");persist();return true
    }
    fun tick(minutes:Int=1){if(!isRunning||paused)return
        for(step in 0 until minutes.coerceIn(1,240)){
            if(!isRunning)break
            elapsed++
            for(u in units){if(u.region!=activeRegion)continue
                if(u.state=="ANFAHRT"){
                    val denominator=max(1,u.routeEndMinute-u.routeStartMinute)
                    val t=((elapsed-u.routeStartMinute).toDouble()/denominator).coerceIn(0.0,1.0)
                    u.lon=u.routeStartLon+(u.routeLon-u.routeStartLon)*t;u.lat=u.routeStartLat+(u.routeLat-u.routeStartLat)*t
                    if(t>=1.0){u.state="BEARBEITUNG";incidents.find{it.id==u.incidentId}?.status="BEARBEITUNG"}
                }else if(u.state=="BEARBEITUNG"&&elapsed>=u.workEndMinute){
                    val i=incidents.find{it.id==u.incidentId};if(i!=null){
                        val staff=u.officers.map{it.skill}.average().takeIf{!it.isNaN()}?:60.0
                        val energy=u.officers.map{it.energy}.average().takeIf{!it.isNaN()}?:80.0
                        val probability=(0.70+(staff-65)*0.005+(energy-70)*0.001-(i.priority-1)*0.01).coerceIn(0.45,0.96)
                        val ok=random.nextDouble()<probability
                        i.status=if(ok)"ERFOLG" else "FEHLGESCHLAGEN"
                        if(ok){successes++;if(mode=="KARRIERE")budget+=i.reward;reputation=(reputation+1).coerceAtMost(100)}else{failures++;reputation=(reputation-2).coerceAtLeast(0)}
                        log("${i.title}: ${if(ok)"erfolgreich" else "nicht erfolgreich"} abgeschlossen")
                    }
                    u.state="FREI";u.incidentId=-1;u.vehicleCondition=(u.vehicleCondition-1).coerceAtLeast(1)
                    u.officers.forEach{it.energy=(it.energy-3).coerceAtLeast(5)}
                }
            }
            for(i in incidents){if(i.region==activeRegion&&i.status=="OFFEN"&&elapsed>i.deadlineMinute){i.status="FEHLGESCHLAGEN";failures++;reputation=(reputation-1).coerceAtLeast(0);log("Frist überschritten: ${i.title}")}}
            if(autoCalls&&elapsed>=nextCallAt&&liveIncidents.size<9){simulateCall();nextCallAt=elapsed+14+random.nextInt(19)}
            if(elapsed>=duration){completeShift();break}
        }
        persist()
    }
    fun completeShift():Boolean{
        if(!isRunning||elapsed<30)return false
        val current=incidents.filter{it.region==activeRegion&&it.createdMinute<=elapsed}
        val handed=current.count{it.status in setOf("OFFEN","ANFAHRT","BEARBEITUNG")}
        for(i in current){if(i.status in setOf("OFFEN","ANFAHRT","BEARBEITUNG"))i.status="ÜBERGABE"}
        for(u in activeUnits){if(u.state in setOf("ANFAHRT","BEARBEITUNG")){u.state="FREI";u.incidentId=-1}
            val fatigue=when(duration){1440->36;720->23;else->15}
            u.officers.forEach{o->o.energy=(o.energy-fatigue+16).coerceIn(15,100);if(o.trainingDays>0){o.trainingDays--;if(o.trainingDays==0){val course=Catalog.courses.find{it.name==o.trainingCourse};if(course!=null)o.skill=(o.skill+course.benefit).coerceAtMost(100);log("Fortbildung abgeschlossen: ${o.name}")}}}
        }
        val expenses=if(mode=="KARRIERE") (activeUnits.size*54*duration/480+650) else 0
        if(mode=="KARRIERE")budget-=expenses
        val report=ShiftReport(day,activeRegion,duration,startHour,current.size,current.count{it.status=="ERFOLG"},current.count{it.status=="FEHLGESCHLAGEN"},handed,-expenses,0)
        reports.add(0,report);stage="AUSWERTUNG";paused=true;log("Schichtende Tag $day – ${duration/60} Stunden");persist();return true
    }
    fun nextDay(){if(!isDebrief)return;day++;stage="PLANUNG";elapsed=0;paused=false;incidents.removeAll{it.status in setOf("ERFOLG","FEHLGESCHLAGEN","ÜBERGABE")&&it.region==activeRegion};log("Tag $day: Einsatzplanung bereit");persist()}
    fun train(unitId:Int,course:Int):Boolean{
        if(stage=="LAUFEND"||course !in Catalog.courses.indices)return false
        val u=units.find{it.id==unitId}?:return false;val c=Catalog.courses[course]
        if(u.officers.any{it.trainingDays>0})return false
        if(mode=="KARRIERE"&&budget<c.price)return false
        if(mode=="KARRIERE")budget-=c.price
        u.officers.forEach{it.trainingDays=c.duration;it.trainingCourse=c.name}
        log("${u.callsign}: Fortbildung ${c.name} gestartet (${c.duration} Tage)");persist();return true
    }
    fun equip(unitId:Int,gear:String):Boolean{
        val u=units.find{it.id==unitId}?:return false
        val o=u.officers.firstOrNull()?:return false
        val cost=Catalog.gear[gear]?:return false
        if(gear in o.equipment)return false
        if(o.equipment.size>=5)return false
        if(mode=="KARRIERE"&&budget<cost)return false
        if(mode=="KARRIERE")budget-=cost
        o.equipment.add(gear);log("${u.callsign}: $gear ausgerüstet");persist();return true
    }
    fun repair(unitId:Int):Boolean{val u=units.find{it.id==unitId}?:return false
        if(mode=="KARRIERE"&&budget<3800)return false
        if(mode=="KARRIERE")budget-=3800
        u.vehicleCondition=100;log("${u.callsign}: Fahrzeug gewartet");persist();return true}
    fun saveJson():String {
        val o=JSONObject()
        o.put("schemaVersion",5).put("mode",mode).put("activeRegion",activeRegion).put("day",day).put("stage",stage).put("startHour",startHour).put("duration",duration).put("elapsed",elapsed).put("paused",paused).put("speed",speed).put("budget",budget).put("reputation",reputation).put("successes",successes).put("failures",failures).put("seq",seq).put("nextCallAt",nextCallAt).put("autoCalls",autoCalls)
        val ua=JSONArray();for(u in units){val x=JSONObject();x.put("id",u.id).put("name",u.callsign).put("type",u.type).put("region",u.region).put("lon",u.lon).put("lat",u.lat).put("baseLon",u.baseLon).put("baseLat",u.baseLat).put("state",u.state).put("incidentId",u.incidentId).put("routeLon",u.routeLon).put("routeLat",u.routeLat).put("routeStartLon",u.routeStartLon).put("routeStartLat",u.routeStartLat).put("routeStartMinute",u.routeStartMinute).put("routeEndMinute",u.routeEndMinute).put("workEndMinute",u.workEndMinute).put("condition",u.vehicleCondition)
            val staff=JSONArray();for(v in u.officers)staff.put(JSONObject().put("id",v.id).put("name",v.name).put("skill",v.skill).put("energy",v.energy).put("portrait",v.portrait).put("equipment",JSONArray(v.equipment)).put("trainingDays",v.trainingDays).put("trainingCourse",v.trainingCourse));x.put("officers",staff);ua.put(x)}
        o.put("units",ua)
        val ia=JSONArray();for(i in incidents){ia.put(JSONObject().put("id",i.id).put("title",i.title).put("region",i.region).put("lon",i.lon).put("lat",i.lat).put("priority",i.priority).put("requiredType",i.requiredType).put("minutesNeeded",i.minutesNeeded).put("createdMinute",i.createdMinute).put("status",i.status).put("assignedUnit",i.assignedUnit).put("deadlineMinute",i.deadlineMinute).put("description",i.description).put("reward",i.reward))};o.put("incidents",ia)
        val hs=JSONArray();for(h in reports)hs.put(JSONObject().put("day",h.day).put("region",h.region).put("length",h.length).put("startHour",h.startHour).put("total",h.total).put("success",h.success).put("failed",h.failed).put("handovers",h.handovers).put("moneyDelta",h.moneyDelta).put("reputationDelta",h.reputationDelta));o.put("reports",hs)
        o.put("logs",JSONArray(logs));o.put("customTemplates",JSONArray(customTemplates.map{JSONObject().put("title",it.first).put("type",it.second).put("priority",it.third)}));return o.toString()
    }
    fun importJson(json:String):Boolean {try {val o=JSONObject(json);if(o.optInt("schemaVersion")!=5||o.optJSONArray("units")==null)return false
        val nu=mutableListOf<VehicleUnit>();val arr=o.getJSONArray("units");if(arr.length()>2000)return false
        for(k in 0 until arr.length()){val x=arr.getJSONObject(k);val staff=mutableListOf<Officer>();val ppl=x.optJSONArray("officers")?:JSONArray();for(p in 0 until ppl.length()){val v=ppl.getJSONObject(p);val eq=mutableListOf<String>();val a=v.optJSONArray("equipment")?:JSONArray();for(z in 0 until a.length())eq.add(a.optString(z));staff.add(Officer(v.getInt("id"),v.getString("name"),v.optInt("skill",60),v.optInt("energy",80),v.optString("portrait","officer_01.webp"),eq,v.optInt("trainingDays",0),v.optString("trainingCourse","")))}
            nu.add(VehicleUnit(x.getInt("id"),x.getString("name"),x.getString("type"),x.getString("region"),x.getDouble("lon"),x.getDouble("lat"),x.optDouble("baseLon",x.getDouble("lon")),x.optDouble("baseLat",x.getDouble("lat")),x.optString("state","FREI"),x.optInt("incidentId",-1),x.optDouble("routeLon",x.getDouble("lon")),x.optDouble("routeLat",x.getDouble("lat")),x.optDouble("routeStartLon",x.getDouble("lon")),x.optDouble("routeStartLat",x.getDouble("lat")),x.optInt("routeStartMinute",0),x.optInt("routeEndMinute",0),x.optInt("workEndMinute",0),staff,x.optInt("condition",100)))
        }
        val ni=mutableListOf<Incident>();val incidentsArray=o.optJSONArray("incidents")?:JSONArray();if(incidentsArray.length()>3000)return false
        for(k in 0 until incidentsArray.length()){val i=incidentsArray.getJSONObject(k);ni.add(Incident(i.getInt("id"),i.getString("title"),i.getString("region"),i.getDouble("lon"),i.getDouble("lat"),i.getInt("priority"),i.getString("requiredType"),i.getInt("minutesNeeded"),i.getInt("createdMinute"),i.optString("status","OFFEN"),i.optInt("assignedUnit",-1),i.optInt("deadlineMinute",45),i.optString("description",""),i.optInt("reward",700)))}
        units.clear();units.addAll(nu);incidents.clear();incidents.addAll(ni)
        mode=o.optString("mode","KARRIERE");activeRegion=o.optString("activeRegion","HH");day=o.optInt("day",1);stage=o.optString("stage","PLANUNG");startHour=o.optInt("startHour",6);duration=o.optInt("duration",480);elapsed=o.optInt("elapsed",0);paused=o.optBoolean("paused",false);speed=o.optInt("speed",1);budget=o.optInt("budget",650000);reputation=o.optInt("reputation",70);successes=o.optInt("successes",0);failures=o.optInt("failures",0);seq=o.optInt("seq",1000);nextCallAt=o.optInt("nextCallAt",10);autoCalls=o.optBoolean("autoCalls",true)
        reports.clear();val reps=o.optJSONArray("reports")?:JSONArray();for(j in 0 until reps.length()){val h=reps.getJSONObject(j);reports.add(ShiftReport(h.getInt("day"),h.getString("region"),h.getInt("length"),h.getInt("startHour"),h.getInt("total"),h.getInt("success"),h.getInt("failed"),h.getInt("handovers"),h.optInt("moneyDelta",0),h.optInt("reputationDelta",0)))}
        logs.clear();val la=o.optJSONArray("logs")?:JSONArray();for(k in 0 until la.length())logs.add(la.optString(k))
        customTemplates.clear();val ca=o.optJSONArray("customTemplates")?:JSONArray();for(k in 0 until ca.length()){val t=ca.getJSONObject(k);customTemplates.add(Triple(t.getString("title"),t.getString("type"),t.getInt("priority")))}
        persist();return true
    }catch(_:Exception){return false}}
    fun persist(){try{context.getSharedPreferences("native_game_v1",Context.MODE_PRIVATE).edit().putString("state",saveJson()).apply()}catch(_:Exception){}}
    private fun restore():Boolean {val raw=context.getSharedPreferences("native_game_v1",Context.MODE_PRIVATE).getString("state",null)?:return false;return importJson(raw)}
    private fun loadRegions():List<Region> {
        val cityMap=JSONObject(context.assets.open("data/cities.json").bufferedReader().use{it.readText()})
        val geo=JSONObject(context.assets.open("data/states.geojson").bufferedReader().use{it.readText()})
        val features=geo.getJSONArray("features");val result=mutableListOf<Region>()
        for(i in 0 until features.length()){
            val f=features.getJSONObject(i);val properties=f.getJSONObject("properties");val code=properties.optString("code",properties.optString("id")).removePrefix("DE-");val g=f.getJSONObject("geometry");val polygons=mutableListOf<List<GeoPoint>>()
            val coords=g.getJSONArray("coordinates");val multi=g.getString("type")=="MultiPolygon"
            val number=if(multi)coords.length() else 1
            for(p in 0 until number){val ringContainer=if(multi)coords.getJSONArray(p) else coords
                val outer=ringContainer.getJSONArray(0);val pts=ArrayList<GeoPoint>(outer.length())
                for(z in 0 until outer.length()){val a=outer.getJSONArray(z);pts.add(GeoPoint(a.getDouble(0),a.getDouble(1)))}
                if(pts.size>=3)polygons.add(pts)
            }
            val all=polygons.flatten();if(all.isEmpty())continue
            val towns=mutableListOf<City>();val cities=cityMap.optJSONArray(code)?:JSONArray();for(n in 0 until cities.length()){val c=cities.getJSONArray(n);towns.add(City(c.getString(0),GeoPoint(c.getDouble(1),c.getDouble(2))))}
            result.add(Region(code,properties.getString("name"),polygons,towns,all.minOf{it.lon},all.minOf{it.lat},all.maxOf{it.lon},all.maxOf{it.lat}))
        }
        return result.sortedBy{it.name}
    }
    companion object {fun haversine(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Double{
        val dLat=Math.toRadians(lat2-lat1);val dLon=Math.toRadians(lon2-lon1)
        val a=sin(dLat/2).pow(2)+cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(dLon/2).pow(2)
        return 6371.0*2*atan2(sqrt(a),sqrt(1-a))
    }}
}
