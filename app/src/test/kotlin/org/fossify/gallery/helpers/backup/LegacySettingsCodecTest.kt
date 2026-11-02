package org.fossify.gallery.helpers.backup

import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.*
import org.junit.Assert.*
import org.junit.Test

class LegacySettingsCodecTest {
    @Test fun originalTxtRoundTripPreservesKeysAndEncoding() {
        val values = linkedMapOf<String, Any>(TEXT_COLOR to -1, USE_24_HOUR_FORMAT to true,
            EDITOR_BRUSH_SIZE to 0.05f, INCLUDED_FOLDERS to "/中文/A,/中文/a", DATE_FORMAT to "yyyy-MM-dd")
        val text = LegacySettingsCodec.encode(values)
        assertEquals("text_color=-1\nuse_24_hour_format=true\neditor_brush_size=0.05\nincluded_folders=/中文/A,/中文/a\ndate_format=yyyy-MM-dd\n", text)
        val plan = LegacySettingsCodec.decode(text)
        assertTrue(plan.applicable); assertEquals(-1, plan.values[TEXT_COLOR])
        assertEquals(setOf("/中文/A", "/中文/a"), plan.values[INCLUDED_FOLDERS])
    }
    @Test fun unknownKeysAreIgnoredWithoutRejectingKnownKeys() {
        val plan = LegacySettingsCodec.decode("future_key=1\n$SHOW_HIDDEN_MEDIA=true")
        assertTrue(plan.applicable); assertEquals(setOf("future_key"), plan.unknownKeys)
    }
    @Test fun duplicateKnownAndUnknownKeysAreReported() {
        for (key in listOf(SHOW_HIDDEN_MEDIA, "future_key")) {
            val plan = LegacySettingsCodec.decode("$SHOW_HIDDEN_MEDIA=true\n$key=false\n$key=true")
            assertFalse(plan.applicable); assertTrue(key in plan.duplicateKeys)
        }
    }
    @Test fun valueWithEqualsIsNeverSplitAgain() {
        val plan = LegacySettingsCodec.decode("$INCLUDED_FOLDERS=/Pictures/a=b")
        assertEquals(setOf("/Pictures/a=b"), plan.values[INCLUDED_FOLDERS]); assertTrue(plan.applicable)
    }
    @Test fun invalidIntegersAndRangesRejectWholePlan() {
        for (value in listOf("abc", "2147483648", "0", "21", "-1")) {
            val plan = LegacySettingsCodec.decode("$SHOW_HIDDEN_MEDIA=true\n$MEDIA_COLUMN_CNT=$value")
            assertFalse(plan.applicable); assertTrue(MEDIA_COLUMN_CNT in plan.invalidKeys)
        }
    }
    @Test fun invalidFloatsRejectWholePlan() {
        for (value in listOf("bad", "NaN", "Infinity", "1e90", "-1", "2")) {
            val plan = LegacySettingsCodec.decode("$EDITOR_BRUSH_SIZE=$value")
            assertFalse(plan.applicable); assertTrue(EDITOR_BRUSH_SIZE in plan.invalidKeys)
        }
    }
    @Test fun booleansMustBeExplicit() {
        for (value in listOf("1", "yes", "", "truefalse")) assertFalse(LegacySettingsCodec.decode("$SHOW_ALL=$value").applicable)
        assertEquals(true, LegacySettingsCodec.decode("$SHOW_ALL=TRUE").values[SHOW_ALL])
    }
    @Test fun emptyUnknownOnlyAndMalformedNeverSucceed() {
        for (value in listOf("", "\n", "unknown=x", "broken", "=value", "$SHOW_ALL=true\nbroken"))
            assertFalse(LegacySettingsCodec.decode(value).applicable)
    }
    @Test fun bomAndCrLfAreCompatible() {
        assertTrue(LegacySettingsCodec.decode("\uFEFF$SHOW_ALL=false\r\n").applicable)
    }
    @Test fun oversizedTxtRejected() {
        assertThrows(IllegalArgumentException::class.java) { LegacySettingsCodec.decode("x".repeat(8 * 1024 * 1024 + 1)) }
    }
    @Test fun invalidCoversAndDateFormatsRejectedBeforeApplication() {
        for (value in listOf("null", "{}", "[{}]", "[{\"path\":\"/a\",\"tmb\":null}]"))
            assertFalse(LegacySettingsCodec.decode("$ALBUM_COVERS=$value").applicable)
        assertFalse(LegacySettingsCodec.decode("$DATE_FORMAT=bad pattern").applicable)
    }
    @Test fun validLegacyRangeValuesAreNotRejected() {
        assertTrue(LegacySettingsCodec.decode("$VIEW_TYPE_FILES=3\n$VIEW_TYPE_FOLDERS=2\n$THUMBNAIL_SPACING=64\n$LAST_CONFLICT_RESOLUTION=4").applicable)
    }
}
