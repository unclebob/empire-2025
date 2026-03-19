# Empire - The Wargame of the Century

## Overview

Empire is a simulation of a full-scale war between two emperors: you and the computer. Naturally, there is only room for one, so the object of the game is to destroy the other. The computer plays by the same rules that you do.

This is a Clojure implementation using Quil for graphics rendering. The game features a 100x60 grid map with cities, land, and sea. Cities produce military units which move across the world destroying enemy pieces, exploring, and capturing more cities.

### Victory and Defeat

You win by destroying all computer cities and units. The game ends with "YOU WIN!" and reveals the full map.

You lose if all your cities and units are destroyed. The game ends with "GAME OVER" and reveals the full map.

## Running the Game

    clj -M:run [options] [cols rows]

Options:

- `--help` or `-h` prints usage to stdout and exits.
- `--seed=N` starts the game with a fixed random seed.
- `--headless=N` runs the real game loop without opening the UI, gives the computer a handicap of `N`, exits on game over or round `N`, and prints a one-line progress report every 20 rounds showing computer-map exploration and whether a major invasion is active.
- `--log` appends every computer unit once per round to a timestamped `empire-units<timestamp>.log` file.
- `--handicap=N` lets the computer play `N` full rounds before the player gets the first turn. The default is `50`.

The optional `cols` and `rows` arguments specify the map size. The default is 100 columns by 60 rows. If the specified size exceeds your monitor's dimensions, the game will display the maximum allowable size and exit.

Examples:

    clj -M:run                          # Default 100x60 map, handicap 50
    clj -M:run --help                   # Print usage
    clj -M:run --log                    # Write per-round computer unit snapshots to a log file
    clj -M:run --seed=42                # Default map with fixed seed
    clj -M:run --headless=400           # Run headlessly for up to 400 rounds
    clj -M:run --headless=400 --log     # Run headlessly and log computer units each round
    clj -M:run --handicap=0 80 50       # Smaller 80x50 map, no handicap
    clj -M:run 120 70                   # Larger 120x70 map (if monitor permits)

## Testing

Run all unit specs:

    clj -M:spec

Run all tests (unit specs + acceptance pipeline + AI map access audit):

    clj -M:all-tests

Run component dependency analysis:

    clj -M:check-dependencies dependency-checker.edn

Dependency checker usage, metrics, config, and policy notes are documented in:

- `~/projects/clojure/dependency-checker/README.md`

Run acceptance pipeline (parse scenarios, generate specs, enforce acceptance boundaries, run generated acceptance specs):

    scripts/run-acceptance-tests.sh

Equivalent Clojure alias:

    clj -M:acceptance-tests

### Test Boundaries

- Specs in `spec/**` must not manipulate `empire.atoms` directly.
- Use `empire.test-utils` and runtime/test helper APIs instead of `@atoms/...`, `reset! atoms/...`, or `swap! atoms/...`.
- Boundary enforcement runs in `clj -M:all-tests` via:
  - `clj -M:check-dependencies`
  - `scripts/check-spec-boundary.sh`
  - `scripts/check-acceptance-boundary.sh`
  - `scripts/check-generated-acceptance-boundary.sh`
  - `scripts/check-ai-map-access.sh`

## The World

The world is a rectangle 100 columns by 60 rows (by default). The geography is randomly generated for each game by creating a height map, smoothing it to form natural terrain features, then setting a sea level to achieve approximately 30% land and 70% water. This produces a world of continents, islands, and ocean passages that varies from game to game.

The terrain consists of:

- **Sea** (blue) - Water that ships can traverse
- **Land** (brown) - Terrain that only armies can cross
- **Free Cities** (white) - Uncontrolled cities available for capture
- **Player Cities** (green) - Cities you control
- **Computer Cities** (red) - Cities the computer controls

Cities are scattered across the land, placed with minimum spacing to ensure they are distributed across the continents. The game starts by assigning you one coastal city and the computer one coastal city. The starting cities are placed at least half the map width apart, each with sufficient surrounding land to begin expansion.

The player can direct cities to produce new pieces according to the production cost of the desired unit. The map displays only areas adjacent to your units (fog of war). Information is updated as your units explore.

---

## Units

