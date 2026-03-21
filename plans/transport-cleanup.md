# Transport Cleanup

## Goal

Simplify the transport state machine, remove the legacy `:sailing` mission, and tighten mission transitions so transports only move in states that permit movement and only enter `:sail-to-unload` with a valid unload path.

## Why

The current transport code still has two structural problems:

- `:sailing` is no longer a first-class mission and remains only as compatibility clutter.
- Some mission transitions permit invalid transport behavior:
  - transports in `:loading` can still crawl if they do not have a vector manifest
  - transports can enter `:sail-to-unload` without a defensible unload path

Those problems obscure the real state machine and allow impossible or misleading runtime states in debug logs.

## Scope

Clean up the normal transport state machine and transition guarantees.

Do not change:

- invasion missions beyond adapting them to the tightened transition rules
- acceptance scenarios without explicit permission
- high-level target-selection strategy outside the transition guards described here

## Steps

1. Remove runtime compatibility branches for `:sailing`.

- Delete `:sailing` from [src/empire/computer/transport/process_decisions.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/process_decisions.cljc).
- Delete the `:sailing` case from [src/empire/computer/transport/sailing_regular.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/sailing_regular.cljc).
- Remove `:sailing` from any “in transit” predicates such as [src/empire/computer/transport/targeting.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/targeting.cljc).

2. Replace remaining producers and fixtures.

- Search for all places that still stamp or seed `:transport-mission :sailing`.
- Convert empty transport fixtures to `:sail-to-load`.
- Convert loaded transport fixtures to `:sail-to-unload`.
- Keep invasion fixtures on their dedicated invasion missions.

3. Make `:loading` a hold-position mission.

- Remove the `loading-crawl-move` fallback from [src/empire/computer/transport/mission_handlers.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/mission_handlers.cljc).
- Require transports in `:loading` to hold position until one of these happens:
  - they become full
  - their manifest is exhausted
  - their timeout policy forces a transition
- If a transport reaches `:loading` without a valid manifest, it must re-enter `:sail-to-load` or `:hold-sail-to-load`; it must not crawl while marked `:loading`.

4. Tighten every transition to `:sail-to-unload`.

- Audit all transitions that set `:transport-mission :sail-to-unload`, especially in [src/empire/computer/transport.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport.cljc) and invasion-related handlers.
- Require each such transition to create a non-empty unload path.
- The path must lead toward an unclaimed or unexplored target, not just any sea movement.
- If no valid unload path exists, the transport must not enter `:sail-to-unload`; it should fall back to a safe alternative state such as `:hold-sail-to-load`, `:sail-to-load`, or a dedicated retry path depending on context.

5. Preserve acceptance behavior.

- Do not edit existing acceptance `.txt` files without permission.
- Run the full acceptance pipeline after the code/spec cleanup.
- If any generated acceptance spec fails because it relied on implicit compatibility behavior, change code to preserve the accepted behavior under the cleaned-up mission names.

6. Verify the state machine shape.

- Check [src/empire/computer/transport/process_decisions.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/process_decisions.cljc) and [src/empire/computer/transport.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport.cljc) for dead branches left behind by the cleanup.
- Confirm the normal mission set is:
  - `:leave-city`
  - `:sail-to-load`
  - `:hold-sail-to-load`
  - `:loading`
  - `:sail-to-unload`
  - `:unloading`
  - invasion-specific missions
- Confirm the state invariants:
  - `:loading` transports do not move
  - `:sail-to-unload` transports always have a non-empty unload path to an unclaimed or unexplored target

## Verification

Run in this order:

1. `clojure -P -M:spec-structure-check`
2. focused transport specs
3. `clj -M:spec`
4. `clj -M:parse-tests`
5. `clj -M:generate-specs`
6. `clj -M:spec generated-acceptance-specs/`
7. `clj -M:all-tests`

## Done When

- No runtime code branches on `:sailing`
- No production code assigns `:transport-mission :sailing`
- `:loading` transports hold position rather than crawling
- Every transition into `:sail-to-unload` proves a non-empty unload path to an unclaimed or unexplored target
- Transport specs and acceptance pipeline are green
- `clj -M:all-tests` passes
