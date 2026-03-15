# State Component Design: `empire.state.api`

## Goal
Replace `empire.application.state-access` (ctx -> ports -> adapters -> atoms) with
`empire.state.api` that wraps atoms directly. No protocols, no ports, no indirection.

## New namespace: `empire.state.api`

Core functions (direct atom access):
- `current-world` -> `@atoms/game-map`
- `update-world! [f & args]` -> `(apply swap! atoms/game-map f args)`
- `read-state [k]` -> `@(key->atom k)`
- `write-state! [k v]` -> `(reset! (key->atom k) v)`
- `update-state! [k f & args]` -> `(apply swap! (key->atom k) f args)`
- `merge-continents! [a b]` -> direct swap on continent-groups atom
- `world-atom` -> `atoms/game-map`

Transition functions (kept until ports/adapters deleted in step C):
- `state-ctx` -> delegates to `app-runtime/default-state-ctx`
- `context-fn [k]` -> `(get (state-ctx) k)`

## Changes
- Create `src/empire/state/api.cljc`
- Rename ~80 requires: `empire.application.state-access` -> `empire.state.api`
- All call sites unchanged (same `:as sa` alias, same function names)
- Run all tests
