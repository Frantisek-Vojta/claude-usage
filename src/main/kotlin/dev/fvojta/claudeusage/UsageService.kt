package dev.fvojta.claudeusage

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import dev.fvojta.claudeusage.api.SubscriptionUsageClient
import dev.fvojta.claudeusage.auth.CredentialsReader
import dev.fvojta.claudeusage.local.TranscriptScanner
import dev.fvojta.claudeusage.model.LocalUsage
import dev.fvojta.claudeusage.model.SubscriptionUsage
import dev.fvojta.claudeusage.settings.ClaudeUsageSettings
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the periodic refresh and the latest [Snapshot]. UI components register a
 * listener and read [snapshot] on the EDT; all fetching happens on this
 * service's single background thread.
 */
class UsageService : Disposable {
    private val LOG = logger<UsageService>()

    data class Snapshot(
        val subscription: SubscriptionUsage? = null,
        val local: LocalUsage = LocalUsage(),
        val error: String? = null,
        val fetchedAt: Instant? = null,
    )

    private val ref = AtomicReference(Snapshot())
    val snapshot: Snapshot get() = ref.get()

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "claude-usage-monitor").apply { isDaemon = true }
    }
    private var scheduled: ScheduledFuture<*>? = null
    private var lastNotifiedTier = AtomicReference<Pair<String, Int>?>(null)

    init {
        reschedule()
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    /** Force an immediate off-EDT refresh. */
    fun refreshNow() {
        executor.execute(::doRefresh)
    }

    /** Re-read the interval from settings and restart the schedule. */
    fun reschedule() {
        scheduled?.cancel(false)
        val minutes = ClaudeUsageSettings.instance.state.refreshIntervalMinutes.coerceIn(1, 60)
        scheduled = executor.scheduleWithFixedDelay(
            ::doRefresh, 0, minutes.toLong(), TimeUnit.MINUTES,
        )
    }

    private fun doRefresh() {
        val local = runCatching { TranscriptScanner.scan() }.getOrElse {
            LOG.warn("Transcript scan failed: ${it.message}"); LocalUsage()
        }

        val subscription: SubscriptionUsage?
        var error: String? = null
        when (val creds = CredentialsReader.read()) {
            is CredentialsReader.Result.Ok -> {
                when (val res = SubscriptionUsageClient.fetch(creds.accessToken)) {
                    is SubscriptionUsageClient.Result.Ok -> subscription = res.usage
                    is SubscriptionUsageClient.Result.Unauthorized -> {
                        subscription = null; error = "Auth expired — run `claude` to re-authenticate"
                    }
                    is SubscriptionUsageClient.Result.Failed -> {
                        subscription = snapshot.subscription // keep last good value
                        error = res.detail
                    }
                }
            }
            is CredentialsReader.Result.Err -> {
                subscription = null; error = creds.reason.message
            }
        }

        ref.set(Snapshot(subscription, local, error, Instant.now()))
        maybeNotify(subscription)
        fireChanged()
    }

    private fun maybeNotify(usage: SubscriptionUsage?) {
        val settings = ClaudeUsageSettings.instance.state
        if (!settings.notifyOnThreshold || usage == null) return
        val tier = settings.quotaTier
        val pct = usage.quota(tier)?.utilization?.toInt() ?: return

        val level = when {
            pct >= settings.redThreshold -> settings.redThreshold
            pct >= settings.yellowThreshold -> settings.yellowThreshold
            else -> 0
        }
        val last = lastNotifiedTier.get()
        if (level == 0) { lastNotifiedTier.set(null); return }
        if (last != null && last.first == tier.label && last.second == level) return
        lastNotifiedTier.set(tier.label to level)

        val type = if (level >= settings.redThreshold) NotificationType.WARNING else NotificationType.INFORMATION
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Claude Usage Monitor")
                .createNotification("Claude ${tier.label} usage at $pct%", "", type)
                .notify(null)
        }
    }

    private fun fireChanged() {
        ApplicationManager.getApplication().invokeLater { listeners.forEach { runCatching { it() } } }
    }

    override fun dispose() {
        scheduled?.cancel(true)
        executor.shutdownNow()
    }

    companion object {
        val instance: UsageService
            get() = ApplicationManager.getApplication().getService(UsageService::class.java)
    }
}
