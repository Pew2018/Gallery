package org.fossify.gallery.helpers.backup

import android.content.SharedPreferences
import java.io.IOException

/** One scoped editor, never prefs.clear()/all replacement. Roll back memory on a failed disk commit.
 * SharedPreferences has no database transaction: disk failure is surfaced even after rollback. */
internal object BackupPreferences {
    fun commit(prefs: SharedPreferences, values: Map<String, Any?>) {
        val before = values.keys.associateWith { key -> prefs.all[key] }
        fun edit(items: Map<String, Any?>): Boolean {
            val editor = prefs.edit()
            items.forEach { (key, value) ->
                when (value) {
                    null -> editor.remove(key)
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                    else -> error("Unsupported backup preference type")
                }
            }
            return editor.commit()
        }
        val failure = try {
            if (edit(values)) return
            IOException("Preference commit returned false")
        } catch (error: Exception) { error }
        val restored = runCatching { edit(before) }.getOrDefault(false)
        throw IOException("Backup preferences could not be persisted; rollback persisted=$restored", failure)
    }
}
