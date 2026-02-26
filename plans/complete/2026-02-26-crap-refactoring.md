# CRAP Score Refactoring — Three Functions

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce CRAP scores for `wake-nearby-sentries`, `wake-fighter-check`, and `calculate-bounce-target` by lowering cyclomatic complexity and improving coverage.

**Architecture:** Extract helper functions and use data-driven dispatch (lookup tables) to replace branching logic. Each function becomes a thin dispatcher over extracted, testable pieces.

**Tech Stack:** Clojure, Speclj, crap4clj

---

### Task 1: Refactor `calculate-bounce-target` — extract bounce helpers

**Files:**
- Modify: `src/empire/units/satellite.cljc:49-71`
- Test: `spec/empire/units/satellite_spec.clj`

**Step 1: Write failing tests for `bounce-vertical` and `bounce-horizontal`**

These are new private helpers. Test via the existing public `calculate-bounce-target` — but add edge-specific tests that nail down the helpers' behavior. Add to the `"calculate-bounce-target"` context in `spec/empire/units/satellite_spec.clj`:

```clojure
(it "mid-left edge bounces to right edge"
  (with-redefs [rand-int (constantly 3)]
    (should= [3 9] (satellite/calculate-bounce-target [5 0] 10 10))))

(it "mid-right edge bounces to left edge"
  (with-redefs [rand-int (constantly 3)]
    (should= [3 0] (satellite/calculate-bounce-target [5 9] 10 10))))

(it "mid-top edge bounces to bottom edge"
  (with-redefs [rand-int (constantly 3)]
    (should= [9 3] (satellite/calculate-bounce-target [0 5] 10 10))))

(it "mid-bottom edge bounces to top edge"
  (with-redefs [rand-int (constantly 3)]
    (should= [0 3] (satellite/calculate-bounce-target [9 5] 10 10))))
```

**Step 2: Run tests to verify they pass with current code**

Run: `clj -M:spec spec/empire/units/satellite_spec.clj`
Expected: PASS (these test existing behavior)

**Step 3: Refactor to data-driven dispatch**

Replace `calculate-bounce-target` (lines 49-71) with:

```clojure
(defn- bounce-vertical [at-top? map-height map-width]
  [(if at-top? (dec map-height) 0) (rand-int map-width)])

(defn- bounce-horizontal [at-left? map-height map-width]
  [(rand-int map-height) (if at-left? (dec map-width) 0)])

(def ^:private bounce-dispatch
  {:vertical bounce-vertical
   :horizontal bounce-horizontal})

(defn calculate-bounce-target
  "Calculates new target on opposite boundary when satellite reaches edge.
   At corners, randomly chooses one of the two opposite boundaries."
  [[x y] map-height map-width]
  (let [edges (cond-> []
                (= x 0)                (conj [:vertical true])
                (= x (dec map-height)) (conj [:vertical false])
                (= y 0)                (conj [:horizontal true])
                (= y (dec map-width))  (conj [:horizontal false]))]
    (if (empty? edges)
      [x y]
      (let [[edge-type near-origin?] (rand-nth edges)]
        ((bounce-dispatch edge-type) near-origin? map-height map-width)))))
```

**Step 4: Run tests to verify refactoring is correct**

Run: `clj -M:spec spec/empire/units/satellite_spec.clj`
Expected: PASS

**Step 5: Run CRAP to verify improvement**

Run: `clj -M:crap src/empire/units/satellite.cljc 2>/dev/null | grep calculate-bounce`
Expected: CC ~3-5, CRAP score well below 16

**Step 6: Commit**

```
git add src/empire/units/satellite.cljc spec/empire/units/satellite_spec.clj
git commit -m "refactor: data-driven dispatch for calculate-bounce-target"
```

---

### Task 2: Refactor `wake-fighter-check` — lookup table

**Files:**
- Modify: `src/empire/movement/wake_conditions.cljc:140-155`
- Test: `spec/empire/movement/wake_conditions_spec.clj`

**Step 1: Run existing tests to establish green baseline**

