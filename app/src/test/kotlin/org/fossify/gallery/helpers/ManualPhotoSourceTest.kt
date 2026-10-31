package org.fossify.gallery.helpers

import android.app.Application
import android.content.res.Configuration
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.extensions.config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ManualPhotoSourceTest {
    @Test fun manualFolderActionsAndSelectionSummaryHaveEnglishAndChineseResources() {
        val context = RuntimeEnvironment.getApplication()
        fun localized(locale: Locale) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) }
        )
        assertEquals("Add other folder", localized(Locale.US).getString(org.fossify.gallery.R.string.photo_source_add_other_folder))
        assertEquals("添加其他文件夹", localized(Locale.SIMPLIFIED_CHINESE).getString(org.fossify.gallery.R.string.photo_source_add_other_folder))
        assertEquals("2 selected", localized(Locale.US).getString(org.fossify.gallery.R.string.photo_source_selected_count, 2))
        assertEquals("已选择 2 项", localized(Locale.SIMPLIFIED_CHINESE).getString(org.fossify.gallery.R.string.photo_source_selected_count, 2))
    }

    @Test fun mediaFetcherReadsDirectMediaFromAnUnindexedFolderWithoutRecursing() {
        val context = RuntimeEnvironment.getApplication()
        val folder = Files.createTempDirectory("manual-photo-source")
        try {
            val direct = folder.resolve("direct.jpg")
            Files.write(direct, byteArrayOf(1, 2, 3))
            val child = Files.createDirectory(folder.resolve("child"))
            Files.write(child.resolve("nested.jpg"), byteArrayOf(4, 5, 6))

            val result = MediaFetcher(context, TYPE_IMAGES).getFilesFrom(
                folder.toString(), false, false, false, false, false,
                arrayListOf(), false, hashMapOf(), hashMapOf(), null
            )

            assertEquals(listOf(direct.toFile().absolutePath), result.map { it.path })
        } finally {
            folder.toFile().deleteRecursively()
        }
    }

    @Test fun validatorAcceptsReadableEmptyFoldersAndRejectsUriAndFilePaths() {
        val activity = Robolectric.buildActivity(SimpleActivity::class.java).setup().get()
        val folder = Files.createTempDirectory("manual-photo-source-empty")
        val file = Files.createTempFile("manual-photo-source-file", ".jpg")
        val hiddenRoot = Files.createTempDirectory("manual-photo-source-hidden")
        val hidden = Files.createDirectory(hiddenRoot.resolve(".hidden"))
        val hiddenAlias = Files.createSymbolicLink(hiddenRoot.resolve("visible-alias"), hidden)
        try {
            assertNull(ManualPhotoSourceValidator.validate(activity, folder.toString()))
            assertEquals(ManualPhotoSourceIssue.INVALID,
                ManualPhotoSourceValidator.validate(activity, "content://tree/not-a-path"))
            assertEquals(ManualPhotoSourceIssue.NOT_DIRECTORY,
                ManualPhotoSourceValidator.validate(activity, file.toString()))
            assertEquals(ManualPhotoSourceIssue.HIDDEN,
                ManualPhotoSourceValidator.validate(activity, hidden.toString()))
            assertEquals(ManualPhotoSourceIssue.HIDDEN,
                ManualPhotoSourceValidator.validate(activity, hiddenAlias.toString()))
            activity.config.excludedFolders = mutableSetOf(folder.toString())
            assertEquals(ManualPhotoSourceIssue.EXCLUDED,
                ManualPhotoSourceValidator.validate(activity, folder.toString()))
        } finally {
            activity.finish()
            folder.toFile().deleteRecursively()
            Files.deleteIfExists(file)
            Files.deleteIfExists(hiddenAlias)
            hiddenRoot.toFile().deleteRecursively()
        }
    }
}
