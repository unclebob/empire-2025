#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

# --- Component-level dependency distance checker ---
clj -M:check-dependencies

# =============================================================================
# Removed namespaces — prevent re-introduction
# =============================================================================

# movement.methods was removed
movement_methods_hits="$(rg -n 'empire\.movement\.methods' src/empire || true)"
if [[ -n "$movement_methods_hits" ]]; then
  echo "Architecture boundary violation: movement.methods was removed; no references should exist:"
  printf '%s\n' "$movement_methods_hits"
  exit 1
fi

# movement.service and movement.services were removed
movement_service_hits="$(rg -n 'empire\.movement\.services?\b' src/empire || true)"
if [[ -n "$movement_service_hits" ]]; then
  echo "Architecture boundary violation: movement.service(s) was removed; require target modules directly:"
  printf '%s\n' "$movement_service_hits"
  exit 1
fi

# movement.context and pathfinding-bfs.context were removed; use application.state-access
context_hits="$(rg -n 'empire\.movement\.context\b|empire\.movement\.pathfinding.bfs\.context' src/empire || true)"
if [[ -n "$context_hits" ]]; then
  echo "Architecture boundary violation: movement.context and pathfinding-bfs.context were removed; use application.state-access:"
  printf '%s\n' "$context_hits"
  exit 1
fi

# units.impl was removed; dispatcher is now a data map in units/dispatcher.cljc
units_impl_hits="$(rg -n 'empire\.units\.impl' src/empire || true)"
if [[ -n "$units_impl_hits" ]]; then
  echo "Architecture boundary violation: units.impl was removed; all dispatcher logic is in units/dispatcher.cljc:"
  printf '%s\n' "$units_impl_hits"
  exit 1
fi

# Removed facade namespaces — require target modules directly
facade_hits="$(rg -n 'empire\.movement\.(player.services|waypoint.services|bootstrap)\b' src/empire || true)"
if [[ -n "$facade_hits" ]]; then
  echo "Architecture boundary violation: movement facade namespaces were removed; require target modules directly:"
  printf '%s\n' "$facade_hits"
  exit 1
fi

# empire.debug facade was removed; require debug.logging or debug.dump directly
debug_facade_hits="$(rg -nP 'empire\.debug(?!\.)' src/empire || true)"
if [[ -n "$debug_facade_hits" ]]; then
  echo "Architecture boundary violation: empire.debug facade was removed; require empire.debug.logging or empire.debug.dump:"
  printf '%s\n' "$debug_facade_hits"
  exit 1
fi

# empire.computer top-level was removed; use empire.computer.coordinator
computer_facade_hits="$(rg -nP 'empire\.computer(?!\.)' src/empire || true)"
if [[ -n "$computer_facade_hits" ]]; then
  echo "Architecture boundary violation: empire.computer top-level was removed; use empire.computer.coordinator:"
  printf '%s\n' "$computer_facade_hits"
  exit 1
fi

# empire.game-loop moved to empire.game-loop.core
game_loop_top_hits="$(rg -nP '(?<!\.)empire\.game-loop(?!\.)' src/empire || true)"
if [[ -n "$game_loop_top_hits" ]]; then
  echo "Architecture boundary violation: empire.game-loop moved to empire.game-loop.core:"
  printf '%s\n' "$game_loop_top_hits"
  exit 1
fi

# empire.save-load moved to empire.application.save-load
save_load_top_hits="$(rg -nP 'empire\.save-load(?!\.)' src/empire || true)"
if [[ -n "$save_load_top_hits" ]]; then
  echo "Architecture boundary violation: empire.save-load moved to empire.application.save-load:"
  printf '%s\n' "$save_load_top_hits"
  exit 1
fi

# application.coords was removed; function moved to ui/util/core
app_coords_hits="$(rg -n 'empire\.application\.coords' src/empire || true)"
if [[ -n "$app_coords_hits" ]]; then
  echo "Architecture boundary violation: application.coords was removed; use ui.util.core/screen->cell:"
  printf '%s\n' "$app_coords_hits"
  exit 1
fi

