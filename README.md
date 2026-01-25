# Death Trace

A RuneLite plugin that records tick-by-tick combat data for post-death analysis. Learn from your mistakes by reviewing exactly what happened in the ticks leading up to your death.

## Features

### Death Recording
- Automatically captures the last 50-100 ticks (configurable) before each death
- Records HP, prayer points, and active prayers each tick
- Tracks damage received and dealt with source attribution
- Captures player actions: eating, drinking, prayer switches, gear changes, attacks, and special attacks
- Detects forced prayer deactivation (running out of prayer, Vorkath purple, etc.)
- Stores up to 3 deaths (configurable) with persistent storage

### Timeline Views
- **Deaths View**: Browse recorded deaths with expandable tick-by-tick timelines
- **Live View**: Watch the current tick buffer in real-time for debugging
  - Pause/resume to freeze the display
  - Export current buffer as JSON

### Export
- Export individual deaths or all deaths as JSON
- Copy to clipboard for external analysis or sharing

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Ticks to Record | 100 | Number of ticks to keep in memory before death |
| Max Deaths to Store | 3 | Maximum number of death records to persist |

## Usage

1. Open the Death Trace panel from the sidebar
2. Play normally - the plugin records in the background
3. When you die, the death is automatically saved
4. Click on a death entry to expand and view the tick timeline
5. Use "Live View" button to see real-time tick data
6. Export deaths as JSON for detailed analysis

## Data Recorded Per Tick

- Tick number and timestamp
- Current/max HP and prayer points
- Active prayers (with upgrade filtering)
- Movement (tiles traveled)
- Player actions (eat, drink, pray, equip, attack, special attack)
- Damage events (received and dealt, with source/target)

## Building

```bash
./gradlew build
```

## Installation

Copy the built JAR to your RuneLite plugins folder, or load as an external plugin during development.
