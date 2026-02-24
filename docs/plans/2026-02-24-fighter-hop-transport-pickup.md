# Fighter Hop-Over & Transport Post-Unload Pickup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Computer fighters hop over friendly units instead of sidestepping; transports BFS for sentry armies after unloading.

**Architecture:** Feature 1 replaces `move-toward-with-sidestep` in `computer/fighter.cljc` with a `hop-over-friendly` function that scans forward along the flight direction, skipping computer-occupied cells. Feature 2 replaces `find-next-pickup-continent-pos` in `computer/transport.cljc` with a BFS for the nearest sentry coastal army with a different country-id, adding a 10-round wait-and-retry when none found.

**Tech Stack:** Clojure, Speclj, acceptance test pipeline (parse/generate/run)

---

## Part A: Computer Fighter Hop-Over

### Task 1: Core hop-over function — basic hop

**Files:**
- Test: `spec/empire/computer/fighter_spec.clj`
- Modify: `src/empire/computer/fighter.cljc:34-48`

**Step 1: Write the failing test**

In `spec/empire/computer/fighter_spec.clj`, add a new `(describe "hop-over-friendly"` block. First test: fighter hops over one friendly unit to land on the empty cell beyond it.

```clojure
(describe "hop-over-friendly"
  (it "hops over one friendly unit toward target"
    ;; 5x1 map: fighter at [0,0], friendly army at [0,1], target at [0,4]
    ;; Fighter should land at [0,2], skipping [0,1]
    (let [game-map (mapmaker/make-game-map 1 5 :sea)]
      (reset! atoms/game-map
        (-> game-map
            (assoc-in [[0 0] :contents] {:type :fighter :owner :computer :fuel 32})
            (assoc-in [[0 1] :contents] {:type :army :owner :computer :hits 1})))
      (let [result (hop-over-friendly [0 0] [0 4] (core/get-neighbors [0 0]))]
        (should= [[0 2]] result)))))
```

Exact shape TBD — the function signature needs to return the sequence of cells hopped through (so the caller knows how many movement points to deduct), or just the landing cell plus a count. Decide during implementation based on how `process-fighter` consumes the result.

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/fighter_spec.clj`
Expected: FAIL — `hop-over-friendly` not defined

**Step 3: Write minimal implementation**

In `src/empire/computer/fighter.cljc`, replace `move-toward-with-sidestep` (lines 34-48) with:

```clojure
(defn- hop-over-friendly
  "When best neighbor toward target is occupied by a friendly unit,
   scan forward along the direction, skipping consecutive friendly-occupied cells.
   Returns {:dest pos :hops n} where n is cells traversed (including dest), or nil."
  [pos target passable-neighbors]
  (let [best (core/best-neighbor-toward pos target passable-neighbors)]
    (when best
      (if (not (friendly-occupied? best))
        {:dest best :hops 1}
        ;; Scan forward along direction [pos -> best]
        (let [dir (mapv - best pos)]
          (loop [current best hops 1]
            (let [next-cell (mapv + current dir)]
              (cond
                (not (core/in-bounds? next-cell)) nil
                (enemy-at? next-cell) {:dest next-cell :hops (inc hops) :attack true}
                (not (friendly-occupied? next-cell))
                  (when (passable-for-fighter? next-cell)
                    {:dest next-cell :hops (inc hops)})
                :else (recur next-cell (inc hops))))))))))
```

Exact helper names (`friendly-occupied?`, `enemy-at?`, `passable-for-fighter?`, `core/best-neighbor-toward`) to be determined during implementation from existing code. `core/best-neighbor-toward` should pick the neighbor with minimum distance to target, preferring diagonals (same logic as current sidestep scoring but without the unoccupied filter).

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/fighter_spec.clj`
Expected: PASS

**Step 5: Commit**

```
git add spec/empire/computer/fighter_spec.clj src/empire/computer/fighter.cljc
git commit -m "feat: hop-over-friendly core function — single unit hop"
```

---

### Task 2: Hop over multiple consecutive friendly units

**Files:**
- Test: `spec/empire/computer/fighter_spec.clj`
- Modify: `src/empire/computer/fighter.cljc`

