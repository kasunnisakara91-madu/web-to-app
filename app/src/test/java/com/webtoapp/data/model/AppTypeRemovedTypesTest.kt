package com.webtoapp.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards [AppType.isSupported] — the single source of truth for "removed app type". Removed
 * types keep decoding so old projects and backups don't crash, but they may not be previewed,
 * edited or exported.
 */
class AppTypeRemovedTypesTest {

    @Test
    fun `removed types are exactly the dropped server runtimes and media apps`() {
        assertThat(AppType.REMOVED_TYPES).containsExactly(
            AppType.IMAGE,
            AppType.VIDEO,
            AppType.WORDPRESS,
            AppType.NODEJS_APP,
            AppType.PHP_APP,
            AppType.PYTHON_APP,
            AppType.GO_APP
        )
    }

    @Test
    fun `supported set is the six retained app types`() {
        val supported = AppType.entries.filter { it.isSupported }
        assertThat(supported).containsExactly(
            AppType.WEB,
            AppType.HTML,
            AppType.GALLERY,
            AppType.FRONTEND,
            AppType.MULTI_WEB
        )
    }

    @Test
    fun `isSupported matches removed set for every app type`() {
        AppType.entries.forEach { type ->
            assertThat(type.isSupported).isEqualTo(type !in AppType.REMOVED_TYPES)
        }
    }

    @Test
    fun `fromPersistedName resolves names case-insensitively`() {
        assertThat(AppType.fromPersistedName("html")).isEqualTo(AppType.HTML)
        assertThat(AppType.fromPersistedName("MULTI_WEB")).isEqualTo(AppType.MULTI_WEB)
        assertThat(AppType.fromPersistedName("php_app")).isEqualTo(AppType.PHP_APP)
        assertThat(AppType.fromPersistedName("nonsense")).isNull()
        assertThat(AppType.fromPersistedName(null)).isNull()
    }
}
