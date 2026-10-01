package com.webtoapp.core.apkbuilder

import com.google.common.truth.Truth.assertThat
import com.webtoapp.data.model.GalleryItem
import com.webtoapp.data.model.GalleryItemType
import com.webtoapp.data.model.HtmlFile
import com.webtoapp.data.model.HtmlFileType
import com.webtoapp.data.model.MultiWebSite
import com.webtoapp.data.model.CustomCaCertificate
import com.webtoapp.data.model.NetworkTrustConfig
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BuildInputPreflightTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `web app has no local input requirements`() {
        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(appType = "WEB")
        )

        assertThat(result.passed).isTrue()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun `html app passes when entry and files are readable`() {
        val index = temp.newFile("index.html").apply {
            writeText("<html></html>")
        }
        val script = temp.newFile("app.js").apply {
            writeText("console.log('ready')")
        }

        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "HTML",
                htmlEntryFile = "index.html",
                htmlFiles = listOf(
                    HtmlFile("index.html", index.absolutePath, HtmlFileType.HTML),
                    HtmlFile("app.js", script.absolutePath, HtmlFileType.JS)
                )
            )
        )

        assertThat(result.passed).isTrue()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun `html app rejects missing entry and missing file`() {
        val index = temp.newFile("index.html").apply {
            writeText("<html></html>")
        }

        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "HTML",
                htmlEntryFile = "missing.html",
                htmlFiles = listOf(
                    HtmlFile("index.html", index.absolutePath, HtmlFileType.HTML),
                    HtmlFile("style.css", temp.root.resolve("missing.css").absolutePath, HtmlFileType.CSS)
                )
            )
        )

        assertThat(result.passed).isFalse()
        assertThat(result.issues.map { it.key }).containsExactly("htmlEntryFile", "htmlFiles[1]").inOrder()
    }

    @Test
    fun `gallery app requires at least one readable media item`() {
        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "GALLERY",
                galleryItems = listOf(
                    GalleryItem(
                        path = temp.root.resolve("missing.mp4").absolutePath,
                        type = GalleryItemType.VIDEO,
                        name = "Missing video"
                    )
                )
            )
        )

        assertThat(result.passed).isFalse()
        assertThat(result.issues.single().key).isEqualTo("galleryItems[0]")
    }

    @Test
    fun `frontend app accepts readable project directory`() {
        val projectDir = temp.newFolder("frontend")

        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "FRONTEND",
                frontendProjectDir = projectDir
            )
        )

        assertThat(result.passed).isTrue()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun `frontend app accepts saved html file list without source project directory`() {
        val index = temp.newFile("index.html").apply {
            writeText("<html></html>")
        }

        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "FRONTEND",
                htmlEntryFile = "index.html",
                htmlFiles = listOf(HtmlFile("index.html", index.absolutePath, HtmlFileType.HTML))
            )
        )

        assertThat(result.passed).isTrue()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun `frontend app rejects empty saved html file`() {
        val index = temp.newFile("index.html")

        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "FRONTEND",
                htmlEntryFile = "index.html",
                htmlFiles = listOf(HtmlFile("index.html", index.absolutePath, HtmlFileType.HTML))
            )
        )

        assertThat(result.passed).isFalse()
        assertThat(result.issues.map { it.key }).contains("htmlFiles[0]")
        assertThat(result.issues.single { it.key == "htmlFiles[0]" }.message).contains("empty")
    }

    @Test
    fun `multi web app with local site requires resolved readable file`() {
        val projectDir = temp.newFolder("multi-web")
        File(projectDir, "site-a/index.html").apply {
            parentFile?.mkdirs()
            writeText("<html>ok</html>")
        }

        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "MULTI_WEB",
                multiWebProjectDir = projectDir,
                multiWebSites = listOf(
                    MultiWebSite(
                        id = "site-a",
                        name = "Local",
                        type = "LOCAL",
                        localFilePath = "site-a/index.html"
                    )
                )
            )
        )

        assertThat(result.passed).isTrue()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun `multi web app rejects missing local file`() {
        val projectDir = temp.newFolder("multi-web")

        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "MULTI_WEB",
                multiWebProjectDir = projectDir,
                multiWebSites = listOf(
                    MultiWebSite(
                        id = "site-a",
                        name = "Local",
                        type = "LOCAL",
                        localFilePath = "site-a/index.html"
                    )
                )
            )
        )

        assertThat(result.passed).isFalse()
        assertThat(result.issues.map { it.key }).contains("multiWebSites[0]")
    }

    @Test
    fun `custom ca must point to readable x509 certificate`() {
        val invalid = temp.newFile("invalid.pem").apply {
            writeText("not a certificate")
        }

        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "WEB",
                networkTrustConfig = NetworkTrustConfig(
                    customCaCertificates = listOf(
                        CustomCaCertificate(
                            id = "bad",
                            displayName = "Bad CA",
                            filePath = invalid.absolutePath,
                            sha256 = "bad"
                        )
                    )
                )
            )
        )

        assertThat(result.passed).isFalse()
        assertThat(result.issues.single().key).isEqualTo("customCa[0]")
        assertThat(result.issues.single().message).contains("valid X.509")
    }

    @Test
    fun `multi web URL-only EXISTING sites pass without local file path`() {
        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "MULTI_WEB",
                multiWebSites = listOf(
                    MultiWebSite(
                        id = "site-a",
                        name = "Baidu",
                        type = "EXISTING",
                        url = "https://www.baidu.com",
                        localFilePath = ""
                    ),
                    MultiWebSite(
                        id = "site-b",
                        name = "Example",
                        type = "EXISTING",
                        url = "https://example.com",
                        localFilePath = ""
                    )
                )
            )
        )

        assertThat(result.passed).isTrue()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun `multi web default URL sites pass without local project dir`() {
        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "MULTI_WEB",
                multiWebSites = listOf(
                    MultiWebSite(
                        id = "site-a",
                        name = "One",
                        url = "https://one.example"
                    ),
                    MultiWebSite(
                        id = "site-b",
                        name = "Two",
                        url = "https://two.example"
                    )
                )
            )
        )

        assertThat(result.passed).isTrue()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun `multi web EXISTING site with blank url and blank path is rejected`() {
        val result = BuildInputPreflight.check(
            BuildInputPreflightRequest(
                appType = "MULTI_WEB",
                multiWebSites = listOf(
                    MultiWebSite(
                        id = "site-a",
                        name = "Broken",
                        type = "EXISTING",
                        url = "",
                        localFilePath = ""
                    )
                )
            )
        )

        assertThat(result.passed).isFalse()
        assertThat(result.issues.map { it.key }).contains("multiWebSites[0]")
    }

}
