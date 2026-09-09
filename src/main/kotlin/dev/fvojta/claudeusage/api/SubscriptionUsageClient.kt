package dev.fvojta.claudeusage.api

import com.intellij.openapi.diagnostic.logger
import dev.fvojta.claudeusage.model.SubscriptionUsage
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

/**
 * Calls the Claude Code CLI's own usage endpoint to read subscription quota.
 *
 * NOTE: `/api/oauth/usage` is **not** a documented public API. It is what the CLI
 * hits for `claude` -> `/usage`. Anthropic may change or remove it at any time.
 * The [USER_AGENT] below mirrors the CLI on purpose — the endpoint has rejected
 * unknown clients before. Bump it if the request starts returning 401/403.
 */
object SubscriptionUsageClient {
    private val LOG = logger<SubscriptionUsageClient>()
    private val json = Json { ignoreUnknownKeys = true }

    private const val URL = "https://api.anthropic.com/api/oauth/usage"
    private const val ANTHROPIC_BETA = "oauth-2025-04-20"
    private const val USER_AGENT = "claude-code/2.1.131"

    sealed interface Result {
        data class Ok(val usage: SubscriptionUsage) : Result
        data object Unauthorized : Result
        data class Failed(val detail: String) : Result
    }

    fun fetch(accessToken: String): Result {
        val conn = URI(URL).toURL().openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("anthropic-beta", ANTHROPIC_BETA)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.bufferedReader().readText()
                    runCatching { json.decodeFromString<SubscriptionUsage>(body) }
                        .map { Result.Ok(it) }
                        .getOrElse { Result.Failed("parse error: ${it.message}") }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> Result.Unauthorized
                429 -> Result.Failed("rate limited")
                else -> Result.Failed("HTTP $code")
            }
        } catch (e: Exception) {
            LOG.warn("Subscription usage request failed: ${e.message}")
            Result.Failed(e.message ?: "network error")
        } finally {
            conn.disconnect()
        }
    }
}
