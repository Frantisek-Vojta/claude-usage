package dev.fvojta.claudeusage.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class ClaudeUsageWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = WIDGET_ID
    override fun getDisplayName() = "Claude Usage Monitor"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = ClaudeUsageWidget()
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar) = true

    companion object {
        const val WIDGET_ID = "ClaudeUsageMonitorWidget"
    }
}
