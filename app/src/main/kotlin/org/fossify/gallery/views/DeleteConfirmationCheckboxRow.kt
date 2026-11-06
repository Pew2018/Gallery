package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Full-row ripple surface for delete confirmation choices.
 * Unlike the 56dp options rows, this wrapper adds no minimum height or padding.
 */
class DeleteConfirmationCheckboxRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs)
