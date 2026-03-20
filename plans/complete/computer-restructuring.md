# Computer Namespace Restructuring Plan

## Goal

Restructure `src/empire/computer/` so the namespace layout communicates architectural intent:

- top-level namespaces are narrow public entrypoints or clearly shared infrastructure
- unit-specific internals live under their unit domain
- decision helpers live beside the behavior they support
- orchestration, decisions, and mutation helpers become easier to locate by responsibility

This is a structure refactor plan, not a module-size plan.

## Current Structural Problem

The current `computer` tree mixes three different kinds of namespaces at the same top level:

- public entrypoints used by the game loop
- shared AI infrastructure and adapters
- fighter, ship, and transport internals

That makes the package feel both wide and deep even where responsibilities are already reasonably cohesive.

Examples:

- Public orchestration and entrypoints:
  - `empire.computer.coordinator`
  - `empire.computer.production`
  - `empire.computer.threat-response`
  - `empire.computer.land-objectives`
- Shared helpers:
  - `empire.computer.core`
  - `empire.computer.movement`
  - `empire.computer.oscillation`
  - `empire.computer.stamping`
- Unit internals still flattened at root:
  - `empire.computer.fighter-*`
  - `empire.computer.ship-*`
  - `empire.computer.transport-*`

`army/`, `early_game/`, `production/`, and `threat_response/` already show the right direction. The refactor should finish that move consistently across the whole package.

## Design Principles

### 1. Keep the top level narrow

The top-level `empire.computer.*` surface should contain only:

- entrypoint facades
- a small number of cross-cutting shared modules
- established high-level domains such as `army`, `production`, `early-game`, and `threat-response`

If a namespace exists only to support one domain, it should live under that domain.

### 2. Group by responsibility, not suffix

A reader looking for fighter behavior should find all fighter code under one subtree.

Do not optimize for patterns like:

- `foo`
- `foo-decisions`
- `foo-process-decisions`

when those files belong to one domain and could sit together under that domain.

### 3. Separate API from internals

Public game-loop entrypoints should remain stable and thin.

Internal namespace motion should happen behind those facades first so callers outside `src/empire/computer/` do not need broad churn.

### 4. Make shared infrastructure explicit

Helpers like grid math, visible-world queries, oscillation tracking, pathfinding adapters, and action-resolution shims should read as infrastructure, not as unit behavior.

### 5. Prefer staged movement over semantic rewrites

The first restructuring passes should be mostly file motion and require updates.
Do not mix a large directory move with behavior changes unless required for safety.

## External Entry Points To Preserve

Current non-computer callers appear to depend primarily on:

- `empire.computer.coordinator`
- `empire.computer.production`
- `empire.computer.threat-response`
- `empire.computer.land-objectives`
- `empire.computer.army`

These should remain stable facades until the internal moves settle.

## Proposed Target Shape

This is the intended structure, not necessarily the exact first-pass move order:

