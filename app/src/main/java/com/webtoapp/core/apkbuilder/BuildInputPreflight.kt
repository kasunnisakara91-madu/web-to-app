package com.webtoapp.core.apkbuilder

import com.webtoapp.data.model.GalleryItem
import com.webtoapp.data.model.HtmlFile
import com.webtoapp.data.model.MultiWebSite
import com.webtoapp.data.model.NetworkTrustConfig
import com.webtoapp.util.NetworkTrustStorage
import java.io.File

data class BuildInputPreflightRequest(
    val appType: String,
    val htmlEntryFile: String = "index.html",
    val htmlFiles: List<HtmlFile> = emptyList(),
    val galleryItems: List<GalleryItem> = emptyList(),
    val multiWebSites: List<MultiWebSite> = emptyList(),
    val frontendProjectDir: File? = null,
    val multiWebProjectDir: File? = null,
    val networkTrustConfig: NetworkTrustConfig = NetworkTrustConfig()
)

data class BuildInputPreflightResult(
    val issues: List<BuildInputIssue>
) {
    val passed: Boolean get() = issues.isEmpty()
}

data class BuildInputIssue(
    val key: String,
    val message: String,
    val path: String? = null
) {
    fun summary(): String {
        return if (path.isNullOrBlank()) "$key: $message" else "$key: $message [$path]"
    }
}

object BuildInputPreflight {

    fun check(request: BuildInputPreflightRequest): BuildInputPreflightResult {
        val issues = mutableListOf<BuildInputIssue>()

        when (request.appType) {
            "HTML" -> {
                issues.requireHtmlFiles(request.htmlEntryFile, request.htmlFiles)
            }
            "GALLERY" -> {
                issues.requireGalleryItems(request.galleryItems)
            }
            "FRONTEND" -> {
                if (request.frontendProjectDir?.let { it.exists() && it.isDirectory && it.canRead() } != true) {
                    issues.requireHtmlFiles(
                        entryFile = request.htmlEntryFile,
                        htmlFiles = request.htmlFiles,
                        label = "Frontend app"
                    )
                }
            }
            "MULTI_WEB" -> {
                issues.requireMultiWebSites(
                    items = request.multiWebSites,
                    projectDir = request.multiWebProjectDir
                )
            }
        }

        issues.requireCustomCaCertificates(request.networkTrustConfig)

        return BuildInputPreflightResult(issues)
    }

    private fun MutableList<BuildInputIssue>.requireCustomCaCertificates(config: NetworkTrustConfig) {
        config.customCaCertificates.forEachIndexed { index, cert ->
            val file = File(cert.filePath)
            when {
                cert.filePath.isBlank() -> add(BuildInputIssue("customCa[$index]", "Custom CA path is blank"))
                !file.exists() -> add(BuildInputIssue("customCa[$index]", "Custom CA file does not exist", file.absolutePath))
                !file.isFile -> add(BuildInputIssue("customCa[$index]", "Custom CA path is not a file", file.absolutePath))
                !file.canRead() -> add(BuildInputIssue("customCa[$index]", "Custom CA file cannot be read", file.absolutePath))
                !NetworkTrustStorage.validateCertificateFile(file.absolutePath) -> {
                    add(BuildInputIssue("customCa[$index]", "Custom CA file is not a valid X.509 certificate", file.absolutePath))
                }
            }
        }
    }

    private fun MutableList<BuildInputIssue>.requireHtmlFiles(
        entryFile: String,
        htmlFiles: List<HtmlFile>,
        label: String = "HTML app"
    ) {
        if (htmlFiles.isEmpty()) {
            add(BuildInputIssue("htmlFiles", "$label has no files to embed"))
            return
        }

        val normalizedEntry = normalizeAssetPath(entryFile)
        if (normalizedEntry.isBlank()) {
            add(BuildInputIssue("htmlEntryFile", "HTML entry file is blank"))
        } else {
            val entryFound = htmlFiles.any { normalizeAssetPath(it.name).equals(normalizedEntry, ignoreCase = true) }
            if (!entryFound) {
                add(BuildInputIssue("htmlEntryFile", "HTML entry file is not included in htmlFiles", entryFile))
            }
        }

        htmlFiles.forEachIndexed { index, file ->
            // Skip internal change-tracking markers (Agent's ".changes/" dir) — they
            // are not real project files and would spuriously fail the empty-file check.
            val name = file.name
            if (name.startsWith(".changes/") || name.contains("/.changes/") ||
                name.endsWith(".__deleted__")) return@forEachIndexed
            requireReadableFile(
                key = "htmlFiles[$index]",
                label = "HTML project file '${name.ifBlank { "(unnamed)" }}'",
                path = file.path,
                requireNonEmpty = true
            )
        }
    }

