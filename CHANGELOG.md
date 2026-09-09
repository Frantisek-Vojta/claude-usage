# Changelog

## [Unreleased]

## [0.1.0]
### Added
- Status-bar widget (bottom-right) showing the selected Claude subscription quota
  (5-hour / 7-day / 7-day-Sonnet) with a percentage, colour indicator and time to reset.
- Local token & estimated-cost breakdown (today / 7 days / 30 days / all time and per model),
  parsed from `~/.claude/projects/**/*.jsonl` with a per-file mtime cache.
- Click-through popup with the full breakdown, a manual refresh and a settings link.
- Settings panel under *Settings → Tools → Claude Usage Monitor*.
- Optional notification when a quota crosses the yellow / red threshold.
- macOS Keychain support with credentials-file fallback.