Run: `clj -M:spec spec/empire/movement/wake_conditions_spec.clj`
Expected: PASS

**Step 2: Extract `build-fighter-checks` and rewrite `wake-fighter-check`**

Replace lines 140-155 with:

```clojure
(defn- build-fighter-checks [unit final-pos current-map]
  (let [dest-cell (get-in @current-map final-pos)
        entering-city? (= (:type dest-cell) :city)
        friendly-city? (= (:city-status dest-cell) :player)
        hostile-city? (and entering-city? (not friendly-city?))
        fuel (:fuel unit config/fighter-fuel)
        low-fuel? (<= fuel 1)
        bingo-fuel? (and (<= fuel (quot config/fighter-fuel 4))
                         (friendly-city-in-range? final-pos fuel current-map)
                         (not (target-is-reachable-friendly-city? unit final-pos fuel current-map)))]
    [[hostile-city?  {:wake? true :reason :fighter-shot-down :shot-down? true}]
     [entering-city? {:wake? true :reason :fighter-landed-and-refueled :refuel? true}]
     [low-fuel?      {:wake? true :reason :fighter-out-of-fuel}]
     [bingo-fuel?    {:wake? true :reason :fighter-bingo}]]))

(defn- wake-fighter-check [unit _from-pos final-pos current-map]
  (let [checks (build-fighter-checks unit final-pos current-map)]
    (some (fn [[pred result]] (when pred result)) checks)))
```

**Step 3: Run tests to verify refactoring is correct**

Run: `clj -M:spec spec/empire/movement/wake_conditions_spec.clj`
Expected: PASS

**Step 4: Run CRAP to verify improvement**

Run: `clj -M:crap src/empire/movement/wake_conditions.cljc 2>/dev/null | grep wake-fighter`
Expected: CC reduced significantly, CRAP well below 16

**Step 5: Commit**

```
git add src/empire/movement/wake_conditions.cljc
git commit -m "refactor: lookup table for wake-fighter-check"
```

---

### Task 3: Refactor `wake-nearby-sentries` — extract pure helpers + add tests

**Files:**
- Modify: `src/empire/computer/core.cljc:133-160`
- Create: `spec/empire/computer/core_spec.clj`

**Step 1: Write failing test for `random-away-direction`**

Create `spec/empire/computer/core_spec.clj`:

```clojure
(ns empire.computer.core-spec
  (:require [speclj.core :refer :all]
            [empire.computer.core :as core]
            [empire.test-utils :refer [reset-all-atoms!]]))

(describe "computer core"
  (before (reset-all-atoms!))

  (context "random-away-direction"
    (it "returns [1 1] when target is southeast of origin"
      (should= [1 1] (core/random-away-direction [0 0] [1 1])))

    (it "returns [-1 -1] when target is northwest of origin"
      (should= [-1 -1] (core/random-away-direction [5 5] [4 4])))

    (it "returns [1 -1] when target is south-west of origin"
      (should= [1 -1] (core/random-away-direction [3 3] [4 2])))

    (it "randomizes axis when aligned on column (dc=0)"
      (with-redefs [rand (constantly 0.1)]
        (should= [-1 1] (core/random-away-direction [3 3] [3 4]))))

    (it "randomizes opposite axis when aligned on column (dc=0, rand>0.5)"
      (with-redefs [rand (constantly 0.9)]
        (should= [1 1] (core/random-away-direction [3 3] [3 4]))))

    (it "randomizes axis when aligned on row (dr=0)"
      (with-redefs [rand (constantly 0.1)]
        (should= [1 -1] (core/random-away-direction [3 3] [4 3]))))

    (it "randomizes opposite axis when aligned on row (dr=0, rand>0.5)"
      (with-redefs [rand (constantly 0.9)]
        (should= [1 1] (core/random-away-direction [3 3] [4 3]))))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/core_spec.clj`
Expected: FAIL — `random-away-direction` not defined

**Step 3: Implement `random-away-direction`**

Add before `wake-nearby-sentries` in `src/empire/computer/core.cljc`:

