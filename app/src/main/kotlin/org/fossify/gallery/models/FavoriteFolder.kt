package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Independent of media favorites. Paths are unique, and invalid paths are
 * removed only after the storage volume is confirmed accessible.
 */
@Entity(tableName = "favorite_folders")
data class FavoriteFolder(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)
