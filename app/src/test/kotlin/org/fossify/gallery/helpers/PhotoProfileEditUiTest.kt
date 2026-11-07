package org.fossify.gallery.helpers

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import com.google.android.material.textfield.TextInputLayout
import org.fossify.gallery.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PhotoProfileEditUiTest {
    @Test fun nameLabelAndPageInputUseReadableClassicThemeColors() {
        val context = RuntimeEnvironment.getApplication()
        val themed = ContextThemeWrapper(context, R.style.AppTheme)
        val root = LayoutInflater.from(themed).inflate(
            R.layout.activity_photo_profile_edit, FrameLayout(themed), false
        )
        val label = root.findViewById<TextView>(R.id.photo_profile_edit_name_label)
        val field = root.findViewById<EditText>(R.id.photo_profile_edit_name)
        val input = root.findViewById<TextInputLayout>(R.id.photo_profile_edit_name_input)
        val scroll = root.findViewById<ScrollView>(R.id.photo_profile_edit_scroll)
        assertNotNull(label)
        assertNotNull(field)
        assertNotNull(input)
        assertNotNull(scroll)
        assertEquals(12f, label.textSize / context.resources.displayMetrics.scaledDensity, 0.1f)

        val accents = listOf(0xFF7DC22F.toInt(), 0xFF42A5F5.toInt(), 0xFFB0BEC5.toInt())
        for (dark in listOf(false, true)) for (accent in accents) {
            val tokens = OxygenThemeColors.resolve(
                classic = true,
                mode = if (dark) "dark" else "light",
                systemDark = dark,
                rawAccent = accent,
                legacyBackground = Color.WHITE,
                legacyText = Color.BLACK
            )
            label.setTextColor(tokens.secondaryText)
            OxygenClassicPageInput.style(input, tokens)
            OxygenClassicPageInput.style(field, tokens)
            assertEquals(tokens.primaryText, field.currentTextColor)
            assertEquals(tokens.secondaryText, field.currentHintTextColor)
            assertEquals(TextInputLayout.BOX_BACKGROUND_OUTLINE, input.boxBackgroundMode)
            assertEquals((1 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1), input.boxStrokeWidth)
            assertEquals((2 * context.resources.displayMetrics.density).toInt().coerceAtLeast(2), input.boxStrokeWidthFocused)
            assertEquals(null, field.background)
            assertTrue(OxygenThemeColors.contrastRatio(field.currentTextColor, tokens.pageBackground) >= 4.5)
            assertTrue(OxygenThemeColors.contrastRatio(field.currentHintTextColor, tokens.pageBackground) >= 4.5)
            assertTrue(OxygenThemeColors.contrastRatio(tokens.interactiveAccent, tokens.pageBackground) >= 3.0)
        }
    }

    @Test fun legacyThemeTextIsAdjustedOnlyWhenThePagePairLacksContrast() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
        val field = EditText(context)
        val darkPage = OxygenThemeColors.resolve(
            classic = false,
            mode = "system",
            systemDark = false,
            rawAccent = 0xFFB0BEC5.toInt(),
            legacyBackground = Color.BLACK,
            legacyText = 0xFF303030.toInt()
        )
        OxygenClassicPageInput.style(field, darkPage)
        val safeText = OxygenClassicPageInput.readableTextColor(darkPage.primaryText, darkPage)
        assertEquals(safeText, field.currentTextColor)
        assertTrue(OxygenThemeColors.contrastRatio(safeText, darkPage.pageBackground) >= 4.5)
        assertTrue(OxygenThemeColors.contrastRatio(field.currentHintTextColor, darkPage.pageBackground) >= 4.5)
    }

    @Test fun inputSemanticsAndCompactScrollableLayoutAreRetained() {
        val context = RuntimeEnvironment.getApplication()
        val themed = ContextThemeWrapper(context, R.style.AppTheme)
        val root = LayoutInflater.from(themed).inflate(
            R.layout.activity_photo_profile_edit, FrameLayout(themed), false
        )
        val field = root.findViewById<EditText>(R.id.photo_profile_edit_name)
        val label = root.findViewById<TextView>(R.id.photo_profile_edit_name_label)
        val addSource = root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.photo_profile_edit_add_source)
        val sources = root.findViewById<android.widget.LinearLayout>(R.id.photo_profile_edit_sources)
        assertEquals(1, field.maxLines)
        assertEquals((56 * context.resources.displayMetrics.density).toInt(), field.minimumHeight)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, label.layoutParams.height)
        assertNotNull(root.findViewById<TextView>(R.id.photo_profile_edit_sources_title))
        assertEquals(0, (sources.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals((56 * context.resources.displayMetrics.density).toInt(), addSource.layoutParams.height)
        assertEquals(context.getString(R.string.photo_profiles_enter_name), field.hint.toString())
        assertNotNull(addSource.drawable)
        assertTrue(root.findViewById<ScrollView>(R.id.photo_profile_edit_scroll).isFillViewport)
    }

    @Test fun addSourceFabStaysVisibleAndMovesWithImeInsets() {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent(app, org.fossify.gallery.activities.PhotoProfileEditActivity::class.java)
            .putExtra(org.fossify.gallery.activities.PhotoProfileEditActivity.EXTRA_CREATING, true)
        val controller = Robolectric.buildActivity(
            org.fossify.gallery.activities.PhotoProfileEditActivity::class.java, intent
        ).setup()
        val activity = controller.get()
        val fab = activity.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.photo_profile_edit_add_source
        )
        val bars = androidx.core.graphics.Insets.of(0, 24, 0, 24)
        val ime = androidx.core.graphics.Insets.of(0, 0, 0, 300)
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(fab,
            androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), bars)
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), ime)
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true)
                .build())
        assertEquals(android.view.View.VISIBLE, fab.visibility)
        assertTrue("visible IME lifts the FAB", fab.translationY < 0f)
        controller.pause().stop().destroy()
    }

    @Test fun fabImePositionUsesOnlyKeyboardOverlapBeyondTheNavigationInset() {
        assertEquals(-276, PhotoProfileFabImePosition.translationY(true, imeBottom = 300, systemBarsBottom = 24))
        assertEquals(0, PhotoProfileFabImePosition.translationY(true, imeBottom = 20, systemBarsBottom = 24))
        assertEquals(0, PhotoProfileFabImePosition.translationY(false, imeBottom = 300, systemBarsBottom = 24))
    }

    @Test fun unsavedEditorDraftSurvivesActivityRecreationButCancelDoesNotPersistIt() {
        val app = RuntimeEnvironment.getApplication()
        val store = PhotoProfileRepository.of(app)
        store.state().profiles.forEach { store.delete(it.id) }
        val intent = Intent(app, org.fossify.gallery.activities.PhotoProfileEditActivity::class.java)
            .putExtra(org.fossify.gallery.activities.PhotoProfileEditActivity.EXTRA_CREATING, true)
        val firstController = Robolectric.buildActivity(
            org.fossify.gallery.activities.PhotoProfileEditActivity::class.java, intent
        ).setup()
        firstController.get().findViewById<EditText>(R.id.photo_profile_edit_name).setText("Draft after rotation")
        val state = Bundle()
        firstController.saveInstanceState(state)
        firstController.pause().stop().destroy()

        val recreated = Robolectric.buildActivity(
            org.fossify.gallery.activities.PhotoProfileEditActivity::class.java, intent
        ).create(state).start().resume().visible()
        val activity = recreated.get()
        assertEquals("Draft after rotation", activity.findViewById<EditText>(R.id.photo_profile_edit_name).text.toString())
        activity.finish()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(store.state().profiles.isEmpty())
        recreated.pause().stop().destroy()
    }

    @Test fun sourcePickerSelectionSnapshotIsRestoredSeparatelyAcrossRecreation() {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent(app, org.fossify.gallery.activities.PhotoProfileEditActivity::class.java)
            .putExtra(org.fossify.gallery.activities.PhotoProfileEditActivity.EXTRA_CREATING, true)
        val controller = Robolectric.buildActivity(
            org.fossify.gallery.activities.PhotoProfileEditActivity::class.java, intent
        ).setup()
        val first = controller.get()
        first.javaClass.getDeclaredField("sourcePickerDraftPaths").apply {
            isAccessible = true
            set(first, setOf("/Camera", "/Camera/Trips"))
        }
        first.javaClass.getDeclaredField("sourcePickerOpen").apply {
            isAccessible = true
            setBoolean(first, true)
        }
        val state = Bundle()
        controller.saveInstanceState(state)
        assertTrue(state.getBoolean("photo_profile_edit_source_picker_open"))
        assertEquals(arrayListOf("/Camera", "/Camera/Trips"),
            state.getStringArrayList("photo_profile_edit_source_picker_draft"))
        controller.pause().stop().destroy()

        val restoredController = Robolectric.buildActivity(
            org.fossify.gallery.activities.PhotoProfileEditActivity::class.java, intent
        ).create(state)
        val restored = restoredController.get()
        assertEquals(setOf("/Camera", "/Camera/Trips"),
            restored.javaClass.getDeclaredField("sourcePickerDraftPaths").apply { isAccessible = true }.get(restored))
        assertTrue(restored.javaClass.getDeclaredField("restoreSourcePickerOnResume").apply { isAccessible = true }.getBoolean(restored))
        restoredController.destroy()
    }
}
