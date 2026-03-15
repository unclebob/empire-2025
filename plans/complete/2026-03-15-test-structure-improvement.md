# Test Structure Improvement Plan

## Goal

Make the Speclj suite easier to understand, cheaper to maintain, and more reliable by reducing setup noise, shrinking oversized spec files, and separating pure tests from atom-backed integration tests.

## Problems To Solve

- Large spec files mix unrelated behaviors, regressions, and infrastructure checks.
- Many tests construct state with repeated `assoc-in` / `update-test-world!` calls.
- “Unit” tests often depend on global atom reset and runtime state, so failures are harder to localize.
- Private-var calls (`#'...`) are used as a pressure valve for structure problems instead of a deliberate testing boundary.
- Regression tests are valuable, but they are mixed into behavior specs without a clear naming convention.

## Desired End State

- Specs are organized by behavior, not just by source namespace.
- Common setup is expressed in high-level builders and scenario helpers.
- Pure functions are tested with plain maps and values.
- Atom-backed tests are clearly identified as orchestration or integration tests.
- Regression coverage remains, but is easier to scan and extend.

## Workstreams

### 1. Split Oversized Specs

Start with the specs that are clearly aggregating too many concerns.

Candidates:

- `spec/empire/computer/kamikazee_spec.clj`
- `spec/empire/game_loop_rounds_spec.clj`
- `spec/empire/debug_spec.clj`

Target structure:

- routing specs
- mission specs
- airport / container specs
- round orchestration specs
- integrity / debug specs
- regression specs for known bugs

### 2. Build Better Test DSL Helpers

Expand `empire.test.utils` so tests can express intent directly.

Add helpers such as:

- create a computer fighter with mission fields
- create a player army with a given mode
- place a computer city with airport counts
- install major invasion state with sensible defaults
- run one fighter threat step / one round setup pass

The rule is that test setup should read like scenario data, not like patch surgery.

### 3. Separate Test Layers

Define explicit test categories:

- Pure decision tests
- Stateful module tests
- Round-loop orchestration tests
- Acceptance-generated specs

Use these categories to decide how much setup is acceptable.

Guideline:

- If a function can be tested without atoms, do that first.
- Use atom-backed tests only when the behavior genuinely depends on state transitions across modules.

### 4. Clean Up Private-Var Testing

Audit tests that reach into private functions.

For each one:

- keep it private-var based if it is truly a narrow internal pure helper
- otherwise expose a better public seam or extract a pure helper namespace

The aim is not to ban private-var testing; it is to stop using it as a substitute for good module boundaries.

### 5. Standardize Regression Specs

Adopt a consistent regression pattern:

- name the bug in the example text
- keep the scenario minimal
- assert only what proves the bug is fixed

If a regression belongs to a specific behavior cluster, keep it there. If not, use a dedicated regression spec per subsystem.

## Suggested Implementation Order

1. Split `kamikazee_spec.clj` into behavior-focused specs.
2. Add mission/city/invasion builders to `test-utils`.
3. Split `game_loop_rounds_spec.clj` into round-start, pause, and handicap specs.
4. Split `debug_spec.clj` into dump/logging/integrity specs.
5. Audit private-var usage and extract pure seams where it buys clarity.

## Success Criteria

- New bug fixes can usually be added to a small, obvious spec file.
- Most examples need only a few lines of setup.
- Pure logic is mostly tested without atom mutation.
- Stateful tests read as scenario execution instead of low-level map rewrites.
- Large spec files stop growing as “miscellaneous holding areas.”
