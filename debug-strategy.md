# Debug Strategy for Empire

## Overview

Click-and-drag selection mechanism to capture a rectangular region of the map and write it to a timestamped file for diagnosing issues.

## Implementation Plan

### 1. New Atoms (atoms.cljc)
- `debug-drag-start` - Screen coords [x y] when drag begins, or nil
- `debug-drag-current` - Current screen coords during drag
- `action-log` - Vector (circular buffer) of recent actions, capped at ~100 entries

### 2. New Module: debug.cljc
**Functions:**
- `log-action!` - Append action to circular buffer with timestamp
- `dump-region` - Extract cells from all three maps for a coordinate range
- `format-cell` - Pretty-print a single cell's state
- `format-dump` - Build complete dump string with global state + cells
- `write-dump!` - Write to timestamped file (e.g., `debug-2026-01-25-143022.txt`)
- `screen-coords-to-cell-range` - Convert drag corners to map coordinates

### 3. Mouse Event Handling (input.cljc or core.cljc)
- On mouse-pressed with Ctrl/Shift: record `debug-drag-start`
- On mouse-dragged with modifier: update `debug-drag-current`
- On mouse-released with modifier: compute cell range, call `write-dump!`, clear drag state

### 4. Visual Feedback (rendering.cljc)
- When `debug-drag-start` is set, draw semi-transparent rectangle from start to current position

### 5. Action Logging Integration
Add `log-action!` calls in:
- `input.cljc` - Log each keyboard command
- `movement.cljc` - Log unit moves
- `combat.cljc` - Log combat outcomes
- `production.cljc` - Log production completions
- `game-loop.cljc` - Log round transitions

### 6. Test Utils Update
- Add new atoms to `reset-all-atoms!`

## Data Captured

### Per-cell data (from all three maps: game-map, player-map, computer-map):
- Coordinates, terrain type, city status
- Unit contents (type, owner, mode, hits, fuel, destination, path)
- Container state (fighter-count, army-count, awake-fighters, etc.)

### Global state:
- Current round number
- `cells-needing-attention` queue
- `player-items` remaining this round
- `waiting-for-input` flag state
- `destination` atom value
- Production queue for any cities in the selection

### Action history:
- Circular buffer of last ~100 actions with timestamps

## Output Format

```
=== Debug Dump: Round 42 ===
Selection: [3,5] to [8,12]
Timestamp: 2026-01-25T14:30:00

--- Global State ---
waiting-for-input: true
attention-queue: [[4,7] [5,9]]
destination: [10,15]
...

--- Recent Actions (last 20) ---
14:29:55 [:move :army [4,6] [4,7]]
14:29:56 [:combat :army [4,7] :destroyed :enemy-army]
...

--- Cells ---
[3,5] game-map: {:type :sea :contents {:type :destroyer :owner :player :mode :moving :hits 3}}
[3,5] player-map: {:type :sea :contents {:type :destroyer :owner :player :mode :moving :hits 3}}
[3,5] computer-map: {:type :unexplored}
...
```

## Trigger Mechanism

Ctrl-drag or Shift-drag to select region. On mouse release, dump is written automatically.

## File Naming

Timestamped files in project root: `debug-YYYY-MM-DD-HHMMSS.txt`
