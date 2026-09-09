package dev.fvojta.claudeusage.auth

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import dev.fvojta.claudeusage.model.CredentialsFile
import dev.fvojta.claudeusage.settings.ClaudeUsageSettings
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reads the OAuth access token that the Claude Code CLI stores after `claude` login.
 *
 * Order of lookup:
 *  1. macOS Keychain generic-password entry "Claude Code-credentials" (CLI default on macOS)
 *  2. `~/.claude/.credentials.json` (default elsewhere, and macOS fallback)
 *
 * The plugin never performs its own login and never writes these files.
 */
object CredentialsReader {
    private val LOG = logger<CredentialsReader>()
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Ok(val accessToken: String) : Result
        data class Err(val reason: Reason) : Result
    }

    enum class Reason(val message: String) {
        NOT_FOUND("Credentials not found — run `claude` to authenticate"),
        NO_TOKEN("No access token in credentials"),
        KEYCHAIN_DENIED("Keychain access denied — approve access for your IDE and retry"),
        UNREADABLE("Could not read credentials"),
    }

    fun read(): Result {
        val settings = ClaudeUsageSettings.instance

        if (SystemInfo.isMac && settings.state.useMacKeychain) {
            when (val fromKeychain = readFromKeychain()) {
                is Result.Ok -> return fromKeychain
                is Result.Err -> {
                    val fromFile = readFromFile(settings.credentialsPath())
                    if (fromFile is Result.Ok) return fromFile
                    return fromKeychain
                }
            }
        }
        return readFromFile(settings.credentialsPath())
    }

    private fun readFromFile(file: File): Result {
        if (!file.exists()) return Result.Err(Reason.NOT_FOUND)
        val raw = runCatching { file.readText() }.getOrElse {
            LOG.warn("Failed to read ${file.absolutePath}: ${it.message}")
            return Result.Err(Reason.UNREADABLE)
        }
        return parse(raw)
    }

    private fun readFromKeychain(): Result {
        return try {
            val cmd = GeneralCommandLine(
                "/usr/bin/security", "find-generic-password",
                "-s", "Claude Code-credentials", "-w",
            )
            val output = ExecUtil.execAndGetOutput(cmd)
            when {
                output.exitCode == 0 && output.stdout.isNotBlank() -> parse(output.stdout.trim())
                output.stderr.contains("could not be found", ignoreCase = true) -> Result.Err(Reason.NOT_FOUND)
                else -> Result.Err(Reason.KEYCHAIN_DENIED)
            }
        } catch (e: Exception) {
            LOG.warn("Keychain read failed: ${e.message}")
            Result.Err(Reason.KEYCHAIN_DENIED)
        }
    }

    private fun parse(raw: String): Result {
        val token = runCatching {
            json.decodeFromString<CredentialsFile>(raw).claudeAiOauth?.accessToken
        }.getOrElse {
            LOG.warn("Failed to parse credentials JSON: ${it.message}")
            return Result.Err(Reason.NO_TOKEN)
        }
        return if (token.isNullOrBlank()) Result.Err(Reason.NO_TOKEN) else Result.Ok(token)
    }
}
