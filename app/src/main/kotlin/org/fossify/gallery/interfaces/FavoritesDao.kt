package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.Favorite

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(favorite: Favorite)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(favorites: List<Favorite>)

    @Query("SELECT full_path FROM favorites")
    fun getValidFavoritePaths(): List<String>

    @Query("SELECT COUNT(*) FROM favorites")
    fun getFavoritesCount(): Long

    @Query("SELECT id FROM favorites WHERE full_path = :path")
    fun isFavorite(path: String): Boolean

    @Query("UPDATE OR REPLACE favorites SET filename = :newFilename, full_path = :newFullPath, parent_path = :newParentPath WHERE full_path = :oldPath")
    fun updateFavorite(newFilename: String, newFullPath: String, newParentPath: String, oldPath: String)

    @Query("DELETE FROM favorites WHERE full_path = :path")
    fun deleteFavoritePath(path: String)

    @Query("DELETE FROM favorites")
    fun clearFavorites()
}
