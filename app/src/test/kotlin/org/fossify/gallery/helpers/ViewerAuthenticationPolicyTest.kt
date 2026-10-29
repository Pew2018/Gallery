package org.fossify.gallery.helpers

import android.app.Application
import android.content.ComponentName
import org.fossify.gallery.activities.ViewPagerActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as TestConfig

/** Never treat a forged Intent extra as authorization in an exported viewer. */
@RunWith(RobolectricTestRunner::class)
@TestConfig(sdk = [28], application = Application::class)
class ViewerAuthenticationPolicyTest {
    @Test fun externalViewerManifestCannotTrustSkipAuthenticationExtra() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context, ViewPagerActivity::class.java)
        val exported = context.packageManager.getActivityInfo(component, 0).exported
        assertTrue("This regression matters because VIEW is exported", exported)
        assertFalse(ViewerAuthenticationPolicy.mayHonorSkipExtra(true, exported))
        assertFalse(ViewerAuthenticationPolicy.mayHonorSkipExtra(false, exported))
        assertFalse(ViewerAuthenticationPolicy.mayHonorSkipExtra(true, true))
    }

    @Test fun unexportedInternalActivitiesMayUseExplicitSkipAfterPriorAuthentication() {
        assertTrue(ViewerAuthenticationPolicy.mayHonorSkipExtra(true, false))
        assertFalse(ViewerAuthenticationPolicy.mayHonorSkipExtra(false, false))
    }
}
