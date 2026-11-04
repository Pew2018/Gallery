# 模拟器测试代码归档（当前未启用）

归档时间：2026-11-04（Asia/Taipei）  
仓库：`Pew2018/Gallery`  
分支：`fix/photo-viewer-five-item-boundary`  
归档基线：`c794c63634f0870bff4747be07c7eec5cfaab0d5`

> 本文保存先前 Android Emulator Actions 的 workflow、instrumentation 测试和夹具脚本原文，供以后人工恢复与复用。它们目前是 Markdown 代码归档，不参与 Gradle、GitHub Actions 或 APK 构建。归档内容来自上述基线；后续若恢复，须按当时源码重新核对 API 和文件路径。

## 变更范围

- 移除 `.github/workflows/gallery-emulator-tests.yml`，停止此分支的 push 自动模拟器任务及手动 `workflow_dispatch` 入口。
- 移除 `app/src/androidTest` 下两项专用 instrumentation 测试。
- 移除 `tools/emulator-tests` 下设备端 runner 与 host fixture 生成器。
- 从 `app/build.gradle.kts` 移除 instrumentation runner、`testBuildType` 和三项 `androidTestImplementation` 依赖。常规 JVM 单测配置、Release 单测、签名与 ARM64 APK workflow 保留。
- 没有改动生产 Kotlin、普通 `app/src/test` 单元测试、`arm64-foss-release.yml` 或应用运行时功能。

## 文件清单与用途

| 归档内容 | 作用 |
|---|---|
| `.github/workflows/gallery-emulator-tests.yml` | smoke、sort_ui、deep、boundary、PIN 三类模拟器 job 及 artifact 上传 |
| `PhotoProfileEmulatorTest.kt` | Photo Profile 列表/加载、排序、深列表、Viewer 滑动/图像身份和删除连续性 instrumentation |
| `PhotoProfilePinEmulatorTest.kt` | 使用真实 PIN UI 和会话授权状态的模拟器覆盖 |
| `run_on_device.sh` | 生成/安装测试媒体、授权调试应用、执行 Gradle connected instrumentation task |
| `generate_media.py` | 在 CI host 生成测试图片及归档夹具 |

## Gradle 中已移除的构建专用片段

以下配置不再位于活动 `app/build.gradle.kts` 中；其余 `testOptions.unitTests` 与 `testImplementation` 设置保持原样。

```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
testBuildType = providers.gradleProperty("EMULATOR_TEST_BUILD_TYPE").getOrElse("release")
androidTestImplementation("androidx.test:runner:1.6.2")
androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
```

## 恢复原则

以后要重启模拟器回归时，可从下方代码块恢复对应文件，并将 Gradle 片段重新加入 `app/build.gradle.kts`。恢复后应先验证当前 AGP/Gradle 版本、权限对话框行为和 fixture 写入路径，再手动触发单一 smoke job；确认后再扩展矩阵。不可直接复制旧的 Actions 运行结果作为新代码的验证结论。

## 归档源代码

### `.github/workflows/gallery-emulator-tests.yml`

````yaml
name: Gallery Android Emulator
on:
  workflow_dispatch:
  push:
    branches: [fix/photo-viewer-five-item-boundary]
    paths:
      - ".github/workflows/gallery-emulator-tests.yml"
      - "app/build.gradle.kts"
      - "app/src/main/kotlin/**/*.kt"
      - "app/src/androidTest/**"
      - "tools/emulator-tests/**"

permissions:
  contents: read

concurrency:
  group: gallery-emulator-${{ github.ref }}
  cancel-in-progress: true

