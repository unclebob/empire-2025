# Architecture Baseline (2026-03-03)

## Current status
- `clj -M:spec`: **3393 examples, 0 failures**
- `clj -M:all-tests-fast`: **pass** (unit specs + boundary check + generated acceptance specs)
- `clj -M:check-dependencies dependency-tool.edn --max-distance 1.0`: **0 violations, 0 cycles**

## Dependency status highlights
- Legacy aggregate port namespace (`empire.application.ports`) is removed and guarded by architecture boundary checks.
- Acceptance harness is classified as implementation (`:acceptance-harness-impl`) behind `:acceptance-harness-port`.
- Direct `computer-policy -> state-adapters` violations are removed via runtime-state/world-store ports.
- Direct `domain-model -> application-use-cases` violations previously observed in `combat`, `units.satellite`, and `containers.ops` are removed.

## Baseline history (same day)
- Earlier baseline snapshot in this session reported:
  - Violations: **29** (later **15**)
  - `all-tests-fast`: failing at architecture boundary step
- Current baseline is now ratcheted to:
  - Violations: **0**
  - `all-tests-fast`: **pass**

## Raw artifacts
- `reports/architecture/baseline-spec.txt`
- `reports/architecture/baseline-all-tests-fast.txt`
- `reports/architecture/baseline-dependencies.txt`
