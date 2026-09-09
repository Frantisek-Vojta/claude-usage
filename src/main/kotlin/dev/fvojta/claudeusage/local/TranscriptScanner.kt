package dev.fvojta.claudeusage.local

import com.intellij.openapi.diagnostic.logger
import dev.fvojta.claudeusage.model.LocalUsage
import dev.fvojta.claudeusage.model.TokenTotals
import dev.fvojta.claudeusage.model.UsageWindow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Counts tokens from the Claude Code CLI transcript logs:
 * `~/.claude/projects/<slug>/<session>.jsonl`, one JSON object per line.
 *
 * NOTE: this only sees traffic that went through the Claude Code CLI — not
 * claude.ai in the browser or other clients — so it is a lower bound on what
 * actually counts against your subscription quota.
 *
 * Per-file results are cached by (path, size, lastModified) so repeated scans
 * only re-parse sessions that changed.
 */
object TranscriptScanner {
    private val LOG = logger<TranscriptScanner>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val zone: ZoneId = ZoneId.systemDefault()

    private data class CacheKey(val path: String, val size: Long, val mtime: Long)

    private val cache = ConcurrentHashMap<String, Pair<CacheKey, Map<LocalDate, TokenTotals>>>()

    fun projectsDir(): File = File(System.getProperty("user.home"), ".claude/projects")

    fun scan(): LocalUsage {
        val root = projectsDir()
        if (!root.isDirectory) return LocalUsage()

        val files = root.walkTopDown().filter { it.isFile && it.extension == "jsonl" }.toList()
        val liveKeys = HashSet<String>(files.size)
        val byDate = HashMap<LocalDate, TokenTotals>()

        for (file in files) {
            liveKeys += file.path
            val key = CacheKey(file.path, file.length(), file.lastModified())
            val agg = cache[file.path]?.takeIf { it.first == key }?.second
                ?: parseFile(file).also { cache[file.path] = key to it }
            agg.forEach { (d, t) -> byDate.getOrPut(d) { TokenTotals() } += t }
        }
        cache.keys.retainAll(liveKeys)

        return LocalUsage(windows = buildWindows(byDate))
    }

    private fun buildWindows(byDate: Map<LocalDate, TokenTotals>): Map<UsageWindow, TokenTotals> {
        val today = LocalDate.now(zone)
        val result = UsageWindow.entries.associateWith { TokenTotals() }
        for ((date, totals) in byDate) {
            val daysBack = ChronoUnit.DAYS.between(date, today)
            if (date == today) result.getValue(UsageWindow.TODAY) += totals
            if (daysBack in 0..6) result.getValue(UsageWindow.LAST_WEEK) += totals
            if (daysBack in 0..29) result.getValue(UsageWindow.LAST_MONTH) += totals
        }
        return result
    }

    private fun parseFile(file: File): Map<LocalDate, TokenTotals> {
        val byDate = HashMap<LocalDate, TokenTotals>()
        val seen = HashSet<String>()
        try {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    if (obj.str("type") != "assistant") continue

                    val message = obj["message"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: continue
                    val model = message.str("model") ?: continue
                    if (model == "<synthetic>") continue

                    val usage = message["usage"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: continue

                    // De-duplicate: the CLI can write the same assistant turn twice.
                    val dedupKey = (message.str("id") ?: "") + "|" + (obj.str("requestId") ?: "")
                    if (dedupKey != "|" && !seen.add(dedupKey)) continue

                    val entry = TokenTotals(
                        inputTokens = usage.long("input_tokens"),
                        outputTokens = usage.long("output_tokens"),
                        cacheCreationTokens = usage.long("cache_creation_input_tokens"),
                        cacheReadTokens = usage.long("cache_read_input_tokens"),
                    )
                    if (entry.totalTokens == 0L) continue

                    val date = obj.str("timestamp")?.let {
                        runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull()
                    } ?: continue

                    byDate.getOrPut(date) { TokenTotals() } += entry
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse ${file.path}: ${e.message}")
        }
        return byDate
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

    private fun JsonObject.long(key: String): Long =
        runCatching { this[key]?.jsonPrimitive?.content?.toDouble()?.toLong() }.getOrNull() ?: 0L
}
