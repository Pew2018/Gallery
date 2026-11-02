package org.fossify.gallery.helpers.backup

import android.content.Context
import org.fossify.gallery.helpers.*

internal fun fullBackupFixture(context: Context, media: List<String> = emptyList(), folders: List<FavoriteBackupCodec.FolderRecord> = emptyList(),
    profiles: PhotoProfileState = PhotoProfileState(), oxygen: Map<String, Any> = emptyMap(), legacy: Map<String, Any> = emptyMap()): GalleryBackupSnapshot =
    GalleryBackupSnapshot(LegacySettingsCodec.encode(collectLegacySettings(Config(context)).apply { putAll(legacy) }),
        media, folders, profiles, OxygenSettingsSnapshot(values = oxygen))

internal fun backupProfile(id: String = "d4dfe496-4e61-4e85-a01b-18e967e59a53", name: String = "旅行照片", path: String = "/storage/OFFLINE/旅行") =
    PhotoProfile(id, name, listOf(PhotoSource(PhotoSourceType.FOLDER, path), PhotoSource(PhotoSourceType.FAVORITES)))