jobs:
  smoke:
    runs-on: ubuntu-latest
    timeout-minutes: 180
    env:
      CI_MEDIA_SCALE: smoke
      CI_SUITE: smoke
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Compile FOSS debug and Android instrumentation APKs
        run: ./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest -PEMULATOR_TEST_BUILD_TYPE=debug --stacktrace
      - name: Enable host KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Run synthetic-media tests inside genuine Android emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          target: google_apis
          profile: pixel_6
          ram-size: 4096M
          disk-size: 4096M
          disable-animations: true
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          script: bash tools/emulator-tests/run_on_device.sh
      - name: Upload JUnit, screenshots, appops, window state and logcat even on failure
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: gallery-emulator-smoke-${{ github.run_id }}
          path: |
            tools/emulator-tests/output/**
            app/build/outputs/androidTest-results/**
            app/build/reports/androidTests/**
          retention-days: 14
          if-no-files-found: warn

  extended:
    if: github.event_name == 'workflow_dispatch' || contains(github.event.head_commit.message, '[emulator-extended]')
    strategy:
      fail-fast: false
      max-parallel: 2
      matrix:
        include:
          - suite: sort_ui
            scale: smoke
            api: 34
          - suite: deep
            scale: large
            api: 34
          - suite: boundary
            scale: segment
            api: 34
          - suite: boundary
            scale: ceiling
            api: 34
    runs-on: ubuntu-latest
    timeout-minutes: 180
    env:
      CI_MEDIA_SCALE: ${{ matrix.scale }}
      CI_SUITE: ${{ matrix.suite }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Compile FOSS debug and Android instrumentation APKs
        run: ./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest -PEMULATOR_TEST_BUILD_TYPE=debug --stacktrace
      - name: Enable host KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Run synthetic-media tests inside genuine Android emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api }}
          arch: x86_64
          target: google_apis
          profile: pixel_6
          ram-size: 4096M
          disk-size: 4096M
          disable-animations: true
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          script: bash tools/emulator-tests/run_on_device.sh
      - name: Upload JUnit, screenshots, appops, window state and logcat even on failure
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: gallery-emulator-${{ matrix.suite }}-${{ matrix.scale }}-${{ github.run_id }}
          path: |
            tools/emulator-tests/output/**
            app/build/outputs/androidTest-results/**
            app/build/reports/androidTests/**
          retention-days: 14
          if-no-files-found: warn

  pin-auth:
    if: github.event_name == 'workflow_dispatch' || contains(github.event.head_commit.message, '[emulator-pin]')
    runs-on: ubuntu-latest
    timeout-minutes: 90
    env:
      CI_MEDIA_SCALE: smoke
      CI_SUITE: pin
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Build PIN UI instrumentation APK
        run: ./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest -PEMULATOR_TEST_BUILD_TYPE=debug --stacktrace
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Real PIN UI setup verification and source-revocation check
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          target: google_apis
          profile: pixel_6
          ram-size: 4096M
          disk-size: 4096M
          disable-animations: true
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          script: bash tools/emulator-tests/run_on_device.sh
      - name: Archive PIN assertions and security logs, including failures
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: gallery-emulator-pin-${{ github.run_id }}
          path: |
            tools/emulator-tests/output/**
            app/build/outputs/androidTest-results/**
            app/build/reports/androidTests/**
          retention-days: 14
          if-no-files-found: warn
````

### `app/src/androidTest/kotlin/org/fossify/gallery/emulatortests/PhotoProfileEmulatorTest.kt`

````kotlin
package org.fossify.gallery.emulatortests

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.adapters.MyPagerAdapter
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.MEDIUM
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.test.uiautomator.UiDevice
import android.graphics.Color
import android.os.Build
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.updateFavorite
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID

/** REAL Android filesystem/MediaStore tests; NOT a PIN or real Viewer gesture test. */
@RunWith(AndroidJUnit4::class)
class PhotoProfileEmulatorTest {
    companion object {
        private lateinit var root: File
        private var scale = "smoke"
        private var suite = "smoke"
        private var countA = 0

        @BeforeClass @JvmStatic fun buildMediaFixtures() {
            scale = InstrumentationRegistry.getArguments().getString("scale") ?: "smoke"
            suite = InstrumentationRegistry.getArguments().getString("suite") ?: "smoke"
            if (Build.VERSION.SDK_INT >= 30) assertTrue(Environment.isExternalStorageManager())
            val sizes = when (scale) {
                "smoke" -> mapOf("A" to 64, "N" to 64, "P" to 64)
                "large" -> mapOf("A" to 12011, "N" to 12013, "P" to 12017)
                "segment" -> mapOf("A" to 20501, "N" to 96, "P" to 96)
                "ceiling" -> mapOf("A" to 50009, "N" to 96, "P" to 96)
                else -> throw AssertionError("Invalid scale: " + scale)
            }
            countA = sizes.getValue("A")
            root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryCI")

            // Shell can stage host-generated files in app-private storage but
            // cannot write shared media directories on current Android images.
            // Copy from the app context, which owns the MANAGE_EXTERNAL_STORAGE grant.
            val staging = File(
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "gallery-ci-import"
            )
            val stagedMedia = File(staging, "GalleryCI")
            if (stagedMedia.isDirectory) {
                assertTrue("Could not copy staged media into Pictures",
                    stagedMedia.copyRecursively(root, overwrite = true))
                stagedMedia.walkTopDown().filter { it.isFile }.forEach { source ->
                    File(root, source.relativeTo(stagedMedia).path).setLastModified(source.lastModified())
                }
                staging.deleteRecursively()
            }

            // GitHub Actions may bulk-extract host-created, valid, uniquely
            // encoded PNG fixtures. Avoid 50k individual PNG compressions on
            // the emulator; still assert exact counts and add JPEG/SVG media.
            val imported = File(root, ".ci_import_ready")
            if (imported.isFile) {
                assertEquals("Wrong imported test scale", scale, imported.readText().trim())
                for ((source, count) in sizes) {
                    val folder = File(root, source)
                    assertTrue("Missing imported folder " + source, folder.isDirectory)
                    assertEquals("Partial imported PNG fixture " + source, count,
                        folder.listFiles().orEmpty().count { it.extension.equals("png", true) })
                    val jpegBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                    jpegBitmap.eraseColor(Color.rgb(source[0].code % 256, 91, 173))
                    val bytes = ByteArrayOutputStream()
                    check(jpegBitmap.compress(Bitmap.CompressFormat.JPEG, 85, bytes))
                    jpegBitmap.recycle()
                    File(folder, "CI_sample.jpg").writeBytes(bytes.toByteArray())
                    File(folder, "CI_sample.svg").writeText(
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\">" +
                            "<rect width=\"24\" height=\"24\" fill=\"#336699\"/></svg>"
                    )
                }
                assertTrue("Imported .nomedia absent", File(root, "N/.nomedia").isFile)
                Log.i("GalleryEmulatorFixture", "IMPORTED scale=" + scale + " roots=" + sizes)
                return
            }

            // Reversible source-qualified 32-bit identity, unique at 50,009 items.
            // Encode into the actual image pixels so filename/path correctness
            // cannot disguise stale or incorrectly bound drawable contents.
            val markerBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(32 * 32)
            fun markedPng(source: String, index: Int): ByteArray {
                val key = (source[0].code.toLong() shl 20) or (index + 1).toLong()
                for (y in 0 until 32) for (x in 0 until 32) {
                    val bit = (key ushr ((y / 4) * 8 + x / 4)) and 1L
                    pixels[y * 32 + x] = if (bit == 1L) Color.rgb(228, 42, 86)
                        else Color.rgb(26, 72, 184)
                }
                markerBitmap.setPixels(pixels, 0, 32, 0, 0, 32, 32)
                val bytes = ByteArrayOutputStream(180)
                check(markerBitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                return bytes.toByteArray()
            }
            for ((source, count) in sizes) {
                val folder = File(root, source)
                assertTrue(folder.mkdirs() || folder.isDirectory)
                if (source == "N") File(folder, ".nomedia").writeText("")
                for (index in 0 until count) {
                    val filename = if (index % 4 == 0) "IMG_" + index + ".png" else
                        "IMG_" + index.toString().padStart(6, '0') + ".png"
                    val file = File(folder, filename)
                    file.writeBytes(markedPng(source, index))
                    assertTrue(file.setLastModified(1578000000000L + ((index * 7919L + source[0].code) % 180000L) * 60000L))
                }
                assertEquals(count, folder.listFiles().orEmpty().count { it.extension.equals("png", true) })
                // Real decodable JPEG plus a valid SVG in every selected source.
                val jpgBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                jpgBitmap.eraseColor(Color.rgb(source[0].code % 256, 91, 173))
                val jpegBytes = ByteArrayOutputStream()
                check(jpgBitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpegBytes))
                jpgBitmap.recycle()
                File(folder, "CI_sample.jpg").writeBytes(jpegBytes.toByteArray())
                File(folder, "CI_sample.svg").writeText(
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\">" +
                        "<rect width=\"24\" height=\"24\" fill=\"#336699\"/></svg>"
                )

            }
            markerBitmap.recycle()
            assertTrue(File(root, "N/.nomedia").isFile)
        }
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val profileIds = ArrayList<String>()
    private var previousSorting = 0

    @Before fun before() { previousSorting = context.config.getFolderSorting(PhotoProfileMediaLoader.PATH) }
    @After fun after() {
        context.config.saveCustomSorting(PhotoProfileMediaLoader.PATH, previousSorting)
        val store = PhotoProfileRepository.of(context)
        profileIds.forEach(store::delete)
        File(root, "DeleteRace").deleteRecursively()
    }

    private fun load(sources: List<String>, sorting: Int, favorites: Boolean = false): List<Medium> {
        context.config.saveCustomSorting(PhotoProfileMediaLoader.PATH, sorting)
        val profile = PhotoProfile(UUID.randomUUID().toString(), "CI",
            sources.map { PhotoSource(PhotoSourceType.FOLDER, File(root, it).canonicalPath) } +
                (if (favorites) listOf(PhotoSource(PhotoSourceType.FAVORITES)) else emptyList()))
        PhotoProfileRepository.of(context).save(profile)
        profileIds.add(profile.id)
        return PhotoProfileMediaLoader(context).load(profile.id).filterIsInstance<Medium>()
    }

    private fun verify(sources: List<String>, actual: List<Medium>) {
        val roots = sources.map { File(root, it).canonicalPath }.toSet()
        val expected = sources.flatMap { name ->
            File(root, name).listFiles().orEmpty()
                .filter { it.extension.lowercase(Locale.ROOT) in setOf("png", "jpg", "svg") }
                .map { it.canonicalPath }
        }.toSet()
        val paths = actual.map { it.path }
        assertEquals("Duplicate media", paths.size, paths.distinct().size)
        val missing = expected - paths.toSet()
        assertTrue("Missing " + missing.size + " media: " + missing.take(5), missing.isEmpty())
        assertTrue("Outside profile", actual.all { it.parentPath in roots })
    }

    @Test fun selectedNomediaAndPublicFolderCombinations() {
        val all = listOf(listOf("A"), listOf("N"), listOf("P"), listOf("A", "N"),
            listOf("A", "P"), listOf("N", "P"), listOf("A", "N", "P"))
        val chosen = if (scale == "smoke") all else listOf(all[0], all[1], all[3], all[6])
        for (sources in chosen) verify(sources, load(sources, SORT_BY_DATE_MODIFIED or SORT_DESCENDING))
    }

    @Test fun fifteenMediaOrderingModes() {
        val types = listOf(SORT_BY_NAME, SORT_BY_PATH, SORT_BY_SIZE, SORT_BY_DATE_MODIFIED, SORT_BY_DATE_TAKEN)
        val modes = if (scale == "smoke") types.flatMap { listOf(it, it or SORT_DESCENDING) } +
            listOf(SORT_BY_RANDOM, SORT_BY_NAME or SORT_USE_NUMERIC_VALUE,
                SORT_BY_NAME or SORT_USE_NUMERIC_VALUE or SORT_DESCENDING,
                SORT_BY_PATH or SORT_USE_NUMERIC_VALUE,
                SORT_BY_PATH or SORT_USE_NUMERIC_VALUE or SORT_DESCENDING)
            else listOf(SORT_BY_SIZE or SORT_DESCENDING, SORT_BY_DATE_MODIFIED or SORT_DESCENDING, SORT_BY_RANDOM)
        val source = if (scale == "smoke") listOf("A", "N") else listOf("A")
        for (mode in modes) {
            val media = load(source, mode)
            verify(source, media)
            if (mode and SORT_BY_RANDOM != 0) continue
            val numeric = mode and SORT_USE_NUMERIC_VALUE != 0
            fun compare(a: Medium, b: Medium): Int = when {
                mode and SORT_BY_NAME != 0 -> {
                    val x = a.name.lowercase(Locale.getDefault())
                    val y = b.name.lowercase(Locale.getDefault())
                    if (numeric) AlphanumericComparator().compare(x, y) else x.compareTo(y)
                }
                mode and SORT_BY_PATH != 0 -> {
                    val x = a.path.lowercase(Locale.getDefault())
                    val y = b.path.lowercase(Locale.getDefault())
                    if (numeric) AlphanumericComparator().compare(x, y) else x.compareTo(y)
                }
                mode and SORT_BY_SIZE != 0 -> a.size.compareTo(b.size)
                mode and SORT_BY_DATE_MODIFIED != 0 -> a.modified.compareTo(b.modified)
                else -> a.taken.compareTo(b.taken)
            }
            media.zipWithNext().forEachIndexed { index, pair ->
                val result = compare(pair.first, pair.second)
                assertTrue("Wrong order mode=" + mode + " index=" + index,
                    if (mode and SORT_DESCENDING != 0) result >= 0 else result <= 0)
            }
        }
    }

    @Test fun largeMediaBoundary() {
        org.junit.Assume.assumeTrue("large boundary requested", scale != "smoke")
        val media = load(listOf("A"), SORT_BY_NAME)
        verify(listOf("A"), media)
        assertTrue("Expected " + countA + ", found " + media.size, media.size >= countA)
    }

    @Test fun virtualFavoritesSourceOverlapNomediaVisibilityAndDynamicMembership() {
        val a = File(root, "A/IMG_0.png").canonicalPath
        val n = File(root, "N/IMG_000001.png").canonicalPath
        val p = File(root, "P/IMG_000002.png").canonicalPath
        val testPaths = listOf(a, n, p)
        testPaths.forEach { path -> assertTrue("Missing favorite fixture " + path, File(path).isFile) }
        val originals = context.getFavoritePaths().toSet()
        try {
            testPaths.forEach { path ->
                assertTrue("Could not favorite " + path, context.updateFavorite(path, true))
            }
            val favoritesOnly = load(emptyList(), SORT_BY_NAME, favorites = true).map { it.path }.toSet()
            assertTrue("Public A favorite missing", a in favoritesOnly)
            assertTrue("Public P favorite missing", p in favoritesOnly)
            assertFalse("A .nomedia source not explicitly selected must not be exposed via Favorites", n in favoritesOnly)
            val both = load(listOf("A", "N"), SORT_BY_NAME, favorites = true)
            val paths = both.map { it.path }
            assertEquals("Folder+Favorites overlap duplicated", paths.size, paths.distinct().size)
            assertTrue("N explicitly selected but missing favorite", n in paths)
            assertTrue("P favorite outside folders missing", p in paths)
            context.updateFavorite(p, false)
            val afterRemoval = load(emptyList(), SORT_BY_NAME, favorites = true).map { it.path }.toSet()
            assertFalse("Unfavorited media reappeared", p in afterRemoval)
        } finally {
            testPaths.filter { it !in originals }.forEach { context.updateFavorite(it, false) }
        }
    }

    @Test fun deletedMediaDoesNotReturnOnProfileReload() {
        val folder = File(root, "DeleteRace").apply { mkdirs() }
        val fixture = File(root, "A/IMG_0.png")
        assertTrue("Missing source fixture", fixture.isFile)
        val paths = (0..3).map { index ->
            File(folder, "delete-$index.png").also { fixture.copyTo(it, overwrite = true) }.canonicalPath
        }
        val profile = PhotoProfile(UUID.randomUUID().toString(), "Delete race", listOf(
            PhotoSource(PhotoSourceType.FOLDER, folder.canonicalPath)
        ))
        PhotoProfileRepository.of(context).save(profile)
        profileIds.add(profile.id)

        val before = PhotoProfileMediaLoader(context).load(profile.id).filterIsInstance<Medium>()
        assertEquals(paths.toSet(), before.map { it.path }.toSet())
        val removed = paths.take(2).toSet()
        removed.forEach { path -> assertTrue("Could not remove $path", File(path).delete()) }
        PhotoContentRevision.changed()

        val after = PhotoProfileMediaLoader(context).load(profile.id).filterIsInstance<Medium>()
        assertEquals("Surviving files remain visible", paths.drop(2).toSet(), after.map { it.path }.toSet())
        assertTrue("Deleted paths must not be restored from cache", after.none { it.path in removed })
    }

    /**
     * Exercises the production post-delete callback with a deliberately reduced
     * one-item progressive window. The filesystem removal is real; invoking the
     * callback directly avoids making the system MediaStore confirmation dialog
     * part of this deterministic navigation regression.
     */
    @Test fun deletingOnlyLoadedPhotoKeepsViewerOpenAndRecoversPreviousGridNeighbor() {
        org.junit.Assume.assumeTrue("smoke real-viewer recovery", suite == "smoke" && scale == "smoke")
        val inst = InstrumentationRegistry.getInstrumentation()
        val folder = File(root, "DeleteContinuity").apply { mkdirs() }
        val fixture = File(root, "A/IMG_0.png")
        val paths = listOf("A", "B", "C", "D").map { name ->
            File(folder, "delete-$name.png").also { fixture.copyTo(it, overwrite = true) }.canonicalPath
        }
        val profile = PhotoProfile(UUID.randomUUID().toString(), "Delete continuity",
            listOf(PhotoSource(PhotoSourceType.FOLDER, folder.canonicalPath)))
        PhotoProfileRepository.of(context).save(profile)
        profileIds.add(profile.id)
        val oldSorting = context.config.getFolderSorting(PhotoProfileMediaLoader.PATH)
        context.config.saveCustomSorting(PhotoProfileMediaLoader.PATH, SORT_BY_NAME)
        val monitor = Instrumentation.ActivityMonitor(ViewPagerActivity::class.java.name, null, false)
        inst.addMonitor(monitor)
        var mediaActivity: MediaActivity? = null
        var viewerActivity: ViewPagerActivity? = null
        var initialPagerAdapter: MyPagerAdapter? = null
        try {
            mediaActivity = inst.startActivitySync(Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, PhotoProfileMediaLoader.PATH)
            }) as MediaActivity
            val grid = mediaActivity.findViewById<RecyclerView>(R.id.media_grid)
            var selectedRow = -1
            awaitCondition("four committed delete fixtures", 30000) {
                inst.runOnMainSync {
                    val adapter = grid.adapter as? MediaAdapter
                    selectedRow = adapter?.media?.indexOfFirst { it is Medium && it.path == paths[2] } ?: -1
                }
                selectedRow >= 0
            }
            inst.runOnMainSync { grid.scrollToPosition(selectedRow) }
            inst.waitForIdleSync()
            var clicked = false
            inst.runOnMainSync {
                clicked = grid.findViewHolderForAdapterPosition(selectedRow)?.itemView?.performClick() == true
            }
            assertTrue("MediaAdapter did not open selected fixture", clicked)
            viewerActivity = inst.waitForMonitorWithTimeout(monitor, 20000) as? ViewPagerActivity
            val viewer = viewerActivity ?: throw AssertionError("Grid did not open delete-continuity Viewer")
            awaitCondition("selected C page", 20000) {
                var selected = false
                inst.runOnMainSync {
                    val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                    val adapter = pager.adapter as? MyPagerAdapter
                    initialPagerAdapter = adapter
                    selected = adapter?.media?.getOrNull(pager.currentItem)?.path == paths[2]
                }
                selected
            }

            // Model the adverse timing from the report: the UI knows only the
            // clicked photo while the profile loader still owns the full scope.
            inst.runOnMainSync {
                val field = ViewPagerActivity::class.java.getDeclaredField("mMediaFiles").apply { isAccessible = true }
                val adapter = viewer.findViewById<ViewPager>(R.id.view_pager).adapter as MyPagerAdapter
                val selected = adapter.media.first { it.path == paths[2] }
                field.set(viewer, arrayListOf(selected))
                ViewPagerActivity::class.java.getDeclaredField("mPos").apply { isAccessible = true }.setInt(viewer, 0)
                ViewPagerActivity::class.java.getDeclaredField("photoCatalogMode").apply { isAccessible = true }.setBoolean(viewer, false)
                ViewPagerActivity::class.java.getDeclaredField("photoCatalogComplete").apply { isAccessible = true }.setBoolean(viewer, false)
                assertTrue("Physical delete failed", File(paths[2]).delete())
                val callback = ViewPagerActivity::class.java.declaredMethods.first { it.name == "hideDeletedMedium" }
                    .apply { isAccessible = true }
                callback.invoke(viewer, paths[2], null, false)
            }

            val expected = paths[1]
            awaitCondition("Viewer recovery to previous B photo", 45000) {
                var ready = false
                inst.runOnMainSync {
                    val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                    val adapter = pager.adapter as? MyPagerAdapter
                    val fragment = adapter?.getCurrentFragment(pager.currentItem)
                    val fragmentPath = (fragment?.arguments?.getSerializable(MEDIUM) as? Medium)?.path
                    val image = fragment?.view?.findViewById<ImageView>(R.id.gestures_view)
                    ready = !viewer.isFinishing && viewer.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        adapter != null && adapter === initialPagerAdapter && adapter.media.none { it.path == paths[2] } &&
                        adapter.media.getOrNull(pager.currentItem)?.path == expected && fragmentPath == expected &&
                        image?.drawable != null
                }
                ready
            }
            assertTrue("Deleted C returned from profile scan", File(paths[2]).exists().not())

            // Continue one page in the original sorted order; the deleted item
            // must not return and the Activity must remain the same instance.
            val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
            val swipe = IntArray(4)
            inst.runOnMainSync {
                val location = IntArray(2)
                pager.getLocationOnScreen(location)
                swipe[0] = location[0] + pager.width * 85 / 100
                swipe[1] = location[1] + pager.height / 2
                swipe[2] = location[0] + pager.width * 15 / 100
                swipe[3] = location[1] + pager.height / 2
            }
            assertTrue(UiDevice.getInstance(inst).swipe(
                swipe[0], swipe[1], swipe[2], swipe[3], 60))
            awaitCondition("next surviving photo D", 20000) {
                var next = false
                inst.runOnMainSync {
                    val adapter = pager.adapter as? MyPagerAdapter
                    next = adapter?.media?.getOrNull(pager.currentItem)?.path == paths[3] &&
                        adapter.media.none { it.path == paths[2] } && !viewer.isFinishing
                }
                next
            }
        } finally {
            viewerActivity?.runOnUiThread { viewerActivity?.finish() }
            mediaActivity?.runOnUiThread { mediaActivity?.finish() }
            inst.removeMonitor(monitor)
            inst.waitForIdleSync()
            context.config.saveCustomSorting(PhotoProfileMediaLoader.PATH, oldSorting)
            folder.deleteRecursively()
        }
    }

    /** Actual MediaActivity grid click -> ViewPagerActivity -> on-device pointer swipes.
     * Verifies the selected and adjacent Fragment identities against committed grid rows.
     * Screenshot evidence is stored on device and extracted by the workflow.
     * PIN and full rendered-pixel assertions remain separate unimplemented coverage.
     */
    @Test fun realGridClickViewerSwipeFollowsCommittedThumbOrder() {
        org.junit.Assume.assumeTrue("smoke case", suite == "smoke" && scale == "smoke")
        runViewerSequence(SORT_BY_NAME, listOf("A"), 6, "smoke")
    }

    @Test fun allFifteenSortModesRealUi() {
        org.junit.Assume.assumeTrue("15-mode real-UI matrix", suite == "sort_ui" && scale == "smoke")
        val bases = listOf(SORT_BY_NAME, SORT_BY_PATH, SORT_BY_SIZE,
            SORT_BY_DATE_MODIFIED, SORT_BY_DATE_TAKEN)
        val modes = bases.flatMap { listOf(it, it or SORT_DESCENDING) } +
            listOf(SORT_BY_RANDOM, SORT_BY_NAME or SORT_USE_NUMERIC_VALUE,
                SORT_BY_NAME or SORT_USE_NUMERIC_VALUE or SORT_DESCENDING,
                SORT_BY_PATH or SORT_USE_NUMERIC_VALUE,
                SORT_BY_PATH or SORT_USE_NUMERIC_VALUE or SORT_DESCENDING)
        assertEquals(15, modes.size)
        modes.forEachIndexed { i, sorting ->
            runViewerSequence(sorting, listOf("A", "N"), 20, "sort" + i)
        }
    }

    @Test fun deepScrollActualUi() {
        org.junit.Assume.assumeTrue("12k deep-scroll suite", suite == "deep" && scale == "large")
        runViewerSequence(SORT_BY_DATE_MODIFIED or SORT_DESCENDING, listOf("A"), 9000, "ordinary-deep-A", true, normalFolder = true)
        runViewerSequence(SORT_BY_DATE_MODIFIED or SORT_DESCENDING, listOf("A", "N"), 12000, "profile-nomedia-deep", true)
        runViewerSequence(SORT_BY_RANDOM, listOf("A", "N", "P"), 20000, "deepMix", true)
    }

    @Test fun boundaryCatalogActualUi() {
        org.junit.Assume.assumeTrue("20k/50k segmented suite",
            suite == "boundary" && scale in listOf("segment", "ceiling"))
        val anchors = if (scale == "segment") listOf(9999, 10001, 19999)
            else listOf(10000, 20000, 30000, 49990)
        anchors.forEach { at ->
            runViewerSequence(SORT_BY_NAME, listOf("A"), at, scale + at, true)
        }
    }

    private fun runViewerSequence(sorting: Int, sources: List<String>,
        selectedOrdinal: Int, caseId: String, deepScroll: Boolean = false,
        normalFolder: Boolean = false) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val sortingPath = if (normalFolder) File(root, "A").canonicalPath else PhotoProfileMediaLoader.PATH
        if (!normalFolder) {
            val profile = PhotoProfile(UUID.randomUUID().toString(), "CI " + caseId,
                sources.map { PhotoSource(PhotoSourceType.FOLDER, File(root, it).canonicalPath) })
            PhotoProfileRepository.of(context).save(profile)
            profileIds.add(profile.id)
        }
        val previousScenarioSorting = context.config.getFolderSorting(sortingPath)
        context.config.saveCustomSorting(sortingPath, sorting)
        Log.i("GalleryEmulatorMatrix", "BEGIN " + caseId + " mode=" + sorting + " sources=" + sources)
        val monitor = Instrumentation.ActivityMonitor(ViewPagerActivity::class.java.name, null, false)
        inst.addMonitor(monitor)
        var mediaActivity: MediaActivity? = null
        var viewerActivity: ViewPagerActivity? = null
        try {
            val intent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, sortingPath)
            }
            mediaActivity = inst.startActivitySync(intent) as MediaActivity
            val grid = mediaActivity.findViewById<RecyclerView>(R.id.media_grid)
            var committed = emptyList<String>()
            var selectedRow = -1
            val expectedCount = sources.sumOf { name ->
                File(root, name).listFiles().orEmpty().count {
                    it.extension.lowercase(Locale.ROOT) in setOf("png", "jpg", "svg")
                }
            }
            fun captureGrid(): Boolean {
                inst.runOnMainSync {
                    val adapter = grid.adapter as? MediaAdapter
                    val media = adapter?.media?.filterIsInstance<Medium>().orEmpty()
                    if (media.size >= expectedCount) {
                        committed = media.map { it.path }
                        val at = selectedOrdinal.coerceIn(2, (media.lastIndex - 2).coerceAtLeast(2))
                        val selected = media.mapIndexedNotNull { i, item ->
                            if (i >= 2 && i <= media.lastIndex - 2 && item.path.endsWith(".png"))
                                i to item else null
                        }.minByOrNull { kotlin.math.abs(it.first - at) }?.second
                        selectedRow = adapter?.media?.indexOfFirst { it is Medium && it.path == selected?.path } ?: -1
                    }
                }
                return committed.size >= expectedCount && selectedRow >= 0
            }
            awaitCondition("complete MediaActivity adapter " + caseId, 180000, ::captureGrid)
            var anchor = committed.firstOrNull { it == (
                (grid.adapter as? MediaAdapter)?.media?.getOrNull(selectedRow) as? Medium
            )?.path } ?: throw AssertionError("Clicked media not in committed grid")
            var clickedIndex = committed.indexOf(anchor)
            assertTrue("Need both neighbors around click", clickedIndex >= 2 && clickedIndex <= committed.lastIndex - 2)
            inst.runOnMainSync {
                grid.scrollToPosition(if (deepScroll) (selectedRow - 25).coerceAtLeast(0) else selectedRow)
            }
            inst.waitForIdleSync()
            if (deepScroll) {
                val pos = IntArray(2)
                var x = 0; var top = 0; var bottom = 0
                inst.runOnMainSync {
                    grid.getLocationOnScreen(pos)
                    x = pos[0] + grid.width / 2
                    top = pos[1] + grid.height / 4
                    bottom = pos[1] + grid.height * 3 / 4
                }
                repeat(3) {
                    assertTrue("Grid touch swipe failed", UiDevice.getInstance(inst).swipe(x, bottom, x, top, 30))
                }
                inst.waitForIdleSync()
                inst.runOnMainSync {
                    val visible = grid.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                        ?: throw AssertionError("RecyclerView layout manager mismatch")
                    val first = visible.findFirstVisibleItemPosition()
                    val last = visible.findLastVisibleItemPosition()
                    val adapter = grid.adapter as? MediaAdapter
                    selectedRow = (first..last).firstOrNull {
                        it >= (first + last) / 2 && adapter?.media?.getOrNull(it) is Medium
                    } ?: (first..last).firstOrNull { adapter?.media?.getOrNull(it) is Medium }
                        ?: throw AssertionError("No visible media after grid swipe")
                    anchor = (adapter?.media?.getOrNull(selectedRow) as? Medium)?.path.orEmpty()
                }
                clickedIndex = committed.indexOf(anchor)
                assertTrue("Deep click missing from committed list " + anchor,
                    clickedIndex >= 2 && clickedIndex <= committed.lastIndex - 2)
            }
            var holder: RecyclerView.ViewHolder? = null
            awaitCondition("visible thumbnail to click", 7000) {
                inst.runOnMainSync { holder = grid.findViewHolderForAdapterPosition(selectedRow) }
                holder != null
            }
            var clicked = false
            inst.runOnMainSync { clicked = holder?.itemView?.performClick() == true }
            assertTrue("MediaAdapter click was not handled", clicked)
            viewerActivity = inst.waitForMonitorWithTimeout(monitor, 20000) as? ViewPagerActivity
            val viewer = viewerActivity ?: throw AssertionError("Grid tap did not launch ViewPagerActivity")
            fun currentIdentity(): Pair<String?, String?> {
                var paths: Pair<String?, String?> = null to null
                inst.runOnMainSync {
                    val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                    val adapter = pager.adapter as? MyPagerAdapter
                    val index = pager.currentItem
                    val catalog = adapter?.media?.getOrNull(index)?.path
                    val fragment = (adapter?.getCurrentFragment(index)?.arguments?.getSerializable(MEDIUM) as? Medium)?.path
                    paths = catalog to fragment
                }
                return paths
            }
            fun capturePagerState(): String {
                var output = ""
                inst.runOnMainSync {
                    val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                    val adapter = pager.adapter as? MyPagerAdapter
                    val index = pager.currentItem
                    val selected = adapter?.media?.getOrNull(index)?.path
                    val currentFragment = adapter?.getCurrentFragment(index)
                    val fragmentPath = (currentFragment?.arguments?.getSerializable(MEDIUM) as? Medium)?.path
                    val drawableReady = (currentFragment?.view?.findViewById<ImageView>(R.id.gestures_view))?.drawable != null
                    output = "item=" + index + " count=" + (adapter?.count ?: -1) +
                        " selected=" + selected + " fragment=" + fragmentPath +
                        " drawableReady=" + drawableReady +
                        " pixelMatch=" + displayedPixelsMatch(currentFragment?.view?.findViewById<ImageView>(R.id.gestures_view), selected.orEmpty()) +
                        " focus=" + viewer.hasWindowFocus() +
                        " topPackage=" + UiDevice.getInstance(inst).currentPackageName +
                        " shown=" + viewer.window.decorView.isShown +
                        " resumed=" + viewer.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }
                return output
            }
            fun pageRendered(path: String): Boolean {
                var ready = false
                inst.runOnMainSync {
                    val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                    val adapter = pager.adapter as? MyPagerAdapter
                    val index = pager.currentItem
                    val fragment = adapter?.getCurrentFragment(index)
                    val fragmentPath = (fragment?.arguments?.getSerializable(MEDIUM) as? Medium)?.path
                    val image = fragment?.view?.findViewById<ImageView>(R.id.gestures_view)
                    ready = viewer.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        viewer.window.decorView.isShown &&
                        UiDevice.getInstance(inst).currentPackageName == context.packageName &&
                        adapter != null && adapter.count > 0 &&
                        (scale != "segment" && scale != "ceiling" || adapter.count <= committed.size) &&
                        adapter.media.getOrNull(index)?.path == path && fragmentPath == path &&
                        displayedPixelsMatch(image, path)
                }
                return ready
            }
            fun expect(path: String, stage: String) {
                try {
                    awaitCondition(stage + " pager & rendered bitmap", 20000) { pageRendered(path) }
                } catch (failure: AssertionError) {
                    Log.e("GalleryEmulatorSwipe", stage + " expected=" + path +
                        " observed=" + capturePagerState(), failure)
                    runCatching {
                        val bitmap = inst.uiAutomation.takeScreenshot()
                        if (bitmap != null) {
                            val folder = File(context.filesDir, "gallery-ci-evidence")
                            folder.mkdirs()
                            FileOutputStream(File(folder, caseId + "-failure-" + stage + ".png")).use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            bitmap.recycle()
                        }
                    }.onFailure { Log.e("GalleryEmulatorSwipe", "Could not capture failure screen", it) }
                    throw AssertionError(stage + " expected=" + path +
                        " observed=" + capturePagerState(), failure)
                }
                Log.i("GalleryEmulatorSwipe", stage + " expected=" + path +
                    " observed=" + capturePagerState())
                val bitmap = inst.uiAutomation.takeScreenshot()
                if (bitmap != null) {
                    val folder = File(context.filesDir, "gallery-ci-evidence")
                    folder.mkdirs()
                    FileOutputStream(File(folder, caseId + "-" + stage + ".png")).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            fun drag(forward: Boolean) {
                val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                val position = IntArray(2)
                var left = 0
                var right = 0
                var y = 0
                inst.runOnMainSync {
                    pager.getLocationOnScreen(position)
                    left = position[0] + (pager.width * 0.15f).toInt()
                    right = position[0] + (pager.width * 0.85f).toInt()
                    y = position[1] + pager.height / 2
                }
                val from = if (forward) right else left
                val to = if (forward) left else right
                Log.i("GalleryEmulatorSwipe", "swipe forward=" + forward +
                    " from=" + from + " to=" + to + " y=" + y +
                    " before=" + capturePagerState())
                assertTrue("UiAutomator could not perform swipe", UiDevice.getInstance(inst).swipe(from, y, to, y, 60))
                inst.waitForIdleSync()
                Log.i("GalleryEmulatorSwipe", "afterSwipe=" + capturePagerState())
            }
            Log.i("GalleryEmulatorMatrix", "GRID " + caseId + " count=" + committed.size + " anchor=" + clickedIndex + " path=" + anchor)
            expect(anchor, "initial")
            drag(true); expect(committed[clickedIndex + 1], "forward1")
            drag(true); expect(committed[clickedIndex + 2], "forward2")
            drag(false); expect(committed[clickedIndex + 1], "backward1")
            drag(false); expect(anchor, "backward2")
        } finally {
            viewerActivity?.runOnUiThread { viewerActivity?.finish() }
            mediaActivity?.runOnUiThread { mediaActivity?.finish() }
            inst.removeMonitor(monitor)
            inst.waitForIdleSync()
            context.config.saveCustomSorting(sortingPath, previousScenarioSorting)
            Log.i("GalleryEmulatorMatrix", "END " + caseId)
        }
    }

    /**
     * Compare the actual displayed drawable's per-cell pixel identity with
     * the decoded file, not merely Medium.path or a non-null drawable.
     */
    private fun displayedPixelsMatch(image: ImageView?, path: String): Boolean {
        if (image == null || image.drawable == null) return false
        if (!path.endsWith(".png", ignoreCase = true)) return true
        val expected = BitmapFactory.decodeFile(path) ?: return false
        return try {
            val rendered = image.drawable.toBitmap(32, 32)
            var matches = true
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val px = x * 4 + 2
                    val py = y * 4 + 2
                    val reference = expected.getPixel(px, py)
                    val actual = rendered.getPixel(px, py)
                    if (kotlin.math.abs(Color.red(reference) - Color.red(actual)) > 12 ||
                        kotlin.math.abs(Color.green(reference) - Color.green(actual)) > 12 ||
                        kotlin.math.abs(Color.blue(reference) - Color.blue(actual)) > 12) {
                        matches = false
                        break
                    }
                }
                if (!matches) break
            }
            matches
        } catch (error: Exception) {
            Log.w("GalleryEmulatorMatrix", "Drawable pixel comparison failed at " + path, error)
            false
        } finally {
            expected.recycle()
        }
    }

    private fun awaitCondition(name: String, timeoutMs: Long, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for " + name)
    }
}
````

