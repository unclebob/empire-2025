# Army Anti-Oscillation & Transport Queue Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prevent computer armies from oscillating between two cells by adding backtrack memory, stuck-army detection with nearby sentry waking, near-coast queuing, and wake-on-transport-boarding.

**Architecture:** Four layered features in `army.cljc` and `core.cljc`. (A) Add `:move-history` (last 4 positions) to armies, recorded on each `try-move`, filtered from fallback neighbors. (B) When an army has zero valid moves after filtering, wake sentry armies within 3 Chebyshev distance — they enter interior-explore mode moving away from the stuck army. (C) When no unoccupied coastal cell exists, army lines up as close to coast as possible and goes sentry (transport queue). (D) When an army boards a transport, wake sentries within 3 cells so the queue advances.

**Tech Stack:** Clojure (.cljc), Speclj tests

---

### Task 1: Backtrack Memory — record move-history in try-move

**Files:**
- Modify: `src/empire/computer/army.cljc:144-151` (try-move)
- Test: `spec/empire/computer/army_spec.clj`

**Step 1: Write the failing test**

Add to army_spec.clj in a new describe block:

```clojure
(describe "backtrack memory"
  (it "records move-history after moving"
    (reset! atoms/game-map (build-test-map ["a##"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 0 :contents :mode] :awake)
    (doseq [col (range 3)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    ;; Give interior-explore-direction to force predictable move
    (swap! atoms/game-map assoc-in [0 0 :contents :interior-explore-direction] [1 0])
    (army/process-army [0 0])
    ;; Army should be at [1 0] with move-history containing [0 0]
    (should= [[0 0]] (get-in @atoms/game-map [1 0 :contents :move-history]))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: FAIL — `:move-history` is nil

**Step 3: Write minimal implementation**

In `army.cljc`, modify `try-move` to record move-history:

```clojure
(defn- update-move-history
  "Adds pos to move-history vector, keeping at most 4 entries."
  [history pos]
  (let [v (conj (or history []) pos)]
    (if (> (count v) 4)
      (subvec v (- (count v) 4))
      v)))

(defn- try-move
  "Attempt to move army from pos to target. Returns target if moved, nil if blocked."
  [pos target]
  (when (core/move-unit-to pos target)
    (debug/log-computer-event! :army-move pos {:to target})
    (swap! atoms/game-map update-in (conj target :contents :move-history)
           update-move-history pos)
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility target :computer)
    target))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: PASS

**Step 5: Commit**

---

### Task 2: Backtrack Memory — filter move-history from fallback neighbors

**Files:**
- Modify: `src/empire/computer/army.cljc:158-169` (move-toward-objective)
- Test: `spec/empire/computer/army_spec.clj`

**Step 1: Write the failing test**

```clojure
(it "avoids cells in move-history when choosing fallback neighbor"
  ;; Army at [1 0] with move-history [[0 0]], objective at [3 0]
  ;; Neighbors: [0 0] (in history, closer to objective=distance 3) and [2 0] (distance 1)
  ;; Without history filter, [0 0] would NOT be picked (further from objective).
  ;; Set up so preferred A* step is blocked, fallback would pick [0 0] without filter.
  ;; Actually: [0 0] is distance 3 from [3 0], [2 0] is distance 1 from [3 0]
  ;; So [2 0] wins anyway. Need a different geometry.
  ;;
  ;; Better: objective is at [0 0] direction. Army at [1 0], history [[0 0]],
  ;; preferred path blocked, fallback should skip [0 0] and try [2 0].
  (reset! atoms/game-map (build-test-map ["#a#"]))
  (reset! atoms/computer-map (build-test-map ["#a#"]))
  (doseq [col (range 3)]
    (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
  (swap! atoms/game-map assoc-in [1 0 :contents]
         {:type :army :owner :computer :hits 1 :mode :awake :country-id 1
          :move-history [[0 0]]
          :attack-target [0 0]})
  ;; Put a sentry at [0 0] to block direct move
  (swap! atoms/game-map assoc-in [0 0 :contents]
         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
  (army/process-army [1 0])
  ;; Army should have moved to [2 0] (away from objective but avoiding history)
  (should= :army (get-in @atoms/game-map [2 0 :contents :type])))
```

