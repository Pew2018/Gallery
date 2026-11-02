package org.fossify.gallery.helpers.backup

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Owns and closes streams, including on failure. Limits refer to bytes, not UTF-16 characters. */
object BackupIo {
    const val MAX_BYTES = 8L * 1024 * 1024

    fun readText(input: InputStream?, maxBytes: Long = MAX_BYTES): String {
        require(maxBytes in 1..MAX_BYTES)
        requireNotNull(input) { "Document provider returned no input stream" }
        val bytes = input.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                if (count == 0) continue
                require(buffer.size().toLong() + count <= maxBytes) { "Backup is too large" }
                buffer.write(chunk, 0, count)
            }
            buffer.toByteArray()
        }
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(text.isNotBlank()) { "Backup is empty" }
        return text
    }

    fun writeText(output: OutputStream?, text: String) {
        writeText(output) { text }
    }

    /** Close output even if snapshot collection or encoding fails before writing. */
    fun writeText(output: OutputStream?, text: () -> String) {
        requireNotNull(output) { "Document provider returned no output stream" }
        output.use { it.write(text().toByteArray(Charsets.UTF_8)); it.flush() }
    }
}
