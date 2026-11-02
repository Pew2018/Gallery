package org.fossify.gallery.helpers.backup

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonObject
import org.fossify.commons.helpers.IS_USING_MODIFIED_APP_ICON
import org.fossify.gallery.helpers.PhotoProfileRepository
import java.io.File

/** Local checkpoint, not a portable backup. No credential/binding/permission values are captured. */
internal data class GalleryRestoreCheckpoint(
    val beforeDb: String, val afterDb: String,
    val beforePreferences: Map<String, Any?>, val afterPreferences: Map<String, Any?>,
    val mediaChanged: Boolean = true
)
internal class GalleryRestoreJournal(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "gallery-restore-checkpoint.json"))
    fun exists(): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    fun delete() { file.delete(); check(!exists()) { "Could not remove restore checkpoint" } }
    fun write(checkpoint: GalleryRestoreCheckpoint) {
        val root = JsonObject().apply {
            addProperty("version", 1); addProperty("beforeDb", checkpoint.beforeDb); addProperty("afterDb", checkpoint.afterDb)
            addProperty("mediaChanged", checkpoint.mediaChanged)
            add("before", encodeValues(checkpoint.beforePreferences)); add("after", encodeValues(checkpoint.afterPreferences))
        }
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Recovery checkpoint exceeds safe budget" }
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) } catch (error: Exception) { file.failWrite(output); throw error }
    }
    fun read(): GalleryRestoreCheckpoint {
        val bytes = file.openRead().use { input ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val size = input.read(chunk); if (size < 0) break
                require(size > 0 && out.size().toLong() + size <= MAX_BYTES)
                out.write(chunk, 0, size)
            }
            out.toByteArray()
        }
        val root = BackupJson.parse(GalleryBackupZipCodec.decodeUtf8(bytes, false), MAX_BYTES).asJsonObject
        require(root.get("version")?.toString() == "1")
        val before = decodeValues(root.getAsJsonObject("before"))
        val after = decodeValues(root.getAsJsonObject("after"))
        require(before.keys == after.keys)
        fun hash(key: String) = root.get(key).asString.also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        val mediaChanged = root.get("mediaChanged")?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean)
            it.asBoolean
        } ?: true // older v1 checkpoints did not distinguish relation types
        return GalleryRestoreCheckpoint(hash("beforeDb"), hash("afterDb"), before, after, mediaChanged)
    }
    companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
        private fun allowed(key: String) = key in LegacySettingsCodec.supportedKeys || OxygenSettingsBackupCodec.supportedKey(key) ||
            key == PhotoProfileRepository.KEY || key == IS_USING_MODIFIED_APP_ICON
        private fun encodeValues(values: Map<String, Any?>): JsonObject = JsonObject().apply {
            values.forEach { (key, value) ->
                require(allowed(key)) { "Unsafe checkpoint key" }
                val item = JsonObject()
                when (value) {
                    null -> item.addProperty("type", "absent")
                    is String -> { item.addProperty("type", "string"); item.addProperty("value", value) }
                    is Int -> { item.addProperty("type", "int"); item.addProperty("value", value) }
                    is Boolean -> { item.addProperty("type", "boolean"); item.addProperty("value", value) }
                    is Float -> { require(value.isFinite()); item.addProperty("type", "float"); item.addProperty("value", value) }
                    is Set<*> -> {
                        item.addProperty("type", "set")
                        item.add("value", com.google.gson.JsonArray().apply { value.forEach { require(it is String); add(it) } })
                    }
                    else -> error("Unsupported checkpoint value")
                }
                add(key, item)
            }
        }
        private fun decodeValues(values: JsonObject): Map<String, Any?> {
            require(values.size() <= 11000)
            return values.entrySet().associate { (key, raw) ->
                require(allowed(key))
                val item = raw.asJsonObject
                val value: Any? = when (item.get("type").asString) {
                    "absent" -> null
                    "string" -> item.get("value").asString
                    "int" -> item.get("value").asString.toInt()
                    "boolean" -> item.get("value").asBoolean
                    "float" -> item.get("value").asFloat.also { require(it.isFinite()) }
                    "set" -> item.getAsJsonArray("value").map { it.asString }.toSet()
                    else -> error("Invalid checkpoint value type")
                }
                key to value
            }
        }
    }
}
