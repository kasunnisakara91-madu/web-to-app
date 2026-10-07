package com.webtoapp.ui.webview

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Separate WebApp tasks are one recents card per document, not one per launch.
 * FLAG_ACTIVITY_MULTIPLE_TASK would ignore intoExisting and stack a new card
 * every time home preview or a desktop shortcut opens the same app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentTaskLaunchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `separate tasks open the document activity without MULTIPLE_TASK`() {
        val uri = Uri.parse("webtoapp://webapp/7")
        val intent = WebViewActivity.buildLaunchIntent(
            context = context,
            separateTasks = true,
            documentUri = uri
        ) {
            putExtra("app_id", 7L)
        }

        assertThat(intent.component?.className).endsWith("WebViewDocumentActivity")
        assertThat(intent.data).isEqualTo(uri)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_DOCUMENT).isNotEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK).isEqualTo(0)
    }

    @Test
    fun `two web apps keep different document uris`() {
        val first = WebViewActivity.buildLaunchIntent(
            context, separateTasks = true, documentUri = Uri.parse("webtoapp://webapp/1")
        ) { putExtra("app_id", 1L) }
        val second = WebViewActivity.buildLaunchIntent(
            context, separateTasks = true, documentUri = Uri.parse("webtoapp://webapp/2")
        ) { putExtra("app_id", 2L) }

        assertThat(first.data).isNotEqualTo(second.data)
        assertThat(first.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK).isEqualTo(0)
        assertThat(second.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK).isEqualTo(0)
    }

    @Test
    fun `shared task path does not request a document task`() {
        val intent = WebViewActivity.buildLaunchIntent(
            context = context,
            separateTasks = false,
            documentUri = Uri.parse("webtoapp://webapp/7")
        ) {
            putExtra("app_id", 7L)
        }

        assertThat(intent.component?.className).endsWith("WebViewActivity")
        assertThat(intent.data).isNull()
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_DOCUMENT).isEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK).isEqualTo(0)
    }

    @Test
    fun `document activity reuses an existing task for the same uri`() {
        val manifest = resolveManifest()
        val text = manifest.readText()
        val declaration = text.substringAfter("android:name=\".ui.webview.WebViewDocumentActivity\"")
            .substringBefore("</activity>")

        assertThat(declaration).contains("android:documentLaunchMode=\"intoExisting\"")
        assertThat(declaration).doesNotContain("android:documentLaunchMode=\"always\"")
    }

    private fun resolveManifest(): File {
        val candidates = listOf(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml"
        )
        for (candidate in candidates) {
            val direct = File(candidate)
            if (direct.exists()) return direct
            val fromModule = File(System.getProperty("user.dir"), candidate)
            if (fromModule.exists()) return fromModule
        }
        throw IllegalStateException("host manifest not found from ${System.getProperty("user.dir")}")
    }
}
