## Permissions

allow all standard unix tools, git, clj, and clojure.


## Acceptance Tests

Acceptance tests are `.txt` files in `acceptanceTests/` in Given/When/Then format.
The detailed directive catalog and translation reference is in `plans/permanent/acceptance-test-framework.md`.
Only read that file when modifying or debugging the parser (`src/empire/acceptance/parser.cljc`) or generator (`src/empire/acceptance/generator.cljc`) - not when writing acceptance tests or running the pipeline.
The parser pattern catalog is in `plans/permanent/parser-pattern-catalog.md`. Read it BEFORE loading any `src/empire/acceptance/parser/*.cljc` source file. Update it whenever patterns are added or changed.

### Pipeline

Tests flow through a three-stage automated pipeline:

```bash
.txt -> Parser -> .edn -> Generator -> .clj -> Speclj runner
```

1. **Parse:** `clj -M:parse-tests` - reads `.txt` files from `acceptanceTests/`, produces `.edn` intermediate representations in `acceptanceTests/edn/`. Source: `src/empire/acceptance/parser.cljc`.
2. **Generate:** `clj -M:generate-specs` - reads `.edn` files from `acceptanceTests/edn/`, produces Speclj spec files in `generated-acceptance-specs/acceptance/`. Source: `src/empire/acceptance/generator.cljc`.
3. **Run:** `clj -M:spec generated-acceptance-specs/` - executes the generated specs.
4. **Clear:** (allow rm) Delete all generated files. Do not use globs - list each file explicitly in `rm -f` commands, e.g. `rm -f acceptanceTests/edn/army.edn acceptanceTests/edn/fighter.edn ...`

Shorthand to run the full pipeline:

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

### Rules

- Never modify an acceptance test `.txt` file without explicit permission.
- Always run the full acceptance test pipeline.
- Include (reset-all-atoms!) before each test.
- Generated specs and `.edn` files are gitignored - do not commit them.
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
clj -M:parse-tests      # Parse .txt -> .edn (acceptanceTests/edn/)
clj -M:generate-specs   # Generate .edn -> .clj (generated-acceptance-specs/)
```

## Architecture

### Core Game Loop

The game follows a Quil sketch pattern with `setup` -> `update-state` -> `draw-state` cycle at 30 FPS:

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

### Large Module Mutation Rule

When the user requests a check on module size use the mutator's --scan mode to measure the number of mutation sites for the modules.  Inform the user of the those over 50 and offer to split them.

### Workflow
 * For every new or changed behavior, write acceptance scenarios.  Ask before changing existing scenarios.  Confirm the scenarios fail.  Write failing unit tests and make them pass until scenarios pass.
 * Before running changed specs use speclj-structure-check to ensure the structure.
 * For every changed module run crap and refactor until crap is 8 or less.
 * For every changed module run differential mutation tests, one module at a time, cover ucovered sites and kill survivors before running the next.  Set max-workers to 3. 
 * Differential mutation updates the manifest automatically. Do not run `--update-manifest` afterward unless there is a separate reason.
 * `--update-manifest` should not run coverage at all; it is a manifest rewrite only.
 * Never run crap or mutate concurrently with any other command, including another crap or mutate run.
 * If you have a batch of mutation runs, let the first run generate fresh coverage, then use `--reuse-lcov` for the rest of the batch to save time.
 * If an unchanged file with a manifest is split, do not copy the parent manifest into the daughters.
 * Run tests first.
 * If green, update daughter manifests.
 * Then continue with CRAP and differential mutation, which should be a no-op for a semantics-preserving split.

### Test Utilities

When adding new atoms, remember to update `reset-all-atoms!`.

## Plans

Implementation plans are stored in the `plans/` directory. Upon completion, move finished plans into `plans/complete/`.

## Tools In `deps.edn`

- `io.github.unclebob/clj-mutate`
- `io.github.unclebob/speclj-structure-check`
- `io.github.unclebob/crap4clj`
