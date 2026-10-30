package org.fossify.gallery

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OxygenThemeEngine
import android.os.Bundle
import com.github.ajalt.reprint.core.Reprint
import org.fossify.gallery.helpers.OxygenClassicCustomizationBridge
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.FossifyApp

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        OxygenThemeEngine.apply(this, config)
    }

    override fun onCreate() {
        super.onCreate()
        OxygenThemeEngine.apply(this, config)
        Reprint.initialize(this)
        // Commons' sealed CustomizationActivity is not a Gallery SimpleActivity.
        // Apply its classic toolbar once on resume, after Commons sets its theme.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityResumed(activity: Activity) {
                // Pre-29 has no post-resume lifecycle callback. One queued pass is sufficient.
                if (android.os.Build.VERSION.SDK_INT < 29) activity.window.decorView.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        OxygenClassicCustomizationBridge.apply(activity)
                    }
                }
            }
            override fun onActivityPostResumed(activity: Activity) {
                OxygenClassicCustomizationBridge.apply(activity)
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                started--
                if (started == 0 && !activity.isChangingConfigurations)
                    org.fossify.gallery.helpers.PhotoProfileAccessSessionManager.revokeAll()
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }
}
