package com.webtoapp.core.apkbuilder

import com.webtoapp.core.logging.AppLogger
import com.webtoapp.util.TextFileClassifier
import java.io.File
import java.util.zip.ZipOutputStream

object RuntimeAssetEmbedder {

    data class EmbedConfig(
        val runtimeName: String,
        val assetPrefix: String,
        val excludeDirs: Set<String>,
        val runtimeType: String? = null,

        val fileHook: ((zipOut: ZipOutputStream, assetPath: String, file: File) -> Boolean)? = null
    )

    fun embedProjectFiles(
        zipOut: ZipOutputStream,
        projectDir: File,
        config: EmbedConfig,
        logger: BuildLogger
    ): Pair<Int, Long> {
        AppLogger.d("RuntimeAssetEmbedder", "Embedding ${config.runtimeName} files from: ${projectDir.absolutePath}")

        var fileCount = 0
        var totalSize = 0L

        fun addDirRecursive(dir: File, basePath: String) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name in config.excludeDirs) {
                    return@forEach
                }
                val relativePath = "$basePath/${file.name}"
                if (file.isDirectory) {
                    addDirRecursive(file, relativePath)
                } else {
                    try {
                        val assetPath = "${config.assetPrefix}$relativePath"

                        val handled = config.fileHook?.invoke(zipOut, assetPath, file) ?: false
                        if (!handled) {

                            if (TextFileClassifier.isTextFile(file.name, config.runtimeType)) {
                                ZipUtils.writeEntryDeflated(zipOut, assetPath, file.readBytes())
                            } else {
                                ZipUtils.writeEntryStoredSimple(zipOut, assetPath, file.readBytes())
                            }
                        }
                        fileCount++
                        totalSize += file.length()
                    } catch (e: Exception) {
                        AppLogger.w("RuntimeAssetEmbedder",
                            "Failed to embed ${config.runtimeName} file: ${file.absolutePath}", e)
                    }
                }
            }
        }

        addDirRecursive(projectDir, "")
        logger.logKeyValue("${config.runtimeName}FilesEmbedded", fileCount)
        logger.logKeyValue("${config.runtimeName}TotalSize", "${totalSize / 1024} KB")

        return fileCount to totalSize
    }





    fun frontendConfig(): EmbedConfig = EmbedConfig(
        runtimeName = "frontend",
        assetPrefix = "assets/frontend_app",
        excludeDirs = setOf("node_modules", ".git", ".cache", "__pycache__", ".next", ".nuxt"),
        runtimeType = "frontend"
    )

    fun multiWebConfig(): EmbedConfig = EmbedConfig(
        runtimeName = "multiWeb",
        assetPrefix = "assets/html_projects",
        excludeDirs = setOf("node_modules", ".git", ".cache", "__pycache__", ".next", ".nuxt"),
        runtimeType = "frontend"
    )

}
