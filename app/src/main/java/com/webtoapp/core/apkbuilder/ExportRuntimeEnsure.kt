package com.webtoapp.core.apkbuilder

import android.content.Context

object ExportRuntimeEnsure {

    fun needsEnsure(
        context: Context,
        needsCronet: Boolean = false
    ): Boolean {
        return needsCronet && !com.webtoapp.core.webview.CronetDependencyManager.isCronetReady(context)
    }

    suspend fun ensure(
        context: Context,
        needsCronet: Boolean = false
    ): Boolean {
        if (needsCronet && !com.webtoapp.core.webview.CronetDependencyManager.isCronetReady(context)) {
            if (!com.webtoapp.core.webview.CronetDependencyManager.downloadCronetRuntime(context)) {
                return false
            }
        }
        return true
    }
}
