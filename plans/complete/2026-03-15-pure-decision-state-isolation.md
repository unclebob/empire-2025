# Pure Decision / State Isolation Plan

## Goal

Strengthen the architectural separation between decision logic and state mutation so that AI, movement, and round processing become easier to reason about, easier to test, and less likely to create stale-write bugs.

## Problems To Solve

- Many functions read runtime state and mutate it in the same body.
- A single call path often mixes:
  - target selection
  - movement choice
  - combat side effects
  - post-move cleanup
- State transitions are hard to replay because the “decision” is not represented as data.
- Bugs like phantom contents and stale writes are easier to create when later code assumes earlier mutations succeeded.

## Desired End State

- Decision functions are predominantly pure.
- Mutating functions are thin application layers around explicit decisions.
- A step in the AI can be represented as data, not just as nested side effects.
- Post-action code only runs when the action result says it is valid to do so.
- Round integrity checks become a backstop, not a primary debugging tool.

## Target Architecture

Use a two-phase pattern wherever practical:

1. Decide
   - input: immutable world snapshot + explicit parameters
   - output: decision data

2. Apply
   - input: decision data
   - effect: mutate world / runtime state

Example decision outputs:

- `{:action :move :from [x y] :to [a b]}`
- `{:action :attack :from [x y] :to [a b]}`
- `{:action :land-at-city :from [x y] :city [a b]}`
- `{:action :launch-kamikazee :city [x y] :launch-pos [a b] :fighter fighter-data}`
- `{:action :no-op :reason :dead-or-stale}`

## Workstreams

### 1. Extract Pure Decision Helpers First

Start with logic that already has an implicit decision embedded inside a mutating function.

Good candidates:

- kamikazee route/hunt step selection
- fighter movement target choice
- airport launch target cell selection
- major invasion assignment choice
- round-start selection decisions that currently both compute and apply

The first extraction should return data only, with no state writes.

### 2. Introduce Step Result Objects

Movement and combat need explicit result objects.

Instead of returning truthy / falsy / position interchangeably, standardize on result maps such as:

- `{:result :moved :pos [x y] :steps-used 1}`
- `{:result :attacker-died :steps-used 1}`
- `{:result :blocked :reason :no-path}`
- `{:result :landed :city [x y] :steps-used 1}`

This is especially important in fighter and kamikazee code, where `nil` currently overloads several meanings.

### 3. Thin the Mutating Shell

For each extracted decision helper, keep one small mutating function that:

- validates the preconditions
- applies the chosen action
- returns the explicit result object

Do not allow follow-up writes to infer what happened from old assumptions. They should consume the explicit result.

### 4. Reduce Direct `sa/read-state` / `sa/update-world!` Scatter

Pass in what a pure function needs rather than letting it read atoms itself.

For mutating code:

- centralize world updates in a smaller number of application functions
- prefer a small number of mutations with clear order over many scattered updates

This will make it easier to audit state transitions and to replay bug scenarios in tests.

### 5. Establish Module Boundaries By Role

Split modules by responsibility:

- decision modules
- mutation/application modules
- orchestration modules

For example:

- mission decision
- movement resolution
- combat application
- round orchestration

The namespace boundary should tell the reader whether a function is expected to be pure or stateful.

## Suggested Initial Targets

1. Kamikazee mission step processing
2. Fighter movement / attack step processing
3. Major invasion assignment and launch planning
4. Round-start maintenance operations that currently mix selection and mutation

## Guardrails

- Keep the round integrity checker while this refactor is underway.
- Prefer introducing explicit result maps before larger namespace splits.
- Do not convert everything at once; refactor one behavior slice end to end.
- Add pure tests for the extracted decision layer before thinning the mutating shell.

## Success Criteria

- Bugs caused by stale assumptions become harder to express in code.
- More AI behavior can be tested with plain immutable maps.
- Mutating functions become shorter and easier to audit.
- Return values from movement/combat code are explicit enough that callers do not infer state from `nil`.
- The architecture makes it obvious which code decides, and which code mutates.
