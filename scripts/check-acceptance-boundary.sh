#!/usr/bin/env bash
set -euo pipefail

# Fails if a change set touches locked acceptance scenarios or parser sources.
# Usage:
#   scripts/check-acceptance-boundary.sh            # checks local staged + unstaged changes
#   scripts/check-acceptance-boundary.sh [BASE_REF] # checks BASE_REF...HEAD range
# Examples:
#   scripts/check-acceptance-boundary.sh origin/master
#   scripts/check-acceptance-boundary.sh main

if [[ $# -gt 0 ]]; then
  base_ref="${1}"
  if git rev-parse --verify "$base_ref" >/dev/null 2>&1; then
    range="$base_ref...HEAD"
  elif git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
    range="HEAD~1...HEAD"
  else
    range="HEAD"
  fi
  changed="$(git diff --name-only "$range" || true)"
  range_label="$range"
else
  changed="$( (git diff --name-only; git diff --name-only --cached) | sort -u )"
  range_label="working-tree+index"
fi

blocked="$(printf '%s\n' "$changed" | rg '^(acceptanceTests/|src/empire/acceptance/parser/)' || true)"

if [[ -n "${blocked}" ]]; then
  echo "Acceptance boundary violation: locked files changed:"
  printf '%s\n' "$blocked"
  exit 1
fi

echo "Acceptance boundary check passed for $range_label"
