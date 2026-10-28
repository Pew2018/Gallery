package org.fossify.gallery.activities

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import android.widget.Toast
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.GalleryBottomBar
import org.fossify.gallery.helpers.PrivateVaultCrypto
import org.fossify.gallery.extensions.config
import java.io.File
import java.util.UUID

/**
 * Separate from hidden/protected albums. The vault never returns plaintext
 * to normal Gallery Activities, MediaStore, sharing providers, or Glide.
 */
class PrivateVaultActivity : SimpleActivity() {
    private companion object {
        const val PICK_DOCUMENT = 9041
    }

    private lateinit var crypto: PrivateVaultCrypto
    private lateinit var root: CoordinatorLayout
    private lateinit var body: LinearLayout
    private var dataKey: ByteArray? = null
    private var pendingImport: android.net.Uri? = null
    private var previewDialog: AlertDialog? = null
    @Volatile private var generation = 0
    // Plaintext previews live in a dedicated app-private, non-backed-up directory.
    // Never write them to MediaStore, external storage or ordinary image caches.
    private val previews by lazy { File(noBackupFilesDir, "vault_previews") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        crypto = PrivateVaultCrypto(applicationContext)
        purgePreviews()

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val background = getProperBackgroundColor()
        val foreground = getProperTextColor()
        val margin = dp(18)
        root = CoordinatorLayout(this).apply { setBackgroundColor(background) }

        val toolbar = TextView(this).apply {
            text = "隐私空间"
            textSize = 20f
            setTextColor(foreground)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            setBackgroundColor(background)
        }
        val toolbarParams = CoordinatorLayout.LayoutParams(-1, dp(56)).apply {
            gravity = Gravity.TOP
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, dp(20), margin, dp(16))
        }
        scroll.addView(body)
        val scrollParams = CoordinatorLayout.LayoutParams(-1, -1).apply {
            topMargin = dp(56)
            bottomMargin = dp(60)
        }
        root.addView(scroll, scrollParams)
        root.addView(toolbar, toolbarParams)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val top = systemBars.top
            val bottom = systemBars.bottom
            if (toolbarParams.topMargin != top) {
                toolbarParams.topMargin = top
                toolbar.layoutParams = toolbarParams
            }
            if (scrollParams.topMargin != top + dp(56) || scrollParams.bottomMargin != bottom + dp(60)) {
                scrollParams.topMargin = top + dp(56)
                scrollParams.bottomMargin = bottom + dp(60)
                scroll.layoutParams = scrollParams
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)

