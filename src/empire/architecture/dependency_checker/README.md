# Dependency Checker

The dependency checker analyzes component boundaries and architecture health for this project.

## Run

    clj -M:check-dependencies dependency-checker.edn
    clj -M:check-dependencies dependency-checker.edn --max-distance 0.35

Create or recreate starter config:

    clj -M:check-dependencies dependency-checker.edn --init
    clj -M:check-dependencies dependency-checker.edn --force-init

## Metrics and Inputs

- Reports component boundaries, cycles, fan-in, fan-out, instability, abstractness, and distance.
- Fan-in/fan-out edges are derived from:
  - `ns :require`, `ns :use`, `ns :import`
  - direct `(require ...)`
  - dynamic namespace lookup forms:
    `requiring-resolve`, `resolve`, `ns-resolve`, `find-ns`, `the-ns`
- Dynamic lookup usage is emitted as warnings in checker output.

## Abstractness Rule

- Never mark abstraction arbitrarily.
- A symbol counts as abstract only when it represents real indirection
  (for example `defprotocol`, `defmulti`, or explicit function-argument injection).
- Do not use config-only pattern marking to inflate abstractness where no indirection exists.

Abstractness in checker metrics is derived from `defprotocol` and `defmulti`.

## Distance Policy

- Default distance threshold is `--max-distance 0`.
- The checker fails when any non-exempt component has distance above the threshold.

Utility component exemption:

- Config key: `:utility-components`
- Type: vector of component keywords
- Purpose: mark components that are intentionally utility-like (`A=0`, `I=0`) so they do not count against `--max-distance`.
- Example:

```clojure
{:utility-components [:architecture-tools]}
```

## Config File

Default config path is `dependency-checker.edn`.

Starter config inference:

- Infers abstract components from the highest namespace subtrees that contain only abstract modules.
- Infers concrete components from implementing namespace subtrees that remain dependency-closed within those abstract roots.
