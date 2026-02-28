# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Empire is a Clojure implementation of the classic VMS Empire wargame - a turn-based strategy game of global conquest between a human player and computer opponent. The game uses Quil for rendering a grid-based map where players produce and command military units (armies, fighters, ships) to capture cities and destroy enemy forces. Source files use `.cljc` extension for cross-platform Clojure/ClojureScript compatibility.

## Permissions
allow git, sed, cp, ls, cat, rm, echo, redirection, while read line, clj, clojure, and all standard unix tools.

## Local rules
- If the working directory is named <x> then local rules will be stored in <x>.md. BEFORE making any changes, read `<x>.md` and follow its restrictions.
- If in a worktree, stay in that worktree and merge to other branches from there.
## Acceptance Tests

Acceptance tests are `.txt` files in `acceptanceTests/` in Given/When/Then format.
The detailed directive catalog and translation reference is in `plans/permanent/acceptance-test-framework.md`.
Only read that file when modifying or debugging the parser (`src/empire/acceptance/parser.cljc`) or generator (`src/empire/acceptance/generator.cljc`) — not when writing acceptance tests or running the pipeline.
The parser pattern catalog is in `plans/permanent/parser-pattern-catalog.md`. Read it BEFORE loading any `src/empire/acceptance/parser/*.cljc` source file. Update it whenever patterns are added or changed.

### Pipeline

Tests flow through a three-stage automated pipeline:

```
.txt → Parser → .edn → Generator → .clj → Speclj runner
```

1. **Parse:** `clj -M:parse-tests` — reads `.txt` files from `acceptanceTests/`, produces `.edn` intermediate representations in `acceptanceTests/edn/`. Source: `src/empire/acceptance/parser.cljc`.
2. **Generate:** `clj -M:generate-specs` — reads `.edn` files from `acceptanceTests/edn/`, produces Speclj spec files in `generated-acceptance-specs/acceptance/`. Source: `src/empire/acceptance/generator.cljc`.
3. **Run:** `clj -M:spec generated-acceptance-specs/` — executes the generated specs.
4. **Clear:** (allow rm) Delete all generated files. Do not use globs — list each file explicitly in `rm -f` commands, e.g. `rm -f acceptanceTests/edn/army.edn acceptanceTests/edn/fighter.edn ...`

Shorthand to run the full pipeline:
```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

### Rules

- Never modify an acceptance test `.txt` file without explicit permission.
- Always check `.txt` vs `.edn` and `.edn` vs `.clj` modification dates before running acceptance tests; re-parse and/or regenerate if the source is newer.
- Clear context (reset-all-atoms!) before each test.
- Before a push, ask whether acceptance tests should be run.
- On failure, report file name and line number of the first GIVEN line.
- If a directive is ambiguous, report the ambiguity rather than guessing.
- Never modify generated specs in `generated-acceptance-specs/`; only delete and regenerate them from the `.txt` source via the pipeline.
- Generated specs and `.edn` files are gitignored — do not commit them.
- If an acceptance test cannot be translated to a spec, report which test and why to the user. Still generate the spec as a failing test documenting the desired behavior.
- Mock the random number generator (`with-redefs [rand ...]`) for tests with random/non-deterministic conditions.

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

# Acceptance test pipeline
clj -M:parse-tests      # Parse .txt → .edn (acceptanceTests/edn/)
clj -M:generate-specs    # Generate .edn → .clj (generated-acceptance-specs/)

# Clear acceptance tests — list each file explicitly, no globs
# rm -f acceptanceTests/edn/<each>.edn generated-acceptance-specs/acceptance/<each>_spec.clj
```

## Architecture

### Core Game Loop

The game follows a Quil sketch pattern with `setup` → `update-state` → `draw-state` cycle at 30 FPS:

