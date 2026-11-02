package org.fossify.gallery.helpers.backup

import android.app.Application
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
class OxygenSettingsBackupCodecTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() { context.getSharedPrefs().edit().clear().commit() }
    @Test fun themeToolbarNavigationAndBrowsingRoundTrip() {
        val state = OxygenSettingsSnapshot(values = linkedMapOf("oxygen_classic_theme_activated" to true,
            "oxygen_classic_theme_preset" to "oxygen_blue", "oxygen_classic_appearance" to "dark",
            "oxygen_classic_custom_accent" to -1, "oxygen_legacy_text" to -16777216,
            "oxygen_toolbar_home_actions" to "search,sort,view", "oxygen_toolbar_folder_actions" to "search,group,filename",
            "oxygen_bottom_nav_order" to "PHOTOS,FAVORITES,ALL", "oxygen_favorite_show_excluded" to true,
            "oxygen_favorite_show_hidden" to true, "gallery_last_favorites_view_files" to true,
            "oxygenos_classic_last_main_view_all" to false, "photo_media_filter" to 3))
        assertEquals(state, OxygenSettingsBackupCodec.decode(OxygenSettingsBackupCodec.encode(state)))
    }
    @Test fun allEightColumnVariantsAreCapturedWithoutChangingPreferences() {
        val before = context.getSharedPrefs().all
        val state = OxygenSettingsBackupCodec.capture(context)
        assertTrue(state.values.keys.containsAll(OxygenSettingsBackupCodec.columns))
        assertEquals(8, OxygenSettingsBackupCodec.columns.size)
        assertEquals(before, context.getSharedPrefs().all)
    }
    @Test fun persistedDirectionalCountsArePreservedExactly() {
        OxygenSettingsBackupCodec.columns.forEachIndexed { index, key -> context.getSharedPrefs().edit().putInt(key, index + 1).commit() }
        val state = OxygenSettingsBackupCodec.capture(context)
        OxygenSettingsBackupCodec.columns.forEachIndexed { index, key -> assertEquals(index + 1, state.values[key]) }
    }
    @Test fun actualDynamicKeysAndPathCaseArePreserved() {
        val config = org.fossify.gallery.helpers.Config(context)
        config.saveCustomSorting("/Pictures/旅行", 1025)
        config.saveFolderGrouping("/Pictures/旅行", 2)
        config.saveFolderViewType("/Pictures/旅行", 3)
        config.customFoldersOrder = "/Pictures/旅行|||/Pictures/Other"
        val snapshot = OxygenSettingsBackupCodec.capture(context)
        assertEquals(1025, snapshot.values["sort_folder_/pictures/旅行"])
        assertEquals(2, snapshot.values["group_folder_/pictures/旅行"])
        assertEquals(3, snapshot.values["view_type_folder_/pictures/旅行"])
        assertEquals("/Pictures/旅行|||/Pictures/Other", snapshot.values[CUSTOM_FOLDERS_ORDER])
        assertEquals(snapshot, OxygenSettingsBackupCodec.decode(OxygenSettingsBackupCodec.encode(snapshot)))
    }
    @Test fun secretsAuthorizationsTemporaryStateWidgetsAndMigrationMarkersAreExcluded() {
        val prefs = context.getSharedPrefs()
        val forbidden = listOf("photo_source_bindings", "photo_protect_password_hash", "favorite_password_hash",
            "temporarily_show_hidden", "temporarily_show_excluded", "app_run_count", "last_filepicker_path",
            "were_favorites_migrated", "widget_42_path", "tree_uri", "protected_folder_hash_/private")
        forbidden.forEach { prefs.edit().putString(it, "secret").commit() }
        val json = OxygenSettingsBackupCodec.encode(OxygenSettingsBackupCodec.capture(context))
        forbidden.forEach { assertFalse(it, json.contains(it)) }
        assertFalse(json.contains("secret"))
    }
    @Test fun injectedCredentialAndUnknownKeyRejectEntireExtensionFile() {
        for (key in listOf("photo_source_bindings", "photo_protect_password_hash", "unknown", "sort_folder_../private"))
            assertThrows(IllegalArgumentException::class.java) { OxygenSettingsBackupCodec.decode("""{"version":1,"values":{"$key":"secret"}}""") }
    }
    @Test fun wrongVersionTypesAndRangesAreRejected() {
        for (text in listOf("""{"version":2,"values":{}}""", """{"version":1,"values":{"media_column_cnt":0}}""",
            """{"version":1,"values":{"oxygen_classic_theme_activated":"true"}}""",
            """{"version":1,"values":{"oxygen_classic_custom_accent":1.5}}""",
            """{"version":1,"values":{"oxygen_classic_appearance":"bad"}}"""))
            assertThrows(IllegalArgumentException::class.java) { OxygenSettingsBackupCodec.decode(text) }
    }
    @Test fun unsupportedOrDuplicateActionsAndInvalidNavigationAreRejected() {
        for (values in listOf(mapOf("oxygen_toolbar_home_actions" to "search,unknown"),
            mapOf("oxygen_toolbar_home_actions" to "search,group"), mapOf("oxygen_toolbar_folder_actions" to "search,search"),
            mapOf("oxygen_bottom_nav_order" to "ALL,FAVORITES"), mapOf("oxygen_bottom_nav_order" to "ALL,ALL,PHOTOS")))
            assertThrows(IllegalArgumentException::class.java) { OxygenSettingsBackupCodec.encode(OxygenSettingsSnapshot(values = values)) }
    }
    @Test fun oldSavedNavigationAndToolbarValuesCaptureTheirEffectiveRuntimeNormalization() {
        context.getSharedPrefs().edit().putString("oxygen_bottom_nav_order", "ALL,FAVORITE_FOLDERS")
            .putString("oxygen_toolbar_home_actions", "unknown,sort").commit()
        val state = OxygenSettingsBackupCodec.capture(context)
        assertEquals("ALL,FAVORITES,PHOTOS", state.values["oxygen_bottom_nav_order"])
        assertEquals("sort", state.values["oxygen_toolbar_home_actions"])
    }
    @Test fun historicToolbarValuesAreKeptAsCompatibilityData() {
        val state = OxygenSettingsSnapshot(values = mapOf("oxygen_classic_toolbar_custom" to true,
            "oxygen_classic_toolbar_color" to -1, "oxygen_classic_toolbar_manual_text" to true, "oxygen_classic_toolbar_text_color" to -16777216))
        assertEquals(state, OxygenSettingsBackupCodec.decode(OxygenSettingsBackupCodec.encode(state)))
    }
    @Test fun portableVideoSpeedAndMuteAreTypedAndBounded() {
        val state = OxygenSettingsSnapshot(values = mapOf(PLAYBACK_SPEED to 1.5f, PLAYBACK_SPEED_PROGRESS to 200, MUTE_VIDEOS to true))
        assertEquals(state, OxygenSettingsBackupCodec.decode(OxygenSettingsBackupCodec.encode(state)))
        for (speed in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 4f)) {
            assertThrows(IllegalArgumentException::class.java) { OxygenSettingsBackupCodec.encode(OxygenSettingsSnapshot(values = mapOf(PLAYBACK_SPEED to speed))) }
        }
    }
}
