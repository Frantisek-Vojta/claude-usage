package dev.fvojta.claudeusage.statusbar

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.fvojta.claudeusage.UsageService
import dev.fvojta.claudeusage.model.CostWindow
import dev.fvojta.claudeusage.model.QuotaTier
import dev.fvojta.claudeusage.settings.ClaudeUsageConfigurable
import dev.fvojta.claudeusage.settings.ClaudeUsageSettings
import java.awt.Component
import java.awt.Font
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JPanel

internal object UsagePopup {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun show(anchor: Component) {
        val service = UsageService.instance
        val panel = build(service)
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
            root.add(JBLabel(snap.error ?: "No subscription data yet").apply { foreground = UIUtil.getErrorForeground() })
        } else {
            for (tier in QuotaTier.entries) {
                val q = sub.quota(tier) ?: continue
                val reset = Format.untilReset(q.resetsAt).let { if (it.isEmpty()) "" else "  ·  resets in $it" }
                root.add(JBLabel("${tier.label}:  ${q.utilization.toInt()}%$reset"))
            }
            sub.extraUsage?.takeIf { it.isEnabled }?.let { e ->
                val used = e.usedCredits?.let { Format.usd(it) } ?: "?"
                val limit = e.monthlyLimit?.let { Format.usd(it) } ?: "?"
                root.add(JBLabel("Extra usage:  $used / $limit"))
            }
        }

        root.add(separator())
        root.add(header("Estimated cost (local logs, API list prices)"))
        val settings = ClaudeUsageSettings.instance.state
        for (w in CostWindow.entries) {
            val t = snap.local.window(w)
            val marker = if (w == settings.costWindow) "▸ " else "   "
            root.add(JBLabel("$marker${w.label}:  ${Format.tokens(t.totalTokens)} tok  ·  ${Format.cost(t)}"))
        }

        val models = snap.local.byModel.take(3)
        if (models.isNotEmpty()) {
            root.add(separator())
            root.add(header("Top models (all time)"))
            for ((model, t) in models) {
                root.add(JBLabel("$model:  ${Format.tokens(t.totalTokens)} tok  ·  ${Format.cost(t)}"))
            }
        }

        root.add(separator())
        val updated = snap.fetchedAt?.let { "Updated ${timeFmt.format(it)}" } ?: "Never updated"
        val footer = JPanel(VerticalLayout(4))
        footer.add(JBLabel(updated).apply { foreground = UIUtil.getContextHelpForeground() })
        footer.add(link("Refresh now") { service.refreshNow() })
        footer.add(link("Settings…") {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, ClaudeUsageConfigurable::class.java)
        })
        root.add(footer)
        return root
    }

    private fun header(text: String) = JBLabel(text).apply {
        font = font.deriveFont(Font.BOLD)
        foreground = UIUtil.getContextHelpForeground()
    }

    private fun separator() = JPanel().apply {
        border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1, 0, 0, 0)
        preferredSize = JBUI.size(0, 1)
    }

    private fun link(text: String, action: () -> Unit) =
        com.intellij.ui.components.ActionLink(text) { action() }
}
