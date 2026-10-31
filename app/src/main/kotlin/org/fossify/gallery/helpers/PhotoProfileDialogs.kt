package org.fossify.gallery.helpers

import android.app.Activity
import org.fossify.gallery.R

/** Retains the compact profile switch dialog; profile management lives on its own page. */
class PhotoProfileDialogs(private val activity: Activity, private val changed: () -> Unit) {
    private val store = PhotoProfileRepository.of(activity)
    private fun text(id: Int) = activity.getString(id)
    fun switch() {
        val state = store.state()
        if (state.profiles.isEmpty()) { edit(); return }
        val builder = OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_switch))
            .setSingleChoiceItems(state.profiles.map { it.name }.toTypedArray(), state.profiles.indexOf(state.current)) { dialog, index ->
                store.select(state.profiles[index].id)
                dialog.dismiss()
                changed()
            }.setNegativeButton(org.fossify.commons.R.string.cancel, null)
        OxygenClassicDialog.show(activity, builder)
    }

    fun edit(profile: PhotoProfile? = null, creating: Boolean = profile == null) {
        org.fossify.gallery.activities.PhotoProfileEditActivity.launch(activity, profile, creating)
    }

}
