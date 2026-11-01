package de.einsatzleitstelle110

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlin.math.*

/** Android UI made exclusively with native Views, Canvas and AlertDialogs.
 * NO WebView, HTML, JavaScript, CDN, cloud map service or API token.
 */
class MainActivity:Activity() {
 private val dark=Color.rgb(7,17,27)
 private val panelColor=Color.rgb(15,29,43)
 private val cardColor=Color.rgb(19,36,52)
 private val blue=Color.rgb(74,185,249)
 private val pale=Color.rgb(224,237,246)
 private val muted=Color.rgb(156,177,193)
 private val green=Color.rgb(72,213,164)
 private val red=Color.rgb(251,95,109)
 private val orange=Color.rgb(247,183,82)
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var game:GameController
 private lateinit var map:TacticalMapView
 private lateinit var status:TextView
 private lateinit var body:FrameLayout
 private lateinit var nav:LinearLayout
 private lateinit var audio:NativeAudio
 private var tab="MAP"
 private var lastSaveTick=0
 private var lastCount=0
 private var reportShownDay=0
 private val SAVE=701
 private val LOAD=702
 private fun dp(v:Float)=(resources.displayMetrics.density*v).toInt()
 private fun bg(hex:Int,stroke:Int=0,round:Float=10f):GradientDrawable {
  val g=GradientDrawable();g.setColor(hex);g.cornerRadius=dp(round).toFloat()
  if(stroke!=0)g.setStroke(dp(1f),stroke)
  return g
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=dark;window.navigationBarColor=dark
  val regions=GeoData.load(this)
  val saved=GameStore.load(this)?:GameState()
  if(regions.none{it.code==saved.region})saved.region="DE-HH"
  game=GameController(saved,regions);game.seedUnitsIfNeeded()
  audio=NativeAudio().also{it.muted=saved.mute}
  game.notify={title,description->
    if(title.startsWith("Neuer Notruf"))audio.cue(if(title.contains("P1"))"P1" else "CALL")
    else if(title=="Einheit unterwegs")audio.cue("DISPATCH")
    else if(title=="Einsatz beendet")audio.cue("SUCCESS")
    else if(title=="Einsatz fehlgeschlagen")audio.cue("FAILED")
    else if(title=="Schichtende")audio.cue("SHIFT")
  }
  map=TacticalMapView(this,game,{showIncident(it)},{showUnit(it)})
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(dark)}
  val header=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
   setPadding(dp(13f),dp(7f),dp(9f),dp(7f));setBackgroundColor(panelColor)
  }
  val brand=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  brand.addView(label("◈ EINSATZLEITSTELLE 110",16f,pale,true))
  brand.addView(label("DEUTSCHLAND   ·   NATIVE ANDROID",9.5f,blue,true))
  header.addView(brand,LinearLayout.LayoutParams(0,dp(52f),1f))
  status=label("TAG 1  ·  06:00",11f,green,true)
  header.addView(status)
  header.addView(press("⚙",11f){showSettings()},LinearLayout.LayoutParams(dp(42f),dp(38f)))
  root.addView(header,LinearLayout.LayoutParams(-1,dp(64f)))
  val divider=View(this);divider.setBackgroundColor(Color.rgb(42,73,93))
  root.addView(divider,LinearLayout.LayoutParams(-1,dp(1f)))
  body=FrameLayout(this);root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setBackgroundColor(panelColor)}
  root.addView(nav,LinearLayout.LayoutParams(-1,dp(64f)))
  setContentView(root)
  showTab("MAP")
  if(saved.phase=="DEBRIEF")handler.postDelayed({showDebrief()},350)
  if(saved.phase=="PLANNING"&&saved.day==1)handler.postDelayed({showWelcome()},500)
  handler.post(ticker)
 }
 private val ticker=object:Runnable {
  override fun run(){
   if(!isFinishing){
    val oldPhase=game.state.phase
    if(oldPhase=="RUNNING"&&!game.state.paused)game.advance(game.state.speed)
    if(game.state.phase=="DEBRIEF"&&oldPhase!="DEBRIEF"&&reportShownDay!=game.state.day){
     reportShownDay=game.state.day
     handler.postDelayed({showDebrief()},300)
    }
    refreshHeader()
    if(tab=="MAP")map.invalidate()
    if(++lastSaveTick%7==0)GameStore.save(this@MainActivity,game.state)
    handler.postDelayed(this,1000)
   }
  }
 }
 override fun onPause(){super.onPause();GameStore.save(this,game.state)}
 override fun onDestroy(){handler.removeCallbacks(ticker);audio.close();super.onDestroy()}
 private fun label(txt:String,size:Float=13f,color:Int=pale,bold:Boolean=false)=TextView(this).apply{
  text=txt;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL
  setLineSpacing(dp(2f).toFloat(),1f)
  if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
 }
 private fun press(txt:String,size:Float=12f,click:()->Unit)=TextView(this).apply{
  text=txt;textSize=size;setTextColor(pale);gravity=Gravity.CENTER
  background=bg(Color.rgb(28,57,79),Color.rgb(50,96,123),8f)
  setPadding(dp(8f),dp(7f),dp(8f),dp(7f))
  setOnClickListener{click()}
 }
 private fun card():LinearLayout=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL
  background=bg(cardColor,Color.rgb(44,77,99),12f)
  setPadding(dp(12f),dp(11f),dp(12f),dp(11f))
 }
 private fun addCard(v:LinearLayout,owner:LinearLayout) {
  val lp=LinearLayout.LayoutParams(-1,-2)
  lp.setMargins(dp(10f),dp(5f),dp(10f),dp(5f));owner.addView(v,lp)
 }
 private fun row():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
 private fun spacer(w:Float=8f)=View(this).apply{layoutParams=LinearLayout.LayoutParams(dp(w),1)}
 private fun scrollPanel():LinearLayout {
  val scroll=ScrollView(this).apply{isFillViewport=false;setBackgroundColor(dark)}
  val inner=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(5f),0,dp(24f))}
  scroll.addView(inner)
  body.addView(scroll,FrameLayout.LayoutParams(-1,-1))
  return inner
 }
 private fun refreshHeader(){
  val s=game.state
  status.text="TAG "+s.day+" · "+game.clock()+"\n"+when(s.phase){
   "RUNNING"->(if(s.paused)"PAUSE" else "LIVE ×"+s.speed)
   "DEBRIEF"->"SCHICHTENDE"
   else->"PLANUNG"
  }
  status.setTextColor(if(s.phase=="RUNNING"&&!s.paused)green else orange)
 }
 private fun refresh(){GameStore.save(this,game.state);showTab(tab)}
 private fun showTab(selected:String){
  tab=selected;body.removeAllViews();nav.removeAllViews()
  for((key,name) in listOf("MAP" to "◈ Karte","CALLS" to "⚑ Notrufe",
   "UNITS" to "▣ Einheiten","MANAGE" to "◷ Dienst","EDITOR" to "✎ Editor")){
   val b=press(name,10.8f){showTab(key)}
   b.background=bg(if(key==tab)Color.rgb(31,87,118) else panelColor,
     if(key==tab)blue else Color.rgb(27,50,66),0f)
   b.setTextColor(if(key==tab)Color.WHITE else muted)
   nav.addView(b,LinearLayout.LayoutParams(0,-1,1f))
  }
  when(selected){
   "MAP"->showMap()
   "CALLS"->showCalls()
   "UNITS"->showUnits()
   "MANAGE"->showManage()
   "EDITOR"->showEditor()
  }
  refreshHeader()
 }
 private fun overlay(container:FrameLayout,view:View,gravity:Int,margin:Int=8) {
  val lp=FrameLayout.LayoutParams(-1,-2,gravity)
  lp.setMargins(dp(margin.toFloat()),dp(margin.toFloat()),dp(margin.toFloat()),dp(margin.toFloat()))
  container.addView(view,lp)
 }
 private fun showMap(){
  if(map.parent!=null)(map.parent as ViewGroup).removeView(map)
  body.addView(map,FrameLayout.LayoutParams(-1,-1))
  val top=row().apply{setPadding(dp(4f),dp(4f),dp(4f),dp(4f))}
  val region=game.region()
  top.addView(press("⌖ "+region.name,11f){chooseRegion()},
    LinearLayout.LayoutParams(0,dp(38f),1.7f))
  top.addView(spacer(6f))
  top.addView(press("FILTER: "+map.filter,10f){chooseFilter()},
    LinearLayout.LayoutParams(0,dp(38f),1.3f))
  top.addView(spacer(6f))
  top.addView(press("⛶",16f){map.resetCamera()},
    LinearLayout.LayoutParams(dp(44f),dp(38f)))
  overlay(body,top,Gravity.TOP)
  val bottom=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(dp(11f),dp(9f),dp(11f),dp(9f))
   background=bg(Color.rgb(11,24,37),Color.rgb(46,89,112),10f)
  }
  val line=row()
  val live=game.openIncidents();val free=game.freeUnits()
  line.addView(label("● "+live+" OFFENE EINSÄTZE  ·  "+free+" VERFÜGBAR",11.3f,green,true),
    LinearLayout.LayoutParams(0,dp(40f),1f))
  line.addView(press(if(game.state.phase=="PLANNING")"▶ SCHICHT" else
    if(game.state.phase=="RUNNING")(if(game.state.paused)"▶ WEITER" else "Ⅱ PAUSE")
    else "AUSWERTUNG",11f){
    when(game.state.phase){"PLANNING"->showShiftDialog()
     "RUNNING"->{game.state.paused=!game.state.paused;refresh()}
     else->showDebrief()}
  },LinearLayout.LayoutParams(dp(119f),dp(39f)))
  bottom.addView(line)
  val first=game.state.incidents.firstOrNull{it.region==game.state.region&&it.status=="OPEN"}
  if(first!=null){
   val r=row()
   val color=if(first.priority==1)red else if(first.priority==2)orange else blue
   r.addView(label("P"+first.priority+"  "+first.title+"   ·   "+first.location,11.1f,color,true),
    LinearLayout.LayoutParams(0,dp(28f),1f))
   r.addView(press("Zuweisen",10f){showIncident(first)},LinearLayout.LayoutParams(dp(84f),dp(34f)))
   bottom.addView(r)
  }
  overlay(body,bottom,Gravity.BOTTOM)
 }
 private fun chooseFilter(){
  val values=arrayOf("ALLE","EINSÄTZE","STREIFE","SEK","MEK","KRIPO","RETTUNG","FEUERWEHR")
  AlertDialog.Builder(this).setTitle("Kartenfilter").setItems(values){_,i->
   map.filter=values[i];showTab("MAP")
  }.show()
 }
 private fun chooseRegion(){
  if(game.state.phase=="RUNNING"){toast("Regionenwechsel nur in der Schichtplanung.");return}
  val items=game.regions.map{it.name}.toTypedArray()
  AlertDialog.Builder(this).setTitle("Ganzes Bundesland anzeigen").setItems(items){_,i->
   game.setRegion(game.regions[i].code);map.resetCamera();refresh()
  }.show()
 }
 private fun showCalls(){
  val list=scrollPanel()
  val header=card()
  header.addView(label("NOTRUFE / LEITSTELLE",19f,pale,true))
  header.addView(label("Schicht: "+game.clock()+"  •  "+game.region().name,11f,muted))
  val bar=row()
  bar.addView(press("+ NOTRUF SIMULIEREN",11f){
    if(game.state.phase=="RUNNING"){
     game.newIncident();refresh()
    }else toast("Zuerst die Schicht starten")
  },LinearLayout.LayoutParams(0,dp(42f),1f))
  bar.addView(spacer())
  bar.addView(press("⌖ KARTE",11f){showTab("MAP")},LinearLayout.LayoutParams(dp(95f),dp(42f)))
  header.addView(bar);addCard(header,list)
  val calls=game.state.incidents.filter{it.region==game.state.region}.take(40)
  for(call in calls){
   val b=card();val c=when(call.priority){1->red;2->orange;else->blue}
   b.addView(label("P"+call.priority+"  ·  "+call.title+"     ["+call.id+"]",15f,c,true))
   b.addView(label(call.location+"  ·  "+call.status+"  ·  "+call.required,12f,muted))
   b.addView(label(call.description,11.5f,pale))
   b.addView(press(if(call.status=="OPEN")"EINHEIT ZUWEISEN" else "DETAILS ANZEIGEN",11f){
    showIncident(call)
   },LinearLayout.LayoutParams(-1,dp(37f)))
   addCard(b,list)
  }
  if(calls.isEmpty())addCard(card().apply{addView(label("Keine Einsätze. Starte deine Schicht.",14f,muted))},list)
 }
 private fun showIncident(call:IncidentState){
  val title="P"+call.priority+" · "+call.title
  val d=AlertDialog.Builder(this).setTitle(title)
   .setMessage(call.description+"\n\nOrt: "+call.location+
    "\nBenötigt: "+call.required+"\nStatus: "+call.status+
    "\nSeit: "+call.age+" Sim.-Minuten")
  if(call.status=="OPEN"&&game.state.phase=="RUNNING"){
   d.setPositiveButton("Einheit zuweisen"){_,_->
    val units=game.state.units.filter{it.region==call.region&&it.status=="FREE"&&it.type==call.required}
     .sortedBy{GeoData.km(GeoPoint(it.lon,it.lat),GeoPoint(call.lon,call.lat))}
    if(units.isEmpty()){toast("Keine passende Einheit frei");return@setPositiveButton}
    AlertDialog.Builder(this).setTitle("Welche Einheit entsenden?")
     .setItems(units.map{"◉ "+it.callsign+" • "+
      GeoData.km(GeoPoint(it.lon,it.lat),GeoPoint(call.lon,call.lat)).toInt()+" km"}.toTypedArray()){_,i->
      if(game.dispatch(call,units[i])){map.focus(call.lon,call.lat);showTab("MAP");GameStore.save(this,game.state)}
     }.setNegativeButton("Abbrechen",null).show()
   }
  }
  d.setNeutralButton("Auf Karte"){_,_->map.focus(call.lon,call.lat);showTab("MAP")}
  d.setNegativeButton("Schließen",null).show()
 }
 private fun portrait(n:Int):ImageView{
  val v=ImageView(this)
  try{
   val f="officer_"+n.coerceIn(1,8).toString().padStart(2,'0')+".webp"
   val bm=assets.open(f).use{BitmapFactory.decodeStream(it)}
   v.setImageBitmap(bm)
  }catch(_:Exception){v.setImageResource(android.R.drawable.sym_def_app_icon)}
  v.scaleType=ImageView.ScaleType.CENTER_CROP
  v.background=bg(panelColor,blue,8f)
  return v
 }
 private fun showUnits(){
  val list=scrollPanel()
  val top=card()
  top.addView(label("EINHEITEN / PERSONAL",19f,pale,true))
  top.addView(label(game.region().name+"  ·  "+game.freeUnits()+" frei  ·  "+
    game.state.units.count{it.region==game.state.region}+" insgesamt",11f,muted))
  top.addView(press("+ EINHEIT ERSTELLEN",11f){editUnit()},LinearLayout.LayoutParams(-1,dp(40f)))
  addCard(top,list)
  for(u in game.state.units.filter{it.region==game.state.region}){
   val item=card();val line=row()
   line.addView(portrait(u.officer.portrait),LinearLayout.LayoutParams(dp(61f),dp(65f)))
   line.addView(spacer(10f))
   val details=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   details.addView(label(u.callsign+"   ·   "+u.type,14.2f,pale,true))
   details.addView(label(u.officer.name+"  ·  Stufe "+u.level+"  ·  "+u.crew+" Personen",11f,muted))
   details.addView(label(u.status+(if(u.trainingLeft>0)" · Ausbildung "+u.trainingLeft+" Tage" else ""),
    11f,if(u.status=="FREE")green else orange,true))
   line.addView(details,LinearLayout.LayoutParams(0,-2,1f));item.addView(line)
   item.addView(press("AUSRÜSTUNG / AUSBILDUNG ANSEHEN",11f){showUnit(u)},
    LinearLayout.LayoutParams(-1,dp(40f)))
   addCard(item,list)
  }
 }
 private fun showUnit(u:UnitState){
  AlertDialog.Builder(this).setTitle(u.callsign+" · "+u.type)
   .setMessage("Beamter: "+u.officer.name+"\nBesatzung: "+u.crew+
    "\nStatus: "+u.status+"\nStufe: "+u.level+"  •  Erfahrung: "+u.experience+
    "\nFähigkeit: "+u.officer.skill+"/100\nEnergie: "+u.officer.energy+"/100"+
    "\nAusrüstung: "+u.equipment.joinToString()+
    (if(u.trainingLeft>0)"\nLehrgang: "+u.training+" ("+u.trainingLeft+" Tage)" else ""))
   .setPositiveButton("Ausrüsten"){_,_->equipUnit(u)}
   .setNeutralButton("Ausbilden"){_,_->trainUnit(u)}
   .setNegativeButton("Schließen",null).show()
 }
 private fun equipUnit(u:UnitState){
  val items=GameContent.equipment.filter{it !in u.equipment}
  if(items.isEmpty()){toast("Bereits vollständig ausgerüstet");return}
  AlertDialog.Builder(this).setTitle("Ausrüstung kaufen und anlegen")
   .setItems(items.map{it+" · "+GameContent.prices.getValue(it)+" €"}.toTypedArray()){_,i->
     if(game.buyEquipment(u,items[i])){toast("Ausrüstung angelegt");refresh()}
     else toast("Nicht möglich: Budget oder Ausrüstungsplätze")
   }.show()
 }
 private fun trainUnit(u:UnitState){
  val courses=GameContent.courses.entries.toList()
  AlertDialog.Builder(this).setTitle("Fortbildung · nur in Schichtplanung")
   .setItems(courses.map{it.key+" · "+it.value.first+" Tage · "+it.value.second+" €"}.toTypedArray()){_,i->
    if(game.train(u,courses[i].key)){toast("Lehrgang gestartet");refresh()}
    else toast("Nicht möglich: Schicht, Einheit oder Budget")
   }.show()
 }
 private fun showManage(){
  val list=scrollPanel()
  val s=game.state
  val top=card()
  top.addView(label("DIENST- UND SCHICHTLEITUNG",19f,pale,true))
  top.addView(label("TAG "+s.day+"   ·   "+game.region().name,12f,blue,true))
  top.addView(label("Spielmodus: "+if(s.mode=="CAREER")"KARRIERE" else "SANDBOX",12f,muted))
  top.addView(label("Schicht: "+(s.duration/60)+" Stunden · Beginn "+
     "%02d:00".format(s.startMinute/60)+" · "+game.clock(),12f,pale))
  top.addView(label("Fortschritt: "+s.elapsed+" / "+s.duration+" Sim.-Min.",12f,green))
  val mainAction=when(s.phase){"PLANNING"->"▶ SCHICHT STARTEN";"RUNNING"->"Ⅱ PAUSE / WEITER";else->"TAGESAUSWERTUNG"}
  top.addView(press(mainAction,12f){
    when(s.phase){"PLANNING"->showShiftDialog()
     "RUNNING"->{s.paused=!s.paused;refresh()}
     else->showDebrief()}
  },LinearLayout.LayoutParams(-1,dp(49f)))
  addCard(top,list)
  val shift=card();shift.addView(label("SCHICHTDAUER",16f,pale,true))
  shift.addView(label("8 / 12 / 24 Stunden · Anfangszeit frei wählbar",12f,muted))
  shift.addView(press("SCHICHTPLAN ÄNDERN",11f){showShiftDialog()},
    LinearLayout.LayoutParams(-1,dp(42f)))
  addCard(shift,list)
  val economy=card()
  economy.addView(label("BEHÖRDENSTATUS",16f,pale,true))
  economy.addView(label("Budget    "+s.budget.toString()+" €",14f,green,true))
  economy.addView(label("Reputation    "+s.reputation+"/100",13f,pale))
  economy.addView(label("Einsätze aktuell    "+game.openIncidents(),13f,pale))
  economy.addView(label("Verfügbare Fahrzeuge    "+game.freeUnits(),13f,pale))
  economy.addView(label("Ausbildung    "+s.units.count{it.trainingLeft>0}+" Lehrgänge",13f,pale))
  economy.addView(press("REGION WECHSELN",11f){chooseRegion()},
   LinearLayout.LayoutParams(-1,dp(38f)))
  addCard(economy,list)
  val history=card();history.addView(label("BISHERIGE SCHICHTEN",16f,pale,true))
  for(r in s.reports.take(12)){
   history.addView(label("Tag "+r.day+" · "+r.duration/60+"h: "+
     r.solved+" ✓ / "+r.failed+" ✗ / "+r.handed+" Übergaben",12f,muted))
  }
  addCard(history,list)
 }
 private fun showShiftDialog(){
  if(game.state.phase=="DEBRIEF"){showDebrief();return}
  if(game.state.phase=="RUNNING"){toast("Schicht läuft bereits");return}
  val durs=arrayOf("8 Stunden – Früh / Spät / Nacht","12 Stunden – Tag / Nacht","24 Stunden – ganzer Diensttag")
  AlertDialog.Builder(this).setTitle("Schichtlänge wählen").setItems(durs){_,i->
   val hours=listOf(8,12,24)[i]
   val starts=arrayOf("00:00","06:00","08:00","12:00","14:00","18:00","22:00")
   AlertDialog.Builder(this).setTitle("Beginn "+hours+"h-Schicht")
    .setItems(starts){_,j->
     val hour=starts[j].substring(0,2).toInt()
     game.setShift(hour,hours)
     AlertDialog.Builder(this).setTitle("Schicht starten?")
      .setMessage(game.region().name+"\n"+hours+" Stunden, Beginn "+starts[j]+
       "\nEnde um "+"%02d:00".format((hour+hours)%24)+
       if(hour+hours>=24)" (Folgetag)" else "")
      .setPositiveButton("STARTEN"){_,_->game.startShift();refresh()}
      .setNeutralButton("Nur planen"){_,_->refresh()}
      .setNegativeButton("Abbrechen",null).show()
    }.show()
  }.show()
 }
 private fun showDebrief(){
  val s=game.state
  val r=s.reports.firstOrNull()
  if(r==null){toast("Noch keine Schicht beendet");return}
  val report="Tag "+r.day+"  ·  "+r.duration/60+" Stunden\n\n"+
    "Anrufe insgesamt       "+r.served+"\n"+
    "Erfolgreich                 "+r.solved+"\n"+
    "Fehlgeschlagen          "+r.failed+"\n"+
    "Übergeben                    "+r.handed+"\n\n"+
    "Einnahmen                  +"+r.income+" €\n"+
    "Personal / Betrieb      -"+r.expenses+" €\n"+
    "Reputationsänderung      "+r.repDelta+"\n\n"+
    "Ausbildungen und Ausrüstung bleiben erhalten."
  AlertDialog.Builder(this).setTitle("SCHICHTENDE · AUSWERTUNG")
   .setMessage(report)
   .setPositiveButton("NÄCHSTEN TAG"){_,_->s.phase="DEBRIEF";game.nextDay();refresh()}
   .setNegativeButton("Schließen",null).show()
 }
 private fun showEditor(){
  val list=scrollPanel()
  val a=card();a.addView(label("SANDBOX / EINSATZEDITOR",18f,pale,true))
  a.addView(label("Eigene Notrufe und Fahrzeuggruppen erstellen. Alles bleibt lokal gespeichert.",12f,muted))
  a.addView(press("+ EIGENEN EINSATZ ERSTELLEN",11f){editScenario()},
   LinearLayout.LayoutParams(-1,dp(43f)))
  a.addView(press("+ EIGENE EINHEIT ERSTELLEN",11f){editUnit()},
   LinearLayout.LayoutParams(-1,dp(43f)))
  addCard(a,list)
  val b=card();b.addView(label("EINSATZVORLAGEN",16f,pale,true))
  b.addView(label(game.allTemplates().size.toString()+" Datenvorlagen",12f,muted))
  for(t in game.state.customTemplates){
   b.addView(press("▶ "+t.title+"   ·   P"+t.priority){if(game.state.phase=="RUNNING"){
    game.newIncident(t);refresh()
   }else toast("Zuerst die Schicht starten")},LinearLayout.LayoutParams(-1,dp(39f)))
  }
  addCard(b,list)
  val c=card()
  c.addView(label("SICHERUNG / DATEN",16f,pale,true))
  c.addView(press("SPIELSTAND ALS JSON EXPORTIEREN",11f){exportSave()},
   LinearLayout.LayoutParams(-1,dp(43f)))
  c.addView(press("SPIELSTAND IMPORTIEREN",11f){importSave()},
   LinearLayout.LayoutParams(-1,dp(43f)))
  addCard(c,list)
 }
 private fun editScenario(){
  val box=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(dp(16f),dp(6f),dp(16f),0)
  }
  val name=EditText(this).apply{hint="Einsatzname";setSingleLine(true)}
  val desc=EditText(this).apply{hint="Notruftext / Beschreibung";setSingleLine(false)}
  val pr=Spinner(this);pr.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
    arrayOf("P1 KRITISCH","P2 HOCH","P3 NORMAL"))
  val ty=Spinner(this);val types=arrayOf("STREIFE","SEK","MEK","KRIPO","RETTUNG","FEUERWEHR")
  ty.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,types)
  val dur=EditText(this).apply{hint="Dauer (Minuten, 1–240)";setText("25")
   inputType=android.text.InputType.TYPE_CLASS_NUMBER}
  box.addView(name);box.addView(desc);box.addView(pr);box.addView(ty);box.addView(dur)
  AlertDialog.Builder(this).setTitle("Eigenen Notruf erstellen").setView(box)
   .setPositiveButton("SPEICHERN"){_,_->
    val template=IncidentTemplate(name.text.toString(),pr.selectedItemPosition+1,
     types[ty.selectedItemPosition],dur.text.toString().toIntOrNull()?:25,desc.text.toString())
    if(game.addTemplate(template)){toast("Vorlage gespeichert");refresh()}
    else toast("Ungültige Eingabe")
   }.setNegativeButton("Abbrechen",null).show()
 }
 private fun editUnit(){
  val types=arrayOf("STREIFE","SEK","MEK","KRIPO","RETTUNG","FEUERWEHR")
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16f),dp(6f),dp(16f),0)}
  val name=EditText(this).apply{hint="Funkrufname, z. B. Peter 11/1";setSingleLine()}
  val ty=Spinner(this);ty.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,types)
  val cities=game.region().cities
  val loc=Spinner(this)
  loc.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,cities.map{it.name})
  box.addView(name);box.addView(ty);box.addView(label("Standort / Wache",12f,muted));box.addView(loc)
  AlertDialog.Builder(this).setTitle("Neue Einsatzgruppe").setView(box)
   .setPositiveButton("ERSTELLEN"){_,_->
    if(cities.isNotEmpty()&&game.addUnit(name.text.toString(),types[ty.selectedItemPosition],
        cities[loc.selectedItemPosition])){toast("Einheit hinzugefügt");refresh()}
    else toast("Nicht möglich: Name oder Budget")
   }.setNegativeButton("Abbrechen",null).show()
 }
 private fun showSettings(){
  val s=game.state
  val options=arrayOf(
   "Spielmodus: "+if(s.mode=="CAREER")"KARRIERE" else "SANDBOX",
   "Notrufe automatisch: "+if(s.autoCalls)"AN" else "AUS",
   "Sound: "+if(s.mute)"AUS" else "AN",
   "Spieltempo: ×"+s.speed,
   "Ganztageskarte / Zoom zurücksetzen",
   "Spielstand sichern / laden",
   "Neues Spiel starten"
  )
  AlertDialog.Builder(this).setTitle("Einstellungen").setItems(options){_,i->
   when(i){
    0->{
     if(s.phase=="RUNNING")toast("Moduswechsel nur zwischen Schichten")
     else{s.mode=if(s.mode=="CAREER")"SANDBOX" else "CAREER";refresh()}
    }
    1->{s.autoCalls=!s.autoCalls;refresh()}
    2->{s.mute=!s.mute;audio.muted=s.mute;refresh()}
    3->{val speeds=intArrayOf(1,4,12,30)
     AlertDialog.Builder(this).setTitle("Spielminuten pro Sekunde")
       .setItems(speeds.map{"×"+it}.toTypedArray()){_,j->s.speed=speeds[j];refresh()}.show()}
    4->{map.resetCamera();showTab("MAP")}
    5->{AlertDialog.Builder(this).setItems(arrayOf("Export","Import")){_,j->
      if(j==0)exportSave()else importSave()
     }.show()}
    6->{AlertDialog.Builder(this).setTitle("Neues Spiel?")
     .setMessage("Gespeicherter Fortschritt wird überschrieben.")
     .setPositiveButton("NEUSTART"){_,_->
      game=GameController(GameState(),GeoData.all);game.seedUnitsIfNeeded()
      map=TacticalMapView(this,game,{showIncident(it)},{showUnit(it)})
      audio.muted=false;refresh();showWelcome()
     }.setNegativeButton("Abbrechen",null).show()}
   }
  }.show()
 }
 private fun showWelcome(){
  AlertDialog.Builder(this).setTitle("EINSATZLEITSTELLE 110 · V1")
   .setMessage("Native Android-Leitstellensimulation ohne WebView.\n\n"+
    "Die Karte zeigt die GESAMTE gewählte Landesgrenze mit Einsatzfahrzeugen und Notrufen. "+
    "Das Wegenetz und die Fahrzeiten sind spielerisch simuliert.\n\n"+
    "Spiele Karriere mit Budget oder Sandbox mit freien Ressourcen.")
   .setPositiveButton("KARRIERE"){_,_->game.state.mode="CAREER";refresh();showShiftDialog()}
   .setNeutralButton("SANDBOX"){_,_->game.state.mode="SANDBOX";refresh();showShiftDialog()}
   .setNegativeButton("Später",null).show()
 }
 private fun exportSave(){
  val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE);type="application/json"
   putExtra(Intent.EXTRA_TITLE,"Einsatzleitstelle110-NativeV1-Spielstand.json")
  }
  startActivityForResult(i,SAVE)
 }
 private fun importSave(){
  val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE);type="application/json"
  }
  startActivityForResult(i,LOAD)
 }
 @Deprecated("Kept for API 26 support; native SAF, not WebView")
 override fun onActivityResult(req:Int,res:Int,data:Intent?){
  super.onActivityResult(req,res,data)
  val uri=data?.data?:return
  if(res!=RESULT_OK)return
  try{
   if(req==SAVE){
    contentResolver.openOutputStream(uri)?.use{
      it.write(GameStore.toJson(game.state).toString(2).toByteArray(Charsets.UTF_8))
    }
    toast("Spielstand exportiert")
   }else if(req==LOAD){
    val json=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}
      ?:throw IllegalArgumentException("Leerer Spielstand")
    val restored=GameStore.fromJson(org.json.JSONObject(json))
    if(GeoData.all.none{it.code==restored.region})throw IllegalArgumentException("Unbekanntes Bundesland")
    game=GameController(restored,GeoData.all);game.seedUnitsIfNeeded()
    map=TacticalMapView(this,game,{showIncident(it)},{showUnit(it)})
    audio.muted=game.state.mute;refresh()
    toast("Spielstand importiert")
   }
  }catch(e:Exception){toast("Fehler beim Import/Export: "+e.message)}
 }
 private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
 override fun onBackPressed(){
  if(tab!="MAP")showTab("MAP")
  else AlertDialog.Builder(this).setTitle("Spiel verlassen?")
   .setMessage("Dein Spielstand wird automatisch gesichert.")
   .setPositiveButton("Beenden"){_,_->GameStore.save(this,game.state);finish()}
   .setNegativeButton("Weiterspielen",null).show()
 }
}
