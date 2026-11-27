package de.einsatzleitstelle110

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/** A slot is explicit; the continuing autosave remains owned by [GameStore]. */
data class SlotInfo(
    val slot: Int,
    val name: String,
    val day: Int? = null,
    val region: String? = null,
    val savedAt: Long? = null,
    val hasSave: Boolean = false,
    val recoveredFromBackup: Boolean = false,
    val corrupt: Boolean = false,
)

/**
 * Five independent native save slots. Names are metadata, never file paths. Each replacement
 * is validated and fsynced before an atomic rename; only a validated previous primary may
 * replace its backup. A corrupt primary therefore cannot destroy the last readable save.
 */
class SlotRepository(private val directory: File) {
    private data class Saved(val name: String, val time: Long?, val state: GameState)
    private val lock = locks.getOrPut(directory.canonicalPath) { Any() }

    private fun primary(slot: Int): File {
        require(slot in 1..SLOT_COUNT) { "Speicherplatz muss zwischen 1 und $SLOT_COUNT liegen." }
        return File(directory, "slot-$slot.json")
    }

    private fun backup(slot: Int) = File(directory, primary(slot).name + ".backup")

    private fun checkedName(name: String): String {
        val clean = name.trim()
        require(clean.length in 1..48 && clean.none { it.isISOControl() }) {
            "Spielstandname: 1–48 Zeichen ohne Steuerzeichen erforderlich."
        }
        return clean
    }

    private fun decode(file: File): Saved {
        if (!file.isFile || file.length() !in 2..MAX_SAVE_BYTES) throw IOException("Spielstand fehlt oder ist zu groß.")
        val root = JSONObject(file.readText(Charsets.UTF_8))
        // A direct native save is accepted too, so existing native exports retain migrations.
        if (!root.has("slotFormat")) return Saved("Importierter Spielstand", null, GameStore.fromJson(root))
        require(root.getInt("slotFormat") == 1) { "Unbekanntes Speicherplatzformat." }
        val name = checkedName(root.getString("name"))
        val time = root.optLong("savedAt", 0).takeIf { it > 0 }
        return Saved(name, time, GameStore.fromJson(root.getJSONObject("state")))
    }

    private fun readable(file: File): Saved? = try { decode(file) } catch (_: Exception) { null }

    private fun info(slot: Int): SlotInfo {
        val base = primary(slot)
        val old = backup(slot)
        val current = readable(base)
        val saved = current ?: readable(old)
        return if (saved == null) SlotInfo(slot, "Speicherplatz $slot", corrupt = base.exists() || old.exists())
        else SlotInfo(slot, saved.name, saved.state.day, saved.state.region, saved.time,
            hasSave = true, recoveredFromBackup = current == null)
    }

    fun list(): List<SlotInfo> = synchronized(lock) { (1..SLOT_COUNT).map(::info) }

    /** Call with a game-thread state; serialization uses a detached value snapshot. */
    fun save(slot: Int, name: String, state: GameState): SlotInfo {
        primary(slot) // Validate arguments before copying or touching disk.
        val label = checkedName(name)
        val snapshot = GameSnapshots.capture(state)
        return synchronized(lock) {
            val json = GameStore.toJson(snapshot)
            GameStore.fromJson(json) // Do not replace a good save with an invalid state.
            val raw = JSONObject().put("slotFormat", 1).put("name", label)
                .put("savedAt", System.currentTimeMillis()).put("state", json).toString()
                .toByteArray(Charsets.UTF_8)
            require(raw.size.toLong() <= MAX_SAVE_BYTES) { "Spielstand überschreitet das Speicherbudget." }
            check(directory.isDirectory || directory.mkdirs()) { "Speicherverzeichnis konnte nicht angelegt werden." }
            val base = primary(slot)
            val old = backup(slot)
            if (readable(base) != null) writeSaveAtomically(old, base.readBytes())
            writeSaveAtomically(base, raw)
            info(slot)
        }
    }

    fun load(slot: Int): GameState = synchronized(lock) {
        val current = readable(primary(slot)) ?: readable(backup(slot))
        current?.state ?: throw IOException("Speicherplatz $slot ist leer oder beschädigt.")
    }

    fun delete(slot: Int) = synchronized(lock) {
        val files = listOf(primary(slot), backup(slot), File(directory, primary(slot).name + ".tmp"), File(directory, backup(slot).name + ".tmp"))
        for (file in files) if (file.exists() && !file.delete()) throw IOException("Speicherplatz $slot konnte nicht gelöscht werden.")
    }

    companion object {
        const val SLOT_COUNT = 5
        private const val MAX_SAVE_BYTES = 32L * 1024 * 1024
        private val locks = ConcurrentHashMap<String, Any>()
    }
}

/** Android facade; slots never overwrite the active autosave until the UI explicitly loads one. */
object SaveSlots {
    private fun repository(context: Context) = SlotRepository(File(context.filesDir, "native-save-slots"))
    fun list(context: Context): List<SlotInfo> = repository(context).list()
    fun save(context: Context, slot: Int, name: String, state: GameState): SlotInfo = repository(context).save(slot, name, state)
    fun load(context: Context, slot: Int): GameState = repository(context).load(slot)
    fun delete(context: Context, slot: Int) = repository(context).delete(slot)
}

internal fun writeSaveAtomically(target: File, raw: ByteArray) {
    val temp = File(target.parentFile, target.name + ".tmp")
    try {
        FileOutputStream(temp).use { stream -> stream.write(raw); stream.fd.sync() }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        if (temp.exists()) temp.delete()
    }
}
