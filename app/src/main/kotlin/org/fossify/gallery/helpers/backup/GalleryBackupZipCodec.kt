package org.fossify.gallery.helpers.backup

import org.fossify.gallery.helpers.FavoriteBackupCodec
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

internal class InvalidGalleryBackup(cause: Exception) : IllegalArgumentException("Invalid complete backup", cause)

/** Fixed names only. No archive entry is ever resolved as a filesystem destination. */
internal object GalleryBackupZipCodec {
    const val MANIFEST_LIMIT = 64L * 1024
    const val TOTAL_LIMIT = 40L * 1024 * 1024 + MANIFEST_LIMIT // five 8 MiB payloads plus manifest
    const val ZIP_LIMIT = 48L * 1024 * 1024 // accommodates incompressible data and ZIP headers

    fun encode(snapshot: GalleryBackupSnapshot, target: File, appId: String = "org.fossify.gallery") {
        require(LegacySettingsCodec.decode(snapshot.legacySettings).applicable)
        require(FavoriteBackupCodec.decodeMediaPlan(FavoriteBackupCodec.encodeMediaPaths(snapshot.mediaFavorites)).invalid == 0)
        val work = File(target.parentFile, "backup-payloads-${UUID.randomUUID()}")
        require(work.mkdirs())
        try {
            val payloads = listOf<Pair<String, () -> String>>(
                "settings.txt" to { snapshot.legacySettings },
                "favorites.txt" to { FavoriteBackupCodec.encodeMediaPaths(snapshot.mediaFavorites) },
                "favorite_folders.json" to { FavoriteBackupCodec.encodeFolders(snapshot.favoriteFolders) },
                "photo_profiles.json" to { PhotoProfileBackupCodec.encode(snapshot.photoProfiles) },
                "oxygen_settings.json" to { OxygenSettingsBackupCodec.encode(snapshot.oxygenSettings) })
            val files = payloads.map { (name, encode) ->
                val bytes = encode().toByteArray(Charsets.UTF_8)
                require(bytes.size <= BackupIo.MAX_BYTES && (bytes.isNotEmpty() || name == "favorites.txt"))
                File(work, name).outputStream().use { it.write(bytes) }
                GalleryBackupFile(name, bytes.size.toLong(), BackupDigest.sha256(bytes))
            }
            File(work, "manifest.json").writeText(GalleryBackupManifest.now(appId, files).encode(), Charsets.UTF_8)
            ZipOutputStream(target.outputStream().buffered(), Charsets.UTF_8).use { zip ->
                GalleryBackupManifest.names.forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    File(work, name).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            inspect(target) // complete central directory, local entries, CRCs, sizes and hashes
        } catch (error: Exception) { target.delete(); throw error }
        finally { work.deleteRecursively() }
    }

    fun decode(input: InputStream?, cache: File): GalleryBackupSnapshot {
        if (input == null) throw IOException("Document provider returned no input stream")
        val file = File(cache, "backup-read-${UUID.randomUUID()}.zip")
        try {
            input.use { source -> file.outputStream().use { copyBounded(source, it, ZIP_LIMIT) } }
            try {
                inspect(file)
                var settings: String? = null
                var media: List<String>? = null
                var folders: List<FavoriteBackupCodec.FolderRecord>? = null
                var profiles: org.fossify.gallery.helpers.PhotoProfileState? = null
                var oxygen: OxygenSettingsSnapshot? = null
                // Consume the same local entry bytes whose hashes were validated, not central offsets.
                ZipInputStream(file.inputStream().buffered(), Charsets.UTF_8).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val text = decodeUtf8(zip.readBytes(), entry.name == "favorites.txt")
                        when (entry.name) {
                            "settings.txt" -> { require(LegacySettingsCodec.decode(text).applicable); settings = text }
                            "favorites.txt" -> {
                                val plan = FavoriteBackupCodec.decodeMediaPlan(text)
                                require(plan.invalid == 0); media = plan.paths
                            }
                            "favorite_folders.json" -> folders = FavoriteBackupCodec.decodeFolders(text)
                            "photo_profiles.json" -> profiles = PhotoProfileBackupCodec.decodeStrict(text)
                            "oxygen_settings.json" -> oxygen = OxygenSettingsBackupCodec.decode(text)
                        }
                        zip.closeEntry()
                    }
                }
                return GalleryBackupSnapshot(requireNotNull(settings), requireNotNull(media), requireNotNull(folders),
                    requireNotNull(profiles), requireNotNull(oxygen))
            } catch (error: Exception) { throw InvalidGalleryBackup(error) }
        } finally { file.delete() }
    }

    internal fun inspect(file: File): GalleryBackupManifest {
        require(file.length() in 22..ZIP_LIMIT) { "Invalid ZIP size" }
        RandomAccessFile(file, "r").use { source ->
            require(source.readInt() == 0x504b0304) { "ZIP must begin with a file header" }
            // Reject truncation/trailing bytes. ZIP64 is unnecessary within these size budgets.
            val count = minOf(source.length(), 65557).toInt()
            source.seek(source.length() - count)
            val tail = ByteArray(count).also(source::readFully)
            val eocd = (tail.size - 22 downTo 0).firstOrNull { i ->
                tail[i] == 0x50.toByte() && tail[i + 1] == 0x4b.toByte() && tail[i + 2] == 5.toByte() && tail[i + 3] == 6.toByte() &&
                    i + 22 + ((tail[i + 20].toInt() and 255) or ((tail[i + 21].toInt() and 255) shl 8)) == tail.size
            }
            require(eocd != null) { "Missing ZIP end record" }
        }
        val central = ZipFile(file, Charsets.UTF_8).use { zip ->
            val entries = mutableListOf<ZipEntry>()
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                require(entries.size < 6) { "Too many ZIP entries" }
                entries.add(enumeration.nextElement())
            }
            require(entries.size == 6 && entries.none { it.isDirectory } &&
                entries.map { it.name }.toSet() == GalleryBackupManifest.names.toSet()) { "Unknown, duplicate or missing ZIP entries" }
            entries.associate { it.name to it }
        }
        val actual = linkedMapOf<String, GalleryBackupFile>()
        var manifestText: String? = null
        var total = 0L
        ZipInputStream(file.inputStream().buffered(), Charsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entry.name in central && entry.name !in actual && !entry.isDirectory) { "Invalid local ZIP entry" }
                val limit = if (entry.name == "manifest.json") MANIFEST_LIMIT else BackupIo.MAX_BYTES
                val digest = MessageDigest.getInstance("SHA-256")
                val crc = CRC32()
                val manifest = if (entry.name == "manifest.json") ByteArrayOutputStream() else null
                val buffer = ByteArray(8192)
                var size = 0L
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    require(read > 0) { "ZIP stream made no progress" }
                    size += read; total += read
                    require(size <= limit && total <= TOTAL_LIMIT) { "ZIP expansion limit exceeded" }
                    digest.update(buffer, 0, read); crc.update(buffer, 0, read); manifest?.write(buffer, 0, read)
                }
                require(crc.value == central.getValue(entry.name).crc && size == central.getValue(entry.name).size &&
                    entry.compressedSize == central.getValue(entry.name).compressedSize && entry.method == central.getValue(entry.name).method) { "ZIP local/central CRC mismatch" }
                actual[entry.name] = GalleryBackupFile(entry.name, size, BackupDigest.hex(digest.digest()))
                if (manifest != null) manifestText = decodeUtf8(manifest.toByteArray(), false)
                zip.closeEntry()
            }
        }
        require(actual.keys == central.keys) { "Missing local ZIP entries" }
        val manifest = GalleryBackupManifest.decode(requireNotNull(manifestText))
        manifest.files.forEach { require(actual[it.name] == it) { "Manifest size or SHA-256 mismatch: ${it.name}" } }
        return manifest
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(8192)
        var count = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            require(read > 0) { "Input stream made no progress" }
            count += read; require(count <= limit) { "ZIP exceeds compressed size budget" }
            output.write(buffer, 0, read)
        }
    }

    internal fun decodeUtf8(bytes: ByteArray, allowEmpty: Boolean): String {
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(allowEmpty || text.isNotBlank()) { "Empty backup component" }
        return text
    }
}