        GalleryBottomBar.attach(this, root, GalleryBottomBar.Tab.PRIVATE) { tab ->
            when (tab) {
                GalleryBottomBar.Tab.ALL -> {
                    val showPhotos = config.lastMainViewAll
                    config.showAll = showPhotos
                    val destination = if (showPhotos) {
                        Intent(this, MediaActivity::class.java).putExtra(DIRECTORY, "")
                    } else {
                        Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)
                    }
                    GalleryBottomBar.open(this, destination, finishCurrent = true)
                }
                GalleryBottomBar.Tab.FAVORITE_FOLDERS -> {
                    GalleryBottomBar.open(this, Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_FOLDER_FAVORITES, true),
                        finishCurrent = true)
                }
                GalleryBottomBar.Tab.PRIVATE -> {
                    if (dataKey != null) showUnlocked() else showLocked()
                }
            }
        }
        showLocked()
    }

    override fun onStop() {
        // No session key or decrypted preview may survive backgrounding.
        lock()
        super.onStop()
    }

    override fun onDestroy() {
        lock()
        super.onDestroy()
    }

    private fun purgePreviews() {
        previews.listFiles()?.forEach { it.delete() }
    }

    private fun lock() {
        generation++
        previewDialog?.dismiss()
        previewDialog = null
        dataKey?.fill(0)
        dataKey = null
        purgePreviews()
        if (::body.isInitialized) showLocked()
    }

    private fun button(label: String, action: () -> Unit) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val primary = getProperPrimaryColor()
        val foreground = getProperTextColor()
        val surface = getProperBackgroundColor()
        val container = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(surface)
            setStroke(dp(1), Color.argb(50, Color.red(foreground), Color.green(foreground), Color.blue(foreground)))
        }
        val row = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(foreground)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(32, Color.red(primary), Color.green(primary), Color.blue(primary))),
                container, null
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        body.addView(row, LinearLayout.LayoutParams(-1, dp(52)).apply {
            bottomMargin = dp(10)
        })
    }

    private fun heading(text: String) {
        val density = resources.displayMetrics.density
        body.addView(TextView(this).apply {
            this.text = text
            textSize = 23f
            setTextColor(getProperTextColor())
            setPadding(0, (14 * density).toInt(), 0, (20 * density).toInt())
        })
    }

    private fun info(text: String) {
        val density = resources.displayMetrics.density
        body.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getProperTextColor())
            alpha = 0.8f
            setPadding(0, (4 * density).toInt(), 0, (20 * density).toInt())
        })
    }

    private fun showLocked() {
        body.removeAllViews()
        heading(if (crypto.isSetUp) "空间已锁定" else "创建隐私空间")
        if (!crypto.isSetUp && crypto.hasEncryptedFiles) {
            info("设备密钥或设置已损坏。为保护现有加密文件，不允许直接重置空间。")
            return
        }
        if (crypto.isSetUp) {
            info("已锁定。文件经过 AES-GCM 加密，不属于普通的隐藏相册。")
            button("解锁隐私空间") { passwordDialog(create = false) }
        } else {
            info("尚未设置密码。创建后文件只以密文保存在应用私有目录；原文件不会自动删除。")
            button("设置密码并创建空间") { passwordDialog(create = true) }
        }
    }

    private fun passwordDialog(create: Boolean, changing: Boolean = false) {
        val first = EditText(this).apply {
            hint = if (create || changing) "新密码（至少 6 位）" else "隐私空间密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val second = EditText(this).apply {
            hint = "再次输入密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            visibility = if (create || changing) android.view.View.VISIBLE else android.view.View.GONE
        }
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        first.isSingleLine = true
        second.isSingleLine = true
        val inputs = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
            addView(first, LinearLayout.LayoutParams(-1, dp(56)))
            addView(second, LinearLayout.LayoutParams(-1, dp(56)).apply {
                topMargin = dp(8)
            })
        }
        AlertDialog.Builder(this)
            .setTitle(if (changing) "更改密码" else if (create) "创建隐私空间" else "解锁隐私空间")
            .setView(inputs)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                val pw = first.text.toString().toCharArray()
                val pw2 = second.text.toString().toCharArray()
                if ((create || changing) && !pw.contentEquals(pw2)) {
                    pw.fill('\u0000'); pw2.fill('\u0000')
                    feedback("两次密码不一致")
                    return@setPositiveButton
                }
                pw2.fill('\u0000')
                if ((create || changing) && pw.size < 6) {
                    pw.fill('\u0000')
                    feedback("密码至少需要 6 位")
                    return@setPositiveButton
                }
                val current = dataKey?.copyOf()
                val currentGeneration = generation
                Thread {
                    try {
                        val newKey = when {
                            changing -> {
                                check(current != null)
                                crypto.changePassword(current, pw)
                                current
                            }
                            create -> crypto.setup(pw)
                            else -> crypto.unlock(pw)
                        }
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed && generation == currentGeneration) {
                                dataKey?.fill(0)
                                dataKey = newKey
                                showUnlocked()
                                pendingImport?.let { uri ->
                                    pendingImport = null
                                    importFile(uri)
                                }
                            } else {
                                newKey.fill(0)
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread { feedback("密码错误、密钥失效或加密数据损坏") }
                    } finally {
                        pw.fill('\u0000')
                        if (changing) current?.fill(0)
                    }
                }.start()
            }.show()
    }

    private fun showUnlocked() {
        if (dataKey == null) return
        body.removeAllViews()
        heading("隐私空间 · 已解锁")
        info("已加密保存的文件不会出现在系统图库中。退出此页面或切换到其他应用会自动锁定。")
        button("添加图片、视频或文件") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, PICK_DOCUMENT)
        }
        button("更改密码") { passwordDialog(create = false, changing = true) }
        button("立即锁定") { lock() }
        val files = crypto.files()
        if (files.isEmpty()) {
            info("空间为空。请选择需要导入的文件。导入后原文件仍保留在原位置。")
        } else {
            files.forEachIndexed { index, file ->
                val type = when {
                    file.name.endsWith(".image.gvlt") -> "图片"
                    file.name.endsWith(".video.gvlt") -> "视频"
                    else -> "普通文件"
                }
                button("${index + 1}. $type · 加密文件") { preview(file, type) }
                button("删除第 ${index + 1} 个加密副本") {
                    AlertDialog.Builder(this).setMessage("仅删除隐私空间内的加密副本？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除") { _, _ ->
                            Thread {
                                try { crypto.delete(file) } catch (_: Exception) { }
                                runOnUiThread { if (dataKey != null) showUnlocked() }
                            }.start()
                        }.show()
                }
            }
        }
    }

    @Deprecated("Compatibility with the existing Activity result flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_DOCUMENT && resultCode == RESULT_OK) {
            pendingImport = data?.data
            if (dataKey == null) passwordDialog(create = false)
            else pendingImport?.let { pendingImport = null; importFile(it) }
        }
    }

    private fun importFile(uri: android.net.Uri) {
        val snapshot = dataKey?.copyOf() ?: return
        val token = generation
        feedback("正在加密导入")
        Thread {
            try {
                crypto.import(uri, contentResolver.getType(uri), snapshot)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && generation == token && dataKey != null) {
                        showUnlocked()
                        feedback("已加密导入，原文件未删除")
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { feedback("导入失败，未修改原文件") }
            } finally {
                snapshot.fill(0)
            }
        }.start()
    }

    private fun preview(file: File, type: String) {
        if (type == "普通文件") {
            AlertDialog.Builder(this).setMessage("此类型暂不支持应用内预览；加密副本仍安全保存。")
                .setPositiveButton("确定", null).show()
            return
        }
        val snapshot = dataKey?.copyOf() ?: return
        val token = generation
        Thread {
            val preview = File(previews.apply { mkdirs() }, UUID.randomUUID().toString() +
                if (type == "视频") ".mp4" else ".jpg")
            try {
                crypto.decryptForPreview(file, preview, snapshot)
                if (token != generation) { preview.delete(); return@Thread }
                if (type == "图片") {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(preview.path, bounds)
                    val options = BitmapFactory.Options()
                    var sample = 1
                    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                    options.inSampleSize = sample
                    val bitmap = BitmapFactory.decodeFile(preview.path, options)
                        ?: error("不支持的图片格式")
                    runOnUiThread {
                        if (token != generation || dataKey == null || isFinishing) {
                            bitmap.recycle(); preview.delete()
                        } else {
                            val image = ImageView(this).apply {
                                setImageBitmap(bitmap)
                                adjustViewBounds = true
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                            previewDialog = AlertDialog.Builder(this).setView(image)
                                .setPositiveButton("关闭", null)
                                .setOnDismissListener {
                                    image.setImageDrawable(null)
                                    bitmap.recycle()
                                    preview.delete()
                                    previewDialog = null
                                }.create().also { it.show() }
                        }
                    }
                } else {
                    runOnUiThread {
                        if (token != generation || dataKey == null || isFinishing) preview.delete()
                        else {
                            val player = VideoView(this).apply {
                                layoutParams = LinearLayout.LayoutParams(-1, (260 * resources.displayMetrics.density).toInt())
                                setVideoPath(preview.path)
                            }
                            previewDialog = AlertDialog.Builder(this).setView(player)
                                .setPositiveButton("关闭", null)
                                .setOnDismissListener {
                                    player.stopPlayback()
                                    preview.delete()
                                    previewDialog = null
                                }.create().also { it.show(); player.start() }
                        }
                    }
                }
            } catch (_: Exception) {
                preview.delete()
                runOnUiThread { feedback("预览失败：文件损坏或格式不受支持") }
            } finally {
                snapshot.fill(0)
            }
        }.start()
    }

    private fun feedback(message: String) {
        if (!isFinishing && !isDestroyed) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
