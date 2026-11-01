package de.einsatzleitstelle110

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.util.LruCache
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.*

/** Native Canvas map: optional OpenStreetMap raster tiles (online), local GeoJSON always available.
 * Draws no WebView and runs completely as an Android View. Offline route network is schematic.
 */
class OperatorMapView(context: Context, attrs: AttributeSet? = null):View(context,attrs) {
    lateinit var game:SimEngine
    var onIncidentClicked:((Incident)->Unit)?=null
    var onUnitClicked:((VehicleUnit)->Unit)?=null
    var onPositionLongPress:((GeoPoint)->Unit)?=null
    var onBackgroundClicked:(()->Unit)?=null
    var onlineTiles:Boolean=true
    var selectedType:String="ALLE"
    var showIncidents:Boolean=true
    var showUnits:Boolean=true
    var showBases:Boolean=true
    var selectedIncidentId:Int=-1
    var selectedUnitId:Int=-1
    private var center=GeoPoint(10.0,53.55)
    private var zoom=9.0
    private val px=Paint(Paint.ANTI_ALIAS_FLAG)
    private val font=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    private val scaleDetector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean{zoom=(zoom+ln(detector.scaleFactor.toDouble())/ln(2.0)).coerceIn(4.0,15.5);invalidate();return true}
    })
    private var downX=0f;private var downY=0f;private var previousX=0f;private var previousY=0f;private var pressedAt=0L;private var dragging=false
    private val tiles=RasterTiles(context){postInvalidate()}
    private var initialFitPending=true
    private val unitHits=mutableListOf<Pair<VehicleUnit,PointF>>()
    private val incidentHits=mutableListOf<Pair<Incident,PointF>>()
    fun bind(engine:SimEngine){game=engine;fitRegion();invalidate()}
    fun fitRegion(){if(!::game.isInitialized)return;val r=game.region;center=r.center;initialFitPending=true;if(width>0&&height>0)fitActual(r);invalidate()}
    fun setRegion(code:String){if(!::game.isInitialized)return;fitRegion()}
    fun zoomIn(){zoom=(zoom+1.0).coerceAtMost(15.5);invalidate()}
    fun zoomOut(){zoom=(zoom-1.0).coerceAtLeast(4.0);invalidate()}
    fun focus(point:GeoPoint){center=point;zoom=max(zoom,10.5);invalidate()}
    fun toggleTiles(){onlineTiles=!onlineTiles;invalidate()}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);if(::game.isInitialized&&w>0&&h>0&&initialFitPending)fitActual(game.region)}
    private fun fitActual(r:Region){if(width<=0||height<=0)return;val availW=width*.82;val availH=height*.82
        center=GeoPoint((r.minLon+r.maxLon)*.5,(r.minLat+r.maxLat)*.5)
        for(z in 15 downTo 4){val a=world(GeoPoint(r.minLon,r.minLat),z.toDouble());val b=world(GeoPoint(r.maxLon,r.maxLat),z.toDouble());if(abs(a.x-b.x)<availW && abs(a.y-b.y)<availH){zoom=z.toDouble();break} else zoom=4.0}
        initialFitPending=false
    }
    private fun world(p:GeoPoint,z:Double):PointF{val size=256.0*2.0.pow(z);val lat=p.lat.coerceIn(-85.0,85.0)*Math.PI/180.0
        return PointF(((p.lon+180.0)/360.0*size).toFloat(),((1.0-ln(tan(lat)+1/cos(lat))/Math.PI)/2.0*size).toFloat())}
    private fun project(p:GeoPoint):PointF{val a=world(p,zoom);val c=world(center,zoom);return PointF(width/2f+a.x-c.x,height/2f+a.y-c.y)}
    private fun unproject(x:Float,y:Float):GeoPoint {val size=256.0*2.0.pow(zoom);val c=world(center,zoom);val xx=(c.x+(x-width/2))/size;val yy=(c.y+(y-height/2))/size
        val lat=atan(sinh(Math.PI*(1-2*yy)))*180/Math.PI;val lon=(xx*360-180).toDouble();return GeoPoint(lon,lat)}
    private fun paint(color:Int,style:Paint.Style=Paint.Style.FILL,stroke:Float=1f){px.color=color;px.style=style;px.strokeWidth=stroke;px.shader=null;px.pathEffect=null;return px}
    private fun text(canvas:Canvas,s:String,x:Float,y:Float,size:Float,color:Int=Color.WHITE,align:Paint.Align=Paint.Align.LEFT,bold:Boolean=false){font.color=color;font.textSize=size*resources.displayMetrics.density; font.textAlign=align;font.typeface=Typeface.create(if(bold)"sans-serif-condensed" else "sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);font.setShadowLayer(3f,0f,1f,0xff07111e.toInt());canvas.drawText(s,x,y,font);font.clearShadowLayer()}
    private fun polygonPath(points:List<GeoPoint>):Path {val p=Path();points.forEachIndexed{index,q->val v=project(q);if(index==0)p.moveTo(v.x,v.y) else p.lineTo(v.x,v.y)};p.close();return p}
    override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(0xff0a1421.toInt());if(!::game.isInitialized)return
        if(initialFitPending&&width>0&&height>0)fitActual(game.region)
        drawGrid(c)
        val tileCount=if(onlineTiles)drawTiles(c) else 0
        drawTerritory(c, tileCount>0)
        drawRoutes(c)
        drawStations(c)
        drawMarkers(c)
        drawOverlay(c, tileCount>0)
    }
    private fun drawGrid(c:Canvas){val gap=48f*resources.displayMetrics.density;var x=0f;while(x<width){c.drawLine(x,0f,x,height.toFloat(),paint(0x163e647c,Paint.Style.STROKE,1f));x+=gap};var y=0f;while(y<height){c.drawLine(0f,y,width.toFloat(),y,paint(0x163e647c,Paint.Style.STROKE,1f));y+=gap}}
    private fun drawTiles(c:Canvas):Int {val tileZ=floor(zoom).toInt().coerceIn(4,15);val tileScale=2.0.pow(zoom-tileZ);val cx=world(center,tileZ.toDouble());val ds=256*tileScale
        val minX=floor(cx.x/256.0-(width/2.0)/ds).toInt();val maxX=ceil(cx.x/256.0+(width/2.0)/ds).toInt();val minY=floor(cx.y/256.0-(height/2.0)/ds).toInt();val maxY=ceil(cx.y/256.0+(height/2.0)/ds).toInt()
        val candidates=mutableListOf<Triple<Int,Int,Int>>();for(x in minX..maxX)for(y in minY..maxY){if(y<0||y>=1.shl(tileZ)||x<0||x>=1.shl(tileZ))continue;candidates.add(Triple(tileZ,x,y))}
        if(candidates.size>45)return 0
        var visible=0
        for((z,x,y) in candidates){val left=((x*256-cx.x)*tileScale+width/2.0).toFloat();val top=((y*256-cx.y)*tileScale+height/2.0).toFloat();val bitmap=tiles.get(z,x,y)
            if(bitmap!=null){c.drawBitmap(bitmap,null,RectF(left,top,(left+ds).toFloat(),(top+ds).toFloat()),paint(Color.WHITE));visible++}
        };return visible
    }
    private fun drawTerritory(c:Canvas,hasTiles:Boolean){val reg=game.region;for(points in reg.outlines){val path=polygonPath(points)
            if(!hasTiles){c.drawPath(path,paint(0xff172b34.toInt()));c.drawPath(path,paint(0x2548a8bc,Paint.Style.STROKE,7f))}
            c.drawPath(path,paint(if(hasTiles)0xff21acd6.toInt() else 0xff4abfdd.toInt(),Paint.Style.STROKE,2.7f*resources.displayMetrics.density))
        }
        if(!hasTiles){val cities=reg.cities
            for(a in cities){val first=project(a.pos);val nearest=cities.filter{it!==a}.sortedBy{SimEngine.haversine(a.pos.lat,a.pos.lon,it.pos.lat,it.pos.lon)}.take(2)
                for(b in nearest){val dest=project(b.pos);c.drawLine(first.x,first.y,dest.x,dest.y,paint(0xff364950.toInt(),Paint.Style.STROKE,5f));c.drawLine(first.x,first.y,dest.x,dest.y,paint(0xff7b8b90.toInt(),Paint.Style.STROKE,1.8f))}
            }
        }
        for(city in reg.cities){val p=project(city.pos);if(p.x< -50||p.x>width+50||p.y< -50||p.y>height+50)continue
            c.drawCircle(p.x,p.y,3.4f*resources.displayMetrics.density,paint(0xffffd68b.toInt()))
            if(zoom>=6.2||reg.code in setOf("HH","HB","BE"))text(c,city.name,p.x+7f*resources.displayMetrics.density,p.y-6f*resources.displayMetrics.density,if(zoom<8)9f else 10f,0xfff1e9ce.toInt())
        }
    }
    private fun drawRoutes(c:Canvas){val d=resources.displayMetrics.density
        for(u in game.activeUnits){if(u.state!="ANFAHRT")continue
            if(selectedUnitId!=-1 && selectedUnitId!=u.id)continue
            val a=project(GeoPoint(u.lon,u.lat));val b=project(GeoPoint(u.routeLon,u.routeLat))
            val line=paint(0xff54d7ff.toInt(),Paint.Style.STROKE,2.7f*d);line.pathEffect=DashPathEffect(floatArrayOf(10f*d,6f*d),0f)
            c.drawLine(a.x,a.y,b.x,b.y,line);line.pathEffect=null
        }
    }
    private fun drawStations(c:Canvas){if(!showBases)return
        val d=resources.displayMetrics.density
        for(city in game.region.cities){val p=project(city.pos);if(p.x<0||p.x>width||p.y<0||p.y>height)continue
            c.drawRect(p.x-4*d,p.y-4*d,p.x+4*d,p.y+4*d,paint(0xff3c7f9f.toInt()));c.drawRect(p.x-4*d,p.y-4*d,p.x+4*d,p.y+4*d,paint(0xffd1e7ff.toInt(),Paint.Style.STROKE,1*d))
        }
    }
    private fun drawMarkers(c:Canvas){unitHits.clear();incidentHits.clear();val d=resources.displayMetrics.density
        if(showIncidents)for(inc in game.liveIncidents){val p=project(GeoPoint(inc.lon,inc.lat));if(p.x<0||p.x>width||p.y<0||p.y>height)continue
            val color=when(inc.priority){1->0xfff04f66.toInt();2->0xffffbb56.toInt();else->0xff5ab7ff.toInt()}
            val r=if(inc.id==selectedIncidentId)17*d else 12*d
            c.drawCircle(p.x,p.y,r+7*d,paint((color and 0x00ffffff) or 0x35000000))
            c.drawCircle(p.x,p.y,r,paint(0xff101e2d.toInt()));c.drawCircle(p.x,p.y,r,paint(color,Paint.Style.STROKE,2.3f*d))
            text(c,"${inc.priority}",p.x,p.y+4.7f*d,12f,color,Paint.Align.CENTER,true)
            incidentHits.add(inc to p)
        }
        if(showUnits)for(u in game.activeUnits){if(selectedType!="ALLE"&&u.type!=selectedType)continue
            val p=project(GeoPoint(u.lon,u.lat));if(p.x<0||p.x>width||p.y<0||p.y>height)continue
            val color=when {u.state=="FREI"->0xff5eddb0.toInt();u.state=="ANFAHRT"->0xff3fc5ff.toInt();u.state=="BEARBEITUNG"->0xffffa74b.toInt();else->0xff8b9dae.toInt()}
            val a=10*d
            if(u.id==selectedUnitId)c.drawCircle(p.x,p.y,17*d,paint(0x5558caff))
            val box=RectF(p.x-a,p.y-a*.75f,p.x+a,p.y+a*.75f)
            c.drawRoundRect(box,4*d,4*d,paint(0xff0b2633.toInt()));c.drawRoundRect(box,4*d,4*d,paint(color,Paint.Style.STROKE,2*d))
            val label=when(u.type){"SEK"->"S";"MEK"->"M";"KRIPO"->"K";"VERKEHR"->"V";"WASSER"->"W";"HUBSCHRAUBER"->"H";else->"P"}
            text(c,label,p.x,p.y+4*d,11f,0xffffffff.toInt(),Paint.Align.CENTER,true)
            unitHits.add(u to p)
        }
    }
    private fun drawOverlay(c:Canvas,tileEnabled:Boolean){val d=resources.displayMetrics.density
        c.drawRoundRect(RectF(10*d,10*d,198*d,61*d),9*d,9*d,paint(0xe50b1b29.toInt()))
        text(c,"LIVE OPERATIONS",21*d,29*d,12f,0xff5ecaff.toInt(),bold=true)
        text(c,"${game.activeUnits.size} Fahrzeuge   •   ${game.liveIncidents.size} Lagen",21*d,47*d,10f,0xffdaebf5.toInt())
        val info=if(tileEnabled)"© OpenStreetMap-Mitwirkende" else "Offline · schematische Straßen"
        c.drawRect(0f,height-21*d,width.toFloat(),height.toFloat(),paint(0xbb0b1926.toInt()))
        text(c,"${game.region.name}   •   $info",10*d,height-6*d,10f,0xffb5c8d6.toInt())
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {scaleDetector.onTouchEvent(e)
        when(e.actionMasked){MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;previousX=e.x;previousY=e.y;pressedAt=SystemClock.uptimeMillis();dragging=false;return true}
            MotionEvent.ACTION_MOVE->{if(e.pointerCount==1&&!scaleDetector.isInProgress){if(abs(e.x-downX)>8||abs(e.y-downY)>8)dragging=true
                if(dragging){val old=unproject(previousX,previousY);val now=unproject(e.x,e.y);center=GeoPoint(center.lon+(old.lon-now.lon),center.lat+(old.lat-now.lat).coerceIn(-15.0,15.0));center=center.copy(lat=center.lat.coerceIn(46.0,58.0),lon=center.lon.coerceIn(3.0,18.0));invalidate()}
            };previousX=e.x;previousY=e.y;return true}
            MotionEvent.ACTION_UP->{if(!dragging&&!scaleDetector.isInProgress){val d=resources.displayMetrics.density
                    if(SystemClock.uptimeMillis()-pressedAt>560){onPositionLongPress?.invoke(unproject(e.x,e.y));return true}
                    val hitI=incidentHits.minByOrNull{hypot((it.second.x-e.x).toDouble(),(it.second.y-e.y).toDouble())}
                    if(hitI!=null&&hypot((hitI.second.x-e.x).toDouble(),(hitI.second.y-e.y).toDouble())<26*d){selectedIncidentId=hitI.first.id;selectedUnitId=-1;onIncidentClicked?.invoke(hitI.first);invalidate();return true}
                    val hitU=unitHits.minByOrNull{hypot((it.second.x-e.x).toDouble(),(it.second.y-e.y).toDouble())}
                    if(hitU!=null&&hypot((hitU.second.x-e.x).toDouble(),(hitU.second.y-e.y).toDouble())<25*d){selectedUnitId=hitU.first.id;selectedIncidentId=-1;onUnitClicked?.invoke(hitU.first);invalidate();return true}
                    onBackgroundClicked?.invoke()
                };return true}
        };return true
    }
    private class RasterTiles(private val ctx:Context,private val changed:()->Unit){
        private val cache=object:LruCache<String,Bitmap>(14*1024*1024){override fun sizeOf(k:String,v:Bitmap)=v.byteCount}
        private val loading=mutableSetOf<String>()
        private val executor=Executors.newFixedThreadPool(2)
        private val directory=File(ctx.cacheDir,"osm_tiles").apply{mkdirs()}
        fun get(z:Int,x:Int,y:Int):Bitmap?{val key="${z}_${x}_${y}";synchronized(cache){cache.get(key)?.let{return it}}
            synchronized(loading){if(!loading.add(key))return null;if(loading.size>34){loading.remove(key);return null}}
            executor.submit{try{val cached=File(directory,"$key.png");val bitmap=if(cached.exists()&&System.currentTimeMillis()-cached.lastModified()<6*86400000L){BitmapFactory.decodeFile(cached.path)}else{
                    val url=URL("https://tile.openstreetmap.org/$z/$x/$y.png")
                    val conn=url.openConnection() as HttpURLConnection;conn.setRequestProperty("User-Agent","Einsatzleitstelle110Native/1.0 (+https://github.com/GoldenUnicorn24/Operator)");conn.connectTimeout=4000;conn.readTimeout=4500;conn.setRequestProperty("Accept","image/png");conn.setRequestProperty("Accept-Encoding","identity")
                    try{if(conn.responseCode==200){conn.inputStream.use{stream->cached.outputStream().use{stream.copyTo(it)}};BitmapFactory.decodeFile(cached.path)}else null}finally{conn.disconnect()}
                }
                if(bitmap!=null){synchronized(cache){cache.put(key,bitmap)};changed()}
            }catch(_:Exception){}finally{synchronized(loading){loading.remove(key)}}}
            return null
        }
    }
}
