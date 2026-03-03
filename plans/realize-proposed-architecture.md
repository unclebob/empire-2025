# Realize Proposed Architecture

## 1. Baseline and freeze
- Run and record baseline:
  - `clj -M:spec`
  - `clj -M:all-tests-fast`
  - `clj -M:check-dependencies dependency-tool.edn --max-distance 1.0`
- Store dependency violations snapshot in a tracked report file.

## 2. Port split completion
- Migrate any remaining call sites to split ports:
  - `empire.application.ports.world-store`
  - `empire.application.ports.runtime-state`
  - `empire.application.ports.movement`
  - `empire.application.ports.persistence`
  - `empire.application.ports.clock`
  - `empire.application.ports.random`
  - `empire.application.ports.acceptance-harness`
- Remove old aggregate namespace usage entirely.
- Add architecture boundary checks to forbid old aggregate imports.

## 3. Acceptance boundary inversion
- Route acceptance execution through `acceptance-harness-port` + concrete harness implementation.
- Remove direct acceptance pipeline dependencies on game-loop/computer/movement internals where feasible.
- Add boundary checks for acceptance isolation.

## 4. State adapter extraction from policy
- Eliminate direct `empire.adapters.state.runtime` usage in policy modules.
- Ensure policy reads/writes runtime state via injected/runtime port context.
- Cover with targeted specs for invasion/transport/army state transitions.

## 5. Domain purification split
- Split toward:
  - `domain-core` (pure)
  - `domain-services` (domain policies)
- Remove application/runtime dependencies from domain namespaces.
- Enforce no `domain-* -> application-use-cases` dependencies.

## 6. Application/use-case split
- Keep use-case orchestration and contract definitions separate.
- Introduce explicit impl namespaces only where true indirection exists.

## 7. Adapter realignment
- Add implementation namespaces for planned ports:
  - `state-port-impl`
  - `persistence-port-impl`
  - others as created
- Maintain dependency direction: concrete -> abstract only.

## 8. Orchestration tightening
- Keep `orchestration` focused on wiring and sequencing.
- Move mutation logic into use-cases/services.

## 9. Dependency checker ratchet
- Burn down violations incrementally with each phase.
- Keep cycles forbidden and tighten gate to zero violations once ready.

## 10. Documentation and governance
- Update architecture docs/README with final component boundaries and dependency rules.
- Keep UML in sync with dependency-tool configuration.

## 11. Execution cadence
- Implement in small slices.
- After each slice: run specs + acceptance + dependency checks.
- Request play-test review at major phase boundaries.
