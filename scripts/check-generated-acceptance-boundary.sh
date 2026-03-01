#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
spec_dir="$root_dir/generated-acceptance-specs/acceptance"

if [[ ! -d "$spec_dir" ]]; then
  echo "Generated acceptance spec directory not found: $spec_dir"
  echo "Run: clj -M:generate-specs"
  exit 1
fi

forbidden_pattern='empire\.atoms|empire\.test-utils|empire\.ui\.util\.input\.dispatch|quil\.core|empire\.ui\.quil\.input|empire\.computer\.production|empire\.computer\.transport|empire\.computer\.fighter|empire\.computer\.ship|empire\.movement\.visibility|empire\.game-loop|empire\.game-loop\.item-processing'

forbidden_hits="$(rg -n "$forbidden_pattern" "$spec_dir" || true)"
if [[ -n "$forbidden_hits" ]]; then
  echo "Generated acceptance boundary violation: forbidden direct dependencies found:"
  printf '%s\n' "$forbidden_hits"
  exit 1
fi

missing_harness=()
while IFS= read -r file; do
  if ! rg -q '\[empire\.acceptance\.harness :as h\b' "$file"; then
    missing_harness+=("$file")
  fi
done < <(find "$spec_dir" -type f -name '*.clj' | sort)

if [[ ${#missing_harness[@]} -gt 0 ]]; then
  echo "Generated acceptance boundary violation: missing harness require in:"
  printf '%s\n' "${missing_harness[@]}"
  exit 1
fi

echo "Generated acceptance boundary check passed for $spec_dir"