```clojure
(defn random-away-direction
  "Computes a direction vector pointing away from origin toward target.
   When aligned on an axis (delta=0), randomly picks -1 or 1."
  [origin target]
  (let [[oc or'] origin
        [tc tr] target
        dc (Integer/signum (- tc oc))
        dr (Integer/signum (- tr or'))]
    [(if (zero? dc) (if (< (rand) 0.5) -1 1) dc)
     (if (zero? dr) (if (< (rand) 0.5) -1 1) dr)]))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/core_spec.clj`
Expected: PASS

**Step 5: Write failing test for `find-wakeable-sentries`**

Add to `spec/empire/computer/core_spec.clj`:

```clojure
(context "find-wakeable-sentries"
  (it "finds computer sentry armies within radius"
    (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                    [{:type :land :contents {:type :army :owner :computer :mode :awake}}]
                    [{:type :land}]
                    [{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                    [{:type :land :contents {:type :army :owner :player :mode :sentry}}]]]
      (should= [[0 0] [3 0]] (core/find-wakeable-sentries game-map [2 0] 3))))

  (it "excludes sentries outside radius"
    (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                    [{:type :land}]
                    [{:type :land}]
                    [{:type :land}]
                    [{:type :land :contents {:type :army :owner :computer :mode :sentry}}]]]
      (should= [[4 0]] (core/find-wakeable-sentries game-map [3 0] 2))))

  (it "excludes the origin position"
    (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]]]
      (should= [] (core/find-wakeable-sentries game-map [0 0] 3)))))
```

**Step 6: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/core_spec.clj`
Expected: FAIL — `find-wakeable-sentries` not defined

**Step 7: Implement `find-wakeable-sentries`**

Add before `wake-nearby-sentries` in `src/empire/computer/core.cljc`:

```clojure
(defn find-wakeable-sentries
  "Returns seq of [col row] coords of computer sentry armies within
   Chebyshev distance of pos. Pure — takes game-map value, not atom."
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
```

**Step 8: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/core_spec.clj`
Expected: PASS

**Step 9: Rewrite `wake-nearby-sentries` to use extracted helpers**

Replace the current `wake-nearby-sentries` (lines 133-160) with:

```clojure
(defn wake-nearby-sentries
  "Wakes sentry armies within radius Chebyshev distance of pos.
   Each woken army gets interior-explore-direction pointing away from pos.
   Returns count of armies woken."
  [pos radius]
  (let [candidates (find-wakeable-sentries @atoms/game-map pos radius)]
    (doseq [coord candidates
            :let [direction (random-away-direction pos coord)]]
      (swap! atoms/game-map update-in (conj coord :contents)
             #(-> % (assoc :mode :awake
                            :interior-explore-direction direction)
                  (dissoc :move-history))))
    (count candidates)))
```

**Step 10: Run all tests to verify refactoring is correct**

Run: `clj -M:spec spec/empire/computer/`
Expected: PASS (both core_spec and army_territory_spec)

**Step 11: Run CRAP to verify improvement**

Run: `clj -M:crap src/empire/computer/core.cljc 2>/dev/null | grep -E "wake-nearby|find-wakeable|random-away"`
Expected: CC ~2-3 each, CRAP scores well below 16, coverage improved

**Step 12: Commit**

```
git add src/empire/computer/core.cljc spec/empire/computer/core_spec.clj
git commit -m "refactor: extract helpers from wake-nearby-sentries, add tests"
```

---

### Task 4: Final verification

**Step 1: Run full test suite**

Run: `clj -M:spec`
Expected: All pass

**Step 2: Run CRAP on all three files**

Run:
```
clj -M:crap src/empire/units/satellite.cljc 2>/dev/null | grep calculate-bounce
clj -M:crap src/empire/movement/wake_conditions.cljc 2>/dev/null | grep wake-fighter
clj -M:crap src/empire/computer/core.cljc 2>/dev/null | grep -E "wake-nearby|find-wakeable|random-away"
```

Verify all CRAP scores are below 10.

**Step 3: Run coverage**

Run: `clj -M:cov`
Verify coverage stays in high 90s.
