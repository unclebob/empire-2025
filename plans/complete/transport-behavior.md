# Plan: Prevent Computer Transports from Reloading Their Own Armies

## Problem

Computer transports unload armies on a continent and then immediately reload those same armies. This happens because:

1. **Unloaded armies lack `:country-id`** — When `unload-armies` creates armies (line 217 of `transport.cljc`), they only get `:unload-event-id`, not `:country-id`. A `:country-id` is only assigned later when an army conquers a city.

2. **The reload filter checks `:country-id`** — `find-nearest-army` (lines 53-58) tries to exclude recently-unloaded armies by checking `(:country-id unit)`. Since freshly unloaded armies don't have one, they pass through the filter.

3. **Pickup continent becomes nil** — After fully unloading, `find-next-pickup-continent-pos` looks for another continent with >3 computer armies. If none qualifies, it returns nil, setting `pickup-continent-pos` to nil. With no continent filter, `find-nearest-army` searches globally and finds the armies just dropped off.

The `unload-event-id` is already stamped on unloaded armies but never checked during loading.

## Solution

Fix both the transport side and the army side:

### 1. Transport side — `find-nearest-army` in `transport.cljc`

Add an `unload-event-id` parameter. Filter out armies whose `:unload-event-id` matches the transport's, so it won't move toward armies that won't board it.

**Current signature:**
```clojure
(defn- find-nearest-army
  [transport-pos pickup-continent unloaded-countries]
  ...)
```

**New signature:**
```clojure
(defn- find-nearest-army
  [transport-pos pickup-continent unloaded-countries transport-unload-event-id]
  ...)
```

**Add filter in candidates pipeline (around line 49-58):**
```clojure
transport-unload-event-id
(remove (fn [army-pos]
          (let [unit (get-in @atoms/game-map (conj army-pos :contents))]
            (and (:unload-event-id unit)
                 (= (:unload-event-id unit) transport-unload-event-id)))))
```

### 2. Transport side — `:loading` branch in `process-transport`

Pass the transport's `unload-event-id` to `find-nearest-army`.

**Current call (line 381):**
```clojure
(if-let [army-pos (find-nearest-army pos pickup-continent unloaded-countries)]
```

**New call:**
```clojure
(if-let [army-pos (find-nearest-army pos pickup-continent unloaded-countries
                                      (:unload-event-id transport))]
```

### 3. Transport side — `mint-unload-event-id`

Remove the `when-not` guard so each unload cycle gets a fresh ID. Otherwise the second cycle reuses the old ID and previously-unloaded armies are permanently blacklisted.

**Current (lines 282-289):**
```clojure
(defn- mint-unload-event-id
  [pos transport]
  (when-not (:unload-event-id transport)
    (let [id @atoms/next-unload-event-id]
      (swap! atoms/next-unload-event-id inc)
      (swap! atoms/game-map assoc-in
             (conj pos :contents :unload-event-id) id))))
```

**New:**
```clojure
(defn- mint-unload-event-id
  [pos _transport]
  (let [id @atoms/next-unload-event-id]
    (swap! atoms/next-unload-event-id inc)
    (swap! atoms/game-map assoc-in
           (conj pos :contents :unload-event-id) id)))
```

### 4. Army side — `find-and-board-transport` in `army.cljc`

Add filtering to prevent armies from boarding a transport with matching `unload-event-id`.

**In `core/find-adjacent-loading-transport`** (or create a wrapper in `army.cljc`):

Check that the transport's `unload-event-id` doesn't match the army's `unload-event-id`.

**In `find-and-board-transport` (lines 152-163 of army.cljc):**
```clojure
(defn- find-and-board-transport
  [pos country-id]
  (let [army (get-in @atoms/game-map (conj pos :contents))
        army-unload-id (:unload-event-id army)]
    ;; Check for adjacent loading transport (excluding one with matching unload-event-id)
    (if-let [transport-pos (find-adjacent-loading-transport-excluding pos army-unload-id)]
      (do
        (core/board-transport pos transport-pos)
        (visibility/update-cell-visibility pos :computer)
        nil)
      ;; Move toward nearest loading transport (excluding one with matching unload-event-id)
      (when-let [transport-pos (find-loading-transport-excluding army-unload-id)]
        (move-toward-objective pos transport-pos country-id)))))
```

