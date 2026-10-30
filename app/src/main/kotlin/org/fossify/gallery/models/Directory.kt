package org.fossify.gallery.models

import android.content.Context
import androidx.room.*
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.helpers.FastScrollerBubble
import org.fossify.gallery.helpers.RECYCLE_BIN

@Entity(tableName = "directories", indices = [Index(value = ["path"], unique = true)])
data class Directory(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "thumbnail") var tmb: String,
    @ColumnInfo(name = "filename") var name: String,
    @ColumnInfo(name = "media_count") var mediaCnt: Int,
    @ColumnInfo(name = "last_modified") var modified: Long,
    @ColumnInfo(name = "date_taken") var taken: Long,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "location") var location: Int,
    @ColumnInfo(name = "media_types") var types: Int,
    @ColumnInfo(name = "sort_value") var sortValue: String,

    // used with "Group direct subfolders" enabled
    @Ignore var subfoldersCount: Int = 0,
    @Ignore var subfoldersMediaCount: Int = 0,
    @Ignore var containsMediaFilesDirectly: Boolean = true
) {

    constructor() : this(null, "", "", "", 0, 0L, 0L, 0L, 0, 0, "", 0, 0)

    @Ignore var folderStatuses: String = ""

    fun getBubbleText(sorting: Int, context: Context, dateFormat: String? = null, timeFormat: String? = null): String {
        val fallback = FastScrollerBubble.displayName(name, path, context.getString(R.string.unknown_file_location))
        return when {
            sorting and SORT_BY_NAME != 0 -> fallback
            sorting and SORT_BY_PATH != 0 -> path.ifBlank { fallback }
            sorting and SORT_BY_SIZE != 0 -> size.formatSize()
            sorting and SORT_BY_DATE_MODIFIED != 0 ->
                FastScrollerBubble.dateOrFallback(modified, fallback) {
                    modified.formatDate(context, dateFormat, timeFormat)
                }
            sorting and SORT_BY_RANDOM != 0 -> fallback
            sorting and SORT_BY_CUSTOM != 0 -> fallback
            else -> FastScrollerBubble.dateOrFallback(taken, fallback) { taken.formatDate(context) }
        }
    }

    fun areFavorites() = path == FAVORITES

    fun isRecycleBin() = path == RECYCLE_BIN

    fun getKey() = ObjectKey("$path-$modified")
}
