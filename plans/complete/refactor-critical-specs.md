# Refactor Critical Specs

## Goal

Reduce SCRAP pressure in the critical spec files without weakening coverage or hiding intent behind opaque helpers.

## Files Reviewed

- `spec/empire/acceptance/parser/given_spec.clj`
- `spec/empire/computer/army_coastal_spec.clj`
- `spec/empire/computer/early_game/strategy_spec.clj`
- `spec/empire/computer/kamikazee_routing_spec.clj`
- `spec/empire/debug_dump_spec.clj`
- `spec/empire/ui/quil/rendering/messages_spec.clj`

## Assessment

### `spec/empire/acceptance/parser/given_spec.clj`

I agree with the tool.

Reason:
- The file is mostly a flat matrix of one-line parser cases.
- The pressure is coming from repetition, not example complexity.
- Table-driving by directive family should lower duplication without obscuring behavior.

Recommended treatment:
- Keep one spec file.
- Refactor into grouped table-driven examples by parser concern.

### `spec/empire/computer/army_coastal_spec.clj`

I agree with the tool.

Reason:
- The file already contains three distinct responsibilities:
  `lake handling`, `local-empty-coast-target`, `move-to-coast-for-invasion`, and `fill-coastal-cell`.
- The setup is integration-heavy and the examples mix map construction with behavior assertions.
- Splitting first is safer than trying to compress everything with helpers.

Recommended treatment:
- Split into multiple files by behavior family.

### `spec/empire/computer/early_game/strategy_spec.clj`

I mostly agree with the tool’s `REVIEW_FIRST` stance.

Reason:
- The file mixes:
  `theater phase characterization`,
  `opening production`,
  `coastal staging`,
  and `desired-role-counts`.
- Some sections are naturally data-driven, especially `desired-role-counts`.
- Other sections are stateful and scenario-based, so aggressive helper extraction would likely hide important setup.

Recommended treatment:
- Split or partially split by conceptual area.
- Then table-drive only the pure policy sections.

### `spec/empire/computer/kamikazee_routing_spec.clj`

I agree with `REVIEW_FIRST`, but I would not split immediately.

Reason:
- The file is still small enough to keep together for now.
- The main issue is mixed abstraction levels:
  graph-building scenarios,
  helper behavior,
  and production override logic.
- A light reorganization into contexts plus a small table for helper-style cases may be enough.

Recommended treatment:
- Reorganize locally first.
- Re-evaluate before splitting.

### `spec/empire/debug_dump_spec.clj`

I agree with the tool.

Reason:
- The file mixes many unrelated output concerns:
  cell formatting,
  range extraction,
  filename/writing,
  movement formatting,
  and full dump rendering.
- The file is a clear candidate for splitting by output surface.

Recommended treatment:
- Split by feature area before smaller cleanup.

### `spec/empire/ui/quil/rendering/messages_spec.clj`

I strongly agree with the tool.

Reason:
- The file already has at least four separate responsibilities:
  `draw-message-area`,
  `hud-tooltip`,
  `tooltip-box-position`,
  and `tooltip rendering integration`.
- The rendering tests have repetitive state setup and many literal render-call assertions.
- This is structural pressure, not just duplication noise.

Recommended treatment:
- Split by rendering concern.

## Refactoring Strategy

### Phase 1: Split the obvious multi-responsibility files

1. Split `spec/empire/computer/army_coastal_spec.clj` into:
   - `spec/empire/computer/army_coastal_lakes_spec.clj`
   - `spec/empire/computer/army_coastal_targeting_spec.clj`
   - `spec/empire/computer/army_coastal_invasion_spec.clj`
   - or a similar responsibility-based layout that preserves discoverability.

2. Split `spec/empire/debug_dump_spec.clj` into:
   - `spec/empire/debug_dump_format_cell_spec.clj`
   - `spec/empire/debug_dump_range_spec.clj`
   - `spec/empire/debug_dump_output_spec.clj`

3. Split `spec/empire/ui/quil/rendering/messages_spec.clj` into:
   - `spec/empire/ui/quil/rendering/messages_draw_spec.clj`
   - `spec/empire/ui/quil/rendering/messages_tooltip_spec.clj`
   - `spec/empire/ui/quil/rendering/messages_layout_spec.clj`

### Phase 2: Consolidate repetitive parser and policy matrices

4. Refactor `spec/empire/acceptance/parser/given_spec.clj` into table-driven groups:
   - map parsing
   - unit prop parsing
   - container-state parsing
   - directive parsing
   - stub/special directive parsing

5. Refactor `spec/empire/computer/early_game/strategy_spec.clj` by:
   - moving `desired-role-counts` cases into a table-driven section,
   - isolating `opening-production` scenarios,
   - isolating `allow-coastal-staging?` scenarios.

### Phase 3: Local cleanup on the review-first files

6. Reorganize `spec/empire/computer/kamikazee_routing_spec.clj` into explicit contexts:
   - target choice
   - routing graph construction
   - route planning helpers
   - production override behavior

7. After reorganization, rerun `scrap` on the file and only split if the pressure remains critical.

## Constraints

- Do not reduce behavioral coverage just to improve the score.
- Prefer table-driven tests for pure parser/policy matrices.
- Prefer file splits over helper extraction when setup and assertions describe different responsibilities.
- Avoid “magic” shared helpers that hide map topology or expected render calls.
- Keep file names aligned with production module concepts so the test surface stays navigable.

## Verification

After each refactor batch:

1. Run `clj -M:spec-structure-check` on changed spec files.
2. Run the changed specs.
3. Run `clj -M:scrap spec` or at minimum `clj -M:scrap` on the affected spec paths.
4. Confirm that the SCRAP improvement came from clearer structure, not weaker assertions.
