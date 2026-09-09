package dev.fvojta.claudeusage.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import dev.fvojta.claudeusage.model.CostWindow
import dev.fvojta.claudeusage.model.QuotaTier
import java.io.File

@State(
    name = "ClaudeUsageMonitorSettings",
    storages = [Storage("claude-usage-monitor.xml")],
)
class ClaudeUsageSettings : SimplePersistentStateComponent<ClaudeUsageSettings.State>(State()) {

    class State : BaseState() {
        /** Which subscription quota the status bar text shows. */
        var quotaTier by enum<QuotaTier>(QuotaTier.FIVE_HOUR)

        /** Also show an estimated cost next to the quota. */
        var showCost by property(true)

        /** Which local window the status-bar cost refers to. */
        var costWindow by enum<CostWindow>(CostWindow.TODAY)

        /** Minutes between background refreshes (1..60). */
        var refreshIntervalMinutes by property(1)

        var yellowThreshold by property(70)
        var redThreshold by property(90)

        /** Notify once when a quota crosses the yellow / red threshold. */
        var notifyOnThreshold by property(true)

        /** macOS only: read the token from the login Keychain first. */
        var useMacKeychain by property(true)

        /** Empty = default `~/.claude/.credentials.json`. */
        var credentialsFilePath by string("")
    }

    fun credentialsPath(): File {
        val configured = state.credentialsFilePath.orEmpty()
        if (configured.isNotBlank()) {
            val expanded = if (configured.startsWith("~"))
                configured.replaceFirst("~", System.getProperty("user.home")) else configured
            return File(expanded)
        }
        return File(System.getProperty("user.home"), ".claude/.credentials.json")
    }

    companion object {
        val instance: ClaudeUsageSettings
            get() = ApplicationManager.getApplication().getService(ClaudeUsageSettings::class.java)
    }
}
