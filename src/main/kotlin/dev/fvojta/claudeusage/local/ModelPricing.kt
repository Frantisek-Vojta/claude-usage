package dev.fvojta.claudeusage.local

/**
 * Best-effort USD pricing per 1M tokens, used only to *estimate* what the logged
 * traffic would have cost at API list prices. Subscription users are not billed
 * this. Prices are approximate and change over time — adjust as needed.
 */
object ModelPricing {

    data class Rates(
        val inputPerM: Double,
        val outputPerM: Double,
        val cacheWritePerM: Double,
        val cacheReadPerM: Double,
    )

    private val OPUS = Rates(15.0, 75.0, 18.75, 1.50)
    private val SONNET = Rates(3.0, 15.0, 3.75, 0.30)
    private val HAIKU = Rates(0.80, 4.0, 1.0, 0.08)

    /** Matches on the model family found in the model id, defaulting to Sonnet. */
    fun ratesFor(model: String): Rates {
        val m = model.lowercase()
        return when {
            "opus" in m -> OPUS
            "haiku" in m -> HAIKU
            else -> SONNET
        }
    }

    fun cost(
        model: String,
        inputTokens: Long,
        outputTokens: Long,
        cacheCreationTokens: Long,
        cacheReadTokens: Long,
    ): Double {
        val r = ratesFor(model)
        return inputTokens / 1_000_000.0 * r.inputPerM +
            outputTokens / 1_000_000.0 * r.outputPerM +
            cacheCreationTokens / 1_000_000.0 * r.cacheWritePerM +
            cacheReadTokens / 1_000_000.0 * r.cacheReadPerM
    }
}
