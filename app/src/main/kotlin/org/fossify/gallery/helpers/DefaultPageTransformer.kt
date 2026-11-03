package org.fossify.gallery.helpers

import android.view.View
import androidx.viewpager.widget.ViewPager

class DefaultPageTransformer : ViewPager.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        // FadePageTransformer uses translation and alpha. Restore both when
        // leaving slideshow so interactive swipes remain ordinary ViewPager
        // slides rather than inheriting a partially transparent page.
        if (view.translationX != 0f) view.translationX = 0f
        if (view.alpha != 1f) view.alpha = 1f
    }
}
