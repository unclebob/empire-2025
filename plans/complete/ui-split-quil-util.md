# Plan: Split UI modules into quil/ and util/ sub-packages

## Context

The three UI modules (`rendering.cljc`, `input.cljc`, `core.cljc`) mix Quil-dependent drawing code with pure testable logic. This refactoring separates them into `empire.ui.quil.*` (Quil-dependent) and `empire.ui.util.*` (pure, testable) sub-namespaces to improve testability and enforce the Quil isolation boundary. All files stay under ~200 lines.

## New File Structure

```
src/empire/ui/
  quil/
    rendering/
      map.cljc        (empire.ui.quil.rendering.map)       ~95 lines
      messages.cljc    (empire.ui.quil.rendering.messages)  ~100 lines
      overlay.cljc     (empire.ui.quil.rendering.overlay)   ~70 lines
    input.cljc         (empire.ui.quil.input)               ~15 lines
    core.cljc          (empire.ui.quil.core)                ~120 lines
  util/
    rendering/
      format.cljc      (empire.ui.util.rendering.format)    ~105 lines
      display.cljc     (empire.ui.util.rendering.display)   ~140 lines
    input/
      actions.cljc     (empire.ui.util.input.actions)       ~190 lines
      dispatch.cljc    (empire.ui.util.input.dispatch)      ~180 lines
    core.cljc          (empire.ui.util.core)                ~45 lines
```

## Function Assignments

### quil/rendering/map.cljc — Map drawing
- `draw-map` (public)
- `draw-production-indicators` (public)
- `draw-unit` (private)
- `draw-waypoint` (private)
- `draw-debug-selection-rectangle` (public)
- Requires: `quil.core`, `empire.atoms`, `empire.config`, `empire.ui.util.rendering.format`, `empire.ui.util.rendering.display`, `empire.movement.map-utils`

### quil/rendering/messages.cljc — Message area drawing
- `draw-message-area` (public)
- `draw-text-right-justified` (private)
- `draw-attention` (private)
- `draw-turn` (private)
- `draw-error` (private)
- `draw-game-info` (private)
- `draw-debug` (private)
- `draw-round-status` (private)
- `draw-hover-info` (private)
- `draw-production-status` (private)
- `draw-game-status` (private)
- Requires: `quil.core`, `empire.atoms`, `empire.config`, `empire.ui.util.rendering.display`, `empire.movement.map-utils`

### quil/rendering/overlay.cljc — Overlay/hover drawing
- `update-hover-status` (public)
- `draw-load-menu` (public)
- Requires: `quil.core`, `empire.atoms`, `empire.config`, `empire.save-load`, `empire.movement.map-utils`, `empire.ui.util.rendering.display`

### quil/input.cljc — Quil input wrappers (2 functions)
- `mouse->cell` (make public)
- `key-down` (public)
- Requires: `quil.core`, `empire.movement.map-utils`, `empire.ui.util.input.dispatch`

### quil/core.cljc — Quil lifecycle (11 functions)
- `create-fonts`, `setup`, `update-state`, `draw-state`
- `key-pressed`, `get-modifiers` (private)
- `mouse-pressed`, `mouse-dragged`, `mouse-released`
- `on-close`, `-main`
- Requires: `quil.core`, `quil.middleware`, `empire.atoms`, `empire.config`, `empire.init`, `empire.game-loop`, `empire.ui.quil.rendering.map`, `empire.ui.quil.rendering.messages`, `empire.ui.quil.rendering.overlay`, `empire.ui.quil.input`, `empire.ui.util.input.dispatch`, `empire.ui.util.core`

### util/rendering/format.cljc — Status string formatting
- `format-unit-status` (public)
- `format-city-status` (public)
- `format-waypoint-status` (public)
- `format-hover-status` (public)
- `format-production-status` (public)
- Private helpers: `unit-fuel-str`, `unit-cargo-str`, `transport-mission-str`, `patrol-mode-str`, `army-mission-str`, `unit-orders-str`, `format-ship-for-dock`, `format-shipyard`
- Constants: `unit-type-order`, `unit-type-labels`
- Requires: `empire.config`, `empire.containers.helpers`, `empire.units.dispatcher`

### util/rendering/display.cljc — Display computation
- `should-show-error?` (public)
- `resolve-turn-text` (public)
- `should-show-paused?` (public)
- `resolve-round-status-text` (public)
- `resolve-display-map` (public)
- `compute-hover-message` (public)
- `compute-hover-result` (public)
- `determine-display-unit` (public)
- `production-indicator-data` (public)
- `group-cells-by-color` (public)
- Requires: `empire.config`, `empire.containers.helpers`, `empire.ui.util.rendering.format`

### util/input/actions.cljc — Unit/city action handlers
- `handle-key` (public — entry point for unit/city key handling)
- `army-aboard-action` (public)
- Private: `try-set-production`, `handle-city-production-key`, `calculate-extended-target`, `launch-fighter-and-update`, `handle-army-aboard-movement`, `undamaged-ship-entering-friendly-city?`, `handle-standard-unit-movement`, `execute-unit-movement`, `handle-unit-movement-key`, `handle-space-key`, `handle-unload-key`, `handle-sentry-key`, `find-adjacent-land`, `handle-look-around-key`
- Requires: `empire.atoms`, `empire.config`, `empire.combat`, `empire.containers.ops`, `empire.containers.helpers`, `empire.game-loop`, `empire.movement.coastline`, `empire.movement.explore`, `empire.movement.map-utils`, `empire.movement.movement`, `empire.player.attention`, `empire.player.orders`, `empire.player.production`, `empire.player.commands`, `empire.units.dispatcher`