# legacy aggregate empire.application.ports namespace is forbidden
legacy_app_ports_hits="$(rg -nP 'empire\.application\.ports(?!\.)' src spec generated-acceptance-specs 2>/dev/null || true)"
if [[ -n "$legacy_app_ports_hits" ]]; then
  echo "Architecture boundary violation: legacy aggregate namespace empire.application.ports is forbidden; use split port namespaces."
  printf '%s\n' "$legacy_app_ports_hits"
  exit 1
fi

# =============================================================================
# Isolation boundaries — restrict where certain APIs/patterns may appear
# =============================================================================

# No mutation primitives (swap!/reset!) in application/domain layers
inner_mutation_hits="$(rg -n '\bswap!\b|\breset!\b|\bcompare-and-set!\b' src/empire/application src/empire/domain || true)"
if [[ -n "$inner_mutation_hits" ]]; then
  echo "Architecture boundary violation: mutation primitives in application/domain:"
  printf '%s\n' "$inner_mutation_hits"
  exit 1
fi

# Movement API only referenced by movement adapter/api
movement_api_hits="$(rg -n 'empire\.movement\.api' src/empire || true)"
movement_api_consumer_violations="$(printf '%s\n' "$movement_api_hits" | rg -v '^src/empire/movement/api\.cljc:|^src/empire/movement/adapter\.cljc:' || true)"
if [[ -n "$movement_api_consumer_violations" ]]; then
  echo "Architecture boundary violation: movement api must only be referenced by movement adapter/api:"
  printf '%s\n' "$movement_api_consumer_violations"
  exit 1
fi

# state-ctx delay only in state_access.cljc (and test_utils.cljc)
state_ctx_delay_hits="$(rg -n 'delay.*default-state-ctx' src/empire --glob '!**/state_access.cljc' --glob '!**/test_utils.cljc' || true)"
if [[ -n "$state_ctx_delay_hits" ]]; then
  echo "Architecture boundary violation: state-ctx delay should only exist in application/state_access.cljc:"
  printf '%s\n' "$state_ctx_delay_hits"
  exit 1
fi

# Quil only referenced from within ui/quil/
quil_outside_hits="$(rg -n 'empire\.ui\.quil' src/empire --glob '!src/empire/ui/quil/*' || true)"
if [[ -n "$quil_outside_hits" ]]; then
  echo "Architecture boundary violation: empire.ui.quil must only be referenced from within ui/quil/:"
  printf '%s\n' "$quil_outside_hits"
  exit 1
fi

# =============================================================================
# Layer enforcement — these overlap with clj -M:check-dependencies but provide
# fast, file-level guards that catch violations before the full checker runs.
# =============================================================================

# Domain services must not depend on use-cases
ds_to_uc_hits="$(rg -n 'empire\.(player|computer|game.loop)' src/empire/movement src/empire/combat.cljc src/empire/containers src/empire/debug || true)"
if [[ -n "$ds_to_uc_hits" ]]; then
  echo "Architecture boundary violation: domain-services must not depend on use-cases (player/computer/game-loop):"
  printf '%s\n' "$ds_to_uc_hits"
  exit 1
fi

# Application must not depend on use-cases (excluding composition roots)
app_to_uc_hits="$(rg -n 'empire\.(player|computer|game.loop)' src/empire/application src/empire/adapters src/empire/atoms.cljc src/empire/atoms_runtime.cljc --glob '!**/bootstrap.cljc' --glob '!**/acceptance_engine.cljc' || true)"
if [[ -n "$app_to_uc_hits" ]]; then
  echo "Architecture boundary violation: application must not depend on use-cases (player/computer/game-loop):"
  printf '%s\n' "$app_to_uc_hits"
  exit 1
fi

# Application must not depend on domain-services (excluding composition roots)
app_to_ds_hits="$(rg -n 'empire\.(movement|combat|containers|debug)' src/empire/application src/empire/adapters src/empire/atoms.cljc src/empire/atoms_runtime.cljc --glob '!**/bootstrap.cljc' --glob '!**/acceptance_engine.cljc' || true)"
if [[ -n "$app_to_ds_hits" ]]; then
  echo "Architecture boundary violation: application must not depend on domain-services (movement/combat/containers/debug):"
  printf '%s\n' "$app_to_ds_hits"
  exit 1
fi

echo "Architecture boundary check passed"
