# HUD Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current priority-based message HUD with a 3x2 dedicated-zone grid and add an audible "bonk" warning sound.

**Architecture:** Six dedicated zones in a 3-row × 2-column grid. Left column: attention, warning, command response. Right column: game status, inspector summary, inspector detail. Messages write to their zone directly — no priority arbitration, no timeouts. Warning and command zones clear on next player action. A single `.wav` bonk plays on every warning.

**Tech Stack:** Clojure/ClojureScript, Quil (Processing wrapper), javax.sound.sampled for audio.

**Spec:** `docs/superpowers/specs/2026-03-31-hud-redesign-design.md`

---

### Task 1: Add new state keys and update defaults

**Files:**
- Modify: `src/empire/state/ui.cljc`
- Modify: `src/empire/test/utils.cljc` (if `reset-all-atoms!` lists keys explicitly)

This task adds `:warning-message` and `:command-message` state keys, and keeps the old keys temporarily for backward compatibility during migration.

- [ ] **Step 1: Add new state keys to defaults**

In `src/empire/state/ui.cljc`, add two new keys to the `defaults` map after `:error-until`:

```clojure
:warning-message ""
:command-message ""
```

- [ ] **Step 2: Run tests to verify nothing breaks**

Run: `clj -M:spec`
Expected: All existing tests pass (new keys are additive).

- [ ] **Step 3: Update reset-all-atoms! if needed**

Check `src/empire/test/utils.cljc` for explicit key listings. If `reset-all-atoms!` resets to `defaults`, no change needed. If it lists keys individually, add `:warning-message` and `:command-message`.

- [ ] **Step 4: Run tests again**

Run: `clj -M:spec`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/empire/state/ui.cljc
git commit -m "Add warning-message and command-message state keys"
```

---

### Task 2: Add zone-clearing on player action

**Files:**
- Modify: `src/empire/ui/util/input/dispatch.cljc`

Every player keypress and map click should clear the warning and command zones. The single best choke point is `dispatch-key` and `key-down` in `dispatch.cljc`, plus `mouse-down`.

- [ ] **Step 1: Write failing test for zone clearing on key dispatch**

Create: `spec/empire/ui/util/input/dispatch_clearing_spec.clj`

```clojure
(ns empire.ui.util.input.dispatch-clearing-spec
  (:require [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.util.input.dispatch :as dispatch]
            [speclj.core :refer :all]))

