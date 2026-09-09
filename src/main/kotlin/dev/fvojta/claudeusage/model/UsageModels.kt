package dev.fvojta.claudeusage.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ----------------------------------------------------------------------------
 * Credentials file: ~/.claude/.credentials.json
 * ------------------------------------------------------------------------- */

@Serializable
data class CredentialsFile(
    val claudeAiOauth: OAuthCredentials? = null,
)

@Serializable
data class OAuthCredentials(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val subscriptionType: String? = null,
    val rateLimitTier: String? = null,
)

/* ----------------------------------------------------------------------------
 * Response of GET https://api.anthropic.com/api/oauth/usage
 * (undocumented endpoint used by the Claude Code CLI)
 * ------------------------------------------------------------------------- */

@Serializable
data class SubscriptionUsage(
    @SerialName("five_hour") val fiveHour: Quota? = null,
    @SerialName("seven_day") val sevenDay: Quota? = null,
    @SerialName("seven_day_sonnet") val sevenDaySonnet: Quota? = null,
    @SerialName("extra_usage") val extraUsage: ExtraUsage? = null,
) {
    fun quota(tier: QuotaTier): Quota? = when (tier) {
        QuotaTier.FIVE_HOUR -> fiveHour
        QuotaTier.SEVEN_DAY -> sevenDay
        QuotaTier.SEVEN_DAY_SONNET -> sevenDaySonnet
    }
}

@Serializable
data class Quota(
    /** Percentage used, 0..100. */
    val utilization: Double = 0.0,
    @SerialName("resets_at") val resetsAt: String? = null,
)

@Serializable
data class ExtraUsage(
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("monthly_limit") val monthlyLimit: Double? = null,
    @SerialName("used_credits") val usedCredits: Double? = null,
    val utilization: Double? = null,
)

enum class QuotaTier(val label: String) {
    FIVE_HOUR("5-hour"),
    SEVEN_DAY("7-day"),
    SEVEN_DAY_SONNET("7-day Sonnet"),
}

/* ----------------------------------------------------------------------------
 * Local token/cost aggregation from ~/.claude/projects/**/*.jsonl
 * ------------------------------------------------------------------------- */

/** Token totals for one time window. */
data class TokenTotals(
    var inputTokens: Long = 0,
    var outputTokens: Long = 0,
    var cacheCreationTokens: Long = 0,
    var cacheReadTokens: Long = 0,
    var costUsd: Double = 0.0,
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens

    operator fun plusAssign(other: TokenTotals) {
        inputTokens += other.inputTokens
        outputTokens += other.outputTokens
        cacheCreationTokens += other.cacheCreationTokens
        cacheReadTokens += other.cacheReadTokens
        costUsd += other.costUsd
    }
}

enum class CostWindow(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("7 days"),
    LAST_30_DAYS("30 days"),
    ALL_TIME("All time"),
}

data class LocalUsage(
    val windows: Map<CostWindow, TokenTotals> = emptyMap(),
    /** Per-model all-time totals, highest cost first. */
    val byModel: List<Pair<String, TokenTotals>> = emptyList(),
) {
    fun window(w: CostWindow): TokenTotals = windows[w] ?: TokenTotals()
}
