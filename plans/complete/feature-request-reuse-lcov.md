# Feature Request: Optional Coverage Reuse for `clj -M:mutate`

## Problem

`clj -M:mutate` currently regenerates `target/coverage/lcov.info` before mutation runs whenever it considers the coverage file stale.

In practice, this is expensive during refactoring workflows, especially for:

- semantics-preserving splits
- manifest-only updates
- repeated differential mutation runs on small files
- batches of daughter modules created from one parent module

During the kamikazee split work, the expensive step was repeatedly rebuilding LCOV, not running mutants. The rebuild often dominated the total runtime even when the eventual mutation result was a no-op:

- module hash unchanged
- differential surface area `0`
- manifest-violating surface area `0`

## Requested Feature

Add an opt-in mutator flag to reuse existing coverage data instead of regenerating it.

Suggested names:

- `--reuse-coverage`
- `--use-existing-coverage`
- `--skip-coverage-refresh`

Preferred name: `--reuse-coverage`

## Desired Behavior

When `--reuse-coverage` is supplied:

1. If `target/coverage/lcov.info` exists, use it as-is.
2. Do not run `clj -M:cov --lcov`.
3. Print a clear warning that coverage reuse is being forced and may be stale.
4. Continue with normal mutation planning and execution.

## Safety Constraints

This should be opt-in only. It should not replace the current default behavior.

Reasons:

- stale coverage data can misclassify mutation sites as covered or uncovered
- source line movement can invalidate old line-to-coverage mappings
- semantics-changing edits should still force fresh coverage

## Nice-to-Have Safety Checks

If easy to implement, print additional diagnostics when `--reuse-coverage` is used:

- whether `lcov.info` exists
- its last modified time
- whether the target source file is newer than the LCOV file
- whether the module hash changed since the previous mutation run

These checks do not need to block execution. Warnings are sufficient.

## High-Value Use Cases

This flag is especially useful for:

- `--update-manifest`
- semantics-preserving module splits
- rerunning differential mutation after only manifest edits
- iterative mutation work where coverage has already been freshly generated once in the same session

## Manifest Update Note

`--update-manifest` should not run coverage at all.

Reason:

- manifest updates rewrite embedded manifest metadata from current source forms
- they do not execute mutations
- they do not classify mutation sites as covered or uncovered

So even without `--reuse-coverage`, manifest update paths should bypass coverage refresh entirely.

## Non-Goal

This feature should not silently assume LCOV is valid. The default should remain conservative.

## Example

```bash
clj -M:mutate src/empire/computer/threat_response/kamikazee_routing.cljc --reuse-coverage --max-workers 3
```

Expected console note:

```text
Reusing existing coverage data from target/coverage/lcov.info.
Warning: coverage may be stale; covered/uncovered site classification may be inaccurate.
```

## Batch Workflow Recommendation

If you have a batch of mutation runs to do, let the first mutation run refresh coverage and then use `--reuse-coverage` for the rest of the batch.

Example:

```bash
clj -M:mutate src/empire/computer/threat_response/kamikazee_routing.cljc --max-workers 3
clj -M:mutate src/empire/computer/threat_response/kamikazee_targets.cljc --reuse-coverage --max-workers 3
clj -M:mutate src/empire/computer/threat_response/kamikazee_mission.cljc --reuse-coverage --max-workers 3
```

## Expected Benefit

- faster mutation feedback during refactors
- less repeated full-suite coverage work
- better ergonomics for semantics-preserving structural changes
- lower friction when updating manifests for split daughter modules
