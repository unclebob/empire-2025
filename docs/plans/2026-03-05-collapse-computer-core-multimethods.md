# Collapse computer.core Fake Multimethods Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace 17 fake-polymorphic `defmulti`/`defmethod :default` pairs in `computer/core.cljc` + `core/impl.cljc` with plain `defn` functions in a single `computer/core.cljc`.

**Architecture:** Both files are in `:outer-ring`. The collapse merges `core/impl.cljc` into `core.cljc`, removes the bootstrap wiring, and removes the boundary guard. No consumer files change — same namespace, same function names, same arities.

**Tech Stack:** Clojure, Speclj (`clj -M:spec`), architecture boundary checker (`scripts/check-architecture-boundaries.sh`)

---

### Task 1: Merge impl into core.cljc

**Files:**
- Modify: `src/empire/computer/core.cljc`
- Reference: `src/empire/computer/core/impl.cljc` (source of implementations)

**Step 1: Rewrite `core.cljc` with all functions merged**

Replace the entire file. The new file:
1. Keeps the existing ns declaration but adds all `:require` clauses from `impl.cljc`
2. Keeps `neighbor-offsets`, `neighbors-in-map`, `adjacent?` as-is (pure helpers)
3. Adds the 6 private helpers from impl as `defn-`
4. Converts each `defmethod core/X :default` to `defn X` (public) — remove the `core/` prefix and the `:default` dispatch value
5. Internal self-calls change from `core/X` to just `X`

The new `core.cljc` content:

