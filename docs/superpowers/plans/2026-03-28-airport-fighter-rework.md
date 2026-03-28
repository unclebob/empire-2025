# Airport Fighter Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework airport fighter processing so only one launches on flight path, one always gets attention, and `u`/`s` control batch attention (mirroring the transport/army pattern).

**Architecture:** Remove round-start waking. Change item processing to launch one fighter on flight path, then present one for attention. Add `u`/`s` airport handlers using the same wake/sleep pattern as transports. Change production to put fighters directly into airport count.

**Tech Stack:** Clojure, Speclj

---

## File Structure

| File | Responsibility |
|------|---------------|
| `src/empire/game_mechanics/containers/helpers.cljc` | Add `remove-one-fighter` (decrement fighter-count only, no awake-fighters) |
| `src/empire/game_mechanics/containers/ops.cljc` | Add `wake-fighters-on-airport`, `sleep-fighters-on-airport`; modify `launch-fighter-from-airport` to use `remove-one-fighter` |
| `src/empire/game_mechanics/movement/movement_state.cljc` | Change `active-airport-fighter` to check `fighter-count > 0` |
| `src/empire/player/commands_action_decisions.cljc` | Add airport cases to `unload-key-action` and `sentry-key-action` |
| `src/empire/player/commands_actions.cljc` | Add airport handlers for `u` and `s` |
| `src/empire/player/attention_decisions.cljc` | Change attention check from `awake-fighters` to `fighter-count` |
| `src/empire/player/production_decisions.cljc` | Fighter production returns nil for contents |
| `src/empire/player/production.cljc` | Fighter spawn increments `fighter-count` instead of placing in contents |
| `src/empire/game/loop/item_processing.cljc` | Rework auto-launch (one only); requeue on `awake-fighters > 0` |
| `src/empire/game/loop/round_start.cljc` | Remove `wake-airport-fighters` call |
| `src/empire/game/loop/round_setup/waking.cljc` | Remove `wake-airport-fighters` function |

---

### Task 1: Add `remove-one-fighter` to container helpers

Currently `remove-awake-unit` decrements both `:fighter-count` and `:awake-fighters`. Under the new system, flight-path launches need to decrement only `:fighter-count` (since `awake-fighters` will normally be 0). Add a dedicated helper.

**Files:**
- Modify: `src/empire/game_mechanics/containers/helpers.cljc`
- Test: `spec/empire/game_mechanics/containers/helpers_spec.clj`

- [ ] **Step 1: Write the failing test**

In the helpers spec file, add:

```clojure
(it "removes one fighter from count without touching awake"
  (let [city {:type :city :fighter-count 3 :awake-fighters 0}
        result (uc/remove-one-fighter city)]
    (should= 2 (:fighter-count result))
    (should= 0 (:awake-fighters result))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/game_mechanics/containers/helpers_spec.clj`
Expected: FAIL — `remove-one-fighter` not defined

- [ ] **Step 3: Write minimal implementation**

In `helpers.cljc`, add after `remove-awake-unit`:

```clojure
(defn remove-one-fighter
  [entity]
  (update entity :fighter-count (fnil dec 0)))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/game_mechanics/containers/helpers_spec.clj`
Expected: PASS

- [ ] **Step 5: Run spec-structure-check**

Run: `clj -M:spec-structure-check spec/empire/game_mechanics/containers/helpers_spec.clj`
Expected: OK

- [ ] **Step 6: Commit**

```bash
git add src/empire/game_mechanics/containers/helpers.cljc spec/empire/game_mechanics/containers/helpers_spec.clj
git commit -m "Add remove-one-fighter helper for flight-path launches"
```

---

### Task 2: Add `wake-fighters-on-airport` and `sleep-fighters-on-airport` to container ops

These mirror `wake-fighters-on-carrier` / `sleep-fighters-on-carrier` but operate on the city cell directly (not on `:contents`).

**Files:**
- Modify: `src/empire/game_mechanics/containers/ops.cljc`
- Test: `spec/empire/game_mechanics/containers/ops_spec.clj` (or the existing airport spec)

- [ ] **Step 1: Write the failing test for wake**

```clojure
(it "wakes all fighters on airport"
  (let [city-coords [5 5]
        _ (test-utils/set-test-state! :game-map
            (assoc-in test-map city-coords
              {:type :city :city-status :player :fighter-count 3 :awake-fighters 0}))]
    (container-ops/wake-fighters-on-airport city-coords)
    (let [cell (get-in (test-utils/read-test-state :game-map) city-coords)]
      (should= 3 (:awake-fighters cell))
      (should= 3 (:fighter-count cell)))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/movement/fighter_airport_spec.clj`
Expected: FAIL — `wake-fighters-on-airport` not defined

- [ ] **Step 3: Implement `wake-fighters-on-airport`**

In `ops.cljc`, add in the Airport operations section:

```clojure
(defn wake-fighters-on-airport
  [city-coords]
  (let [cell (get-in (sa/current-world) city-coords)
        updated-cell (uc/wake-all cell :fighter-count :awake-fighters)]
    (sa/update-world! assoc-in city-coords updated-cell)))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/movement/fighter_airport_spec.clj`
Expected: PASS

- [ ] **Step 5: Write the failing test for sleep**

