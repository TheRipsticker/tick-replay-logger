# Changelog

## Unreleased
- Added a configurable **Save Snapshot Hotkey** (Hotkeys section, unbound by default) that saves the current tick buffer without opening the panel.
- Each hotkey snapshot is written to its own timestamped file in `~/.runelite/tick-replay-logger/snapshots/`, using a temp-file-then-move write so a crash can't leave a partial file.
- Hotkey snapshots appear in Saved Recordings and can be replayed, renamed, and deleted (deleting removes the file).
- Optional game chat confirmation when a hotkey snapshot is saved.

## 1.0.0 - 2026-01-27
- Initial Plugin Hub release as Tick Replay Logger.
- Added optional Live View toggle for strict post-death review.
- Improved live snapshot saving behavior and documentation.

