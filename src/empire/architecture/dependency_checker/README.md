# Dependency Checker

Analyzes component boundaries and architecture health for this project.

## Run

    clj -M:check-dependencies
    clj -M:check-dependencies dependency-checker.edn
    clj -M:check-dependencies dependency-checker.edn --format edn

Create or recreate starter config:

    clj -M:check-dependencies dependency-checker.edn --init
    clj -M:check-dependencies dependency-checker.edn --force-init

## Config File

Default config path is `dependency-checker.edn`.

### Component Rules

Each rule maps a component name to a namespace regex pattern:

```clojure
{:component-rules
 [{:component :ui
   :match "^empire\\.ui(\\..*)?$"}
  {:component :game
   :match "^empire\\.game(\\..*)?$"}]}
```

Rules are matched in order — the first matching rule wins. A catch-all rule (e.g., `"empire.*"`) can be placed last.

### Allowed Dependencies

The `:allowed-dependencies` map declares which components each component may depend on. Any dependency not listed is a violation.

```clojure
{:allowed-dependencies
 {:ui [:game :player :state :config]
  :game [:player :state :config]
  :player [:state :config]
  :state [:config]
  :config []
  :test-infra :all}}
```

Use `:all` to allow a component to depend on anything (useful for test infrastructure).

Self-dependencies (a component depending on itself) are always allowed.

### Allowed Exceptions

Specific namespace-level edges can be exempted from violation reporting:

```clojure
{:allowed-exceptions [{:from-ns "empire.player.production"
                       :to-ns "empire.computer.production"}]}
```

### Failure Flags

```clojure
{:fail-on-violations true   ; exit 1 if boundary violations found
 :fail-on-cycles true}      ; exit 1 if component cycles found
```

## Metrics

Reports per-component: fan-in, fan-out, instability, abstractness, and distance from the main sequence.

Fan-in/fan-out edges are derived from:
- `ns :require`, `ns :use`, `ns :import`
- Direct `(require ...)`
- Dynamic namespace lookup forms: `requiring-resolve`, `resolve`, `ns-resolve`, `find-ns`, `the-ns`

Dynamic lookup usage is emitted as warnings.

## Abstractness

A symbol counts as abstract only when it represents real indirection (`defprotocol`, `defmulti`). Config-only marking does not count.

## Backward Compatibility

Old configs using `:forbidden-dependencies` (list of `[from to]` pairs) still work. If `:allowed-dependencies` is present and non-empty, it takes precedence.

## Starter Config

`--init` infers components from namespace structure:
- Abstract components from subtrees containing only `defprotocol`/`defmulti` modules.
- Concrete components from implementing subtrees.
- Generates an empty `:allowed-dependencies` map for you to fill in.