### `app/src/androidTest/kotlin/org/fossify/gallery/emulatortests/PhotoProfilePinEmulatorTest.kt`

````kotlin
package org.fossify.gallery.emulatortests

import android.app.Instrumentation
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.extensions.authenticatePhotoPassword
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.configurePhotoPassword
import org.fossify.gallery.extensions.unlockPhotoProfile
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.PhotoProfile
import org.fossify.gallery.helpers.PhotoProfileAccessSessionManager
import org.fossify.gallery.helpers.PhotoProfileMediaLoader
import org.fossify.gallery.helpers.PhotoProfileRepository
import org.fossify.gallery.helpers.PhotoSource
import org.fossify.gallery.helpers.PhotoSourceType
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PIN-only UI authentication (no biometrics), with two truly protected source
 * folders. The folder LOCK records are seeded from fixture-specific PIN
 * hashes; the live capability is obtained EXCLUSIVELY via the real UI flow.
 * No code here writes source bindings, grants capabilities or mocks credentials.
 */
@RunWith(AndroidJUnit4::class)
class PhotoProfilePinEmulatorTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val device get() = UiDevice.getInstance(inst)

    private fun waitFor(label: String, deadline: Long = 20000L, ready: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + deadline
        while (SystemClock.elapsedRealtime() < until) {
            if (ready()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("PIN UI timeout waiting for " + label)
    }

    private fun button(id: String): androidx.test.uiautomator.UiObject2 =
        device.wait(Until.findObject(By.res(context.packageName, id)), 15000L)
            ?: throw AssertionError("Missing genuine PIN UI control " + id)

    private fun enterPin(value: String) {
        for (digit in value) button("pin_" + digit).click()
        button("pin_ok").click()
        Log.i("GalleryEmulatorPIN", "PIN challenge submitted (" + value.length + " digits); no PIN value logged")
    }

    private fun fixtureHash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
    }

    private fun cancelDialog() {
        val label = context.getString(org.fossify.commons.R.string.cancel)
        val control = device.wait(Until.findObject(By.text(label)), 12000)
            ?: throw AssertionError("Could not find cancellation in security dialog")
        control.click()
    }

    @Test fun realPinSetupWrongCancelMultiLockBindingAndRevocation() {
        Assume.assumeTrue("PIN suite only",
            InstrumentationRegistry.getArguments().getString("suite") == "pin" &&
                InstrumentationRegistry.getArguments().getString("scale") == "smoke")
        PhotoProfileEmulatorTest.buildMediaFixtures()
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryCI")
        val protectedP = File(root, "P").canonicalPath
        val protectedP2 = File(root, "P2").canonicalPath
        val secondFolder = File(protectedP2)
        assertTrue(secondFolder.mkdirs() || secondFolder.isDirectory)
        File(root, "P/IMG_0.png").copyTo(File(secondFolder, "SECOND.png"), overwrite = true)

        val repo = PhotoProfileRepository.of(context)
        val profile = PhotoProfile(UUID.randomUUID().toString(), "PIN regression",
            listOf("A", "P", "P2").map { PhotoSource(PhotoSourceType.FOLDER, File(root, it).canonicalPath) })
        val config = context.config
        val priorCredential = config.photoCredentialSnapshot()
        Assume.assumeFalse("Do not touch a preconfigured credential", priorCredential.enabled)
        val originalP = config.isFolderProtected(protectedP)
        val originalP2 = config.isFolderProtected(protectedP2)
        Assume.assumeFalse("Fixture source already locked", originalP || originalP2)
        var host: MediaActivity? = null
        try {
            val intent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, PhotoProfileMediaLoader.PATH)
            }
            host = inst.startActivitySync(intent) as MediaActivity
            val activity = host
            val setupComplete = AtomicBoolean(false)
            inst.runOnMainSync {
                activity.configurePhotoPassword { credential ->
                    setupComplete.set(credential != null)
                }
            }
            val pinLabel = context.getString(org.fossify.commons.R.string.pin)
            val choosePin = device.wait(Until.findObject(By.text(pinLabel)), 12000L)
                ?: throw AssertionError("Actual PIN-vs-pattern choice not shown")
            choosePin.click()
            enterPin("1357")
            enterPin("1357")
            waitFor("photo PIN credential configured") { setupComplete.get() }
            val credential = config.photoCredentialSnapshot()
            assertTrue("Configured credential is not current", config.isPhotoCredentialCurrent(credential))
            assertEquals(PROTECTION_PIN, credential.type)
            assertEquals("Fixture PIN hash differs from SecurityDialog hash", fixtureHash("1357"), credential.hash)

            // Synthetic source setup, not access grant: creates two real locks
            // and forces the app to verify distinct PIN secrets.
            config.addFolderProtection(protectedP, credential.hash, PROTECTION_PIN)
            config.addFolderProtection(protectedP2, fixtureHash("2468"), PROTECTION_PIN)
            repo.save(profile)
            PhotoProfileAccessSessionManager.revokeAll("pin_ci_preflight")
            val blocked = PhotoProfileMediaLoader(context).load(profile.id)
                .filterIsInstance<Medium>().map { it.path }
            assertTrue("Public A media unexpectedly absent", blocked.any { it.contains("/A/") })
            assertFalse("P leaked before unlocking", blocked.any { it.contains("/P/") })
            assertFalse("P2 leaked before unlocking", blocked.any { it.contains("/P2/") })

            // Incorrect first PIN followed by cancel must never create a token.
            val badComplete = AtomicBoolean(false)
            inst.runOnMainSync { activity.unlockPhotoProfile(profile.id) { badComplete.set(true) } }
            enterPin("0000")
            cancelDialog()
            waitFor("cancel callback") { badComplete.get() }
            assertNull("Wrong PIN + cancel yielded a token",
                PhotoProfileAccessSessionManager.currentToken(context))

            val goodComplete = AtomicBoolean(false)
            inst.runOnMainSync { activity.unlockPhotoProfile(profile.id) { goodComplete.set(true) } }
            enterPin("1357")
            // The same PIN covers P. P2 has a separate credential; the
            // original app should request separate consent and a second PIN.
            val okLabel = context.getString(org.fossify.commons.R.string.ok)
            val bindPrompt = device.wait(Until.findObject(By.text(okLabel)), 20000)
                ?: throw AssertionError("Missing second-source PIN challenge consent")
            bindPrompt.click()
            enterPin("2468")
            waitFor("two-source authorization") { goodComplete.get() }
            val token = PhotoProfileAccessSessionManager.currentToken(context)
            assertNotNull("Valid PINs were not granted a photo capability", token)
            val unlocked = PhotoProfileMediaLoader(context, sessionId = token).load(profile.id)
                .filterIsInstance<Medium>().map { it.path }
            assertTrue("P absent after real PIN verification", unlocked.any { it.contains("/P/") })
            assertTrue("P2 absent after separate PIN verification", unlocked.any { it.contains("/P2/") })
            Log.i("GalleryEmulatorPIN", "AUTHORIZED sources=A,P,P2 count=" + unlocked.size)

            PhotoProfileAccessSessionManager.revokeAll("pin_ci_verify_revoke")
            val revoked = PhotoProfileMediaLoader(context).load(profile.id)
                .filterIsInstance<Medium>().map { it.path }
            assertFalse("Revoked P content leaked", revoked.any { it.contains("/P/") })
            assertFalse("Revoked P2 content leaked", revoked.any { it.contains("/P2/") })
            Log.i("GalleryEmulatorPIN", "REVOKED sources=P,P2 count=" + revoked.size)
        } finally {
            host?.runOnUiThread { host?.finish() }
            inst.waitForIdleSync()
            PhotoProfileAccessSessionManager.revokeAll("pin_ci_cleanup")
            repo.delete(profile.id)
            config.removeFolderProtection(protectedP)
            config.removeFolderProtection(protectedP2)
            config.clearPhotoCredential()
            secondFolder.deleteRecursively()
        }
    }
}
````

