package org.fossify.gallery.helpers

import android.app.Application
import android.content.res.Configuration
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProtectionStatusFormatterTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun show(
        enabled: Boolean,
        configured: Boolean = true,
        biometric: Boolean = false,
        primary: Int = PROTECTION_PIN,
        fallback: Int = PROTECTION_PATTERN,
        legacy: Boolean = false,
        context: android.content.Context = app
    ) = ProtectionStatusFormatter.format(
        context,
        ProtectionStatusPresentation(enabled, configured, biometric, primary, fallback, legacy)
    )

    private fun localized(language: String): android.content.Context {
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(Locale(language))
        }
        return app.createConfigurationContext(configuration)
    }

    @Test fun allCredentialStatesUseSharedEnglishPresentation() {
        val en = localized("en")
        assertEquals("Protection off · Tap to configure", show(enabled = false))
        assertEquals("Protection on · PIN", show(enabled = true, primary = PROTECTION_PIN))
        assertEquals("Protection on · Pattern", show(enabled = true, primary = PROTECTION_PATTERN))
        assertEquals(
            "Protection on · Biometrics · Backup: PIN",
            show(enabled = true, biometric = true, primary = PROTECTION_PIN, fallback = PROTECTION_PIN)
        )
        assertEquals(
            "Protection on · Biometrics · Backup: Pattern",
            show(enabled = true, biometric = true, fallback = PROTECTION_PATTERN)
        )
        assertEquals(
            "Protection error · Cannot verify",
            show(enabled = true, configured = false)
        )
        assertEquals(
            "Legacy favorites lock · Tap to merge protection",
            show(enabled = false, legacy = true)
        )
        assertEquals("Favorites password", en.getString(org.fossify.gallery.R.string.favorite_password_title))
        assertEquals("Photo browsing password", en.getString(org.fossify.gallery.R.string.photo_password_title))
    }

    @Test fun bothProtectionsShareLocalizedStatusAndChineseLabels() {
        val zh = localized("zh")
        assertEquals("保护已关闭 · 点击设置", show(enabled = false, context = zh))
        assertEquals("保护已开启 · PIN 码", show(enabled = true, primary = PROTECTION_PIN, context = zh))
        assertEquals("保护已开启 · 图案", show(enabled = true, primary = PROTECTION_PATTERN, context = zh))
        assertEquals(
            "保护已开启 · 生物识别 · 备用：图案",
            show(enabled = true, biometric = true, fallback = PROTECTION_PATTERN, context = zh)
        )
        assertEquals("保护配置异常 · 无法验证", show(enabled = true, configured = false, context = zh))
        val favoriteState = ProtectionStatusPresentation(true, true, true, PROTECTION_PIN, PROTECTION_PIN)
        val photoState = ProtectionStatusPresentation(true, true, true, PROTECTION_PIN, PROTECTION_PIN)
        assertEquals(ProtectionStatusFormatter.format(zh, favoriteState), ProtectionStatusFormatter.format(zh, photoState))
        assertEquals("收藏密码", zh.getString(org.fossify.gallery.R.string.favorite_password_title))
        assertEquals("照片浏览密码", zh.getString(org.fossify.gallery.R.string.photo_password_title))
    }

    @Test fun mainPinOrPatternNeverShowsBackupAndFormatterAcceptsNoSecretMaterial() {
        val pin = show(enabled = true, primary = PROTECTION_PIN)
        val pattern = show(enabled = true, primary = PROTECTION_PATTERN)
        assertFalse(pin.contains("Backup"))
        assertFalse(pattern.contains("Backup"))
        assertFalse(pin.contains("not set"))
        assertTrue(ProtectionStatusPresentation::class.java.declaredFields.none {
            it.name.contains("hash", ignoreCase = true) || it.name.contains("token", ignoreCase = true)
        })
    }
}
