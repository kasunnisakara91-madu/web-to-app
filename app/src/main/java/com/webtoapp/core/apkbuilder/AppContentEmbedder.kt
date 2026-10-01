package com.webtoapp.core.apkbuilder

import com.webtoapp.core.crypto.AssetEncryptor
import com.webtoapp.core.crypto.EncryptionConfig
import java.io.File
import java.util.zip.ZipOutputStream

interface AppContentEmbedder {

    fun embed(zipOut: ZipOutputStream, ctx: EmbedContext): EmbedResult
}

class EmbedContext(
    val config: ApkConfig,
    val logger: BuildLogger,
    val encryptor: AssetEncryptor?,
    val encryptionConfig: EncryptionConfig,

    val htmlFiles: List<com.webtoapp.data.model.HtmlFile>,
    val galleryItems: List<com.webtoapp.data.model.GalleryItem>,
    val projectDir: File?,
    val secondaryProjectDir: File?,

    val fnAddHtmlFiles: (ZipOutputStream, List<com.webtoapp.data.model.HtmlFile>, AssetEncryptor?, EncryptionConfig) -> Int,
    val fnAddGalleryItems: (ZipOutputStream, List<com.webtoapp.data.model.GalleryItem>, AssetEncryptor?, EncryptionConfig, String) -> Unit,
    val fnAddFrontendFiles: (ZipOutputStream, File, List<com.webtoapp.data.model.HtmlFile>) -> Unit,
    val multiWebSiteSourceDirs: Map<String, File> = emptyMap(),
    /** Multi-web GALLERY site id -> source host-path items (embedded under the site prefix). */
    val multiWebSiteGalleryItems: Map<String, List<com.webtoapp.data.model.GalleryItem>> = emptyMap()
)

data class EmbedResult(
    val success: Boolean,
    val itemCount: Int = 0,
    val message: String = ""
)

object AppContentEmbedderFactory {

    fun create(appType: String): AppContentEmbedder? {
        return when (appType) {
            "HTML" -> HtmlContentEmbedder()
            "GALLERY" -> GalleryContentEmbedder()
            "FRONTEND" -> FrontendContentEmbedder()
            "MULTI_WEB" -> MultiWebContentEmbedder()
            else -> null
        }
    }
}

class HtmlContentEmbedder : AppContentEmbedder {
    override fun embed(zipOut: ZipOutputStream, ctx: EmbedContext): EmbedResult {
        val dir = ctx.projectDir
        if (dir != null && dir.exists() && dir.isDirectory) {
            ctx.logger.section("Embed HTML Project Directory")
            ctx.logger.log("Embedding entire project directory: ${dir.absolutePath}")
            val (count, size) = RuntimeAssetEmbedder.embedProjectFiles(
                zipOut = zipOut,
                projectDir = dir,
                config = RuntimeAssetEmbedder.EmbedConfig(
                    runtimeName = "html",
                    assetPrefix = "assets/html",
                    excludeDirs = setOf("node_modules", ".git", ".cache", "__pycache__", ".next", ".nuxt"),
                    runtimeType = "html",
                    fileHook = if (ctx.encryptionConfig.enabled && ctx.encryptor != null) {
                        { zipOut, assetPath, file ->
                            val assetName = assetPath.removePrefix("assets/")
                            val encryptedData = ctx.encryptor!!.encrypt(file.readBytes(), assetName)
                            ZipUtils.writeEntryDeflated(zipOut, "${assetPath}.enc", encryptedData)
                            ctx.logger.log("File encrypted: ${assetPath}.enc (${encryptedData.size} bytes)")
                            true
                        }
                    } else null
                ),
                logger = ctx.logger
            )
            ctx.logger.logKeyValue("htmlProjectFilesEmbedded", count)
            ctx.logger.logKeyValue("htmlProjectTotalSize", "${size / 1024} KB")
            if (count > 0) return EmbedResult(true, count, "$count project files embedded from directory")
        }

        if (ctx.htmlFiles.isEmpty()) {
            ctx.logger.warn("HTML app but htmlFiles is empty! htmlConfig=${ctx.config.htmlEntryFile}")
            return EmbedResult(false, message = "No HTML files")
        }
        ctx.logger.section("Embed HTML Files")
        val count = ctx.fnAddHtmlFiles(zipOut, ctx.htmlFiles, ctx.encryptor, ctx.encryptionConfig)
        ctx.logger.logKeyValue("htmlFilesEmbeddedCount", count)
        if (count == 0) {
            ctx.logger.warn("HTML app failed to embed any files!")
        } else {

            val entryFile = ctx.config.htmlEntryFile
            val embeddedNames = ctx.htmlFiles.map { it.name }
            val entryFound = embeddedNames.any { it.equals(entryFile, ignoreCase = true) }
            if (!entryFound) {
                ctx.logger.warn("⚠️ Entry file '$entryFile' was NOT found in embedded file list!")
                ctx.logger.warn("   Embedded files: ${embeddedNames.joinToString(", ")}")
                ctx.logger.warn("   The app may show ERR_FILE_NOT_FOUND at runtime.")
                ctx.logger.warn("   ShellContentRouter will attempt auto-discovery as fallback.")
            } else {
                ctx.logger.log("✓ Entry file '$entryFile' confirmed in embedded files")
            }
        }
        return EmbedResult(count > 0, count, "$count HTML files embedded")
    }
}

