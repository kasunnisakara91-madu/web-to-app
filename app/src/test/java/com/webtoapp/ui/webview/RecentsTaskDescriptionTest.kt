package com.webtoapp.ui.webview

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentsTaskDescriptionTest {

    @Test
    fun `description carries the label and icon`() {
        val icon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val description = recentsTaskDescription("Example", icon)

        assertThat(description.label).isEqualTo("Example")
        assertThat(description.icon).isNotNull()
        assertThat(description.icon!!.width).isEqualTo(32)
        assertThat(description.icon!!.height).isEqualTo(32)
        // The platform copies the bitmap. Leave the caller's instance alone.
        assertThat(icon.isRecycled).isFalse()
    }

    @Test
    fun `missing icon still sets the label`() {
        val description = recentsTaskDescription("Example", null)

        assertThat(description.label).isEqualTo("Example")
        assertThat(description.icon).isNull()
    }

    @Test
    fun `oversized icon is scaled to the recents cap`() {
        val icon = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val description = recentsTaskDescription("Example", icon)

        assertThat(description.icon).isNotNull()
        assertThat(description.icon!!.width).isEqualTo(144)
        assertThat(description.icon!!.height).isEqualTo(72)
        assertThat(icon.isRecycled).isTrue()
        assertThat(description.icon!!.isRecycled).isFalse()
    }
}
