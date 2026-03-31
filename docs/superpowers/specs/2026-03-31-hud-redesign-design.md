# HUD Redesign

## Overview

Redesign the bottom message area into a 3-row x 2-column grid of dedicated zones.
Each message category owns its zone — no overwriting. Warnings play an audible "bonk."

## Layout

Same rectangular area: map-width pixels wide, 80px tall (5 rows x 16px cell height).
Partitioned into a 3x2 grid. Left column ~55%, right column ~45%.

```
+------------------------------+------------------------+
| ATTENTION (gold)             | GAME STATUS (gray/grn) |
| Row 1 Left                   | Row 1 Right            |
+------------------------------+------------------------+
| WARNING (red + bonk)         | INSPECTOR SUMMARY      |
| Row 2 Left                   | Row 2 Right            |
+------------------------------+------------------------+
| COMMAND RESPONSE (light blue)| INSPECTOR DETAIL       |
| Row 3 Left                   | Row 3 Right            |
+------------------------------+------------------------+
```

## Zone Definitions

### Attention (Row 1, Left)
- **Content**: Unit/city description + reason. No "needs attention" text.
- **Examples**: "Fighter [23,15] - Bingo! Refuel?", "City [45,12]",
  "Army [10,5] - aboard transport - At beach",
  "Damaged Destroyer [23,15] 2/3 hits"
- **Color**: Gold (#ffd740)
- **Lifetime**: Persists until unit/city is given orders or dismissed.
- **Sound**: None.

### Warning (Row 2, Left)
- **Content**: Blocked movement, combat outcomes, environmental alerts, critical errors.
- **Examples**: "Can't move into water", "Something's in the way",
  "Submarine destroyed.", "Fighter destroyed by city defenses",
  "Failed to conquer city", "Enemy spotted", "Fighter out of fuel",
  "Hit map edge", "Lookaround limit reached", "Returned to start",
  "Not near coast", "That's not on the map!",
  "Must be coastal city to produce destroyer."
- **Color**: Red (#ff5050)
- **Lifetime**: Clears on next player action (any keypress or click).
- **Sound**: "Bonk" plays once when the warning appears.
- **Note**: Combat results show only the outcome ("Submarine destroyed."),
  not blow-by-blow counts.

### Command Response (Row 3, Left)
- **Content**: Order confirmations, save/load, informational feedback.
- **Examples**: "Marching orders set to 45,12", "Flight path set to 30,8",
  "Waypoint placed at 20,10", "Waypoint removed from 20,10",
  "Saved to autosave.edn", "Loaded autosave.edn",
  "Landed and refueled", "Docked for repair. 2/3 hits",
  "At beach", "Found land!", "Debug: /path/to/dump"
- **Color**: Light blue (#ebf5ff), muted opacity.
- **Lifetime**: Clears on next player action.
- **Sound**: None.

### Game Status (Row 1, Right)
- **Content**: Round number, PAUSED indicator, exploration percentage,
  compact production counts (zero counts dropped).
- **Color**: Light gray (#bec6d0), green (#90ee90) for exploration %,
  red (#ff6666) for PAUSED.
- **Lifetime**: Always visible. Updated every round.
- **Tooltip**: Hover shows full production detail (existing popup behavior).

### Inspector Summary (Row 2, Right)
- **Content**: Hover cell info — coordinates, unit type, hits, mode.
- **Color**: Light gray (#bec6d0).
- **Lifetime**: Visible while mouse hovers over map; clears when mouse leaves.

### Inspector Detail (Row 3, Right)
- **Content**: Additional hover detail — cargo, fuel, production, dock status.
- **Color**: Light gray (#bec6d0).
- **Lifetime**: Same as Inspector Summary.

## Clearing Rules

| Zone             | Clears when...                              |
|------------------|---------------------------------------------|
| Attention        | Unit/city is given orders or dismissed       |
| Warning          | Next player action (any keypress or click)   |
| Command Response | Next player action (any keypress or click)   |
| Game Status      | Never (always visible, updated each round)   |
| Inspector        | Mouse leaves map area                        |

## Audio

- Single "bonk" sound effect.
- Format: `.wav` file bundled in `resources/`.
- Playback: `javax.sound.sampled` — load clip once at startup, play on each warning.
- Triggers: Every write to the Warning zone.

## Visual Styling

- Background: Dark blue (#0e1216), same as current HUD.
- Grid separators: Subtle lines (#1e2428 horizontal, #363c42 vertical).
- Font: Courier New, consistent size across all zones (~13px equivalent at 18pt Quil).
- All text left-aligned within each zone.
- Game Status right-column items spaced across the row.

## State Changes

Replace the current message state keys with zone-specific keys:
- `:attention-message` — retained, same semantics.
- `:warning-message` — replaces `:error-message`. No timeout; cleared by action.
- `:command-message` — replaces `:turn-message`. No timeout; cleared by action.
- `:hover-message` — retained, same semantics.
- `:production-status` — retained, compact format (drop zeros).
- Remove `:error-until`, `:turn-message-until` (timeouts no longer needed).

## Message Classification

### Attention zone
- City attention
- Fighter attention (airport, carrier, fuel)
- Army attention (transport, beach)
- Damaged unit attention
- Unit needing orders

### Warning zone (bonk)
- Can't move into water
- Ships don't drive on land
- Ships can't enter city
- Something's in the way
- Blocked
- Combat outcome (e.g., "Submarine destroyed.")
- Fighter destroyed by city defenses
- Failed to conquer city / Conquest failed
- Enemy spotted
- Fighter out of fuel / Fighter crashed
- Hit map edge
- Lookaround limit reached
- Returned to start
- Not near coast
- That's not on the map!
- Coastal city required for naval production
- Game over
- World integrity error

### Command response zone
- Marching orders set
- Flight path set
- Waypoint placed / removed / orders set
- Saved to / Loaded from file
- Landed and refueled
- Docked for repair
- At beach / Found land!
- Transport at beach
- Debug dump path
- Marching orders lookaround
