package com.webtoapp.core.app

import android.content.Context
import com.webtoapp.data.model.AppType
import com.webtoapp.data.model.WebApp
import com.webtoapp.core.logging.AppLogger
import java.io.File

/**
 * Deletes the on-disk project directory owned by a [WebApp] when the app is removed.
 *
 * Project-backed app types store their source files under `filesDir/<root>/<projectId>`
 * (e.g. `html_projects/<id>`, `frontend_builds/<id>`). [MainViewModel.deleteApp] previously
 * only dropped the database row, leaving these directories as orphans that accumulated
 * storage.
 *
 * Exported artifacts (built_apks/, built_aabs/) are intentionally NOT touched — those are the
 * user's deliverables and independent of the source project.
 *
 * Safe by design: it only deletes directories it resolves through the stored project id /
 * absolute projectDir, and only when they actually live under the app's private filesDir.
 * It never follows an arbitrary absolute path outside the sandbox, so a corrupted projectDir
 * field cannot cause data loss elsewhere.
 */
object ProjectDirCleaner {

    private const val TAG = "ProjectDirCleaner"

    /** Returns the list of directories that were deleted (empty if nothing applied). */
    fun deleteForApp(context: Context, app: WebApp): List<File> {
        val appContext = context.applicationContext
        val sandboxRoot = appContext.filesDir.canonicalFile
        val deleted = mutableListOf<File>()

        fun deleteIfSandboxed(dir: File) {
            try {
                if (!dir.exists()) return
                val canonical = dir.canonicalFile
                // Guard: only delete inside our own filesDir. A tampered/absolute projectDir must
                // never let us wipe an arbitrary location.
                if (!canonical.path.startsWith(sandboxRoot.path)) {
                    AppLogger.w(TAG, "Refusing to delete dir outside filesDir: $canonical")
                    return
                }
                if (dir.deleteRecursively()) {
                    deleted += canonical
                    AppLogger.i(TAG, "Deleted project dir: $canonical")
                } else {
                    AppLogger.w(TAG, "Failed to delete project dir: $canonical")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error deleting project dir $dir", e)
            }
        }

        when (app.appType) {
            AppType.FRONTEND -> {
                // Frontend projects live under frontend_builds/<projectId>.
                app.htmlConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(File(appContext.filesDir, "frontend_builds/$pid"))
                }
            }
            AppType.HTML, AppType.MULTI_WEB -> {
                // HTML projects: prefer the stored absolute projectDir (imported HTML), fall back
                // to html_projects/<projectId>. MULTI_WEB reuses the html_projects storage.
                val cfg = app.htmlConfig
                val pid = cfg?.projectId?.takeIf { it.isNotBlank() }
                val importedDir = cfg?.projectDir?.takeIf { it.isNotBlank() }?.let(::File)
                if (importedDir != null) {
                    deleteIfSandboxed(importedDir)
                } else if (pid != null) {
                    // A MULTI_WEB app built purely from EXISTING sites borrows the source HTML
                    // app's projectId (MainViewModel.saveMultiWebApp). That directory is still
                    // owned by the source app — deleting it would wipe another live app's files.
                    // Only delete when the multi-web app owns the id (no site points back at it).
                    val borrowedFromSite = app.multiWebConfig?.sites?.any {
                        it.type == "EXISTING" && it.sourceProjectId.isNotBlank() && it.sourceProjectId == pid
                    } == true
                    if (borrowedFromSite) {
                        AppLogger.i(TAG, "Skip html_projects/$pid: MULTI_WEB app borrows the source app's project id")
                    } else {
                        deleteIfSandboxed(File(appContext.filesDir, "html_projects/$pid"))
                    }
                }
            }
            // Removed server-runtime types no longer produce projects, but old installs may
            // still carry their on-disk project dirs — deleting the app must not orphan them.
            AppType.WORDPRESS -> {
                app.wordpressConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(File(appContext.filesDir, "wordpress_projects/$pid"))
                }
            }
            AppType.NODEJS_APP -> {
                app.nodejsConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(File(appContext.filesDir, "nodejs_projects/$pid"))
                }
            }
            AppType.PHP_APP -> {
                app.phpAppConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(File(appContext.filesDir, "php_projects/$pid"))
                }
            }
            AppType.PYTHON_APP -> {
                app.pythonAppConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(File(appContext.filesDir, "python_projects/$pid"))
                }
            }
            AppType.GO_APP -> {
                app.goAppConfig?.projectId?.takeIf { it.isNotBlank() }?.let { pid ->
                    deleteIfSandboxed(File(appContext.filesDir, "go_projects/$pid"))
                }
            }
            // WEB / IMAGE / VIDEO / GALLERY have no source project directory on disk.
            else -> { }
        }

        return deleted
    }
}
