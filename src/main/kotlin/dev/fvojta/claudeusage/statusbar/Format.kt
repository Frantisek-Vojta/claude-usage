package dev.fvojta.claudeusage.statusbar

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

    /** Time left until [isoTimestamp], as "H:MM" (or "Dd H:MM"); "" when unknown, "now" when past. */
    fun untilReset(isoTimestamp: String?): String {
        if (isoTimestamp.isNullOrBlank()) return ""
        val target = runCatching { OffsetDateTime.parse(isoTimestamp) }.getOrNull() ?: return ""
        val d = Duration.between(OffsetDateTime.now(), target)
        if (d.isNegative || d.isZero) return "now"
        val days = d.toDays()
        val hours = d.toHours() % 24
        val minutes = d.toMinutes() % 60
        val hm = "%d:%02d".format(hours, minutes)
        return if (days > 0) "${days}d $hm" else hm
    }
}
