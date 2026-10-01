package com.webtoapp.core.agent.tool.builtin

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.webtoapp.core.agent.tool.Tool
import com.webtoapp.core.agent.tool.ToolContext
import com.webtoapp.core.agent.tool.ToolResult
import com.webtoapp.core.adblock.AdBlocker
import org.koin.java.KoinJavaComponent

class GetAdBlockStatusTool : Tool {
    override val name = "GetAdBlockStatus"
    override val description = """
        Get the current ad-blocker status: total rules, hosts rules, enabled/disabled sources,
        user-imported custom sources with their display names, and whether ad-blocking is
        globally enabled.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {}
    override fun isReadOnly() = true
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val blocker = KoinJavaComponent.get<AdBlocker>(AdBlocker::class.java, null, null)
        val enabled = blocker.isEnabled()
        val totalRules = blocker.getRuleCount()
        val hostsRules = blocker.getHostsFileRuleCount()
        val enabledSources = blocker.getEnabledHostsSources()
        val disabledSources = blocker.getDisabledHostsSources()
        val downloaded = blocker.getAllDownloadedSourceKeys()
        val customSources = blocker.getCustomHostsSources()
        return ToolResult.ok(buildString {
            appendLine("Ad-block enabled: $enabled")
            appendLine("Total rules: $totalRules (hosts: $hostsRules)")
            appendLine("Downloaded sources: ${downloaded.size}")
            if (enabledSources.isNotEmpty()) appendLine("Enabled: ${enabledSources.joinToString(", ")}")
            if (disabledSources.isNotEmpty()) appendLine("Disabled: ${disabledSources.joinToString(", ")}")
            if (customSources.isNotEmpty()) {
                appendLine("Custom (user-imported) sources:")
                customSources.forEach { source ->
                    val state = when {
                        enabledSources.contains(source.url) -> "enabled"
                        disabledSources.contains(source.url) -> "disabled"
                        else -> "unknown"
                    }
                    appendLine("- ${source.name} -> ${source.url} ($state)")
                }
            }
        }.trimEnd())
    }
}

class ManageHostsRulesTool : Tool {
    override val name = "ManageHostsRules"
    override val description = """
        Manage hosts-based ad-block rule sources. Actions:
        - import_url: download and import a hosts list from a URL. Pass displayName to give
          the source a friendly name (shown in the ad-block UI and per-app selector).
        - toggle: enable or disable a downloaded source.
        - remove: remove a downloaded source.
        - clear: remove all hosts sources.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {
        enum("action", listOf("import_url", "toggle", "remove", "clear"), "The action to perform.", required = true)
        string("url", "URL for import_url action.")
        string("displayName", "For import_url: optional friendly name for the imported source.")
        string("sourceKey", "Source key for toggle/remove (use GetAdBlockStatus to find keys).")
        boolean("enabled", "For toggle: true to enable, false to disable.")
    }
    override fun isReadOnly() = false
    override fun activityDescription(args: JsonObject): String? =
        "Managing hosts rules: ${args.get("action")?.asString}"
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val blocker = KoinJavaComponent.get<AdBlocker>(AdBlocker::class.java, null, null)
        val action = args.get("action")?.asString ?: return ToolResult.error("ManageHostsRules: missing `action`.")
        return when (action) {
            "import_url" -> {
                val url = args.get("url")?.asString ?: return ToolResult.error("ManageHostsRules: missing `url` for import_url.")
                val displayName = args.get("displayName")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                val result = blocker.importHostsFromUrl(url, ctx.androidContext, displayName)
                if (result.isSuccess) ToolResult.ok("Imported ${result.getOrNull()} rules from $url.")
                else ToolResult.error("Import failed: ${result.exceptionOrNull()?.message}")
            }
            "toggle" -> {
                val key = args.get("sourceKey")?.asString ?: return ToolResult.error("ManageHostsRules: missing `sourceKey`.")
                val enabled = args.get("enabled")?.asBoolean ?: true
                val result = blocker.setHostsSourceEnabled(ctx.androidContext, key, enabled)
                if (result.isSuccess) ToolResult.ok("Source $key ${if (enabled) "enabled" else "disabled"}.")
                else ToolResult.error("Toggle failed: ${result.exceptionOrNull()?.message}")
            }
            "remove" -> {
                val key = args.get("sourceKey")?.asString ?: return ToolResult.error("ManageHostsRules: missing `sourceKey`.")
                val result = blocker.removeHostsSource(ctx.androidContext, key)
                if (result.isSuccess) ToolResult.ok("Removed source $key.")
                else ToolResult.error("Remove failed: ${result.exceptionOrNull()?.message}")
            }
            "clear" -> {
                blocker.clearHostsFileRules()
                ToolResult.ok("Cleared all hosts rules.")
            }
            else -> ToolResult.error("ManageHostsRules: unknown action `$action`.")
        }
    }
}
