# Architecture Cohesion Improvements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce coupling and eliminate boilerplate across 5 coordinated module splits: state-access extraction (58 files), config split (5 sub-modules), round_setup split (4 sub-modules), actions split (3 sub-modules), and computer/core contract/impl split.

**Architecture:** Pure refactoring — no behavior changes. Extract shared boilerplate into a state-access module first (all subsequent splits depend on it), then perform 4 independent module splits. Each split moves functions to sub-modules and updates requires.

**Tech Stack:** Clojure 1.12, Speclj (BDD test framework), custom dependency checker, architecture boundary script.

---

## Validation Commands

After every task that changes code:
```bash
clj -M:spec                    # all unit tests (3393+ examples)
clj -M:all-tests-fast          # unit tests + boundary checks + acceptance
```

After every spec file change:
```bash
clj -M:spec-structure-check <changed-spec-file>
```

---

## Task 1: Create `application/state_access.cljc`

**Files:**
- Create: `src/empire/application/state_access.cljc`
- Test: `spec/empire/application/state_access_spec.clj`

**Step 1: Write the spec**

```clojure
(ns empire.application.state-access-spec
  (:require [speclj.core :refer :all]
            [empire.application.state-access :as sa]
            [empire.test-utils :as tu]))

(describe "state-access"
  (before (tu/reset-all-atoms!))

  (context "current-world"
    (it "returns the game map"
      (tu/set-test-world! (tu/build-grid ["..."]))
      (should= (tu/read-world) (sa/current-world))))

  (context "read-state / write-state!"
    (it "reads and writes runtime state"
      (sa/write-state! :round-number 42)
      (should= 42 (sa/read-state :round-number))))

  (context "update-state!"
    (it "applies f to current value"
      (sa/write-state! :round-number 10)
      (sa/update-state! :round-number inc)
      (should= 11 (sa/read-state :round-number))))

  (context "update-world!"
    (it "applies f to the world and saves"
      (tu/set-test-world! (tu/build-grid ["..."]))
      (sa/update-world! assoc-in [0 0 :test-key] :test-val)
      (should= :test-val (get-in (sa/current-world) [0 0 :test-key])))))
```

**Step 2: Run spec-structure-check and verify spec fails**

```bash
clj -M:spec-structure-check spec/empire/application/state_access_spec.clj
clj -M:spec spec/empire/application/state_access_spec.clj
```
Expected: FAIL — namespace not found.

**Step 3: Write the implementation**

```clojure
(ns empire.application.state-access
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]))

(def ^:private ctx (delay (app-runtime/default-state-ctx)))

(defn state-ctx [] @ctx)

(defn current-world [] ((:load-world @ctx)))

(defn update-world! [f & args]
  (apply app-state/update-world! @ctx f args))

(defn read-state [k] ((:read-runtime-state @ctx) k))

(defn write-state! [k v] ((:write-runtime-state! @ctx) k v))

(defn update-state! [k f & args]
  (write-state! k (apply f (read-state k) args)))
```

**Step 4: Run spec and verify it passes**

```bash
clj -M:spec spec/empire/application/state_access_spec.clj
```
Expected: PASS.

**Step 5: Run full test suite**

```bash
clj -M:all-tests-fast
```

**Step 6: Commit**

```bash
git add src/empire/application/state_access.cljc spec/empire/application/state_access_spec.clj
git commit -m "Add application/state-access module for shared state helpers"
```

---

## Task 2: Migrate `game_loop/round_setup.cljc` to state-access

This is the pilot migration — one file at a time to establish the pattern.

**Files:**
- Modify: `src/empire/game_loop/round_setup.cljc`
- Test: `spec/empire/game_loop/round_setup_spec.clj` (660 lines, no changes expected)

**Step 1: Update requires in round_setup.cljc**

