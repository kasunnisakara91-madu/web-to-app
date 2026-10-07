package com.webtoapp.ui.webview

import android.content.Intent
import android.os.Bundle
import java.util.Collections
import java.util.WeakHashMap

/**
 * One recents task per document URI. Home preview and shortcuts use it when
 * the About-screen separate-tasks switch is on. The preview tool always uses
 * it, so an external agent has a page the user can watch. [WebViewActivity]
 * stays singleTask so the default path still reuses one preview.
 *
 * Shortcuts pinned before the launch flags dropped MULTIPLE_TASK still ask
 * for a new task. [duplicateDocumentBringIntent] sends that launch back to the
 * task that already shows the same document.
 */
class WebViewDocumentActivity : WebViewActivity() {

    companion object {
        private val live = Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<WebViewDocumentActivity, Boolean>())
        )

        fun finishAllDocumentTasks() {
            val snapshot = synchronized(live) { live.toList() }
            snapshot.forEach { activity ->
                if (!activity.isFinishing) {
                    activity.finishAndRemoveTask()
                }
            }
        }
    }

    override fun duplicateDocumentBringIntent(): Intent? {
        val key = PreviewSessions.sessionKey(intent) ?: return null
        val existing = synchronized(live) {
            live.firstOrNull { other ->
                other !== this && !other.isFinishing && !other.isDestroyed &&
                    PreviewSessions.sessionKey(other.intent) == key
            }
        } ?: return null
        return Intent(intent).apply {
            component = existing.componentName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        live.add(this)
        PreviewSessions.onActivityReady(this)
    }

    override fun onDestroy() {
        PreviewSessions.onActivityGone(this)
        live.remove(this)
        super.onDestroy()
    }
}
