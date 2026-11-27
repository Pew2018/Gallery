package org.fossify.gallery.fragments

import android.graphics.Point
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import org.fossify.commons.extensions.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium
import java.io.File
import com.awxkee.jxlcoder.JxlCoder

abstract class ViewPagerFragment : Fragment() {
    var listener: FragmentListener? = null

    /** Update presentation fields without replacing the page or its decoded media. */
    open fun updateMedium(medium: Medium) {
        arguments?.putSerializable(MEDIUM, medium)
    }

    private var mTouchDownTime = 0L
    private var mTouchDownX = 0f
    private var mTouchDownY = 0f
    private var mCloseDownThreshold = 100f
    private var mIgnoreCloseDown = false

    private var dismissGestureController: MediaTransitionGestureController? = null
    private var dismissVelocityTracker: VelocityTracker? = null
    private var dismissGestureClaimed = false
    private var dismissGestureInterruptedSettle = false

    abstract fun fullscreenToggled(isFullscreen: Boolean)

    interface FragmentListener {
        fun fragmentClicked()

        fun videoEnded(): Boolean

        fun goToPrevItem()

        fun goToNextItem()

        fun launchViewVideoIntent(path: String)

        fun isSlideShowActive(): Boolean

        fun isFullScreen(): Boolean

        /** Called after this page has installed its current request's media surface. */
        fun imageRendered(path: String, page: ViewPagerFragment) {}

        fun entryMediaLoadFailed(path: String, page: ViewPagerFragment) {}

        /** The current media page owns a downward drag; progress is derived from live pointer position. */
        fun mediaDismissProgress(progress: Float, deltaX: Float, deltaY: Float): Boolean = false

        /** Cancels a settling frame and returns its progress so a new pointer can take over. */
        fun mediaDismissGestureBegin(): Float? = null

        /** Ends a live media drag and asks the host to settle or close from its current frame. */
        fun mediaDismissSettled(dismiss: Boolean) {}
    }

    override fun onDestroyView() {
        dismissGestureController?.cancel()
        dismissGestureController = null
        dismissVelocityTracker?.recycle()
        dismissVelocityTracker = null
        dismissGestureClaimed = false
        dismissGestureInterruptedSettle = false
        super.onDestroyView()
    }

    override fun onDestroy() {
        listener = null
        super.onDestroy()
    }

    fun getMediumExtendedDetails(medium: Medium): String {
        val context = context ?: return ""
        val file = File(medium.path)
        if (!context.getDoesFilePathExist(file.absolutePath)) {
            return ""
        }

        val path = "${file.parent.trimEnd('/')}/"
        val exif = try {
            ExifInterface(medium.path)
        } catch (e: Exception) {
            return ""
        }

        val details = StringBuilder()
        val detailsFlag = context.config.extendedDetails
        if (detailsFlag and EXT_NAME != 0) {
            medium.name.let { if (it.isNotEmpty()) details.appendLine(it) }
        }

        if (detailsFlag and EXT_PATH != 0) {
            path.let { if (it.isNotEmpty()) details.appendLine(it) }
        }

        if (detailsFlag and EXT_SIZE != 0) {
            file.length().formatSize().let { if (it.isNotEmpty()) details.appendLine(it) }
        }

        if (detailsFlag and EXT_RESOLUTION != 0) {
            getResolution(medium, file)?.let { if (it.isNotEmpty()) details.appendLine(it) }
        }

        if (detailsFlag and EXT_LAST_MODIFIED != 0) {
            getFileLastModified(file).let { if (it.isNotEmpty()) details.appendLine(it) }
        }

        if (detailsFlag and EXT_DATE_TAKEN != 0) {
            exif.getExifDateTaken(context).let { if (it.isNotEmpty()) details.appendLine(it) }
        }

        if (detailsFlag and EXT_CAMERA_MODEL != 0) {
            exif.getExifCameraModel().let { if (it.isNotEmpty()) details.appendLine(it) }
        }

        if (detailsFlag and EXT_EXIF_PROPERTIES != 0) {
            exif.getExifProperties().let { if (it.isNotEmpty()) details.appendLine(it) }
        }

        if (detailsFlag and EXT_GPS != 0) {
            getLatLonAltitude(medium.path).let { if (it.isNotEmpty()) details.appendLine(it) }
        }
        return details.toString().trim()
    }

    fun getPathToLoad(medium: Medium): String {
        val context = context ?: return medium.path
        return if (context.isPathOnOTG(medium.path)) {
            medium.path.getOTGPublicPath(context)
        } else {
            medium.path
        }
    }

    private fun getResolution(medium: Medium, file: File): String? {
        if (medium.name.endsWith(".jxl",ignoreCase = true)) {
            val resolution = try {
                JxlCoder.getSize(file.readBytes())
            } catch (ignored: OutOfMemoryError) {
                null
            }
            return resolution?.let { Point(it.width,it.height).formatAsResolution() }
        } else {
            return context?.getResolution(file.absolutePath)?.formatAsResolution()
        }
    }

