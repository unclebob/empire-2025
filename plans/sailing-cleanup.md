# Transport `:sailing` Cleanup

## Goal

Remove the legacy transport mission `:sailing` and make `:sail-to-load` and `:sail-to-unload` the only normal sailing missions.

## Why

` :sailing` is no longer a first-class mission. Current code only treats it as a compatibility alias:

- empty transport -> `:sail-to-load`
- loaded transport -> `:sail-to-unload`

Leaving it in place obscures the real state machine and forces compatibility branches through transport processing.

## Scope

Only remove the legacy mission and its compatibility handling.

Do not change:

- invasion missions
- transport target selection semantics
- loading or unloading policies
- acceptance scenarios unrelated to mission names

## Steps

1. Remove runtime compatibility branches.

- Delete `:sailing` from [src/empire/computer/transport/process_decisions.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/process_decisions.cljc).
- Delete the `:sailing` case from [src/empire/computer/transport/sailing_regular.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/sailing_regular.cljc).
- Remove `:sailing` from any “in transit” predicates such as [src/empire/computer/transport/targeting.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/targeting.cljc).

2. Replace remaining producers and fixtures.

- Search for all places that still stamp or seed `:transport-mission :sailing`.
- Convert empty transport fixtures to `:sail-to-load`.
- Convert loaded transport fixtures to `:sail-to-unload`.
- Keep invasion fixtures on their dedicated invasion missions.

3. Preserve acceptance behavior.

- Do not edit existing acceptance `.txt` files without permission.
- Run the full acceptance pipeline after the code/spec cleanup.
- If any generated acceptance spec fails because it relied on implicit compatibility behavior, change code to preserve the accepted behavior under the new mission names.

4. Verify the state machine shape.

- Check [src/empire/computer/transport/process_decisions.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport/process_decisions.cljc) and [src/empire/computer/transport.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport.cljc) for any dead branches left behind by the removal.
- Confirm the normal mission set is:
  - `:leave-city`
  - `:sail-to-load`
  - `:hold-sail-to-load`
  - `:loading`
  - `:sail-to-unload`
  - `:unloading`
  - invasion-specific missions

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
- Transport specs and acceptance pipeline are green
- `clj -M:all-tests` passes
