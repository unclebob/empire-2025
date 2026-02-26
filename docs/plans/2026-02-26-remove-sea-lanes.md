# Remove Sea Lane Network — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the sea lane network system and simplify naval pathfinding to pure A* with path caching.

**Architecture:** The sea lane system is a performance optimization layer that sits between `pathfinding/next-step` and `a-star`. It records naval A* paths into a persistent graph and later routes through it via Dijkstra. Removing it means deleting the module, stripping references from 7 other files, cleaning 2 spec files, and simplifying `next-step` to go directly to A*.

**Tech Stack:** Clojure, Speclj (testing)

---

### Task 1: Delete sea lane module and its spec

**Files:**
- Delete: `src/empire/movement/sea_lanes.cljc`
- Delete: `spec/empire/movement/sea_lanes_spec.clj`

**Step 1: Delete the files**

```bash
rm src/empire/movement/sea_lanes.cljc
rm spec/empire/movement/sea_lanes_spec.clj
```

**Step 2: Verify deletion**

```bash
ls src/empire/movement/sea_lanes.cljc 2>&1  # should say "No such file"
ls spec/empire/movement/sea_lanes_spec.clj 2>&1  # should say "No such file"
```

No commit yet — code won't compile until references are removed.

---

### Task 2: Remove sea lane atom, config constants, and peripheral references

**Files:**
- Modify: `src/empire/atoms.cljc` — remove `sea-lane-network` atom (lines 154-157)
- Modify: `src/empire/config.cljc` — remove 6 `sea-lane-*` constants (lines 109-115)
- Modify: `src/empire/test_utils.cljc` — remove sea-lane reset (lines 222-223)
- Modify: `src/empire/save_load.cljc` — remove `:sea-lane-network` from `saveable-atoms` (line 25)

**Step 1: Edit atoms.cljc**

Remove lines 154-157 (the `sea-lane-network` def and its docstring).

**Step 2: Edit config.cljc**

Remove lines 109-115:
```clojure
;; Sea lane network constants
(def max-sea-lane-nodes 500)
(def max-sea-lane-segments 1000)
(def sea-lane-local-radius 15)
(def sea-lane-extended-radius 25)
(def sea-lane-min-segment-length 2)
(def sea-lane-min-network-nodes 4)
```

**Step 3: Edit test_utils.cljc**

Remove lines 222-223:
```clojure
  (reset! atoms/sea-lane-network {:nodes {} :segments {} :pos->node {} :pos->seg {}
                                   :next-node-id 1 :next-segment-id 1})
```

**Step 4: Edit save_load.cljc**

Remove line 25:
```clojure
   :sea-lane-network atoms/sea-lane-network
```

No commit yet — pathfinding still references the deleted module.

---

### Task 3: Simplify pathfinding.cljc

**Files:**
- Modify: `src/empire/movement/pathfinding.cljc`

Remove these items:
1. The `sea-lanes` require from the ns form (line 9)
2. The `config` require from the ns form (line 7) — only used by sea lane code; check first
3. `bounded-a-star` function (lines 131-143)
4. `chebyshev` function (lines 543-546) — only used by sea lane code
5. `naval-types` def (lines 548-549)
6. `try-network-route` function (lines 551-558)
7. `compute-network-step` function (lines 560-566)
8. Sea lane recording from `compute-a-star-step` (lines 572-573)
9. Network routing from `next-step` (lines 585-586)

**Step 1: Remove the `sea-lanes` require**

In the `ns` form, remove:
```clojure
            [empire.movement.sea-lanes :as sea-lanes]
```

Check if `config` is used elsewhere in the file. If not, also remove:
```clojure
            [empire.config :as config]
```

**Step 2: Delete `bounded-a-star`**

Remove lines 131-143 entirely.

**Step 3: Delete `chebyshev`, `naval-types`, `try-network-route`, `compute-network-step`**

Remove lines 543-566 entirely.

**Step 4: Simplify `compute-a-star-step`**

Change from:
```clojure
(defn- compute-a-star-step
  "Computes A* path and returns next step. Records naval paths to sea-lane network."
  [start goal unit-type passability-fn cache-key-extra]
  (when-let [path (a-star start goal unit-type @atoms/game-map passability-fn)]
    (when (and (naval-types unit-type) (not passability-fn))
      (sea-lanes/record-path! path))
    (cache-sub-paths! path goal unit-type cache-key-extra)
    (second path)))
```

