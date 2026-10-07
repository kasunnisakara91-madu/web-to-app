package com.webtoapp.core.apkbuilder

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Pins the launcher-background rewrite done by [ArscRebuilder]: the template ships
 * `color/ic_launcher_background` as a black color int. Adaptive icon XML references
 * that color, so a rebuild that has a derived background color must store an opaque
 * ARGB color int. A string path does not inflate as a color, and the installed
 * adaptive icon would stay black. With no derived color, the template black stays.
 *
 * Localization goes through [ArscRebuilder.findLauncherBackgroundEntry] — the same
 * by-name lookup the runtime uses. An earlier revision scanned for the entry at a
 * hardcoded index (0x71) inside the color type chunk, which silently assumed a fixed
 * resource-merge order; adding the Credential Manager libraries shifted the indices
 * and broke the test without any functional change.
 */
class ArscRebuilderLauncherBgTest {

    private val templateApk: File by lazy {
        resolveFile(
            "src/main/assets/template/webview_shell.apk",
            "app/src/main/assets/template/webview_shell.apk"
        )
    }

    private fun assumeTemplateBuilt() {
        assumeTrue(
            "shell template not built — run ':app:syncShellTemplateApk' first",
            templateApk.exists()
        )
    }

    private fun readTemplateArsc(): ByteArray {
        assumeTemplateBuilt()
        return ZipFile(templateApk).use { it.getInputStream(it.getEntry("resources.arsc")).readBytes() }
    }

    @Test
    fun `original launcher background entry value is a black color int`() {
        val original = readTemplateArsc()
        val entry = ArscRebuilder().findLauncherBackgroundEntry(original)

        assertThat(entry).isNotNull()
        assertThat(entry!![1]).isEqualTo(0x1d)
        assertThat(entry[2]).isEqualTo(0xff000000.toInt())
    }

    @Test
    fun `rebuild paints launcher background as an opaque argb color`() {
        val original = readTemplateArsc()
        val color = 0x00ABCDEF
        val rebuilt = ArscRebuilder().rebuildWithNewAppNameAndIcons(
            original,
            "TestApp",
            replaceIcons = true,
            launcherBackgroundColor = color
        )

        assertThat(rebuilt.size).isGreaterThan(0)

        val entry = ArscRebuilder().findLauncherBackgroundEntry(rebuilt)
        assertThat(entry).isNotNull()
        assertThat(entry!![1]).isEqualTo(0x1c)
        assertThat(entry[2]).isEqualTo(color or 0xFF000000.toInt())
    }

    @Test
    fun `rebuild without a background color keeps the template black`() {
        val original = readTemplateArsc()
        val rebuilt = ArscRebuilder().rebuildWithNewAppNameAndIcons(
            original,
            "TestApp",
            replaceIcons = true
        )

        val entry = ArscRebuilder().findLauncherBackgroundEntry(rebuilt)
        assertThat(entry).isNotNull()
        assertThat(entry!![1]).isEqualTo(0x1d)
        assertThat(entry[2]).isEqualTo(0xff000000.toInt())
    }

    private fun resolveFile(vararg candidates: String): File {
        for (c in candidates) {
            val f = File(c)
            if (f.exists()) return f
            val f2 = File(System.getProperty("user.dir"), c)
            if (f2.exists()) return f2
        }
        return File(candidates.first())
    }
}
