package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.getSharedPrefs

object PhotoProfileRepository {
    const val KEY = "photo_profiles_v1"
    fun of(context: Context): PhotoProfileStore {
        val prefs = context.applicationContext.getSharedPrefs()
        return PhotoProfileStore(
            { prefs.getString(KEY, "").orEmpty() },
            { prefs.edit().putString(KEY, it).apply() }
        )
    }
}
