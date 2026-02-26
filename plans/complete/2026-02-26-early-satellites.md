# Early Satellites Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Produce satellites and patrol boats earlier by triggering one-shot production when the first transport is fully loaded, reduce army overproduction, and time-limit random exploration.

**Architecture:** Three boolean atoms gate one-shot early production. The trigger fires in `start-sailing`. Production decision checks the flags before normal rules. Army limit reduced to 2/3 coastal cells. Random explore gets a 10-round counter.

**Tech Stack:** Clojure, Speclj, crap4clj

---

### Task 1: Add three boolean atoms

**Files:**
- Modify: `src/empire/atoms.cljc:205-208` (after `land-ho-targets`)
- Modify: `src/empire/test_utils.cljc:223` (after `land-ho-targets` reset)
- Modify: `src/empire/save_load.cljc:29-30` (after `land-ho-targets` entry)

**Step 1: Add atoms to `atoms.cljc`**

After the `land-ho-targets` def (line 208), add:

```clojure
(def transport-fully-loaded?
  "Set true when any computer transport first reaches full army load. Never reset."
  (atom false))

(def early-patrol-boat-produced?
  "Set true when the early patrol boat enters production. Never reset."
  (atom false))

(def early-satellite-produced?
  "Set true when the early satellite enters production. Never reset."
  (atom false))
```

**Step 2: Add resets to `test_utils.cljc`**

After `(reset! atoms/land-ho-targets [])` (line 223), add:

```clojure
(reset! atoms/transport-fully-loaded? false)
(reset! atoms/early-patrol-boat-produced? false)
(reset! atoms/early-satellite-produced? false)
```

**Step 3: Add to `save_load.cljc`**

After the `:land-ho-targets` entry (line 29), add:

```clojure
:transport-fully-loaded? atoms/transport-fully-loaded?
:early-patrol-boat-produced? atoms/early-patrol-boat-produced?
:early-satellite-produced? atoms/early-satellite-produced?
```

**Step 4: Run tests to verify nothing broke**

Run: `clj -M:spec spec/empire/save_load_spec.clj`
Expected: PASS

**Step 5: Commit**

```
git add src/empire/atoms.cljc src/empire/test_utils.cljc src/empire/save_load.cljc
git commit -m "feat: add early-satellite boolean atoms"
```

---

### Task 2: Fire trigger when transport starts sailing

**Files:**
- Modify: `src/empire/computer/transport.cljc:518-523` (`start-sailing` function)
- Test: `spec/empire/computer/transport_spec.clj` (new context)

**Step 1: Write the failing test**

Add a new context in `spec/empire/computer/transport_spec.clj`:

```clojure
(context "transport-fully-loaded trigger"
  (it "sets transport-fully-loaded? when transport starts sailing"
    (let [game-map (tu/build-test-map ["~t~"
                                       "###"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (tu/set-test-unit atoms/game-map "t"
                        :transport-mission :loading
                        :army-count 6
                        :country-id 1
                        :transport-id 1)
      (should= false @atoms/transport-fully-loaded?)
      (transport/process-transport [0 1])
      (should= true @atoms/transport-fully-loaded?)))

  (it "does not re-set when already true"
    (let [game-map (tu/build-test-map ["~t~"
                                       "###"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (reset! atoms/transport-fully-loaded? true)
      (tu/set-test-unit atoms/game-map "t"
                        :transport-mission :loading
                        :army-count 6
                        :country-id 1
                        :transport-id 1)
      (transport/process-transport [0 1])
      (should= true @atoms/transport-fully-loaded?))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/transport_spec.clj`
Expected: First test FAILS (atom stays false)

**Step 3: Add trigger to `start-sailing`**

In `src/empire/computer/transport.cljc`, modify `start-sailing` (line 518). Add after line 522 (`mint-unload-event-id`):

```clojure
(when-not @atoms/transport-fully-loaded?
  (reset! atoms/transport-fully-loaded? true))
```

Ensure `empire.atoms` is already in the require (it is).

**Step 4: Run tests**

Run: `clj -M:spec spec/empire/computer/transport_spec.clj`
Expected: PASS

**Step 5: Commit**

```
git add src/empire/computer/transport.cljc spec/empire/computer/transport_spec.clj
git commit -m "feat: fire trigger when transport starts sailing"
```

---

### Task 3: Early production rules

