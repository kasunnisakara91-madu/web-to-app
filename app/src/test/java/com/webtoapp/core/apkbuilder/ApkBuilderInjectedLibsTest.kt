package com.webtoapp.core.apkbuilder

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression guard for GeckoView native-lib replacement: the template APK ships gecko libs AND
 * the injection step writes them per selected ABI. modifyApk skips the template copies using
 * [ApkBuilder.geckoRuntimeEntryNames], so this locks the exact entry names that must be skipped
 * to avoid emitting a duplicate zip entry.
 */
class ApkBuilderInjectedLibsTest {

    @Test
    fun `gecko runtime entries replace matching template entries for every selected abi`() {
        val runtimeEntries = ApkBuilder.geckoRuntimeEntryNames(
            nativeLibNamesByAbi = mapOf(
                "arm64-v8a" to listOf("libcrashhelper.so", "libxul.so"),
                "x86_64" to listOf("libcrashhelper.so")
            ),
            abiFilters = listOf("arm64-v8a", "x86_64")
        )

        assertThat(runtimeEntries).containsExactly(
            "lib/arm64-v8a/libcrashhelper.so",
            "lib/arm64-v8a/libxul.so",
            "lib/x86_64/libcrashhelper.so",
            "assets/omni.ja"
        )
    }
}