### `tools/emulator-tests/run_on_device.sh`

````bash
#!/usr/bin/env bash
set -euo pipefail
OUT="$PWD/tools/emulator-tests/output"
mkdir -p "$OUT"
APP="org.fossify.gallery.debug"
capture() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  adb shell dumpsys meminfo "$APP" > "$OUT/meminfo.txt" 2>&1 || true
  adb shell dumpsys window windows > "$OUT/window.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
  adb shell df -h /sdcard > "$OUT/storage.txt" 2>&1 || true
  # Android 14 may block shell from Android/data; the app is a debug build,
  # so run-as can still export synthetic test screenshots without relaxing security.
  mkdir -p "$OUT/viewer-evidence"
  adb shell run-as "$APP" ls -la files/gallery-ci-evidence > "$OUT/viewer-evidence-list.txt" 2>&1 || true
  adb exec-out run-as "$APP" tar -cf - -C files gallery-ci-evidence \
    > "$OUT/viewer-evidence.tar" 2> "$OUT/viewer-export-error.txt" || true
  if [[ -s "$OUT/viewer-evidence.tar" ]]; then
    tar -xf "$OUT/viewer-evidence.tar" -C "$OUT/viewer-evidence" 2>> "$OUT/viewer-export-error.txt" || true
  fi
  adb shell screencap -p /sdcard/gallery-ci-final.png >/dev/null 2>&1 || true
  adb pull /sdcard/gallery-ci-final.png "$OUT/final.png" >/dev/null 2>&1 || true
}
trap capture EXIT
printf "suite=%s\nscale=%s\nsha=%s\n" "${CI_SUITE:-smoke}" "${CI_MEDIA_SCALE:-smoke}" "${GITHUB_SHA:-unknown}" > "$OUT/inputs.txt"
# Host-side media generation creates 50k truly distinct PNG pixels in seconds
# and retains source timestamps; this is faster than emulator PNG recompression.
python3 tools/emulator-tests/generate_media.py \
  --scale "$CI_MEDIA_SCALE" \
  --output "$RUNNER_TEMP/gallery-ci-media.tar" \
  --manifest "$OUT/media-manifest.json"

