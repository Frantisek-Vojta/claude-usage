# Changelog

## [Unreleased]

## [0.1.0]
### Added
- Status-bar widget (bottom-right) showing `% used | H:MM` for the selected Claude
  subscription quota (5-hour / 7-day / 7-day-Sonnet), with a colour indicator.
- Local token counts for today / last week / last month, parsed from
  `~/.claude/projects/**/*.jsonl` with a per-file mtime cache.
- Click-through popup: subscription quota + token counts, a manual refresh and a settings link.
- Settings panel under *Settings → Tools → Claude Usage Monitor*.
- Optional notification when a quota crosses the yellow / red threshold.
- macOS Keychain support with credentials-file fallback.