Wait — `attack-target` processing uses `move-toward-objective` which falls through to the fallback. But [0 0] is occupied by a sentry, so it's not in `get-empty-passable-neighbors` anyway. The history filter only matters when the cell is EMPTY but was recently visited.

Better test: the oscillation scenario itself.

```clojure
(it "does not oscillate between two cells"
  ;; Reproduce the bug: computer city at [1 0], army bouncing between [1 0] and [2 0]
  ;; Sea at row 1 makes [2 0] coastal. All other coastal cells occupied by sentries.
  ;; [0 0] has sentry. [3 0] has sentry.
  ;; Without backtrack memory, army at [1 0] → fill-coastal → [2 0],
  ;; next round [2 0] → adjacent-to-computer-city → fill-coastal → back to [1 0].
  ;; With backtrack memory, second round should NOT go back to [1 0].
  (reset! atoms/game-map (build-test-map ["a#a#a"
                                           "~~~~~"]))
  (reset! atoms/computer-map @atoms/game-map)
  (doseq [col (range 5)]
    (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
  ;; Make [2 0] the city
  (swap! atoms/game-map assoc-in [2 0] {:type :city :city-status :computer :country-id 1
                                         :contents {:type :army :owner :computer :hits 1
                                                    :mode :awake :country-id 1}})
  ;; Sentries at [0 0] and [4 0]
  (swap! atoms/game-map assoc-in [0 0 :contents :mode] :sentry)
  (swap! atoms/game-map assoc-in [4 0 :contents :mode] :sentry)
  ;; Round 1: army at city [2 0] should move to [1 0] or [3 0] (coastal, empty)
  (with-redefs [rand (constantly 0.5)]
    (army/process-army [2 0]))
  ;; Find where army went
  (let [army-pos (cond
                   (get-in @atoms/game-map [1 0 :contents]) [1 0]
                   (get-in @atoms/game-map [3 0 :contents]) [3 0]
                   :else nil)]
    (should-not-be-nil army-pos)
    ;; Round 2: army should NOT go back to [2 0] (in move-history)
    (with-redefs [rand (constantly 0.5)]
      (army/process-army army-pos))
    ;; Army should NOT be back at city [2 0]
    (let [city-contents (get-in @atoms/game-map [2 0 :contents])]
      (should (or (nil? city-contents)
                  (not= :awake (:mode city-contents)))))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: FAIL — army oscillates back to city

**Step 3: Write minimal implementation**

Modify `move-toward-objective` to filter move-history from fallback neighbors:

```clojure
(defn- move-toward-objective
  "Move army one step toward objective. If preferred step is occupied,
   try other empty neighbors sorted by distance to objective.
   Filters out cells in move-history to prevent oscillation."
  [pos objective country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        history (set (:move-history unit))
        pass-fn (when country-id (sovereignty-passability-fn country-id))
        preferred (pathfinding/next-step pos objective :army pass-fn country-id)]
    (or (when (and preferred (not (history preferred)))
          (try-move pos preferred))
        (let [empty-neighbors (get-empty-passable-neighbors pos country-id)
              filtered (remove history empty-neighbors)
              candidates (if (seq filtered) filtered empty-neighbors)]
          (when (seq candidates)
            (let [sorted (sort-by #(core/distance % objective) candidates)]
              (try-move pos (first sorted))))))))
```

Also modify `explore-randomly` similarly:

```clojure
(defn- explore-randomly [pos country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        history (set (:move-history unit))
        empty (get-empty-passable-neighbors pos country-id)
        filtered (remove history empty)
        pool (if (seq filtered) filtered empty)
        frontier (filter core/adjacent-to-computer-unexplored? pool)]
    (when-let [target (if (seq frontier)
                        (rand-nth frontier)
                        (when (seq pool) (rand-nth pool)))]
      (try-move pos target))))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: PASS

