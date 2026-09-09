# Claude Usage Monitor

A small, free, open-source JetBrains plugin that shows your **Claude** usage in the
IDE status bar (bottom-right).

The status bar shows a Claude icon + `14% used | resets in 1:14` — percent of the
selected quota used and time (`H:MM`) until it resets.

| | Source | Needs login? |
|---|---|---|
| **Subscription quota** — 5-hour / 7-day / 7-day-Sonnet limits, `%` used, colour indicator, time to reset | `GET https://api.anthropic.com/api/oauth/usage`, authenticated with the token the Claude Code CLI already stored | No — reuses the CLI's credentials |
| **Tokens used** — total tokens for today / last week / last month | Parsed locally from `~/.claude/projects/**/*.jsonl` | No |

Click the widget for the breakdown (quota + token counts). Configure under
**Settings → Tools → Claude Usage Monitor**.

<!-- Add before publishing:
![Status bar](docs/status-bar.png)
![Popup](docs/popup.png)
-->

## Privacy

- The plugin reads the OAuth token the Claude Code CLI already stored
  (`~/.claude/.credentials.json` or the macOS Keychain). It is **read-only** — the plugin
  never writes your credentials.
- The token is sent to exactly **one** URL: `https://api.anthropic.com/api/oauth/usage`
  (HTTPS, Anthropic's own server), in the `Authorization` header, to read your usage.
  It goes nowhere else.
- The token is never logged and never persisted by the plugin; it lives in memory only
  for the duration of a request.
- The local scanner only **reads** files under `~/.claude/projects`. No other file access.
- No telemetry, no analytics, no third-party servers.
- It's ~10 small files — see `src/main/kotlin/dev/fvojta/claudeusage/auth/` and `api/`.

## Requirements

- IntelliJ-based IDE 2025.1+ (IDEA, WebStorm, PyCharm, GoLand, Rider, …)
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
        per assistant message: sum usage tokens, bucket by local date,
        cache per file by (size, mtime)
        ▼
   Snapshot ──► ClaudeUsageWidget (status bar) + UsagePopup (details)
```

### Caveats

- **`/api/oauth/usage` is undocumented.** It is what `claude` → `/usage` calls. Anthropic
  can change or remove it; the request deliberately sends a `claude-code/*` User-Agent
  (`SubscriptionUsageClient.USER_AGENT`) because the endpoint has rejected unknown clients
  before. If the quota part stops working, bump that constant.
- **Token counts are from the Claude Code CLI logs only** — usage from claude.ai in the
  browser or other clients is not in those files, so the numbers are a lower bound and
  will not match the quota percentage exactly.

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

1. Confirm `pluginId` / `pluginGroup` in `gradle.properties` are globally unique.
2. Create an account at <https://plugins.jetbrains.com>, accept the agreement. The upload
   form asks for a contact email — that one is not shown publicly (unlike `<vendor email>`
   in `plugin.xml`, which is why this repo leaves it out).
3. Generate a signing certificate (see the
   [signing docs](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)) and a
   publish token, then export:
   ```
   CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD, PUBLISH_TOKEN
   ```
4. `./gradlew signPlugin`, then upload `build/distributions/*.zip` **manually** for the first
   version (JetBrains reviews new plugins, ~2 business days). Upload the screenshots on the
   plugin page while you wait.
5. After approval: `./gradlew publishPlugin` for later releases.

### Screenshots to upload

1. **Status bar, cropped tight** — the bottom-right corner showing `⟡ 14% used | resets in 1:14`.
2. **Popup open** — click the widget: the Subscription quota + Tokens used breakdown.
3. **Settings page** — *Settings → Tools → Claude Usage Monitor*.
4. *(optional)* the widget in the yellow / red state, or a full-width shot of the IDE
   bottom bar so people see where it lives.

PNG, ideally one light-theme and one dark-theme shot, ~2× (retina). Nothing sensitive is
ever on screen (no token is shown anywhere in the UI).

## License

[MIT](LICENSE). Not affiliated with Anthropic.
