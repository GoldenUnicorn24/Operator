package de.einsatzleitstelle110

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/** Real image resources; atlas crops happen in native Bitmap rendering, never HTML. */
object EquipmentArt {
    private val cache =
        object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

    fun equipmentImage(name: String): String {
        val n =
            when {
                name.contains("P99") -> 0
                name.contains("MP5") -> 1
                name.contains("G36") || name.contains("HK416") -> 2
                name.contains("Taser") || name.contains("impuls", true) -> 3
                name == "Funkgerät" || name.contains("Repeater") -> 4
                name.contains("weste") -> 5
                name.contains("helm") -> 6
                name.contains("gürtel") -> 7
                name.contains("Bodycam") -> 8
                name.contains("Erste") -> 9
                name.contains("lampe") -> 10
                name.contains("schild") -> 11
                name.contains("Drohne") -> 12
                name.contains("kamera") || name.contains("Kamera") -> 13
                name.contains("Handschuh", true) -> 14
                else -> 15
            }
        return "art/gear-atlas.png#$n"
    }

    fun load(context: Context, path: String): Bitmap? {
        cache.get(path)?.let {
            return it
        }
        return try {
            val bits = path.split('#')
            val raw =
                cache.get(bits[0])
                    ?: context.assets
                        .open(bits[0])
                        .use { BitmapFactory.decodeStream(it) }
                        ?.also { cache.put(bits[0], it) }
                    ?: return null
            val out =
                if (bits.size == 1) raw
                else {
                    val n = bits[1].toInt()
                    val cols =
                        if (bits[0].contains("portrait") || bits[0].contains("vehicle")) 2 else 4
                    val rows =
                        if (bits[0].contains("extras")) 3
                        else if (bits[0].contains("vehicle")) 2 else 4
                    val w = raw.width / cols
                    val h = raw.height / rows
                    Bitmap.createBitmap(raw, (n % cols) * w, (n / cols) * h, w, h)
                }
            cache.put(path, out)
            out
        } catch (_: Exception) {
            null
        }
    }

    fun portrait(context: Context, index: Int): Bitmap? =
        load(context, "art/portrait-atlas.png#${(index-1).coerceIn(0,7)}")

    fun vehicle(context: Context, type: String): Bitmap? {
        val extra =
            when (type) {
                "HELI" -> 0
                "HUND" -> 1
                "BEREITSCHAFT" -> 2
                "VERKEHR" -> 3
                "RETTUNG" -> 4
                "FEUERWEHR" -> 5
                else -> -1
            }
        return load(
            context,
            if (extra >= 0) "art/vehicle-extras-atlas.png#$extra"
            else
                "art/vehicle-atlas.png#${when(type){"SEK"->1
"MEK"->2
"KRIPO"->3
else->0}}",
        )
    }
}
