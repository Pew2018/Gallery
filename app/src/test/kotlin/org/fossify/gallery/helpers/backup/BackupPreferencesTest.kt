package org.fossify.gallery.helpers.backup

import android.app.Application
import android.content.SharedPreferences
import org.fossify.commons.extensions.getSharedPrefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupPreferencesTest {
    @Test fun failedCommitRollsBackScopedMemoryWithoutTouchingOtherPreferences() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPrefs()
        prefs.edit().clear().putString("existing", "before").putString("credential", "secret").commit()
        var commits = 0
        val failing = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val editor = prefs.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean {
                        editor.commit()
                        commits++
                        return commits > 1
                    }
                }
            }
        }
        assertThrows(IOException::class.java) { BackupPreferences.commit(failing, mapOf("existing" to "after", "new" to true)) }
        assertEquals("before", prefs.getString("existing", null)); assertFalse(prefs.contains("new"))
        assertEquals("secret", prefs.getString("credential", null)); assertEquals(2, commits)
    }
    @Test fun outputIsClosedWhenSnapshotEncodingFails() {
        var closed = false
        val output = object : java.io.ByteArrayOutputStream() { override fun close() { closed = true } }
        assertThrows(IllegalStateException::class.java) { BackupIo.writeText(output) { error("snapshot failed") } }
        assertTrue(closed)
    }
}
