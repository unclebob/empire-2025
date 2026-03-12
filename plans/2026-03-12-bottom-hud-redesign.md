# Bottom HUD Redesign

## Status: reviewed

## Goal

Replace the current three-region bottom display area with a clearer
four-line HUD organized by message priority and reading order.

The new HUD should answer these questions in order:

1. What do I need to know right now?
2. What state is the game in?
3. What am I looking at?

This redesign is display-only. It does not change game behavior or
message-producing state.

## Current Problems

The existing bottom strip mixes too many unrelated concerns:

- left: attention, result, and error messages
- center: debug output
- right: round state, hover text, and production status

This creates several issues:

- actionable and non-actionable text compete visually
- errors do not have a dedicated high-priority channel
- hover text churns constantly in the same space as stable status
- debug output consumes permanent player-facing real estate
- destination is treated like a transient turn message instead of stable state
- the player must scan left, center, and right every frame

## New Layout

The new bottom panel is a 4-line hierarchy, not a 3-region dashboard:

```text
|--------------------------------------------------------------|
| Banner                                                       |
|--------------------------------------------------------------|
| Status: left                 center                 right     |
| Inspector summary                                            |
| Inspector detail                                             |
|--------------------------------------------------------------|
```

### Line 1 — Banner

Full-width, single-message, highest-priority line.

Priority:

1. `error-message`
2. `attention-message`
3. `turn-message`
4. empty

Message kinds:

- `:error`
- `:attention`
- `:result`
- `:empty`

The banner owns urgency. It should never compete with hover or production text.

### Line 2 — Status

Stable status line with three anchored zones:

- left: round / pause / map mode
- center: destination / waypoint / current order context
- right: production summary

The status line should be compact and low-churn.

### Line 3 — Inspector Summary

Primary hover/selection summary.

Examples:

- `[12,7] player destroyer [2/3] awake`
- `[4,9] city:player producing:fighter`
- `[20,3] sea`

### Line 4 — Inspector Detail

Secondary details only when useful.

Examples:

- `fuel:20  fighters:2  mission:escort`
- `shipyard:destroyer  remaining:3`
- `waypoint:14,9`

If there is nothing useful to show, this line remains empty.

## Message Categories

### 1. Banner Messages

These stay in the banner:

- `error-message`
- `attention-message`
- `turn-message`

They should no longer share space with hover text or production summary.

### 2. Stable Status

These belong on the status line:

- `round-number`
- `paused`
- `pause-requested`
- `map-to-display`
- `destination`
- `production-status`

### 3. Inspector

These belong in the inspector:

- `hover-message`

The inspector should derive summary/detail lines from hover state instead of
rendering one long raw string.

### 4. Debug

`debug-message` should not be shown in the normal player HUD.

For now:

- remove it from the bottom panel
- do not re-home it in this change

A separate debug overlay can be designed later if needed.

## Visual Design

### Fonts

- Use one mono font for the entire HUD.
- Give the banner slightly stronger emphasis via size or weight.
- Avoid introducing a second visual language for debug.

### Colors

Use a small semantic palette:

- HUD background: dark charcoal
- body text: soft white
- secondary/status text: muted gray
- error banner: red
- attention banner: amber
- result banner: pale cyan or white

Avoid the current cyan/white/red mixture outside semantic use.

### Separators

Use minimal but clear structure:

- keep the top separator above the HUD
- add a subtle horizontal separator below the banner
- avoid permanent vertical dividers unless spacing proves insufficient

## Text Fitting Rules

### Banner

- longest width budget
- truncate last
- no scrolling behavior in this plan

### Status

- fixed width budgets for left / center / right zones
- abbreviate aggressively before truncating
- center zone collapses before left zone

### Inspector Summary

- preserve more than detail
- truncate with ellipsis if needed

### Inspector Detail

- lower priority than summary
- truncate first

No marquee / Times Square behavior is included in this plan.

## Data Model

Introduce a pure HUD view model in the display layer:

```clojure
{:banner {:kind :error|:attention|:result|:empty
          :text "..."}
 :status {:left "PAUSED  Round 17  Map: Player"
          :center "Dest 12,7"
          :right "Prod: fighter 2r"}
 :inspector {:summary "[12,7] player carrier [5/8] sentry"
             :detail "fuel:32  fighters:3  orders:14,9"}}
```

The Quil rendering layer should consume this model rather than re-reading many
atoms ad hoc.

## Implementation Strategy

### Step 1 — Pure HUD composition helpers

Add pure helpers in `ui.util.rendering.display`:

- `resolve-banner`
- `resolve-status-line`
- `resolve-inspector-lines`

These functions should centralize layout decisions, priorities, abbreviations,
and truncation.

No visual changes yet.

### Step 2 — Inspector split

Refactor hover formatting so the display layer can produce:

- summary text
- detail text

Do not rely on one raw `hover-message` string for final presentation.

### Step 3 — Four-line geometry

Update rendering config:

- increase text area rows from 3 to 4
- add any needed line offset constants
- preserve the existing separator above the panel
- add a banner separator below line 1

### Step 4 — Rewrite bottom-panel renderer

Rewrite `ui.quil.rendering.messages/draw-message-area` to render:

- full-width banner
- one status row with left/center/right anchors
- two inspector rows

Remove the permanent center debug region.

### Step 5 — Compact formatting

Introduce compact status formats:

- `Round 17`
- `Map: Player`
- `Dest 12,7`
- abbreviated production summary

Destination should live in status, not as a turn-message fallback.

### Step 6 — Semantic colors

Apply visual styling by semantic role:

- banner colors by message kind
- stable subdued colors for status and inspector
- no debug cyan in the default HUD

### Step 7 — Cleanup

Remove obsolete rendering helpers tied to the old 3-region model.

Likely removals or simplifications:

- left/center/right message region helpers in `ui.quil.rendering.messages`
- destination fallback logic from banner/turn rendering
- debug region drawing in the bottom HUD

## Files Likely To Change

- `src/empire/ui/quil/rendering/messages.cljc`
- `src/empire/ui/util/rendering/display.cljc`
- `src/empire/ui/util/rendering/format.cljc`
- `src/empire/config/rendering.cljc`
- rendering specs under `spec/empire/ui/...`

## Verification

Before implementation:

- create focused specs for HUD composition and layout helpers

During implementation:

1. `clj -M:spec-structure-check` on changed specs
2. focused rendering specs
3. `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
4. `clj -M:crap` on changed production modules
5. differential mutation, one changed production module at a time

## Out of Scope

- marquee / Times Square scrolling
- new gameplay state
- moving debug output into a replacement overlay
- redesigning hover data semantics beyond summary/detail formatting

## Notes

This plan supersedes the old three-region bottom display model described in
`plans/complete/display-area-redesign.md`.
