# Design: Collapse computer.core Fake Multimethods to Plain Functions

**Date:** 2026-03-05
**Status:** Approved

## Problem

`empire.computer.core` declares 17 multimethods that all dispatch `(fn [& _] :default)` — no actual polymorphism. Each has exactly one `defmethod :default` implementation in `empire.computer.core.impl`. This creates unnecessary ceremony: a contract/impl split, bootstrap wiring, and a boundary guard — all for functions that will never have alternate implementations.

## Approach

**Collapse to plain functions.** Merge all 17 implementations from `core/impl.cljc` into `core.cljc` as regular `defn`/`defn-`. Delete `core/impl.cljc`.

### What changes

| Item | Before | After |
|------|--------|-------|
| `computer/core.cljc` | 49 lines (3 pure helpers + 17 `defmulti`) | ~210 lines (3 pure helpers + 6 private helpers + 17 `defn`) |
| `computer/core/impl.cljc` | 213 lines | **Deleted** |
| `application/bootstrap.cljc` | requires `computer.core.impl` | Remove that require |
| `check-architecture-boundaries.sh` | has `computer_core_impl` guard | Remove that guard |
| 33 consumer files | `(:require [empire.computer.core :as core])` | **No change** — same namespace, same function names |

### What does NOT change

- **dependency-checker.edn** — both files are `:outer-ring`; no rule changes needed
- **Consumer call sites** — all 33 files already call `core/get-neighbors`, `core/distance`, etc. These become plain function calls instead of multimethod dispatches. Same names, same arities.
- **Three-ring architecture** — unaffected. `computer.core` stays in `:outer-ring`.
- **Spec files** — specs that `with-redefs` on `core/` functions continue to work (plain functions are redef-able just like multimethods).

### Migration steps

1. Add `:require` clauses from `impl.cljc` to `core.cljc`
2. Move 6 private helpers (`movement-services`, `country-city-producing-armies?`, `set-city-production!`, `update-cell-visibility!`, `on-same-continent?`, `foreign-territory?`) into `core.cljc` as `defn-`
3. Convert each `defmethod core/X :default` to `defn X` (or `defn- X` for internal-only functions)
4. Delete `core/impl.cljc`
5. Remove `empire.computer.core.impl` require from `bootstrap.cljc`
6. Remove `computer_core_impl` boundary guard from `check-architecture-boundaries.sh`
7. Run `clj -M:spec` and `scripts/check-architecture-boundaries.sh`

### Risk

Low. This is a mechanical transformation. Every consumer already references `empire.computer.core` — the public API is unchanged. The only risk is a typo during the merge, caught immediately by tests.
