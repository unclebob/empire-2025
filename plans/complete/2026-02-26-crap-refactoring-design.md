# CRAP Score Refactoring — Design

Three functions targeted for CC reduction and coverage improvement.

## 1. `wake-nearby-sentries` (CC=6→~3, Cov 33%→90%+, CRAP 16.6→low)

**File:** `src/empire/computer/core.cljc`

**Problem:** Low coverage drives CRAP score. Mutating `doseq` with `rand` is hard to test.

**Design:** Extract two pure helpers:

- `find-wakeable-sentries [game-map pos radius]` — returns seq of `[col row]` coords for computer army sentries within Chebyshev distance. Pure grid filter, no mutation.
- `random-away-direction [pos target-pos]` — computes signum direction vector with random fallback when aligned on an axis. Pure with mockable `rand`.

Outer function becomes: find candidates → compute direction for each → `swap!` each. CC ~2-3 per piece.

## 2. `wake-fighter-check` (CC=16→~4, Cov 100%, CRAP 16→~4)

**File:** `src/empire/movement/wake_conditions.cljc`

**Problem:** CC=16 from many boolean sub-conditions despite only 4 cond branches.

**Design:** Lookup table of `[predicate-fn result-map]` pairs:

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

Predicates are eagerly computed booleans in a vector. `some` finds the first truthy. CC: ~4 in builder (let bindings), ~1 in check.

## 3. `calculate-bounce-target` (CC=16→~3, Cov 96%→100%, CRAP 16→~3)

**File:** `src/empire/units/satellite.cljc`

**Problem:** Corner branch duplicates edge bounce logic. Many nested `if` expressions.

**Design:** Tagged edge data + dispatch map:

```clojure
(defn- bounce-vertical [at-top? map-height map-width]
  [(if at-top? (dec map-height) 0) (rand-int map-width)])

(defn- bounce-horizontal [at-left? map-height map-width]
  [(rand-int map-height) (if at-left? (dec map-width) 0)])

(def ^:private bounce-dispatch
  {:vertical bounce-vertical
   :horizontal bounce-horizontal})

(defn calculate-bounce-target [[x y] map-height map-width]
  (let [edges (cond-> []
                (= x 0)             (conj [:vertical true])
                (= x (dec map-height)) (conj [:vertical false])
                (= y 0)             (conj [:horizontal true])
                (= y (dec map-width))  (conj [:horizontal false]))]
    (if (empty? edges)
      [x y]
      (let [[edge-type near-origin?] (rand-nth edges)]
        ((bounce-dispatch edge-type) near-origin? map-height map-width)))))
```

- Edges collected as tagged tuples `[:vertical at-top?]` / `[:horizontal at-left?]`
- `rand-nth` picks one (handles corners naturally — two edges in vector, random choice)
- Dispatch map calls the right bounce function
- CC: ~2 in each bounce fn, ~3 in main