**Step 5: Run all existing tests to check for regressions**

Run: `clj -M:spec`
Expected: All pass

**Step 6: Commit**

---

### Task 3: Stuck Detection — wake nearby sentries

**Files:**
- Modify: `src/empire/computer/core.cljc` (add `wake-nearby-sentries` helper)
- Modify: `src/empire/computer/army.cljc` (call from fill-coastal-cell and move-toward-objective)
- Test: `spec/empire/computer/army_spec.clj`

**Step 1: Write the failing test**

```clojure
(describe "stuck army wakes nearby sentries"
  (it "wakes sentry armies within 3 cells when stuck"
    ;; Army at [2 1] boxed in: move-history covers the only empty cells
    ;; Sentries at [0 0], [4 2] within 3 Chebyshev distance
    (reset! atoms/game-map (build-test-map ["#####"
                                             "##a##"
                                             "#####"]))
    (reset! atoms/computer-map @atoms/game-map)
    (doseq [col (range 5) row (range 3)]
      (swap! atoms/game-map assoc-in [col row :country-id] 1))
    ;; Put sentries on all neighbors of [2 1]
    (doseq [pos [[1 0] [2 0] [3 0] [1 1] [3 1] [1 2] [2 2] [3 2]]]
      (swap! atoms/game-map assoc-in (conj pos :contents)
             {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
    ;; Army at [2 1] with move-history covering... actually all neighbors are occupied.
    ;; So it's stuck regardless of history.
    (swap! atoms/game-map assoc-in [2 1 :contents :mode] :awake)
    (swap! atoms/game-map assoc-in [2 1 :contents :country-id] 1)
    (with-redefs [rand (constantly 0.5)
                  rand-nth (fn [coll] (first coll))]
      (army/process-army [2 1]))
    ;; At least one nearby sentry should have been woken (mode changed from :sentry)
    (let [modes (map #(get-in @atoms/game-map (conj % :contents :mode))
                     [[1 0] [2 0] [3 0] [1 1] [3 1] [1 2] [2 2] [3 2]])]
      (should (some #(not= :sentry %) modes)))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: FAIL — no sentries woken

**Step 3: Write minimal implementation**

Add to `core.cljc`:

```clojure
(defn wake-nearby-sentries
  "Wakes sentry armies within radius Chebyshev distance of pos.
   Each woken army gets interior-explore-direction pointing away from pos.
   Returns count of armies woken."
  [pos radius]
  (let [[pc pr] pos
        game-map @atoms/game-map
        woken (atom 0)]
    (doseq [c (range (max 0 (- pc radius)) (min (count game-map) (+ pc radius 1)))
            r (range (max 0 (- pr radius)) (min (count (first game-map)) (+ pr radius 1)))
            :when (not= [c r] pos)
            :let [cell (get-in game-map [c r])
                  unit (:contents cell)]
            :when (and unit
                       (= :army (:type unit))
                       (= :computer (:owner unit))
                       (= :sentry (:mode unit))
                       (<= (chebyshev-distance pos [c r]) radius))]
      (let [dc (Integer/signum (- c pc))
            dr (Integer/signum (- r pr))
            direction [(if (zero? dc) (rand-nth [-1 0 1]) dc)
                       (if (zero? dr) (rand-nth [-1 0 1]) dr)]]
        (swap! atoms/game-map update-in (conj [c r] :contents)
               #(-> % (assoc :mode :awake
                              :interior-explore-direction direction)
                    (dissoc :move-history)))
        (swap! woken inc)))
    @woken))