| Unit            | You | Enemy | Speed | Hits | Strength | Cost |
|-----------------|-----|-------|-------|------|----------|------|
| Army            | A   | a     | 1     | 1    | 1        | 5    |
| Fighter         | F   | f     | 8     | 1    | 1        | 10   |
| Patrol Boat     | P   | p     | 4     | 1    | 1        | 15   |
| Destroyer       | D   | d     | 2     | 3    | 1        | 20   |
| Submarine       | S   | s     | 2     | 2    | 3        | 20   |
| Troop Transport | T   | t     | 2     | 1    | 1        | 30   |
| Aircraft Carrier| C   | c     | 2     | 8    | 1        | 30   |
| Battleship      | B   | b     | 2     | 10   | 2        | 40   |
| Satellite       | Z   | z     | 10    | --   | --       | 50   |

- **Speed** is the number of squares the unit can move per round.
- **Hits** is the amount of damage a unit can take before destruction.
- **Strength** is the damage inflicted per combat round.
- **Cost** is the number of rounds needed for a city to produce the unit.

### Unit Descriptions

**Armies** can only move on land and are the only units that can capture cities. This means you must produce armies to win the game. Armies have a 50% chance of capturing a city when they attack. Armies can be carried by troop transports (up to 6 per transport). While aboard, armies can disembark onto empty land or attack an adjacent city, but cannot attack enemy armies.

**Fighters** move over both land and sea at 8 squares per round. Their high speed makes them ideal for exploring. However, fighters must periodically land at player-owned cities or carriers for refueling. A fighter can travel 32 squares without refueling. Fighters are shot down if they attempt to fly over an enemy city.

**Patrol Boats** are fast but lightly armored. They are useful for patrolling ocean waters and exploring coastlines. They can use coastline-follow mode to automatically explore along shores.

**Destroyers** are fairly heavily armored and reasonably quick to produce. They are useful for hunting enemy transports.

**Submarines** deal triple damage (3 hits per strike instead of 1). Thus submarines can inflict heavy damage against armored ships. A healthy submarine typically defeats a healthy destroyer two-thirds of the time.

**Troop Transports** are the only ships that can carry armies (maximum 6) and are therefore critical for victory. Their weakness means they need protection from stronger ships.

**Aircraft Carriers** are the only ships that can carry fighters (maximum 8). Fighters are refueled when they land on a carrier.

**Battleships** are similar to destroyers but much stronger, with 10 hits and strength 2.

**Satellites** are only useful for reconnaissance. They cannot be attacked. They are launched in a diagonal direction and bounce off map edges for 50 turns. They can see 2 squares in all directions (farther than other units).

### Ship Repair

All ships can dock in a player-owned city. Docked ships are repaired at the rate of 1 hit per turn. Ships that have suffered heavy damage move more slowly. Because of their ability to be repaired, ships with many hits (Carriers and Battleships) have an advantage: after combat, they can return to port for quick repairs.

### Combat Odds

The following table shows the probability that the unit on the left defeats the unit at the top (both undamaged):

|      | AFPT  | D     | S     | C       | B        |
|:----:|-------|-------|-------|---------|----------|
| AFPT | 50.0% | 12.5% | 25.0% | 0.39%   | 0.10%    |
| D    | 87.5% | 50.0% | 25.0% | 5.47%   | 0.54%    |
| S    | 75.0% | 75.0% | 50.0% | 31.3%   | 6.25%    |
| C    | 99.6% | 94.5% | 68.7% | 50.0%   | 4.61%    |
| B    | 99.9% | 99.5% | 93.8% | 95.4%   | 50.0%    |

Damaged ships have significantly worse odds. A healthy submarine has a 25% chance against a battleship with one hit of damage, and 50% against a carrier with two hits of damage.

---

## User Manual

### Unit Modes

Units operate in different modes:

- **Awake** (white) - Waiting for orders; you will be prompted to move them
- **Sentry** (pink) - Sleeping; will wake if an enemy appears nearby
- **Explore** (light green) - Auto-exploring toward unexplored territory
- **Coastline-Follow** (light green) - Ships automatically following coastlines
- **Moving** (black) - Executing movement orders toward a destination

### Movement Keys

Movement uses the keys surrounding `S` on the keyboard:

```
    Q W E
    A   D
    Z X C
```