```clojure
(it "sleeps all fighters on airport"
  (let [city-coords [5 5]
        _ (test-utils/set-test-state! :game-map
            (assoc-in test-map city-coords
              {:type :city :city-status :player :fighter-count 3 :awake-fighters 2}))]
    (container-ops/sleep-fighters-on-airport city-coords)
    (let [cell (get-in (test-utils/read-test-state :game-map) city-coords)]
      (should= 0 (:awake-fighters cell))
      (should= 3 (:fighter-count cell)))))
```

- [ ] **Step 6: Run test to verify it fails**

Run: `clj -M:spec spec/empire/movement/fighter_airport_spec.clj`
Expected: FAIL — `sleep-fighters-on-airport` not defined

- [ ] **Step 7: Implement `sleep-fighters-on-airport`**

In `ops.cljc`, add after `wake-fighters-on-airport`:

```clojure
(defn sleep-fighters-on-airport
  [city-coords]
  (let [cell (get-in (sa/current-world) city-coords)
        updated-cell (uc/sleep-all cell :awake-fighters)]
    (sa/update-world! assoc-in city-coords updated-cell)))
```

- [ ] **Step 8: Run test to verify it passes**

Run: `clj -M:spec spec/empire/movement/fighter_airport_spec.clj`
Expected: PASS

- [ ] **Step 9: Run spec-structure-check and commit**

Run: `clj -M:spec-structure-check spec/empire/movement/fighter_airport_spec.clj`

```bash
git add src/empire/game_mechanics/containers/ops.cljc spec/empire/movement/fighter_airport_spec.clj
git commit -m "Add wake/sleep fighters on airport operations"
```

---

### Task 3: Modify `launch-fighter-from-airport` to use `remove-one-fighter`

Currently it calls `uc/remove-awake-unit cell :fighter-count :awake-fighters`. Change it to use `uc/remove-one-fighter` since awake-fighters will normally be 0 during flight-path launches.

**Files:**
- Modify: `src/empire/game_mechanics/containers/ops.cljc:192-215`
- Test: `spec/empire/movement/fighter_airport_spec.clj`

- [ ] **Step 1: Write the failing test**

Add a test that launches from an airport where `awake-fighters` is 0 (the new default):

```clojure
(it "launches fighter from airport with zero awake-fighters"
  (let [city-coords [5 5]
        target-coords [5 0]
        _ (test-utils/set-test-state! :game-map
            (assoc-in test-map city-coords
              {:type :city :city-status :player :fighter-count 2 :awake-fighters 0}))]
    (container-ops/launch-fighter-from-airport city-coords target-coords)
    (let [cell (get-in (test-utils/read-test-state :game-map) city-coords)]
      (should= 1 (:fighter-count cell))
      (should= 0 (:awake-fighters cell)))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/movement/fighter_airport_spec.clj`
Expected: FAIL — `awake-fighters` goes to -1 because `remove-awake-unit` decrements both

- [ ] **Step 3: Change `launch-fighter-from-airport` to use `remove-one-fighter`**

In `ops.cljc`, change line 206 from:

```clojure
        after-remove (uc/remove-awake-unit cell :fighter-count :awake-fighters)
```

to:

```clojure
        after-remove (uc/remove-one-fighter cell)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/movement/fighter_airport_spec.clj`
Expected: PASS

- [ ] **Step 5: Run full specs to check for regressions**

Run: `clj -M:spec`
Expected: All existing tests still pass (some may need updating in later tasks)

- [ ] **Step 6: Commit**

```bash
git add src/empire/game_mechanics/containers/ops.cljc spec/empire/movement/fighter_airport_spec.clj
git commit -m "Change launch-fighter-from-airport to use remove-one-fighter"
```

---

### Task 4: Change `active-airport-fighter` to check `fighter-count > 0`

Currently checks `awake-fighters > 0`. Change to check `fighter-count > 0` so that a synthetic fighter is presented for attention even when `awake-fighters` is 0.

**Files:**
- Modify: `src/empire/game_mechanics/movement/movement_state.cljc:58-61`
- Test: `spec/empire/movement/fighter_airport_spec.clj`

- [ ] **Step 1: Write the failing test**

```clojure
(it "creates synthetic fighter when fighter-count > 0 and awake-fighters is 0"
  (let [cell {:type :city :city-status :player :fighter-count 2 :awake-fighters 0}
        active (movement-state/get-active-unit cell)]
    (should= :fighter (:type active))
    (should= true (:from-airport active))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/movement/fighter_airport_spec.clj`
Expected: FAIL — returns nil because `awake-fighters` is 0

- [ ] **Step 3: Change `active-airport-fighter`**

In `movement_state.cljc`, change:

```clojure
(defn- active-airport-fighter
  [cell]
  (when (uc/has-awake? cell :awake-fighters)
    {:type :fighter :mode :awake :owner :player :fuel config/fighter-fuel :from-airport true}))
```

to:

```clojure
(defn- active-airport-fighter
  [cell]
  (when (pos? (:fighter-count cell 0))
    {:type :fighter :mode :awake :owner :player :fuel config/fighter-fuel :from-airport true}))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/movement/fighter_airport_spec.clj`
Expected: PASS

- [ ] **Step 5: Run spec-structure-check and commit**

