package com.webtoapp.ui.shell

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webtoapp.core.logging.AppLogger
import com.webtoapp.core.port.PortConflictException
import com.webtoapp.core.port.PortManager
import com.webtoapp.core.shell.ShellConfig
import com.webtoapp.core.i18n.Strings
import com.webtoapp.core.webview.WebViewCallbacks
import com.webtoapp.data.model.WebViewConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HtmlFrontendShellMode(
    config: ShellConfig,
    webViewRecreationKey: Int,
    webViewConfig: WebViewConfig,
    webViewCallbacks: WebViewCallbacks,
    webViewManager: com.webtoapp.core.webview.WebViewManager,
    onWebViewCreated: (WebView) -> Unit,
    onBrowserSurfaceCreated: (com.webtoapp.core.engine.BrowserSurface) -> Unit = {},
    onWebViewRefUpdated: (WebView) -> Unit,
    swipeRefreshEnabled: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf("extracting") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var errorThrowable by remember { mutableStateOf<Throwable?>(null) }
    var targetUrl by remember { mutableStateOf<String?>(null) }
    val portOwner = remember(config.packageName, config.siteId) {
        if (config.siteId.isNotBlank()) "${config.packageName}_${config.siteId}" else config.packageName
    }
    val preferredHttpPort = remember(portOwner, config.htmlConfig.port) {
        if (config.htmlConfig.port > 0) {
            config.htmlConfig.port
        } else {
            com.webtoapp.core.webview.LocalHttpServer.stablePortForPackageName(portOwner)
        }
    }
    val portConflictPolicy = remember(config.htmlConfig.portConflictMode) {
        PortManager.ConflictPolicy.fromName(config.htmlConfig.portConflictMode)
    }
    val httpServer = remember(preferredHttpPort) {
        com.webtoapp.core.webview.LocalHttpServer(context, preferredHttpPort)
    }

    DisposableEffect(httpServer) {
        onDispose { httpServer.stop() }
    }

    val effectiveEntryFile = config.htmlConfig.getValidEntryFile()

    LaunchedEffect(
        config.versionCode,
        effectiveEntryFile,
        config.webViewConfig.enableCrossOriginIsolation,
        preferredHttpPort,
        portConflictPolicy
    ) {
        withContext(Dispatchers.IO) {
            try {
                val siteDir = File(context.filesDir, config.siteDirName.ifBlank { "html_shell_site" })
                val marker = File(siteDir, ".html_extracted")
                val configuredEntryFile = config.htmlConfig.getValidEntryFile()
                val extractionToken = buildExtractionToken(
                    context = context,
                    scope = config.siteId.ifBlank { "html" },
                    configVersionCode = config.versionCode,
                    extra = "${config.appType}|$configuredEntryFile|coi=${config.webViewConfig.enableCrossOriginIsolation}"
                )

                val assetBase = config.siteAssetBase.ifBlank { "html" }
                // Multi-web per-site projects are embedded under assets/multiweb_sites/<siteId>/<assetBase>
                // (AppContentEmbedder.MultiWebContentEmbedder); plain HTML/FRONTEND shells embed at
                // the top-level assets/<assetBase>.
                val embeddedAssetRoot = if (config.siteId.isNotBlank()) "multiweb_sites/${config.siteId}/$assetBase" else assetBase
                val hasBundledAssets = try { context.assets.list(embeddedAssetRoot)?.isNotEmpty() == true } catch (_: Exception) { false }
                if (hasBundledAssets && shouldReextractAssets(marker, extractionToken)) {
                    AppLogger.i("HtmlShell", "Extracting HTML assets to ${siteDir.absolutePath}")
                    siteDir.deleteRecursively()
                    extractAssetsRecursive(context, embeddedAssetRoot, siteDir)
                    writeExtractionMarker(marker, extractionToken)
                }

                phase = "starting"
                val resolvedEntry = resolveExtractedHtmlEntry(siteDir, effectiveEntryFile)

                val requiresHttpServer = !config.htmlUsesFileScheme

                if (requiresHttpServer) {

                    val shouldEnableIsolation = config.webViewConfig.enableCrossOriginIsolation ||
                        com.webtoapp.core.webview.LocalHttpServer.shouldEnableCrossOriginIsolation(siteDir)
                    val baseUrl = httpServer.start(
                        rootDir = siteDir,
                        enableCrossOriginIsolation = shouldEnableIsolation,
                        owner = portOwner,
                        conflictPolicy = portConflictPolicy,
                        preferredPort = preferredHttpPort
                    )
                    targetUrl = buildLocalHttpTargetUrl(baseUrl, resolvedEntry)
                    AppLogger.i(
                        "HtmlShell",
                        "HTML Shell ready (HTTP server): url=$targetUrl, entry=$resolvedEntry, port=${httpServer.actualPort}, crossOriginIsolation=$shouldEnableIsolation"
                    )
                } else {

                    httpServer.stop()
                    val normalizedEntry = resolvedEntry.removePrefix("/").ifBlank { "index.html" }
                    val entryFileObj = File(siteDir, normalizedEntry)
                    targetUrl = android.net.Uri.fromFile(entryFileObj).toString()
                    AppLogger.i(
                        "HtmlShell",
                        "HTML Shell ready (file:// compatibility mode): url=$targetUrl, entry=$resolvedEntry"
                    )
                }
                phase = "ready"
            } catch (e: Exception) {
                AppLogger.e("HtmlShell", "HTML Shell Launch failed", e)
                phase = "error"
                errorMsg = when (e) {
                    is PortConflictException -> "${Strings.portConflictTitle}: ${e.port}"
                    else -> e.message ?: Strings.serverStartFailed
                }
                errorThrowable = e
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            "ready" -> {
                val url = targetUrl ?: return@Box
                ShellLocalFileWebView(
                    config = config,
                    webViewRecreationKey = webViewRecreationKey,
                    webViewConfig = webViewConfig,
                    webViewCallbacks = webViewCallbacks,
                    webViewManager = webViewManager,
                    targetUrl = url,
                    enableJavaScript = config.htmlConfig.enableJavaScript,
                    enableLocalStorage = config.htmlConfig.enableLocalStorage,
                    swipeRefreshEnabled = swipeRefreshEnabled,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onWebViewCreated = onWebViewCreated,
                    onWebViewRefUpdated = onWebViewRefUpdated,
                    onBrowserSurfaceCreated = onBrowserSurfaceCreated
                )
            }

            "extracting", "starting" -> {

                var showLoadingUi by remember { mutableStateOf(false) }
                LaunchedEffect(phase) {
                    delay(600)
                    showLoadingUi = true
                }
                if (showLoadingUi) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

            }

            "error" -> {
                ShellErrorScreen(
                    config = config,
                    mode = "HTML",
                    message = errorMsg ?: Strings.serverStartFailed,
                    throwable = errorThrowable
                )
            }
        }
    }
}

private fun resolveExtractedHtmlEntry(siteDir: File, configuredEntry: String): String {
    val normalizedConfiguredEntry = configuredEntry.removePrefix("/")
    if (normalizedConfiguredEntry.isNotBlank() && File(siteDir, normalizedConfiguredEntry).exists()) {
        return normalizedConfiguredEntry
    }

    val preferredFallbacks = listOf("index.html", "index.htm", "main.html")
    preferredFallbacks.firstOrNull { File(siteDir, it).exists() }?.let { return it }

    return siteDir.walkTopDown()
        .filter { it.isFile }
        .firstOrNull {
            it.extension.equals("html", ignoreCase = true) ||
                it.extension.equals("htm", ignoreCase = true)
        }
        ?.relativeTo(siteDir)
        ?.invariantSeparatorsPath
        ?: normalizedConfiguredEntry.ifBlank { "index.html" }
}


private fun buildLocalHttpTargetUrl(baseUrl: String, relativePath: String): String {
    val normalizedPath = relativePath.removePrefix("/").ifBlank { "index.html" }
    return "$baseUrl/${Uri.encode(normalizedPath, "/")}"
}
