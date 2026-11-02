package org.fossify.gallery.helpers.backup

import android.app.Application
import android.content.res.Configuration
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LegacySettingsRestoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val config get() = org.fossify.gallery.helpers.Config(context)
    @Before fun reset() { context.getSharedPrefs().edit().clear().commit() }
    @Test fun allDefaultExportKeysCanBeReadBack() {
        val snapshot = collectLegacySettings(config)
        val plan = LegacySettingsCodec.decode(LegacySettingsCodec.encode(snapshot))
        assertEquals(emptyMap<String, String>(), plan.invalidKeys)
        assertEquals(emptySet<String>(), plan.unknownKeys)
        assertEquals(snapshot.size, plan.values.size)
        assertTrue(plan.applicable)
        LegacySettingsRestore.apply(context, config, plan)
    }
    @Test fun collectionMergeKeepsLocalPathsAndRecycleBinPinSideEffect() {
        config.addIncludedFolders(setOf("/local"))
        config.addExcludedFolders(setOf("/exclude"))
        config.addPinnedFolders(setOf("/pinned"))
        config.showRecycleBinLast = true
        val plan = LegacySettingsCodec.decode("$INCLUDED_FOLDERS=/import\n$EXCLUDED_FOLDERS=/import-exclude\n$PINNED_FOLDERS=$RECYCLE_BIN")
        LegacySettingsRestore.apply(context, config, plan)
        assertEquals(setOf("/local", "/import"), config.includedFolders)
        assertEquals(setOf("/exclude", "/import-exclude"), config.excludedFolders)
        assertEquals(setOf("/pinned", RECYCLE_BIN), config.pinnedFolders)
        assertFalse(config.showRecycleBinLast)
    }
    @Test fun invalidLaterValueDoesNotWriteEarlierValues() {
        config.showAll = false
        val before = context.getSharedPrefs().all
        val plan = LegacySettingsCodec.decode("$SHOW_ALL=true\n$MEDIA_COLUMN_CNT=bad")
        assertThrows(IllegalArgumentException::class.java) { LegacySettingsRestore.apply(context, config, plan) }
        assertEquals(before, context.getSharedPrefs().all)
    }
    @Test fun unsupportedIconDoesNotWriteEarlierValues() {
        val before = context.getSharedPrefs().all
        val plan = LegacySettingsCodec.decode("$SHOW_ALL=true\n$APP_ICON_COLOR=123")
        assertThrows(IllegalArgumentException::class.java) { LegacySettingsRestore.apply(context, config, plan) }
        assertEquals(before, context.getSharedPrefs().all)
    }
    @Test fun scopedImportPreservesAllSecurityAndExtensionState() {
        val prefs = context.getSharedPrefs()
        prefs.edit().putString("photo_source_bindings", "local-only").putString("photo_protect_password_hash", "local-secret")
            .putString(PhotoProfileRepository.KEY, "local-profiles").commit()
        LegacySettingsRestore.apply(context, config, LegacySettingsCodec.decode("$SHOW_ALL=false\nphoto_source_bindings=malicious"))
        assertEquals("local-only", prefs.getString("photo_source_bindings", null))
        assertEquals("local-secret", prefs.getString("photo_protect_password_hash", null))
        assertEquals("local-profiles", prefs.getString(PhotoProfileRepository.KEY, null))
    }
    @Test fun columnsUseEffectiveScrollAndOrientationKeys() {
        val res = context.resources
        val old = Configuration(res.configuration)
        try {
            val next = Configuration(old).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
            @Suppress("DEPRECATION") res.updateConfiguration(next, res.displayMetrics)
            LegacySettingsRestore.apply(context, config, LegacySettingsCodec.decode("$SCROLL_HORIZONTALLY=true\n$MEDIA_COLUMN_CNT=7\n$DIR_COLUMN_CNT=8"))
            assertEquals(7, context.getSharedPrefs().getInt(MEDIA_LANDSCAPE_HORIZONTAL_COLUMN_CNT, -1))
            assertEquals(8, context.getSharedPrefs().getInt(DIR_LANDSCAPE_HORIZONTAL_COLUMN_CNT, -1))
        } finally { @Suppress("DEPRECATION") res.updateConfiguration(old, res.displayMetrics) }
    }
    @Test fun importWritesOnlyProfileKeyAndManagementStoreReadsItImmediately() {
        val prefs = context.getSharedPrefs()
        prefs.edit().putString("photo_source_bindings", "unchanged").commit()
        val incoming = PhotoProfileBackupCodec.decodeStrict("""{"version":1,"profiles":[{"id":"d4dfe496-4e61-4e85-a01b-18e967e59a53","name":"旅行","sources":[{"type":"FAVORITES"}]}]}""")
        PhotoProfileRepository.of(context).importState(incoming)
        assertEquals(incoming, PhotoProfileRepository.of(context).state())
        assertEquals("unchanged", prefs.getString("photo_source_bindings", null))
    }
}