(describe "zone clearing on player action"
  (before (reset-all-atoms!))

  (it "clears warning-message on key-down"
    (sa/write-state! :warning-message "Can't move into water.")
    (sa/write-state! :command-message "Marching orders set to 5,12")
    (sa/write-state! :waiting-for-input true)
    (sa/write-state! :map-screen-dimensions [1100 960])
    (sa/write-state! :text-area-dimensions [0 960 1100 80])
    (with-redefs [dispatch/dispatch-key (fn [_ _] nil)]
      (dispatch/key-down :w 50 50))
    (should= "" (sa/read-state :warning-message))
    (should= "" (sa/read-state :command-message))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/ui/util/input/dispatch_clearing_spec.clj`
Expected: FAIL — warning-message not cleared.

- [ ] **Step 3: Run spec-structure-check**

Run: `clj -M:spec-structure-check spec/empire/ui/util/input/dispatch_clearing_spec.clj`
Expected: OK

- [ ] **Step 4: Implement clearing in key-down**

In `src/empire/ui/util/input/dispatch.cljc`, modify `key-down`:

```clojure
(defn key-down
  "Process a key press with explicit mouse coordinates."
  [k mouse-x mouse-y]
  (sa/write-state! :warning-message "")
  (sa/write-state! :command-message "")
  (let [cell-coords (when (map-utils/on-map? mouse-x mouse-y)
                      (map-utils/determine-cell-coordinates mouse-x mouse-y))]
    (dispatch-key k cell-coords)))
```

- [ ] **Step 5: Run test to verify it passes**

Run: `clj -M:spec spec/empire/ui/util/input/dispatch_clearing_spec.clj`
Expected: PASS

- [ ] **Step 6: Add clearing on mouse-down**

In `src/empire/ui/util/input/dispatch.cljc`, modify `mouse-down`:

```clojure
(defn mouse-down
  "Handles mouse click events."
  [x y button]
  (sa/write-state! :warning-message "")
  (sa/write-state! :command-message "")
  (mouse/mouse-down x y button))
```

- [ ] **Step 7: Run all tests**

Run: `clj -M:spec`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/empire/ui/util/input/dispatch.cljc spec/empire/ui/util/input/dispatch_clearing_spec.clj
git commit -m "Clear warning and command zones on every player action"
```

---

### Task 3: Create zone-resolution pure functions

**Files:**
- Modify: `src/empire/ui/util/rendering/display/text.cljc`
- Modify: `spec/empire/ui/util/rendering/display_status_spec.clj`

Replace the banner priority system with direct zone resolution. The new functions read one state key each — no priority logic needed.

- [ ] **Step 1: Write failing test for resolve-attention-zone**

Add to `spec/empire/ui/util/rendering/display_status_spec.clj`:

```clojure
(describe "resolve-attention-zone"
  (it "returns attention text when present"
    (should= "Fighter [23,15] - Bingo!"
             (display/resolve-attention-zone "Fighter [23,15] - Bingo!")))

  (it "returns nil for empty string"
    (should-be-nil (display/resolve-attention-zone "")))

  (it "returns nil for nil"
    (should-be-nil (display/resolve-attention-zone nil))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/ui/util/rendering/display_status_spec.clj`
Expected: FAIL — function not found.

- [ ] **Step 3: Run spec-structure-check**

Run: `clj -M:spec-structure-check spec/empire/ui/util/rendering/display_status_spec.clj`
Expected: OK

- [ ] **Step 4: Implement resolve-attention-zone**

In `src/empire/ui/util/rendering/display/text.cljc`:

```clojure
(defn resolve-attention-zone
  "Returns the attention zone text, or nil if empty."
  [attention-message]
  (when (seq attention-message) attention-message))
```

- [ ] **Step 5: Run test to verify it passes**

Run: `clj -M:spec spec/empire/ui/util/rendering/display_status_spec.clj`
Expected: PASS

- [ ] **Step 6: Write failing test for resolve-warning-zone**

Add to `spec/empire/ui/util/rendering/display_status_spec.clj`:

```clojure
(describe "resolve-warning-zone"
  (it "returns warning text when present"
    (should= "Can't move into water."
             (display/resolve-warning-zone "Can't move into water.")))

  (it "returns nil for empty string"
    (should-be-nil (display/resolve-warning-zone ""))))
```

- [ ] **Step 7: Implement resolve-warning-zone**

```clojure
(defn resolve-warning-zone
  "Returns the warning zone text, or nil if empty."
  [warning-message]
  (when (seq warning-message) warning-message))
```

- [ ] **Step 8: Write failing test for resolve-command-zone**

```clojure
(describe "resolve-command-zone"
  (it "returns command text when present"
    (should= "Marching orders set to 5,12"
             (display/resolve-command-zone "Marching orders set to 5,12")))

  (it "returns nil for empty string"
    (should-be-nil (display/resolve-command-zone ""))))
```

- [ ] **Step 9: Implement resolve-command-zone**

```clojure
(defn resolve-command-zone
  "Returns the command response zone text, or nil if empty."
  [command-message]
  (when (seq command-message) command-message))
```

- [ ] **Step 10: Run all tests**

Run: `clj -M:spec`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add src/empire/ui/util/rendering/display/text.cljc spec/empire/ui/util/rendering/display_status_spec.clj
git commit -m "Add zone resolution functions for attention, warning, and command"
```

---

### Task 4: Update rendering config for 3x2 grid layout

**Files:**
- Modify: `src/empire/config/rendering.cljc`

Replace the old 4-line Y-position constants with 3-row grid positions and add column split fraction.

- [ ] **Step 1: Update rendering constants**

In `src/empire/config/rendering.cljc`, replace the message line constants:

```clojure
;; Old constants to remove:
;; msg-line-1-y, msg-line-2-y, msg-line-3-y, msg-line-4-y
;; msg-banner-separator-y, game-info-width-fraction, debug-width-fraction, game-status-width-fraction

;; New grid constants:
(def grid-row-height 26)
(def grid-row-1-y 4)
(def grid-row-2-y 30)
(def grid-row-3-y 56)
(def grid-left-fraction 0.55)
(def grid-separator-color [30 36 40])
(def grid-vertical-separator-color [54 60 66])
```

Keep `msg-left-padding`, `status-left-padding`, `status-right-padding`, `msg-separator-offset`, `hud-background-color`, `hud-top-separator-color`. Remove `hud-banner-separator-color` (replaced by `grid-separator-color`).

- [ ] **Step 2: Run tests — expect some failures**

Run: `clj -M:spec`

Some existing rendering tests reference old constants like `msg-line-1-y`, `msg-line-2-y`, `msg-banner-separator-y`. Note which tests fail — they will be updated in Task 6.

- [ ] **Step 3: Commit**

```bash
git add src/empire/config/rendering.cljc
git commit -m "Replace message line constants with 3x2 grid layout constants"
```

---

### Task 5: Rewrite draw-message-area for 3x2 grid

**Files:**
- Modify: `src/empire/ui/quil/rendering/messages.cljc`

Replace `draw-banner`, `draw-status`, `draw-inspector` with zone-based drawing functions.

- [ ] **Step 1: Replace color definitions**

At the top of `messages.cljc`, replace existing color defs:

```clojure
(def ^:private attention-color [255 215 64])
(def ^:private warning-color [255 80 80])
(def ^:private command-color [235 245 255])
(def ^:private status-color [190 198 208])
(def ^:private inspector-color [190 198 208])
(def ^:private hud-tooltip-background [245 239 200])
(def ^:private hud-tooltip-border [70 64 40])
(def ^:private hud-tooltip-text [20 20 20])
```

- [ ] **Step 2: Write draw-zone helper**

```clojure
(defn- draw-zone-text
  "Draws text in a zone at the given position with the given color."
  [text x y color]
  (when (seq text)
    (apply q/fill color)
    (q/text text x y)))
```

- [ ] **Step 3: Write zone drawing functions**

```clojure
(defn- draw-attention-zone
  [text-x text-y]
  (let [text (display/resolve-attention-zone (sa/read-state :attention-message))]
    (draw-zone-text text
                    (+ text-x rendering/msg-left-padding)
                    (+ text-y rendering/grid-row-1-y 16)
                    attention-color)))

(defn- draw-warning-zone
  [text-x text-y]
  (let [text (display/resolve-warning-zone (sa/read-state :warning-message))]
    (draw-zone-text text
                    (+ text-x rendering/msg-left-padding)
                    (+ text-y rendering/grid-row-2-y 16)
                    warning-color)))

(defn- draw-command-zone
  [text-x text-y]
  (let [text (display/resolve-command-zone (sa/read-state :command-message))]
    (draw-zone-text text
                    (+ text-x rendering/msg-left-padding)
                    (+ text-y rendering/grid-row-3-y 16)
                    command-color)))

(defn- draw-status-zone
  [text-x text-y text-w]
  (let [left-x (+ text-x (* text-w rendering/grid-left-fraction) rendering/status-left-padding)
        {:keys [left center right]}
        (display/resolve-status-line (sa/read-state :round-number)
                                     (sa/read-state :handicap-display-rounds)
                                     (sa/read-state :paused)
                                     (sa/read-state :pause-requested)
                                     (sa/read-state :map-to-display)
                                     (sa/read-state :destination)
                                     (sa/read-state :production-status)
                                     (sa/current-world)
                                     (sa/read-state :cells-needing-attention))
        right-edge (- (+ text-x text-w) rendering/status-right-padding)
        y (+ text-y rendering/grid-row-1-y 16)]
    (when left
      (apply q/fill status-color)
      (q/text left left-x y))
    (when right
      (apply q/fill status-color)
      (draw-text-right-justified right right-edge y))))

(defn- draw-inspector-zones
  [text-x text-y text-w]
  (let [left-x (+ text-x (* text-w rendering/grid-left-fraction) rendering/status-left-padding)
        {:keys [summary detail]}
        (display/resolve-inspector-lines (sa/read-state :hover-message))]
    (draw-zone-text summary left-x
                    (+ text-y rendering/grid-row-2-y 16)
                    inspector-color)
    (draw-zone-text detail left-x
                    (+ text-y rendering/grid-row-3-y 16)
                    inspector-color)))
```

- [ ] **Step 4: Write grid separator drawing**

```clojure
(defn- draw-grid-separators
  [text-x text-y text-w text-h]
  (let [col-x (+ text-x (* text-w rendering/grid-left-fraction))]
    ;; Vertical separator
    (apply q/stroke rendering/grid-vertical-separator-color)
    (q/line col-x text-y col-x (+ text-y text-h))
    ;; Horizontal separators
    (apply q/stroke rendering/grid-separator-color)
    (q/line text-x (+ text-y rendering/grid-row-2-y -2) (+ text-x text-w) (+ text-y rendering/grid-row-2-y -2))
    (q/line text-x (+ text-y rendering/grid-row-3-y -2) (+ text-x text-w) (+ text-y rendering/grid-row-3-y -2))))
```

- [ ] **Step 5: Rewrite draw-message-area**

```clojure
(defn draw-message-area
  "Draws the 3x2 zone-based HUD."
  []
  (let [[text-x text-y text-w text-h] (sa/read-state :text-area-dimensions)
        top-separator-y (- text-y config/msg-separator-offset)]
    (q/no-stroke)
    (apply q/fill rendering/hud-background-color)
    (q/rect text-x text-y text-w text-h)
    (apply q/stroke rendering/hud-top-separator-color)
    (q/line text-x top-separator-y (+ text-x text-w) top-separator-y)
    (draw-grid-separators text-x text-y text-w text-h)
    (q/text-font (sa/read-state :text-font))
    ;; Left column
    (draw-attention-zone text-x text-y)
    (draw-warning-zone text-x text-y)
    (draw-command-zone text-x text-y)
    ;; Right column
    (draw-status-zone text-x text-y text-w)
    (draw-inspector-zones text-x text-y text-w)
    ;; Tooltip
    (let [mouse-x (q/mouse-x) mouse-y (q/mouse-y)
          {:keys [left center right]}
          (display/resolve-status-line (sa/read-state :round-number)
                                       (sa/read-state :handicap-display-rounds)
                                       (sa/read-state :paused)
                                       (sa/read-state :pause-requested)
                                       (sa/read-state :map-to-display)
                                       (sa/read-state :destination)
                                       (sa/read-state :production-status)
                                       (sa/current-world)
                                       (sa/read-state :cells-needing-attention))
          tooltip (hud-tooltip mouse-x mouse-y text-x text-y text-w left center right
                               (sa/read-state :production-status))]
      (when tooltip
        (draw-tooltip tooltip mouse-x mouse-y)))))
```

- [ ] **Step 6: Remove old functions**

Delete `draw-banner`, `draw-status`, `draw-inspector`, `banner-color`, `soften-color`, and the old color defs (`banner-error-color`, `banner-result-color`, `hud-text-color`, `hud-status-color`, `hud-secondary-color`).

- [ ] **Step 7: Run tests — fix any compilation errors**

Run: `clj -M:spec`

Fix any remaining references to deleted functions or renamed constants.

- [ ] **Step 8: Commit**

```bash
git add src/empire/ui/quil/rendering/messages.cljc
git commit -m "Rewrite message area rendering as 3x2 zone grid"
```

---

### Task 6: Update existing rendering tests

**Files:**
- Modify: `spec/empire/ui/quil/rendering/messages_layout_spec.clj`
- Modify: `spec/empire/ui/quil/rendering/messages_draw_spec.clj`

Update tests to work with the new grid layout and zone-based rendering.

- [ ] **Step 1: Read existing test files to understand what they test**

Read both spec files and note which assertions reference old constants or old drawing functions.

- [ ] **Step 2: Update messages_layout_spec.clj**

The test "shows attention-cell order context in the status center" should still work but the Y coordinate will change from `140` (old msg-line-2-y = 40 offset from text-y 100) to use the new grid-row-1-y position. The status zone now starts at `text-y + grid-row-1-y + 16`. Update the expected Y coordinate and X coordinate (status is now in the right column, offset by `grid-left-fraction * text-w`).

- [ ] **Step 3: Update messages_draw_spec.clj**

Update assertions that reference old banner drawing to use the new zone functions. Replace references to `msg-line-1-y`, `msg-line-2-y`, etc. with `grid-row-1-y`, `grid-row-2-y`, `grid-row-3-y`.

- [ ] **Step 4: Run spec-structure-check on updated specs**

Run: `clj -M:spec-structure-check spec/empire/ui/quil/rendering/messages_layout_spec.clj`
Run: `clj -M:spec-structure-check spec/empire/ui/quil/rendering/messages_draw_spec.clj`
Expected: OK

- [ ] **Step 5: Run all tests**

Run: `clj -M:spec`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add spec/empire/ui/quil/rendering/
git commit -m "Update rendering tests for 3x2 grid layout"
```

---

### Task 7: Migrate message writers to new state keys

**Files:**
- Modify: `src/empire/ui/util/input/actions/helpers.cljc` — `set-error-message!` → `set-warning-message!`
- Modify: `src/empire/player/orders.cljc` — `set-turn-message!` → `set-command-message!`
- Modify: `src/empire/game_mechanics/services/ship_action_resolution.cljc` — combat messages → `:warning-message`
- Modify: all other files that write `:error-message` or `:turn-message` directly

This is the largest task. It reclassifies every message writer to target the correct zone.

- [ ] **Step 1: Replace set-error-message! with set-warning-message!**

In `src/empire/ui/util/input/actions/helpers.cljc`:

```clojure
(defn set-warning-message!
  [msg]
  (sa/write-state! :warning-message msg))
```

No timeout parameters — warnings clear on next action.

- [ ] **Step 2: Replace set-turn-message! with set-command-message!**

In `src/empire/player/orders.cljc`:

```clojure
(defn- set-command-message!
  [msg]
  (sa/write-state! :command-message msg))
```

Remove the `ms` parameter and the `decisions/turn-message-state` call. Update all callers within the file to drop the `2000` argument: `(set-command-message! message)`.

- [ ] **Step 3: Update ship_action_resolution.cljc**

Change the private `set-turn-message!` to write to `:warning-message` (combat results are warnings):

```clojure
(defn- set-warning-message!
  [msg]
  (sa/write-state! :warning-message msg))
```

In `attack-enemy`, change line 24 from:
```clojure
(set-turn-message! message Long/MAX_VALUE)
```
to:
```clojure
(set-warning-message! message)
```

- [ ] **Step 4: Update combat message format**

In `attack-enemy` (ship_action_resolution.cljc), change the message to drop blow-by-blow counts. Replace:
```clojure
(combat/format-combat-status (:log result) ...)
```
with just the outcome. Create a helper or use:
```clojure
(let [loser-type (if (= :attacker (:winner result)) (:type defender) (:type attacker))
      message (str (clojure.string/capitalize (name loser-type)) " destroyed.")]
  ...)
```

- [ ] **Step 5: Update all direct sa/write-state! callers of :error-message**

Search for every file writing `:error-message` and change to `:warning-message`:

- `src/empire/game/loop/round_start.cljc` — game over and invasion probe: change `:error-message` to `:warning-message`
- `src/empire/computer/shared/action_resolution.cljc` — change `:error-message` to `:warning-message`
- `src/empire/computer/threat_response/probe.cljc` — change `:error-message` to `:warning-message`
- `src/empire/game/loop/round_setup/fuel.cljc` — private `set-error-message!`: rename to `set-warning-message!`, write `:warning-message`, drop ms/until params
- `src/empire/game_mechanics/movement/wake_conditions/post_move.cljc` — private `set-error-message!`: same treatment
- `src/empire/ui/util/input/actions/production.cljc` — change `helpers/set-error-message!` call to `helpers/set-warning-message!`, drop ms argument
- `src/empire/ui/util/input/actions/movement.cljc` — change `helpers/set-error-message!` call to `helpers/set-warning-message!`, drop ms argument

- [ ] **Step 6: Update all direct sa/write-state! callers of :turn-message**

- `src/empire/ui/util/input/dispatch_keys.cljc` lines 68-70, 116-118: change `:turn-message` to `:command-message`, remove `:turn-message-until` writes
- `src/empire/ui/util/input/dispatch_mouse.cljc` lines 70-71: change `:turn-message` to `:command-message`, remove `:turn-message-until` writes
- `src/empire/game/save_load/persistence.cljc`: change `:turn-message` to `:command-message`, remove `:turn-message-until` writes
- `src/empire/game_mechanics/movement/waypoint.cljc`: private `set-turn-message!` → write `:command-message`, remove ms/until

- [ ] **Step 7: Update callers of helpers/set-error-message!**

All callers in Step 5 that used `helpers/set-error-message!` now call `helpers/set-warning-message!` with just the message string (no ms argument).

- [ ] **Step 8: Run all tests**

Run: `clj -M:spec`

Fix any compilation errors from renamed functions or changed signatures.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Migrate all message writers to warning-message and command-message state keys"
```

---

### Task 8: Remove "needs attention" text from attention messages

**Files:**
- Modify: `src/empire/player/attention_decisions.cljc`
- Modify: `src/empire/config/messages.cljc`
- Modify: `spec/empire/player/attention_decisions_spec.clj` (if it exists)

- [ ] **Step 1: Check for existing attention_decisions tests**

Run: `ls spec/empire/player/attention_decisions_spec.clj 2>/dev/null`

- [ ] **Step 2: Update attention-message in attention_decisions.cljc**

In `attention-message` (line 130-152), remove the ` needs attention` text from all branches:

For `airport-fighter?`:
```clojure
(str "Fighter (" (:fighter-count cell 0) " in airport)"
     (fuel-string active-unit))
```

For `carrier-fighter?`:
```clojure
(str "Fighter - aboard carrier (" (:fighter-count unit 0) " fighters)"
     (fuel-string active-unit))
```

For `transport-army?`:
```clojure
(str "Army - aboard transport (" (:army-count unit 0) " armies) - "
     (:transport-at-beach config/messages))
```

For `active-unit` (in `active-unit-attention-message`, line 112-128):
Change line 124 from:
```clojure
(str damage-prefix unit-name (:unit-needs-attention config/messages) ...)
```
to:
```clojure
(str damage-prefix unit-name ...)
```

For `:else` (city): Change from `(:city-needs-attention config/messages)` to just `"City"`.

- [ ] **Step 3: Remove unused message keys from config/messages.cljc**

Remove these keys from the `messages` map:
- `:city-needs-attention`
- `:unit-needs-attention`
- `:fighter-airport-attention`
- `:fighter-carrier-attention`
- `:army-transport-attention`
- `:damaged-unit-attention`
- `:unit-attention`

- [ ] **Step 4: Run all tests**

Run: `clj -M:spec`

Update any tests that assert on "needs attention" text.

- [ ] **Step 5: Commit**

```bash
git add src/empire/player/attention_decisions.cljc src/empire/config/messages.cljc
git commit -m "Remove 'needs attention' text from attention messages"
```

---

### Task 9: Simplify combat result format

**Files:**
- Modify: `src/empire/game_mechanics/services/ship_action_resolution.cljc`
- Modify: `src/empire/config/domain/model/combat.cljc`

Combat messages should show only the outcome, not blow-by-blow counts.

- [ ] **Step 1: Add format-combat-outcome function**

In `src/empire/config/domain/model/combat.cljc`, add:

```clojure
(defn format-combat-outcome
  "Returns just the outcome: 'Submarine destroyed.'"
  [attacker-type defender-type winner]
  (let [loser-type (if (= winner :attacker) defender-type attacker-type)]
    (str (unit-name loser-type) " destroyed.")))
```

- [ ] **Step 2: Update attack-enemy to use format-combat-outcome**

In `ship_action_resolution.cljc`, change the message construction in `attack-enemy`:

```clojure
(let [message (combat/format-combat-outcome (:type attacker)
                                             (:type defender)
                                             (:winner result))]
  ...)
```

- [ ] **Step 3: Run all tests**

Run: `clj -M:spec`
Expected: PASS (or update tests that assert on combat message format).

- [ ] **Step 4: Commit**

```bash
git add src/empire/config/domain/model/combat.cljc src/empire/game_mechanics/services/ship_action_resolution.cljc
git commit -m "Simplify combat result to show only outcome"
```

---

### Task 10: Add bonk sound

**Files:**
- Create: `resources/bonk.wav`
- Create: `src/empire/ui/sound.cljc`

- [ ] **Step 1: Generate a bonk sound file**

Create a short (~100ms) synthetic bonk sound. Use `javax.sound.sampled` to write a simple tone-burst WAV file, or download a free sound effect. Place it at `resources/bonk.wav`.

A simple approach — write a Clojure script to generate one:

```clojure
;; Run once to generate the bonk file
(import '[javax.sound.sampled AudioFormat AudioFileFormat$Type AudioSystem]
        '[java.io ByteArrayInputStream])

(let [sample-rate 44100
      duration 0.08
      n-samples (int (* sample-rate duration))
      freq 220
      buf (byte-array n-samples)]
  (dotimes [i n-samples]
    (let [t (/ (double i) sample-rate)
          envelope (Math/exp (* -30.0 t))
          sample (* envelope (Math/sin (* 2 Math/PI freq t)) 127)]
      (aset buf i (byte (int sample)))))
  (let [format (AudioFormat. sample-rate 8 1 true false)
        bais (ByteArrayInputStream. buf)
        ais (javax.sound.sampled.AudioInputStream. bais format n-samples)]
    (AudioSystem/write ais AudioFileFormat$Type/WAVE (java.io.File. "resources/bonk.wav"))))
```

- [ ] **Step 2: Create sound module**

Create `src/empire/ui/sound.cljc`:

```clojure
(ns empire.ui.sound
  (:import [javax.sound.sampled AudioSystem Clip]))

(def ^:private bonk-clip (atom nil))

(defn init-sound!
  "Loads the bonk sound clip. Call once at startup."
  []
  (try
    (let [url (clojure.java.io/resource "bonk.wav")
          clip (AudioSystem/getClip)
          stream (AudioSystem/getAudioInputStream url)]
      (.open clip stream)
      (reset! bonk-clip clip))
    (catch Exception _ nil)))

(defn play-bonk!
  "Plays the bonk warning sound."
  []
  (when-let [clip @bonk-clip]
    (.setFramePosition clip 0)
    (.start clip)))
```

- [ ] **Step 3: Write test for sound module**

Create `spec/empire/ui/sound_spec.clj`:

```clojure
(ns empire.ui.sound-spec
  (:require [empire.ui.sound :as sound]
            [speclj.core :refer :all]))

(describe "sound module"
  (it "init-sound! does not throw when resource is missing"
    (with-redefs [clojure.java.io/resource (fn [_] nil)]
      (should-not-throw (sound/init-sound!))))

  (it "play-bonk! does not throw when clip is nil"
    (should-not-throw (sound/play-bonk!))))
```

- [ ] **Step 4: Run spec-structure-check and tests**

Run: `clj -M:spec-structure-check spec/empire/ui/sound_spec.clj`
Run: `clj -M:spec spec/empire/ui/sound_spec.clj`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
mkdir -p resources
git add resources/bonk.wav src/empire/ui/sound.cljc spec/empire/ui/sound_spec.clj
git commit -m "Add bonk warning sound module"
```

---

### Task 11: Wire bonk to warning writes

**Files:**
- Modify: `src/empire/ui/util/input/actions/helpers.cljc`
- Modify: all files with private `set-warning-message!` functions

Every write to `:warning-message` should trigger the bonk.

- [ ] **Step 1: Add bonk to helpers/set-warning-message!**

In `src/empire/ui/util/input/actions/helpers.cljc`:

```clojure
(ns empire.ui.util.input.actions.helpers
  (:require [empire.state.api :as sa]
            [empire.game.loop.core :as game-loop]
            [empire.ui.sound :as sound]))

(defn set-warning-message!
  [msg]
  (sa/write-state! :warning-message msg)
  (sound/play-bonk!))
```

- [ ] **Step 2: Add bonk to all private set-warning-message! functions**

In each file that has a private `set-warning-message!` (fuel.cljc, post_move.cljc, ship_action_resolution.cljc), add `(sound/play-bonk!)` after the write. Add the require for `empire.ui.sound`.

For files that write `:warning-message` directly via `sa/write-state!` (round_start.cljc, action_resolution.cljc, probe.cljc), add `(sound/play-bonk!)` after the write.

- [ ] **Step 3: Initialize sound at startup**

In `src/empire/ui/quil/core.cljc` (or wherever `setup` is called), add:

```clojure
(sound/init-sound!)
```

- [ ] **Step 4: Run all tests**

Run: `clj -M:spec`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/empire/ui/util/input/actions/helpers.cljc src/empire/ui/sound.cljc src/empire/ui/quil/core.cljc
git commit -m "Wire bonk sound to all warning message writes"
```

---

### Task 12: Remove old message state keys and dead code

**Files:**
- Modify: `src/empire/state/ui.cljc` — remove `:error-message`, `:error-until`, `:turn-message`, `:turn-message-until`
- Modify: `src/empire/ui/util/rendering/display/text.cljc` — remove `should-show-error?`, `resolve-banner`, `active-banners`, `resolve-banner-pair`, `resolve-banner-list`, `resolve-turn-text`, `resolve-round-status-text`, `resolve-center-lines`
- Modify: `src/empire/config/messages.cljc` — remove `error-message-duration`
- Modify: `src/empire/player/orders_decisions.cljc` — remove `turn-message-state` if it only served the old timeout logic

- [ ] **Step 1: Remove old state keys from defaults**

In `src/empire/state/ui.cljc`, remove `:error-message`, `:error-until`, `:turn-message`, `:turn-message-until` from the `defaults` map.

- [ ] **Step 2: Remove dead functions from display/text.cljc**

Remove: `should-show-error?`, `resolve-banner`, `active-banners`, `resolve-banner-pair`, `resolve-banner-list`, `resolve-turn-text`, `resolve-round-status-text`, `resolve-center-lines`.

- [ ] **Step 3: Remove error-message-duration from config/messages.cljc**

Delete the `(def error-message-duration 10000)` line.

- [ ] **Step 4: Remove old tests**

Delete or update tests in `spec/empire/ui/util/rendering/display_status_spec.clj` that test `should-show-error?`, `resolve-banner`, `resolve-banner-pair`, `resolve-banner-list`, `resolve-turn-text`, `resolve-round-status-text`.

- [ ] **Step 5: Grep for any remaining references to removed keys/functions**

Run: `grep -r "error-message\|error-until\|turn-message\|turn-message-until\|resolve-banner\|should-show-error" src/ spec/ --include="*.clj*" -l`

Fix any remaining references.

- [ ] **Step 6: Run all tests**

Run: `clj -M:spec`
Expected: PASS

- [ ] **Step 7: Run acceptance tests**

Run the full acceptance pipeline:
```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Remove old message priority system and timeout state keys"
```

---

### Task 13: Update hud-tooltip for new grid layout

**Files:**
- Modify: `src/empire/ui/quil/rendering/messages.cljc` — `hud-tooltip` function
- Modify: `spec/empire/ui/quil/rendering/messages_tooltip_spec.clj`

The tooltip hover detection needs to account for the new column layout — production status is now in row 1 right column.

- [ ] **Step 1: Update hud-tooltip coordinates**

The production count is now in the right column at `grid-row-1-y`. Update `hud-tooltip` to detect hover over the right column first row instead of the old status row area.

Replace references to `msg-banner-separator-y` and `msg-line-3-y` with the new grid row positions. The production hover zone is now:
- X: from `text-x + (text-w * grid-left-fraction)` to `text-x + text-w`
- Y: from `text-y + grid-row-1-y` to `text-y + grid-row-1-y + grid-row-height`

- [ ] **Step 2: Update tooltip tests**

Update `messages_tooltip_spec.clj` to use new grid coordinates.

- [ ] **Step 3: Run tests**

Run: `clj -M:spec spec/empire/ui/quil/rendering/messages_tooltip_spec.clj`
Expected: PASS

- [ ] **Step 4: Run all tests**

Run: `clj -M:spec`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/empire/ui/quil/rendering/messages.cljc spec/empire/ui/quil/rendering/messages_tooltip_spec.clj
git commit -m "Update HUD tooltip detection for new grid layout"
```

---

### Task 14: Final verification

- [ ] **Step 1: Run all unit tests**

Run: `clj -M:spec`
Expected: All PASS

- [ ] **Step 2: Run acceptance tests**

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```
Expected: All PASS

- [ ] **Step 3: Run the game and visually verify**

Run: `clj -M:run`

Verify:
- 3x2 grid layout renders in the HUD area
- Attention messages appear in row 1 left (gold, no "needs attention" text)
- Warnings appear in row 2 left (red) with bonk sound
- Command responses appear in row 3 left (light blue)
- Game status appears in row 1 right
- Hover inspector appears in rows 2-3 right
- Warnings and commands clear on next keypress/click
- Attention persists until unit is dealt with
- Production tooltip still works on hover
- No messages overwrite each other

- [ ] **Step 4: Commit final state**

```bash
git add -A
git commit -m "Complete HUD redesign: 3x2 zone grid with bonk warning sound"
```
