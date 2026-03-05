#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

clj -M:check-dependencies

inner_mutation_hits="$(rg -n '\bswap!\b|\breset!\b|\bcompare-and-set!\b' src/empire/application src/empire/domain || true)"
if [[ -n "$inner_mutation_hits" ]]; then
  echo "Architecture boundary violation: mutation primitives in application/domain:"
  printf '%s\n' "$inner_mutation_hits"
  exit 1
fi

movement_methods_hits="$(rg -n 'empire\.movement\.methods' src/empire || true)"
if [[ -n "$movement_methods_hits" ]]; then
  echo "Architecture boundary violation: movement.methods was removed; no references should exist:"
  printf '%s\n' "$movement_methods_hits"
  exit 1
fi

movement_service_hits="$(rg -n 'empire\.movement\.service[^s]' src/empire || true)"
if [[ -n "$movement_service_hits" ]]; then
  echo "Architecture boundary violation: movement.service was removed; no references should exist:"
  printf '%s\n' "$movement_service_hits"
  exit 1
fi

movement_api_hits="$(rg -n 'empire\.movement\.api' src/empire || true)"
movement_api_consumer_violations="$(printf '%s\n' "$movement_api_hits" | rg -v '^src/empire/movement/api\.cljc:|^src/empire/movement/adapter\.cljc:' || true)"
if [[ -n "$movement_api_consumer_violations" ]]; then
  echo "Architecture boundary violation: movement api must only be referenced by movement adapter/api:"
  printf '%s\n' "$movement_api_consumer_violations"
  exit 1
fi

legacy_app_ports_hits="$(rg -nP 'empire\.application\.ports(?!\.)' src spec generated-acceptance-specs || true)"
if [[ -n "$legacy_app_ports_hits" ]]; then
  echo "Architecture boundary violation: legacy aggregate namespace empire.application.ports is forbidden; use split port namespaces."
  printf '%s\n' "$legacy_app_ports_hits"
  exit 1
fi

state_ctx_delay_hits="$(rg -n 'delay.*default-state-ctx' src/empire --glob '!**/state_access.cljc' --glob '!**/test_utils.cljc' || true)"
if [[ -n "$state_ctx_delay_hits" ]]; then
  echo "Architecture boundary violation: state-ctx delay should only exist in application/state_access.cljc:"
  printf '%s\n' "$state_ctx_delay_hits"
  exit 1
fi

units_impl_hits="$(rg -n 'empire\.units\.impl' src/empire || true)"
units_impl_violations="$(printf '%s\n' "$units_impl_hits" | rg -v '^src/empire/units/impl/|^src/empire/application/bootstrap\.cljc:' || true)"
if [[ -n "$units_impl_violations" ]]; then
  echo "Architecture boundary violation: units.impl must only be referenced from itself and application/bootstrap:"
  printf '%s\n' "$units_impl_violations"
  exit 1
fi

quil_outside_hits="$(rg -n 'empire\.ui\.quil' src/empire --glob '!src/empire/ui/quil/*' || true)"
if [[ -n "$quil_outside_hits" ]]; then
  echo "Architecture boundary violation: empire.ui.quil must only be referenced from within ui/quil/:"
  printf '%s\n' "$quil_outside_hits"
  exit 1
fi

# Domain services must not depend on use-cases
ds_to_uc_hits="$(rg -n 'empire\.(player|computer|game.loop)' src/empire/movement src/empire/combat.cljc src/empire/combat src/empire/containers src/empire/debug || true)"
if [[ -n "$ds_to_uc_hits" ]]; then
  echo "Architecture boundary violation: domain-services must not depend on use-cases (player/computer/game-loop):"
  printf '%s\n' "$ds_to_uc_hits"
  exit 1
fi

# Application must not depend on use-cases (excluding composition roots)
app_to_uc_hits="$(rg -n 'empire\.(player|computer|game.loop)' src/empire/application src/empire/adapters src/empire/atoms --glob '!**/bootstrap.cljc' --glob '!**/acceptance_engine.cljc' || true)"
if [[ -n "$app_to_uc_hits" ]]; then
  echo "Architecture boundary violation: application must not depend on use-cases (player/computer/game-loop):"
  printf '%s\n' "$app_to_uc_hits"
  exit 1
fi

# Application must not depend on domain-services (excluding composition roots)
app_to_ds_hits="$(rg -n 'empire\.(movement|combat|containers|debug)' src/empire/application src/empire/adapters src/empire/atoms --glob '!**/bootstrap.cljc' --glob '!**/acceptance_engine.cljc' || true)"
if [[ -n "$app_to_ds_hits" ]]; then
  echo "Architecture boundary violation: application must not depend on domain-services (movement/combat/containers/debug):"
  printf '%s\n' "$app_to_ds_hits"
  exit 1
fi

echo "Architecture boundary check passed"