- **ui/core.cljc**: Entry point, Quil sketch setup, keyboard/mouse event routing
- **game_loop.cljc**: Round progression, unit movement execution, production advancement
- **ui/input.cljc**: Keyboard command handling (movement keys qweasdzxc, shift+key for extended movement to map edge, backtick prefix for special commands, production keys, sentry/explore modes)
- **ui/rendering.cljc**: Map drawing, unit display, status area rendering

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

- **movement/movement.cljc**: Unit movement logic, pathfinding, wake conditions, boarding/disembarking
- **player/production.cljc**: City production queue management, unit spawning
- **combat.cljc**: Battle resolution, city conquest attempts
- **player/attention.cljc**: Determines which units/cities need player attention each round
- **config.cljc**: Game constants (colors, key mappings); delegates unit stats to dispatcher
- **containers/helpers.cljc** and **containers/ops.cljc**: Helpers and operations for units that carry other units (transports carry armies, carriers carry fighters, cities have airports)
- **movement/map_utils.cljc**: Coordinate calculations, neighbor finding, screen-to-cell mapping
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

### Message Area Layout

The message area below the map has two sections, each 3 lines high:

**Left side (message display):**
- Line 1: Main game message
- Line 2: Combat log (e.g., "c-3,S-1,S-1. Submarine destroyed.")
- Line 3: Flashing red warnings (conquest failed, fighter destroyed, etc.)

**Right side (status display):**
- Line 1: Round number
- Line 2: "PAUSED" indicator or destination coordinates
- Line 3: Hover info (cell details on mouse-over)
- All text is right-justified against the screen edge
- Width accommodates the longest hover message (~60 chars for city with production, fighters, and orders)

## Coding Guidelines

### Quil Isolation

Functions in `input.cljc` and `rendering.cljc` that do not depend on Quil should be moved to appropriate non-Quil modules. Keep Quil dependencies (e.g., `q/mouse-x`, `q/mouse-y`, drawing functions) isolated to thin wrapper functions, with core logic extracted into testable, Quil-independent functions in modules like `movement.cljc`, `config.cljc`, or `unit-container.cljc`.

### Unused Arguments

Remove unused function arguments before committing. If an argument must be retained for API consistency (e.g., polymorphic dispatch where all implementations share the same signature), prefix it with `_` to indicate it is intentionally unused.

### Large Module Mutation Rule

Before mutating a module larger than 250 lines, explicitly suggest splitting it into smaller modules/functions first. If mutation is still required, keep edits minimal and scoped.

### Parenthesis Safety

LLMs lose track of parentheses in deeply nested s-expressions. Follow these practices to limit errors:

1. **Smaller edits**: Use find-and-replace on specific fragments rather than rewriting whole functions.
2. **Lower cyclomatic complexity**: Simpler functions mean shallower nesting and fewer parens to track. Keep CC <= 5.
3. **Extract before modifying**: If changing a deeply nested branch, extract the logic into a named helper first, then modify in the simpler context.
4. **Run tests immediately**: Run specs after every code change to catch paren errors early.
5. **Read before editing**: Always read the current source before editing — never reconstruct code from memory.

### Test Utilities

When adding new atoms to `atoms.cljc`, also add them to `reset-all-atoms!` in `test_utils.cljc`.

## Plans

Implementation plans are stored in the `plans/` directory. Upon completion, move finished plans into `plans/complete/`.

## VMS Empire Reference

The original VMS Empire C source code is at:
```
/Users/unclebob/projects/clojure/empire/Empire-for-VMS
```

Key files:
- `compmove.c` — Computer movement logic (transport_move at line 877)
- `data.c` — Movement objectives and direction offsets
- `map.c` — Pathfinding, direction selection (vmap_find_dir at line 1065)
- `empire.h` — Constants and data structures

## Debug Log Files

Debug logs are written to the project root directory with the pattern:
```
debug-YYYY-MM-DD-HHMMSS.txt
```

Example: `debug-2026-01-29-070715.txt`

To find the latest debug log:
```bash
ls -la /Users/unclebob/projects/clojure/empire/debug-*.txt | tail -1
```
