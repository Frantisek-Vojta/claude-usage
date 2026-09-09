# Claude Usage for JetBrains IDEs

A small, free, open-source plugin that shows your **Claude** subscription usage in the
IDE status bar (bottom-right).

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2025.1%2B-000?logo=intellijidea&logoColor=white)](https://plugins.jetbrains.com/docs/intellij/)
[![Marketplace](https://img.shields.io/badge/JetBrains%20Marketplace-Claude%20Usage%20Monitor-FE315D?logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/34185)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

The widget shows a Claude icon + `14% used | resets in 1:14` — how much of the selected
quota is gone and the time (`H:MM`) until it resets. Click it for the full breakdown.

<!-- Save the Marketplace screenshots here to have them render:
![Status bar](docs/status-bar.png)
![Popup](docs/popup.png)
![Settings](docs/settings.png)
-->

## Features

- **Subscription quota** in the status bar — the 5-hour, 7-day and 7-day-Sonnet limits,
  with a percentage, a colour indicator (green → yellow → red) and time to reset. Same
  numbers as `claude` → `/usage`.
- **Tokens used** — total tokens sent and received (prompt-cache traffic shown separately)
  for today / last week / last month, counted locally from the Claude Code transcript logs.
- Click-through popup with the full breakdown and a manual refresh.
- Optional IDE notification when a quota crosses the yellow / red threshold.
- Settings under **Settings → Tools → Claude Usage Monitor**: which quota to show, refresh
  interval, colour thresholds, credentials path.
- macOS Keychain support, with a credentials-file fallback.

## Install

From the IDE: **Settings → Plugins → Marketplace**, search **"Claude Usage Monitor"**.

Or grab the ZIP from the [Marketplace page](https://plugins.jetbrains.com/plugin/34185) /
[Releases](https://github.com/Frantisek-Vojta/claude-usage/releases) and use
**Settings → Plugins → ⚙ → Install Plugin from Disk…**.

## Requirements

- An IntelliJ-based IDE 2025.1+ (IDEA, WebStorm, PyCharm, GoLand, Rider, …).
- The [Claude Code CLI](https://code.claude.com) installed and signed in (`claude`). The
  plugin reads the token it stores:
  - **macOS:** Keychain item `Claude Code-credentials`, falling back to the file below.
  - **Linux / Windows:** `~/.claude/.credentials.json`.

## Privacy

- The credentials are **read-only**. The plugin never writes them.
- The token is sent to exactly **one** URL — `https://api.anthropic.com/api/oauth/usage`
  (HTTPS, Anthropic's own server), in the `Authorization` header — to read your usage.
  Nowhere else. There are no other network calls in the code.
- The token is never logged and never persisted by the plugin; it stays in memory only
  for the duration of a request.
- The local scanner only **reads** files under `~/.claude/projects`.
- No telemetry, no analytics, no third-party servers.

The relevant code is a handful of files under
[`src/main/kotlin/dev/fvojta/claudeusage/auth/`](src/main/kotlin/dev/fvojta/claudeusage/auth)
and [`api/`](src/main/kotlin/dev/fvojta/claudeusage/api).

## How it works

```
UsageService (application service, one background thread, refresh every N minutes)
├── CredentialsReader ──► access token (Keychain or ~/.claude/.credentials.json)
├── SubscriptionUsageClient ──► GET /api/oauth/usage ──► 5h / 7d / 7d-Sonnet quota
└── TranscriptScanner ──► walk ~/.claude/projects/**/*.jsonl
        per assistant message: sum usage tokens, bucket by local date,
        cache per file by (size, mtime)
        ▼
   Snapshot ──► ClaudeUsageWidget (status bar) + UsagePopup (details)
```

## Limitations

- **`/api/oauth/usage` is undocumented** — it is what `claude` → `/usage` calls. Anthropic
  can change or remove it. The request mirrors the CLI's `User-Agent`
  (`SubscriptionUsageClient.USER_AGENT`) because the endpoint has rejected unknown clients
  before; bump that constant if the quota stops updating.
- **Token counts come from the Claude Code CLI logs only** — usage from claude.ai in the
  browser or other clients is not in those files, so the numbers are a lower bound and
  won't match the quota percentage exactly.
- Not affiliated with Anthropic.

## Development

```bash
./gradlew runIde         # sandbox IDE with the plugin loaded
./gradlew buildPlugin     # -> build/distributions/claude-usage-monitor-<version>.zip
./gradlew verifyPlugin    # IntelliJ Plugin Verifier
```

The build targets a JDK 17 toolchain; the
[Foojay resolver](https://github.com/gradle/foojay-toolchains) in `settings.gradle.kts`
provisions it automatically on first run, so no manual JDK install is needed.

## License

[MIT](LICENSE).
