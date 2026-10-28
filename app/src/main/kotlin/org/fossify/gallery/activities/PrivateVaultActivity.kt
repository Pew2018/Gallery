package org.fossify.gallery.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.GalleryBottomBar
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.PrivateFolderAccess
import org.fossify.gallery.helpers.PrivateFolderIndex
import org.fossify.gallery.helpers.DIRECTORY
import java.util.concurrent.atomic.AtomicInteger

/**
 * Non-encrypting privacy collection: password/optional system biometric gate,
 * SAF directory references and in-process previews without shared media caches.
 * This cannot hide the original files from other apps or the operating system.
 */
class PrivateVaultActivity : SimpleActivity() {
    private val pickerRequest = 9042
    private lateinit var auth: PrivateFolderAccess
    private lateinit var index: PrivateFolderIndex
    private lateinit var root: CoordinatorLayout
    private lateinit var body: LinearLayout
    private lateinit var grid: RecyclerView
    private var authenticated = false
    private val generation = AtomicInteger()
    private var preview: AlertDialog? = null
    private var player: ExoPlayer? = null
    private var pendingFolderUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        auth = PrivateFolderAccess(this)
        index = PrivateFolderIndex(this)
        val surface = getProperBackgroundColor()
        val toolbarTextColor = getProperTextColor()
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density + .5f).toInt()
        root = CoordinatorLayout(this).apply { setBackgroundColor(surface) }
        val bar = TextView(this).apply {
            setBackgroundColor(surface)
            setTextColor(toolbarTextColor)
            text = "隐私空间"
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@PrivateVaultActivity, 2)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(body, LinearLayout.LayoutParams(-1, -2))
        content.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val params = CoordinatorLayout.LayoutParams(-1, -1).apply {
            topMargin = dp(56); bottomMargin = dp(60)
        }
        val barParams = CoordinatorLayout.LayoutParams(-1, dp(56)).apply { gravity = Gravity.TOP }
        root.addView(content, params)
        root.addView(bar, barParams)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
            val bars = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            val top = bars.top
            if (barParams.topMargin != top) { barParams.topMargin = top; bar.layoutParams = barParams }
            if (params.topMargin != top + dp(56) || params.bottomMargin != bars.bottom + dp(60)) {
                params.topMargin = top + dp(56)
                params.bottomMargin = bars.bottom + dp(60)
                content.layoutParams = params
            }
            ins
        }
        ViewCompat.requestApplyInsets(root)
        GalleryBottomBar.attach(this, root, GalleryBottomBar.Tab.PRIVATE) { tab ->
            when (tab) {
                GalleryBottomBar.Tab.ALL -> {
                    config.showAll = config.lastMainViewAll
                    val target = if (config.lastMainViewAll)
                        Intent(this, MediaActivity::class.java).putExtra(DIRECTORY, "")
                    else Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)
                    lock()
                    GalleryBottomBar.open(this, target, true)
                }
                GalleryBottomBar.Tab.FAVORITE_FOLDERS -> {
                    lock()
                    GalleryBottomBar.open(this, Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_FOLDER_FAVORITES, true), true)
                }
                GalleryBottomBar.Tab.PRIVATE -> Unit
            }
        }
        showLocked()
    }

    override fun onStop() {
        lock()
        super.onStop()
    }
    override fun onDestroy() {
        lock()
        super.onDestroy()
    }

    private fun lock() {
        generation.incrementAndGet()
        authenticated = false
        preview?.dismiss()
        preview = null
        player?.release()
        player = null
        if (::grid.isInitialized) grid.adapter = null
        if (::body.isInitialized && !isFinishing && !isDestroyed) showLocked()
    }

    private fun info(value: String) {
        body.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(getProperTextColor())
            alpha = 0.75f
            setPadding(0, 8, 0, 16)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun action(name: String, onClick: () -> Unit) {
        val d = resources.displayMetrics.density
        val accent = getProperPrimaryColor()
        body.addView(TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(getProperTextColor())
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8*d).toInt(), 0, (8*d).toInt(), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent))),
                ColorDrawable(Color.TRANSPARENT), GradientDrawable().apply { setColor(Color.WHITE) })
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(-1, (48*d).toInt()))
    }

    private fun showLocked() {
        authenticated = false
        body.removeAllViews()
        grid.adapter = null
        info(if (auth.configured) "隐私空间已锁定" else "为隐私空间创建独立密码")
        info("目录中的文件保持原始位置，不加密；其他拥有文件权限的应用仍可能读取。")
        if (auth.configured) {
            action("输入密码解锁") { passwordDialog(false) }
            if (auth.biometricsEnabled) action("使用系统生物识别") { biometricUnlock() }
        } else action("设置隐私空间密码") { passwordDialog(true) }
        val legacy = org.fossify.gallery.helpers.PrivateVaultCrypto(this)
        if (legacy.isSetUp || legacy.hasEncryptedFiles) {
            action("进入旧版 AES-GCM 加密空间（保留旧文件）") {
                startActivity(Intent(this, LegacyPrivateVaultActivity::class.java))
            }
        }
    }

    private fun passwordDialog(create: Boolean, change: Boolean = false) {
        val first = EditText(this).apply {
            hint = if (create || change) "新密码（至少 6 位）" else "隐私空间密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val confirm = EditText(this).apply {
            hint = "确认新密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            visibility = if (create || change) View.VISIBLE else View.GONE
            isSingleLine = true
        }
        OxygenClassicDialog.styleInput(this, first)
        OxygenClassicDialog.styleInput(this, confirm)
        val d = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24*d).toInt(), (8*d).toInt(), (24*d).toInt(), (8*d).toInt())
            addView(first, LinearLayout.LayoutParams(-1, (56*d).toInt()))
            if (create || change) addView(confirm, LinearLayout.LayoutParams(-1, (56*d).toInt()))
        }
        val passwordBuilder = OxygenClassicDialog.builder(
            this, if (create) "创建隐私空间" else if (change) "更改隐私空间密码" else "解锁"
        ).setView(row)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                val password = first.text.toString().toCharArray()
                val repeat = confirm.text.toString().toCharArray()
                val token = generation.get()
                Thread {
                    val ok = try {
                        if ((create || change) && !password.contentEquals(repeat)) false
                        else when {
                            create -> auth.setup(password)
                            change && authenticated -> auth.changePassword(password)
                            else -> auth.verify(password)
                        }
                    } finally {
                        password.fill('\u0000'); repeat.fill('\u0000')
                    }
                    runOnUiThread {
                        if (token != generation.get() || isFinishing || isDestroyed) return@runOnUiThread
                        if (ok) {
                            authenticated = true
                            showUnlocked()
                            pendingFolderUri?.let { pending ->
                                pendingFolderUri = null
                                if (index.add(pending)) showFolders()
                                else toast("目录不是可隔离的外部媒体文件夹，未加入")
                            }
                        } else toast("密码错误、密码不足 6 位或两次输入不一致")
                    }
                }.start()
            }
        OxygenClassicDialog.show(this, passwordBuilder, row)
    }

    private fun biometricUnlock() {
        val token = generation.get()
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            toast("当前没有可用的强生物识别，请使用独立密码"); return
        }
        val prompt = BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (token == generation.get() && !isFinishing && !isDestroyed && auth.biometricsEnabled &&
                        lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        authenticated = true
                        showUnlocked()
                    }
                }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁隐私空间")
            .setSubtitle("使用设备生物识别验证身份")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("使用密码")
            .build())
    }

    private fun showUnlocked() {
        if (!authenticated) return
        body.removeAllViews()
        action("添加文件夹") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, pickerRequest)
        }
        action("更改密码") { passwordDialog(false, true) }
        action(if (auth.biometricsEnabled) "关闭生物识别解锁" else "启用生物识别解锁") {
            if (auth.biometricsEnabled) {
                auth.biometricsEnabled = false
                showUnlocked()
            } else if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    BiometricManager.BIOMETRIC_SUCCESS) {
                // Only an already authenticated user can enable this optional unlock route.
                auth.biometricsEnabled = true
                showUnlocked()
            } else toast("未检测到可用的强生物识别")
        }
        action("立即锁定") { lock() }
        action("刷新文件夹") { showFolders() }
        info("下方仅显示已授权的文件夹；失效的目录会保留记录以便重新授权。")
        showFolders()
    }

    @Deprecated("Uses the existing Activity result model for SAF folder selection")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickerRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (authenticated) {
                if (index.add(uri)) showFolders()
                else toast("该目录无法对应到系统媒体路径，无法保证 Gallery 内部隔离，因此没有加入")
            } else {
                // ACTION_OPEN_DOCUMENT_TREE can stop the Activity and invalidate the session.
                // Persist the grant, but require a fresh password/biometric auth before linking.
                pendingFolderUri = uri
                showLocked()
                toast("请重新认证以完成文件夹关联")
            }
        } catch (_: Exception) {
            toast("文件夹授权失败，请重新选择")
        }
    }

    private data class Tile(val name: String, val subtitle: String, val uri: Uri?,
                            val video: Boolean = false, val folder: PrivateFolderIndex.Folder? = null)

    private fun showFolders() {
        grid.layoutManager = GridLayoutManager(this, 2)
        val current = index.folders()
        val token = generation.get()
        // Directory enumeration can involve slow external providers. Never block UI.
        Thread {
            val rows = current.map { entry ->
                val doc = try { DocumentFile.fromTreeUri(this, Uri.parse(entry.uri)) } catch (_: Exception) { null }
                val available = index.isAccessible(entry)
                val children = if (available) try { doc?.listFiles().orEmpty() } catch (_: Exception) { emptyArray() }
                    else emptyArray()
                val images = children.filter { it.isFile && (it.type?.startsWith("image/") == true ||
                    it.type?.startsWith("video/") == true) }
                val photos = images.count { it.type?.startsWith("image/") == true }
                val videos = images.count { it.type?.startsWith("video/") == true }
                Tile(entry.name, if (available) "${photos} 张图片 · ${videos} 个视频" else "授权失效，需重新选择",
                    images.firstOrNull()?.uri, folder = entry)
            }
            runOnUiThread {
                if (token == generation.get() && authenticated && !isFinishing) {
                    grid.adapter = TilesAdapter(rows) { tile ->
                        tile.folder?.let { openFolder(it) }
                    }
                }
            }
        }.start()
    }

    private fun openFolder(entry: PrivateFolderIndex.Folder) {
        if (!authenticated) return
        val token = generation.get()
        body.removeAllViews()
        action("返回文件夹列表") { showUnlocked() }
        action("移除此文件夹（不删除原文件）") {
            OxygenClassicDialog.show(this,
                OxygenClassicDialog.builder(this, "移除隐私文件夹")
                    .setMessage("仅解除隐私空间关联，保留所有原始媒体文件？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("移除") { _, _ ->
                        index.remove(entry)
                        showUnlocked()
                    }
            )
        }
        info(entry.name)
        grid.layoutManager = GridLayoutManager(this, 3)
        Thread {
            val doc = try { DocumentFile.fromTreeUri(this, Uri.parse(entry.uri)) } catch (_: Exception) { null }
            val media = try { doc?.listFiles().orEmpty().filter { it.isFile &&
                (it.type?.startsWith("image/") == true || it.type?.startsWith("video/") == true) } }
                catch (_: Exception) { null }.orEmpty()
            val rows = media.map { Tile(it.name ?: "媒体文件", "", it.uri,
                it.type?.startsWith("video/") == true) }
            runOnUiThread {
                if (authenticated && token == generation.get() && !isFinishing) {
                    grid.adapter = TilesAdapter(rows) { item ->
                        item.uri?.let { previewMedia(it, item.video) }
                    }
                }
            }
        }.start()
    }

    private fun previewMedia(uri: Uri, video: Boolean) {
        if (!authenticated) return
        if (video) {
            val view = PlayerView(this).apply {
                useController = true
                layoutParams = LinearLayout.LayoutParams(-1, (320*resources.displayMetrics.density).toInt())
            }
            val playback = ExoPlayer.Builder(this).build()
            player = playback
            view.player = playback
            playback.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    toast("视频无法播放：" + (error.message ?: "格式或读取错误"))
                }
            })
            preview = OxygenClassicDialog.builder(this, "").setView(view)
                .setPositiveButton("关闭", null)
                .setOnDismissListener {
                    view.player = null
                    playback.release()
                    if (player === playback) player = null
                    preview = null
                }.create()
            preview?.let { dialog ->
                if (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.show()
                OxygenClassicDialog.apply(this, dialog, view)
            }
            playback.setMediaItem(MediaItem.fromUri(uri))
            playback.prepare()
            playback.playWhenReady = true
        } else {
            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            Glide.with(this).load(uri).diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true).into(image)
            preview = OxygenClassicDialog.builder(this, "").setView(image)
                .setPositiveButton("关闭", null)
                .setOnDismissListener {
                    Glide.with(this).clear(image); image.setImageDrawable(null); preview = null
                }.create().also {
                    if (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                        it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    it.show()
                    OxygenClassicDialog.apply(this, it, image)
                }
        }
    }

    private inner class TilesAdapter(private val items: List<Tile>,
        private val opened: (Tile) -> Unit) : RecyclerView.Adapter<TilesAdapter.Holder>() {
        inner class Holder(val item: LinearLayout, val image: ImageView, val title: TextView)
            : RecyclerView.ViewHolder(item)
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val d = resources.displayMetrics.density
            val container = LinearLayout(this@PrivateVaultActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((4*d).toInt(), (4*d).toInt(), (4*d).toInt(), (4*d).toInt())
            }
            val thumb = ImageView(this@PrivateVaultActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.placeholder_square)
                contentDescription = "媒体缩略图"
            }
            val title = TextView(this@PrivateVaultActivity).apply {
                setTextColor(getProperTextColor())
                textSize = 13f
                maxLines = 2
            }
            container.addView(thumb, LinearLayout.LayoutParams(-1, (116*d).toInt()))
            container.addView(title, LinearLayout.LayoutParams(-1, -2))
            return Holder(container, thumb, title)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val tile = items[position]
            holder.title.text = tile.name + if (tile.subtitle.isEmpty()) "" else "\n" + tile.subtitle
            Glide.with(this@PrivateVaultActivity).clear(holder.image)
            holder.image.setImageResource(R.drawable.placeholder_square)
            if (tile.uri != null) {
                Glide.with(this@PrivateVaultActivity).load(tile.uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true)
                    .placeholder(R.drawable.placeholder_square).into(holder.image)
            }
            holder.item.setOnClickListener { if (authenticated) opened(tile) }
            holder.item.contentDescription = holder.title.text
        }
        override fun onViewRecycled(holder: Holder) {
            Glide.with(this@PrivateVaultActivity).clear(holder.image)
            holder.image.setImageDrawable(null)
            super.onViewRecycled(holder)
        }
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
