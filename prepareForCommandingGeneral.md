# Prepare for CommandingGeneral

## Revised Improvement Plan

### Phase 1: Dispatcher Refactoring

Replace case statements with multimethods so each unit module is self-contained.

**Current structure:**
```clojure
;; dispatcher.cljc - large case statements
(defn speed [unit-type]
  (case unit-type
    :army 1
    :fighter 8
    :transport 2
    ...))
```

**Target structure:**
```clojure
;; dispatcher.cljc - multimethod definitions
(defmulti speed identity)
(defmulti cost identity)
(defmulti hits identity)
...

;; units/army.cljc - self-contained
(defmethod dispatcher/speed :army [_] 1)
(defmethod dispatcher/cost :army [_] 5)
(defmethod dispatcher/hits :army [_] 1)
```

**Files affected:**
- `src/empire/units/dispatcher.cljc` - convert to multimethods
- `src/empire/units/*.cljc` (9 files) - add defmethod implementations

### Phase 2: Quil Isolation

Extract pure coordinate/input logic from UI modules.

**Create:** `src/empire/ui/coordinates.cljc`
- `screen->cell` - convert pixel coords to grid cell
- `cell->screen` - convert grid cell to pixel coords
- `in-map-bounds?` - check if position is valid

**Modify:** `src/empire/ui/input.cljc`
- Keep only Quil calls (`q/mouse-x`, `q/mouse-y`, `q/key-as-keyword`)
- Delegate logic to pure functions

### Phase 3: Consolidate Utilities

Move duplicated functions to `movement/map-utils.cljc`:

| Function | Currently In | Move To |
|----------|--------------|---------|
| `is-players?` | movement.cljc, visibility.cljc | map-utils.cljc |
| `is-computers?` | movement.cljc, visibility.cljc | map-utils.cljc |
| `adjacent?` | computer/core.cljc | map-utils.cljc |

### Phase 4: Testing

Add tests for:
- Movement sidestepping logic
- Combat edge cases (ties, unit destruction)
- Visibility updates
- Container operations (load/unload)
- Coordinate conversions (new pure functions)

---

## Execution Order

| Step | Task | Files | Status |
|------|------|-------|--------|
| 1 | Convert dispatcher to multimethods | dispatcher.cljc | DONE |
| 2 | Move defmethods to unit modules | 9 unit files | DONE |
| 3 | Create coordinates.cljc | new file | DONE |
| 4 | Refactor input.cljc to use coordinates | input.cljc | DONE |
| 5 | Consolidate duplicate utilities | map-utils.cljc, movement.cljc, visibility.cljc, computer/core.cljc, computer/threat.cljc | DONE |
| 6 | Add tests for refactored code | spec files | DONE |

## Summary

All phases completed. 1086 tests passing. Key changes:
- Dispatcher now uses multimethods (extensible)
- Unit modules are self-contained with their own defmethod registrations
- New `empire.units.registry` loads all unit modules
- New `empire.ui.coordinates` contains pure coordinate functions
- Ownership predicates consolidated in `map-utils.cljc`
- Distance/adjacency functions consolidated in `coordinates.cljc`