Run: `clj -M:spec-structure-check spec/empire/movement/fighter_airport_spec.clj`

```bash
git add src/empire/game_mechanics/movement/movement_state.cljc spec/empire/movement/fighter_airport_spec.clj
git commit -m "Change active-airport-fighter to check fighter-count instead of awake-fighters"
```

---

### Task 5: Change attention detection to check `fighter-count > 0`

Update both `player-map-cell-needs-attention?` and `world-item-needs-attention?` in `attention_decisions.cljc`.

**Files:**
- Modify: `src/empire/player/attention_decisions.cljc:11-46`
- Test: `spec/empire/player/attention_decisions_spec.clj`

- [ ] **Step 1: Write the failing test**

```clojure
(it "city with fighter-count > 0 and awake-fighters 0 needs attention"
  (let [cell {:type :city :city-status :player :fighter-count 2 :awake-fighters 0}]
    (should (decisions/player-map-cell-needs-attention? cell {:item :army}))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/player/attention_decisions_spec.clj`
Expected: FAIL — `awake-fighters` is 0 so `has-awake?` returns false

- [ ] **Step 3: Change both functions**

In `attention_decisions.cljc`, in `player-map-cell-needs-attention?`, change:

```clojure
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
```

to:

```clojure
        has-airport-fighter? (pos? (:fighter-count cell 0))
```

And update all references from `has-awake-airport-fighter?` to `has-airport-fighter?` in that function.

Do the same in `world-item-needs-attention?`:

```clojure
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
```

to:

```clojure
        has-airport-fighter? (pos? (:fighter-count cell 0))
```

And update all references from `has-awake-airport-fighter?` to `has-airport-fighter?` in that function.

Also update `unit-needs-attention?` (line 62-69) — change:

```clojure
             (uc/has-awake? first-cell :awake-fighters)
```

to:

```clojure
             (pos? (:fighter-count first-cell 0))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/player/attention_decisions_spec.clj`
Expected: PASS

- [ ] **Step 5: Run spec-structure-check and commit**

Run: `clj -M:spec-structure-check spec/empire/player/attention_decisions_spec.clj`

```bash
git add src/empire/player/attention_decisions.cljc spec/empire/player/attention_decisions_spec.clj
git commit -m "Change attention detection to check fighter-count instead of awake-fighters"
```

---

### Task 6: Add airport cases to `u` and `s` decision logic

Add airport fighter handling to `unload-key-action` and `sentry-key-action` in `commands_action_decisions.cljc`.

**Files:**
- Modify: `src/empire/player/commands_action_decisions.cljc:25-42`
- Test: `spec/empire/player/commands_action_decisions_spec.clj`

- [ ] **Step 1: Write the failing test for `u` on airport fighter**

```clojure
(it "unload-key-action returns wake-fighters-on-airport for airport fighter"
  (let [cell {:type :city :city-status :player :fighter-count 3 :awake-fighters 0}
        active-unit {:type :fighter :from-airport true}]
    (should= {:action :wake-fighters-on-airport}
             (decisions/unload-key-action (:contents cell) cell active-unit))))
```

Note: `unload-key-action` currently only takes `contents`. It will need the cell and active-unit parameters too, so the signature must change.

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/player/commands_action_decisions_spec.clj`
Expected: FAIL — wrong arity or no matching case

- [ ] **Step 3: Change `unload-key-action` signature and add airport case**

Change `unload-key-action` from:

```clojure
(defn unload-key-action
  [contents]
  (cond
    (uc/transport-with-armies? contents) {:action :wake-armies-on-transport}
    (uc/carrier-with-fighters? contents) {:action :wake-fighters-on-carrier}
    :else nil))
```

to:

```clojure
(defn unload-key-action
  [contents cell active-unit]
  (cond
    (uc/transport-with-armies? contents) {:action :wake-armies-on-transport}
    (uc/carrier-with-fighters? contents) {:action :wake-fighters-on-carrier}
    (and (= :city (:type cell)) (pos? (:fighter-count cell 0)))
    {:action :wake-fighters-on-airport}
    :else nil))
