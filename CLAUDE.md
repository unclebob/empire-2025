# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Empire is a Clojure implementation of the classic VMS Empire wargame - a turn-based strategy game of global conquest between a human player and computer opponent. The game uses Quil for rendering a grid-based map where players produce and command military units (armies, fighters, ships) to capture cities and destroy enemy forces.

## Development Commands

```bash
# Run the game
clj -M:run

# Run all tests with Speclj
clj -M:spec

# Run tests with coverage report (outputs to target/coverage/)
clj -M:cov
```

## Architecture

### Core Game Loop

The game follows a Quil sketch pattern with `setup` → `update-state` → `draw-state` cycle at 30 FPS:

- **core.cljc**: Entry point, Quil sketch setup, keyboard/mouse event routing
- **game-loop.cljc**: Round progression, unit movement execution, production advancement
- **input.cljc**: Keyboard command handling (movement keys QWEASDZXC, production keys, sentry/explore modes)
- **rendering.cljc**: Map drawing, unit display, status area rendering

### State Management

All game state is stored in atoms defined in **atoms.cljc**:
- `game-map`: 2D vector of cells (the authoritative game state)
- `player-map` / `computer-map`: Fog-of-war visible maps for each side
- `cells-needing-attention`: Queue of units/cities awaiting player input
- `production`: Map of city coordinates to production status

### Key Modules

- **movement.cljc**: Unit movement logic, pathfinding, wake conditions, boarding/disembarking
- **production.cljc**: City production queue management, unit spawning
- **combat.cljc**: Battle resolution, city conquest attempts
- **attention.cljc**: Determines which units/cities need player attention each round
- **config.cljc**: Game constants (unit stats, costs, speeds, colors, key mappings)
- **unit-container.cljc**: Helpers for units that carry other units (transports carry armies, carriers carry fighters, cities have airports)
- **map-utils.cljc**: Coordinate calculations, neighbor finding, screen-to-cell mapping
- **init.cljc**: Map generation with terrain smoothing, city placement

### Cell Structure

Each map cell is a map with:
- `:type` - `:land`, `:sea`, `:city`, or `:unexplored`
- `:city-status` - `:player`, `:computer`, or `:free` (for cities)
- `:contents` - Unit map with `:type`, `:owner`, `:mode`, `:hits`, `:fuel`, etc.
- Container fields: `:fighter-count`, `:army-count`, `:awake-fighters`, etc.

### Unit Modes

Units operate in modes: `:awake` (needs orders), `:sentry` (sleeping), `:explore` (auto-exploring), `:moving` (executing movement orders)
