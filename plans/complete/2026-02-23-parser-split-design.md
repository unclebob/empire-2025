# Parser Split Design

## Problem
The acceptance test parser (`src/empire/acceptance/parser.cljc`) is 1186 lines. This fills Claude's context window, leaving no room for the spec or acceptance tests being debugged.

## Approach
Split along the existing GIVEN/WHEN/THEN seams into a `parser/` subdirectory.

## File Structure

```
src/empire/acceptance/
  parser.cljc              ~85 lines  — public API + test splitting + CLI
  parser/
    helpers.cljc           ~110 lines — shared utils, char sets, pattern dispatch
    given.cljc             ~315 lines — GIVEN extractors, handlers, patterns, parse-given
    when.cljc              ~150 lines — WHEN handlers, patterns, parse-when
    then.cljc              ~450 lines — THEN handlers, patterns, parse-then
```

## Dependencies

```
parser.cljc → parser/{given, when, then, helpers}
given.cljc  → parser/helpers
when.cljc   → parser/helpers
then.cljc   → parser/helpers
```

## What Moves Where

- **helpers.cljc** (L7-112): `strip-trailing-period`, `strip-keyword-prefix`, `blank-or-comment?`, `separator-line?`, `map-row?`, `territory-map-row?`, direction helpers, `parse-coords`, `parse-number`, `parse-count`, char sets (`unit-name->char`, `player-unit-chars`, etc.), `unit-char?`, `city-or-unit-char?`, `cell-prop-aliases`, `resolve-cell-prop`, `first-matching-pattern`, `first-matching-pattern-with-context`
- **given.cljc** (L114-428): `unit-prop-extractors`, `parse-unit-props-line`, `parse-container-state-line`, all `given-handle-*` functions, `given-map-patterns`, `given-directive-patterns`, `parse-given-line`, `parse-given`
- **when.cljc** (L430-578): `determine-key-type`, `determine-combat-type`, all `when-handle-*` functions, `when-patterns`, `parse-when`
- **then.cljc** (L580-1027): all `then-handle-*` functions, `strip-then-preamble`, `tag-timing`, `parse-single-then-clause`, `split-then-continuations`, `split-compound-then`, `extract-then-map-blocks`, `then-bare-patterns`, `then-timed-patterns`, `parse-then`
- **parser.cljc** (L1029-1186): `split-into-tests`, `extract-unit-types-from-givens`, `has-waiting-for-input?`, `parse-test`, `parse-file`, `validate-config-keys`, `write-edn`, `-main`

## Visibility Changes

Functions currently `defn-` (private) that are used across namespaces become `defn` in their new home. This applies mainly to helpers: `strip-trailing-period`, `strip-keyword-prefix`, `blank-or-comment?`, `map-row?`, `parse-coords`, `parse-number`, `parse-count`, `city-or-unit-char?`, `unit-char?`, `resolve-cell-prop`, etc.

Handler functions remain `defn-` within their own namespace (given, when, then). Only the public `parse-given`, `parse-when`, `parse-then` are `defn`.

## Spec Split

The 1030-line spec (`spec/empire/acceptance/parser_spec.clj`) splits along the same seams:
- `spec/empire/acceptance/parser_spec.clj` — top-level tests (split-into-tests, parse-file)
- `spec/empire/acceptance/parser/helpers_spec.clj`
- `spec/empire/acceptance/parser/given_spec.clj`
- `spec/empire/acceptance/parser/when_spec.clj`
- `spec/empire/acceptance/parser/then_spec.clj`

## Public API

No change. `empire.acceptance.parser/parse-file` and `parse-test` remain the entry points. No callers (generator, deps.edn aliases) need updating.
