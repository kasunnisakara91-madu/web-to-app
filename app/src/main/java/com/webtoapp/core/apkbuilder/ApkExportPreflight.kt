package com.webtoapp.core.apkbuilder

import android.content.Context
import com.webtoapp.core.i18n.Strings
import com.webtoapp.data.model.AppType
import com.webtoapp.data.model.WebApp
import java.io.File

data class ApkExportPreflightReport(
    val issues: List<ApkExportPreflightIssue>
) {
    val errors: List<ApkExportPreflightIssue> get() = issues.filter { it.severity == ApkExportPreflightSeverity.Error }
    val warnings: List<ApkExportPreflightIssue> get() = issues.filter { it.severity == ApkExportPreflightSeverity.Warning }
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val passed: Boolean get() = !hasErrors
}

data class ApkExportPreflightIssue(
    val severity: ApkExportPreflightSeverity,
    val key: String,
    val title: String,
    val message: String,
    val path: String? = null
) {
    fun summary(): String {
        val location = path?.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        return "$key: $title - $message$location"
    }
}

enum class ApkExportPreflightSeverity {
    Error,
    Warning
}

object ApkExportPreflight {

    fun check(context: Context, webApp: WebApp): ApkExportPreflightReport {
        val issues = mutableListOf<ApkExportPreflightIssue>()
        if (!webApp.appType.isSupported) {
            issues += ApkExportPreflightIssue(
                severity = ApkExportPreflightSeverity.Error,
                key = "appType",
                title = Strings.appTypeRemoved,
                message = Strings.appTypeRemoved
            )
            return ApkExportPreflightReport(issues)
        }
        val inputPreflight = BuildInputPreflight.check(webApp.toBuildInputPreflightRequest(context))

        inputPreflight.issues.forEach { issue ->
            issues += ApkExportPreflightIssue(
                severity = ApkExportPreflightSeverity.Error,
                key = issue.key,
                title = issue.title(),
                message = issue.message,
                path = issue.path
            )
        }

        issues.addGeneralWarnings(webApp)
        issues.addNetworkTrustWarnings(webApp)

        return ApkExportPreflightReport(issues)
    }

    fun WebApp.toBuildInputPreflightRequest(context: Context): BuildInputPreflightRequest {
        val appTypeName = appType.name
        return BuildInputPreflightRequest(
            appType = appTypeName,
            htmlEntryFile = htmlConfig?.getValidEntryFile() ?: "index.html",
            htmlFiles = when (appType) {
                AppType.HTML, AppType.FRONTEND -> htmlConfig?.files.orEmpty()
                else -> emptyList()
            },
            galleryItems = if (appType == AppType.GALLERY) galleryConfig?.items.orEmpty() else emptyList(),
            multiWebSites = if (appType == AppType.MULTI_WEB) multiWebConfig?.sites.orEmpty() else emptyList(),
            frontendProjectDir = if (appType == AppType.FRONTEND) {
                htmlConfig?.projectId?.takeIf { it.isNotBlank() }
                    ?.let { File(context.filesDir, "html_projects/$it") }
            } else null,
            multiWebProjectDir = if (appType == AppType.MULTI_WEB) {
                multiWebConfig?.projectId?.takeIf { it.isNotBlank() }
                    ?.let { File(context.filesDir, "html_projects/$it") }
            } else null,
            networkTrustConfig = apkExportConfig?.networkTrustConfig ?: com.webtoapp.data.model.NetworkTrustConfig()
        )
    }

    private fun MutableList<ApkExportPreflightIssue>.addGeneralWarnings(webApp: WebApp) {
        val packageName = webApp.apkExportConfig?.customPackageName.orEmpty()
        if (packageName.isBlank()) {
            add(
                ApkExportPreflightIssue(
                    severity = ApkExportPreflightSeverity.Warning,
                    key = "packageName",
                    title = Strings.preflightPackageAutoTitle,
                    message = Strings.preflightPackageAutoMessage
                )
            )
        }

        if (webApp.iconPath.isNullOrBlank()) {
            add(
                ApkExportPreflightIssue(
                    severity = ApkExportPreflightSeverity.Warning,
                    key = "icon",
                    title = Strings.preflightIconMissingTitle,
                    message = Strings.preflightIconMissingMessage
                )
            )
        }

        val runtimePermissions = webApp.apkExportConfig?.runtimePermissions
        if (runtimePermissions?.systemAlertWindow == true) {
            add(
                ApkExportPreflightIssue(
                    severity = ApkExportPreflightSeverity.Warning,
                    key = "permission.systemAlertWindow",
                    title = Strings.preflightOverlayPermissionTitle,
                    message = Strings.preflightOverlayPermissionMessage
                )
            )
        }
    }

    private fun MutableList<ApkExportPreflightIssue>.addNetworkTrustWarnings(webApp: WebApp) {
        val networkTrust = webApp.apkExportConfig?.networkTrustConfig ?: return
        if (!networkTrust.trustSystemCa && !networkTrust.trustUserCa && networkTrust.customCaCertificates.isEmpty()) {
            add(
                ApkExportPreflightIssue(
                    severity = ApkExportPreflightSeverity.Error,
                    key = "networkTrust",
                    title = Strings.preflightNoCaAnchorTitle,
                    message = Strings.preflightNoCaAnchorMessage
                )
            )
        }

        if (networkTrust.customCaCertificates.isNotEmpty()) {
            add(
                ApkExportPreflightIssue(
                    severity = ApkExportPreflightSeverity.Warning,
                    key = "networkTrust.customCa",
                    title = Strings.preflightTemplateCaLimitTitle,
                    message = Strings.preflightTemplateCaLimitMessage
                )
            )
        }

        if (networkTrust.cleartextTrafficPermitted) {
            add(
                ApkExportPreflightIssue(
                    severity = ApkExportPreflightSeverity.Warning,
                    key = "network.cleartext",
                    title = Strings.preflightCleartextTitle,
                    message = Strings.preflightCleartextMessage
                )
            )
        }
    }

    private fun BuildInputIssue.title(): String {
        return when {
            key.startsWith("customCa") -> Strings.preflightCustomCaUnavailable
            key.startsWith("htmlEntryFile") -> Strings.preflightEntryFileIssue
            key.startsWith("htmlFiles") -> Strings.preflightHtmlFileIssue
            key.startsWith("galleryItems") -> Strings.preflightGalleryIssue
            key.startsWith("multiWebSites") || key == "multiWebProjectDir" -> Strings.preflightRuntimeProjectIssue
            key.endsWith("ProjectDir") -> Strings.preflightRuntimeProjectIssue
            else -> Strings.preflightInputIssue
        }
    }
}
