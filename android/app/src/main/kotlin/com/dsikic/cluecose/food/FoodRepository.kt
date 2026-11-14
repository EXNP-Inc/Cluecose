//
//  FoodRepository.kt
//  Cluecose
//
//  Persistence for the food log, mirroring the iOS `FoodLogStore` and the
//  `RealGlucoseRepository` base64-of-JSON idiom: a JSON array of `FoodEntry`
//  (the shared cross-platform schema, via `:core` FoodJson) encoded as base64
//  and stored in SharedPreferences under the SAME key the iOS port writes
//  (`cluecose.food.entries`).
//
//  • 30-day rolling retention: on load, entries older than 30 days are dropped
//    (and the pruned set is written back; their photos are removed too).
//  • Photos are stored as files in the app files dir under `food/photos/`
//    with a stable, predictable name (`{entryId}_{index}.jpg`) so
//    `FoodEntry.photoFileNames` (file names, not paths) stays portable and
//    the entry JSON never balloons.
//

package com.dsikic.cluecose.food

import android.content.Context
import android.util.Base64
import com.dsikic.cluecose.core.food.FoodEntry
import com.dsikic.cluecose.core.food.FoodJson
import java.io.File
import java.util.Calendar

/**
 * The food-log persistence store. All entries are read through [all], which
 * applies the 30-day rolling retention; writes go through [add] / [update] /
 * [delete] / [persistAll]. Photo files live in [photosDirectory].
 */
class FoodRepository(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // MARK: Photos directory

    /** The app-files `food/photos` directory, created on demand. */
    val photosDirectory: File by lazy {
        val dir = File(context.filesDir, PHOTOS_DIR)
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private fun photoFile(fileName: String): File = File(photosDirectory, fileName)

    // MARK: Entries

    /** All entries in persisted order (oldest → newest), retention applied. */
    fun all(): List<FoodEntry> = load()

    /**
     * Load entries from prefs, applying the 30-day rolling retention (dropped
     * entries are removed from disk too — their photos included).
     */
    private fun load(): List<FoodEntry> {
        val raw = prefs.getString(ENTRIES_KEY, null) ?: return emptyList()
        val json = try {
            String(Base64.decode(raw, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            return emptyList()
        }
        val decoded: List<FoodEntry> = FoodJson.decodeList(json)

        val nowSec = System.currentTimeMillis() / 1000.0
        val cutoff = nowSec - RETENTION_DAYS * 86_400.0
        val kept = decoded.filter { it.dateEpochSeconds >= cutoff }
        if (kept.size != decoded.size) {
            // Prune: drop the expired entries (and their photo files).
            for (dropped in decoded) {
                if (dropped.dateEpochSeconds < cutoff) deletePhotos(dropped.id)
            }
            save(kept)
        }
        return kept
    }

    /** Persist the full entry list (JSON array → base64, like the BLE store). */
    private fun save(entries: List<FoodEntry>) {
        try {
            val json = FoodJson.encodeList(entries)
            prefs.edit()
                .putString(ENTRIES_KEY, Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            // Persistence is best-effort here (mirrors iOS `try?`); the in-memory
            // state still holds the entry for this session.
        }
    }

    /** Persist an already-loaded full list (used after a batch insight update). */
    fun persistAll(entries: List<FoodEntry>) = save(entries)

    /** Insert a new entry (at the end = newest). */
    fun add(entry: FoodEntry) {
        val entries = load().toMutableList()
        entries.add(entry)
        save(entries)
    }

    /** Replace the entry with the same id (in place, keeping order). */
    fun update(entry: FoodEntry) {
        val entries = load().toMutableList()
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) entries[index] = entry else entries.add(entry)
        save(entries)
    }

    /** Remove an entry and its photo files. */
    fun delete(id: String) {
        val entries = load().toMutableList()
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) {
            deletePhotos(entries[index].id)
            entries.removeAt(index)
            save(entries)
        }
    }

    /**
     * Entries eaten on the same local-calendar day as [dayEpochSeconds]
     * (mirrors iOS `entries(on:)` — `Calendar.isDate(inSameDayAs:)`).
     */
    fun entries(onDayEpochSeconds: Double): List<FoodEntry> {
        val (dayStart, dayEnd) = localDayBoundaries(onDayEpochSeconds)
        return load().filter { it.dateEpochSeconds >= dayStart && it.dateEpochSeconds < dayEnd }
    }

    /** Entries within the trailing [days] days (inclusive of today). */
    fun entriesInLastDays(days: Int): List<FoodEntry> {
        val cutoff = System.currentTimeMillis() / 1000.0 - maxOf(days, 1) * 86_400.0
        return load().filter { it.dateEpochSeconds >= cutoff }
    }

    // MARK: Photos

    /**
     * Write one (already-compressed) JPEG photo file for an entry. Stable name
     * `{entryId}_{index}.jpg`. Returns the stored file name (for
     * `photoFileNames`).
     */
    fun savePhoto(jpegBytes: ByteArray, entryId: String, index: Int): String {
        val name = "${entryId}_$index.jpg"
        photoFile(name).writeBytes(jpegBytes)
        return name
    }

    /** Load the stored JPEG bytes for one entry (in `photoFileNames` order). */
    fun photoBytes(entry: FoodEntry): List<ByteArray> =
        entry.photoFileNames.mapNotNull { name ->
            val f = photoFile(name)
            if (f.exists()) f.readBytes() else null
        }

    /**
     * Remove one photo from an entry (file + name) and re-index the remaining
     * files so names stay `{id}_{i}.jpg`. Returns the updated entry, or null
     * when [index] is out of range. (The caller persists the result via
     * [update]; the removed file is deleted from disk here.)
     */
    fun removePhotoFrom(entry: FoodEntry, index: Int): FoodEntry? {
        if (index !in entry.photoFileNames.indices) return null
        val removedName = entry.photoFileNames[index]
        val remaining = entry.photoFileNames.toMutableList().apply { removeAt(index) }

        // Re-index the survivors to `{id}_{i}.jpg` (move any that shifted).
        val renamed = mutableListOf<String>()
        for ((i, existing) in remaining.withIndex()) {
            val newName = "${entry.id}_$i.jpg"
            if (newName != existing) {
                val old = photoFile(existing)
                if (old.exists()) old.renameTo(photoFile(newName))
            }
            renamed.add(newName)
        }
        // Delete the removed file only if it did not get shifted onto by a rename.
        if (removedName !in renamed) photoFile(removedName).delete()
        return entry.copy(photoFileNames = renamed)
    }

    /** Delete an entry's photo files from disk (idempotent). */
    fun deletePhotos(entryId: String) {
        val names = load().firstOrNull { it.id == entryId }?.photoFileNames ?: return
        for (name in names) {
            photoFile(name).delete()
        }
    }

    // MARK: Calendar helpers

    /**
     * The local-day `[start, end)` seconds window containing [epochSeconds]
     * (local calendar, so "today" matches the user's day, like iOS `Calendar`).
     */
    private fun localDayBoundaries(epochSeconds: Double): Pair<Double, Double> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = (epochSeconds * 1000).toLong()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis / 1000.0
        return start to (start + 86_400.0)
    }

    companion object {
        private const val PREFS_NAME = "cluecose"
        /** The cross-platform storage key (identical on the iOS port). */
        const val ENTRIES_KEY = "cluecose.food.entries"
        /** Rolling retention window in days: entries older are dropped on load. */
        const val RETENTION_DAYS = 30
        private const val PHOTOS_DIR = "food/photos"
    }
}