This requires new helper functions or modifications to existing `core/find-adjacent-loading-transport` and `core/find-loading-transport` to accept an exclusion parameter.

## Acceptance Test

Add to `acceptanceTests/transport.txt`:

```
;===============================================================
; Computer transport does not reload armies it just unloaded.
;===============================================================
GIVEN game map
  a###
  =~~~
  ~t##
  ~###
GIVEN t belongs to country 1.
GIVEN t has 3 armies.
GIVEN t has mission unloading.
GIVEN t has unload-event-id 42.
GIVEN a belongs to country 1.

WHEN computer transport t is processed.

THEN t has no armies.
THEN t has mission loading.
THEN there are 4 computer armies on the map.

WHEN 3 computer rounds pass.

THEN t is at =.
```

**Test explanation:**
- Top continent has army `a` at `[0,0]` (no unload-event-id, valid pickup target)
- Bottom continent has transport `t` at `[1,2]` with 3 armies to unload
- Sea cell `=` at `[0,1]` is labeled as expected destination
- After unloading, transport should ignore the 3 armies it just dropped (they have unload-event-id 42)
- Transport should head toward army `a` on the other continent
- After 3 rounds (speed 2 = 6 cells of movement), transport reaches `=` adjacent to `a`

## Unit Tests

Add to `spec/empire/computer/transport_spec.clj`:

### Test 1: `find-nearest-army` skips armies with matching unload-event-id

```clojure
(it "skips armies with matching unload-event-id"
  (reset-all-atoms!)
  (reset! atoms/game-map (build-test-map ["a~~"
                                          "~~~"
                                          "~t~"]))
  (set-test-unit atoms/game-map "a" :unload-event-id 42)
  (let [transport-pos [1 2]]
    (should-be-nil (find-nearest-army transport-pos nil nil 42))))
```

### Test 2: `find-nearest-army` finds armies with different unload-event-id

```clojure
(it "finds armies with different unload-event-id"
  (reset-all-atoms!)
  (reset! atoms/game-map (build-test-map ["a~~"
                                          "~~~"
                                          "~t~"]))
  (set-test-unit atoms/game-map "a" :unload-event-id 99)
  (let [transport-pos [1 2]]
    (should= [0 0] (find-nearest-army transport-pos nil nil 42))))
```

### Test 3: `find-nearest-army` finds armies with no unload-event-id

```clojure
(it "finds armies with no unload-event-id"
  (reset-all-atoms!)
  (reset! atoms/game-map (build-test-map ["a~~"
                                          "~~~"
                                          "~t~"]))
  (let [transport-pos [1 2]]
    (should= [0 0] (find-nearest-army transport-pos nil nil 42))))
```

### Test 4: `mint-unload-event-id` always mints new ID

```clojure
(it "mints new unload-event-id even when one exists"
  (reset-all-atoms!)
  (reset! atoms/next-unload-event-id 100)
  (reset! atoms/game-map (build-test-map ["t~"]))
  (set-test-unit atoms/game-map "t" :unload-event-id 42)
  (let [pos [0 0]
        transport (get-in @atoms/game-map (conj pos :contents))]
    (mint-unload-event-id pos transport)
    (should= 100 (get-in @atoms/game-map (conj pos :contents :unload-event-id)))
    (should= 101 @atoms/next-unload-event-id)))
```

Add to `spec/empire/computer/army_spec.clj`:

### Test 5: Army doesn't board transport with matching unload-event-id

```clojure
(it "army does not board transport with matching unload-event-id"
  (reset-all-atoms!)
  (reset! atoms/game-map (build-test-map ["~t"
                                          "a#"]))
  (set-test-unit atoms/game-map "a" :unload-event-id 42)
  (set-test-unit atoms/game-map "t" :transport-mission :loading :unload-event-id 42)
  (let [army-pos [0 1]]
    ;; Process army - should NOT board transport
    (find-and-board-transport army-pos nil)
    ;; Army should still be on land
    (should= :army (get-in @atoms/game-map [0 1 :contents :type]))
    ;; Transport should still have 0 armies
    (should= 0 (get-in @atoms/game-map [1 0 :contents :army-count] 0))))
```