apk=$(find app/build/outputs/apk/foss/debug -maxdepth 1 -name '*.apk' -type f | head -n 1)
test -n "$apk"
adb install -r "$apk"
adb shell appops set "$APP" MANAGE_EXTERNAL_STORAGE allow
adb shell pm grant "$APP" android.permission.READ_MEDIA_IMAGES || true
adb shell pm grant "$APP" android.permission.READ_MEDIA_VIDEO || true
adb shell pm grant "$APP" android.permission.READ_EXTERNAL_STORAGE || true
adb shell appops get "$APP" MANAGE_EXTERNAL_STORAGE > "$OUT/appops.txt"

# Stage the archive in shell-owned /data/local/tmp, then extract as the app uid.
adb shell mkdir -p /sdcard/Pictures
adb push "$RUNNER_TEMP/gallery-ci-media.tar" /data/local/tmp/gallery-ci-media.tar
# The app uid can extract into its private files directory. Instrumentation then
# copies the fixture into shared Pictures through the app's all-files permission.
STAGING="/data/user/0/$APP/files/gallery-ci-import"
adb shell run-as "$APP" mkdir -p "$STAGING"
adb shell run-as "$APP" tar -xof /data/local/tmp/gallery-ci-media.tar -C "$STAGING"
adb shell rm /data/local/tmp/gallery-ci-media.tar
adb shell run-as "$APP" sh -c "find '$STAGING/GalleryCI' -type f | wc -l" > "$OUT/fixture-file-count.txt"
./gradlew :app:connectedFossDebugAndroidTest -PEMULATOR_TEST_BUILD_TYPE=debug "-Pandroid.testInstrumentationRunnerArguments.scale=$CI_MEDIA_SCALE" \
  "-Pandroid.testInstrumentationRunnerArguments.suite=${CI_SUITE:-smoke}" --stacktrace
