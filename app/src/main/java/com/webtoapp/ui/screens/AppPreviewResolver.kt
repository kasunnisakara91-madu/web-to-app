package com.webtoapp.ui.screens

import android.content.Context
import android.net.Uri
import com.webtoapp.data.model.AppType
import com.webtoapp.data.model.WebApp
import java.io.File

internal data class AppPreviewSpec(
    val previewFilePath: String? = null,
    val captureUrl: String? = null,
)

internal fun resolveAppPreviewSpec(context: Context, app: WebApp): AppPreviewSpec {
    return when (app.appType) {
        AppType.WEB -> AppPreviewSpec(captureUrl = app.url.takeIf { it.startsWith("http") })
        AppType.GALLERY -> {
            val firstItem = app.galleryConfig?.getSortedItems()?.firstOrNull()
            val previewFile = existingFile(firstItem?.thumbnailPath) ?: existingFile(firstItem?.path)
            AppPreviewSpec(previewFilePath = previewFile?.absolutePath)
        }
        AppType.HTML,
        AppType.FRONTEND -> resolveHtmlPreviewSpec(context, app)
        AppType.MULTI_WEB -> resolveMultiWebPreviewSpec(app)
        else -> AppPreviewSpec()
    }
}

internal suspend fun captureAppThumbnail(
    context: Context,
    screenshotService: com.webtoapp.core.stats.WebsiteScreenshotService,
    app: WebApp,
    spec: AppPreviewSpec,
): String? {
    val fallbackUrl = spec.captureUrl
    if (fallbackUrl != null) {
        return screenshotService.captureScreenshot(app.id, fallbackUrl)
    }
    return null
}

private fun resolveHtmlPreviewSpec(context: Context, app: WebApp): AppPreviewSpec {
    val config = app.htmlConfig
    val entryFile = config?.getValidEntryFile() ?: "index.html"
    val storedProjectDir = config?.projectId
        ?.takeIf { it.isNotBlank() }
        ?.let { File(context.filesDir, "html_projects/$it") }
        ?.takeIf { it.exists() && it.isDirectory }
    val importedProjectDir = config?.projectDir
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() && it.isDirectory }
    val rootDir = storedProjectDir ?: importedProjectDir
    if (rootDir == null) {
        com.webtoapp.core.logging.AppLogger.w(
            "AppPreviewResolver",
            "HTML preview has no project dir: appId=${app.id}, projectId='${config?.projectId}', projectDir='${config?.projectDir}'"
        )
        return AppPreviewSpec()
    }

    // 入口选择,与 WebViewActivity 的加载逻辑保持一致:只要项目目录在,就一定
    // 产出一个可截图的 captureUrl,不能因为入口文件名对不上就退回空 spec
    // （那会让缩略图变成 “</>” 占位且没有刷新按钮——但应用其实能正常打开）。
    // 优先级:配置入口文件 → 目录里任意 .html/.htm → 兜底直接用配置入口名
    // （即便文件系统检查没命中,file:// 仍能让 WebView 尝试加载,失败也只是
    //  截到一张空白图,而不是连刷新入口都没有）。
    val entry = File(rootDir, entryFile).takeIf { it.exists() && it.isFile }
        ?: findStaticHtmlEntry(rootDir, listOf(""))
        ?: File(rootDir, entryFile)
    return AppPreviewSpec(captureUrl = entry.toFileUrl())
}

private fun findStaticHtmlEntry(projectDir: File, preferredDirs: List<String>): File? {
    preferredDirs.forEach { relativeDir ->
        val candidateDir = if (relativeDir.isBlank()) projectDir else File(projectDir, relativeDir)
        if (!candidateDir.exists() || !candidateDir.isDirectory) {
            return@forEach
        }
        val indexFile = File(candidateDir, "index.html")
        if (indexFile.exists() && indexFile.isFile) {
            return indexFile
        }
        val firstHtml = candidateDir.walkTopDown().firstOrNull {
            it.isFile &&
                it.name != "_preview_.html" &&
                (it.extension.equals("html", ignoreCase = true) || it.extension.equals("htm", ignoreCase = true))
        }
        if (firstHtml != null) {
            return firstHtml
        }
    }
    return null
}

private fun existingFile(path: String?): File? {
    if (path.isNullOrBlank()) return null
    val file = File(path)
    return file.takeIf { it.exists() && it.isFile }
}

private fun File.toFileUrl(): String = Uri.fromFile(this).toString()

private fun resolveMultiWebPreviewSpec(app: WebApp): AppPreviewSpec {

    val firstSite = app.multiWebConfig?.sites?.firstOrNull { it.enabled && it.url.isNotBlank() }
    val previewUrl = firstSite?.url?.takeIf { it.startsWith("http") }
    return AppPreviewSpec(captureUrl = previewUrl)
}
