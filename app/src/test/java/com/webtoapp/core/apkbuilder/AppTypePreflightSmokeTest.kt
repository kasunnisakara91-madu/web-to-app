package com.webtoapp.core.apkbuilder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.webtoapp.data.model.AppType
import com.webtoapp.data.model.GalleryConfig
import com.webtoapp.data.model.GalleryItem
import com.webtoapp.data.model.GalleryItemType
import com.webtoapp.data.model.HtmlConfig
import com.webtoapp.data.model.HtmlFile
import com.webtoapp.data.model.HtmlFileType
import com.webtoapp.data.model.MultiWebConfig
import com.webtoapp.data.model.MultiWebSite
import com.webtoapp.data.model.WebApp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppTypePreflightSmokeTest {

    @Rule @JvmField
    val koinRule = com.webtoapp.util.KoinCleanupRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `every supported app type completes preflight without exceptions`() {
        val supported = AppType.entries.filter { it.isSupported }
        val results = mutableMapOf<AppType, ApkExportPreflightReport>()
        for (type in supported) {
            val app = makeApp(type)
            val report = runCatching { ApkExportPreflight.check(context, app) }
            assertThat(report.isFailure).isFalse()
            results[type] = report.getOrThrow()
        }

        println("==== Preflight smoke matrix ====")
        results.forEach { (type, report) ->
            println(
                "  $type -> passed=${report.passed}, " +
                    "errors=${report.errors.size}, " +
                    "warnings=${report.warnings.size}"
            )
            report.errors.forEach { e -> println("    error[${e.key}]: ${e.message}") }
        }

        assertThat(results.keys).containsExactlyElementsIn(supported)
    }

    @Test
    fun `removed app types are blocked by preflight`() {
        for (type in AppType.REMOVED_TYPES) {
            val report = ApkExportPreflight.check(
                context,
                WebApp(name = type.name, url = "https://example.com", appType = type)
            )
            assertThat(report.passed).isFalse()
            assertThat(report.errors.map { it.key }).contains("appType")
        }
    }

    private fun makeApp(type: AppType): WebApp {
        val packageName = "com.example.${type.name.lowercase()}"
        return when (type) {
            AppType.WEB -> WebApp(name = "Web", url = "https://example.com", appType = type)

            AppType.HTML, AppType.FRONTEND -> {
                val index = temp.newFile("${type.name.lowercase()}_index.html").apply {
                    writeText("<html></html>")
                }
                WebApp(
                    name = type.name,
                    url = "",
                    appType = type,
                    htmlConfig = HtmlConfig(
                        entryFile = "index.html",
                        files = listOf(HtmlFile("index.html", index.absolutePath, HtmlFileType.HTML))
                    )
                )
            }

            AppType.GALLERY -> {
                val image = temp.newFile("gallery_image.jpg").apply { writeBytes(ByteArray(64)) }
                WebApp(
                    name = "Gallery",
                    url = "",
                    appType = type,
                    galleryConfig = GalleryConfig(
                        items = listOf(
                            GalleryItem(
                                path = image.absolutePath,
                                type = GalleryItemType.IMAGE
                            )
                        )
                    )
                )
            }

            AppType.MULTI_WEB -> WebApp(
                name = "MultiWeb",
                url = "",
                appType = type,
                multiWebConfig = MultiWebConfig(
                    sites = listOf(MultiWebSite(id = "s1", name = "Example", url = "https://example.com"))
                )
            )

            // Removed types are covered by `removed app types are blocked by preflight`.
            else -> WebApp(name = type.name, url = "https://example.com", appType = type)
        }
    }
}