Each key moves in its relative direction:
- `Q` - Northwest
- `W` - North
- `E` - Northeast
- `A` - West
- `D` - East
- `Z` - Southwest
- `X` - South
- `C` - Southeast

**Extended Movement:** Hold Shift with a direction key (`Q W E A D Z X C`) to set a destination at the map edge in that direction. The unit will automatically move toward that destination each turn.

**Mouse:** When a unit needs attention, click on an adjacent cell to move there, or click on a distant cell to set it as a destination.

### Unit Commands

When a unit needs attention (highlighted on map):

| Key | Action |
|-----|--------|
| `q w e a d z x c` | Move one square in that direction |
| `Q W E A D Z X C` | Set destination to map edge in that direction |
| `Space` | Skip this turn (wait in place) |
| `s` | Set to Sentry mode (sleep until enemy appears) |
| `l` | Set to Explore/Lookaround mode |
| `u` | Unload - wake armies on transport or fighters on carrier |

**Explore Mode (`l`):**
- Armies enter explore mode and automatically move toward unexplored areas
- Transports and Patrol Boats near coast enter coastline-follow mode
- Armies aboard transports disembark to explore

### City Production

When a city needs attention (no production set):

| Key | Unit Produced |
|-----|---------------|
| `a` | Army |
| `f` | Fighter |
| `z` | Satellite |
| `t` | Transport |
| `p` | Patrol Boat |
| `d` | Destroyer |
| `s` | Submarine |
| `c` | Carrier |
| `b` | Battleship |
| `x` | Cancel production |
| `Space` | Skip (leave unchanged) |

**Note:** Naval units (ships) can only be produced in coastal cities.

### Marching Orders and Flight Paths

You can set standing orders for cities so that newly produced units automatically move to a destination:

| Key | Action |
|-----|--------|
| `.` | Set destination marker at mouse cursor |
| `m` | Set marching orders on city/transport under mouse to destination |
| `f` | Set flight path on city/carrier under mouse to destination |
| `l` | Set city under mouse marching orders to "lookaround" (explore mode) |
| `Q W E A D Z X C` | Set city under mouse marching orders to map edge in that direction |

**Note:** Be mindful of the mouse position when you use these commands.  They apply only the cell under the mouse. For example, if a city is requesting production and you hit `a` while the mouse is on the city, you are setting the marching orders and not telling the city to produce armies.

### Waypoints

| Key | Action |
|-----|--------|
| `*` | Create or remove waypoint at mouse cursor |

Waypoints are markers on land cells. When a city has marching orders to a waypoint, the waypoint can have its own destination set, creating chains of movement.  An army entering a waypoint with marching orders will give those 
marching order to the army.  

### Game Control

| Key | Action |
|-----|--------|
| `P` | Pause/unpause the game |
| `Space` (when paused) | Advance one round |
| `!` | Save game |
| `^` | Open load game menu |
| `Escape` | Close load menu |


---

## Debug Mode

### Backtick Commands