```clojure
;; mutation-tested: 2026-03-03
(ns empire.computer.core
  (:require [empire.application.ports.movement :as movement-port]
            [empire.application.state-access :as sa]
            [empire.combat :as combat]
            [empire.computer.core.transport-search :as transport-search]
            [empire.debug :as debug]))

(def neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn neighbors-in-map
  [the-map [r c]]
  (if (and (sequential? the-map) (seq the-map) (sequential? (first the-map)))
    (let [height (count the-map)
          width (count (first the-map))]
      (for [[dr dc] neighbor-offsets
            :let [nr (+ r dr)
                  nc (+ c dc)]
            :when (and (<= 0 nr) (< nr height)
                       (<= 0 nc) (< nc width))]
        [nr nc]))
    []))

(defn adjacent?
  "Returns true if pos1 and pos2 are adjacent (including diagonally)."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r2 r1))
        dc (Math/abs (- c2 c1))]
    (and (<= dr 1) (<= dc 1) (not (and (zero? dr) (zero? dc))))))

;; --- private helpers ---

(defn- movement-services
  []
  (:movement-port (sa/state-ctx)))

(defn- country-city-producing-armies?
  [city-pos country-id]
  (if-let [f (:country-city-producing-armies? (sa/state-ctx))]
    (f city-pos country-id)
    false))

(defn- set-city-production!
  [city-pos item]
  (if-let [f (:set-city-production! (sa/state-ctx))]
    (f city-pos item)
    nil))

(defn- update-cell-visibility!
  ([pos owner]
   (movement-port/movement-update-cell-visibility (movement-services) pos owner))
  ([pos owner unit]
   (movement-port/movement-update-cell-visibility-with-unit (movement-services) pos owner unit)))

(defn- on-same-continent?
  [country-a country-b]
  ((:on-same-continent? (sa/state-ctx)) country-a country-b))

(defn- foreign-territory?
  "Returns true if unit is a computer army with a country-id and the target
   land cell has a different country-id. Cities are always passable."
  [unit to-cell]
  (and (= :army (:type unit))
       (= :computer (:owner unit))
       (:country-id unit)
       (= :land (:type to-cell))
       (:country-id to-cell)
       (not (on-same-continent? (:country-id unit) (:country-id to-cell)))))

;; --- public functions (formerly defmulti/defmethod) ---

(defn get-neighbors
  [pos]
  (neighbors-in-map (sa/current-world) pos))

(defn distance
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn chebyshev-distance
  [[r1 c1] [r2 c2]]
  (max (Math/abs (- r2 r1)) (Math/abs (- c2 c1))))

(defn attackable-target?
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player)
           (not= :satellite (:type (:contents cell))))))

(defn find-visible-cities
  [status-pred]
  (let [comp-map (sa/read-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])]
          :when (and (= (:type cell) :city)
                     (status-pred (:city-status cell)))]
      [i j])))

(defn move-toward
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(distance % target) passable-neighbors)))

(defn adjacent-to-computer-unexplored?
  [pos]
  (let [comp-map (sa/read-state :computer-map)]
    (boolean (some #(nil? (get-in comp-map %))
                   (neighbors-in-map comp-map pos)))))

(defn stamp-territory
  [pos unit]
  (when (and (= :army (:type unit))
             (= :computer (:owner unit))
             (:country-id unit)
             (#{:land :city} (:type (get-in (sa/current-world) pos))))
    (sa/update-world! assoc-in (conj pos :country-id) (:country-id unit))))

(defn move-unit-to
  [from-pos to-pos]
  (let [from-cell (get-in (sa/current-world) from-pos)
        to-cell (get-in (sa/current-world) to-pos)
        unit (:contents from-cell)]
    (cond
      (:contents to-cell) nil
      (foreign-territory? unit to-cell) nil
      :else
      (do
        (sa/update-world! assoc-in from-pos (dissoc from-cell :contents))
        (sa/update-world! assoc-in (conj to-pos :contents) unit)
        (stamp-territory to-pos unit)
        (update-cell-visibility! from-pos (:owner unit))
        (update-cell-visibility! to-pos (:owner unit) unit)
        to-pos))))

(defn random-away-direction
  [origin target]
  (let [[oc or'] origin
        [tc tr] target
        dc (Integer/signum (- tc oc))
        dr (Integer/signum (- tr or'))]
    [(if (zero? dc) (if (< (rand) 0.5) -1 1) dc)
     (if (zero? dr) (if (< (rand) 0.5) -1 1) dr)]))

(defn find-wakeable-sentries
  [game-map pos radius]
  (let [[pc pr] pos]
    (for [c (range (max 0 (- pc radius)) (min (count game-map) (+ pc radius 1)))
          r (range (max 0 (- pr radius)) (min (count (first game-map)) (+ pr radius 1)))
          :when (not= [c r] pos)
          :let [cell (get-in game-map [c r])
                unit (:contents cell)]
          :when (and unit
                     (= :army (:type unit))
                     (= :computer (:owner unit))
                     (= :sentry (:mode unit))
                     (<= (chebyshev-distance pos [c r]) radius))]
      [c r])))

(defn wake-nearby-sentries
  [pos radius]
  (let [candidates (find-wakeable-sentries (sa/current-world) pos radius)]
    (doseq [coord candidates
            :let [direction (random-away-direction pos coord)]]
      (sa/update-world! update-in (conj coord :contents)
                        #(-> % (assoc :mode :awake
                                      :interior-explore-direction direction)
                             (dissoc :move-history))))
    (count candidates)))

(defn board-transport
  [army-pos transport-pos]
  (when-not (adjacent? army-pos transport-pos)
    (throw (ex-info "Cannot board transport from non-adjacent cell"
                    {:army-pos army-pos :transport-pos transport-pos})))
  (sa/update-world! update-in army-pos dissoc :contents)
  (sa/update-world! update-in (conj transport-pos :contents :army-count) (fnil inc 0))
  (wake-nearby-sentries army-pos 3))

(defn find-visible-player-units
  []
  (let [comp-map (sa/read-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])
                contents (:contents cell)]
          :when (and contents (= (:owner contents) :player))]
      [i j])))

(defn find-loading-transport
  ([] (find-loading-transport nil))
  ([army-unload-event-id]
   (transport-search/find-loading-transport (sa/current-world) army-unload-event-id)))

(defn find-adjacent-loading-transport
  ([pos]
   (find-adjacent-loading-transport pos nil))
  ([pos army-unload-event-id]
   (transport-search/find-adjacent-loading-transport (sa/current-world)
                                                     get-neighbors
                                                     pos
                                                     army-unload-event-id)))

(defn attempt-conquest-computer
  [army-pos city-pos]
  (let [army-cell (get-in (sa/current-world) army-pos)
        army (:contents army-cell)
        city-cell (get-in (sa/current-world) city-pos)]
    (if (< (rand) 0.5)
      ;; Success - conquer the city, army dies
      (do
        (debug/log-computer-event! :army-conquest-success army-pos {:city city-pos})
        (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
        (sa/update-world! assoc-in city-pos (assoc city-cell :city-status :computer))
        (sa/update-state! :computer-city-positions (fnil conj #{}) city-pos)
        (combat/conquer-city-contents city-pos :computer)
        (stamp-territory city-pos army)
        ;; Player-map updates only when the player loses a city.
        ;; Computer conquest of free cities must not update player-map.
        (when (= :player (:city-status city-cell))
          (sa/update-state! :player-map assoc-in city-pos (get-in (sa/current-world) city-pos)))
        (let [city-country-id (:country-id (get-in (sa/current-world) city-pos))]
          (when-not (and city-country-id
                         (country-city-producing-armies? city-pos city-country-id))
            (set-city-production! city-pos :army)))
        (update-cell-visibility! army-pos :computer)
        (update-cell-visibility! city-pos :computer)
        nil)
      ;; Failure - army dies
      (do
        (debug/log-computer-event! :army-conquest-fail army-pos {:city city-pos})
        (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
        (update-cell-visibility! army-pos :computer)
        nil))))
```

