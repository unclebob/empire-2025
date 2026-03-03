#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

clj -M:check-dependencies dependency-tool.edn --max-distance 1.0

inner_mutation_hits="$(rg -n '\bswap!\b|\breset!\b|\bcompare-and-set!\b' src/empire/application src/empire/domain || true)"
if [[ -n "$inner_mutation_hits" ]]; then
  echo "Architecture boundary violation: mutation primitives in application/domain:"
  printf '%s\n' "$inner_mutation_hits"
  exit 1
fi

movement_methods_hits="$(rg -n 'empire\.movement\.methods' src/empire || true)"
movement_methods_violations="$(printf '%s\n' "$movement_methods_hits" | rg -v '^src/empire/movement/methods\.cljc:|^src/empire/movement/bootstrap\.cljc:' || true)"
if [[ -n "$movement_methods_violations" ]]; then
  echo "Architecture boundary violation: movement methods must only be referenced by movement bootstrap:"
  printf '%s\n' "$movement_methods_violations"
  exit 1
fi

movement_service_hits="$(rg -n 'empire\.movement\.service' src/empire || true)"
movement_service_violations="$(printf '%s\n' "$movement_service_hits" | rg -v '^src/empire/movement/service\.cljc:|^src/empire/movement/api\.cljc:|^src/empire/movement/bootstrap\.cljc:' || true)"
if [[ -n "$movement_service_violations" ]]; then
  echo "Architecture boundary violation: movement service must only be referenced by movement api/bootstrap:"
  printf '%s\n' "$movement_service_violations"
  exit 1
fi

movement_api_consumer_violations="$(rg -n 'empire\.movement\.api' \
  src/empire/game_loop/item_processing.cljc \
  src/empire/player/commands.cljc \
  src/empire/player/commands/actions.cljc \
  src/empire/ui/util/input/actions.cljc || true)"
if [[ -n "$movement_api_consumer_violations" ]]; then
  echo "Architecture boundary violation: migrated consumers must use application movement port, not movement api:"
  printf '%s\n' "$movement_api_consumer_violations"
  exit 1
fi

echo "Architecture boundary check passed"
