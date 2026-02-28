# Acceptance Test Framework

This directory contains acceptance tests written in a human-readable Given/When/Then format. These tests describe game behavior at a high level and are automatically translated into executable Speclj specs.

## Pipeline Overview

The acceptance test system uses a three-stage automated pipeline:

```
.txt file → Parser → .edn IR → Generator → .clj spec → Speclj runner
```

1. **Parse:** `clj -M:parse-tests` reads `.txt` files from this directory and produces `.edn` intermediate representations in `acceptanceTests/edn/`
2. **Generate:** `clj -M:generate-specs` reads `.edn` files and produces Speclj spec files in `generated-acceptance-specs/acceptance/`
3. **Run:** `clj -M:spec generated-acceptance-specs/` executes the generated specs

**Full pipeline shorthand:**
```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

## Example Tests

### Simple Unit Mode Change

```
;===============================================================
; Army put to sentry mode.
;===============================================================
GIVEN game map
  A#
GIVEN A is waiting for input.

WHEN the player presses s.

THEN A has mode sentry.
```

### Combat and City Conquest

```
;===============================================================
; Army conquers free city.
;===============================================================
GIVEN game map
  A+
GIVEN A is waiting for input.

WHEN the player presses d and wins the battle.

THEN cell [1 0] is a player city.
```

### Fighter Fuel Management

```
;===============================================================
; Fighter consumes fuel when moving over non-city terrain.
;===============================================================
GIVEN game map
  F~=
F has fuel 32.
GIVEN F is waiting for input.

WHEN the player presses D and the game advances until F is waiting for input.

THEN F is at =.
THEN F has fuel 30.
THEN the attention message contains :hit-edge.
```

### Multi-step Assertions

```
;===============================================================
; Fighter bingo wakes when fuel low and city nearby.
;===============================================================
GIVEN game map
  OF
F is sentry with fuel 9.

WHEN a new round starts and F is waiting for input.

THEN F has mode awake.
and the attention message for F contains :fighter-bingo.
```

---

## Legend: Map Characters

### Terrain

| Char | Meaning |
|:----:|---------|
| `~`  | Sea cell |
| `#`  | Land cell |
| `=`  | Referenceable sea (can be used as target position) |
| `%`  | Referenceable land (can be used as target position) |
| ` `  | Unexplored (space) |
| `.`  | Unexplored (dot) |
| `-`  | Unexplored (dash) |

### Cities

| Char | Meaning |
|:----:|---------|
| `+`  | Free (neutral) city |
| `O`  | Player city |
| `X`  | Computer city |

### Player Units (uppercase)

| Char | Unit Type |
|:----:|-----------|
| `A`  | Army |
| `F`  | Fighter |
| `J`  | Fighter (alias) |
| `T`  | Transport |
| `D`  | Destroyer |
| `C`  | Carrier |
| `S`  | Submarine |
| `B`  | Battleship |
| `P`  | Patrol boat |
| `V`  | Satellite |

### Computer Units (lowercase)

| Char | Unit Type |
|:----:|-----------|
| `a`  | Army |
| `f`  | Fighter |
| `j`  | Fighter (alias) |
| `t`  | Transport |
| `d`  | Destroyer |
| `c`  | Carrier |
| `s`  | Submarine |
| `b`  | Battleship |
| `p`  | Patrol boat |
| `v`  | Satellite |

`J/j` are fighter aliases. They are useful in tests that want a fighter marker on sea-style rows while still behaving as a fighter.

---

## Legend: Direction Keys

```
  q w e
  a   d
  z x c
```

| Key | Direction |
|:---:|-----------|
| `q` | Northwest |
| `w` | North |
| `e` | Northeast |
| `a` | West |
| `d` | East |
| `z` | Southwest |
| `x` | South |
| `c` | Southeast |

**Uppercase** direction keys (D, W, etc.) mean extended movement to the map edge.

---

## Legend: GIVEN Directives

### Map Setup

```
GIVEN game map              ; Main authoritative map
  <map rows>

GIVEN player map            ; Fog-of-war for player
  <map rows>

GIVEN computer map          ; Fog-of-war for computer
  <map rows>
```

### Unit Properties

```
GIVEN A is awake.
GIVEN A is sentry.
GIVEN A is explore.
GIVEN A is moving.
GIVEN F has fuel 20.
GIVEN F has fuel 20 and is in mode moving.
GIVEN D has hits 1.
GIVEN T is sentry with three armies.
GIVEN T is awake with two armies.
GIVEN C has three fighters.
GIVEN C has no fighters.
GIVEN C has no awake fighters.
GIVEN T has mission idle.
GIVEN T has escort destroyer.
GIVEN T with been-to-sea true.
GIVEN T with country-id 1.
GIVEN T has patrol-country-id 1.
```

### Waiting for Input

```
GIVEN A is waiting for input.    ; Sets up unit for player attention
```

This is shorthand for: set mode awake, build player map, set player-items, process items batch.

### Targets and Destinations

```
GIVEN A's target is +.
GIVEN destination [3 7].
```

### Production

```
GIVEN production at O is army.
GIVEN production at O is fighter with 3 rounds remaining.
GIVEN no production.
```

### Cell Properties

```
GIVEN cell [0 0] has awake-fighters 1 and fighter-count 1.
GIVEN cell [1 2] has spawn-orders marching-orders.
GIVEN cell [1 2] has flight-orders [11 0].
```

### Game State

```
GIVEN round 5.
GIVEN the game is waiting for input.
GIVEN player units A, T, O.
```