**Files:**
- Modify: `src/empire/computer/production.cljc:367-380` (`decide-production`)
- Test: `spec/empire/computer/production_spec.clj` (new contexts)

**Step 1: Write failing tests**

Add new contexts in `spec/empire/computer/production_spec.clj`:

```clojure
(context "early production"
  (it "produces patrol boat from coastal city when trigger fired"
    (let [game-map (tu/build-test-map ["X~~"
                                       "###"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/transport-fully-loaded? true)
      (reset! atoms/early-patrol-boat-produced? false)
      (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
      (should= :patrol-boat (production/decide-production [0 0]))))

  (it "produces satellite from inland city after patrol boat flag set"
    (let [game-map (tu/build-test-map ["X#X"
                                       "###"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/transport-fully-loaded? true)
      (reset! atoms/early-patrol-boat-produced? true)
      (reset! atoms/early-satellite-produced? false)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
      ;; [1 0] is inland (surrounded by land)
      (should= :satellite (production/decide-production [1 0]))))

  (it "does not produce satellite before patrol boat flag set"
    (let [game-map (tu/build-test-map ["X#X"
                                       "###"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/transport-fully-loaded? true)
      (reset! atoms/early-patrol-boat-produced? false)
      (reset! atoms/early-satellite-produced? false)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
      ;; Should NOT be satellite since patrol boat hasn't been produced yet
      (should-not= :satellite (production/decide-production [1 0]))))

  (it "prefers inland city for satellite over coastal"
    (let [game-map (tu/build-test-map ["X~~"
                                       "###"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/transport-fully-loaded? true)
      (reset! atoms/early-patrol-boat-produced? true)
      (reset! atoms/early-satellite-produced? false)
      (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
      ;; [0 0] is coastal — should skip satellite, fall through to normal rules
      (should-not= :satellite (production/decide-production [0 0]))))

  (it "coastal city produces satellite when no inland city available"
    ;; This tests the fallback: if after one full round no inland city claimed it,
    ;; a coastal city takes it. Simulated by calling decide-production on a coastal
    ;; city when early-satellite-produced? is still false and patrol boat is done.
    ;; For this test, we need all computer cities to be coastal.
    (let [game-map (tu/build-test-map ["X~~"
                                       "~~~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/transport-fully-loaded? true)
      (reset! atoms/early-patrol-boat-produced? true)
      (reset! atoms/early-satellite-produced? false)
      (reset! atoms/round-number 2)
      (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
      (should= :satellite (production/decide-production [0 0])))))
```

**Step 2: Run tests to verify they fail**

Run: `clj -M:spec spec/empire/computer/production_spec.clj`
Expected: FAIL

**Step 3: Implement early production in `decide-production`**

In `src/empire/computer/production.cljc`, add a helper before `decide-production` (before line 367):

```clojure
(defn- has-inland-computer-city?
  "Returns true if any computer city is inland (not coastal)."
  []
  (some (fn [i]
          (some (fn [j]
                  (let [cell (get-in @atoms/game-map [i j])]
                    (and (= :city (:type cell))
                         (= :computer (:city-status cell))
                         (not (city-is-coastal? [i j])))))
                (range (count (first @atoms/game-map)))))
        (range (count @atoms/game-map))))

(defn- decide-early-production
  "One-shot early production after first transport fully loaded.
   Patrol boat first (coastal city), then satellite (inland preferred, coastal fallback).
   Returns unit type or nil."
  [city-pos coastal?]
  (when @atoms/transport-fully-loaded?
    (cond
      ;; Early patrol boat: coastal city, not yet produced
      (and coastal? (not @atoms/early-patrol-boat-produced?))
      (do (reset! atoms/early-patrol-boat-produced? true)
          :patrol-boat)

      ;; Early satellite: patrol boat done, not yet produced
      (and @atoms/early-patrol-boat-produced? (not @atoms/early-satellite-produced?))
      (cond
        ;; Inland city — always take it
        (not coastal?)
        (do (reset! atoms/early-satellite-produced? true)
            :satellite)

        ;; Coastal city — take it only if no inland city exists
        (not (has-inland-computer-city?))
        (do (reset! atoms/early-satellite-produced? true)
            :satellite)))))
```

Then modify `decide-production` (line 367) to call early production before normal rules. Replace lines 367-380:

