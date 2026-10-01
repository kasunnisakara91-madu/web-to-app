package com.webtoapp.core.apkbuilder

import com.google.common.truth.Truth.assertThat
import com.webtoapp.core.shell.GalleryShellItem
import com.webtoapp.data.model.AppType
import com.webtoapp.data.model.GalleryConfig
import com.webtoapp.data.model.GalleryItem
import com.webtoapp.data.model.GalleryItemType
import com.webtoapp.data.model.WebApp
import java.io.File
import java.nio.file.Files
import org.junit.Test

class MultiWebGallerySiteTest {

    private fun shellItem(
        assetPath: String,
        type: String = "IMAGE",
        thumbnailPath: String? = null
    ) = GalleryShellItem(
        id = assetPath,
        assetPath = assetPath,
        type = type,
        name = assetPath,
        thumbnailPath = thumbnailPath
    )

    @Test
    fun `gallery prefix is namespaced per site`() {
        assertThat(multiWebSiteGalleryAssetPrefix("abc"))
            .isEqualTo("multiweb_sites/abc/gallery")
    }

    @Test
    fun `export rewrite points items at the site prefix`() {
        val items = listOf(
            shellItem("gallery/item_0.png", "IMAGE", "gallery/thumb_0.jpg"),
            shellItem("gallery/item_1.png", "VIDEO", null)
        )
        val rewritten = rewriteMultiWebGallerySitePaths(items, "s1")
        assertThat(rewritten[0].assetPath).isEqualTo("multiweb_sites/s1/gallery/item_0.png")
        assertThat(rewritten[0].thumbnailPath).isEqualTo("multiweb_sites/s1/gallery/thumb_0.jpg")
        assertThat(rewritten[1].assetPath).isEqualTo("multiweb_sites/s1/gallery/item_1.mp4")
        assertThat(rewritten[1].thumbnailPath).isNull()
        // Untouched metadata survives the rewrite.
        assertThat(rewritten[0].id).isEqualTo("gallery/item_0.png")
        assertThat(rewritten[1].type).isEqualTo("VIDEO")
    }

    @Test
    fun `export rewrite of empty list stays empty`() {
        assertThat(rewriteMultiWebGallerySitePaths(emptyList(), "s1")).isEmpty()
    }

    @Test
    fun `preview items carry absolute host paths`() {
        val dir = Files.createTempDirectory("wta-mwgal").toFile()
        try {
            val photo = File(dir, "a.jpg").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
            val app = WebApp(
                id = 5,
                name = "g",
                url = "",
                appType = AppType.GALLERY,
                galleryConfig = GalleryConfig(
                    items = listOf(
                        GalleryItem(path = photo.absolutePath, type = GalleryItemType.IMAGE, name = "a")
                    )
                )
            )
            val items = previewGallerySiteItems(app)
            assertThat(items).hasSize(1)
            assertThat(items[0].assetPath).isEqualTo(photo.absolutePath)
            assertThat(items[0].type).isEqualTo("IMAGE")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `preview items of non-gallery source are empty`() {
        val app = WebApp(id = 5, name = "w", url = "https://example.com", appType = AppType.WEB)
        assertThat(previewGallerySiteItems(app)).isEmpty()
    }


}
