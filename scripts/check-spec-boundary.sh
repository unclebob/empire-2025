#!/usr/bin/env bash
set -euo pipefail

# Fails when spec files directly manipulate atom vars or add new
# direct dependencies on empire.atoms (outside explicit atom tests).
# Enforced patterns:
#   @atoms/...
#   (reset! atoms/...)
#   (swap! atoms/...)
#   [empire.atoms :as atoms]

violations="$(rg -n '@atoms/|reset!\s+atoms/|swap!\s+atoms/' spec -S || true)"

if [[ -n "${violations}" ]]; then
  echo "Spec boundary violation: direct atom manipulation found in spec files:"
  printf '%s\n' "${violations}"
  exit 1
fi

allowed_atoms_requires='^spec/empire/atoms_spec\.clj$'

atoms_requires="$(rg -n '^\s*\[empire\.atoms\s+:as\s+atoms\]' spec -S || true)"
if [[ -n "${atoms_requires}" ]]; then
  blocked_requires="$(printf '%s\n' "${atoms_requires}" \
    | awk -F: '{print $1}' \
    | sort -u \
    | rg -v "${allowed_atoms_requires}" || true)"
  if [[ -n "${blocked_requires}" ]]; then
    echo "Spec boundary violation: direct empire.atoms requires found in spec files:"
    printf '%s\n' "${blocked_requires}"
    exit 1
  fi
fi

echo "Spec boundary check passed"