```clojure
(defn decide-production
  "Decide what a computer city should produce. Returns unit type keyword.
   Early one-shot production first, then per-country priorities, then global."
  [city-pos]
  (let [city-cell (get-in @atoms/game-map city-pos)
        country-id (:country-id city-cell)
        coastal? (city-is-coastal? city-pos)
        unit-counts (count-computer-units)]
    (or (decide-early-production city-pos coastal?)
        (when country-id
          (decide-country-production city-pos country-id coastal? unit-counts))
        (when country-id
          (decide-global-production coastal? unit-counts))
        (when-not (and country-id (country-army-limit-reached? country-id))
          :army))))
```

**Step 4: Run tests**

Run: `clj -M:spec spec/empire/computer/production_spec.clj`
Expected: PASS

**Step 5: Commit**

```
git add src/empire/computer/production.cljc spec/empire/computer/production_spec.clj
git commit -m "feat: early patrol boat and satellite production"
```

---

### Task 4: Reduce army limit to 2/3 of coastal cells

**Files:**
- Modify: `src/empire/computer/production.cljc:286-291` (`country-army-limit-reached?`)
- Test: `spec/empire/computer/production_spec.clj` (new context)

**Step 1: Write the failing test**

Add a new context:

```clojure
(context "army limit 2/3"
  (it "army limit reached at 2/3 of coastal cells"
    (let [game-map (tu/build-test-map ["X###~"
                                       "#####"
                                       "~~~~~"])]
      (reset! atoms/game-map game-map)
      ;; Set country-id on all land/city cells
      (doseq [i (range 5) j (range 3)]
        (when (#{:land :city} (:type (get-in @atoms/game-map [i j])))
          (swap! atoms/game-map assoc-in [i j :country-id] 1)))
      ;; Count coastal cells, place 2/3 armies
      (let [coastal-count (production/count-country-coastal-cells 1)
            army-limit (int (* 2/3 coastal-count))]
        ;; Place army-limit armies on coastal cells
        ;; army-limit-reached? should be true
        (should (>= army-limit 1))
        (should (production/country-army-limit-reached? 1))))))
```

Note: The implementer should adjust the test map and army placement to verify the 2/3 threshold precisely — e.g., with 6 coastal cells, limit is 4; place 4 armies and check true, place 3 and check false.

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/production_spec.clj`
Expected: FAIL (currently uses 100% threshold)

**Step 3: Modify `country-army-limit-reached?`**

Replace lines 286-291:

```clojure
(defn- country-army-limit-reached?
  "Returns true if the country has at least 2/3 as many land armies as coastal land cells."
  [country-id]
  (let [coastal-cells (count-country-coastal-cells country-id)]
    (and (pos? coastal-cells)
         (>= (count-country-land-armies country-id) (* 2/3 coastal-cells)))))
