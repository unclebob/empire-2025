# CRAP Round 5 — Design

**Goal:** Reduce CRAP scores for 7 functions via extract-method refactoring and coverage improvement.

**Two strategies:**
- **Group A (Refactor):** Extract helpers to reduce CC in functions with high complexity
- **Group B (Coverage):** Add tests to functions with low coverage but acceptable structure

---

## Group A: Refactor (4 functions)

### A1. `normal-display-unit` — CC=11→~5, containers/helpers.cljc:96-104

The function checks `(and contents (:type contents))` twice — once gated on `:awake` mode, once unconditionally. The priority order is: awake unit > awake airport > any unit > any airport > nil.

**Refactoring:** Extract `valid-contents?` let-binding, restructure as nested if:

```clojure
(defn normal-display-unit [cell contents has-awake-airport? has-any-airport?]
  (let [has-contents? (and contents (:type contents))]
    (cond
      (and has-contents? (= (:mode contents) :awake)) contents
      has-awake-airport? {:type :fighter :mode :awake}
      has-contents? contents
      has-any-airport? {:type :fighter :mode :sentry}
      :else nil)))
```

This preserves the exact priority order while eliminating the duplicate predicate. CC drops because the `and` is evaluated once instead of twice in the cond chain.

Tests: Already 100% covered (7 tests in helpers_spec.clj). No new tests needed.

### A2. `has-nearby-unloadable-land?` — CC=12→~6+6, computer/transport.cljc:420-459

Two nested closures contain compound boolean predicates. Extract as named `defn-` helpers.

**Refactoring:**
1. Extract `unloadable-neighbor?` — the `(fn [n] ...)` inside `some` that checks land/city, empty, not-excluded, not-pickup-continent
2. Extract `passable-coastal-neighbor?` — the `(fn [n] ...)` inside `filter` that checks unvisited, sea, passable, adjacent-to-land
3. Both take `game-map` and their exclusion sets as parameters

The BFS loop body stays in `has-nearby-unloadable-land?` but delegates to the two clean predicates. Note: `passable-sea?` already exists at line 461 and can be reused.

Tests: 93% covered. The 7% gap is likely the `pickup-continent` exclusion path. Add one test exercising that branch.

### A3. `flood-fill-continent-uncached` — CC=12→~8, computer/land_objectives.cljc:26-70

**Refactoring:**
1. Extract `in-bounds?` predicate for the `(or (neg? r) (neg? c) (>= r height) (>= c width))` check
2. The terrain classification already delegates to `get-terrain` — no further extraction needed
3. The loop structure is inherent to flood-fill and cannot be simplified further

Modest CC reduction. The remaining complexity is algorithmic.

Tests: 98% covered via `flood-fill-continent` tests. The 2% gap is likely the empty-map guard `(when (and (pos? height) (pos? width)))`. Add one edge-case test.

### A4. `load-adjacent-sentry-armies` — CC=9→~4+3, containers/ops.cljc:13-44

**Refactoring:**
1. Extract `loadable-army?` predicate — checks adj-unit is army, sentry, same owner, transport not full
2. Extract `wake-transport-if-needed` — the post-loading logic that wakes transport at beach

The `doseq` loop body becomes: check bounds → check `loadable-army?` → load.

Tests: 65% covered. Existing tests cover basic load, non-army rejection, edge cells, and wake-at-beach. Uncovered branches likely include: transport already full (outer guard), loading multiple armies until capacity reached mid-loop (inner full? recheck). Add 2-3 tests.

---

## Group B: Coverage (3 functions)

### B1. `reposition-carrier` — CC=3, 2%→80%+, computer/ship.cljc:580-592

Easiest win. Two branches:
1. `find-carrier-position` returns a target → set positioning mode, attempt pathfinding step
2. `find-carrier-position` returns nil → set holding mode

**Tests:** Mock `find-carrier-position` with `with-redefs`. Two tests:
- Returns position + pair → carrier switches to `:positioning`, moves toward target
- Returns nil → carrier switches to `:holding`

### B2. `process-computer-unit` — CC=8, 64%→90%+, computer.cljc:12-26

Dispatcher with 6 branches. Tests exist in `computer_spec.clj` covering army, fighter, ship, transport, nil-unit, non-computer-unit. Coverage gap is likely the satellite nil-return branch and possibly specific ship subtypes (patrol-boat, submarine).

**Tests:** Add 2 tests:
- Satellite unit → returns nil, no processing
- Patrol-boat → dispatches to `ship/process-ship`

### B3. `process-player-items-batch` — CC=13, 81%→90%+, game_loop/item_processing.cljc:206-219

No direct tests exist. Coverage comes from integration. Uncovered branches: `:waiting` result, 100-item boundary, victory-mid-batch (`:paused`).

**Tests:** Add 3 tests with mocked atoms:
- `process-one-item` returns `:waiting` → batch stops
- 100+ items in queue → batch stops at limit
- Victory declared mid-batch (`paused` set true) → batch stops

---

## Ordering

Tasks are ordered by independence and risk:
1. A1 (normal-display-unit) — isolated, simple, tests exist
2. B1 (reposition-carrier) — coverage only, isolated
3. B2 (process-computer-unit) — coverage only, tests exist
4. B3 (process-player-items-batch) — coverage only, new tests
5. A4 (load-adjacent-sentry-armies) — refactor + coverage
6. A2 (has-nearby-unloadable-land?) — refactor + coverage
7. A3 (flood-fill-continent-uncached) — refactor, modest gain