Replace:
```clojure
(:require [empire.application.runtime :as app-runtime]
          [empire.application.state :as app-state]
```
With:
```clojure
(:require [empire.application.state-access :as sa]
```
(Keep `app-runtime` only if it's used beyond state-ctx. In this file, it's only used for state-ctx — remove it. Keep `app-state` only if used beyond `update-world!` — it's not, remove it.)

**Step 2: Remove the boilerplate block (lines 16-48)**

Delete the private `state-ctx`, `current-world`, `update-game-map!`, `read-runtime-state`, `write-runtime-state!`, `update-runtime-state!`, `set-error-message!`, and `world-ref` definitions.

**Step 3: Replace all calls**

In the remaining functions:
- `(current-world)` → `(sa/current-world)`
- `(update-game-map! ...)` → `(sa/update-world! ...)`
- `(read-runtime-state k)` → `(sa/read-state k)`
- `(write-runtime-state! k v)` → `(sa/write-state! k v)`
- `(update-runtime-state! k f ...)` → `(sa/update-state! k f ...)`
- `(set-error-message! msg dur)` → keep as local private fn, rewritten to use `sa/write-state!`
- `(world-ref world)` → keep as local private fn `(atom world)`, unchanged

**Step 4: Run tests**

```bash
clj -M:spec spec/empire/game_loop/round_setup_spec.clj
```
Expected: all 660-line spec passes unchanged.

**Step 5: Run full suite**

```bash
clj -M:all-tests-fast
```

**Step 6: Commit**

```bash
git add src/empire/game_loop/round_setup.cljc
git commit -m "Migrate round_setup.cljc to state-access helpers"
```

---

## Task 3: Migrate remaining files to state-access (batch)

**Scope:** All ~57 remaining files with the `(delay (app-runtime/default-state-ctx))` pattern.

**Exclusions:**
- `player/commands/actions.cljc` — uses explicit ctx-passing pattern (Pattern C), leave as-is
- `movement/*.cljc` — uses `movement-context` (Pattern B), leave as-is
- `application/impl/*.cljc` — bootstrap wiring, may need `app-runtime` directly

**Step 1: Migrate in batches of 5-8 files by subsystem**

For each batch:
1. Update requires: add `empire.application.state-access :as sa`, remove `empire.application.runtime` and `empire.application.state` if only used for boilerplate.
2. Delete boilerplate block (state-ctx, current-world, update-game-map!, read-runtime-state, write-runtime-state!).
3. Find-replace all calls to use `sa/` prefix.
4. Keep module-specific helpers (e.g., `movement-port`, `set-error-message!`, `world-ref`, `rebuild-refueling-caches!`) as local fns rewritten to call `sa/` helpers.
5. Run targeted specs for that batch.
6. Run `clj -M:all-tests-fast`.
7. Commit the batch.

**Suggested batch order:**
1. `player/` — `orders.cljc`, `production.cljc`, `attention.cljc`, `commands.cljc` (4 files)
2. `game_loop/` — `game_loop.cljc`, `item_processing.cljc` (2 files)
3. `ui/` — `ui/util/input/actions.cljc`, `ui/util/input/dispatch.cljc`, `ui/util/core.cljc`, `ui/util/rendering/display.cljc`, `ui/quil/core.cljc`, `ui/quil/rendering/map.cljc`, `ui/quil/rendering/messages.cljc`, `ui/quil/rendering/overlay.cljc` (8 files)
4. `containers/` + `domain/` — `containers/ops.cljc`, `domain/model/impl/combat_runtime.cljc`, `units/impl/satellite.cljc` (3 files)
5. `computer/core.cljc` + `computer/movement.cljc` + `computer.cljc` (3 files)
6. `computer/army*.cljc` — `army.cljc`, `army/assignment.cljc`, `army/combat.cljc`, `army/exploration.cljc`, `army/movement.cljc`, `army/transport.cljc`, `army/coastal.cljc` (7 files)
7. `computer/fighter*.cljc` — `fighter.cljc`, `fighter_movement.cljc`, `fighter_exploration.cljc` (3 files)
8. `computer/ship*.cljc` — `ship.cljc`, `ship_core.cljc`, `ship_carrier.cljc`, `ship_carrier_group.cljc`, `ship_escort.cljc`, `ship_patrol.cljc` (6 files)
9. `computer/transport*.cljc` — `transport.cljc`, `transport_core.cljc`, `transport_loading.cljc`, `transport_sailing.cljc`, `transport_targeting.cljc`, `transport_unloading.cljc` (6 files)
10. `computer/threat*.cljc` + `computer/production*.cljc` + `computer/land*.cljc` — `threat_response.cljc`, `threat.cljc`, `production.cljc`, `production/decisions.cljc`, `production/stats.cljc`, `land_objectives.cljc`, `land_ho.cljc` (7 files)
11. Remaining — `debug/logging.cljc`, `debug/dump.cljc`, `debug/dump/output.cljc`, `save_load.cljc`, `init.cljc`, `acceptance/harness.cljc`, `test_utils.cljc`, `application/impl/unit_stamping.cljc`, `application/impl/city_production.cljc` (9 files)

**Step 2: After all batches, verify no boilerplate remains**

```bash
rg '\(delay \(app-runtime/default-state-ctx\)\)' src/empire --glob '!application/state_access.cljc'
```
Expected: 0 matches (excluding movement-context files which use a different pattern).

**Step 3: Commit**

Each batch gets its own commit with message like:
```
Migrate player/ modules to state-access helpers
```

---

## Task 4: Add boundary guard for state-access

**Files:**
- Modify: `scripts/check-architecture-boundaries.sh`

**Step 1: Add guard after the legacy-ports check (line 45)**

```bash
# After all boilerplate migration, no file outside application/ should
# create its own delay of default-state-ctx
state_ctx_delay_hits="$(rg -n 'delay.*default-state-ctx' src/empire --glob '!application/state_access.cljc' || true)"
if [[ -n "$state_ctx_delay_hits" ]]; then
  echo "Architecture boundary violation: state-ctx delay should only exist in application/state_access.cljc:"
  printf '%s\n' "$state_ctx_delay_hits"
  exit 1
fi
```

**Step 2: Run boundary check**

```bash
bash scripts/check-architecture-boundaries.sh
```
Expected: PASS.

**Step 3: Run full suite to confirm guard runs in pipeline**

```bash
clj -M:all-tests-fast
```

**Step 4: Commit**

```bash
git add scripts/check-architecture-boundaries.sh
git commit -m "Add boundary guard: state-ctx delay only in state-access module"
```

---

## Task 5: Split `config.cljc` into 5 sub-modules + facade

**Files:**
- Create: `src/empire/config/rendering.cljc`
- Create: `src/empire/config/generation.cljc`
- Create: `src/empire/config/ai.cljc`
- Create: `src/empire/config/keys.cljc`
- Create: `src/empire/config/messages.cljc`
- Modify: `src/empire/config.cljc` (becomes facade)
- Modify: `spec/empire/config_spec.clj` (update requires if needed)

**Step 1: Create `config/rendering.cljc`**

Move lines 8-41 (cell-size through width fractions) and lines 105-129 (colors) and lines 257-299 (color functions) here.

```clojure
(ns empire.config.rendering)

;; Cell dimensions, fonts, layout constants
(def cell-size [11 16])
(def text-font-name "Courier New")
(def text-font-size 18)
(def cell-char-font-name "CourierNewPS-BoldMT")
(def cell-char-font-size 12)
(def text-area-rows 3)
(def text-area-gap 7)
(def cell-char-x-offset 2)
(def cell-char-y-offset 12)
(def msg-left-padding 10)
(def msg-line-1-y 10)
(def msg-line-2-y 26)
(def msg-line-3-y 42)
(def msg-separator-offset 4)
(def game-info-width-fraction 0.375)
(def debug-width-fraction 0.25)
(def game-status-width-fraction 0.375)

;; Colors
(def cell-colors
  {:player-city [0 255 0]
   :computer-city [255 0 0]
   :free-city [255 255 255]
   :unexplored [0 0 0]
   :land [139 69 19]
   :sea [0 191 255]})

(def land-colors
  [[139 69 19] [160 82 45] [120 66 18] [180 100 50]
   [101 67 33] [170 120 60] [150 75 0] [133 94 66]])

(def production-color [128 128 128])
(def waypoint-color [0 255 0])
(def awake-unit-color [255 255 255])
(def sleeping-unit-color [0 0 0])
(def sentry-unit-color [255 128 128])
(def explore-unit-color [144 238 144])

;; Color functions
(defn city-color-key [city-status]
  (case city-status
    :player :player-city
    :computer :computer-city
    :free :free-city))

(defn country-land-color [country-id]
  (nth land-colors (mod country-id (count land-colors))))

(defn color-of [cell]
  (let [terrain-type (:type cell)]
    (cond
      (= terrain-type :city) (cell-colors (city-color-key (:city-status cell)))
      (and (= terrain-type :land) (:country-id cell)) (country-land-color (:country-id cell))
      :else (cell-colors terrain-type))))

(defn mode->color [mode]
  (case mode
    :awake awake-unit-color
    :sentry sentry-unit-color
    :explore explore-unit-color
    :coastline-follow explore-unit-color
    sleeping-unit-color))

(defn unit->color [unit]
  (cond
    (and (= :computer (:owner unit)) (= :army (:type unit))) awake-unit-color
    (= :loading (:mission unit)) sleeping-unit-color
    :else (mode->color (:mode unit))))
```

**Step 2: Create `config/generation.cljc`**

```clojure
(ns empire.config.generation)

(def default-map-size [100 60])
(def smooth-count 10)
(def land-fraction 0.3)
(def number-of-cities 70)
(def min-city-distance 5)
(def max-placement-attempts 1000)
(def min-surrounding-land 10)

(defn compute-size-constants [cols rows]
  (let [area (* cols rows)
        ref-area 6000]
    {:cols cols
     :rows rows
     :number-of-cities (max 10 (int (* 70 (/ area ref-area))))}))
```

**Step 3: Create `config/ai.cljc`**

```clojure
(ns empire.config.ai)

(def armies-before-transport 6)
(def max-patrol-boats-per-country 4)
(def carrier-city-threshold 10)
(def max-live-carriers 8)
(def max-carrier-producers 2)
(def satellite-city-threshold 15)
(def max-satellites 1)
(def advances-per-frame 10)
```

**Step 4: Create `config/keys.cljc`**

```clojure
(ns empire.config.keys)

(def key->direction
  {:q [-1 -1] :w [0 -1] :e [1 -1]
   :a [-1 0]             :d [1 0]
   :z [-1 1]  :x [0 1]  :c [1 1]})

(def key->extended-direction
  {:Q [-1 -1] :W [0 -1] :E [1 -1]
   :A [-1 0]             :D [1 0]
   :Z [-1 1]  :X [0 1]  :C [1 1]})

(def key->production-item
  {:a :army :f :fighter :z :satellite :t :transport
   :p :patrol-boat :d :destroyer :s :submarine
   :c :carrier :b :battleship})
```

**Step 5: Create `config/messages.cljc`**

```clojure
(ns empire.config.messages)

(def error-message-duration 10000)

(def messages
  { ;; ... exact copy of the messages map from config.cljc lines 163-207
   })
```

(Copy the full messages map verbatim.)

**Step 6: Rewrite `config.cljc` as facade**

```clojure
(ns empire.config
  (:require [empire.config.rendering :as rendering]
            [empire.config.generation :as generation]
            [empire.config.ai :as ai]
            [empire.config.keys :as keys]
            [empire.config.messages :as msgs]
            [empire.units.config :as units-config]
            [empire.units.ships :as ships]))

;; Re-exports: rendering
(def cell-size rendering/cell-size)
(def text-font-name rendering/text-font-name)
(def text-font-size rendering/text-font-size)
(def cell-char-font-name rendering/cell-char-font-name)
(def cell-char-font-size rendering/cell-char-font-size)
(def text-area-rows rendering/text-area-rows)
(def text-area-gap rendering/text-area-gap)
(def cell-char-x-offset rendering/cell-char-x-offset)
(def cell-char-y-offset rendering/cell-char-y-offset)
(def msg-left-padding rendering/msg-left-padding)
(def msg-line-1-y rendering/msg-line-1-y)
(def msg-line-2-y rendering/msg-line-2-y)
(def msg-line-3-y rendering/msg-line-3-y)
(def msg-separator-offset rendering/msg-separator-offset)
(def game-info-width-fraction rendering/game-info-width-fraction)
(def debug-width-fraction rendering/debug-width-fraction)
(def game-status-width-fraction rendering/game-status-width-fraction)
(def cell-colors rendering/cell-colors)
(def land-colors rendering/land-colors)
(def production-color rendering/production-color)
(def waypoint-color rendering/waypoint-color)
(def awake-unit-color rendering/awake-unit-color)
(def sleeping-unit-color rendering/sleeping-unit-color)
(def sentry-unit-color rendering/sentry-unit-color)
(def explore-unit-color rendering/explore-unit-color)
(def city-color-key rendering/city-color-key)
(def country-land-color rendering/country-land-color)
(def color-of rendering/color-of)
(def mode->color rendering/mode->color)
(def unit->color rendering/unit->color)

;; Re-exports: generation
(def default-map-size generation/default-map-size)
(def smooth-count generation/smooth-count)
(def land-fraction generation/land-fraction)
(def number-of-cities generation/number-of-cities)
(def min-city-distance generation/min-city-distance)
(def max-placement-attempts generation/max-placement-attempts)
(def min-surrounding-land generation/min-surrounding-land)
(def compute-size-constants generation/compute-size-constants)

;; Re-exports: ai
(def armies-before-transport ai/armies-before-transport)
(def max-patrol-boats-per-country ai/max-patrol-boats-per-country)
(def carrier-city-threshold ai/carrier-city-threshold)
(def max-live-carriers ai/max-live-carriers)
(def max-carrier-producers ai/max-carrier-producers)
(def satellite-city-threshold ai/satellite-city-threshold)
(def max-satellites ai/max-satellites)
(def advances-per-frame ai/advances-per-frame)

;; Re-exports: keys
(def key->direction keys/key->direction)
(def key->extended-direction keys/key->extended-direction)
(def key->production-item keys/key->production-item)

;; Re-exports: messages
(def error-message-duration msgs/error-message-duration)
(def messages msgs/messages)

;; Domain constants (stay in facade — shared across layers)
(def hostile-city? #{:free :computer})
(def fighter-fuel units-config/fighter-fuel)
(def transport-capacity units-config/transport-capacity)
(def carrier-capacity units-config/carrier-capacity)
(def explore-steps 50)
(def coastline-steps 100)
(def satellite-turns units-config/satellite-turns)
(def max-sidesteps 10)
(def carrier-spacing 22)
(def bingo-fuel-divisor 4)

;; Unit stat delegation (stay in facade — used cross-layer)
(defn item-cost [unit-type]
  (case unit-type
    :army units-config/army-cost
    :fighter units-config/fighter-cost
    :satellite units-config/satellite-cost
    :transport units-config/transport-cost
    :carrier units-config/carrier-cost
    :patrol-boat (ships/config :patrol-boat :cost)
    :destroyer (ships/config :destroyer :cost)
    :submarine (ships/config :submarine :cost)
    :battleship (ships/config :battleship :cost)
    nil))

(defn item-chars [unit-type]
  (case unit-type
    :army units-config/army-display-char
    :fighter units-config/fighter-display-char
    :satellite units-config/satellite-display-char
    :transport units-config/transport-display-char
    :carrier units-config/carrier-display-char
    :patrol-boat (ships/config :patrol-boat :display-char)
    :destroyer (ships/config :destroyer :display-char)
    :submarine (ships/config :submarine :display-char)
    :battleship (ships/config :battleship :display-char)
    nil))

(defn item-hits [unit-type]
  (case unit-type
    :army units-config/army-hits
    :fighter units-config/fighter-hits
    :satellite units-config/satellite-hits
    :transport units-config/transport-hits
    :carrier units-config/carrier-hits
    :patrol-boat (ships/config :patrol-boat :hits)
    :destroyer (ships/config :destroyer :hits)
    :submarine (ships/config :submarine :hits)
    :battleship (ships/config :battleship :hits)
    nil))

(defn unit-speed [unit-type]
  (case unit-type
    :army units-config/army-speed
    :fighter units-config/fighter-speed
    :satellite units-config/satellite-speed
    :transport units-config/transport-speed
    :carrier units-config/carrier-speed
    :patrol-boat (ships/config :patrol-boat :speed)
    :destroyer (ships/config :destroyer :speed)
    :submarine (ships/config :submarine :speed)
    :battleship (ships/config :battleship :speed)
    nil))
```

**Step 7: Run tests**

```bash
clj -M:spec spec/empire/config_spec.clj
clj -M:all-tests-fast
```
Expected: all pass — facade re-exports preserve all existing call sites.

**Step 8: Commit**

```bash
git add src/empire/config/ src/empire/config.cljc spec/empire/config_spec.clj
git commit -m "Split config.cljc into 5 sub-modules with re-export facade"
```

---

## Task 6: Split `round_setup.cljc` into 4 sub-modules

**Files:**
- Create: `src/empire/game_loop/round_setup/fuel.cljc`
- Create: `src/empire/game_loop/round_setup/waking.cljc`
- Create: `src/empire/game_loop/round_setup/lakes.cljc`
- Create: `src/empire/game_loop/round_setup/repair.cljc`
- Modify: `src/empire/game_loop/round_setup.cljc` (becomes orchestrator)
- Test: `spec/empire/game_loop/round_setup_spec.clj` (no changes needed — tests call public functions which stay public in sub-modules)

**Step 1: Create `round_setup/fuel.cljc`**

Move: `bingo-fuel?`, `fuel-action`, `apply-fuel-action`, `consume-sentry-fighter-fuel` (lines 114-145).

```clojure
(ns empire.game-loop.round-setup.fuel
  (:require [empire.application.state-access :as sa]
            [empire.application.movement-services :as movement-services]
            [empire.config :as config]
            [empire.domain.services.round-setup :as domain-round-setup]))

(defn- set-error-message! [msg ms]
  (sa/write-state! :error-message msg)
  (sa/write-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- bingo-fuel? [pos new-fuel]
  (domain-round-setup/bingo-fuel?
   new-fuel
   (movement-services/friendly-city-in-range? pos new-fuel (atom (sa/current-world)))))

(defn- fuel-action [new-fuel pos]
  (domain-round-setup/fuel-action new-fuel (bingo-fuel? pos new-fuel)))

(defn- apply-fuel-action [pos action new-fuel]
  (case action
    :crashed (do (set-error-message! (:fighter-crashed config/messages) config/error-message-duration)
                 (sa/update-world! assoc-in (conj pos :contents :hits) 0))
    :out-of-fuel (sa/update-world! update-in (conj pos :contents)
                                   #(assoc % :fuel new-fuel :mode :awake :reason :fighter-out-of-fuel))
    :bingo (sa/update-world! update-in (conj pos :contents)
                             #(assoc % :fuel new-fuel :mode :awake :reason :fighter-bingo))
    :burn (sa/update-world! assoc-in (conj pos :contents :fuel) new-fuel)))

(defn consume-sentry-fighter-fuel []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :fighter (:type unit))
                       (= :sentry (:mode unit)))]
      (let [new-fuel (dec (:fuel unit config/fighter-fuel))]
        (apply-fuel-action [i j] (fuel-action new-fuel [i j]) new-fuel)))))
```

**Step 2: Create `round_setup/waking.cljc`**

Move: `wake-airport-fighters`, `wake-carrier-fighters`, `wake-sentries-seeing-enemy` (lines 82-161).

```clojure
(ns empire.game-loop.round-setup.waking
  (:require [empire.application.state-access :as sa]
            [empire.application.movement-services :as movement-services]
            [empire.containers.helpers :as uc]))

(defn wake-airport-fighters []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])]
            :when (and (= (:type cell) :city)
                       (= (:city-status cell) :player)
                       (pos? (uc/get-count cell :fighter-count)))]
      (let [total (uc/get-count cell :fighter-count)]
        (sa/update-world! assoc-in [i j :awake-fighters] total)))))

(defn wake-carrier-fighters []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :carrier (:type unit))
                       (= :player (:owner unit))
                       (pos? (uc/get-count unit :fighter-count)))]
      (let [total (uc/get-count unit :fighter-count)]
        (sa/update-world! assoc-in [i j :contents :awake-fighters] total)))))

(defn wake-sentries-seeing-enemy []
  (let [world (sa/current-world)
        world-atom (atom world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :player (:owner unit))
                       (= :sentry (:mode unit))
                       (movement-services/enemy-unit-visible? unit [i j] world-atom))]
      (sa/update-world! update-in [i j :contents]
                        #(assoc % :mode :awake :reason :enemy-spotted)))))
```

**Step 3: Create `round_setup/lakes.cljc`**

Move all lake/evacuation/lock functions (lines 174-387). This is the largest sub-module at ~215 lines.

The namespace will be:
```clojure
(ns empire.game-loop.round-setup.lakes
  (:require [empire.application.state-access :as sa]
            [empire.application.movement-services :as movement-services]
            [empire.containers.ops :as container-ops]
            [clojure.set :as set]))
```

Move functions verbatim, replacing `current-world` → `sa/current-world`, `update-game-map!` → `sa/update-world!`, `read-runtime-state` → `sa/read-state`, `write-runtime-state!` → `sa/write-state!`.

Public functions: `evacuate-lake-patrol-boats`, `mark-lake-locked-ships`.

**Step 4: Create `round_setup/repair.cljc`**

Move: `repair-city-ships`, `repair-damaged-ships` (lines 389-424), plus `find-adjacent-empty-sea` helper (line 174 — also used by lakes, so either duplicate or share).

Since `find-adjacent-empty-sea` is used by both `lakes.cljc` and `repair.cljc`, make it a shared helper in a new `round_setup/helpers.cljc`:

```clojure
(ns empire.game-loop.round-setup.helpers
  (:require [empire.application.state-access :as sa]
            [empire.application.movement-services :as movement-services]))

(defn find-adjacent-empty-sea [pos]
  (first (movement-services/get-matching-neighbors
          pos (sa/current-world) movement-services/neighbor-offsets
          #(and (= :sea (:type %)) (nil? (:contents %))))))
```

Then `repair.cljc`:
```clojure
(ns empire.game-loop.round-setup.repair
  (:require [empire.application.state-access :as sa]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.game-loop.round-setup.helpers :as helpers]))
```

**Step 5: Rewrite `round_setup.cljc` as orchestrator**

Keep: `dead-unit?`, `computer-carrier?`, `remove-dead-units`, `reset-steps-remaining`, `move-satellites`.
Add requires for the 4 sub-modules + helpers. Delegate to sub-module public functions.

**Step 6: Update spec requires**

The spec file tests public functions like `consume-sentry-fighter-fuel`, `wake-airport-fighters`, etc. These are now in sub-modules. Update the spec's `:require` to pull from sub-modules:

```clojure
(:require [empire.game-loop.round-setup.fuel :as fuel]
          [empire.game-loop.round-setup.waking :as waking]
          [empire.game-loop.round-setup.lakes :as lakes]
          [empire.game-loop.round-setup.repair :as repair]
          ...)
```

And update test references: `round-setup/consume-sentry-fighter-fuel` → `fuel/consume-sentry-fighter-fuel`, etc.

**Step 7: Run tests**

```bash
clj -M:spec-structure-check spec/empire/game_loop/round_setup_spec.clj
clj -M:spec spec/empire/game_loop/round_setup_spec.clj
clj -M:all-tests-fast
```

**Step 8: Commit**

```bash
git add src/empire/game_loop/round_setup/ src/empire/game_loop/round_setup.cljc spec/empire/game_loop/round_setup_spec.clj
git commit -m "Split round_setup.cljc into fuel/waking/lakes/repair sub-modules"
```

---

## Task 7: Split `actions.cljc` into 3 sub-modules

**Files:**
- Create: `src/empire/ui/util/input/actions/movement.cljc`
- Create: `src/empire/ui/util/input/actions/modes.cljc`
- Create: `src/empire/ui/util/input/actions/production.cljc`
- Modify: `src/empire/ui/util/input/actions.cljc` (becomes dispatcher)
- Modify: `spec/empire/ui/util/input/actions_spec.clj` (update requires)

**Step 1: Create `actions/movement.cljc`**

Move: `calculate-extended-target`, `launch-fighter-and-update`, `army-aboard-action`, `handle-army-aboard-movement`, `undamaged-ship-entering-friendly-city?`, `hostile-city-action`, `standard-movement-action`, `perform-standard-movement!`, `handle-standard-unit-movement`, `execute-unit-movement`, `handle-unit-movement-key` (lines 78-170).

```clojure
(ns empire.ui.util.input.actions.movement
  (:require [empire.application.state-access :as sa]
            [empire.application.ports.movement :as ports]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.game-loop :as game-loop]
            [empire.player.commands :as player-commands]
            [empire.units.dispatcher :as dispatcher]))
```

The `item-processed!` and `set-error-message!` helpers will be in the parent `actions.cljc` module (or a shared `actions/helpers.cljc`). Movement will require it.

Public functions: `handle-unit-movement-key`, `army-aboard-action`.

**Step 2: Create `actions/modes.cljc`**

Move: `handle-space-key`, `handle-unload-key`, `handle-sentry-key`, `find-adjacent-land`, `begin-army-explore!`, `disembark-army-to-explore!`, `begin-coastline-follow!`, `show-coastline-rejection!`, `handle-look-around-key` (lines 172-280).

```clojure
(ns empire.ui.util.input.actions.modes
  (:require [empire.application.state-access :as sa]
            [empire.application.ports.movement :as ports]
            [empire.config :as config]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.game-loop :as game-loop]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.map-utils :as map-utils]))
```

Public functions: `handle-space-key`, `handle-unload-key`, `handle-sentry-key`, `handle-look-around-key`.

**Step 3: Create `actions/production.cljc`**

Move: `try-set-production`, `handle-city-production-key` (lines 54-76).

```clojure
(ns empire.ui.util.input.actions.production
  (:require [empire.application.state-access :as sa]
            [empire.application.ports.movement :as ports]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.movement.map-utils :as map-utils]
            [empire.player.production :as player-production]
            [empire.units.dispatcher :as dispatcher]))
```

Public function: `handle-city-production-key`.

**Step 4: Rewrite `actions.cljc` as dispatcher**

```clojure
(ns empire.ui.util.input.actions
  (:require [empire.application.state-access :as sa]
            [empire.application.ports.movement :as ports]
            [empire.game-loop :as game-loop]
            [empire.ui.util.input.actions.movement :as movement]
            [empire.ui.util.input.actions.modes :as modes]
            [empire.ui.util.input.actions.production :as production]))

(defn- movement-port []
  (or (:movement-port (sa/state-ctx))
      (throw (ex-info "Movement port not configured" {}))))

(defn army-aboard-action [extended? target-cell hostile-city?]
  (movement/army-aboard-action extended? target-cell hostile-city?))

(defn handle-key [k]
  (when-let [coords (first (sa/read-state :cells-needing-attention))]
    (let [cell (get-in (sa/current-world) coords)
          active-unit (ports/movement-get-active-unit (movement-port) cell)]
      (if active-unit
        (case k
          :space (modes/handle-space-key coords)
          :u (modes/handle-unload-key coords cell)
          :s (modes/handle-sentry-key coords cell active-unit)
          :l (modes/handle-look-around-key coords cell active-unit)
          (movement/handle-unit-movement-key k coords cell))
        (production/handle-city-production-key k coords cell)))))
```

**Step 5: Update spec requires**

Update `actions_spec.clj` to require the sub-modules for any tests that directly test sub-module functions. The `handle-key` tests remain against `actions/handle-key`.

**Step 6: Run tests**

```bash
clj -M:spec-structure-check spec/empire/ui/util/input/actions_spec.clj
clj -M:spec spec/empire/ui/util/input/actions_spec.clj
clj -M:all-tests-fast
```

**Step 7: Commit**

```bash
git add src/empire/ui/util/input/actions/ src/empire/ui/util/input/actions.cljc spec/empire/ui/util/input/actions_spec.clj
git commit -m "Split actions.cljc into movement/modes/production sub-modules"
```

---

## Task 8: Split `computer/core.cljc` contract/impl

**Files:**
- Create: `src/empire/computer/core/impl.cljc`
- Modify: `src/empire/computer/core.cljc` (keep contracts + pure helpers)
- Modify: `src/empire/application/bootstrap.cljc` (add impl require)
- Modify: `scripts/check-architecture-boundaries.sh` (add guard)
- Test: `spec/empire/computer/core_spec.clj` (no changes — tests call multimethods)

**Step 1: Create `computer/core/impl.cljc`**

Move all 17 `defmethod :default` implementations plus private stateful helpers.

```clojure
(ns empire.computer.core.impl
  (:require [empire.application.state-access :as sa]
            [empire.application.ports.movement :as movement-port]
            [empire.computer.core :as core]
            [empire.computer.core.transport-search :as transport-search]
            [empire.debug :as debug]
            [empire.combat :as combat]))

;; Private helpers
(defn- movement-services [] (:movement-port (sa/state-ctx)))

(defn- country-city-producing-armies? [city-pos country-id]
  (if-let [f (:country-city-producing-armies? (sa/state-ctx))]
    (f city-pos country-id) false))

;; ... (remaining private helpers, all using sa/ instead of local boilerplate)

;; Implementations
(defmethod core/get-neighbors :default [pos]
  (core/neighbors-in-map (sa/current-world) pos))

(defmethod core/distance :default [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

;; ... (remaining 15 defmethod implementations)
```

**Step 2: Rewrite `computer/core.cljc` as contracts**

Keep: 17 `defmulti` declarations + pure helpers (`neighbor-offsets`, `neighbors-in-map`, `adjacent?`).
Remove: all `defmethod` forms, all private stateful helpers.

```clojure
(ns empire.computer.core
  "Shared utilities for computer AI modules — contracts only.")

(def ^:private neighbor-offsets
  [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])

(defn neighbors-in-map [the-map [r c]]
  (if (and (sequential? the-map) (seq the-map) (sequential? (first the-map)))
    (let [height (count the-map) width (count (first the-map))]
      (for [[dr dc] neighbor-offsets
            :let [nr (+ r dr) nc (+ c dc)]
            :when (and (<= 0 nr) (< nr height) (<= 0 nc) (< nc width))]
        [nr nc]))
    []))

(defn adjacent? [pos1 pos2]
  (let [[r1 c1] pos1 [r2 c2] pos2
        dr (Math/abs (- r2 r1)) dc (Math/abs (- c2 c1))]
    (and (<= dr 1) (<= dc 1) (not (and (zero? dr) (zero? dc))))))

(defmulti get-neighbors (fn [& _] :default))
(defmulti distance (fn [& _] :default))
(defmulti chebyshev-distance (fn [& _] :default))
(defmulti attackable-target? (fn [& _] :default))
(defmulti find-visible-cities (fn [& _] :default))
(defmulti move-toward (fn [& _] :default))
(defmulti adjacent-to-computer-unexplored? (fn [& _] :default))
(defmulti stamp-territory (fn [& _] :default))
(defmulti move-unit-to (fn [& _] :default))
(defmulti attempt-conquest-computer (fn [& _] :default))
(defmulti random-away-direction (fn [& _] :default))
(defmulti find-wakeable-sentries (fn [& _] :default))
(defmulti wake-nearby-sentries (fn [& _] :default))
(defmulti board-transport (fn [& _] :default))
(defmulti find-visible-player-units (fn [& _] :default))
(defmulti find-loading-transport (fn [& _] :default))
(defmulti find-adjacent-loading-transport (fn [& _] :default))
```

Note: `neighbors-in-map` and `adjacent?` must become public (remove `defn-`) since `impl.cljc` needs them. `neighbor-offsets` can stay private if `impl.cljc` doesn't need it directly.

**Step 3: Wire in bootstrap**

Add to `src/empire/application/bootstrap.cljc`:
```clojure
[empire.computer.core.impl]
```

**Step 4: Add boundary guard**

Add to `scripts/check-architecture-boundaries.sh`:
```bash
core_impl_hits="$(rg -n 'empire\.computer\.core\.impl' src/empire || true)"
core_impl_violations="$(printf '%s\n' "$core_impl_hits" | rg -v '^src/empire/computer/core/impl\.cljc:|^src/empire/application/bootstrap\.cljc:' || true)"
if [[ -n "$core_impl_violations" ]]; then
  echo "Architecture boundary violation: computer.core.impl must only be referenced by itself and application.bootstrap:"
  printf '%s\n' "$core_impl_violations"
  exit 1
fi
```

**Step 5: Run tests**

```bash
clj -M:spec spec/empire/computer/core_spec.clj
clj -M:all-tests-fast
```

**Step 6: Commit**

```bash
git add src/empire/computer/core.cljc src/empire/computer/core/impl.cljc src/empire/application/bootstrap.cljc scripts/check-architecture-boundaries.sh
git commit -m "Split computer/core.cljc into contract and impl modules"
```

---

## Task 9: Final verification

**Step 1: Run full test suite**

```bash
clj -M:all-tests-fast
```

**Step 2: Run acceptance pipeline**

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

**Step 3: Verify no remaining boilerplate**

```bash
rg '\(delay \(app-runtime/default-state-ctx\)\)' src/empire --glob '!application/state_access.cljc'
```
Expected: 0 matches.

**Step 4: Verify all files under 250 lines**

```bash
find src/empire -name '*.cljc' -exec sh -c 'lines=$(wc -l < "$1"); if [ "$lines" -gt 250 ]; then echo "$1: $lines"; fi' _ {} \;
```
Review: `round_setup.cljc`, `actions.cljc`, `core.cljc` should all be under 250.

**Step 5: Commit and report**

If any issues, fix and re-verify. Then commit any final adjustments.