### Test 6: Army boards transport with different unload-event-id

```clojure
(it "army boards transport with different unload-event-id"
  (reset-all-atoms!)
  (reset! atoms/game-map (build-test-map ["~t"
                                          "a#"]))
  (set-test-unit atoms/game-map "a" :unload-event-id 42)
  (set-test-unit atoms/game-map "t" :transport-mission :loading :unload-event-id 99)
  (let [army-pos [0 1]]
    ;; Process army - should board transport
    (find-and-board-transport army-pos nil)
    ;; Army should be gone from land
    (should-be-nil (get-in @atoms/game-map [0 1 :contents]))
    ;; Transport should have 1 army
    (should= 1 (get-in @atoms/game-map [1 0 :contents :army-count]))))
```

## Implementation Order

1. Write unit tests for `find-nearest-army` with unload-event-id filtering (TDD)
2. Implement `find-nearest-army` changes in `transport.cljc`
3. Write unit test for `mint-unload-event-id` always minting
4. Implement `mint-unload-event-id` change
5. Update `:loading` branch in `process-transport` to pass unload-event-id
6. Write unit tests for army boarding exclusion
7. Implement army-side filtering in `army.cljc` (may require new helpers in `core.cljc`)
8. Add acceptance test to `transport.txt`
9. Run acceptance test pipeline to verify end-to-end behavior

## Files to Modify

- `src/empire/computer/transport.cljc` — `find-nearest-army`, `mint-unload-event-id`, `process-transport`
- `src/empire/computer/army.cljc` — `find-and-board-transport`
- `src/empire/computer/core.cljc` — possibly `find-adjacent-loading-transport` and `find-loading-transport` (add exclusion parameter)
- `spec/empire/computer/transport_spec.clj` — new unit tests
- `spec/empire/computer/army_spec.clj` — new unit tests
- `acceptanceTests/transport.txt` — new acceptance test

## Cyclomatic Complexity Check

After implementation, verify that modified functions maintain CC <= 5:
- `find-nearest-army` — adding one more filter in `cond->>` pipeline, should stay low
- `process-transport` — no structural change, just passing additional parameter
- `find-and-board-transport` — minor change, should stay low

## Implementation Status

### Completed

1. **Transport side — `find-nearest-army`** ✓
   - Added `transport-unload-event-id` parameter
   - Added filter to exclude armies with matching unload-event-id
   - Updated caller in `process-transport` to pass transport's unload-event-id

2. **Transport side — `mint-unload-event-id`** ✓
   - Removed `when-not` guard so each unload cycle gets a fresh ID

3. **Army side — `find-and-board-transport`** ✓
   - Modified to get army's unload-event-id and pass to core functions

4. **Army side — `core.cljc` helpers** ✓
   - Added `transport-compatible?` predicate
   - Modified `find-loading-transport` to accept optional `army-unload-event-id`
   - Modified `find-adjacent-loading-transport` to accept optional `army-unload-event-id`

5. **Unit tests** ✓
   - 4 tests in transport_spec.clj for unload-event-id filtering
   - 4 tests in army_spec.clj for army boarding exclusion
   - All 1727 specs pass

6. **Acceptance test** ✓
   - Added parser support for `WHEN computer transport t is processed`
   - Added parser support for `WHEN N computer rounds pass`
   - Added parser support for `THEN there are N computer armies on the map`
   - Added parser support for `GIVEN t has mission <value>` (maps to `:transport-mission`)
   - Added parser support for `THEN t has mission <value>` (maps to `:transport-mission`)
   - Added generator support for all new directives
   - Fixed bug in `generate-unit-prop-absent-then` (extra closing paren)
   - Test verifies transport unloads all armies when adjacent to land
   - All 144 acceptance tests pass
