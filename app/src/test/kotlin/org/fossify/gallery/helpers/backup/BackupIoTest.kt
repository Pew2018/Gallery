package org.fossify.gallery.helpers.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class BackupIoTest {
    private class Input(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }
    @Test fun utf8BomRoundTripAndStreamsClosed() {
        val input = Input("\uFEFF路径=中文\n".toByteArray())
        assertEquals("路径=中文\n", BackupIo.readText(input))
        assertTrue(input.closed)
        var closed = false
        val out = object : ByteArrayOutputStream() { override fun close() { closed = true } }
        BackupIo.writeText(out, "中文\n")
        assertEquals("中文\n", out.toString("UTF-8")); assertTrue(closed)
    }
    @Test fun emptyFileFailsAndCloses() {
        val input = Input(byteArrayOf())
        assertThrows(IllegalArgumentException::class.java) { BackupIo.readText(input) }
        assertTrue(input.closed)
    }
    @Test fun byteLimitIncludesMultibyteCharactersAndCloses() {
        val input = Input("中文".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { BackupIo.readText(input, 5) }
        assertTrue(input.closed)
    }
    @Test fun malformedUtf8RejectedAndClosed() {
        val input = Input(byteArrayOf(0xc3.toByte(), 0x28))
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { BackupIo.readText(input) }
        assertTrue(input.closed)
    }
    @Test fun nullProviderStreamsFail() {
        assertThrows(IllegalArgumentException::class.java) { BackupIo.readText(null) }
        assertThrows(IllegalArgumentException::class.java) { BackupIo.writeText(null, "a") }
    }
    @Test fun failedReadClosesInput() {
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("provider unavailable")
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { BackupIo.readText(input) }; assertTrue(closed)
    }
    @Test fun failedWriteClosesOutput() {
        var closed = false
        val out = object : OutputStream() {
            override fun write(value: Int): Unit = throw IOException("full")
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { BackupIo.writeText(out, "a") }; assertTrue(closed)
    }
    @Test fun strictJsonRejectsMalformedAndDuplicateMembers() {
        for (text in listOf("{'a':1}", "{\"a\":1,}", "{\"a\":1,\"a\":2}", "{} trailing", "// comment\n{}", "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]]]]]]]]]]")) {
            assertThrows(text, IllegalArgumentException::class.java) { BackupJson.parse(text) }
        }
    }
}
