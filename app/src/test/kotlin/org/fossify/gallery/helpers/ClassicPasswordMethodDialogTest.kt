package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.ClassicPasswordMethodDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, manifest = Config.NONE, qualifiers = "w360dp-h800dp-mdpi")
class ClassicPasswordMethodDialogTest {
    private fun texts(root: View): List<TextView> = buildList {
        if (root is TextView) add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) addAll(texts(root.getChildAt(i)))
    }

    @Test fun fourEntrancesShareRowsAndDismissBeforeDeliveringOnlyOneChoice() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val host = controller.get().apply { setTheme(R.style.OxygenClassicAlertDialog_Light) }
        try {
            for (favorite in listOf(true, false)) for (fallback in listOf(true, false)) for (biometric in listOf(true, false)) {
                val title = if (fallback) R.string.photo_password_fallback_title else
                    if (favorite) R.string.favorite_password_title else R.string.photo_password_title
                val labels = if (fallback) listOf(host.getString(R.string.photo_password_choose_pin), host.getString(R.string.photo_password_choose_pattern))
                    else listOf(host.getString(org.fossify.commons.R.string.pattern), host.getString(org.fossify.commons.R.string.pin)) +
                        if (biometric) listOf(host.getString(R.string.photo_password_biometric)) else emptyList()
                var calls = 0
                var chosen = -1
                lateinit var dialog: AlertDialog
                dialog = ClassicPasswordMethodDialog.show(host, title, labels,
                    description = if (fallback) R.string.photo_password_fallback_description else null,
                    onCancel = { fail("selection must not cancel") }) { index ->
                    assertFalse("old surface closes before opening credential input", dialog.isShowing)
                    calls++; chosen = index
                }
                val rows = texts(dialog.window!!.decorView).filter { it.isClickable && it !is android.widget.Button && it.text.toString() in labels }
                assertEquals(labels, rows.map { it.text.toString() })
                rows.forEach {
                    assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, it.layoutParams.width)
                    assertEquals(48, it.minHeight)
                    assertEquals(16f, it.textSize / host.resources.displayMetrics.scaledDensity, .01f)
                    assertTrue(it.background is android.graphics.drawable.RippleDrawable)
                }
                rows.last().performClick(); rows.first().performClick()
                dialog.cancel()
                assertEquals(1, calls); assertEquals(labels.lastIndex, chosen)
            }
        } finally { controller.destroy() }
    }

    @Test fun cancellationAndRequestGuardRemainOwnedByTheCaller() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val host = controller.get().apply { setTheme(R.style.OxygenClassicAlertDialog_Light) }
        try {
            var cancelled = 0
            val dialog = ClassicPasswordMethodDialog.show(host, R.string.photo_password_fallback_title, listOf("PIN", "Pattern"),
                description = R.string.photo_password_fallback_description, canChoose = { false },
                onCancel = { cancelled++ }, onChoice = { fail("stale request") })
            texts(dialog.window!!.decorView).first { it.text == "PIN" }.performClick()
            assertTrue(dialog.isShowing)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            dialog.cancel()
            assertEquals(1, cancelled)
        } finally { controller.destroy() }
    }
}
