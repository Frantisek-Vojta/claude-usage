package dev.fvojta.claudeusage.local

import com.intellij.openapi.diagnostic.logger
import dev.fvojta.claudeusage.model.CostWindow
import dev.fvojta.claudeusage.model.LocalUsage
import dev.fvojta.claudeusage.model.TokenTotals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Aggregates token usage and estimated cost from the Claude Code CLI transcript
 * logs: `~/.claude/projects/<slug>/<session>.jsonl`, one JSON object per line.
 *
 * Per-file results are cached by (path, size, lastModified) so repeated scans
 * only re-parse sessions that changed.
 */
object TranscriptScanner {
    private val LOG = logger<TranscriptScanner>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val zone: ZoneId = ZoneId.systemDefault()

    private data class CacheKey(val path: String, val size: Long, val mtime: Long)
    private data class FileAgg(
        val byDate: Map<LocalDate, TokenTotals>,
        val byModel: Map<String, TokenTotals>,
    )

    private val cache = ConcurrentHashMap<String, Pair<CacheKey, FileAgg>>()

    fun projectsDir(): File = File(System.getProperty("user.home"), ".claude/projects")

    fun scan(): LocalUsage {
        val root = projectsDir()
        if (!root.isDirectory) return LocalUsage()

        val files = root.walkTopDown().filter { it.isFile && it.extension == "jsonl" }.toList()
        val liveKeys = HashSet<String>(files.size)

        val byDate = HashMap<LocalDate, TokenTotals>()
        val byModel = HashMap<String, TokenTotals>()

        for (file in files) {
            liveKeys += file.path
            val key = CacheKey(file.path, file.length(), file.lastModified())
            val agg = cache[file.path]?.takeIf { it.first == key }?.second
                ?: parseFile(file).also { cache[file.path] = key to it }

            agg.byDate.forEach { (d, t) -> byDate.getOrPut(d) { TokenTotals() } += t }
            agg.byModel.forEach { (m, t) -> byModel.getOrPut(m) { TokenTotals() } += t }
        }
        cache.keys.retainAll(liveKeys)

        return LocalUsage(
            windows = buildWindows(byDate),
            byModel = byModel.entries.sortedByDescending { it.value.costUsd }.map { it.key to it.value },
        )
    }

    private fun buildWindows(byDate: Map<LocalDate, TokenTotals>): Map<CostWindow, TokenTotals> {
        val today = LocalDate.now(zone)
        val result = CostWindow.entries.associateWith { TokenTotals() }
        for ((date, totals) in byDate) {
            val daysBack = java.time.temporal.ChronoUnit.DAYS.between(date, today)
            if (date == today) result.getValue(CostWindow.TODAY) += totals
            if (daysBack in 0..6) result.getValue(CostWindow.LAST_7_DAYS) += totals
            if (daysBack in 0..29) result.getValue(CostWindow.LAST_30_DAYS) += totals
            result.getValue(CostWindow.ALL_TIME) += totals
        }
        return result
    }

    private fun parseFile(file: File): FileAgg {
        val byDate = HashMap<LocalDate, TokenTotals>()
        val byModel = HashMap<String, TokenTotals>()
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

                    val input = usage.long("input_tokens")
                    val output = usage.long("output_tokens")
                    val cacheWrite = usage.long("cache_creation_input_tokens")
                    val cacheRead = usage.long("cache_read_input_tokens")
                    if (input == 0L && output == 0L && cacheWrite == 0L && cacheRead == 0L) continue

                    val date = obj.str("timestamp")?.let {
                        runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull()
                    } ?: continue

                    val cost = ModelPricing.cost(model, input, output, cacheWrite, cacheRead)
                    val entry = TokenTotals(input, output, cacheWrite, cacheRead, cost)
                    byDate.getOrPut(date) { TokenTotals() } += entry
                    byModel.getOrPut(model) { TokenTotals() } += entry
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse ${file.path}: ${e.message}")
        }
        return FileAgg(byDate, byModel)
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

    private fun JsonObject.long(key: String): Long =
        runCatching { this[key]?.jsonPrimitive?.content?.toDouble()?.toLong() }.getOrNull() ?: 0L
}
