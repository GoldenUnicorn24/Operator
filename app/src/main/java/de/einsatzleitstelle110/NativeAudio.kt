package de.einsatzleitstelle110

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*
import java.util.concurrent.Executors

/** Native, original, programmatically synthesised sound cues. */
class NativeAudio {
 var muted:Boolean=false
 private val queue=Executors.newSingleThreadExecutor()
 private fun play(notes:List<Pair<Double,Int>>, volume:Double=0.12) {
  if(muted)return
  queue.execute {
   try{
    val rate=22050
    val samples=ArrayList<Short>()
    for((freq,length) in notes) {
     val count=(rate*length/1000).coerceAtLeast(1)
     for(i in 0 until count){
      val envelope=min(1.0,i/(rate*.006)).coerceAtMost(
        min(1.0,(count-i)/(rate*.035))
      ).coerceAtLeast(0.0)
      val t=i.toDouble()/rate
      val wave=(sin(t*freq*2*PI)+.24*sin(t*freq*4*PI))*.8
      samples.add((wave*envelope*32767*volume).toInt().coerceIn(-32767,32767).toShort())
     }
    }
    val bytes=ByteArray(samples.size*2)
    for(i in samples.indices) {
     val v=samples[i].toInt();bytes[i*2]=v.toByte();bytes[i*2+1]=(v shr 8).toByte()
    }
    val track=AudioTrack.Builder()
      .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
      .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
      .setBufferSizeInBytes(bytes.size.coerceAtLeast(1024))
      .setTransferMode(AudioTrack.MODE_STATIC).build()
    track.write(bytes,0,bytes.size);track.play()
    Thread.sleep(notes.sumOf{it.second}.toLong()+70)
    track.stop();track.release()
   }catch(_:Exception){}
  }
 }
 fun cue(name:String) {
  when(name){
   "CALL"->play(listOf(620.0 to 90,0.0 to 70,750.0 to 100))
   "P1"->play(listOf(660.0 to 140,440.0 to 130,660.0 to 140,440.0 to 130))
   "DISPATCH"->play(listOf(430.0 to 75,660.0 to 105))
   "ARRIVAL"->play(listOf(510.0 to 90,735.0 to 80))
   "SUCCESS"->play(listOf(490.0 to 75,680.0 to 75,870.0 to 120))
   "FAILED"->play(listOf(420.0 to 110,310.0 to 165))
   "SHIFT"->play(listOf(440.0 to 130,550.0 to 130,660.0 to 220))
   else->play(listOf(530.0 to 75))
  }
 }
 fun close(){queue.shutdownNow()}
}