```text
src/empire/computer/
  army.cljc
  coordinator.cljc
  land_objectives.cljc
  production.cljc
  threat_response.cljc

  shared/
    grid.cljc
    visible_world.cljc
    action_resolution.cljc
    movement.cljc
    oscillation.cljc
    stamping.cljc
    threat.cljc

  army/
    assignment.cljc
    assignment_decisions.cljc
    coastal.cljc
    coastal_invasion.cljc
    coastal_invasion_decisions.cljc
    coastal_positioning.cljc
    combat.cljc
    exploration.cljc
    movement.cljc
    transport.cljc

  fighter/
    core.cljc
    decisions.cljc
    process_decisions.cljc
    movement.cljc
    movement_impl.cljc
    movement_decisions.cljc
    action_resolution.cljc
    flight_plan.cljc
    flight_decisions.cljc
    exploration.cljc

  ship/
    core.cljc
    patrol.cljc
    patrol_decisions.cljc
    escort.cljc
    carrier.cljc
    carrier_group.cljc
    lake_naval.cljc

  transport/
    core.cljc
    decisions.cljc
    process_decisions.cljc
    mission_handlers.cljc
    mission_handler_decisions.cljc
    loading.cljc
    unloading.cljc
    targeting.cljc
    targeting_decisions.cljc
    sailing.cljc
    sailing_path.cljc
    sailing_support.cljc
    sailing_regular.cljc
    sailing_invasion.cljc
    sailing_decisions.cljc

  production/
    decisions.cljc
    stats.cljc
    selection_decisions.cljc

  threat_response/
    core.cljc
    decisions.cljc
    port.cljc
    processing.cljc
    processing_decisions.cljc
    processing_fighter.cljc
    processing_ship.cljc
    country_defense.cljc
    invasion_decision.cljc
    invasion_state.cljc
    major_invasion.cljc
    major_invasion_manager.cljc
    major_invasion_manager_decisions.cljc
    major_invasion_assignment.cljc
    major_invasion_assignment_decisions.cljc
    kamikazee.cljc
    kamikazee_routing.cljc
    kamikazee_targets.cljc
    kamikazee_target_decisions.cljc
    kamikazee_mission.cljc
    kamikazee_mission_decisions.cljc
    kamikazee_launch_decisions.cljc
    probe.cljc

  early_game/
    role_assignment.cljc
    role_policy.cljc
    role_policy_large.cljc
    role_policy_minimal.cljc
    role_policy_one_coast.cljc
    role_policy_two_coast.cljc
    roles.cljc
    strategy.cljc
    theater.cljc
```

## Domain-Specific Notes

### Shared / infrastructure

Candidates to leave top-level only temporarily, then move under `shared/`:

- `core.cljc`
- `movement.cljc`
- `oscillation.cljc`
- `stamping.cljc`
- `computer_action_resolution.cljc`
- `army_action_resolution.cljc`
- `threat.cljc`

Important note:

- `core.cljc` is currently overloaded.
- It mixes grid math, visible-world queries, movement helpers, boarding helpers, and action-resolution re-exports.
- It should eventually split into smaller shared namespaces instead of remaining a grab bag.

### Fighter

The fighter code already behaves like a subdomain but is physically flat.

Move together:

- `fighter.cljc`
- `fighter_decisions.cljc`
- `fighter_process_decisions.cljc`
- `fighter_movement.cljc`
- `fighter_movement_impl.cljc`
- `fighter_movement_decisions.cljc`
- `fighter_action_resolution.cljc`
- `fighter_flight_plan.cljc`
- `fighter_flight_decisions.cljc`
- `fighter_exploration.cljc`

Preferred end state:

- `empire.computer.fighter` remains the entry facade
- internals move under `empire.computer.fighter.*`

### Ship

The ship code is split conceptually but not structurally.

Move together:

- `ship.cljc`
- `ship_core.cljc`
- `ship_patrol.cljc`
- `ship_patrol_decisions.cljc`
- `ship_escort.cljc`
- `ship_carrier.cljc`
- `ship_carrier_group.cljc`
- `lake_naval.cljc`

Preferred end state:

- `empire.computer.ship` remains the public entry facade
- ship internals move under `empire.computer.ship.*`

### Transport

Transport has the clearest regrouping opportunity.
It is already a coherent cluster, but the whole cluster sits at the root.

Move together:

- `transport.cljc`
- `transport_core.cljc`
- `transport_decisions.cljc`
- `transport_process_decisions.cljc`
- `transport_mission_handlers.cljc`
- `transport_mission_handler_decisions.cljc`
- `transport_loading.cljc`
- `transport_unloading.cljc`
- `transport_targeting.cljc`
- `transport_targeting_decisions.cljc`
- `transport_sailing.cljc`
- `transport_sailing_path.cljc`
- `transport_sailing_support.cljc`
- `transport_sailing_regular.cljc`
- `transport_sailing_invasion.cljc`
- `transport_sailing_decisions.cljc`

Preferred end state:

- `empire.computer.transport` remains the public entry facade
- internals move under `empire.computer.transport.*`

### Threat response

This domain already has a subtree, but it is incomplete.