    private fun getFileLastModified(file: File): String {
        val context = context ?: return ""
        val projection = arrayOf(Images.Media.DATE_MODIFIED)
        val uri = Files.getContentUri("external")
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(file.absolutePath)
        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            return if (cursor.moveToFirst()) {
                val dateModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
                dateModified.formatDate(context)
            } else {
                file.lastModified().formatDate(context)
            }
        }
        return ""
    }

    private fun getLatLonAltitude(path: String): String {
        var result = ""
        val exif = try {
            ExifInterface(path)
        } catch (e: Exception) {
            return ""
        }

        val latLon = FloatArray(2)

        if (exif.getLatLong(latLon)) {
            result = "${latLon[0]},  ${latLon[1]}"
        }

        val altitude = exif.getAltitude(0.0)
        if (altitude != 0.0) {
            result += ",  ${altitude}m"
        }

        return result.trimStart(',').trim()
    }

    protected fun handleMediaDismissTouch(view: View, event: MotionEvent, canDismiss: () -> Boolean): Boolean {
        val context = context ?: return false
        val controller = dismissGestureController ?: MediaTransitionGestureController(
            touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
            dismissDistance = (resources.displayMetrics.heightPixels * 0.42f).coerceAtLeast(320f),
        ).also { dismissGestureController = it }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val eligible = context.config.allowDownGesture && canDismiss()
                val baseProgress = if (eligible) listener?.mediaDismissGestureBegin() else null
                dismissGestureInterruptedSettle = baseProgress != null
                controller.down(pointerId, event.getX(index), event.getY(index), eligible, baseProgress ?: 0f)
                dismissGestureClaimed = false
                dismissVelocityTracker?.recycle()
                dismissVelocityTracker = if (eligible) VelocityTracker.obtain().also { it.addMovement(event) } else null
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                dismissVelocityTracker?.addMovement(event)
                val pointerId = event.getPointerId(event.actionIndex.coerceAtMost(event.pointerCount - 1))
                val pointerIndex = event.findPointerIndex(pointerId)
                if (pointerIndex < 0) return dismissGestureClaimed
                val frame = controller.move(pointerId, event.getX(pointerIndex), event.getY(pointerIndex))
                if (frame != null) {
                    if (frame.began) {
                        if (!canDismiss() || listener?.mediaDismissProgress(frame.progress, frame.deltaX, frame.deltaY) != true) {
                            controller.cancel()
                            return false
                        }
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        dismissGestureClaimed = true
                    } else {
                        listener?.mediaDismissProgress(frame.progress, frame.deltaX, frame.deltaY)
                    }
                    return true
                }
                return dismissGestureClaimed
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (controller.pointerChanged() == MediaTransitionGestureController.Release.RESTORE ||
                    dismissGestureInterruptedSettle) {
                    listener?.mediaDismissSettled(false)
                }
                dismissVelocityTracker?.recycle()
                dismissVelocityTracker = null
                view.parent?.requestDisallowInterceptTouchEvent(false)
                dismissGestureClaimed = false
                dismissGestureInterruptedSettle = false
                return false
            }
            MotionEvent.ACTION_UP -> {
                dismissVelocityTracker?.addMovement(event)
                val pointerIndex = event.actionIndex.coerceAtMost(event.pointerCount - 1)
                val pointerId = event.getPointerId(pointerIndex)
                dismissVelocityTracker?.computeCurrentVelocity(1000)
                val velocityY = dismissVelocityTracker?.getYVelocity(pointerId) ?: 0f
                val release = controller.release(pointerId, velocityY)
                val wasClaimed = dismissGestureClaimed
                if (wasClaimed || dismissGestureInterruptedSettle) {
                    listener?.mediaDismissSettled(wasClaimed && release == MediaTransitionGestureController.Release.DISMISS)
                }
                dismissVelocityTracker?.recycle()
                dismissVelocityTracker = null
                view.parent?.requestDisallowInterceptTouchEvent(false)
                dismissGestureClaimed = false
                dismissGestureInterruptedSettle = false
                return wasClaimed
            }
            MotionEvent.ACTION_CANCEL -> {
                val release = controller.cancel()
                val wasClaimed = dismissGestureClaimed
                if (wasClaimed || release == MediaTransitionGestureController.Release.RESTORE || dismissGestureInterruptedSettle) {
                    listener?.mediaDismissSettled(false)
                }
                dismissVelocityTracker?.recycle()
                dismissVelocityTracker = null
                view.parent?.requestDisallowInterceptTouchEvent(false)
                dismissGestureClaimed = false
                dismissGestureInterruptedSettle = false
                return wasClaimed
            }
        }
        return dismissGestureClaimed
    }

    protected fun handleEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownTime = System.currentTimeMillis()
                mTouchDownX = event.rawX
                mTouchDownY = event.rawY
            }

            MotionEvent.ACTION_POINTER_DOWN -> mIgnoreCloseDown = true
            MotionEvent.ACTION_CANCEL -> mIgnoreCloseDown = false
            MotionEvent.ACTION_UP -> {
                val diffX = mTouchDownX - event.rawX
                val diffY = mTouchDownY - event.rawY

                val downGestureDuration = System.currentTimeMillis() - mTouchDownTime
                if (!mIgnoreCloseDown && (Math.abs(diffY) > Math.abs(diffX)) && (diffY < -mCloseDownThreshold) && downGestureDuration < MAX_CLOSE_DOWN_GESTURE_DURATION && context?.config?.allowDownGesture == true) {
                    activity?.finish()
                }
                mIgnoreCloseDown = false
            }
        }
    }
}
