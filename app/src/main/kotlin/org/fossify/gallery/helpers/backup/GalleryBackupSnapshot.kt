package org.fossify.gallery.helpers.backup

import android.content.Context
import com.google.gson.Gson
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.*
import java.security.MessageDigest

internal data class GalleryBackupSnapshot(
    val legacySettings: String,
    val mediaFavorites: List<String>,
    val favoriteFolders: List<FavoriteBackupCodec.FolderRecord>,
    val photoProfiles: PhotoProfileState,
    val oxygenSettings: OxygenSettingsSnapshot,
    val mediaTotal: Int = mediaFavorites.size,
    val mediaDuplicates: Int = mediaFavorites.size - mediaFavorites.distinct().size
)

/** Local-only concurrency evidence. NEVER serialize preferences into a portable archive. */
internal data class GalleryLocalBackupState(
    val snapshot: GalleryBackupSnapshot,
    val preferences: Map<String, Any?>,
    val token: String,
    val authorizationToken: String = token
)

internal object BackupDigest {
    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    fun preferences(values: Map<String, Any?>): String = Gson().toJson(values.toSortedMap().mapValues {
        if (it.value is Set<*>) (it.value as Set<*>).map { value -> value.toString() }.sorted() else it.value
    })
    fun relations(media: List<String>, folders: List<FavoriteBackupCodec.FolderRecord>): String = sha256(
        Gson().toJson(listOf(media.sorted(), folders.sortedBy { it.path })).toByteArray(Charsets.UTF_8))
}

internal class GalleryBackupCollector(private val context: Context, private val database: GalleryDatabase) {
    fun capture(): GalleryLocalBackupState = BackupOperationRunner.serialized {
        repeat(3) {
            val prefs = context.getSharedPrefs()
            val before = prefs.all.toMap()
            val revision = PhotoContentRevision.current()
            var media = emptyList<String>()
            var folders = emptyList<FavoriteBackupCodec.FolderRecord>()
            database.runInTransaction {
                media = database.FavoritesDao().getValidFavoritePaths().sorted()
                folders = database.FavoriteFoldersDao().getAll().map { FavoriteBackupCodec.FolderRecord(it.path, it.addedAt) }.sortedBy { it.path }
            }
            val config = Config(context)
            val legacy = LegacySettingsCodec.encode(collectLegacySettings(config))
            val raw = prefs.getString(PhotoProfileRepository.KEY, "").orEmpty()
            val profiles = if (raw.isBlank()) PhotoProfileState() else PhotoProfileBackupCodec.decodeStrict(raw)
            val oxygen = OxygenSettingsBackupCodec.capture(context)
            val after = prefs.all.toMap()
            var relationsStillMatch = false
            database.runInTransaction {
                relationsStillMatch = BackupDigest.relations(media, folders) == BackupDigest.relations(
                    database.FavoritesDao().getValidFavoritePaths(), database.FavoriteFoldersDao().getAll()
                        .map { FavoriteBackupCodec.FolderRecord(it.path, it.addedAt) })
            }
            if (before == after && after == prefs.all && relationsStillMatch && revision == PhotoContentRevision.current()) {
                val snapshot = GalleryBackupSnapshot(legacy, media, folders, profiles, oxygen)
                val token = BackupDigest.sha256((BackupDigest.preferences(after) + BackupDigest.relations(media, folders)).toByteArray(Charsets.UTF_8))
                return@serialized GalleryLocalBackupState(snapshot, after, token)
            }
        }
        error("Application state changed during snapshot; retry backup")
    }
}