Pull into the subtree:

- `threat_response_decisions.cljc`
- `threat_response_port.cljc`

Likely end state:

- `empire.computer.threat-response` becomes a facade
- orchestration internals move under `empire.computer.threat-response.*`
- if the existing `threat_response.cljc` remains large, rename it internally to a `core` or `coordinator` namespace while keeping the facade stable

### Production

Production is already mostly grouped. The one structural cleanup is to move:

- `production_selection_decisions.cljc`

into:

- `production/selection_decisions.cljc`

Then keep `empire.computer.production` as the stable facade.

## Concrete Refactor Rules

1. A top-level namespace should not exist solely as an internal helper for one subdomain.
2. `*-decisions` namespaces should usually live under the domain they support.
3. Facades should be thin:
   - require subdomain modules
   - expose stable entrypoints
   - avoid carrying domain logic that belongs deeper in the tree
4. Shared helpers should not quietly accumulate domain-specific behavior.
5. Moves should preserve behavior first and improve names second.

## Recommended Move Order

### Phase 1: Preserve public entrypoints, move internals behind them

Safest first step:

1. regroup transport internals under `transport/`
2. regroup fighter internals under `fighter/`
3. regroup ship internals under `ship/`

Rationale:

- these domains already have clear internal seams
- each has a single obvious top-level facade
- external callers can keep requiring the same public namespace

### Phase 2: Finish incomplete subtrees

4. move `production_selection_decisions.cljc` under `production/`
5. move `threat_response_decisions.cljc` and `threat_response_port.cljc` under `threat_response/`

### Phase 3: Clean the shared layer

6. carve `core.cljc` into smaller shared namespaces
7. move `movement.cljc`, `oscillation.cljc`, `stamping.cljc`, `threat.cljc`, and action-resolution shims into `shared/`

This phase should happen only after domain-local moves are stable so it is easier to see what is truly shared.

### Phase 4: Reassess facade necessity

After the moves:

- review whether every facade is still necessary
- keep only the facades that protect external callers or improve readability
- remove compatibility wrappers that are no longer buying anything

## Change Safety Strategy

For each phase:

1. Move namespaces in one coherent slice.
2. Update requires only for that slice.
3. Keep public entrypoints stable where possible.
4. Run tests after each slice before proceeding.
5. Avoid mixing structural moves with behavioral fixes.

If a namespace move reveals an awkward dependency cycle, stop and split the shared helper instead of forcing cross-domain internals to depend on one another.

## Likely Pressure Points

### `core.cljc`

This is the biggest structural ambiguity in the package.

It currently serves as:

- grid math helper
- visible-world query helper
- transport boarding helper
- action-resolution facade

That makes it harder to tell which dependencies are foundational and which are accidental.

### `threat_response.cljc`

This is a large orchestration namespace with a real subtree underneath it.

Expected outcome:

- keep `empire.computer.threat-response` as the public facade
- move orchestration internals into `threat_response/core.cljc` or `threat_response/coordinator.cljc`
- let the facade delegate

### `army.cljc`

Army is already grouped better than fighter, ship, or transport.

Avoid unnecessary churn here.
Only restructure army further if needed for consistency or if a better shared-layer split makes it obvious.

## Success Criteria

- The top level of `src/empire/computer/` becomes small and readable.
- A reader can find all fighter internals under fighter, ship internals under ship, and transport internals under transport.
- Decision namespaces sit next to the domains they support.
- Shared infrastructure is explicit and no longer masquerades as domain behavior.
- External callers outside `src/empire/computer/` require only a small, stable public surface.

## First Practical Slice

If this plan is executed incrementally, start with transport.

Why transport first:

- the cluster is already cohesive
- the naming is consistent
- the target subtree is obvious
- the facade pattern is already in place

Suggested first slice:

1. move transport helper namespaces under `src/empire/computer/transport/`
2. update internal requires
3. keep `empire.computer.transport` as the stable entrypoint
4. run tests

After transport, repeat the same pattern for fighter and ship.
