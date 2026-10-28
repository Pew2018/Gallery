package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.FavoriteFolder

@Dao
interface FavoriteFoldersDao {
    @Query("SELECT * FROM favorite_folders ORDER BY added_at DESC")
    fun getAll(): List<FavoriteFolder>

    @Query("SELECT path FROM favorite_folders")
    fun getPaths(): List<String>

    @Query("SELECT COUNT(*) FROM favorite_folders WHERE path = :path")
    fun contains(path: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(folder: FavoriteFolder)

    @Query("DELETE FROM favorite_folders WHERE path = :path")
    fun delete(path: String)

    @Query("UPDATE favorite_folders SET path = :newPath WHERE path = :oldPath")
    fun rename(oldPath: String, newPath: String)
}
