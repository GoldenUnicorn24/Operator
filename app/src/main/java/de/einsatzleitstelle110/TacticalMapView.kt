package de.einsatzleitstelle110

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

/** Entirely native tactical map. Real state boundaries, offline schematic intercity network.
 * No WebView, HTML, JavaScript or downloaded tile services.
 */
class TacticalMapView(
 context:Context, private val game:GameController,
 private val onCall:(IncidentState)->Unit,
 private val onUnit:(UnitState)->Unit
):View(context) {
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 private val d=resources.displayMetrics.density
 var filter="ALLE"
 var showRoutes=true
 var zoom=1f
 private var panX=0f; private var panY=0f
 private var previousX=0f; private var previousY=0f
 private var moved=false
 private val pinLocations=mutableListOf<Triple<Float,Float,Any>>()
 private var transform:Transform?=null
 var useOnlineStreets=true
 private val streetTiles=NativeStreetTiles(context){postInvalidate()}
 fun close(){streetTiles.close()}
 private val detector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
  override fun onScale(detector:ScaleGestureDetector):Boolean{
   zoom=(zoom*detector.scaleFactor).coerceIn(0.8f,10f)
   invalidate();return true
  }
 })
 data class Transform(val region:GeoRegion,val w:Float,val h:Float,val centerX:Double,
  val centerY:Double,val fitted:Double,val zoom:Float,val panX:Float,val panY:Float) {
  companion object {
   fun worldX(lon:Double)=(lon+180.0)/360.0
   fun worldY(lat:Double):Double{
    val r=Math.toRadians(lat.coerceIn(-85.0,85.0))
    return (1.0-ln(tan(r)+1.0/cos(r))/PI)/2.0
   }
  }
  fun point(lon:Double,lat:Double):PointF =
    PointF((w/2+((worldX(lon)-centerX)*fitted*zoom+panX)).toFloat(),
      (h/2+((worldY(lat)-centerY)*fitted*zoom+panY)).toFloat())
 }
 fun resetCamera(){zoom=1f;panX=0f;panY=0f;invalidate()}
 fun focus(lon:Double,lat:Double){
  zoom=2.4f
  val t=makeTransform(zoom,0f,0f)
  val a=t.point(lon,lat)
  panX=width/2f-a.x;panY=height/2f-a.y
  invalidate()
 }
 private fun makeTransform(z:Float,px:Float,py:Float):Transform {
  val region=game.region();val b=region.bounds
  val minX=Transform.worldX(b[0]);val maxX=Transform.worldX(b[2])
  val minY=Transform.worldY(b[3]);val maxY=Transform.worldY(b[1])
  val ww=max(1f,width.toFloat());val hh=max(1f,height.toFloat())
  val scale=min((ww-50*d)/(maxX-minX).coerceAtLeast(.000001),
     (hh-76*d)/(maxY-minY).coerceAtLeast(.000001)).coerceAtLeast(.001)
  return Transform(region,ww,hh,(minX+maxX)/2,(minY+maxY)/2,scale,z,px,py)
 }
 private fun paint(color:Int,stroke:Float=1f,style:Paint.Style=Paint.Style.FILL){
  p.reset();p.isAntiAlias=true;p.color=color;p.style=style;p.strokeWidth=stroke*d
  p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
 }
 private fun color(hex:String)=Color.parseColor(hex)
 private fun line(c:Canvas,a:PointF,b:PointF,color:Int,stroke:Float){
  paint(color,stroke,Paint.Style.STROKE);c.drawLine(a.x,a.y,b.x,b.y,p)
 }
 private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,tint:Int,bold:Boolean=false){
  paint(tint);p.textSize=size*d;p.typeface=if(bold)android.graphics.Typeface.DEFAULT_BOLD else
    android.graphics.Typeface.DEFAULT;c.drawText(s,x,y,p)
 }
 private fun pathFor(t:Transform):Path {
  val path=Path().apply{fillType=Path.FillType.EVEN_ODD}
  for(ring in t.region.rings) {
   ring.forEachIndexed {i,point->
    val q=t.point(point.lon,point.lat)
    if(i==0)path.moveTo(q.x,q.y)else path.lineTo(q.x,q.y)
   }
   path.close()
  }
  return path
 }
 override fun onDraw(c:Canvas) {
  super.onDraw(c)
  c.drawColor(color("#0a141e"))
  val t=makeTransform(zoom,panX,panY);transform=t
  // Contour lines and technical grid
  paint(color("#19303d"),.55f,Paint.Style.STROKE)
  var x=(panX% (46*d))
  while(x<width){c.drawLine(x,0f,x,height.toFloat(),p);x+=46*d}
  var y=(panY%(46*d))
  while(y<height){c.drawLine(0f,y,width.toFloat(),y,p);y+=46*d}
  val shape=pathFor(t)
  paint(color("#051018"),9f,Paint.Style.STROKE)
  c.drawPath(shape,p)
  paint(color("#1b2934"))
  c.drawPath(shape,p)
  c.save()
  c.clipPath(shape)
  streetTiles.enabled=useOnlineStreets
  val onlineTiles=if(useOnlineStreets)streetTiles.draw(c,t.centerX,t.centerY,t.fitted,
    zoom,panX,panY,width,height)else 0
  // Offline city graph fallback; do not present the simulated graph as GPS road data.
  if(onlineTiles==0)for(city in t.region.cities){
   val center=t.point(city.lon,city.lat)
   val close=t.region.cities.filter{it!=city}.sortedBy{
    GeoData.km(GeoPoint(city.lon,city.lat),GeoPoint(it.lon,it.lat))
   }.take(3)
   for(near in close) {
    val dest=t.point(near.lon,near.lat)
    if(city.name>=near.name)continue
    // Parallel graph lines mimic intercity arteries; clearly not GPS street data.
    line(c,center,dest,color("#374c59"),3.2f)
    line(c,center,dest,color("#667482"),.8f)
   }
   // Fine secondary lines for more legible local grid at higher zoom.
   val radius=(if(t.region.code=="DE-HH")24 else 13)*d*zoom
   paint(color("#536675"),.75f,Paint.Style.STROKE)
   for(i in 0..5){
    val a=i*PI/3
    val dx=cos(a).toFloat()*radius
    val dy=sin(a).toFloat()*radius*.72f
    c.drawLine(center.x-dx*.5f,center.y-dy*.5f,center.x+dx,center.y+dy,p)
   }
  }
  // Regional location anchors
  for(city in t.region.cities) {
   val q=t.point(city.lon,city.lat)
   paint(color("#d2cba9"));c.drawCircle(q.x,q.y,2.6f*d,p)
   text(c,city.name,q.x+5*d,q.y-5*d,if(zoom<1.15)9f else 11f,color("#d4dce1"),true)
  }
  c.restore()
  paint(color("#8cb1c3"),2.0f,Paint.Style.STROKE)
  c.drawPath(shape,p)
  paint(color("#365768"),.7f,Paint.Style.STROKE)
  c.drawPath(shape,p)
  pinLocations.clear()
  val state=game.state
  // In-flight call-to-destination paths
  if(showRoutes)for(u in state.units) {
   if(u.region!=state.region||u.status!="DRIVING"||!visible(u.type))continue
   val a=t.point(u.lon,u.lat);val b=t.point(u.destLon,u.destLat)
   paint(color("#49cfff"),2.7f,Paint.Style.STROKE)
   p.pathEffect=android.graphics.DashPathEffect(floatArrayOf(8*d,6*d),0f)
   c.drawLine(a.x,a.y,b.x,b.y,p)
   p.pathEffect=null
  }
  for(i in state.incidents) {
   if(i.region!=state.region||i.status !in listOf("OPEN","DRIVING","WORKING"))continue
   if(filter !in listOf("ALLE","EINSÄTZE"))continue
   val q=t.point(i.lon,i.lat)
   val tint=when(i.priority){1->color("#ff526b");2->color("#e9ad49");else->color("#5eaefc")}
   val pulse=if(i.status=="OPEN")1.0 else .72
   paint(Color.argb((40*pulse).toInt(),Color.red(tint),Color.green(tint),Color.blue(tint)))
   c.drawCircle(q.x,q.y,17*d,p)
   paint(tint,1.7f,Paint.Style.STROKE);c.drawCircle(q.x,q.y,10.8f*d,p)
   paint(tint);c.drawCircle(q.x,q.y,5.2f*d,p)
   text(c,"P"+i.priority,q.x+12*d,q.y-8*d,10f,tint,true)
   pinLocations.add(Triple(q.x,q.y,i))
  }
  for(u in state.units) {
   if(u.region!=state.region||!visible(u.type))continue
   val q=t.point(u.lon,u.lat)
   val tint=when(u.type) {
    "SEK"->color("#c69bff");"MEK"->color("#6fe1ad");"KRIPO"->color("#63d3d4")
    "RETTUNG"->color("#ffd18f");"FEUERWEHR"->color("#fa7d61");else->color("#50b6ff")
   }
   if(u.status=="TRAINING")continue
   val cx=q.x;val cy=q.y
   paint(color("#09151f"))
   c.drawRoundRect(RectF(cx-14*d,cy-12*d,cx+14*d,cy+12*d),5*d,5*d,p)
   paint(tint,1.8f,Paint.Style.STROKE)
   c.drawRoundRect(RectF(cx-13*d,cy-11*d,cx+13*d,cy+11*d),5*d,5*d,p)
   paint(tint);c.drawRoundRect(RectF(cx-7*d,cy-4*d,cx+7*d,cy+2*d),2*d,2*d,p)
   c.drawCircle(cx-5*d,cy+5*d,2*d,p);c.drawCircle(cx+5*d,cy+5*d,2*d,p)
   if(u.status=="DRIVING"){
    paint(color("#fff1b3"),1f,Paint.Style.STROKE)
    c.drawCircle(cx,cy,16*d,p)
   }
   pinLocations.add(Triple(cx,cy,u))
  }
  drawInfoHud(c,t)
 }
 private fun visible(type:String)= filter=="ALLE" || filter==type
 private fun drawInfoHud(c:Canvas,t:Transform) {
  paint(color("#07131e")) // opaque fallback; alpha set below
  p.alpha=225
  val r=RectF(10*d,10*d,min(width-10*d,230*d),75*d)
  c.drawRoundRect(r,8*d,8*d,p)
  text(c,"● LIVE  ·  POLIZEI",20*d,29*d,11f,color("#67d7fe"),true)
  text(c,t.region.name,20*d,48*d,16f,Color.WHITE,true)
  text(c,"EINSÄTZE "+game.openIncidents()+"   ·   FREI "+game.freeUnits(),20*d,63*d,10f,color("#b9cbd7"))
  paint(color("#07131e"));p.alpha=220
  val b=RectF(10*d,height-32*d,width-10*d,height-5*d)
  c.drawRoundRect(b,6*d,6*d,p)
  text(c,if(useOnlineStreets)"© OPENSTREETMAP CONTRIBUTORS  ·  ROUTING SIMULIERT"
    else "GRENZEN ECHT  ·  NETZ SIMULIERT  ·  OFFLINE",18*d,height-15*d,
    8.1f,color("#a9bbc5"))
 }
 override fun onTouchEvent(event:MotionEvent):Boolean {
  detector.onTouchEvent(event)
  when(event.actionMasked){
   MotionEvent.ACTION_DOWN->{previousX=event.x;previousY=event.y;moved=false;return true}
   MotionEvent.ACTION_MOVE->{
    if(event.pointerCount==1&&!detector.isInProgress){
     val dx=event.x-previousX;val dy=event.y-previousY
     if(abs(dx)>1||abs(dy)>1){
      panX+=dx;panY+=dy;moved=true;invalidate()
     }
     previousX=event.x;previousY=event.y
    };return true
   }
   MotionEvent.ACTION_UP->{
    if(!moved&&!detector.isInProgress){
     val r=pinLocations.minByOrNull{
      hypot(it.first-event.x,it.second-event.y)
     }
     if(r!=null&&hypot(r.first-event.x,r.second-event.y)<27*d){
      when(val data=r.third){
       is IncidentState->onCall(data)
       is UnitState->onUnit(data)
      }
     }else performClick()
    }
    return true
   }
   MotionEvent.ACTION_CANCEL->return true
  }
  return true
 }
 override fun performClick():Boolean {
  super.performClick()
  return true
 }
}
