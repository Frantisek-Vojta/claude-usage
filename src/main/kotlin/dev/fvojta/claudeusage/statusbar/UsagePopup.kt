package dev.fvojta.claudeusage.statusbar

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.fvojta.claudeusage.UsageService
import dev.fvojta.claudeusage.model.QuotaTier
import dev.fvojta.claudeusage.model.UsageWindow
import dev.fvojta.claudeusage.settings.ClaudeUsageConfigurable
import java.awt.Component
import java.awt.Font
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JPanel

internal object UsagePopup {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun show(anchor: Component) {
        val panel = build(UsageService.instance)
        val popup: JBPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setRequestFocus(true)
            .setResizable(false)
            .setMovable(false)
            .createPopup()
        popup.showUnderneathOf(anchor)
    }

    private fun build(service: UsageService): JPanel {
        val snap = service.snapshot
        val root = JPanel(VerticalLayout(6)).apply { border = JBUI.Borders.empty(10, 12) }

        root.add(header("Subscription quota"))
        val sub = snap.subscription
        if (sub == null) {
            root.add(JBLabel(snap.error ?: "No subscription data yet").apply {
                foreground = UIUtil.getErrorForeground()
            })
        } else {
            for (tier in QuotaTier.entries) {
                val q = sub.quota(tier) ?: continue
                val reset = Format.untilReset(q.resetsAt).let { if (it.isEmpty()) "" else "  ·  resets in $it" }
                root.add(JBLabel("${tier.label}:  ${q.utilization.toInt()}%$reset"))
            }
        }

        root.add(separator())
        root.add(header("Tokens used (sent + received)"))
        root.add(caption("Claude Code CLI only. Cache = context re-read each turn; cheap, not quota."))
        for (w in UsageWindow.entries) {
            val t = snap.local.window(w)
            root.add(JBLabel("${w.label}:  ${Format.tokens(t.ioTokens)}  ·  ${Format.tokens(t.cacheTokens)} cache"))
        }

        root.add(separator())
        val updated = snap.fetchedAt?.let { "Updated ${timeFmt.format(it)}" } ?: "Never updated"
        val footer = JPanel(VerticalLayout(4))
        footer.add(caption(updated))
        footer.add(ActionLink("Refresh now") { service.refreshNow() })
        footer.add(ActionLink("Settings…") {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, ClaudeUsageConfigurable::class.java)
        })
        root.add(footer)
        return root
    }

    private fun header(text: String) = JBLabel(text).apply {
        font = font.deriveFont(Font.BOLD)
        foreground = UIUtil.getContextHelpForeground()
    }

    private fun caption(text: String) = JBLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }

    private fun separator() = JPanel().apply {
        border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1, 0, 0, 0)
        preferredSize = JBUI.size(0, 1)
    }
}