### util/input/dispatch.cljc — Key dispatch + mouse + debug
- `handle-unit-click` (public — alias to commands/handle-unit-click)
- `handle-cell-click` (public)
- `handle-load-menu-click` (public)
- `mouse-down` (public)
- `modifier-held?` (public)
- `debug-drag-start!`, `debug-drag-update!`, `debug-drag-end!` (public)
- `dispatch-key` (public — top-level key dispatcher)
- Private: `has-area?`, `dispatch-load-menu-key`, `dispatch-backtick-key`, `dispatch-game-control-key`, `dispatch-save-load-key`, `dispatch-standing-order-key`, `dispatch-coord-key`, `dispatch-normal-key`
- Constants: `backtick-unit-map`, `standing-order-handlers`, `backtick-key`, `bang-key`, `caret-key`
- Requires: `empire.atoms`, `empire.config`, `empire.debug`, `empire.game-loop`, `empire.movement.map-utils`, `empire.player.attention`, `empire.player.commands`, `empire.player.orders`, `empire.save-load`, `empire.ui.util.input.actions`

### util/core.cljc — Pure core functions (5 functions)
- `screen->cell` (public — from coordinates.cljc)
- `compute-screen-dimensions` (public)
- `calculate-screen-dimensions` (public)
- `screen-dimensions` (private — AWT)
- `key-released` (public)
- Requires: `empire.atoms`, `empire.config`

## Files to Delete
- `src/empire/ui/rendering.cljc`
- `src/empire/ui/rendering_util.cljc`
- `src/empire/ui/input.cljc`
- `src/empire/ui/core.cljc`
- `src/empire/ui/coordinates.cljc`

## External Dependency Updates

| Consumer | Old require | New require |
|----------|-----------|-------------|
| `empire.game-loop` | `empire.ui.rendering-util :as ru` | `empire.ui.util.rendering.format :as ru` (uses `ru/format-production-status`) |
| `empire.debug` | `empire.ui.coordinates :as coords` | `empire.ui.util.core :as coords` |
| `empire.acceptance.generator` | `"[empire.ui.input :as input]"` (string) | `"[empire.ui.util.input.dispatch :as input]"` |

## Test File Changes

| Old test file | New test file(s) |
|--------------|-----------------|
| `spec/empire/ui/rendering_util_spec.clj` | Split into `spec/empire/ui/util/rendering/format_spec.clj` + `spec/empire/ui/util/rendering/display_spec.clj` |
| `spec/empire/ui/input_spec.clj` | `spec/empire/ui/util/input/actions_spec.clj` (or split actions/dispatch) |
| `spec/empire/ui/core_spec.clj` | `spec/empire/ui/util/core_spec.clj` |
| `spec/empire/ui/coordinates_spec.clj` | Merge into `spec/empire/ui/util/core_spec.clj` |
| `spec/empire/ui/army_aboard_spec.clj` | `spec/empire/ui/util/input/army_aboard_spec.clj` — require `empire.ui.util.input.actions` |
| `spec/empire/ui/mouse_down_spec.clj` | `spec/empire/ui/util/input/mouse_down_spec.clj` — require `empire.ui.util.input.dispatch` |
| `spec/empire/map_spec.clj` | (stays) — require `empire.ui.util.input.dispatch` |

Acceptance test spec expectations:
- `spec/empire/acceptance/generator_spec.clj` — update string match
- `spec/empire/acceptance/generator_output_spec.clj` — update string match

Old test files to delete:
- `spec/empire/ui/rendering_util_spec.clj`
- `spec/empire/ui/input_spec.clj`
- `spec/empire/ui/core_spec.clj`
- `spec/empire/ui/coordinates_spec.clj`
- `spec/empire/ui/army_aboard_spec.clj`
- `spec/empire/ui/mouse_down_spec.clj`

## Execution Order

1. Create directory structure
2. Create util files first (no Quil deps, needed by quil files):
   a. `util/rendering/format.cljc`
   b. `util/rendering/display.cljc`
   c. `util/input/actions.cljc`
   d. `util/input/dispatch.cljc`
   e. `util/core.cljc`
3. Create quil files (depend on util):
   a. `quil/rendering/map.cljc`
   b. `quil/rendering/messages.cljc`
   c. `quil/rendering/overlay.cljc`
   d. `quil/input.cljc`
   e. `quil/core.cljc`
4. Update external consumers: `game_loop.cljc`, `debug.cljc`, `acceptance/generator.cljc`
5. Create new test files, delete old test files
6. Update acceptance test string expectations
7. Delete old source files
8. Run `clj -M:spec` — verify all tests pass
9. Run `clj -M:spec-structure-check` on new spec files

## Verification

```bash
clj -M:spec
clj -M:spec-structure-check spec/empire/ui/util/
grep -rn "empire\.ui\.rendering\b\|empire\.ui\.rendering-util\|empire\.ui\.input\b\|empire\.ui\.core\b\|empire\.ui\.coordinates" src/ spec/ --include="*.clj*" | grep -v "empire\.ui\.quil\|empire\.ui\.util"
```