```

**Step 4: Run tests**

Run: `clj -M:spec spec/empire/computer/production_spec.clj`
Expected: PASS

**Step 5: Run full computer tests**

Run: `clj -M:spec spec/empire/computer/`
Expected: PASS

**Step 6: Commit**

```
git add src/empire/computer/production.cljc spec/empire/computer/production_spec.clj
git commit -m "feat: reduce army limit to 2/3 of coastal cells"
```

---

### Task 5: Random explore 10-round timeout

**Files:**
- Modify: `src/empire/computer/army.cljc:381-425` (`process-move-inland` and `process-random-explore`)
- Test: `spec/empire/computer/army_spec.clj` (new contexts)

**Step 1: Write the failing test**

Add new contexts in `spec/empire/computer/army_spec.clj`:

```clojure
(context "random explore timeout"
  (it "initializes random-explore-rounds to 0 when entering random-explore"
    (let [game-map (tu/build-test-map ["###"
                                       "###"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (doseq [i (range 3) j (range 3)]
        (when (= :land (:type (get-in @atoms/game-map [i j])))
          (swap! atoms/game-map assoc-in [i j :country-id] 1)))
      ;; Place army at [1 1] (inland, not adjacent to sea) in :move-inland mode
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :move-inland :country-id 1})
      (with-redefs [rand-nth (constantly [0 -1])]
        (army/process-army [1 1]))
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should= :random-explore (:mode unit))
        (should= 0 (:random-explore-rounds unit)))))

  (it "transitions to awake after 10 rounds of random-explore"
    (let [game-map (tu/build-test-map ["#####"
                                       "#####"
                                       "#####"
                                       "#####"
                                       "~~~~~"])]
      (reset! atoms/game-map game-map)
      (doseq [i (range 5) j (range 5)]
        (when (= :land (:type (get-in @atoms/game-map [i j])))
          (swap! atoms/game-map assoc-in [i j :country-id] 1)))
      ;; Place army at interior cell in random-explore with 10 rounds elapsed
      (swap! atoms/game-map assoc-in [2 2 :contents]
             {:type :army :owner :computer :hits 1 :mode :random-explore
              :country-id 1 :random-explore-direction [0 1]
              :random-explore-rounds 10})
      (army/process-army [2 2])
      (let [unit (get-in @atoms/game-map [2 2 :contents])]
        (should= :awake (:mode unit))
        (should-be-nil (:random-explore-direction unit))
        (should-be-nil (:random-explore-rounds unit))))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: FAIL

**Step 3: Modify `process-move-inland` to initialize counter**

In `src/empire/computer/army.cljc`, modify line 386-388. Change:

```clojure
    (do (swap! atoms/game-map update-in (conj pos :contents)
               assoc :mode :random-explore
                     :random-explore-direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))
```

To:

```clojure
    (do (swap! atoms/game-map update-in (conj pos :contents)
               assoc :mode :random-explore
                     :random-explore-direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])
                     :random-explore-rounds 0)
```

**Step 4: Modify `process-random-explore` to check and increment counter**

In `process-random-explore` (line 399), add a timeout check at the top. Replace lines 399-425:

```clojure
(defn- process-random-explore
  "Moves army in stored random-explore direction. Goes sentry on coast or when blocked.
   Times out after 10 rounds and transitions to fill-coastal-cell."
  [pos country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        rounds (:random-explore-rounds unit 0)]
    (if (>= rounds 10)
      ;; Timeout: clear random-explore state, fall through to fill-coastal-cell next round
      (do (swap! atoms/game-map update-in (conj pos :contents)
                 #(-> % (assoc :mode :awake)
                        (dissoc :random-explore-direction :random-explore-rounds)))
          nil)
      (do (swap! atoms/game-map update-in (conj pos :contents)
                 update :random-explore-rounds (fnil inc 0))
          (if (and (adjacent-to-sea? pos)
                   (not= :city (:type (get-in @atoms/game-map pos))))
            (do (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
                pos)
            (let [[dc dr] (:random-explore-direction unit)
                  [c r] pos
                  target [(+ c dc) (+ r dr)]]
              (if (and (in-bounds? target)
                       (sovereign-passable? country-id (get-in @atoms/game-map target))
                       (nil? (:contents (get-in @atoms/game-map target)))
                       (try-move pos target))
                (when (adjacent-to-sea? target)
                  (swap! atoms/game-map assoc-in (conj target :contents :mode) :sentry)
                  target)
                ;; Blocked or off-map
                (if (= :city (:type (get-in @atoms/game-map pos)))
                  ;; At a city: try any empty neighbor to leave
                  (when-let [neighbors (seq (get-empty-passable-neighbors pos country-id))]
                    (try-move pos (rand-nth (vec neighbors))))
                  ;; Not at city: clear mode so army falls to fill-coastal-cell next round
                  (do (swap! atoms/game-map update-in (conj pos :contents)
                             #(-> % (assoc :mode :awake) (dissoc :random-explore-direction :random-explore-rounds)))
                      nil)))))))))
```

**Step 5: Run tests**

Run: `clj -M:spec spec/empire/computer/army_spec.clj`
Expected: PASS

**Step 6: Commit**

```
git add src/empire/computer/army.cljc spec/empire/computer/army_spec.clj
git commit -m "feat: random explore 10-round timeout"
```

---

### Task 6: Final verification

**Step 1: Run full test suite**

Run: `clj -M:spec`
Expected: PASS

**Step 2: Run acceptance tests**

Run: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
Expected: PASS

**Step 3: Remove early satellites from future issues**

In `plans/future-issues.md`, remove the line:
```
- **Early satellites**: Produce satellites early in the game for rapid map exploration.
```

**Step 4: Commit**

```
git add plans/future-issues.md
git commit -m "docs: remove completed early-satellites issue"
```
