# Fighters Prefer Unexplored Cells — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Computer fighters prefer unexplored cells during regular leg navigation, maximizing map discovery per step.

**Architecture:** Replace the distance-capped filter in `navigate-toward-target` with score-based neighbor selection using the existing `count-unexplored-neighbors` function. When fuel margin allows, score all passable unoccupied neighbors by unexplored-neighbor count, break ties by proximity to target. No distance cap — fuel is the only constraint.

**Tech Stack:** Clojure, Speclj tests

---

### Task 1: Test — fighter picks neighbor with highest unexplored count

**Files:**
- Test: `spec/empire/computer/fighter_flight_spec.clj`

**Step 1: Write the failing test**

Add a new context inside the existing `"navigate-toward-target enhancement"` context (line 236). The test sets up a fighter on a regular leg with plenty of fuel, with two possible directions — one adjacent to more unexplored cells than the other. Verify the fighter moves toward the higher-scoring direction.

```clojure
(it "prefers neighbor with highest unexplored-neighbor count"
  ;; 5x5 map. Fighter at [2,2] on regular leg to city at [4,4].
  ;; Row 0 is unexplored on computer-map → [1,1] has more unexplored neighbors than [3,3].
  ;; Fighter should detour toward [1,1] or [1,2] (near unexplored) not [3,3] (direct).
  (reset! atoms/game-map (build-test-map ["#####"
                                           "#####"
                                           "##f##"
                                           "#####"
                                           "####X"]))
  (set-test-unit atoms/game-map "f" :fuel 30
                 :flight-target-site [4 4]
                 :flight-origin-site [0 0]
                 :flight-mode :regular)
  ;; Row 0 unexplored (nil), rest explored
  (reset! atoms/computer-map (build-test-map ["-----"
                                               "#####"
                                               "##f##"
                                               "#####"
                                               "####X"]))
  (let [unit (get-in @atoms/game-map [2 2 :contents])]
    (fighter/process-fighter [2 2] unit)
    ;; Fighter should move toward row 0 (unexplored), not toward [4,4] direct
    (let [result (get-test-unit atoms/game-map "f")
          [c r] (:pos result)]
      (should-not-be-nil result)
      ;; Should be at row 0 or 1 (moved toward unexplored), not row 3+
      (should (< r 2)))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/computer/fighter_flight_spec.clj`
Expected: FAIL — current code limits detours to `(inc direct-dist)`, so the fighter takes the direct path toward [4,4] instead.

### Task 2: Implement score-based navigation

**Files:**
- Modify: `src/empire/computer/fighter.cljc:454-478` (`navigate-toward-target`)

**Step 3: Write minimal implementation**

Replace `navigate-toward-target` with:

```clojure
(defn- select-best-navigation-target
  "Score passable unoccupied neighbors by unexplored count, break ties by proximity."
  [passable target]
  (let [candidates (filter #(not (occupied? %)) passable)
        scored (map (fn [n] [n (count-unexplored-neighbors n)]) candidates)
        best-score (when (seq scored) (apply max (map second scored)))]
    (when (and best-score (pos? best-score))
      (let [at-best (filter #(= best-score (second %)) scored)]
        (first (first (sort-by (fn [[n _]] (distance-to n target)) at-best)))))))

(defn- navigate-toward-target
  "Move one step toward target, preferring unexplored cells when fuel allows.
   Returns {:pos p :hops n} or nil."
  [pos target fuel]
  (let [passable (get-passable-neighbors pos)
        direct-dist (distance-to pos target)
        fuel-margin? (> fuel (+ direct-dist 2))
        explore-pos (when fuel-margin?
                      (select-best-navigation-target passable target))]
    (if explore-pos
      (when (core/move-unit-to pos explore-pos)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility explore-pos :computer)
        (when (consume-fighter-fuel explore-pos)
          {:pos explore-pos :hops 1}))
      (when-let [hop (hop-over-friendly pos target)]
        (when-let [{:keys [pos hops]} (execute-hop pos hop)]
          (when (consume-fighter-fuel pos)
            {:pos pos :hops hops}))))))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/fighter_flight_spec.clj`
Expected: PASS

**Step 5: Run full test suite**

Run: `clj -M:spec`
Expected: All tests pass — no regressions.

### Task 3: Test — direct path when fuel is tight

**Files:**
- Test: `spec/empire/computer/fighter_flight_spec.clj`

**Step 6: Write the failing test**

```clojure
(it "takes direct path when fuel is tight despite unexplored neighbors"
  ;; Fighter at [2,2] heading to city at [4,4]. Fuel = direct-dist + 2 = 6.
  ;; Should NOT detour even though row 0 is unexplored.
  (reset! atoms/game-map (build-test-map ["#####"
                                           "#####"
                                           "##f##"
                                           "#####"
                                           "####X"]))
  (set-test-unit atoms/game-map "f" :fuel 6
                 :flight-target-site [4 4]
                 :flight-origin-site [0 0]
                 :flight-mode :regular)
  (reset! atoms/computer-map (build-test-map ["-----"
                                               "#####"
                                               "##f##"
                                               "#####"
                                               "####X"]))
  (let [unit (get-in @atoms/game-map [2 2 :contents])]
    (fighter/process-fighter [2 2] unit)
    ;; With tight fuel, fighter should head toward target (row 3+), not detour
    (let [result (get-test-unit atoms/game-map "f")]
      (should-not-be-nil result))))
```

**Step 7: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/fighter_flight_spec.clj`
Expected: PASS (fuel guard already handles this).

### Task 4: Test — fallback to direct when no unexplored neighbors

**Files:**
- Test: `spec/empire/computer/fighter_flight_spec.clj`

**Step 8: Write the failing test**

```clojure
(it "falls back to direct navigation when all neighbors fully explored"
  ;; Entire map explored — no unexplored neighbors anywhere.
  ;; Fighter should navigate directly toward target.
  (reset! atoms/game-map (build-test-map ["#####"
                                           "#####"
                                           "##f##"
                                           "#####"
                                           "####X"]))
  (set-test-unit atoms/game-map "f" :fuel 30
                 :flight-target-site [4 4]
                 :flight-origin-site [0 0]
                 :flight-mode :regular)
  ;; Everything explored
  (reset! atoms/computer-map (build-test-map ["#####"
                                               "#####"
                                               "##f##"
                                               "#####"
                                               "####X"]))
  (let [unit (get-in @atoms/game-map [2 2 :contents])]
    (fighter/process-fighter [2 2] unit)
    ;; Fighter should move toward target [4,4]
    (let [result (get-test-unit atoms/game-map "f")
          [c r] (:pos result)]
      (should-not-be-nil result)
      (should (or (> r 2) (> c 2))))))
```

**Step 9: Run test to verify it passes**

Run: `clj -M:spec spec/empire/computer/fighter_flight_spec.clj`
Expected: PASS (fallback to hop-over-friendly handles this).

### Task 5: Run full suite and commit

**Step 10: Run full test suite**

Run: `clj -M:spec`
Expected: All tests pass.

**Step 11: Commit**

```bash
git add src/empire/computer/fighter.cljc spec/empire/computer/fighter_flight_spec.clj
git commit -m "feat: computer fighters prefer unexplored cells during regular leg navigation"
```
