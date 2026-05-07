# dry4clj Duplicate Report: src/empire/game

Generated from:

```bash
clj -M:dry4clj src/empire/game
```

## Current Candidate: Clearing UI State

Score: `0.86`

Locations:

- `src/empire/game/loop/core.cljc:35-40`
- `src/empire/game/save_load/menu.cljc:46-50`

These functions both clear small groups of UI-related atoms by writing default values. The structure is similar, but the state keys and domain concepts differ.

```clojure
;; src/empire/game/loop/core.cljc:35-40
(defn item-processed
  "Called when user input has been processed for current item.
   Victory check happens in item-processing/process-player-items-batch."
  []
  (sa/write-state! :waiting-for-input false)
  (sa/write-state! :cells-needing-attention []))
```

```clojure
;; src/empire/game/save_load/menu.cljc:46-50
(defn close-load-menu!
  []
  (sa/write-state! :load-menu-open false)
  (sa/write-state! :load-menu-files [])
  (sa/write-state! :load-menu-hovered nil))
```

Recommendation: leave this one alone for now. The shared shape is only "write several default values", and extracting a generic reset helper would hide the specific intent at the call sites.

## Reduced Candidates

### Visibility Map Updates

Former locations:

- `src/empire/game/loop/advance.cljc:10-17`
- `src/empire/game/loop/advance.cljc:19-26`

Reduced by extracting `update-visible-map`, parameterized by visible map key and owner.

### Batch Advancement Loop

Former locations:

- `src/empire/game/loop/advance.cljc:63-75`
- `src/empire/game/loop/core.cljc:25-38`

Reduced by extracting `run-advance-game-batch` into `advance.cljc`. The `core.cljc` facade still defines `advance-game-batch` locally and passes its own `advance-game` var into the shared runner, preserving existing `with-redefs` behavior in tests.

### Item List Builders

Former locations:

- `src/empire/game/loop/round_start.cljc:67-75`
- `src/empire/game/loop/round_start.cljc:90-101`

Reduced by extracting `owned-item-coordinates`, shared by both player and computer item builders. The computer builder still applies its processing-order sort after collecting owned coordinates.
