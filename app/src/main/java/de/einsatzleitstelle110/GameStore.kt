package de.einsatzleitstelle110

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Versioned offline persistence for the native Android engine. */
object GameStore {
 private const val FILE="einsatzleitstelle-native-v1.json"
 fun save(context:Context,state:GameState) {
  val out=toJson(state).toString()
  val temp="einsatzleitstelle.tmp"
  context.openFileOutput(temp,Context.MODE_PRIVATE).use{it.write(out.toByteArray(Charsets.UTF_8))}
  val base=context.getFileStreamPath(FILE)
  val from=context.getFileStreamPath(temp)
  if(!from.renameTo(base)) {
   context.openFileOutput(FILE,Context.MODE_PRIVATE).use{it.write(out.toByteArray(Charsets.UTF_8))}
   from.delete()
  }
 }
 fun load(context:Context):GameState? = try {
  val json=context.openFileInput(FILE).bufferedReader().use{it.readText()}
  fromJson(JSONObject(json))
 }catch(_:Exception){null}
 fun toJson(s:GameState):JSONObject {
  val root=JSONObject()
  root.put("schemaVersion",4)
  root.put("region",s.region).put("mode",s.mode).put("day",s.day).put("phase",s.phase)
   .put("startMinute",s.startMinute).put("duration",s.duration).put("elapsed",s.elapsed)
   .put("speed",s.speed).put("paused",s.paused).put("budget",s.budget)
   .put("reputation",s.reputation).put("shiftIncoming",s.shiftIncoming)
   .put("shiftSolved",s.shiftSolved).put("shiftFailed",s.shiftFailed)
   .put("shiftHanded",s.shiftHanded).put("shiftIncome",s.shiftIncome)
   .put("nextId",s.nextId).put("autoCalls",s.autoCalls).put("mute",s.mute)
  val units=JSONArray()
  for(u in s.units){
   val o=JSONObject().put("id",u.id).put("callsign",u.callsign).put("type",u.type)
     .put("region",u.region).put("lat",u.lat).put("lon",u.lon).put("crew",u.crew)
     .put("level",u.level).put("experience",u.experience).put("status",u.status)
     .put("incidentId",u.incidentId).put("startLat",u.startLat).put("startLon",u.startLon)
     .put("destLat",u.destLat).put("destLon",u.destLon).put("travelTotal",u.travelTotal)
     .put("travelLeft",u.travelLeft).put("workLeft",u.workLeft)
     .put("training",u.training).put("trainingLeft",u.trainingLeft)
     .put("equipment",JSONArray(u.equipment.toList()))
     .put("officer",JSONObject().put("name",u.officer.name).put("skill",u.officer.skill)
       .put("energy",u.officer.energy).put("experience",u.officer.experience)
       .put("portrait",u.officer.portrait))
   units.put(o)
  }
  root.put("units",units)
  val incidents=JSONArray()
  for(i in s.incidents){
   incidents.put(JSONObject().put("id",i.id).put("title",i.title).put("location",i.location)
     .put("region",i.region).put("lat",i.lat).put("lon",i.lon)
     .put("priority",i.priority).put("required",i.required)
     .put("duration",i.duration).put("description",i.description)
     .put("status",i.status).put("age",i.age).put("assignedUnit",i.assignedUnit))
  }
  root.put("incidents",incidents)
  val templates=JSONArray()
  for(t in s.customTemplates){
   templates.put(JSONObject().put("title",t.title).put("priority",t.priority)
     .put("required",t.required).put("duration",t.duration).put("description",t.description))
  }
  root.put("customTemplates",templates)
  val reports=JSONArray()
  for(r in s.reports) {
   reports.put(JSONObject().put("day",r.day).put("region",r.region)
    .put("duration",r.duration).put("served",r.served).put("solved",r.solved)
    .put("failed",r.failed).put("handed",r.handed)
    .put("income",r.income).put("expenses",r.expenses).put("repDelta",r.repDelta))
  }
  root.put("reports",reports)
  return root
 }
 fun fromJson(root:JSONObject):GameState {
  require(root.optInt("schemaVersion",0) in 3..4) {"Ungültiger Spielstand"}
  val s=GameState(
   region=root.optString("region","DE-HH"), mode=root.optString("mode","CAREER"),
   day=root.optInt("day",1).coerceAtLeast(1),phase=root.optString("phase","PLANNING"),
   startMinute=root.optInt("startMinute",360),duration=root.optInt("duration",480),
   elapsed=root.optInt("elapsed",0),speed=root.optInt("speed",4),
   paused=root.optBoolean("paused"),budget=root.optInt("budget",240000),
   reputation=root.optInt("reputation",74),shiftIncoming=root.optInt("shiftIncoming"),
   shiftSolved=root.optInt("shiftSolved"),shiftFailed=root.optInt("shiftFailed"),
   shiftHanded=root.optInt("shiftHanded"),shiftIncome=root.optInt("shiftIncome"),
   nextId=root.optInt("nextId",1),autoCalls=root.optBoolean("autoCalls",true),
   mute=root.optBoolean("mute",false)
  )
  if(s.duration !in listOf(480,720,1440))s.duration=480
  val units=root.optJSONArray("units")?:JSONArray()
  for(j in 0 until units.length()){
   val v=units.optJSONObject(j)?:continue
   val o=v.optJSONObject("officer")?:JSONObject()
   val officer=Officer(o.optString("name","P. Müller"),o.optInt("skill",58),
    o.optInt("energy",100),o.optInt("experience"),o.optInt("portrait",1))
   val eq=mutableSetOf<String>();val arr=v.optJSONArray("equipment")?:JSONArray()
   for(k in 0 until arr.length())eq.add(arr.optString(k))
   val u=UnitState(v.optString("id"),v.optString("callsign"),v.optString("type"),
     v.optString("region"),v.optDouble("lat"),v.optDouble("lon"),
     v.optInt("crew",2),v.optInt("level",1),v.optInt("experience"),officer,eq,
     v.optString("status","FREE"),v.optString("incidentId"),
     v.optDouble("startLat",v.optDouble("lat")),v.optDouble("startLon",v.optDouble("lon")),
     v.optDouble("destLat",v.optDouble("lat")),v.optDouble("destLon",v.optDouble("lon")),
     v.optInt("travelTotal"),v.optInt("travelLeft"),v.optInt("workLeft"),
     v.optString("training"),v.optInt("trainingLeft"))
   s.units.add(u)
  }
  val incidents=root.optJSONArray("incidents")?:JSONArray()
  for(j in 0 until incidents.length()){
   val v=incidents.optJSONObject(j)?:continue
   s.incidents.add(IncidentState(v.optString("id"),v.optString("title"),
    v.optString("location"),v.optString("region"),v.optDouble("lat"),
    v.optDouble("lon"),v.optInt("priority",3),v.optString("required","STREIFE"),
    v.optInt("duration",15),v.optString("description"),v.optString("status","OPEN"),
    v.optInt("age"),v.optString("assignedUnit")))
  }
  val templates=root.optJSONArray("customTemplates")?:JSONArray()
  for(j in 0 until templates.length()){
   val v=templates.optJSONObject(j)?:continue
   s.customTemplates.add(IncidentTemplate(v.optString("title"),v.optInt("priority",3),
     v.optString("required","STREIFE"),v.optInt("duration",15),v.optString("description")))
  }
  val reports=root.optJSONArray("reports")?:JSONArray()
  for(j in 0 until reports.length()) {
   val v=reports.optJSONObject(j)?:continue
   s.reports.add(ShiftReport(v.optInt("day"),v.optString("region"),
    v.optInt("duration"),v.optInt("served"),v.optInt("solved"),v.optInt("failed"),
    v.optInt("handed"),v.optInt("income"),v.optInt("expenses"),v.optInt("repDelta")))
  }
  return s
 }
}
