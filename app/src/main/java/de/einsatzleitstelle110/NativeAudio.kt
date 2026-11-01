package de.einsatzleitstelle110

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.max
import java.util.Random

/** Small royalty-free, locally synthesized sound palette. No recordings or copyrighted game sounds. */
class NativeAudio(context:Context){
    private val settings=context.getSharedPreferences("operator_audio",Context.MODE_PRIVATE)
    var enabled:Boolean=settings.getBoolean("enabled",true)
        set(value){field=value;settings.edit().putBoolean("enabled",value).apply()}
    var volume:Float=settings.getFloat("volume",0.45f)
        set(value){field=value.coerceIn(0f,1f);settings.edit().putFloat("volume",field).apply()}
    private val pool=SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val samples=mutableMapOf<String,Int>()
    init{val folder=File(context.cacheDir,"native_sfx").apply{mkdirs()}
        val cues=mapOf("call" to doubleArrayOf(740.0,620.0),"critical" to doubleArrayOf(940.0,740.0,940.0),"warning" to doubleArrayOf(720.0,520.0),"dispatch" to doubleArrayOf(390.0,530.0),"success" to doubleArrayOf(523.0,659.0,784.0),"failure" to doubleArrayOf(395.0,325.0),"shift" to doubleArrayOf(440.0,554.0,660.0,880.0),"click" to doubleArrayOf(880.0),"radio" to doubleArrayOf(0.0))
        for((key,tones) in cues){val f=File(folder,"$key.wav");if(!f.exists())f.writeBytes(wave(tones,key=="radio"));samples[key]=pool.load(f.absolutePath,1)}
    }
    fun play(key:String){if(enabled){samples[key]?.let{pool.play(it,volume,volume,1,0,1f)}}}
    fun release(){pool.release()}
    private fun wave(tones:DoubleArray,noise:Boolean):ByteArray{
        val rate=22050;val one=if(noise)0.12 else 0.105;val n=(rate*one*tones.size).toInt();val pcm=ByteArray(n*2);val rng=Random(101L)
        for(i in 0 until n){val idx=(i/(rate*one)).toInt().coerceIn(0,tones.size-1);val t=(i%(rate*one).toInt()).toDouble()/rate;val fade=(minOf(1.0,t/.016)*minOf(1.0,(one-t)/.028)).coerceAtLeast(0.0);val hz=tones[idx]
            val signal=if(noise)(rng.nextDouble()*2-1)*0.42 else (sin(2*PI*hz*t)+0.16*sin(2*PI*hz*2*t))*0.33
            val sample=(signal*fade*32767).toInt().coerceIn(-32767,32767)
            pcm[2*i]=(sample and 255).toByte();pcm[2*i+1]=(sample shr 8).toByte()
        }
        val output=ByteArray(44+pcm.size);fun put16(offset:Int,v:Int){output[offset]=(v and 255).toByte();output[offset+1]=(v shr 8).toByte()}
        fun put32(offset:Int,v:Int){for(j in 0..3)output[offset+j]=(v shr (8*j)).toByte()}
        fun name(offset:Int,s:String){s.toByteArray(Charsets.US_ASCII).copyInto(output,offset)}
        name(0,"RIFF");put32(4,36+pcm.size);name(8,"WAVE");name(12,"fmt ");put32(16,16);put16(20,1);put16(22,1);put32(24,rate);put32(28,rate*2);put16(32,2);put16(34,16);name(36,"data");put32(40,pcm.size);pcm.copyInto(output,44);return output
    }
}
