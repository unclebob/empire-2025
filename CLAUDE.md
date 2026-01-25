# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Empire is a Clojure implementation of the classic VMS Empire wargame - a turn-based strategy game of global conquest between a human player and computer opponent. The game uses Quil for rendering a grid-based map where players produce and command military units (armies, fighters, ships) to capture cities and destroy enemy forces. Source files use `.cljc` extension for cross-platform Clojure/ClojureScript compatibility.

## Development Commands

```bash
# Run the game
clj -M:run

# Run all tests with Speclj
clj -M:spec

# Run specific test file or directory
clj -M:spec spec/empire/movement_spec.clj
clj -M:spec spec/empire/units/

# Run tests with coverage report (outputs to target/coverage/)
clj -M:cov
```

## Architecture

### Core Game Loop

The game follows a Quil sketch pattern with `setup` → `update-state` → `draw-state` cycle at 30 FPS:

- **core.cljc**: Entry point, Quil sketch setup, keyboard/mouse event routing
- **game-loop.cljc**: Round progression, unit movement execution, production advancement
- **input.cljc**: Keyboard command handling (movement keys qweasdzxc, shift+key for extended movement to map edge, backtick prefix for special commands, production keys, sentry/explore modes)
- **rendering.cljc**: Map drawing, unit display, status area rendering

### State Management

All game state is stored in atoms defined in **atoms.cljc**:
- `game-map`: 2D vector of cells (the authoritative game state)
- `player-map` / `computer-map`: Fog-of-war visible maps for each side
- `cells-needing-attention`: Queue of units/cities awaiting player input
- `production`: Map of city coordinates to production status
- `destination`: Remembered destination for marching orders and flight paths
- `player-items`: List of player city/unit coords to process this round
- `waiting-for-input`: Flag indicating we're waiting for user input
- `backtick-pressed`: Modifier key state for prefixed commands (e.g., \`o for own-city)

### Key Modules

- **movement.cljc**: Unit movement logic, pathfinding, wake conditions, boarding/disembarking
- **production.cljc**: City production queue management, unit spawning
- **combat.cljc**: Battle resolution, city conquest attempts
- **attention.cljc**: Determines which units/cities need player attention each round
- **config.cljc**: Game constants (colors, key mappings); delegates unit stats to dispatcher
- **unit-container.cljc**: Helpers for units that carry other units (transports carry armies, carriers carry fighters, cities have airports)
- **map-utils.cljc**: Coordinate calculations, neighbor finding, screen-to-cell mapping
- **init.cljc**: Map generation with terrain smoothing, city placement

### Unit Modules

Unit-specific configuration and behavior is in `src/empire/units/`:
- Each unit type (army, fighter, satellite, transport, carrier, patrol-boat, destroyer, submarine, battleship) has its own module defining: `speed`, `cost`, `hits`, `display-char`, `visibility-radius`, `initial-state`, `can-move-to?`, `needs-attention?`
- **dispatcher.cljc**: Unified interface to access unit configuration by type (e.g., `(dispatcher/speed :fighter)`, `(dispatcher/can-move-to? :army cell)`)

### Cell Structure

Each map cell is a map with:
- `:type` - `:land`, `:sea`, `:city`, or `:unexplored`
- `:city-status` - `:player`, `:computer`, or `:free` (for cities)
- `:contents` - Unit map with `:type`, `:owner`, `:mode`, `:hits`, `:fuel`, etc.
- Container fields: `:fighter-count`, `:army-count`, `:awake-fighters`, etc.

### Unit Modes

Units operate in modes: `:awake` (needs orders), `:sentry` (sleeping), `:explore` (auto-exploring), `:moving` (executing movement orders)

### Computer AI and FSM Architecture

Computer unit movement decisions are made entirely within FSM (Finite State Machine) action functions. The FSM actions in `src/empire/fsm/` return a `:move-to` key in their result map specifying the target position. The orchestration layer (`src/empire/computer/army.cljc`) extracts `:move-to` from fsm-data and executes the movement, handling map mutations and visibility updates as side effects.

Key FSM modules:
- **coastline_explorer.cljc**: Drives army exploration with `seek-coast-action` and `follow-coast-action`
- **context.cljc**: Builds context maps for FSM guards and actions, includes `get-valid-army-moves`
- **engine.cljc**: FSM execution engine with priority event queue support

### Message Area Layout

The message area below the map has two sections, each 3 lines high:

**Move window (left side):**
- Line 1: Main game message
- Line 2: Combat log (e.g., "c-3,S-1,S-1. Submarine destroyed.")
- Line 3: Flashing red warnings (conquest failed, fighter destroyed, etc.)
- Shows messages relevant to the current and last move

**Debug window (middle):**
- Three lines between the move window and status window
- Used for debug-related messages and information
- Bounds should be set so it does not interfere with messages in the move window or status window

**Status window (right side):**
- Line 1: Round number
- Line 2: "PAUSED" indicator or destination coordinates
- Line 3: Hover info (cell details on mouse-over), debug file notifications
- All text is right-justified against the screen edge
- Width accommodates the longest hover message (~60 chars for city with production, fighters, and orders)
- Contains round number and other status information

## Coding Guidelines

### Quil Isolation

Functions in `input.cljc` and `rendering.cljc` that do not depend on Quil should be moved to appropriate non-Quil modules. Keep Quil dependencies (e.g., `q/mouse-x`, `q/mouse-y`, drawing functions) isolated to thin wrapper functions, with core logic extracted into testable, Quil-independent functions in modules like `movement.cljc`, `config.cljc`, or `unit-container.cljc`.

### Unused Arguments

Remove unused function arguments before committing. If an argument must be retained for API consistency (e.g., polymorphic dispatch where all implementations share the same signature), prefix it with `_` to indicate it is intentionally unused.

### Test Utilities

When adding new atoms to `atoms.cljc`, also add them to `reset-all-atoms!` in `test_utils.cljc`.