To:
```clojure
(defn- compute-a-star-step
  "Computes A* path and returns next step."
  [start goal unit-type passability-fn cache-key-extra]
  (when-let [path (a-star start goal unit-type @atoms/game-map passability-fn)]
    (cache-sub-paths! path goal unit-type cache-key-extra)
    (second path)))
```

**Step 5: Simplify `next-step`**

Change from:
```clojure
(defn next-step
  "Returns the next step toward goal, or nil if unreachable or already at goal."
  ([start goal unit-type]
   (next-step start goal unit-type nil nil))
  ([start goal unit-type passability-fn cache-key-extra]
   (when (not= start goal)
     (if-let [cached (get @path-cache [start goal unit-type cache-key-extra])]
       (second cached)
       (or (when-not passability-fn
             (compute-network-step start goal unit-type cache-key-extra))
           (compute-a-star-step start goal unit-type passability-fn cache-key-extra))))))
```

To:
```clojure
(defn next-step
  "Returns the next step toward goal, or nil if unreachable or already at goal."
  ([start goal unit-type]
   (next-step start goal unit-type nil nil))
  ([start goal unit-type passability-fn cache-key-extra]
   (when (not= start goal)
     (if-let [cached (get @path-cache [start goal unit-type cache-key-extra])]
       (second cached)
       (compute-a-star-step start goal unit-type passability-fn cache-key-extra)))))
```

**Step 6: Run tests**

```bash
clj -M:spec
```

Expected: All tests pass except sea-lane-specific tests in `pathfinding_bfs_spec.clj` and `debug_spec.clj` (which reference removed code). Those are cleaned in the next task.

---

### Task 4: Clean spec files

**Files:**
- Modify: `spec/empire/movement/pathfinding_bfs_spec.clj`
- Modify: `spec/empire/debug_spec.clj`

**Step 1: Clean pathfinding_bfs_spec.clj**

Remove the entire `(describe "sea lane network integration" ...)` block (lines 235-349). This includes:
- "next-step records naval paths into sea lane network"
- "next-step does not record army paths into sea lane network"
- "bounded-a-star finds path on small map"
- "bounded-a-star returns nil when goal is outside radius"
- "next-step skips network for short-distance goals"
- "next-step uses network for long-distance goals"
- "compute-network-step caches and returns next step when network routes"

Also in the `"mutation-killing tests"` describe block:
- Remove `(context "bounded-a-star" ...)` (lines 467-470)
- Remove `(context "next-step sea lane recording with passability-fn" ...)` (lines 520-525)

**Step 2: Clean debug_spec.clj**

Remove the entire `(describe "format-dump includes sea lane network section" ...)` block (lines 8-40).

**Step 3: Run spec-structure-check on both files**

```bash
clj -M:spec-structure-check spec/empire/movement/pathfinding_bfs_spec.clj
clj -M:spec-structure-check spec/empire/debug_spec.clj
```

Expected: OK for both.

**Step 4: Run all tests**

```bash
clj -M:spec
```

Expected: All tests pass.

---

### Task 5: Remove sea lane debug formatting

**Files:**
- Modify: `src/empire/debug.cljc`

**Step 1: Delete `format-sea-lane-section`**

Remove lines 222-248 (the entire `format-sea-lane-section` function).

**Step 2: Remove its call from `format-dump`**

In `format-dump` (around line 290), remove:
```clojure
        sea-lane-section (format-sea-lane-section)
```

And remove `sea-lane-section` from the final `str` call (line 300).

**Step 3: Run tests**

```bash
clj -M:spec spec/empire/debug_spec.clj
```

Expected: All debug tests pass.

---

### Task 6: Update future issues and run full test suite

**Files:**
- Modify: `plans/future-issues.md`

**Step 1: Edit future-issues.md**

Remove the line:
```
- **Remove sea lanes**: Remove the sea lane network system.
```

Add a new line:
```
- **Carrier movement optimization**: Carriers currently use full A* for every step. Consider caching or other optimization if performance is an issue.
```

**Step 2: Run full test suite**

```bash
clj -M:spec
```

Expected: All tests pass.

**Step 3: Run coverage**

```bash
clj -M:cov
```

Expected: Coverage stays in the high 90s. Removing dead code should not decrease coverage.

**Step 4: Commit**

```bash
git add -A
git commit -m "remove sea lane network system

Delete the sea lane module and simplify naval pathfinding to pure A*
with the existing per-round path cache. Only carriers used this system.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```