Press backtick (`` ` ``) followed by a key to access debug commands. These commands add units at the mouse cursor position:

| Command | Action |
|---------|--------|
| `` `A `` | Add player Army |
| `` `F `` | Add player Fighter |
| `` `Z `` | Add player Satellite |
| `` `T `` | Add player Transport |
| `` `P `` | Add player Patrol Boat |
| `` `D `` | Add player Destroyer |
| `` `S `` | Add player Submarine |
| `` `C `` | Add player Carrier |
| `` `B `` | Add player Battleship |
| `` `a `` | Add computer Army |
| `` `f `` | Add computer Fighter |
| `` `z `` | Add computer Satellite |
| `` `t `` | Add computer Transport |
| `` `p `` | Add computer Patrol Boat |
| `` `d `` | Add computer Destroyer |
| `` `s `` | Add computer Submarine |
| `` `c `` | Add computer Carrier |
| `` `b `` | Add computer Battleship |
| `` `o `` | Own city (convert city under mouse to player-owned) |

### Other Debug Keys

| Key | Action |
|-----|--------|
| `+` | Cycle map view (player map / computer map / actual map) |

### Debug Log Creation

To create a debug dump of a map region:

1. Hold **Ctrl** (or **Cmd** on Mac, or **Alt**)
2. Click and drag to select a rectangular region on the map
3. Release to generate the debug file

The debug log is written to the project directory with the filename pattern:
```
debug-YYYY-MM-DD-HHMMSS.txt
```

The debug log contains:
- Round number and timestamp
- Global game state (cells needing attention, player items, etc.)
- Recent game actions (last 50)
- Units in coastline-follow mode
- Player unit movement history (last 20 rounds)
- Full cell data for the selected region from all three maps (game-map, player-map, computer-map)

---

## Appendix: History of Empire

### Origins (1970s)

Empire was originally written outside of Digital Equipment Corporation, probably at a university. The earliest known version was written in FORTRAN for the TOPS-10/20 operating system. The original authors listed in early documentation are **Mario DeNobili** and **Thomas N. Paulson**.

### VMS Era (1979-1986)

The game was ported to DEC's VAX/VMS from the TOPS-10/20 FORTRAN sources around fall 1979. Because the game ran on VMS machines for so long, this version became known as "VMS Empire." Support for different terminal types was added by **Craig Leres**.

### Berkeley and C Conversion (1986)

**Ed James** obtained the sources at Berkeley and converted portions of the code to C, primarily to use the curses library for screen handling. He published his modified sources on the net in December 1986.

### Complete C Rewrite (1987)

In early 1987, **Chuck Simmons** at Amdahl reverse-engineered the program and wrote a version completely in C. He used structured programming with defined constants, attempting to make the code flexible and easy to modify. The algorithms were completely new, command names were changed to be more mnemonic, and new commands were implemented. As Simmons noted:

> "My hope is that the commented C sources I have written will prove far easier to modify and enhance than the original FORTRAN sources."

### Later Enhancements (1990s-2000s)

**Eric S. Raymond** colorized the code and added speed optimizations and a save-interval option. **Michael Self** corrected bugs in the victory-odds probability table. **James T. Jordan** contributed speedups, ANSI prototypes, and code cleanup.

### Clojure Implementation (2025)

This version is a complete reimplementation in Clojure using the Quil graphics library. It features:

- Modern graphical interface (no terminal/curses dependency)
- Fog of war with visible map tracking
- Automated unit behaviors (explore, coastline-follow, sentry)
- Marching orders and flight paths for cities
- Waypoint system for complex unit routing
- Save/load functionality
- Debug tools for game state inspection
- Comprehensive test suite

The implementation preserves the core gameplay mechanics and the retro feel while modernizing the interface and adding quality-of-life features for the 21st century.

---

## Files

- **saves/** - Directory containing saved game files (`.edn` format)
- **debug-*.txt** - Debug dump files generated during gameplay

## Diagram Generation

Render Mermaid diagrams to PDF with:

```bash
scripts/mermaid-to-pdf docs/uml/components.md
```

You can also pass an explicit output path:

```bash
scripts/mermaid-to-pdf docs/uml/components.md docs/uml/components.pdf
```

Rotate the final PDF page(s):

```bash
scripts/mermaid-to-pdf --rotate 90 docs/uml/components.md docs/uml/components.pdf
```

For larger, more readable diagrams:

```bash
scripts/mermaid-to-pdf --pdf-fit --font-size 24 --width 2600 --height 1800 docs/uml/components.md docs/uml/components.pdf
```

Notes:
- Input may be either a Mermaid markdown file (`.md`, first ```mermaid block is used) or a raw Mermaid file (`.mmd`).
- The script uses `mmdc` if installed, otherwise falls back to `npx @mermaid-js/mermaid-cli`.
- `--rotate` requires `qpdf` to be installed.
- When authoring Mermaid, quote text labels, especially those with brackets. Example: `A["Text with [brackets]"]`.

## License

This implementation is based on the classic Empire game. The original C version by Chuck Simmons was released under the terms described in the COPYING file distributed with that version.

## Authors

- **Original concept:** Mario DeNobili and Thomas N. Paulson
- **VMS port and terminal support:** Craig Leres
- **C/curses conversion:** Ed James
- **C rewrite:** Chuck Simmons
- **Colorization and enhancements:** Eric S. Raymond
- **Probability table corrections:** Michael Self
- **Clojure implementation:** Robert C. Martin (2025)
