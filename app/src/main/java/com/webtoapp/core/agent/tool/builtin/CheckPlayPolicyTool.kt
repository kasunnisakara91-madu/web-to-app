package com.webtoapp.core.agent.tool.builtin

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.webtoapp.core.agent.tool.Tool
import com.webtoapp.core.agent.tool.ToolContext
import com.webtoapp.core.agent.tool.ToolResult
import com.webtoapp.core.playstore.PlayPolicyChecker

class CheckPlayPolicyTool : Tool {
    override val name = "CheckPlayPolicy"
    override val description = """
        Check Google Play policy compliance for an app. Returns blockers (will prevent
        Play upload), warnings, and info items. Use before exporting an AAB.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {
        integer("appId", "The app id to check.", required = true)
    }
    override fun isReadOnly() = true
    override fun activityDescription(args: JsonObject): String? =
        args.get("appId")?.asString?.let { "Checking Play policy for app $it" }
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val appId = args.get("appId")?.asLong ?: return ToolResult.error("CheckPlayPolicy: missing `appId`.")
        val app = ctx.appRepository.getWebApp(appId) ?: return ToolResult.error("CheckPlayPolicy: app $appId not found.")
        val report = PlayPolicyChecker.check(app)
        if (report.isClean) {
            return ToolResult.ok("App \"${app.name}\" has no policy issues. Ready for Play.")
        }
        val lines = report.violations.joinToString("\n") { v ->
            "- [${v.severity}] ${v.ruleId}: ${v.policyArea}"
        }
        return ToolResult.ok(buildString {
            appendLine("App \"${app.name}\": ${report.blockerCount} blocker(s), ${report.warningCount} warning(s), ${report.violations.size - report.blockerCount - report.warningCount} info")
            appendLine("Can publish: ${report.canPublish}")
            appendLine(lines)
        }.trimEnd())
    }
}
