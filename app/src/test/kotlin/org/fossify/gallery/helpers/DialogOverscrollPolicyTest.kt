package org.fossify.gallery.helpers

import android.app.Application
import android.app.Dialog
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.views.ClassicSecurityScrollView
import org.fossify.gallery.views.ClassicSecurityViewPager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Tests the real presentation hooks; system popup rendering still requires device acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class DialogOverscrollPolicyTest {
    private fun activity() = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    private fun assertNone(view: View) {
        assertEquals(view.javaClass.name, View.OVER_SCROLL_NEVER, view.overScrollMode)
        if (view is RecyclerView) assertFalse(view.edgeEffectFactory is OxygenClassicEdgeGlow.ClassicFactory)
        if (view is ViewGroup) for (i in 0 until view.childCount) assertNone(view.getChildAt(i))
    }
    @Test fun plainPlatformDialogAndAlertDialogUseTheNoEffectBoundary() {
        val activity = activity()
        for (dialog in listOf(Dialog(activity), android.app.AlertDialog.Builder(activity)
            .setItems(Array(100) { "Item $it" }, null).create())) {
            DialogOverscrollPolicy.protectDialog(dialog)
            dialog.show()
            val decor = dialog.window!!.decorView
            decor.viewTreeObserver.dispatchOnGlobalLayout()
            assertNone(decor)
            OxygenClassicEdgeGlow.install(decor)
            assertNone(decor)
            dialog.dismiss()
        }
        activity.finish()
    }
    @Test fun galleryLongCheckboxDialogRemainsForbiddenAcrossReopenAndThemeChange() {
        val activity = activity()
        repeat(2) {
            val content = ClassicDialogScrollView(activity)
            val choices = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            repeat(60) { choices.addView(CheckBox(activity).apply { text = "Option $it" }) }
            content.addView(choices)
            val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Choices").setView(content), content)
            assertNone(dialog.window!!.decorView)
            activity.setTheme(R.style.OxygenClassicAlertDialog_Dark)
            OxygenClassicEdgeGlow.install(content)
            OxygenClassicDialog.apply(activity, dialog, content)
            assertNone(content)
            assertTrue(choices.getChildAt(0).isClickable)
            dialog.dismiss()
        }
        activity.finish()
    }
    @Test fun commonsHookProtectsLateListsAndPreservesScrollbars() {
        val activity = activity()
        val content = FrameLayout(activity)
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        ClassicCommonsHooks.beforeDialogShow(activity, dialog, content)
        dialog.show()
        ClassicCommonsHooks.onDialogShown(activity, dialog, content)
        val list = ListView(activity).apply {
            isVerticalScrollBarEnabled = true
            adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, Array(100) { "Item $it" })
        }
        content.addView(list)
        dialog.window!!.decorView.viewTreeObserver.dispatchOnGlobalLayout()
        assertNone(content)
        assertTrue(list.isVerticalScrollBarEnabled)
        assertEquals(100, list.adapter.count)
        dialog.dismiss()
        activity.finish()
    }
    @Test fun actualPickerLayoutsAndSecurityContainersDefaultToNone() {
        val activity = activity()
        for (id in listOf(R.layout.dialog_directory_picker, R.layout.dialog_medium_picker, R.layout.dialog_filepicker)) {
            val picker = LayoutInflater.from(activity).inflate(id, null)
            // The actual dialog boundary protects content before its parent chain exists.
            DialogOverscrollPolicy.protect(picker)
            activity.setContentView(picker)
            shadowOf(Looper.getMainLooper()).idle()
            OxygenClassicEdgeGlow.install(picker)
            assertNone(picker)
        }
        val attrs = Robolectric.buildAttributeSet().build()
        val security = ClassicSecurityScrollView(activity, attrs)
        val pager = ClassicSecurityViewPager(activity)
        assertNone(security)
        assertNone(pager)
        activity.finish()
    }
    @Test fun nestedPolicyRootsRestoreDynamicCoverageAfterIndependentReattach() {
        val activity = activity()
        val outer = FrameLayout(activity)
        val inner = FrameLayout(activity)
        DialogOverscrollPolicy.protect(inner)
        DialogOverscrollPolicy.protect(outer)
        outer.addView(inner)
        activity.setContentView(outer)
        outer.viewTreeObserver.dispatchOnGlobalLayout()
        outer.removeView(inner)
        val nextHost = FrameLayout(activity)
        nextHost.addView(inner)
        activity.setContentView(nextHost)
        val late = RecyclerView(activity)
        inner.addView(late)
        inner.viewTreeObserver.dispatchOnGlobalLayout()
        assertNone(inner)
        assertTrue(DialogOverscrollPolicy.isForbidden(late))
        activity.finish()
    }

    @Test fun longDialogStillScrollsAfterOverscrollIsDisabled() {
        val activity = activity()
        val content = ClassicDialogScrollView(activity)
        content.addView(View(activity), ViewGroup.LayoutParams(360, 2000))
        DialogOverscrollPolicy.protect(content)
        content.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        content.scrollTo(0, 800)
        assertTrue(content.scrollY > 0)
        assertEquals(View.OVER_SCROLL_NEVER, content.overScrollMode)
        activity.finish()
    }

    class PlainFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: android.os.Bundle?): Dialog = Dialog(requireContext()).apply {
            setContentView(ScrollView(context).apply { addView(TextView(context).apply { text = "Long\n".repeat(200) }) })
        }
        override fun onStart() {
            // Same pre-show boundary as the transformed base DialogFragment.onStart().
            DialogOverscrollPolicy.protectFragment(this)
            super.onStart()
        }
    }
    @Test fun dialogFragmentBoundaryProtectsTheWholeWindowBeforeFirstDraw() {
        val activity = activity()
        val fragment = PlainFragment()
        fragment.showNow(activity.supportFragmentManager, "test")
        assertNone(fragment.requireDialog().window!!.decorView)
        fragment.dismissNow()
        activity.finish()
    }
}
