package dev.fvojta.claudeusage.statusbar

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

internal object ClaudeIcons {
    /** 16×16 Claude sunburst, shown in the status-bar widget. */
    val STATUS: Icon = IconLoader.getIcon("/icons/claude.svg", ClaudeIcons::class.java)
}