**Step 1: Write the failing test**

```clojure
(it "hops over multiple consecutive friendly units"
  ;; fighter at [0,0], armies at [0,1] and [0,2], target at [0,4]
  ;; Should land at [0,3], hops = 3
  ...)
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/fighter_spec.clj`

**Step 3: Implement if needed (may already pass from Task 1's loop)**

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```
git commit -m "test: hop-over multiple consecutive friendly units"
```

---

### Task 3: Hop stops at enemy — attack instead

**Files:**
- Test: `spec/empire/computer/fighter_spec.clj`
- Modify: `src/empire/computer/fighter.cljc`

**Step 1: Write the failing test**

```clojure
(it "attacks enemy found during hop scan"
  ;; fighter at [0,0], friendly army at [0,1], player army at [0,2], target [0,4]
  ;; Should attack at [0,2], not hop past
  ...)
```

**Step 2-5: Red/green/commit cycle**

---

### Task 4: Hop returns nil when path runs off map

**Files:**
- Test: `spec/empire/computer/fighter_spec.clj`

**Step 1: Write the failing test**

```clojure
(it "returns nil when hop path runs off map"
  ;; fighter at [0,3] in a 1x5 map, friendly at [0,4], target at [0,4]
  ;; No room beyond [0,4] — return nil
  ...)
```

**Step 2-5: Red/green/commit cycle**

---

### Task 5: Hop costs movement points — integrate with process-fighter loop

**Files:**
- Test: `spec/empire/computer/fighter_spec.clj`
- Modify: `src/empire/computer/fighter.cljc:483-507`

**Step 1: Write the failing test**

Test that `process-fighter` on a fighter with speed 8, hopping over 2 friendlies, consumes 3 steps (2 hops + landing) and has fuel decremented by 3.

**Step 2: Run test to verify it fails**

**Step 3: Modify `step-fighter` and `process-fighter`**

Currently `step-fighter` returns a single position and `process-fighter` decrements by 1 each loop. Change `step-fighter` to return `{:pos new-pos :steps-used n}` or a similar structure so the loop can deduct the right number of steps. Each intermediate cell also needs `consume-fighter-fuel`.

Key change in `process-fighter` loop (lines 502-506):
```clojure
(loop [current-pos pos
       steps-remaining fighter-speed]
  (when (pos? steps-remaining)
    (when-let [{:keys [pos steps-used]} (step-fighter current-pos steps-remaining)]
      (recur pos (- steps-remaining steps-used)))))
```

Pass `steps-remaining` into `step-fighter` so it knows how far the hop can go.

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```
git commit -m "feat: hop-over consumes correct movement points and fuel"
```

---

### Task 6: Replace sidestep call sites with hop-over

**Files:**
- Modify: `src/empire/computer/fighter.cljc` — lines 121, 370, 388
- Test: `spec/empire/computer/fighter_spec.clj`

**Step 1: Write failing tests**

Tests for `navigate-toward-target`, `do-patrol`, and `move-and-consume-toward` that place a friendly unit in the path and verify the fighter hops instead of sidestepping.

**Step 2: Run tests to verify they fail**

**Step 3: Replace each call to `move-toward-with-sidestep` with the hop-over equivalent**

- `do-patrol` (line 121): replace `move-toward-with-sidestep` call
- `navigate-toward-target` (line 370): replace `move-toward-with-sidestep` call
- `move-and-consume-toward` (line 388): replace `move-toward-with-sidestep` call

Also update `explore-move-step` (line 214) which filters `(complement occupied?)` — apply hop-over logic there too.

**Step 4: Run all fighter tests**

Run: `clj -M:spec spec/empire/computer/fighter_spec.clj`

**Step 5: Commit**

```
git commit -m "feat: replace all fighter sidestep calls with hop-over"
```

---

### Task 7: Update existing sidestep unit tests for fighter behavior

**Files:**
- Modify: `spec/empire/movement/sidestep_spec.clj` — fighter-specific tests
- Modify: `spec/empire/movement/movement_spec.clj` — fighter sidestep tests

**Step 1: Update fighter tests in sidestep_spec.clj**

Tests at lines 103-124 (fighter sidesteps around friendly fighter), 256-271 (fighter sidesteps around free city), 273-288 (fighter sidesteps around player city), 290-308 (fighter does not sidestep target city), 310-325 (fighter sidesteps around hostile city).

These should now verify hop-over behavior instead of sidestep. For city tests: fighters should still be able to enter target cities (no hop needed). For non-target cities: the fighter hops over the city cell direction-wise.

Note: some of these tests may be for *player* fighters (movement module), not computer fighters. Only update tests that exercise computer fighter movement. Player fighter movement via keyboard input still uses the movement module's sidestep. Read each test carefully before changing.

**Step 2: Update fighter tests in movement_spec.clj**

Tests at lines 726-735, 737-746, 748-758. Same principle — only change tests for computer fighters.

**Step 3: Run all tests**

Run: `clj -M:spec`

**Step 4: Commit**

```
git commit -m "test: update sidestep tests for fighter hop-over behavior"
```

---

### Task 8: Add acceptance tests for fighter hop-over

**Files:**
- Modify: `acceptanceTests/fighter.txt`

**Step 1: Add acceptance test scenarios**

```
;===============================================================
; Computer fighter hops over friendly unit on flight path.
;===============================================================
GIVEN game map
  ~f~~~
  ~a~~~
  ~~~~~
  ~~~~~
  ~~~~X
f has fuel 32.
f has flight-target-site [4 4].
a is a computer army.

WHEN computer fighter f is processed.

THEN f is not at [0 1].
THEN a is at [1 1].

;===============================================================
; Computer fighter hops over multiple friendly units.
;===============================================================
...

;===============================================================
; Computer fighter hop costs fuel per cell.
;===============================================================
...
```

Exact syntax depends on what the acceptance test parser supports. Check `plans/permanent/parser-pattern-catalog.md` for available directives before writing. May need new directives for computer fighter processing.

**Step 2: Run acceptance pipeline**

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

**Step 3: Verify tests pass**

**Step 4: Commit**

```
git commit -m "test: acceptance tests for computer fighter hop-over"
```

---

### Task 9: Run full test suite and clean up

**Step 1: Run all unit tests**

Run: `clj -M:spec`

**Step 2: Run acceptance pipeline**

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

**Step 3: Fix any failures**

**Step 4: Remove dead code**

If `move-toward-with-sidestep` is no longer called by any fighter code path, and is not used by other modules, remove it. Check with grep first — it's private to fighter.cljc so it should be safe to remove if all fighter call sites are converted.

**Step 5: Commit**

```
git commit -m "chore: clean up unused sidestep code in fighter module"
```

---

## Part B: Transport Post-Unload Pickup

### Task 10: BFS for nearest sentry coastal army with different country-id

**Files:**
- Test: `spec/empire/computer/transport_spec.clj`
- Modify: `src/empire/computer/transport.cljc`

**Step 1: Write the failing test**

```clojure
(it "finds nearest sentry army on coast with different country-id"
  ;; Transport at [0,1] sea, unload-country-id 5
  ;; Army at [1,0] land (coastal), sentry, country-id 3
  ;; Army at [3,0] land (coastal), sentry, country-id 5 (same — skip)
  ;; Should find [1,0]
  ...)
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/transport_spec.clj`

**Step 3: Write `find-nearest-pickup-army`**

```clojure
(defn- find-nearest-pickup-army
  "BFS from transport pos over sea cells to find nearest sentry army
   on a coastal land cell with country-id != exclude-cid."
  [pos exclude-cid]
  ;; BFS over sea cells, at each cell check adjacent land for sentry armies
  ;; with country-id != exclude-cid. Return first match (nearest by BFS distance).
  ...)
```

The BFS walks sea cells (like existing `has-nearby-loadable-armies?` pattern). At each sea cell, check all adjacent land cells for a computer army in `:sentry` mode on a coastal cell with a different country-id.

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```
git commit -m "feat: find-nearest-pickup-army BFS for sentry coastal armies"
```

---

### Task 11: BFS returns nil when no qualifying army exists

**Files:**
- Test: `spec/empire/computer/transport_spec.clj`

**Step 1: Write the failing test**

```clojure
(it "returns nil when no sentry army with different country-id exists"
  ;; Only armies on coast have same country-id as transport
  ...)
```

**Step 2-5: Red/green/commit cycle**

---

### Task 12: Update transition-to-loading to use BFS

**Files:**
- Test: `spec/empire/computer/transport_spec.clj`
- Modify: `src/empire/computer/transport.cljc:506-515`

**Step 1: Write the failing test**

```clojure
(it "after unloading, sets pickup-target to nearest sentry army with different country-id"
  ;; Transport unloads last army, transitions to loading
  ;; Should find sentry army on coast and set :pickup-target
  ...)
```

**Step 2: Run test to verify it fails**

**Step 3: Rewrite `transition-to-loading`**

```clojure
(defn- transition-to-loading
  "Switch empty transport to loading. BFS for nearest sentry army
   with different country-id. If none found, enter waiting state."
  [pos]
  (set-transport-mission pos :loading)
  (swap! atoms/game-map update-in (conj pos :contents)
         dissoc :unload-target-city :pickup-continent-pos :pickup-country-id)
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        exclude-cid (:unload-country-id transport)
        target (find-nearest-pickup-army pos exclude-cid)]
    (if target
      (swap! atoms/game-map assoc-in (conj pos :contents :pickup-target) target)
      (swap! atoms/game-map assoc-in (conj pos :contents :waiting-since) @atoms/round-number))))
```

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```
git commit -m "feat: transition-to-loading uses BFS for sentry army pickup"
```

---

### Task 13: Waiting state — transport waits 10 rounds then retries

**Files:**
- Test: `spec/empire/computer/transport_spec.clj`
- Modify: `src/empire/computer/transport.cljc` (loading branch of `process-transport`)

**Step 1: Write the failing test**

```clojure
(it "waits 10 rounds then retries BFS when no pickup army found"
  ;; Transport in loading with :waiting-since round 5, current round 14
  ;; No armies available — should still wait
  ;; Advance to round 15 — should retry BFS
  ...)
```

**Step 2: Run test to verify it fails**

**Step 3: Add waiting logic to loading branch**

In the loading section of `process-transport` (lines 625-649), before the existing load/sail logic, check for waiting state:

```clojure
;; Check waiting state
(if-let [waiting-since (:waiting-since transport)]
  (when (>= (- @atoms/round-number waiting-since) 10)
    ;; Retry BFS
    (let [target (find-nearest-pickup-army pos (:unload-country-id transport))]
      (if target
        (do (swap! atoms/game-map update-in (conj pos :contents) dissoc :waiting-since)
            (swap! atoms/game-map assoc-in (conj pos :contents :pickup-target) target))
        ;; Reset wait timer
        (swap! atoms/game-map assoc-in (conj pos :contents :waiting-since) @atoms/round-number))))
  ;; Normal loading behavior...
  ...)
```

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```
git commit -m "feat: transport waits 10 rounds and retries pickup BFS"
```

---

### Task 14: Navigate toward pickup-target in loading mode

**Files:**
- Test: `spec/empire/computer/transport_spec.clj`
- Modify: `src/empire/computer/transport.cljc` (loading branch)

**Step 1: Write the failing test**

```clojure
(it "navigates toward pickup-target during loading"
  ;; Transport at [0,0], pickup-target at [0,5] (sentry army on coast)
  ;; Should move toward [0,5] via move-toward-position
  ...)
```

**Step 2: Run test to verify it fails**

**Step 3: Replace pickup-continent-pos navigation with pickup-target**

In the loading branch (lines 644-648), replace:
```clojure
(if-let [pcp (:pickup-continent-pos transport')]
  (or (move-toward-position pos pcp) (coastal-crawl-move pos))
  (coastal-crawl-move pos))
```
with:
```clojure
(if-let [pt (:pickup-target transport')]
  (or (move-toward-position pos pt) (coastal-crawl-move pos))
  (coastal-crawl-move pos))
```

Also update the "clear pickup target when arrived" logic (lines 631-635) to use `:pickup-target` instead of `:pickup-continent-pos`.

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```
git commit -m "feat: transport navigates toward pickup-target in loading mode"
```

---

### Task 15: Re-run BFS when target army gone on arrival

**Files:**
- Test: `spec/empire/computer/transport_spec.clj`
- Modify: `src/empire/computer/transport.cljc`

**Step 1: Write the failing test**

```clojure
(it "re-runs BFS when pickup target army is gone"
  ;; Transport arrives at pickup-target but army has moved/died
  ;; Should find a new pickup target via BFS
  ...)
```

**Step 2-5: Red/green/commit cycle**

When the transport clears its pickup-target (arrived at destination), check if there are loadable armies nearby. If not, re-run `find-nearest-pickup-army` to get a new target.

---

### Task 16: Move country-id minting from sail-start to load-start

**Files:**
- Test: `spec/empire/computer/transport_spec.clj`
- Modify: `src/empire/computer/transport.cljc:539-544`

**Step 1: Write the failing test**

```clojure
(it "mints unload-country-id when first army loaded"
  ;; Transport in loading mode, no unload-country-id
  ;; Loads first army — should mint new country-id
  ...)
```

**Step 2: Run test to verify it fails**

**Step 3: Move `mint-unload-country-id` call**

Remove from `start-sailing` (line 544). Add to `load-adjacent-armies` — when army-count goes from 0 to 1, call `mint-unload-country-id`.

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```
git commit -m "feat: mint unload-country-id at load-start instead of sail-start"
```

---

### Task 17: Remove pickup-continent-pos and related dead code

**Files:**
- Modify: `src/empire/computer/transport.cljc`
- Test: `spec/empire/computer/transport_spec.clj`

**Step 1: Remove `find-next-pickup-continent-pos` (lines 155-192)**

This function is replaced by `find-nearest-pickup-army`.

**Step 2: Remove `record-pickup-continent-pos` and `:pickup-country-id` references**

Search for all uses of `:pickup-continent-pos`, `:pickup-country-id`, `record-pickup-continent-pos`, `adjacent-to-pickup-continent?`, `exclude-country-ids`, `pickup-continent-for-exclusion` and remove or replace.

**Step 3: Update any tests still referencing removed fields**

**Step 4: Run all tests**

Run: `clj -M:spec`

**Step 5: Commit**

```
git commit -m "chore: remove pickup-continent-pos and related dead code"
```

---

### Task 18: Update existing transport acceptance tests

**Files:**
- Modify: `acceptanceTests/computer-transport.txt`
- Modify: `acceptanceTests/sailing-transport.txt`

**Step 1: Review and update affected scenarios**

- `sailing-transport.txt` line 107-119: "Empty sailing transport transitions to loading" — may need to verify pickup-target instead of pickup-continent-pos
- `computer-transport.txt`: review any scenarios that reference pickup-continent-pos

**Step 2: Run acceptance pipeline**

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

**Step 3: Commit**

```
git commit -m "test: update transport acceptance tests for pickup-target"
```

---

### Task 19: Add new acceptance tests for transport pickup

**Files:**
- Modify: `acceptanceTests/computer-transport.txt`

**Step 1: Add acceptance test scenarios**

```
;===============================================================
; Transport after unloading finds sentry army on different-country coast.
;===============================================================
...

;===============================================================
; Transport waits when no pickup army available.
;===============================================================
...

;===============================================================
; Transport retries pickup search after 10 rounds.
;===============================================================
...
```

Check `plans/permanent/parser-pattern-catalog.md` for available directives. May need new directives for setting country-id, waiting-since, and round number.

**Step 2: Run acceptance pipeline**

**Step 3: Commit**

```
git commit -m "test: acceptance tests for transport post-unload pickup"
```

---

### Task 20: Full test suite and final cleanup

**Step 1: Run all unit tests**

Run: `clj -M:spec`

**Step 2: Run acceptance pipeline**

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

**Step 3: Fix any failures**

**Step 4: Run the game with seed 42 and observe both behaviors**

```bash
clj -M:run --seed=42
```

Verify: computer fighters hop over friendly units; transports seek sentry armies after unloading.

**Step 5: Commit**

```
git commit -m "feat: fighter hop-over and transport post-unload pickup complete"
```
