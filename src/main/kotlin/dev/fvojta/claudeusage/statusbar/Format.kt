package dev.fvojta.claudeusage.statusbar

import dev.fvojta.claudeusage.model.TokenTotals
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.abs

internal object Format {

    fun tokens(n: Long): String {
        val a = abs(n)
        return when {
            a >= 1_000_000_000 -> "%.1fB".format(n / 1_000_000_000.0)
            a >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
            a >= 1_000 -> "%.1fk".format(n / 1_000.0)
            else -> n.toString()
        }
    }

    fun usd(v: Double): String = when {
        v >= 100 -> "$%.0f".format(v)
        v >= 1 -> "$%.2f".format(v)
        else -> "$%.3f".format(v)
    }

    fun cost(t: TokenTotals): String = usd(t.costUsd)

    /** "2d 3h", "3h 10m", "12m", "<1m", or "" when unknown. */
    fun untilReset(isoTimestamp: String?): String {
        if (isoTimestamp.isNullOrBlank()) return ""
        val target = runCatching { OffsetDateTime.parse(isoTimestamp) }.getOrNull() ?: return ""
        val d = Duration.between(OffsetDateTime.now(), target)
        if (d.isNegative || d.isZero) return "now"
        val days = d.toDays()
        val hours = d.toHours() % 24
        val minutes = d.toMinutes() % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
