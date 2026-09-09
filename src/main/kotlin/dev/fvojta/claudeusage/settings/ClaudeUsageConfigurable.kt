package dev.fvojta.claudeusage.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import dev.fvojta.claudeusage.UsageService
import dev.fvojta.claudeusage.model.QuotaTier

class ClaudeUsageConfigurable : BoundConfigurable("Claude Usage Monitor") {

    private val state get() = ClaudeUsageSettings.instance.state

    override fun createPanel(): DialogPanel = panel {
        group("Status bar") {
            row("Show quota:") {
                comboBox(QuotaTier.entries.toList())
                    .bindItem({ state.quotaTier }, { state.quotaTier = it ?: QuotaTier.FIVE_HOUR })
            }
        }

        group("Refresh") {
            row("Interval (minutes):") {
                spinner(1..60).bindIntValue({ state.refreshIntervalMinutes }, { state.refreshIntervalMinutes = it })
            }
        }

        group("Thresholds") {
            row("Yellow at (%):") {
                spinner(1..100).bindIntValue({ state.yellowThreshold }, { state.yellowThreshold = it })
            }
            row("Red at (%):") {
                spinner(1..100).bindIntValue({ state.redThreshold }, { state.redThreshold = it })
            }
            row {
                checkBox("Notify when a quota crosses a threshold")
                    .bindSelected({ state.notifyOnThreshold }, { state.notifyOnThreshold = it })
            }
        }

        group("Credentials") {
            if (SystemInfo.isMac) {
                row {
                    checkBox("Read token from macOS Keychain (with file fallback)")
                        .bindSelected({ state.useMacKeychain }, { state.useMacKeychain = it })
                }
            }
            row("Credentials file:") {
                textField()
                    .columns(40)
                    .bindText({ state.credentialsFilePath.orEmpty() }, { state.credentialsFilePath = it.trim() })
                    .comment("Leave empty for the default <code>~/.claude/.credentials.json</code>")
            }
        }
    }

    override fun apply() {
        super.apply()
        UsageService.instance.reschedule()
        UsageService.instance.refreshNow()
    }
}