class GalleryContentEmbedder : AppContentEmbedder {
    override fun embed(zipOut: ZipOutputStream, ctx: EmbedContext): EmbedResult {
        if (ctx.galleryItems.isEmpty()) {
            ctx.logger.warn("Gallery app but galleryItems is empty!")
            return EmbedResult(false, message = "No gallery items")
        }
        ctx.logger.section("Embed Gallery Items")
        ctx.fnAddGalleryItems(zipOut, ctx.galleryItems, ctx.encryptor, ctx.encryptionConfig, "gallery")
        ctx.logger.logKeyValue("galleryItemsEmbeddedCount", ctx.galleryItems.size)
        return EmbedResult(true, ctx.galleryItems.size, "${ctx.galleryItems.size} gallery items embedded")
    }
}

class FrontendContentEmbedder : AppContentEmbedder {
    override fun embed(zipOut: ZipOutputStream, ctx: EmbedContext): EmbedResult {
        val dir = ctx.projectDir
        if (dir != null && dir.exists()) {
            ctx.logger.section("Embed Frontend Project Files")
            ctx.fnAddFrontendFiles(zipOut, dir, ctx.htmlFiles)
            return EmbedResult(true, message = "Frontend files embedded")
        }

        if (ctx.htmlFiles.isNotEmpty()) {
            ctx.logger.section("Embed Frontend Files (from file list)")
            val count = ctx.fnAddHtmlFiles(zipOut, ctx.htmlFiles, ctx.encryptor, ctx.encryptionConfig)
            ctx.logger.logKeyValue("frontendFilesEmbeddedCount", count)
            return EmbedResult(count > 0, count, "$count frontend files embedded (fallback)")
        }
        return EmbedResult(false, message = "No frontend project directory or files")
    }
}

class MultiWebContentEmbedder : AppContentEmbedder {
    override fun embed(zipOut: ZipOutputStream, ctx: EmbedContext): EmbedResult {
        val sites = ctx.config.multiWeb.sites
        val embedded = mutableListOf<String>()
        sites.forEach { site ->
            val appType = site.appType.uppercase()
            if (appType != "HTML" && appType != "FRONTEND") return@forEach
            val dir = ctx.multiWebSiteSourceDirs[site.id] ?: ctx.secondaryProjectDir
            if (dir == null || !dir.exists()) return@forEach
            ctx.logger.log("Embed multi-web site ${site.id} (${site.name}) from ${dir.absolutePath}")
            RuntimeAssetEmbedder.embedProjectFiles(
                zipOut = zipOut,
                projectDir = dir,
                config = RuntimeAssetEmbedder.EmbedConfig(
                    runtimeName = "multiWebSite_${site.id}",
                    assetPrefix = "assets/multiweb_sites/${site.id}/${site.siteShellConfig?.siteAssetBase?.ifBlank { "html" } ?: "html"}",
                    excludeDirs = setOf("node_modules", ".git", ".cache", "__pycache__", ".next", ".nuxt"),
                    runtimeType = "frontend"
                ),
                logger = ctx.logger
            )
            embedded.add(site.id)
        }
        // Gallery sites: standalone gallery exports live at assets/gallery/,
        // which a multi-web APK never populates for sites. Embed each site's
        // media under its own prefix, matching rewriteMultiWebGallerySitePaths
        // (same order, same naming), otherwise every cell renders black.
        sites.forEach { site ->
            if (site.appType.uppercase() != "GALLERY") return@forEach
            val items = ctx.multiWebSiteGalleryItems[site.id].orEmpty()
            if (items.isEmpty()) {
                ctx.logger.warn("Multi-web gallery site ${site.id} (${site.name}) has no embeddable items")
                return@forEach
            }
            ctx.logger.section("Embed Multi-Web Gallery Site ${site.id}")
            ctx.fnAddGalleryItems(
                zipOut, items, ctx.encryptor, ctx.encryptionConfig,
                multiWebSiteGalleryAssetPrefix(site.id)
            )
            embedded.add(site.id)
        }
        if (embedded.isEmpty()) {
            val dir = ctx.secondaryProjectDir
            if (dir != null && dir.exists()) {
                ctx.logger.section("Embed Multi-Web Legacy Local Site Files")
                RuntimeAssetEmbedder.embedProjectFiles(
                    zipOut = zipOut,
                    projectDir = dir,
                    config = RuntimeAssetEmbedder.multiWebConfig(),
                    logger = ctx.logger
                )
            }
        }
        return EmbedResult(true, message = "Multi-web local site files embedded (${embedded.size} sites)")
    }
}