````

### `tools/emulator-tests/generate_media.py`

````python
#!/usr/bin/env python3
"""Make unique-pixel PNG files on the host, not with 50k slow emulator Bitmap.compress calls.

Every image carries a source-qualified 32-bit identity in a stable 8x8
grid of 4x4-pixel blocks. These exact RGBA values are used by the
instrumentation drawable-vs-file pixel comparison. Standard-library-only.
"""
import argparse
import io
import json
import pathlib
import struct
import tarfile
import time
import zlib

ROOT = "GalleryCI"
ON = bytes((228, 42, 86, 255))
OFF = bytes((26, 72, 184, 255))
PNG_HEADER = b"\x89PNG\r\n\x1a\n"

def chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

def png(source: str, index: int) -> bytes:
    key = (ord(source) << 20) | (index + 1)
    pixels = bytearray()
    for y in range(32):
        pixels.append(0)  # PNG RGBA scanline filter None
        for x in range(32):
            pixels.extend(ON if (key >> ((y // 4) * 8 + x // 4)) & 1 else OFF)
    return (PNG_HEADER +
            chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 6, 0, 0, 0)) +
            chunk(b"tEXt", b"GalleryCI\x00" + f"{source}:{index}".encode()) +
            chunk(b"IDAT", zlib.compress(bytes(pixels), level=1)) +
            chunk(b"IEND", b""))

def add(archive, path, payload, modified):
    item = tarfile.TarInfo(path)
    item.mode = 0o644
    item.size = len(payload)
    item.mtime = modified
    archive.addfile(item, io.BytesIO(payload))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scale", choices=("smoke", "large", "segment", "ceiling"), required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--manifest", required=True)
    args = parser.parse_args()
    sizes = {
        "smoke": {"A": 64, "N": 64, "P": 64},
        "large": {"A": 12011, "N": 12013, "P": 12017},
        "segment": {"A": 20501, "N": 96, "P": 96},
        "ceiling": {"A": 50009, "N": 96, "P": 96},
    }[args.scale]
    start = time.monotonic()
    pathlib.Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.output, "w") as archive:
        add(archive, ROOT + "/N/.nomedia", b"", 1700000000)
        for source, size in sizes.items():
            for index in range(size):
                filename = f"IMG_{index}.png" if index % 4 == 0 else f"IMG_{index:06d}.png"
                modified = 1578000000 + ((index * 7919 + ord(source)) % 180000) * 60
                add(archive, f"{ROOT}/{source}/{filename}", png(source, index), modified)
        add(archive, ROOT + "/.ci_import_ready", args.scale.encode(), 1700000000)
    manifest = {"scale": args.scale, "pngPerSource": sizes, "totalPng": sum(sizes.values()),
                "pixel": "unique 32-bit source-qualified 8x8 marker, 32x32 RGBA",
                "durationSeconds": round(time.monotonic() - start, 3)}
    pathlib.Path(args.manifest).write_text(json.dumps(manifest, indent=2))
    print(json.dumps(manifest), flush=True)

if __name__ == "__main__":
    main()
````