```

- [ ] **Step 4: Update callers of `unload-key-action`**

In `commands_actions.cljc` line 57, change:

```clojure
  (case (:action (decisions/unload-key-action (:contents cell)))
```

to:

```clojure
  (case (:action (decisions/unload-key-action (:contents cell) cell active-unit))
```

This means `handle-unload-key` also needs the `active-unit` parameter. Change its signature from:

```clojure
(defn handle-unload-key [ctx coords cell]
```

to:

```clojure
(defn handle-unload-key [ctx coords cell active-unit]
```

And update the caller in `commands.cljc` where `handle-unload-key` is called, to pass `active-unit`.

- [ ] **Step 5: Run test to verify it passes**

Run: `clj -M:spec spec/empire/player/commands_action_decisions_spec.clj`
Expected: PASS

- [ ] **Step 6: Write the failing test for `s` on airport fighter**

```clojure
(it "sentry-key-action returns sleep-fighters-on-airport for airport fighter"
  (let [cell {:type :city :city-status :player :fighter-count 3 :awake-fighters 2}
        active-unit {:type :fighter :from-airport true :mode :awake}]
    (should= {:action :sleep-fighters-on-airport}
             (decisions/sentry-key-action cell active-unit))))
```

- [ ] **Step 7: Run test to verify it fails**

Run: `clj -M:spec spec/empire/player/commands_action_decisions_spec.clj`
Expected: FAIL — currently returns nil for airport fighters

- [ ] **Step 8: Change `sentry-key-action` to handle airport fighters**

Change:

```clojure
(defn sentry-key-action
  [cell active-unit]
  (let [is-army-aboard? (movement-state/is-army-aboard-transport? active-unit)
        is-carrier-fighter? (movement-state/is-fighter-from-carrier? active-unit)
        is-airport-fighter? (movement-state/is-fighter-from-airport? active-unit)]
    (cond
      is-army-aboard? {:action :sleep-armies-on-transport}
      is-carrier-fighter? {:action :sleep-fighters-on-carrier}
      (and (not= :city (:type cell)) (not is-airport-fighter?) (not is-carrier-fighter?))
      {:action :set-sentry-mode}
      :else nil)))
```

to:

```clojure
(defn sentry-key-action
  [cell active-unit]
  (let [is-army-aboard? (movement-state/is-army-aboard-transport? active-unit)
        is-carrier-fighter? (movement-state/is-fighter-from-carrier? active-unit)
        is-airport-fighter? (movement-state/is-fighter-from-airport? active-unit)]
    (cond
      is-army-aboard? {:action :sleep-armies-on-transport}
      is-carrier-fighter? {:action :sleep-fighters-on-carrier}
      is-airport-fighter? {:action :sleep-fighters-on-airport}
      (not= :city (:type cell))
      {:action :set-sentry-mode}
      :else nil)))
```

- [ ] **Step 9: Run test to verify it passes**

Run: `clj -M:spec spec/empire/player/commands_action_decisions_spec.clj`
Expected: PASS

- [ ] **Step 10: Run spec-structure-check and commit**

Run: `clj -M:spec-structure-check spec/empire/player/commands_action_decisions_spec.clj`

```bash
git add src/empire/player/commands_action_decisions.cljc spec/empire/player/commands_action_decisions_spec.clj
git commit -m "Add airport fighter cases to u and s decision logic"
```

---

### Task 7: Add airport handlers to `commands_actions.cljc`

Wire up the new decision actions to actual operations.

**Files:**
- Modify: `src/empire/player/commands_actions.cljc:56-87`
- Test: `spec/empire/player/commands_actions_spec.clj`

- [ ] **Step 1: Write the failing test for `u` on airport**

```clojure
(it "handle-unload-key wakes fighters on airport"
  ;; Set up city with fighter-count 3, awake-fighters 0
  ;; Call handle-unload-key
  ;; Verify awake-fighters = fighter-count - 1 = 2 (all except current)
  )
```

The exact test setup will depend on the test utilities pattern used in `commands_actions_spec.clj`. Follow the existing test patterns in that file.

Key assertion: after `handle-unload-key`, the city cell should have `awake-fighters = fighter-count - 1`.

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/player/commands_actions_spec.clj`
Expected: FAIL

- [ ] **Step 3: Add `:wake-fighters-on-airport` case to `handle-unload-key`**

Change `handle-unload-key` to:

```clojure
(defn handle-unload-key [ctx coords cell active-unit]
  (case (:action (decisions/unload-key-action (:contents cell) cell active-unit))
      :wake-armies-on-transport
      (do (container-ops/wake-armies-on-transport coords)
          (item-processed! ctx)
          true)

      :wake-fighters-on-carrier
      (do (container-ops/wake-fighters-on-carrier coords)
          (item-processed! ctx)
          true)

      :wake-fighters-on-airport
      (do (container-ops/wake-fighters-on-airport coords)
          (item-processed! ctx)
          true)

      nil))
```

Wait — the spec says `u` while a fighter has attention should set `awake-fighters = fighter-count - 1`. But `wake-fighters-on-airport` sets `awake-fighters = fighter-count`. We need to subtract 1 for the current fighter. So the handler should wake then decrement:

```clojure
      :wake-fighters-on-airport
      (do (container-ops/wake-fighters-on-airport coords)
          ;; Subtract 1 for the fighter currently getting attention
          (let [cell (get-in (current-world ctx) coords)
                adjusted (update cell :awake-fighters dec)]
            (update-game-map! ctx assoc-in coords adjusted))
          (item-processed! ctx)
          true)
```

Actually, a cleaner approach: add a dedicated `wake-other-fighters-on-airport` operation in `ops.cljc` that sets `awake-fighters = fighter-count - 1`. Or handle it inline. Let's keep it simple and handle inline.

- [ ] **Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/player/commands_actions_spec.clj`
Expected: PASS

- [ ] **Step 5: Write the failing test for `s` on airport**

```clojure
(it "handle-sentry-key sleeps fighters on airport"
  ;; Set up city with fighter-count 3, awake-fighters 2
  ;; Call handle-sentry-key with airport-fighter active-unit
  ;; Verify awake-fighters = 0
  )
```

- [ ] **Step 6: Run test to verify it fails**

Run: `clj -M:spec spec/empire/player/commands_actions_spec.clj`
Expected: FAIL

- [ ] **Step 7: Add `:sleep-fighters-on-airport` case to `handle-sentry-key`**

```clojure
(defn handle-sentry-key [ctx coords cell active-unit]
  (case (:action (decisions/sentry-key-action cell active-unit))
      :sleep-armies-on-transport
      (do (container-ops/sleep-armies-on-transport coords)
          (item-processed! ctx)
          true)

      :sleep-fighters-on-carrier
      (do (container-ops/sleep-fighters-on-carrier coords)
          (item-processed! ctx)
          true)

      :sleep-fighters-on-airport
      (do (container-ops/sleep-fighters-on-airport coords)
          (item-processed! ctx)
          true)

      :set-sentry-mode
      (do (movement-state/set-unit-mode coords :sentry)
          (item-processed! ctx)
          true)

      nil))
```

- [ ] **Step 8: Run test to verify it passes**

Run: `clj -M:spec spec/empire/player/commands_actions_spec.clj`
Expected: PASS

- [ ] **Step 9: Run spec-structure-check and commit**

Run: `clj -M:spec-structure-check spec/empire/player/commands_actions_spec.clj`

```bash
git add src/empire/player/commands_actions.cljc spec/empire/player/commands_actions_spec.clj
git commit -m "Add airport wake/sleep handlers for u and s keys"
```

---

### Task 8: Change fighter production to increment airport count

Fighters should go into the airport (`fighter-count`) instead of `:contents`.

**Files:**
- Modify: `src/empire/player/production_decisions.cljc:14-32`
- Modify: `src/empire/player/production.cljc:46-62`
- Test: `spec/empire/player/production_decisions_spec.clj`
- Test: `spec/empire/player/production_spec.clj`

- [ ] **Step 1: Write the failing test for production decisions**

```clojure
(it "build-produced-unit returns nil for fighter"
  (should-be-nil (decisions/build-produced-unit :fighter :player nil [5 0])))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/player/production_decisions_spec.clj`
Expected: FAIL — currently returns a fighter unit map

- [ ] **Step 3: Change `build-produced-unit` to return nil for fighters**

In `production_decisions.cljc`, change `build-produced-unit`:

```clojure
(defn build-produced-unit
  [item owner marching-orders flight-path]
  (when (not= item :fighter)
    (-> (create-base-unit item owner)
        (apply-unit-type-attributes item)
        (apply-movement-orders item marching-orders flight-path))))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/player/production_decisions_spec.clj`
Expected: PASS

- [ ] **Step 5: Write the failing test for spawn-unit fighter**

```clojure
(it "spawning a fighter increments fighter-count and does not set contents"
  ;; Set up city with fighter-count 0
  ;; Trigger production complete for fighter
  ;; Verify fighter-count = 1 and contents = nil
  )
```

- [ ] **Step 6: Run test to verify it fails**

Run: `clj -M:spec spec/empire/player/production_spec.clj`
Expected: FAIL

- [ ] **Step 7: Change `spawn-unit` to handle nil unit (fighter case)**

In `production.cljc`, change `spawn-unit`:

```clojure
(defn- spawn-unit
  [coords cell item]
  (let [owner (:city-status cell)
        marching-orders (:marching-orders cell)
        flight-path (:flight-path cell)
        unit (-> (decisions/build-produced-unit item owner marching-orders flight-path)
                 (stamp-unit-fields cell)
                 (apply-coast-walk-fields item cell coords)
                 (apply-random-explore-fields item cell coords)
                 (cond-> (= item :transport) (assoc :produced-at coords)))]
    (if unit
      (do (sa/update-world! assoc-in (conj coords :contents) unit)
          (when (and (= owner :computer) (= item :army) (:country-id cell))
            (stamp-adjacent-land coords (:country-id cell)))
          (when (and (= owner :computer) (= item :carrier))
            (sa/update-state! :computer-carrier-positions conj coords)))
      ;; Fighter: increment airport count
      (sa/update-world! update-in (conj coords :fighter-count) (fnil inc 0)))
    owner))
```

Note: `stamp-unit-fields`, `apply-coast-walk-fields`, `apply-random-explore-fields` will receive nil and need to handle it. Check if they guard against nil. If not, move the unit-building pipeline inside the `if`:

```clojure
(defn- spawn-unit
  [coords cell item]
  (let [owner (:city-status cell)]
    (if (= item :fighter)
      (sa/update-world! update-in (conj coords :fighter-count) (fnil inc 0))
      (let [marching-orders (:marching-orders cell)
            flight-path (:flight-path cell)
            unit (-> (decisions/build-produced-unit item owner marching-orders flight-path)
                     (stamp-unit-fields cell)
                     (apply-coast-walk-fields item cell coords)
                     (apply-random-explore-fields item cell coords)
                     (cond-> (= item :transport) (assoc :produced-at coords)))]
        (sa/update-world! assoc-in (conj coords :contents) unit)
        (when (and (= owner :computer) (= item :army) (:country-id cell))
          (stamp-adjacent-land coords (:country-id cell)))
        (when (and (= owner :computer) (= item :carrier))
          (sa/update-state! :computer-carrier-positions conj coords))))
    owner))
```

- [ ] **Step 8: Run test to verify it passes**

Run: `clj -M:spec spec/empire/player/production_spec.clj`
Expected: PASS

- [ ] **Step 9: Also update `city-production-step`**

Currently `city-production-step` blocks production if `:contents` is non-nil. For fighters, production should not be blocked by contents (since fighters don't use contents). Change:

```clojure
(defn city-production-step
  [cell prod]
  (cond
    (:contents cell) {:action :blocked}
    ...))
```

to:

```clojure
(defn city-production-step
  [cell prod]
  (cond
    (and (:contents cell) (not= (:item prod) :fighter)) {:action :blocked}
    ...))
```

- [ ] **Step 10: Run spec-structure-check and commit**

Run: `clj -M:spec-structure-check spec/empire/player/production_spec.clj`
Run: `clj -M:spec-structure-check spec/empire/player/production_decisions_spec.clj`

```bash
git add src/empire/player/production.cljc src/empire/player/production_decisions.cljc spec/empire/player/production_spec.clj spec/empire/player/production_decisions_spec.clj
git commit -m "Fighter production increments airport count instead of placing in contents"
```

---

### Task 9: Rework item processing — launch one, then attention

This is the core behavior change. Rewrite the auto-launch and requeue logic.

**Files:**
- Modify: `src/empire/game/loop/item_processing.cljc:83-183`
- Test: `spec/empire/game_loop/item_processing_player_batch_launch_spec.clj`

- [ ] **Step 1: Write the failing test — only one fighter launched**

```clojure
(it "launches only one fighter from airport with flight-path"
  ;; Set up city with fighter-count 3, awake-fighters 0, flight-path [5 0]
  ;; Process the item
  ;; Verify fighter-count = 2 (one launched)
  ;; Verify one fighter placed on map moving toward [5 0]
  )
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/game_loop/item_processing_player_batch_launch_spec.clj`
Expected: FAIL

- [ ] **Step 3: Rewrite auto-launch and requeue logic**

Change `awake-airport-fighter?` to check `fighter-count > 0` and no contents:

```clojure
(defn- airport-has-fighters? [cell]
  (and (not (:contents cell)) (pos? (:fighter-count cell 0))))
```

Change `auto-launch-fighter` to only launch from airport when flight-path is set and fighters exist:

```clojure
(defn- auto-launch-fighter [coords cell]
  (when-let [flight-path (airport-flight-path cell)]
    (cond
      (airport-has-fighters? cell)
      (container-ops/launch-fighter-from-airport coords flight-path)

      (awake-carrier-fighter? cell)
      (container-ops/launch-fighter-from-carrier coords flight-path))))
```

Change `should-requeue-airport?` to requeue when city still has awake-fighters > 0 (set by `u` key), regardless of flight-path:

```clojure
(defn- should-requeue-airport?
  [coords]
  (let [cell (get-in (sa/current-world) coords)]
    (and (= :city (:type cell))
         (uc/has-awake? cell :awake-fighters))))
```

The key change in `process-one-item`: after auto-launch, the city still has `fighter-count > 0`, so `item-needs-attention?` will return true and the item will go to `:attention` (presenting one fighter for attention). The auto-launched fighter's coords go into the queue for movement, and the city stays in the queue for attention.

Actually, looking at the flow more carefully: after auto-launch, `auto-coords` is non-nil, so action is `:auto-move`. The launched fighter's coords replace the city in the queue. But we also need the city to get attention. The current `should-requeue-airport?` handles this — it re-adds the city coords to the front of the queue. But under the new system, we always want to requeue the city after a flight-path launch (not just when there are more awake fighters).

Change `should-requeue-airport?` to requeue after any flight-path launch from a city that still has fighters:

```clojure
(defn- should-requeue-airport?
  [coords]
  (let [cell (get-in (sa/current-world) coords)]
    (and (= :city (:type cell))
         (pos? (:fighter-count cell 0)))))
```

This way: launch one on flight path -> city requeued -> next processing cycle presents the city for attention (since `fighter-count > 0` and no more flight-path auto-launch because we already launched one... wait, it would try to auto-launch again).

We need a way to prevent the second auto-launch. One approach: after the first launch, the city gets requeued. On the second pass, `airport-has-fighters?` is still true and `flight-path` is still set, so it would launch again. We need to prevent this.

Option: Track that a flight-path launch already happened this round for this city. A simple approach: the auto-launch only fires on the **first** pass. We could check if the city was just requeued (i.e., it's the same coords appearing again).

Simpler option: Don't auto-launch from the item processing loop at all for airports. Instead, do the flight-path launch inline at the start of processing, then immediately fall through to attention. This avoids the requeue dance.

Revised approach for `process-one-item`:

```clojure
(defn- process-one-item []
  (sa/update-state! :player-items decisions/normalize-item-queue)
  (let [coords (first (sa/read-state :player-items))
        cell (get-in (sa/current-world) coords)
        ;; Auto-launch one fighter on flight path if applicable
        _ (when (and (airport-has-fighters? cell) (airport-flight-path cell))
            (container-ops/launch-fighter-from-airport coords (airport-flight-path cell)))
        ;; Re-read cell after possible launch
        cell (get-in (sa/current-world) coords)
        unit (:contents cell)
        sat-moving? (decisions/satellite-with-target? unit)
        unit-in-auto-mode? (decisions/unit-auto-mode? unit)
        auto-coords (when-not sat-moving? (try-auto-launch-or-disembark coords cell))
        action (decisions/player-item-action
                {:sat-moving? sat-moving?
                 :auto-coords auto-coords
                 :unit-in-auto-mode? unit-in-auto-mode?
                 :needs-attention? (player-attention/item-needs-attention? coords)})]
    ;; ... rest unchanged
```

But this mixes airport-specific logic into the general processing loop. A cleaner approach: do the flight-path launch inside `try-auto-launch-or-disembark`, but have `auto-launch-fighter` only fire for carriers (not airports). Handle the airport flight-path launch as a separate step that doesn't redirect processing.

Cleanest approach: Extract a `launch-one-on-flight-path` step that runs before the main processing logic. It launches one fighter and doesn't affect the processing flow — the city still gets attention.

```clojure
(defn- launch-airport-flight-path-fighter
  "If city has fighters and a flight-path, launch one. Does not affect processing flow."
  [coords cell]
  (when (and (= :city (:type cell))
             (pos? (:fighter-count cell 0))
             (:flight-path cell))
    (container-ops/launch-fighter-from-airport coords (:flight-path cell))))
```

Then in `process-one-item`, call it before the main logic, and remove the airport case from `auto-launch-fighter`:

```clojure
(defn- auto-launch-fighter [coords cell]
  (when-let [flight-path (airport-flight-path cell)]
    (when (awake-carrier-fighter? cell)
      (container-ops/launch-fighter-from-carrier coords flight-path))))
```

And `should-requeue-airport?` becomes about `awake-fighters > 0` only (for the `u` key requeue):

```clojure
(defn- should-requeue-airport?
  [coords]
  (let [cell (get-in (sa/current-world) coords)]
    (and (= :city (:type cell))
         (uc/has-awake? cell :awake-fighters))))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/game_loop/item_processing_player_batch_launch_spec.clj`
Expected: PASS

- [ ] **Step 5: Write test — city gets attention after flight-path launch**

```clojure
(it "city with fighters gets attention after flight-path launch"
  ;; Set up city with fighter-count 3, flight-path [5 0]
  ;; Process item
  ;; Verify waiting-for-input is true (attention requested)
  ;; Verify cells-needing-attention contains city coords
  )
```

- [ ] **Step 6: Run test to verify it passes**

Run: `clj -M:spec spec/empire/game_loop/item_processing_player_batch_launch_spec.clj`
Expected: PASS (should already work with the new flow)

- [ ] **Step 7: Write test — city without flight-path still gets attention**

```clojure
(it "city with fighters but no flight-path gets attention"
  ;; Set up city with fighter-count 2, no flight-path
  ;; Process item
  ;; Verify waiting-for-input is true
  )
```

- [ ] **Step 8: Run test to verify it passes**

Run: `clj -M:spec spec/empire/game_loop/item_processing_player_batch_launch_spec.clj`
Expected: PASS

- [ ] **Step 9: Write test — requeue on awake-fighters > 0**

```clojure
(it "requeues city when awake-fighters > 0 after handling fighter"
  ;; Set up city with fighter-count 3, awake-fighters 2
  ;; Simulate processing one fighter (giving it orders)
  ;; Verify city is requeued in player-items
  )
```

- [ ] **Step 10: Run test to verify it passes**

Run: `clj -M:spec spec/empire/game_loop/item_processing_player_batch_launch_spec.clj`
Expected: PASS

- [ ] **Step 11: Run spec-structure-check and commit**

Run: `clj -M:spec-structure-check spec/empire/game_loop/item_processing_player_batch_launch_spec.clj`

```bash
git add src/empire/game/loop/item_processing.cljc spec/empire/game_loop/item_processing_player_batch_launch_spec.clj
git commit -m "Rework item processing: launch one on flight path, then attention"
```

---

### Task 10: Remove round-start airport fighter waking

**Files:**
- Modify: `src/empire/game/loop/round_start.cljc:127`
- Modify: `src/empire/game/loop/round_setup/waking.cljc:5-13`

- [ ] **Step 1: Remove `wake-airport-fighters` call from `start-new-round`**

In `round_start.cljc`, delete line 127:

```clojure
  (round-setup/wake-airport-fighters)
```

- [ ] **Step 2: Remove `wake-airport-fighters` function from `waking.cljc`**

Delete the function (lines 5-13). Keep `wake-carrier-fighters` and `wake-sentries-seeing-enemy`.

- [ ] **Step 3: Run full specs**

Run: `clj -M:spec`
Expected: All tests pass. Some existing tests may fail if they relied on round-start waking — fix them by setting up `fighter-count` instead of `awake-fighters` in test fixtures.

- [ ] **Step 4: Commit**

```bash
git add src/empire/game/loop/round_start.cljc src/empire/game/loop/round_setup/waking.cljc
git commit -m "Remove round-start airport fighter waking"
```

---

### Task 11: Update `u` key behavior for non-attention city context

When `u` is hit on a city that doesn't currently have a fighter asking for attention, all fighters should be awakened.

**Files:**
- Modify: `src/empire/player/commands_action_decisions.cljc`
- Test: `spec/empire/player/commands_action_decisions_spec.clj`

- [ ] **Step 1: Write the failing test**

```clojure
(it "unload-key-action on city without active airport fighter wakes all"
  (let [cell {:type :city :city-status :player :fighter-count 3 :awake-fighters 0}
        active-unit nil]  ;; no active unit (or a non-airport unit)
    (should= {:action :wake-fighters-on-airport}
             (decisions/unload-key-action nil cell active-unit))))
```

- [ ] **Step 2: Run test to verify behavior**

Run: `clj -M:spec spec/empire/player/commands_action_decisions_spec.clj`

The `unload-key-action` from Task 6 already handles this case — it checks `(pos? (:fighter-count cell 0))` regardless of whether the active unit is an airport fighter. The difference is in the handler: when the active unit IS an airport fighter, set `awake-fighters = fighter-count - 1`; when it's NOT, set `awake-fighters = fighter-count`.

This distinction belongs in `commands_actions.cljc`, not in the decision. The decision returns the same action; the handler checks context.

- [ ] **Step 3: Update `handle-unload-key` handler to distinguish contexts**

```clojure
      :wake-fighters-on-airport
      (do (container-ops/wake-fighters-on-airport coords)
          ;; If an airport fighter currently has attention, subtract 1 for it
          (when (movement-state/is-fighter-from-airport? active-unit)
            (let [cell (get-in (current-world ctx) coords)
                  adjusted (update cell :awake-fighters dec)]
              (update-game-map! ctx assoc-in coords adjusted)))
          (item-processed! ctx)
          true)
```

- [ ] **Step 4: Run tests**

Run: `clj -M:spec spec/empire/player/commands_actions_spec.clj`
Expected: PASS

- [ ] **Step 5: Run spec-structure-check and commit**

Run: `clj -M:spec-structure-check spec/empire/player/commands_action_decisions_spec.clj`

```bash
git add src/empire/player/commands_actions.cljc src/empire/player/commands_action_decisions.cljc spec/empire/player/commands_actions_spec.clj spec/empire/player/commands_action_decisions_spec.clj
git commit -m "Handle u key context: subtract 1 when airport fighter has attention"
```

---

### Task 12: Run full test suite and fix regressions

**Files:**
- Various spec and source files as needed

- [ ] **Step 1: Run all unit tests**

Run: `clj -M:spec`

- [ ] **Step 2: Fix any failing tests**

Likely failures:
- Tests that set up `awake-fighters` expecting round-start waking — change to set `fighter-count` instead
- Tests that expect `build-produced-unit` to return a fighter — update to expect nil
- Tests that expect fighter in `:contents` after production — update to check `fighter-count`

- [ ] **Step 3: Run acceptance test pipeline**

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

- [ ] **Step 4: Fix any acceptance test failures**

Update acceptance test expectations as needed (with permission).

- [ ] **Step 5: Commit fixes**

```bash
git add -A
git commit -m "Fix test regressions from airport fighter rework"
```

---

### Task 13: Update display helpers for new airport model

The `normal-display-unit` and `blinking-contained-unit` functions in `helpers.cljc` check `has-awake-airport?` which uses `awake-fighters`. Update to use `fighter-count`.

**Files:**
- Modify: `src/empire/game_mechanics/containers/helpers.cljc:71-92`
- Test: existing display tests

- [ ] **Step 1: Write failing test**

```clojure
(it "normal-display-unit shows fighter when fighter-count > 0 and awake-fighters 0"
  (let [result (uc/normal-display-unit nil nil false true)]
    (should= :fighter (:type result))
    (should= :sentry (:mode result))))
```

The `has-any-airport?` parameter is already passed by callers based on `fighter-count > 0`. Verify the callers pass this correctly. The `has-awake-airport?` parameter needs to be updated at call sites to use `fighter-count > 0` instead of `awake-fighters > 0`.

- [ ] **Step 2: Find and update callers**

Search for calls to `normal-display-unit` and `blinking-contained-unit` and ensure they pass `fighter-count > 0` for the airport-related boolean parameters.

- [ ] **Step 3: Run tests and commit**

Run: `clj -M:spec`

```bash
git add src/empire/game_mechanics/containers/helpers.cljc
git commit -m "Update display helpers for fighter-count based airport model"
```

---

### Task 14: Update attention message for airport fighters

The attention message currently says "Fighter needs attention - Landed and refueled." Since fighters no longer go through an explicit land-and-refuel cycle at round start, the message should just say the fighter needs attention.

**Files:**
- Modify: `src/empire/player/attention_decisions.cljc:127-149`

- [ ] **Step 1: Review and update attention message**

In `attention-message`, the airport fighter case (line 130-133) says:

```clojure
    airport-fighter?
    (str "Fighter" (:unit-needs-attention config/messages) " - "
         (:fighter-landed-and-refueled config/messages)
         (fuel-string active-unit))
```

Consider changing to include the airport fighter count:

```clojure
    airport-fighter?
    (str "Fighter" (:unit-needs-attention config/messages)
         " (" (:fighter-count cell 0) " in airport)"
         (fuel-string active-unit))
```

This requires passing `cell` to `attention-message`. Check the caller to see if cell is available.

- [ ] **Step 2: Run tests and commit**

Run: `clj -M:spec`

```bash
git add src/empire/player/attention_decisions.cljc
git commit -m "Update airport fighter attention message to show count"
```