**Step 2: Run core specs to verify**

Run: `clj -M:spec spec/empire/computer/core_spec.clj`
Expected: All 558 lines of tests PASS — same namespace, same function names.

**Step 3: Run full test suite**

Run: `clj -M:spec`
Expected: All tests pass. The 33 consumer files require `empire.computer.core` and call functions by the same names.

---

### Task 2: Delete impl and clean up wiring

**Files:**
- Delete: `src/empire/computer/core/impl.cljc`
- Modify: `src/empire/application/bootstrap.cljc` (line 17 — remove `[empire.computer.core.impl]`)

**Step 1: Delete `core/impl.cljc`**

```bash
rm src/empire/computer/core/impl.cljc
```

**Step 2: Remove the require from bootstrap.cljc**

In `src/empire/application/bootstrap.cljc`, remove line 17:
```
            [empire.computer.core.impl]
```

**Step 3: Run tests**

Run: `clj -M:spec`
Expected: All tests pass. The impl namespace is no longer needed since all functions live in `core.cljc`.

---

### Task 3: Remove boundary guard

**Files:**
- Modify: `scripts/check-architecture-boundaries.sh` (lines 54-60)

**Step 1: Remove the `computer_core_impl` guard block**

Delete lines 54-60 from `check-architecture-boundaries.sh`:
```bash
computer_core_impl_hits="$(rg -n 'empire\.computer\.core\.impl' src/empire || true)"
computer_core_impl_violations="$(printf '%s\n' "$computer_core_impl_hits" | rg -v '^src/empire/computer/core/impl\.cljc:|^src/empire/application/bootstrap\.cljc:' || true)"
if [[ -n "$computer_core_impl_violations" ]]; then
  echo "Architecture boundary violation: computer.core.impl must only be referenced from itself and application/bootstrap:"
  printf '%s\n' "$computer_core_impl_violations"
  exit 1
fi
```

**Step 2: Run boundary checker**

Run: `scripts/check-architecture-boundaries.sh`
Expected: "Architecture boundary check passed"

**Step 3: Run full test suite one final time**

Run: `clj -M:spec`
Expected: All tests pass.

---

### Task 4: Commit

**Step 1: Stage and commit**

```bash
git add -A
git commit -m "Collapse computer.core fake multimethods to plain functions"
```

Verify no references to `empire.computer.core.impl` remain:
```bash
rg 'empire\.computer\.core\.impl' src/ spec/
```
Expected: No matches.
