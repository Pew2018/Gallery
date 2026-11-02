package org.fossify.gallery.helpers.backup

import android.app.Application
import com.google.gson.JsonParser
import org.fossify.gallery.helpers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.*
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GalleryBackupZipCodecTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun encoded(snapshot: GalleryBackupSnapshot = fullBackupFixture(context)): ByteArray {
        val file = File.createTempFile("full-backup-test", ".zip", context.cacheDir)
        try { GalleryBackupZipCodec.encode(snapshot, file); return file.readBytes() } finally { file.delete() }
    }
    private fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> = linkedMapOf<String, ByteArray>().apply {
        ZipInputStream(bytes.inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) } }
    }
    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private fun reject(bytes: ByteArray) { assertThrows(IllegalArgumentException::class.java) { GalleryBackupZipCodec.decode(bytes.inputStream(), context.cacheDir) } }
    private fun remanifest(files: MutableMap<String, ByteArray>) {
        val entries = GalleryBackupManifest.payloadNames.map { GalleryBackupFile(it, files.getValue(it).size.toLong(), BackupDigest.sha256(files.getValue(it))) }
        files["manifest.json"] = GalleryBackupManifest.now("org.fossify.gallery", entries).encode().toByteArray()
    }
    @Test fun sixFilesRoundTripPreservesChineseAndEmptyFavoritesAndProfiles() {
        val snapshot = fullBackupFixture(context, folders = listOf(FavoriteBackupCodec.FolderRecord("/storage/OFFLINE/中文", 123)))
        val bytes = encoded(snapshot)
        assertEquals(GalleryBackupManifest.names, entries(bytes).keys.toList())
        assertEquals(0, entries(bytes).getValue("favorites.txt").size)
        assertEquals(snapshot, GalleryBackupZipCodec.decode(bytes.inputStream(), context.cacheDir))
    }
    @Test fun manifestContainsRealLengthsAndHashes() {
        val files = entries(encoded())
        val manifest = GalleryBackupManifest.decode(String(files.getValue("manifest.json"), Charsets.UTF_8))
        manifest.files.forEach { assertEquals(files.getValue(it.name).size.toLong(), it.size); assertEquals(BackupDigest.sha256(files.getValue(it.name)), it.sha256) }
        assertTrue(manifest.createdAt.contains('T')); assertEquals("org.fossify.gallery", manifest.appId)
    }
    @Test fun allPayloadsRoundTripIncludingSelectedProfileAndExtensions() {
        val profile = backupProfile()
        val snapshot = fullBackupFixture(context, listOf("/Pictures/中文.JPG"), listOf(FavoriteBackupCodec.FolderRecord("/Pictures/中文", 42)),
            PhotoProfileState(profiles = listOf(profile), selectedId = profile.id), mapOf("oxygen_bottom_nav_order" to "PHOTOS,ALL,FAVORITES"))
        assertEquals(snapshot, GalleryBackupZipCodec.decode(encoded(snapshot).inputStream(), context.cacheDir))
    }
    @Test fun missingEntriesAreRejected() { val files = entries(encoded()); files.remove("favorites.txt"); reject(zip(files)) }
    @Test fun unknownEntriesAreRejected() { val files = entries(encoded()); files["gallery.db"] = byteArrayOf(1); reject(zip(files)) }
    @Test fun pathTraversalAbsoluteAndDirectoryEntriesAreRejected() {
        for (name in listOf("../settings.txt", "/settings.txt", "dir/", "dir/settings.txt", "C:\\settings.txt")) {
            val files = entries(encoded()); files[name] = files.remove("settings.txt")!!; reject(zip(files))
        }
    }
    @Test fun duplicateCentralAndLocalEntriesAreRejected() {
        // Equal-length substitution creates duplicate ZIP names in both local and central headers.
        val files = entries(encoded()); files["settings.txX"] = files.getValue("settings.txt")
        val raw = zip(files)
        val from = "settings.txX".toByteArray(); val to = "settings.txt".toByteArray()
        for (i in 0..raw.size - from.size) if (from.indices.all { raw[i + it] == from[it] }) to.copyInto(raw, i)
        reject(raw)
    }
    @Test fun manifestDuplicateRecordsAreRejected() {
        val files = entries(encoded()); val root = JsonParser.parseString(String(files.getValue("manifest.json"))).asJsonObject
        val array = root.getAsJsonArray("files"); array.set(1, array[0].deepCopy())
        files["manifest.json"] = root.toString().toByteArray(); reject(zip(files))
    }
    @Test fun sizeAndHashMismatchesAreRejected() {
        for (field in listOf("size", "sha256")) {
            val files = entries(encoded()); val root = JsonParser.parseString(String(files.getValue("manifest.json"))).asJsonObject
            val file = root.getAsJsonArray("files")[0].asJsonObject
            if (field == "size") file.addProperty(field, 1) else file.addProperty(field, "0".repeat(64))
            files["manifest.json"] = root.toString().toByteArray(); reject(zip(files))
        }
    }
    @Test fun wrongFormatVersionAppIdAndDateAreRejected() {
        for ((field, value) in listOf("format" to "other", "version" to "2", "appId" to "other.app", "createdAt" to "2026-99-99T15:30:00+08:00")) {
            val files = entries(encoded()); val root = JsonParser.parseString(String(files.getValue("manifest.json"))).asJsonObject
            if (field == "version") root.addProperty(field, 2) else root.addProperty(field, value)
            files["manifest.json"] = root.toString().toByteArray(); reject(zip(files))
        }
    }
    @Test fun truncationAndTrailingGarbageAreRejected() {
        val raw = encoded()
        for (bytes in listOf(raw.copyOf(20), raw.copyOf(raw.size - 1), raw.copyOf(raw.size - 22), raw + byteArrayOf(1))) reject(bytes)
    }
    @Test fun compressedBombExceedingPerEntryBudgetIsRejected() {
        val files = entries(encoded()); files["favorites.txt"] = ByteArray(8 * 1024 * 1024 + 1) { 65 }; reject(zip(files))
    }
    @Test fun oversizedManifestRejectedEvenIfZipCompressesWell() {
        val files = entries(encoded()); files["manifest.json"] = ByteArray(65537) { 32 }; reject(zip(files))
    }
    @Test fun malformedJsonInvalidUtf8AndInvalidSettingsRejectedEvenWithCorrectChecksums() {
        for ((name, bytes) in listOf("photo_profiles.json" to "{broken}".toByteArray(),
            "oxygen_settings.json" to byteArrayOf(0xc3.toByte(), 0x28), "settings.txt" to "media_column_cnt=bad".toByteArray(),
            "favorites.txt" to "relative-path".toByteArray(), "favorite_folders.json" to "null".toByteArray())) {
            val files = entries(encoded()); files[name] = bytes; remanifest(files); reject(zip(files))
        }
    }
    @Test fun emptyNonFavoritePayloadsAreRejected() {
        for (name in GalleryBackupManifest.payloadNames.filterNot { it == "favorites.txt" }) {
            val files = entries(encoded()); files[name] = byteArrayOf(); remanifest(files); reject(zip(files))
        }
        reject(byteArrayOf())
    }
    @Test fun temporaryFilesAndProviderStreamsAreReleasedOnFailure() {
        val before = context.cacheDir.listFiles()!!.map { it.name }.toSet()
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) { override fun close() { closed = true } }
        reject(input.readBytes())
        assertThrows(IllegalArgumentException::class.java) { GalleryBackupZipCodec.decode(input, context.cacheDir) }
        assertTrue(closed)
        assertEquals(before, context.cacheDir.listFiles()!!.map { it.name }.toSet())
        assertThrows(IOException::class.java) { GalleryBackupZipCodec.decode(null, context.cacheDir) }
    }
    @Test fun standaloneFavoriteEmptyTextIsAllowedOnlyByFormatSpecificReader() {
        assertThrows(IllegalArgumentException::class.java) { BackupIo.readText(byteArrayOf().inputStream()) }
        assertTrue(FavoriteBackupCodec.decodeMediaPlan(BackupIo.readMediaFavorites(byteArrayOf().inputStream())).paths.isEmpty())
    }
    @Test fun totalBudgetIncludesFiveMaximumPayloadsAndManifest() {
        assertEquals(5 * BackupIo.MAX_BYTES + GalleryBackupZipCodec.MANIFEST_LIMIT, GalleryBackupZipCodec.TOTAL_LIMIT)
    }
    @Test fun providerReadFailureClosesStreamAndRemovesTemporaryZip() {
        var closed = false
        val before = context.cacheDir.listFiles()!!.map { it.name }.toSet()
        val source = object : InputStream() {
            override fun read(): Int = throw IOException("provider disconnected")
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { GalleryBackupZipCodec.decode(source, context.cacheDir) }
        assertTrue(closed); assertEquals(before, context.cacheDir.listFiles()!!.map { it.name }.toSet())
    }
    @Test fun compressedInputBudgetUsesActualReadCount() {
        var closed = false
        val source = object : InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { buffer.fill(0, offset, offset + length); return length }
            override fun close() { closed = true }
        }
        assertThrows(IllegalArgumentException::class.java) { GalleryBackupZipCodec.decode(source, context.cacheDir) }
        assertTrue(closed)
    }
    @Test fun duplicateMediaCountsAreReadAsMetadataWithoutChangingTheSixFileV1Format() {
        val snapshot = fullBackupFixture(context, listOf("/Pictures/a.jpg", "/Pictures/a.jpg", "/Pictures/b.jpg"))
        val raw = encoded(snapshot)
        assertEquals(GalleryBackupManifest.names, entries(raw).keys.toList())
        val decoded = GalleryBackupZipCodec.decode(raw.inputStream(), context.cacheDir)
        assertEquals(listOf("/Pictures/a.jpg", "/Pictures/b.jpg"), decoded.mediaFavorites)
        assertEquals(3, decoded.mediaTotal); assertEquals(1, decoded.mediaDuplicates)
    }

    @Test fun unsupportedVersionHasATypedReasonAndRemainsRejected() {
        val files = entries(encoded())
        val manifest = JsonParser.parseString(String(files.getValue("manifest.json"))).asJsonObject
        manifest.addProperty("version", 2)
        files["manifest.json"] = manifest.toString().toByteArray()
        val error = assertThrows(InvalidGalleryBackup::class.java) { GalleryBackupZipCodec.decode(zip(files).inputStream(), context.cacheDir) }
        assertEquals(InvalidGalleryBackupReason.UNSUPPORTED_VERSION, error.reason)
    }

    @Test fun centralCrcAndDirectorySignatureCorruptionAreRejected() {
        val original = encoded()
        val central = (0..original.size - 4).first { i -> original[i] == 0x50.toByte() && original[i + 1] == 0x4b.toByte() && original[i + 2] == 1.toByte() && original[i + 3] == 2.toByte() }
        for (offset in listOf(central, central + 16)) {
            val corrupt = original.copyOf()
            corrupt[offset] = (corrupt[offset].toInt() xor 0x40).toByte()
            reject(corrupt)
        }
    }

}