### Stubs (for computer logic)

```
GIVEN computer controls 12 cities.
GIVEN a valid carrier position exists.
```

---

## Legend: WHEN Directives

### Keyboard Input

```
WHEN the player presses d.           ; Single key press
WHEN the player presses D.           ; Extended movement (uppercase)
WHEN the player presses space.       ; Space key
WHEN the player types d d d.         ; Multiple key presses
```

### Combat

```
WHEN the player presses d and wins the battle.
WHEN the player presses d and loses the battle.
```

### Mouse Input

```
WHEN the player clicks cell [3 5].
WHEN the mouse is at cell [3 5] and the player presses m.
WHEN the mouse is at cell [3 5] and backtick then a.
```

### Game Advancement

```
WHEN the game advances.              ; advance-game
WHEN the game advances one batch.    ; advance-game-batch
WHEN a new round starts.             ; start-new-round
WHEN the next round begins.          ; start-new-round
WHEN player items are processed.     ; process-player-items-batch
WHEN production updates.             ; update-production
```

### Visibility and Computer

```
WHEN visibility updates.
WHEN cell visibility updates for F.
WHEN production for O is evaluated.
WHEN computer transport t is processed.
WHEN 5 computer rounds pass.
```

### Combined Patterns

```
WHEN A is waiting for input and the player presses d.
WHEN a new round starts and F is waiting for input.
WHEN the player presses d and F is waiting for input.
WHEN the player presses D and the game advances until F is waiting for input.
```

---

## Legend: THEN Directives

### Unit Position and State

```
THEN A is at [0 2].
THEN A is at [0 2] in mode moving.
THEN A has fuel 19.
THEN A has mode sentry.
THEN A has mode awake.
THEN T has three armies.
THEN T has no armies.
THEN V has 50 turns remaining.
THEN T has no mission.
THEN there is no A on the map.
THEN no unit at [1 3].
THEN there is an A at [2 3].
THEN A occupies the fighter cell.
```

### Messages

Three message areas: **attention** (line 1), **turn** (line 2), **error** (line 3).

**Literal string:**
```
THEN the attention message contains "fuel:20".
THEN the attention message is "City needs attention".
THEN there is no attention message.
```

**Config key (preferred):**
```
THEN the attention message contains :army-found-city.
THEN the error message is :conquest-failed.
THEN the error message contains :fighter-crashed.
```

**Format function:**
```
THEN the turn message is (fmt :marching-orders-set 3 7).
THEN the error message is (fmt :coastal-city-required "transport").
```

### Cell Assertions

```
THEN cell [0 0] is a city.
THEN cell [1 0] is a player city.
THEN cell [1 0] is a computer city.
THEN cell [1 0] is sea.
THEN cell [0 0] has spawn-orders lookaround.
THEN cell [0 0] has flight-orders [11 0].
```

### Game State

```
THEN production at O is army.
THEN there is no production at O.
THEN production at O is army with 3 rounds remaining.
THEN the game is waiting for input.
THEN the game is not waiting for input.
THEN the game is paused.
THEN round is 5.
THEN destination is [3 7].
```

### Visibility

```
THEN the player can see [1 0].
THEN the player cannot see [5 0].
THEN there are 3 computer armies on the map.
```

### Timing Modifiers

```
THEN at next round A will be at %.
THEN at the next step A will be at %.
THEN after one step there is an F at %.
THEN eventually A will be at %.
```

### Containers

```
THEN C has one fighter aboard.
THEN O has one fighter in its airport.
THEN C has no fighters.
THEN C has three awake fighters.
```

### Continuation

```
THEN A wakes up and asks for input,
and the attention message contains "fuel:20".
```

---

## Map Coordinate System

Maps use visual row-major layout but are indexed as `[column, row]`:

```
GIVEN game map
  ~T#    ; row 0: col 0=sea, col 1=transport, col 2=land
  ~~~    ; row 1: col 0=sea, col 1=sea, col 2=sea
```

Visually:
```
       x=0  x=1  x=2
  y=0   ~    T    #
  y=1   ~    ~    ~
```

Position `[1 0]` is where `T` is located (column 1, row 0).

---

## Writing Tips

1. **Use referenceable cells** (`=` for sea, `%` for land) as movement targets instead of hard-coded coordinates
2. **Use config keys** (`:army-found-city`) instead of literal strings for message assertions
3. **Use "waiting for input"** to set up unit attention instead of manual mode/player-items setup
4. **Comments** start with `;` and are preserved in generated spec descriptions
5. **Leading whitespace** in map lines is automatically stripped

---

## Appendix: Generated Spec Example

The following acceptance test:

```
;===============================================================
; Army put to sentry mode.
;===============================================================
GIVEN game map
  A#
GIVEN A is waiting for input.

WHEN the player presses s.

THEN A has mode sentry.
```

Generates this Speclj spec:

```clojure
(ns acceptance.army-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-city reset-all-atoms! message-matches?
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(describe "army.txt"

  (it "army.txt:7 - Army put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "A"))))))
```

Key features of the generated spec:
- **Test name** includes source file and line number (`army.txt:7`)
- **Comment text** becomes part of the test description
- **`reset-all-atoms!`** clears state before each test
- **`build-test-map`** converts map strings to game map structure
- **`set-test-unit`** configures unit properties
- **"waiting for input"** expands to full attention setup code
- **Quil mocking** (`with-redefs [q/mouse-x ...]`) handles UI dependencies
- **`should=`** and other Speclj assertions verify expected state
