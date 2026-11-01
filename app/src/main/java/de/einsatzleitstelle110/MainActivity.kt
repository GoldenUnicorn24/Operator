package de.einsatzleitstelle110

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.InputType
import android.widget.LinearLayout.LayoutParams
import java.util.Locale
import kotlin.math.max

/** Entire interface is native Android Views. No WebView, browser, JS runtime or HTML UI. */
class MainActivity:Activity(){
    private lateinit var game:SimEngine
    private lateinit var sound:NativeAudio
    private lateinit var root:LinearLayout
    private lateinit var mapView:OperatorMapView
    private var activeTab="KARTE"
    private var headerTime:TextView?=null
    private var headerStats:TextView?=null
    private var mapTicker:TextView?=null
    private var mapFeed:LinearLayout?=null
    private var mapControls:LinearLayout?=null
    private val handler=Handler(Looper.getMainLooper())
    private val bg=0xff081421.toInt();private val panel=0xff142537.toInt();private val border=0xff29435a.toInt()
    private val ice=0xffe6f2ff.toInt();private val muted=0xffa7bdcf.toInt();private val cyan=0xff45baff.toInt();private val mint=0xff5edfb0.toInt();private val red=0xffff5e71.toInt();private val amber=0xffffc45e.toInt()
    private var selectedDuration=8;private var selectedHour=6
    private var pinPoint:GeoPoint?=null
    private val ticker=object:Runnable {override fun run(){if(game.isRunning&&!game.paused){val old=game.liveIncidents.size;val prior=game.stage;game.tick(game.speed)
        if(game.liveIncidents.size>old)sound.play("call")
        if(prior!=game.stage&&game.isDebrief){sound.play("shift");showTab("SCHICHT");showReport()}
    }
    headerTime?.text="TAG ${game.day}  ·  ${game.clock}"
    headerStats?.text="${game.liveIncidents.size} EINSÄTZE     ${game.available} FREI     ${money(game.budget)} €"
    mapViewIfReady()?.invalidate();if(activeTab=="KARTE")updateMapFeed()
    handler.postDelayed(this,1000)
    }}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        window.statusBarColor=bg;window.navigationBarColor=bg
        game=SimEngine(this);sound=NativeAudio(this)
        selectedDuration=game.duration/60;selectedHour=game.startHour
        buildRoot();showTab("KARTE");handler.postDelayed(ticker,1000)
        if(game.isPlanning)handler.postDelayed({showShiftDialog(true)},350)
        if(game.isDebrief)handler.postDelayed({showReport()},350)
    }
    override fun onPause(){super.onPause();game.persist()}
    override fun onDestroy(){handler.removeCallbacks(ticker);sound.release();super.onDestroy()}
    private fun mapViewIfReady():OperatorMapView?=if(::mapView.isInitialized && mapView.parent!=null)mapView else null
    private fun dp(value:Float)= (value*resources.displayMetrics.density+.5f).toInt()
    private fun rounded(color:Int,radius:Int=12,stroke:Int?=border):GradientDrawable=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(color);cornerRadius=dp(radius.toFloat()).toFloat();if(stroke!=null)setStroke(dp(1f),stroke)}
    private fun txt(s:String,size:Float=13f,color:Int=ice,bold:Boolean=false):TextView=TextView(this).apply{text=s;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL;includeFontPadding=false;if(bold)typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD);setPadding(dp(2f),dp(4f),dp(2f),dp(4f))}
    private fun vline()=View(this).apply{setBackgroundColor(0xff294052.toInt());layoutParams=LayoutParams(-1,dp(1f))}
    private fun horizontal():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun vertical():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun container(padding:Int=10):LinearLayout=vertical().apply{setPadding(dp(padding.toFloat()),dp(padding.toFloat()),dp(padding.toFloat()),dp(padding.toFloat()));background=rounded(panel)}
    private fun button(title:String,action:()->Unit,kind:Int=0):Button=Button(this).apply{
        text=title;textSize=11f;isAllCaps=false;setTextColor(if(kind==0)ice else if(kind==1)0xff071621.toInt() else cyan)
        background=rounded(if(kind==1)cyan else if(kind==2)0xff12253b.toInt() else 0xff1b354b.toInt(),9,if(kind==1)null else border)
        minHeight=dp(42f);minimumHeight=dp(42f);setPadding(dp(9f),0,dp(9f),0)
        setOnClickListener{sound.play("click");action()}
    }
    private fun addMargin(parent:LinearLayout,child:View,top:Int=5,bottom:Int=5){val p=LayoutParams(-1,LayoutParams.WRAP_CONTENT);p.topMargin=dp(top.toFloat());p.bottomMargin=dp(bottom.toFloat());parent.addView(child,p)}
    private fun btnRow(parent:LinearLayout,vararg entries:Pair<String,()->Unit>){val row=horizontal();entries.forEach{(s,a)->val b=button(s,a);val p=LayoutParams(0,dp(44f),1f);p.setMargins(dp(2f),0,dp(2f),0);row.addView(b,p)};addMargin(parent,row,6,5)}
    private fun money(n:Int)=String.format(Locale.GERMANY,"%,d",n)
    private fun notice(message:String){Toast.makeText(this,message,Toast.LENGTH_SHORT).show()}
    private fun buildRoot(){root=vertical();root.setBackgroundColor(bg);setContentView(root)}
    private fun drawShell(){root.removeAllViews();mapFeed=null;headerTime=null;headerStats=null
        val header=horizontal().apply{setPadding(dp(12f),dp(4f),dp(12f),dp(4f));setBackgroundColor(0xff0c1e2c.toInt())}
        val brand=vertical();brand.addView(txt("◉  EINSATZLEITSTELLE 110",18f,ice,true));brand.addView(txt("DEUTSCHLAND   /   NATIVE EDITION",9f,cyan,true));header.addView(brand,LayoutParams(0,dp(51f),1f))
        headerTime=txt("TAG ${game.day} · ${game.clock}",11f,mint,true);header.addView(headerTime,LayoutParams(-2,dp(51f)))
        root.addView(header,LayoutParams(-1,dp(58f)))
        headerStats=txt("${game.liveIncidents.size} EINSÄTZE     ${game.available} FREI      ${money(game.budget)} €",12f,ice,true)
        headerStats?.apply{gravity=Gravity.CENTER;setBackgroundColor(0xff193146.toInt())}
        root.addView(headerStats,LayoutParams(-1,dp(30f)))
        val content=vertical();content.tag="body";root.addView(content,LayoutParams(-1,0,1f))
        val bottom=horizontal().apply{setPadding(dp(2f),dp(2f),dp(2f),dp(2f));setBackgroundColor(0xff101f30.toInt())}
        val tabs=listOf("KARTE" to "▣ KARTE","EINSÄTZE" to "◉ LAGEN","EINHEITEN" to "▤ KRÄFTE","PERSONAL" to "♙ PERSONAL","EDITOR" to "+ EDITOR","SCHICHT" to "◷ SCHICHT")
        val navScroller=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;isFillViewport=true}
        for((name,title)in tabs){val b=button(title,{showTab(name)},if(activeTab==name)1 else 2);bottom.addView(b,LayoutParams(dp(if(name=="EINHEITEN"||name=="PERSONAL")94f else 89f),dp(53f)))}
        navScroller.addView(bottom);root.addView(navScroller,LayoutParams(-1,dp(57f)))
    }
    private fun body():LinearLayout=root.findViewWithTag("body")
    private fun showTab(tab:String){activeTab=tab;drawShell();val content=body()
        when(tab){"KARTE"->renderMap(content);"EINSÄTZE"->renderIncidentList(content);"EINHEITEN"->renderUnitList(content);"PERSONAL"->renderPersonnel(content);"EDITOR"->renderEditor(content);"SCHICHT"->renderShift(content)}
    }
    private fun mapTool(parent:LinearLayout,title:String,action:()->Unit){val b=button(title,action,2);parent.addView(b,LayoutParams(dp(45f),dp(46f)))}
    private fun renderMap(parent:LinearLayout){
        val toolbar=horizontal().apply{setPadding(dp(7f),dp(3f),dp(7f),dp(3f));setBackgroundColor(0xff0e1f2e.toInt())}
        val names=game.regions.map{it.name};val dropdown=Spinner(this)
        dropdown.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names)
        dropdown.setSelection(game.regions.indexOfFirst{it.code==game.activeRegion},false)
        dropdown.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){val code=game.regions[position].code;if(game.activeRegion!=code){if(game.isRunning){notice("Bundeslandwechsel nach Schichtende");dropdown.setSelection(game.regions.indexOfFirst{it.code==game.activeRegion});return};game.setRegion(code);showTab("KARTE")}}
        }
        toolbar.addView(dropdown,LayoutParams(0,dp(46f),1f));toolbar.addView(button("⌖ ALLES",{mapView.fitRegion()},1),LayoutParams(dp(79f),dp(43f)))
        toolbar.addView(button("FILTER",{showMapFilter()},2),LayoutParams(dp(73f),dp(43f)))
        parent.addView(toolbar,LayoutParams(-1,dp(51f)))
        val frame=FrameLayout(this)
        mapView=OperatorMapView(this).apply{bind(game);onIncidentClicked={showIncidentDetails(it)};onUnitClicked={showUnitDetail(it)};onPositionLongPress={p->pinPoint=p;showPositionDialog(p)};onBackgroundClicked={}}
        frame.addView(mapView,FrameLayout.LayoutParams(-1,-1))
        val zooms=vertical();zoomies(zooms)
        val zoomLayout=FrameLayout.LayoutParams(dp(48f),dp(205f),Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply{rightMargin=dp(8f)}
        frame.addView(zooms,zoomLayout)
        parent.addView(frame,LayoutParams(-1,0,1f))
        val feed=vertical().apply{setBackgroundColor(0xff0e1d2b.toInt());setPadding(dp(8f),dp(5f),dp(8f),dp(5f))}
        mapTicker=txt("DISPOSITION    /    LAGENÜBERSICHT",12f,cyan,true)
        feed.addView(mapTicker);mapFeed=vertical();feed.addView(mapFeed,LayoutParams(-1,-2))
        parent.addView(feed,LayoutParams(-1,dp(157f)))
        updateMapFeed()
    }
    private fun zoomies(c:LinearLayout){c.addView(button("+",{mapView.zoomIn()},2),LayoutParams(dp(47f),dp(48f)));c.addView(button("−",{mapView.zoomOut()},2),LayoutParams(dp(47f),dp(48f)))
        c.addView(button("⌖",{mapView.fitRegion()},2),LayoutParams(dp(47f),dp(48f)));c.addView(button("OSM",{mapView.toggleTiles();notice(if(mapView.onlineTiles)"Online-Straßenkarte" else "Offline-Landeskarte")},2),LayoutParams(dp(47f),dp(48f)))}
    private fun updateMapFeed(){val box=mapFeed?:return;val info=game.liveIncidents
        mapTicker?.text="DISPOSITION     ${info.size} AKTIVE LAGEN     ·     ${game.region.name.uppercase(Locale.GERMANY)}"
        box.removeAllViews();val row=horizontal()
        if(game.isPlanning){row.addView(button("▶ SCHICHT STARTEN",{showShiftDialog(true)},1),LayoutParams(0,dp(45f),1f));row.addView(button("+ EINHEIT",{showUnitEditor()},2),LayoutParams(0,dp(45f),1f));box.addView(row);return}
        if(game.isDebrief){row.addView(button("TAGESAUSWERTUNG →",{showReport()},1),LayoutParams(-1,dp(45f)));box.addView(row);return}
        row.addView(button(if(game.paused)"▶" else "Ⅱ",{game.togglePause()},2),LayoutParams(0,dp(44f),0.7f))
        row.addView(button("${game.speed}×",{val s=if(game.speed==1)4 else if(game.speed==4)8 else if(game.speed==8)16 else 1;game.setSpeed(s);updateMapFeed()},2),LayoutParams(0,dp(44f),0.7f))
        row.addView(button("+ NOTRUF",{game.simulateCall();sound.play("call");updateMapFeed();mapView.invalidate()},1),LayoutParams(0,dp(44f),1.3f))
        row.addView(button("SCHICHTENDE",{endShiftDialog()},2),LayoutParams(0,dp(44f),1.25f));box.addView(row)
        val active=info.take(2)
        for(i in active){val line=horizontal();line.addView(txt("P${i.priority}  ${i.title.take(22)} · ${i.status}",11f,if(i.priority==1)red else if(i.priority==2)amber else ice,true),LayoutParams(0,dp(29f),1f))
            line.addView(button("ZUWEISEN",{showIncidentDetails(i)},2),LayoutParams(dp(93f),dp(31f)));box.addView(line)}
        if(active.isEmpty())box.addView(txt("Keine offene Lage – Schicht läuft.  Tippe auf + NOTRUF.",11f,muted))
    }
    private fun showMapFilter(){val opts=(listOf("ALLE")+Catalog.unitTypes).map{if(it=="ALLE")"Alle Einsatzfahrzeuge" else it}
        AlertDialog.Builder(this).setTitle("Kartenfilter – Einheiten").setSingleChoiceItems(opts.toTypedArray(),(listOf("ALLE")+Catalog.unitTypes).indexOf(mapView.selectedType)){d,idx->mapView.selectedType=(listOf("ALLE")+Catalog.unitTypes)[idx];mapView.invalidate();d.dismiss()}
            .setNeutralButton(if(mapView.onlineTiles)"Onlinekarte aus" else "Onlinekarte an"){_,_->mapView.toggleTiles()}.setPositiveButton("Fertig",null).show()}
    private fun showIncidentDetails(i:Incident){val options=game.activeUnits.filter{it.state=="FREI"&&(it.type==i.requiredType||(i.requiredType in listOf("STREIFE","VERKEHR")&&it.type in listOf("STREIFE","VERKEHR")))}
        val card=vertical().apply{setPadding(dp(18f),dp(12f),dp(18f),dp(12f))}
        card.addView(txt("PRIORITÄT ${i.priority}     ${i.requiredType}",13f,if(i.priority==1)red else amber,true))
        card.addView(txt(i.description,13f,ice));card.addView(txt("Status: ${i.status}  ·  Einsatz #${i.id}",12f,muted))
        card.addView(txt("${"%.3f".format(Locale.US,i.lat)}, ${"%.3f".format(Locale.US,i.lon)}",11f,muted))
        val dlg=AlertDialog.Builder(this).setTitle(i.title).setView(card).setNegativeButton("Karte",{_,_->if(activeTab!="KARTE")showTab("KARTE");mapView.focus(GeoPoint(i.lon,i.lat))}).setNeutralButton("Schließen",null)
        if(i.status=="OFFEN"&&game.isRunning){dlg.setPositiveButton("Einheit zuweisen"){_,_->
            if(options.isEmpty()){notice("Keine passende freie Einheit");return@setPositiveButton}
            val names=options.map{u->val km=SimEngine.haversine(u.lat,u.lon,i.lat,i.lon).toInt();"${u.callsign} · $km km · ${u.type}"}
            AlertDialog.Builder(this).setTitle("Geeignete Kräfte").setItems(names.toTypedArray()){_,which->val u=options[which];if(game.dispatch(i.id,u.id)){sound.play("dispatch");notice("${u.callsign} auf Anfahrt");if(activeTab=="KARTE"){mapView.selectedUnitId=u.id;mapView.invalidate()};updateMapFeed()}else notice("Disposition nicht möglich")}.setNegativeButton("Abbrechen",null).show()
        }}
        dlg.show()
    }
    private fun showPositionDialog(pos:GeoPoint){val r=game.region
        if(!r.contains(pos)){notice("Außerhalb von ${r.name}");return}
        AlertDialog.Builder(this).setTitle("Kartenposition").setMessage("${"%.4f".format(Locale.US,pos.lat)}, ${"%.4f".format(Locale.US,pos.lon)}\n\nHier einen eigenen Einsatz oder eine Einheit anlegen?")
            .setPositiveButton("Neuer Einsatz"){_,_->showIncidentEditor(pos)}.setNeutralButton("Neue Einheit"){_,_->showUnitEditor(pos)}.setNegativeButton("Abbrechen",null).show()
    }
    private fun scrollBody(parent:LinearLayout):LinearLayout{val scroll=ScrollView(this).apply{isFillViewport=true};val column=vertical().apply{setPadding(dp(10f),dp(10f),dp(10f),dp(12f))};scroll.addView(column);parent.addView(scroll,LayoutParams(-1,-1));return column}
    private fun section(parent:LinearLayout,title:String,sub:String=""){addMargin(parent,txt(title,18f,ice,true),6,2);if(sub.isNotBlank())addMargin(parent,txt(sub,11f,muted),0,7)}
    private fun renderIncidentList(parent:LinearLayout){val list=scrollBody(parent);section(list,"EINSÄTZE · ${game.region.name}","Prioritäten, Lage und passende Kräfte. Disposition direkt per Antippen.")
        if(game.liveIncidents.isEmpty())addMargin(list,txt("Keine offenen Einsätze. Über KARTE kannst du Notrufe simulieren.",13f,muted))
        for(i in game.liveIncidents){val card=container(12);val line=horizontal();line.addView(txt("P${i.priority}   ${i.title}",16f,if(i.priority==1)red else if(i.priority==2)amber else ice,true),LayoutParams(0,dp(38f),1f));line.addView(txt(i.status,10f,mint,true));card.addView(line)
            card.addView(txt("${i.requiredType}   ·   ${i.minutesNeeded} Min Bearbeitung   ·   #${i.id}",11f,muted))
            card.addView(txt(i.description,12f,ice));btnRow(card,"Einsatz öffnen" to {showIncidentDetails(i)},"Karte" to {showTab("KARTE");mapView.focus(GeoPoint(i.lon,i.lat))})
            addMargin(list,card)
        }
        section(list,"FUNKPROTOKOLL");for(line in game.logs.take(12))addMargin(list,txt(line,11f,muted),1,1)
    }
    private fun renderUnitList(parent:LinearLayout){val list=scrollBody(parent);section(list,"EINHEITEN · ${game.region.name}","Native Fahrzeuge, Personalstatus und Verfügbarkeit.")
        for(u in game.activeUnits){val card=container(10);val line=horizontal();val image=portrait(u,60);line.addView(image,LayoutParams(dp(62f),dp(62f)))
            val col=vertical();col.addView(txt(u.callsign,16f,ice,true));col.addView(txt("${u.type} · ${u.state}",11f,if(u.state=="FREI")mint else amber));col.addView(txt("${u.officers.size} Beamte · Zustand ${u.vehicleCondition}%",11f,muted));line.addView(col,LayoutParams(0,-2,1f));card.addView(line)
            btnRow(card,"Details" to {showUnitDetail(u)},"Karte" to {showTab("KARTE");mapView.focus(GeoPoint(u.lon,u.lat))})
            addMargin(list,card,4,4)
        }
    }
    private fun portrait(u:VehicleUnit,size:Int):ImageView {val image=ImageView(this);image.scaleType=ImageView.ScaleType.CENTER_CROP;image.background=rounded(0xff2c4e62.toInt(),8)
        val file=if(u.type=="SEK")"sek_team.webp" else u.officers.firstOrNull()?.portrait?:"officer_01.webp"
        try{assets.open("images/$file").use{image.setImageBitmap(BitmapFactory.decodeStream(it))}}catch(_:Exception){}
        image.layoutParams=LayoutParams(dp(size.toFloat()),dp(size.toFloat()));return image}
    private fun showUnitDetail(u:VehicleUnit){val body=vertical().apply{setPadding(dp(18f),dp(12f),dp(18f),dp(12f))};val row=horizontal();row.addView(portrait(u,96),LayoutParams(dp(96f),dp(96f)))
        val details=vertical();details.addView(txt(u.callsign,18f,ice,true));details.addView(txt(u.type,12f,cyan,true));details.addView(txt(u.state,12f,if(u.state=="FREI")mint else amber,true));row.addView(details,LayoutParams(0,-2,1f));body.addView(row)
        for(o in u.officers)body.addView(txt("${o.name} · Erfahrung ${o.skill} · Energie ${o.energy}%\nAusrüstung: ${o.equipment.joinToString()}",12f,muted))
        body.addView(txt("Fahrzeugzustand: ${u.vehicleCondition}%",12f,ice,true))
        AlertDialog.Builder(this).setTitle("Einheitendetails").setView(body).setPositiveButton("Ausrüstung"){_,_->showEquipment(u)}.setNeutralButton("Ausbildung"){_,_->showTraining(u)}.setNegativeButton("Schließen",null).show()
    }
    private fun showEquipment(u:VehicleUnit){val current=u.officers.firstOrNull()?.equipment?: emptyList<String>();val options=Catalog.gear.keys.filter{it !in current}
        if(options.isEmpty()){notice("Ausrüstung vollständig");return}
        AlertDialog.Builder(this).setTitle("${u.callsign} – Ausstattung kaufen").setItems(options.map{"$it · ${money(Catalog.gear[it]?:0)} €"}.toTypedArray()){_,which->if(game.equip(u.id,options[which])){sound.play("dispatch");notice("Ausrüstung gekauft");if(activeTab=="EINHEITEN")showTab(activeTab)}else notice("Nicht möglich: Budget oder Ausstattungslimit")}.setNegativeButton("Abbrechen",null).show()
    }
    private fun showTraining(u:VehicleUnit){if(game.isRunning){notice("Ausbildung nur zwischen Schichten");return}
        AlertDialog.Builder(this).setTitle("${u.callsign} – Fortbildung").setItems(Catalog.courses.map{"${it.name} · ${it.duration} Tage · ${money(it.price)} €"}.toTypedArray()){_,idx->if(game.train(u.id,idx)){sound.play("success");notice("Lehrgang begonnen");if(activeTab=="PERSONAL")showTab(activeTab)}else notice("Lehrgang nicht möglich")}.setNegativeButton("Abbrechen",null).show()
    }
    private fun renderPersonnel(parent:LinearLayout){val list=scrollBody(parent);section(list,"PERSONAL & AUSBILDUNG", "Beamtinnen und Beamte, Fortbildung, Ausrüstung und Einsatzbereitschaft.")
        for(u in game.activeUnits){val card=container();val r=horizontal();r.addView(portrait(u,64),LayoutParams(dp(68f),dp(68f)))
            val labels=vertical();labels.addView(txt(u.callsign+" / "+u.type,14f,ice,true));for(o in u.officers.take(2))labels.addView(txt("${o.name} · Skill ${o.skill} · Energie ${o.energy}%",11f,muted));r.addView(labels,LayoutParams(0,-2,1f));card.addView(r)
            val pending=u.officers.firstOrNull()?.trainingDays?:0;if(pending>0)card.addView(txt("Fortbildung: ${u.officers[0].trainingCourse} · noch $pending Tage",11f,amber,true))
            btnRow(card,"Fortbildung" to {showTraining(u)},"Ausrüstung" to {showEquipment(u)},"Wartung" to {if(game.repair(u.id))notice("Wartung erledigt")else notice("Budget nicht ausreichend")});addMargin(list,card,4,4)
        }
    }
    private fun renderEditor(parent:LinearLayout){val list=scrollBody(parent);section(list,"SANDBOX · EDITOREN", "Eigene Einheiten, Notrufe und Einsatzvorlagen, auch im Karrieremodus.")
        val actions=container();btnRow(actions,"+ Einsatzvorlage" to {showIncidentEditor(null)},"+ Einheit" to {showUnitEditor()});btnRow(actions,"+ Live-Einsatz" to {if(game.isRunning){game.simulateCall();notice("Notruf hinzugefügt")}else notice("Nur während der Schicht")},"Export / Import" to {showDataMenu()});addMargin(list,actions)
        section(list,"EIGENE EINSATZVORLAGEN (${game.customTemplates.size})")
        for(t in game.customTemplates){val card=container();card.addView(txt(t.first,14f,ice,true));card.addView(txt("Priorität ${t.third} · ${t.second}",12f,muted));btnRow(card,"Jetzt starten" to {if(game.isRunning){game.createIncident(t.first,t.second,t.third);notice("Einsatz gestartet")}else notice("Erst Schicht starten")});addMargin(list,card,2,2)}
        section(list,"KARTENEDITOR")
        addMargin(list,txt("Halte eine Position auf der Karte gedrückt, um dort einen eigenen Einsatz oder ein Fahrzeug zu platzieren.",13f,muted))
    }
    private fun editField(label:String,prefill:String=""):Pair<LinearLayout,EditText>{val row=vertical();row.addView(txt(label,12f,muted,true));val e=EditText(this).apply{setText(prefill);setTextColor(ice);textSize=16f;setSingleLine(true);background=rounded(0xff1a3348.toInt(),8);setPadding(dp(12f),dp(9f),dp(12f),dp(9f))};row.addView(e,LayoutParams(-1,dp(48f)));return row to e}
    private fun selectSpinner(options:List<String>):Spinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options)}
    private fun showIncidentEditor(point:GeoPoint?=null){val body=vertical().apply{setPadding(dp(16f),dp(10f),dp(16f),dp(4f))};val(f,e)=editField("Einsatzbezeichnung","Eigener Notruf");body.addView(f);val types=selectSpinner(Catalog.unitTypes);body.addView(txt("Benötigte Organisation",11f,muted));body.addView(types);val priorities=selectSpinner(listOf("P1 – kritisch","P2 – hoch","P3 – normal"));body.addView(txt("Priorität",11f,muted));body.addView(priorities)
        AlertDialog.Builder(this).setTitle(if(point==null)"Einsatzeditor" else "Einsatz an Kartenposition").setView(body).setPositiveButton(if(point==null)"Vorlage speichern" else "Einsatz erstellen"){_,_->
            val name=e.text.toString();val type=Catalog.unitTypes[types.selectedItemPosition];val p=priorities.selectedItemPosition+1
            val ok=if(point!=null&&game.isRunning){game.createIncident(name,type,p,point);true}else game.addCustomTemplate(name,type,p)
            if(ok){sound.play("success");notice("Gespeichert");if(activeTab=="EDITOR")showTab(activeTab)}else notice("Nicht gespeichert")
        }.setNegativeButton("Abbrechen",null).show()
    }
    private fun showUnitEditor(point:GeoPoint?=null){val body=vertical().apply{setPadding(dp(16f),dp(10f),dp(16f),dp(4f))};val(f,e)=editField("Funkrufname","Peter ${game.region.code} 99/1");body.addView(f);val types=selectSpinner(Catalog.unitTypes);body.addView(txt("Fahrzeug-/Einheitentyp",11f,muted));body.addView(types)
        AlertDialog.Builder(this).setTitle("Neue Einheit erstellen").setView(body).setPositiveButton("Erstellen"){_,_->
            val ok=game.addUnit(e.text.toString(),Catalog.unitTypes[types.selectedItemPosition],point)
            if(ok){sound.play("success");notice("Einheit erstellt");if(activeTab=="KARTE"||activeTab=="EINHEITEN"||activeTab=="EDITOR")showTab(activeTab)}else notice("Nicht möglich – Budget oder Eingaben prüfen")
        }.setNegativeButton("Abbrechen",null).show()
    }
    private fun renderShift(parent:LinearLayout){val list=scrollBody(parent);section(list,"SCHICHTFÜHRUNG", "Eine Schicht ist ein abgeschlossener Spieltag – in Karriere und Sandbox.")
        val card=container(14);card.addView(txt("TAG ${game.day}   ·   ${game.region.name}",16f,cyan,true));card.addView(txt("Spielmodus: ${game.mode}",13f,ice));card.addView(txt("Dienst: ${game.duration/60} Stunden · ${"%02d".format(game.startHour)}:00 bis ${"%02d".format(game.shiftEndHour)}:00",13f,ice));card.addView(txt("Zeit: ${game.clock} · gespielt ${game.elapsedHours}",13f,mint,true));
        card.addView(txt("Budget ${money(game.budget)} € · Ruf ${game.reputation}%",12f,muted))
        when(game.stage){"PLANUNG"->btnRow(card,"Schicht auswählen / starten" to {showShiftDialog(true)})
            "LAUFEND"->btnRow(card,"${if(game.paused)"Fortsetzen" else "Pause"}" to {game.togglePause();showTab("SCHICHT")},"Schicht beenden" to {endShiftDialog()})
            "AUSWERTUNG"->btnRow(card,"Tagesbericht öffnen" to {showReport()})}
        addMargin(list,card)
        section(list,"SCHICHTPROTOKOLL");for(report in game.reports.take(7)){val c=container();c.addView(txt("TAG ${report.day} · ${report.length/60}h · ${report.region}",14f,ice,true));c.addView(txt("${report.success}/${report.total} erfolgreich · ${report.failed} fehlgeschlagen · ${report.handovers} Übergaben",12f,muted));addMargin(list,c,4,4)}
        section(list,"EINSTELLUNGEN");val options=container();btnRow(options,"Audio ${if(sound.enabled)"AN" else "AUS"}" to {sound.enabled=!sound.enabled;showTab("SCHICHT")},"Spielstand" to {showDataMenu()});btnRow(options,"Neues Spiel" to {newGameDialog()},"Karte" to {showTab("KARTE")});addMargin(list,options)
    }
    private fun showShiftDialog(start:Boolean){if(!game.isPlanning)return;val body=vertical().apply{setPadding(dp(18f),dp(8f),dp(18f),dp(10f))};body.addView(txt("REGION  ${game.region.name}",12f,cyan,true));body.addView(txt("Wähle 8, 12 oder 24 Stunden. Schichtende erzeugt den Tagesbericht.",12f,muted))
        val lengths=selectSpinner(listOf("8 Stunden – Früh/Spät/Nacht","12 Stunden – Tag/Nacht","24 Stunden – Ganztag"));lengths.setSelection(when(selectedDuration){12->1;24->2;else->0});body.addView(txt("Schichtdauer",12f,ice,true));body.addView(lengths)
        val hours=selectSpinner((0..23).map {"%02d:00".format(Locale.GERMANY,it)});hours.setSelection(selectedHour.coerceIn(0,23));body.addView(txt("Dienstbeginn",12f,ice,true));body.addView(hours)
        val auto=CheckBox(this).apply{text="Automatische Notrufe";isChecked=game.autoCalls;setTextColor(ice)};body.addView(auto)
        AlertDialog.Builder(this).setTitle("Dienstplanung – Tag ${game.day}").setView(body).setPositiveButton("Schicht starten"){_,_->
            selectedDuration=listOf(8,12,24)[lengths.selectedItemPosition];selectedHour=hours.selectedItemPosition;game.planShift(selectedDuration,selectedHour);game.autoCalls=auto.isChecked;game.startShift();sound.play("dispatch");showTab("KARTE")
        }.setNeutralButton("Später",null).setNegativeButton("Anderer Spielmodus"){_,_->newGameDialog()}.show()
    }
    private fun endShiftDialog(){if(!game.isRunning)return
        if(game.elapsed<30){notice("Schichtende frühestens nach 30 Spielminuten");return}
        AlertDialog.Builder(this).setTitle("Schichtende").setMessage("Schicht jetzt beenden? Aktive Einsätze werden dokumentiert und an die Folgeschicht übergeben.")
            .setPositiveButton("Auswertung"){_,_->if(game.completeShift()){sound.play("shift");showTab("SCHICHT");showReport()}}.setNegativeButton("Weiterarbeiten",null).show()
    }
    private fun showReport(){val h=game.reports.firstOrNull()?:return
        val message="${h.region} · ${h.length/60} Stunden ab ${"%02d".format(h.startHour)}:00\n\nNotrufe: ${h.total}\nErfolgreich: ${h.success}\nFehlgeschlagen: ${h.failed}\nÜbergaben: ${h.handovers}\n\nTageskosten: ${money(-h.moneyDelta)} €\nBudget: ${money(game.budget)} €\nRuf: ${game.reputation}%\n\nLehrgänge und Ausrüstung bleiben erhalten."
        AlertDialog.Builder(this).setTitle("SCHICHTENDE · TAG ${h.day}").setMessage(message).setCancelable(false)
            .setPositiveButton("Nächsten Tag vorbereiten"){_,_->game.nextDay();showTab("SCHICHT");showShiftDialog(true)}.setNeutralButton("Zur Auswertung",null).show()
    }
    private fun newGameDialog(){AlertDialog.Builder(this).setTitle("Neues Spiel").setMessage("Neuer Spielstand überschreibt den bisherigen lokalen Stand.\n\nKARRIERE: Budget, Kosten, Ruf.\nSANDBOX: freies Einheitenmanagement.")
        .setPositiveButton("KARRIERE"){_,_->game.startNewGame("KARRIERE");showTab("KARTE");showShiftDialog(true)}
        .setNeutralButton("SANDBOX"){_,_->game.startNewGame("SANDBOX");showTab("KARTE");showShiftDialog(true)}.setNegativeButton("Abbrechen",null).show()
    }
    private fun showDataMenu(){AlertDialog.Builder(this).setTitle("Spielstand verwalten").setItems(arrayOf("Spielstand als JSON exportieren","Spielstand aus JSON importieren","Lokalen Spielstand sichern")){_,idx->when(idx){0->{val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="application/json";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"Einsatzleitstelle110-NativeV1.json")};startActivityForResult(intent,51)}
            1->{val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/json";addCategory(Intent.CATEGORY_OPENABLE)};startActivityForResult(intent,52)}
            else->{game.persist();notice("Lokal gespeichert")}}}.setNegativeButton("Abbrechen",null).show()}
    @Deprecated("Storage Access Framework result callback; minSdk26")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK)return
        val uri=data?.data?:return
        try{if(requestCode==51){contentResolver.openOutputStream(uri)?.use{it.write(game.saveJson().toByteArray(Charsets.UTF_8))};notice("Spielstand exportiert")}
            if(requestCode==52){val raw=contentResolver.openInputStream(uri)?.use{it.bufferedReader().readText()}?:return
                if(game.importJson(raw)){notice("Spielstand importiert");showTab("KARTE")}else notice("Ungültiger Spielstand (Schema V5)")}
        }catch(e:Exception){notice("Dateifehler: ${e.message?.take(48)}")}
    }
}