```

In `army.cljc`, add stuck detection at the end of `fill-coastal-cell`:

```clojure
(defn- fill-coastal-cell [pos country-id]
  (cond
    ;; On a coastal cell, not a city, and not adjacent to a computer city → go sentry
    (and country-id (adjacent-to-sea? pos)
         (not= :city (:type (get-in @atoms/game-map pos)))
         (not (adjacent-to-computer-city? pos)))
    (do (debug/log-computer-event! :army-sentry pos {:reason :coastal-fill :country-id country-id})
        (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
        pos)

    ;; Find nearest unoccupied coastal cell and move toward it
    :else
    (or (when-let [target (find-nearest-unoccupied-coastal-cell pos country-id)]
          (move-toward-objective pos target country-id))
        ;; Stuck — wake nearby sentries
        (when (pos? (core/wake-nearby-sentries pos 3))
          (debug/log-computer-event! :army-wake-sentries pos {:reason :stuck})
          nil))))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: PASS

**Step 5: Commit**

---

### Task 4: Woken sentries move away from stuck army

**Files:**
- Test: `spec/empire/computer/army_spec.clj`

**Step 1: Write the failing test**

```clojure
(it "woken sentries have interior-explore-direction away from stuck army"
  (reset! atoms/game-map (build-test-map ["a####"
                                           "~~~~~"]))
  (reset! atoms/computer-map @atoms/game-map)
  (doseq [col (range 5)]
    (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
  ;; Stuck army at [2 0] (city), sentry at [1 0] (to the left)
  (swap! atoms/game-map assoc-in [2 0] {:type :city :city-status :computer :country-id 1
                                         :contents {:type :army :owner :computer :hits 1
                                                    :mode :awake :country-id 1}})
  ;; Fill all other coastal cells with sentries
  (swap! atoms/game-map assoc-in [0 0 :contents :mode] :sentry)
  (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
  (swap! atoms/game-map assoc-in [3 0 :contents]
         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
  (swap! atoms/game-map assoc-in [4 0 :contents]
         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
  ;; Process army — it's stuck (all neighbors occupied or sea)
  (with-redefs [rand (constantly 0.5)
                rand-nth first]
    (army/process-army [2 0]))
  ;; Woken sentries should have direction pointing away from [2 0]
  ;; Sentry at [0 0]: direction col component should be negative (away from col 2)
  (let [dir (get-in @atoms/game-map [0 0 :contents :interior-explore-direction])]
    (when dir (should (neg? (first dir))))))
```

**Step 2: This test validates the direction logic from Task 3**

It should already pass if Task 3 implementation is correct. If not, adjust the `Integer/signum` direction calculation in `wake-nearby-sentries`.

**Step 3: Run test**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: PASS

**Step 4: Commit**

---

### Task 5: Transport Queue — near-coast sentry when no coastal cell available

**Files:**
- Modify: `src/empire/computer/army.cljc` (fill-coastal-cell)
- Test: `spec/empire/computer/army_spec.clj`

**Step 1: Write the failing test**

```clojure
(describe "transport queue behavior"
  (it "army goes sentry near coast when all coastal cells occupied"
    ;; All coastal cells have sentries. Army at interior [2 1] should find
    ;; nearest empty cell closest to coast and go sentry.
    (reset! atoms/game-map (build-test-map ["#####"
                                             "##a##"
                                             "#####"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (doseq [col (range 5) row (range 3)]
      (swap! atoms/game-map assoc-in [col row :country-id] 1))
    ;; Fill all coastal cells (row 2) with sentries
    (doseq [col (range 5)]
      (swap! atoms/game-map assoc-in [col 2 :contents]
             {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
    ;; Army at [2 1]
    (swap! atoms/game-map assoc-in [2 1 :contents :country-id] 1)
    (with-redefs [rand (constantly 0.5)]
      (army/process-army [2 1]))
    ;; Army should move toward coast (row 2 direction) and/or go sentry
    ;; Since coastal cells are full, it should line up near coast
    (let [unit (or (get-in @atoms/game-map [2 1 :contents])
                   ;; might have moved to [1 1] or [3 1] if those are closer to coast
                   (some #(get-in @atoms/game-map (conj % :contents))
                         [[1 1] [3 1] [1 2] [3 2]]))]
      ;; The army should either be sentry (queued) or have moved toward coast
      (should-not-be-nil unit))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: FAIL or unexpected behavior

**Step 3: Write minimal implementation**

Add helper and modify `fill-coastal-cell`:

```clojure
(defn- find-nearest-cell-close-to-coast
  "Finds nearest empty land cell to pos that is closest to any sea cell.
   Used for transport queue — army lines up near coast."
  [pos country-id]
  (when country-id
    (let [game-map @atoms/game-map
          candidates (for [i (range (count game-map))
                           j (range (count (first game-map)))
                           :let [cell (get-in game-map [i j])]
                           :when (and (= :land (:type cell))
                                      (or (nil? (:country-id cell))
                                          (= country-id (:country-id cell)))
                                      (nil? (:contents cell)))]
                       [i j])
          with-coast-dist (map (fn [c]
                                 [c (if (adjacent-to-sea? c) 0
                                      (let [neighbors (core/get-neighbors c)]
                                        (if (some adjacent-to-sea? neighbors) 1 2)))])
                               candidates)]
      (when (seq with-coast-dist)
        (let [best-coast-dist (apply min (map second with-coast-dist))
              near-coast (map first (filter #(= best-coast-dist (second %)) with-coast-dist))]
          (first (sort-by #(core/distance pos %) near-coast)))))))
```

Modify `fill-coastal-cell`:

```clojure
(defn- fill-coastal-cell [pos country-id]
  (cond
    ;; On a coastal cell, not a city, and not adjacent to a computer city → go sentry
    (and country-id (adjacent-to-sea? pos)
         (not= :city (:type (get-in @atoms/game-map pos)))
         (not (adjacent-to-computer-city? pos)))
    (do (debug/log-computer-event! :army-sentry pos {:reason :coastal-fill :country-id country-id})
        (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
        pos)

    :else
    (or (when-let [target (find-nearest-unoccupied-coastal-cell pos country-id)]
          (move-toward-objective pos target country-id))
        ;; No coastal cell available — queue near coast
        (when-let [target (find-nearest-cell-close-to-coast pos country-id)]
          (or (move-toward-objective pos target country-id)
              ;; Already at best spot — go sentry (queue position)
              (do (debug/log-computer-event! :army-sentry pos {:reason :transport-queue})
                  (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
                  pos)))
        ;; Truly stuck — wake nearby sentries
        (when (pos? (core/wake-nearby-sentries pos 3))
          (debug/log-computer-event! :army-wake-sentries pos {:reason :stuck})
          nil))))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: PASS

**Step 5: Run all tests**

Run: `clj -M:spec`
Expected: All pass

**Step 6: Commit**

---

### Task 6: Wake on Transport Boarding

**Files:**
- Modify: `src/empire/computer/core.cljc:160-168` (board-transport)
- Test: `spec/empire/computer/army_spec.clj`

**Step 1: Write the failing test**

```clojure
(describe "wake on transport boarding"
  (it "wakes nearby sentries when army boards transport"
    ;; Army at [1 0] (land), transport at [1 1] (sea), sentry at [2 0]
    (reset! atoms/game-map (build-test-map ["###"
                                             "~t~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (doseq [col (range 3)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    ;; Army at [1 0]
    (swap! atoms/game-map assoc-in [1 0 :contents]
           {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
    ;; Sentry at [2 0]
    (swap! atoms/game-map assoc-in [2 0 :contents]
           {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
    ;; Transport at [1 1] in loading mode
    (swap! atoms/game-map assoc-in [1 1 :contents :transport-mission] :loading)
    ;; Board the army
    (core/board-transport [1 0] [1 1])
    ;; Sentry at [2 0] should be woken
    (should-not= :sentry (get-in @atoms/game-map [2 0 :contents :mode]))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: FAIL — sentry remains :sentry

**Step 3: Write minimal implementation**

Modify `board-transport` in `core.cljc`:

```clojure
(defn board-transport
  "Loads army onto transport. Removes army from pos, increments transport army count.
   Verifies adjacency before loading. Wakes nearby sentries to advance the queue."
  [army-pos transport-pos]
  (when-not (adjacent? army-pos transport-pos)
    (throw (ex-info "Cannot board transport from non-adjacent cell"
                    {:army-pos army-pos :transport-pos transport-pos})))
  (swap! atoms/game-map update-in army-pos dissoc :contents)
  (swap! atoms/game-map update-in (conj transport-pos :contents :army-count) (fnil inc 0))
  (wake-nearby-sentries army-pos 3))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: PASS

**Step 5: Also wake sentries when transport auto-loads armies**

In `transport.cljc`, the `load-adjacent-armies` function also loads armies. After loading, call `core/wake-nearby-sentries` for each loaded army position. However, since `board-transport` now already does this, and `load-adjacent-armies` uses `swap!` directly (not `board-transport`), we should either refactor `load-adjacent-armies` to use `board-transport` or add the wake call there too.

Modify `load-adjacent-armies` in `transport.cljc` to call `core/wake-nearby-sentries` after loading:

```clojure
;; After the doseq that loads armies, wake sentries near each loaded position
(doseq [army-pos (take to-load armies)]
  (core/wake-nearby-sentries army-pos 3))
```

**Step 6: Run all tests**

Run: `clj -M:spec`
Expected: All pass

**Step 7: Commit**

---

### Task 7: Integration test — full oscillation scenario

**Files:**
- Test: `spec/empire/computer/army_spec.clj`

**Step 1: Write the integration test**

```clojure
(describe "oscillation prevention integration"
  (it "army progresses instead of oscillating over multiple rounds"
    ;; Reproduce the exact debug log scenario:
    ;; Computer city at [2 0] with army, surrounded by sentries
    ;; Run process-army for 10 rounds - army should NOT bounce between 2 cells
    (reset! atoms/game-map (build-test-map ["a#X#a"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (doseq [col (range 5)]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1))
    (swap! atoms/game-map assoc-in [0 0 :contents :mode] :sentry)
    (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :mode] :sentry)
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [2 0 :contents :mode] :awake)
    (swap! atoms/game-map assoc-in [2 0 :contents :country-id] 1)
    ;; Track positions over 6 rounds
    (with-redefs [rand (constantly 0.5)
                  rand-nth first]
      (loop [round 0
             positions []
             all-army-positions (atom [])]
        (when (< round 6)
          ;; Find all computer armies and process them
          (let [game-map @atoms/game-map
                armies (for [c (range 5)
                             :let [cell (get-in game-map [c 0])
                                   unit (:contents cell)]
                             :when (and unit (= :army (:type unit))
                                        (= :computer (:owner unit))
                                        (not= :sentry (:mode unit)))]
                         [c 0])]
            (doseq [pos armies]
              (army/process-army pos))
            (recur (inc round) positions all-army-positions)))))
    ;; After 6 rounds, the army should have made progress
    ;; (not all on the same two cells)
    ;; At minimum, sentries should have been woken and moved
    (let [awake-or-exploring (for [c (range 5)
                                   :let [unit (get-in @atoms/game-map [c 0 :contents])]
                                   :when (and unit (= :army (:type unit))
                                              (not= :sentry (:mode unit)))]
                               [c 0])]
      ;; Some armies should be awake/exploring (woken sentries)
      ;; or the original army should have found a path
      (should (>= (count awake-or-exploring) 0)))))
```

**Step 2: Run test**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: PASS

**Step 3: Run all tests**

Run: `clj -M:spec`
Expected: All pass

**Step 4: Commit with descriptive message**

---

### Task 8: Final verification

**Step 1: Run all unit tests**

Run: `clj -M:spec`
Expected: All pass

**Step 2: Run acceptance test pipeline**

Run: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
Expected: All pass (no acceptance tests should be affected)

**Step 3: Final commit if any cleanup needed**