    private fun MutableList<BuildInputIssue>.requireGalleryItems(items: List<GalleryItem>) {
        if (items.isEmpty()) {
            add(BuildInputIssue("galleryItems", "Gallery app has no media items to embed"))
            return
        }

        items.forEachIndexed { index, item ->
            requireReadableFile(
                key = "galleryItems[$index]",
                label = "Gallery item '${item.name.ifBlank { item.id }}'",
                path = item.path,
                requireNonEmpty = true
            )
        }
    }

    private fun MultiWebSite.hasLocalMaterial(): Boolean {
        return localFilePath.isNotBlank() || inlineHtml.isNotBlank()
    }

    private fun MultiWebSite.isUrlOnlySite(): Boolean {
        if (!hasLocalMaterial() && url.isNotBlank()) {
            return true
        }
        return when (type.uppercase()) {
            "URL", "" -> true
            "EXISTING" -> !hasLocalMaterial()
            "LOCAL", "INLINE_HTML" -> !hasLocalMaterial() && url.isNotBlank()
            else -> !hasLocalMaterial() && url.isNotBlank()
        }
    }

    private fun MultiWebSite.requiresLocalFile(): Boolean {
        if (isUrlOnlySite()) return false
        if (type.uppercase() == "INLINE_HTML" && inlineHtml.isNotBlank() && localFilePath.isBlank()) {
            return false
        }
        if (localFilePath.isNotBlank()) return true
        return when (type.uppercase()) {
            "LOCAL", "INLINE_HTML" -> true
            "EXISTING" -> false
            else -> false
        }
    }

    private fun MutableList<BuildInputIssue>.requireMultiWebSites(
        items: List<MultiWebSite>,
        projectDir: File?
    ) {
        val enabledSites = items.filter { it.enabled }
        if (enabledSites.isEmpty()) {
            add(BuildInputIssue("multiWebSites", "Multi-web app has no enabled sites"))
            return
        }

        var requiresLocalProject = false
        enabledSites.forEachIndexed { index, site ->
            val key = "multiWebSites[$index]"
            when {
                site.isUrlOnlySite() -> {
                    if (site.url.isBlank()) {
                        add(BuildInputIssue(key, "Multi-web URL site is missing its URL"))
                    }
                }
                site.type.uppercase() == "INLINE_HTML" &&
                    site.inlineHtml.isBlank() &&
                    site.localFilePath.isBlank() -> {
                    add(BuildInputIssue(key, "Multi-web inline site is missing HTML content"))
                }
                site.requiresLocalFile() && site.localFilePath.isBlank() -> {
                    add(BuildInputIssue(key, "Multi-web local site is missing its file path"))
                }
                site.localFilePath.isNotBlank() -> {
                    requiresLocalProject = true
                }
            }
        }

        if (!requiresLocalProject) return

        if (projectDir == null) {
            add(BuildInputIssue("multiWebProjectDir", "Multi-web local site directory was not resolved"))
            return
        }

        enabledSites.forEachIndexed { index, site ->
            if (site.isUrlOnlySite() || site.localFilePath.isBlank()) return@forEachIndexed
            val expectedFile = File(projectDir, site.localFilePath.trimStart('/'))
            val key = "multiWebSites[$index]"
            when {
                !expectedFile.exists() -> add(
                    BuildInputIssue(
                        key,
                        "Multi-web local site file does not exist",
                        expectedFile.absolutePath
                    )
                )
                !expectedFile.isFile -> add(
                    BuildInputIssue(
                        key,
                        "Multi-web local site path is not a file",
                        expectedFile.absolutePath
                    )
                )
                !expectedFile.canRead() -> add(
                    BuildInputIssue(
                        key,
                        "Multi-web local site file cannot be read",
                        expectedFile.absolutePath
                    )
                )
                expectedFile.length() == 0L -> add(
                    BuildInputIssue(
                        key,
                        "Multi-web local site file is empty",
                        expectedFile.absolutePath
                    )
                )
            }
        }
    }

    private fun MutableList<BuildInputIssue>.requireReadableFile(
        key: String,
        label: String,
        path: String?,
        requireNonEmpty: Boolean
    ) {
        val trimmedPath = path?.trim().orEmpty()
        if (trimmedPath.isBlank()) {
            add(BuildInputIssue(key, "$label path is blank"))
            return
        }

        val file = File(trimmedPath)
        when {
            !file.exists() -> add(BuildInputIssue(key, "$label does not exist", file.absolutePath))
            !file.isFile -> add(BuildInputIssue(key, "$label is not a file", file.absolutePath))
            !file.canRead() -> add(BuildInputIssue(key, "$label cannot be read", file.absolutePath))
            requireNonEmpty && file.length() == 0L -> add(BuildInputIssue(key, "$label is empty", file.absolutePath))
        }
    }

    private fun normalizeAssetPath(value: String): String {
        return value.trim().replace('\\', '/').trimStart('/')
    }
}
