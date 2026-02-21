# Mutation Testing Tool Design

## Overview

A Clojure-native mutation testing tool that reads source files, walks the form tree with `postwalk`, applies one mutation at a time, runs the targeted spec, and reports which mutants survived.

## Invocation

```bash
clj -M:mutate src/empire/combat.cljc
```

## Architecture

Three phases: Discover, Execute, Report.

### Discovery

1. Read source file with `clojure.tools.reader` (configured for `.cljc` reader conditionals).
2. Parse all top-level forms.
3. `postwalk` each form with an atom counter. At each node, check all mutation rules.
4. Record each match as `{:index N :original <form> :mutant <form> :description "..."}`.

### Execution

For each mutation site:

1. Hold original file content in memory.
2. Apply mutation N via a second postwalk that replaces only at the matching index.
3. Serialize mutated forms with `pr-str`, write to source file.
4. Shell out: `clj -M:spec <corresponding-spec-file>`.
5. Exit code 0 = survived, non-zero = killed.
6. Restore original file in `try/finally`.

### Report

Console summary showing each mutant (killed/survived), ending with a count and list of survivors.

```
=== Mutation Testing: src/empire/combat.cljc ===
Spec: spec/empire/combat_spec.clj
Found 47 mutation sites.

[ 1/47] KILLED  (= owner :player) → (not= owner :player)
[ 3/47] SURVIVED  (+ damage 1) → (- damage 1)

=== Summary ===
45/47 mutants killed (95.7%)
2 mutants survived:
  #3  (+ damage 1) → (- damage 1)
  #31 true → false
```

## Source-to-Spec Mapping

Convention: `src/empire/foo.cljc` → `spec/empire/foo_spec.clj`. Subdirectories follow the same pattern. If no spec exists, report and skip.

## Mutation Rules (Core Set)

| Category | Original | Mutant |
|----------|----------|--------|
| Arithmetic | `+` | `-` |
| Arithmetic | `-` | `+` |
| Arithmetic | `*` | `/` |
| Arithmetic | `inc` | `dec` |
| Arithmetic | `dec` | `inc` |
| Comparison | `>` | `>=` |
| Comparison | `>=` | `>` |
| Comparison | `<` | `<=` |
| Comparison | `<=` | `<` |
| Equality | `=` | `not=` |
| Equality | `not=` | `=` |
| Boolean | `true` | `false` |
| Boolean | `false` | `true` |
| Conditional | `if` | `if-not` |
| Conditional | `if-not` | `if` |
| Conditional | `when` | `when-not` |
| Conditional | `when-not` | `when` |
| Constant | `0` (int) | `1` |
| Constant | `1` (int) | `0` |

Arithmetic/comparison/conditional mutations apply only in function position (first element of a list). Boolean and constant mutations apply anywhere.

## File Structure

```
src/empire/mutation/
  core.cljc      — Entry point, orchestration (discover → execute → report)
  mutations.cljc — Mutation rules table and matching logic
  runner.cljc    — Shell out to spec runner, capture results
```

## deps.edn Alias

```clojure
:mutate {:main-opts ["-m" "empire.mutation.core"]
         :extra-deps {org.clojure/tools.reader {:mvn/version "1.4.2"}}}
```

## Serialization

`pr-str` won't reproduce original formatting. Acceptable because the mutated file only needs to compile and run; the original is restored immediately after each test. Reader conditionals handled with `{:read-cond :allow :features #{:clj}}`.

## Safety

- Original file content held in memory and restored in `try/finally`.
- Tool runs in a worktree; `git checkout` recovers from interruptions.
