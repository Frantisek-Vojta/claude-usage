# Claude Usage Monitor

A small, free, open-source JetBrains plugin that shows your **Claude** usage in the
IDE status bar (bottom-right).

Two things in one widget:

| | Source | Needs login? |
|---|---|---|
| **Subscription quota** — 5-hour / 7-day / 7-day-Sonnet limits, `%` used, colour indicator, time to reset | `GET https://api.anthropic.com/api/oauth/usage`, authenticated with the token the Claude Code CLI already stored | No — reuses the CLI's credentials |
| **Token & cost breakdown** — input / output / cache tokens and estimated USD cost for today / 7d / 30d / all time, plus per-model totals | Parsed locally from `~/.claude/projects/**/*.jsonl` | No |

Click the widget for the full breakdown. Configure under
**Settings → Tools → Claude Usage Monitor**.

> Screenshots: `docs/status-bar.png`, `docs/popup.png` _(add before publishing)_

## Requirements

- IntelliJ-based IDE 2024.1+ (IDEA, WebStorm, PyCharm, GoLand, Rider, …)
- [Claude Code CLI](https://code.claude.com) installed and authenticated
  (`claude` → sign in). The plugin reads:
  - **macOS:** Keychain item `Claude Code-credentials`, falling back to the file below
  - **Linux / Windows:** `~/.claude/.credentials.json`

## How it works

```
UsageService (app service, 1 background thread, refresh every N min)
├── CredentialsReader ──► access token (Keychain or ~/.claude/.credentials.json)
├── SubscriptionUsageClient ──► GET /api/oauth/usage ──► 5h / 7d / 7d-Sonnet quota
└── TranscriptScanner ──► walk ~/.claude/projects/**/*.jsonl
        per assistant message: sum usage tokens, price with ModelPricing,
        bucket by local date + model, cache per file by (size, mtime)
        ▼
   Snapshot ──► ClaudeUsageWidget (status bar) + UsagePopup (details)
```

### Caveats

- **`/api/oauth/usage` is undocumented.** It is what `claude` → `/usage` calls. Anthropic
  can change or remove it; the request deliberately sends a `claude-code/*` User-Agent
  (`SubscriptionUsageClient.USER_AGENT`) because the endpoint has rejected unknown clients
  before. If the quota part stops working, bump that constant. The local token/cost view
  is unaffected.
- Costs are **estimates** at API list prices (`ModelPricing`), shown so you can see what
  the traffic would have cost per token. Subscription users are not billed this.

## Build & run

```bash
./gradlew runIde        # launch a sandbox IDE with the plugin
./gradlew buildPlugin    # -> build/distributions/claude-usage-monitor-<version>.zip
./gradlew verifyPlugin   # IntelliJ Plugin Verifier
```

The build targets a **JDK 17 toolchain**. You don't need JDK 17 installed — the
[Foojay resolver](https://github.com/gradle/foojay-toolchains) in `settings.gradle.kts`
lets Gradle download it on first run.

Install a local build: **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick the zip.

## Publishing to the JetBrains Marketplace

1. Pick a globally-unique plugin id and group in `gradle.properties`
   (`pluginId`, `pluginGroup`) — e.g. based on your GitHub handle.
2. Create an account at <https://plugins.jetbrains.com>, accept the agreement.
3. Generate a signing certificate (see the
   [template docs](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)) and a
   publish token, then export:
   ```
   CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD, PUBLISH_TOKEN
   ```
4. `./gradlew signPlugin` then upload `build/distributions/*.zip` **manually** for the first
   version (JetBrains reviews new plugins, ~2 business days).
5. After approval: `./gradlew publishPlugin` for subsequent releases.

## License

[MIT](LICENSE). Not affiliated with Anthropic.
