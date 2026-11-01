package de.einsatzleitstelle110

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.*

/**
 * Optional NATIVE tile renderer. Only interactive viewport tiles are fetched,
 * at a maximum of two concurrent requests. No bulk fetching or prefetch.
 *
 * Tiles: © OpenStreetMap contributors. OSMF tile server use is best-effort;
 * for public distribution, a dedicated provider is recommended.
 */
class NativeStreetTiles(private val context:Context,private val onReady:()->Unit) {
 var enabled=false
 private val downloader=Executors.newFixedThreadPool(2)
 private val cache=object:LruCache<String,Bitmap>(72) {}
 private val loading=Collections.synchronizedSet(mutableSetOf<String>())
 private val disk=File(context.cacheDir,"osm_tactical_tiles").also{it.mkdirs()}
 private val ttl=7L*24*60*60*1000
 private var requested=0
 private val tint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
  // Tinted inverse of OSM raster: black/steel-blue roads, light labels.
  colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
   -.67f,0f,0f,0f,181f,
   0f,-.62f,0f,0f,192f,
   0f,0f,-.60f,0f,207f,
   0f,0f,0f,1f,0f)))
 }
 private fun key(z:Int,x:Int,y:Int) = "t"+z+"_"+x+"_"+y
 private fun cached(z:Int,x:Int,y:Int):Bitmap? {
  val k=key(z,x,y)
  synchronized(cache){cache.get(k)?.let{return it}}
  val f=File(disk,k+".png")
  if(f.isFile&&System.currentTimeMillis()-f.lastModified()<ttl){
   try{
    val bmp=BitmapFactory.decodeFile(f.absolutePath)
    if(bmp!=null){synchronized(cache){cache.put(k,bmp)};return bmp}
   }catch(_:Exception){}
  }
  return null
 }
 private fun request(z:Int,x:Int,y:Int){
  val k=key(z,x,y)
  if(requested>=500||loading.size>=15||!loading.add(k))return
  requested++
  downloader.execute {
   try{
    val url=URL("https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png")
    val connection=url.openConnection() as HttpURLConnection
    try {
     connection.connectTimeout=5500;connection.readTimeout=5500
     connection.setRequestProperty("User-Agent",
       "Einsatzleitstelle110-AndroidNative/1.0 (de.einsatzleitstelle110)")
     connection.setRequestProperty("Accept","image/png")
     if(connection.responseCode==200) {
      val data=connection.inputStream.use{it.readBytes()}
      if(data.size<500000) {
       val bitmap=BitmapFactory.decodeByteArray(data,0,data.size)
       if(bitmap!=null){
        synchronized(cache){cache.put(k,bitmap)}
        val file=File(disk,k+".png")
        file.writeBytes(data);file.setLastModified(System.currentTimeMillis())
        onReady()
       }
      }
     }
    } finally {connection.disconnect()}
   } catch(_:Exception) {
    // Always retain native offline fallback if a server/network is unavailable.
   } finally {loading.remove(k)}
  }
 }
 fun draw(
  c:Canvas, centerX:Double,centerY:Double, worldScale:Double,zoom:Float,
  panX:Float,panY:Float,w:Int,h:Int
 ):Int {
  if(!enabled||w<1||h<1||worldScale<=0)return 0
  val scale=worldScale*zoom
  val z=log2(scale/256.0).roundToInt().coerceIn(5,15)
  val n=1 shl z
  val west=centerX+(-w/2.0-panX)/scale
  val east=centerX+(w/2.0-panX)/scale
  val north=centerY+(-h/2.0-panY)/scale
  val south=centerY+(h/2.0-panY)/scale
  val left=floor(west*n).toInt().coerceIn(0,n-1)
  val right=floor(east*n).toInt().coerceIn(0,n-1)
  val top=floor(north*n).toInt().coerceIn(0,n-1)
  val bottom=floor(south*n).toInt().coerceIn(0,n-1)
  var drawn=0;var count=0
  for(y in top..bottom)for(x in left..right){
   if(++count>36)return drawn
   val leftPx=(w/2.0+((x.toDouble()/n-centerX)*scale)+panX).toFloat()
   val topPx=(h/2.0+((y.toDouble()/n-centerY)*scale)+panY).toFloat()
   val sizePx=(scale/n).toFloat()
   val bitmap=cached(z,x,y)
   if(bitmap!=null) {
    c.drawBitmap(bitmap,null,RectF(leftPx,topPx,leftPx+sizePx+.5f,topPx+sizePx+.5f),tint)
    drawn++
   }else request(z,x,y)
  }
  return drawn
 }
 fun close(){downloader.shutdownNow()}
}
