# Tick Replay Logger

A RuneLite plugin that records tick-by-tick combat data for post-death analysis. Learn from your mistakes by reviewing exactly what happened in the ticks leading up to your death.

## Features

### Death Recording
- Automatically captures the last 50-100 ticks (configurable) before each death
- Records HP, prayer points, and active prayers each tick
- Tracks damage received and dealt with source attribution
- Captures player actions: eating, drinking, prayer switches, gear changes, attacks, and special attacks
- Detects prayer conflicts (e.g., multiple overhead prayers, incompatible combat prayers)
- Stores up to 3 deaths (configurable) with persistent storage

### Playback System
- **Visual Playback Dashboard** - Replay recordings tick-by-tick with synchronized panels
- **Transport Controls** - Play/Pause, Previous/Next tick, Jump to First/Last
- **Playback Speed** - 0.5x, 1x, or 2x speed options
- **HP & Prayer Bars** - Color-coded progress bars showing current values with change indicators
- **Cumulative Damage** - Running totals for damage taken and dealt
- **Active Prayers Display** - Shows all active prayers for the current tick
- **Current Tick Events** - Detailed view of actions, damage, and movement with icons
- **Event Log Timeline** - Clickable list to jump to any tick
  - Multi-color angled stripe highlighting for significant events
  - Red for damage received, Blue for damage dealt, Green for healing, Purple for prayer actions
- **Combat Status** - Shows current state (Attacking, In Combat, Moving, Idle)
- **Keyboard Shortcuts** - Space (play/pause), Left/Right arrows (prev/next tick)

### Saved Recordings
- **Two-Section View** - Organized into "Saved Recordings" and "Recent Deaths"
- **Save Deaths** - Star button to save important deaths (won't auto-delete)
- **Save Live Snapshots** - Capture current tick buffer as a named recording
- **No Limit** - Saved recordings have no storage limit
- **Persistent Storage** - Saved recordings stored separately from deaths

### Timeline Views
- **Recordings View** - Browse deaths and saved recordings with expandable tick-by-tick timelines
- **Live View** - Watch the current tick buffer in real-time
  - Pause/resume to freeze the display
  - Export current buffer as JSON
  - Save current buffer as a recording
- **Icons** - OSRS skill icons for actions, damage, prayers, and movement

### Export
- Export individual recordings as JSON
- Copy to clipboard for external analysis or sharing

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Ticks to Record | 100 | Number of ticks to keep in memory before death |
| Max Deaths to Store | 3 | Maximum number of death records to persist (saved recordings are unlimited) |

## Usage

1. Open the Tick Replay Logger panel from the sidebar
2. Play normally - the plugin records in the background
3. When you die, the death is automatically saved
4. Click **Replay** to open the playback dashboard and step through tick-by-tick
5. Click **Timeline** to expand the inline tick timeline
6. Use **Live View** button to see real-time tick data
7. Click the **★** button to save a death permanently
8. In Live View, click **★ Save** to capture a snapshot

### Playback Controls

| Control | Action |
|---------|--------|
| ⏮ | Jump to first tick |
| ◀ | Previous tick |
| ▶/⏸ | Play/Pause |
| ▶ | Next tick |
| ⏭ | Jump to last tick (death) |
| Speed dropdown | Change playback speed |
| Event log row | Click to jump to that tick |

### Keyboard Shortcuts (in Playback)

| Key | Action |
|-----|--------|
| Space | Play/Pause |
| Left Arrow | Previous tick |
| Right Arrow | Next tick |

## Data Recorded Per Tick

- Tick number and timestamp
- Current/max HP and prayer points
- Active prayers (with upgrade filtering)
- Movement (tiles traveled, run/walk detection)
- Player actions (eat, drink, pray, equip, attack, special attack)
- Damage events (received and dealt, with source/target and type)

## Storage

Data is stored in the RuneLite directory:
- `~/.runelite/tick-replay-logger/deaths.json` - Recent deaths (auto-pruned)
- `~/.runelite/tick-replay-logger/saved.json` - Saved recordings (permanent)

## Building

```bash
./gradlew build
```

## Installation

Copy the built JAR to your RuneLite plugins folder, or load as an external plugin during development.
