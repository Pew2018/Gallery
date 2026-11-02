package org.fossify.gallery.helpers.backup

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal

/** Unlike JsonParser's lenient entry point, reject comments, duplicate members and trailing data. */
internal object BackupJson {
    fun parse(text: String, maxBytes: Long = BackupIo.MAX_BYTES): JsonElement {
        require(text.toByteArray(Charsets.UTF_8).size <= maxBytes) { "Backup is too large" }
        try {
            return JsonReader(StringReader(text.removePrefix("\uFEFF"))).use { reader ->
                reader.isLenient = false
                val root = read(reader, 0)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON data" }
                root
            }
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid backup JSON", error)
        }
    }

    private fun read(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 24) { "JSON nesting limit exceeded" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().also { obj ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(!obj.has(key)) { "Duplicate JSON member: $key" }
                    obj.add(key, read(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().also { array ->
                reader.beginArray()
                while (reader.hasNext()) {
                    require(array.size() < 100000) { "Too many JSON records" }
                    array.add(read(reader, depth + 1))
                }
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> error("Unexpected JSON token")
        }
    }
}
