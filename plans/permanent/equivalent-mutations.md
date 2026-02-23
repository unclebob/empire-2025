# Equivalent Mutations Reference

Consult this document after running mutation testing to classify survivors before writing tests.

## Auto-Suppressed Patterns

These are automatically suppressed by the mutation tool and will not appear as survivors.

### rand comparison (`< -> <=`, `> -> >=`, etc.)
Pattern: `(< (rand) 0.5)` — `rand` returns a continuous double, so `<` vs `<=` is equivalent.
Suppressed: all comparison mutations where second arg is `(rand)`.

### rand-nth single-element guard
Pattern: `(if (= 1 (count v)) (first v) (rand-nth v))` — the guard prevents `rand-nth` on empty/single collections. All mutations inside this form are equivalent.
Suppressed: `=`, `if`, `if-not`, `0`, `1` mutations inside this pattern.

### rand-nth literal pool
Pattern: `(rand-nth [0 1 -1])` — mutating constants inside the pool just changes which random value is selected, which is equivalent.
Suppressed: `0 -> 1`, `1 -> 0` inside vectors that are args to `rand-nth`.

### subvec trim boundary
Pattern: `(if (> (count v) N) (subvec v 0 N) v)` — off-by-one at the boundary is equivalent when subvec trims to the same practical limit.
Suppressed: `> -> >=` when then-branch contains `subvec`.

## Manually Classified Patterns

These survive mutation testing but are equivalent. Do NOT write tests to kill them.

### Bounds check + nil guard (`+ -> -` in neighbor offsets)
Pattern: `(let [nx (+ tx dx)] (when (>= nx 0) ...))` — changing `+` to `-` produces different but still in-bounds coordinates. If no test places data asymmetrically around the target, both pass.
Classification: Often equivalent in test setups with symmetric neighbors. Kill by placing units at edges.

### when/when-not with nil return
Pattern: `(when condition (do-thing))` — `when` returns nil on false; `when-not` returns nil on true. If the caller ignores the return value, these may be equivalent.
Classification: Equivalent when return value is unused. Kill by asserting the side effect occurs.

### Redundant comparison strictness (`>= -> >` in bounds checks)
Pattern: `(>= nx 0)` vs `(> nx 0)` — if no test exercises the boundary (nx = 0), both pass.
Classification: Kill by testing at exact boundary values.

## Workflow

1. Run `clj -M:mutate src/empire/<module>.cljc`
2. For each survivor, check this reference document
3. If the pattern matches an auto-suppressed or manually-classified equivalent, skip it
4. If the pattern is new and equivalent, add it here
5. Write tests only for killable survivors
