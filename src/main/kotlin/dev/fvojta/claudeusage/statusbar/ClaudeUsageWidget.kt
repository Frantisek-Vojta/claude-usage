package dev.fvojta.claudeusage.statusbar

import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.fvojta.claudeusage.UsageService
import dev.fvojta.claudeusage.model.QuotaTier
import dev.fvojta.claudeusage.settings.ClaudeUsageSettings
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingConstants

class ClaudeUsageWidget : CustomStatusBarWidget {

    private val label = JBLabel("Claude …", null, SwingConstants.CENTER).apply {
        border = JBUI.Borders.empty(0, 6)
        toolTipText = "Claude usage — click for details"
    }
    private val listener: () -> Unit = { UIUtil.invokeLaterIfNeeded(::render) }

    override fun ID() = ClaudeUsageWidgetFactory.WIDGET_ID

    override fun getPresentation(): com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation? = null

    override fun getComponent(): JComponent = label

    override fun install(statusBar: StatusBar) {
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1) UsagePopup.show(label)
            }
        })
        UsageService.instance.addListener(listener)
        render()
    }

    override fun dispose() {
        UsageService.instance.removeListener(listener)
    }

    private fun render() {
        val snap = UsageService.instance.snapshot
        val settings = ClaudeUsageSettings.instance.state
        val quota = snap.subscription?.quota(settings.quotaTier)

        label.text = if (quota == null) {
            "Claude —"
        } else {
            val pct = "${quota.utilization.toInt()}% used"
            val reset = Format.untilReset(quota.resetsAt)
            if (reset.isEmpty()) pct else "$pct | $reset"
        }

        label.foreground = when {
            quota == null -> UIUtil.getLabelForeground()
            quota.utilization >= settings.redThreshold -> JBColor.RED
            quota.utilization >= settings.yellowThreshold -> JBColor(0xB58900, 0xB58900)
            else -> JBColor(0x2E7D32, 0x66BB6A)
        }

        label.toolTipText = buildString {
            append("<html>")
            snap.subscription?.let { sub ->
                QuotaTier.entries.forEach { tier ->
                    sub.quota(tier)?.let { append("${tier.label}: ${it.utilization.toInt()}%<br>") }
                }
            }
            snap.error?.let { append("<font color='red'>$it</font><br>") }
            append("Click for the full breakdown.</html>")
        }
    }
}
