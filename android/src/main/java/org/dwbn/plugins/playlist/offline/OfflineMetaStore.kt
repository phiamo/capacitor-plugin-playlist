package org.dwbn.plugins.playlist.offline

import org.json.JSONObject
import java.io.File

/**
 * Plugin-owned download metadata: only the licence clock. Delivery URLs are never stored here
 * (Media3 keeps the request URI in its no-backup index). Lives under `noBackupFilesDir`.
 */
class OfflineMetaStore(private val file: File) {
    data class Entry(val acquiredAt: Long, val expiresAt: Long)

    @Synchronized
    fun get(downloadId: String): Entry? = read()[downloadId]

    @Synchronized
    fun put(downloadId: String, entry: Entry) {
        val all = read().toMutableMap()
        all[downloadId] = entry
        write(all)
    }

    @Synchronized
    fun remove(downloadId: String) {
        val all = read().toMutableMap()
        if (all.remove(downloadId) != null) {
            write(all)
        }
    }

    @Synchronized
    fun all(): Map<String, Entry> = read()

    private fun read(): Map<String, Entry> {
        if (!file.exists()) {
            return emptyMap()
        }
        return try {
            val root = JSONObject(file.readText())
            val out = LinkedHashMap<String, Entry>()
            for (key in root.keys()) {
                val item = root.optJSONObject(key) ?: continue
                out[key] = Entry(item.optLong("acquiredAt"), item.optLong("expiresAt"))
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun write(all: Map<String, Entry>) {
        val root = JSONObject()
        for ((id, entry) in all) {
            root.put(
                id,
                JSONObject().put("acquiredAt", entry.acquiredAt).put("expiresAt", entry.expiresAt)
            )
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }
}
