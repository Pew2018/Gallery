package org.fossify.gallery.helpers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The product has no About surface: no menu entry, no launcher intent target, no
 * FAQ / licences / contributors page. These assertions exist so a future upstream
 * merge that reintroduces the Commons About screen fails a test instead of shipping.
 */
class NoAboutSurfaceTest {
    private val app = File("src/main")
    private val menus = listOf("menu_main.xml", "menu_media.xml")
    private val manifest = app.resolve("AndroidManifest.xml").readText()
    private val sources = app.resolve("kotlin").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun noMenuDeclaresAnAboutEntry() {
        menus.forEach { name ->
            val text = app.resolve("res/menu/$name").readText()
            assertFalse("$name still declares @+id/about", text.contains("@+id/about"))
            assertFalse("$name still shows the about title", text.contains("android:title=\"@string/about\""))
        }
    }

    @Test fun aboutAndItsChildPagesAreStrippedFromTheMergedManifest() {
        listOf("AboutActivity", "FAQActivity", "LicenseActivity", "ContributorsActivity").forEach { activity ->
            val declaration = manifest.substringAfter(
                "android:name=\"org.fossify.commons.activities.$activity\"", missingDelimiterValue = "")
            assertTrue("$activity is not removed from the manifest", declaration.isNotBlank())
            assertTrue(
                "$activity is declared without tools:node=\"remove\"",
                declaration.substringBefore("</activity>").contains("tools:node=\"remove\"")
            )
        }
    }

    @Test fun noSourceStartsTheAboutScreen() {
        val offenders = sources.filter { file ->
            val text = file.readText()
            listOf("launchAbout", "startAboutActivity", "R.id.about").any { text.contains(it) }
        }.map { it.name }
        assertTrue("About entry points still exist: $offenders", offenders.isEmpty())
    }
}
