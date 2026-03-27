#!/usr/bin/env bash
set -euo pipefail

# Reports direct authoritative game-map reads in AI-related code.
#
# Usage:
#   scripts/check-ai-map-access.sh
#   scripts/check-ai-map-access.sh path/to/dir path/to/file.cljc
#
# It scans for:
#   sa/current-world
#   sa/read-state :game-map
#
# Default scope covers AI code plus shared BFS helpers that influence AI pathing.

if [[ $# -gt 0 ]]; then
  scope=("$@")
else
  scope=(
    "src/empire/computer"
    "src/empire/game_mechanics/movement/pathfinding_bfs"
  )
fi

all_accesses="$(
  rg -n \
    -e '\bsa/current-world\b' \
    -e '\bsa/read-state\s+:game-map\b' \
    "${scope[@]}" \
    || true
)"

# This allowlist is the explicit set of authoritative accesses permitted in AI-related
# code today. It must never be modified without explicit user permission.
readonly ALLOWED_ACCESSES=(
  "src/empire/computer/shared/action_resolution.cljc:131:  (let [from-cell (get-in (sa/current-world) from-pos)"
  "src/empire/computer/shared/action_resolution.cljc:132:        to-cell (get-in (sa/current-world) to-pos)"
  "src/empire/computer/shared/action_resolution.cljc:203:         (sa/current-world))))"
  "src/empire/computer/shared/action_resolution.cljc:216:  (let [army-cell (get-in (sa/current-world) army-pos)"
  "src/empire/computer/shared/action_resolution.cljc:218:        city-cell (get-in (sa/current-world) city-pos)]"
  "src/empire/computer/shared/action_resolution.cljc:237:          (sa/update-state! :player-map assoc-in city-pos (get-in (sa/current-world) city-pos)))"
  "src/empire/computer/shared/action_resolution.cljc:238:        (let [city-country-id (:country-id (get-in (sa/current-world) city-pos))]"
  "src/empire/computer/threat_response/probe.cljc:52:        game-map (sa/read-state :game-map)"
)

unexpected_accesses="${all_accesses}"
for allowed in "${ALLOWED_ACCESSES[@]}"; do
  unexpected_accesses="$(printf '%s\n' "${unexpected_accesses}" | grep -F -x -v "${allowed}" || true)"
done

if [[ -n "${unexpected_accesses//$'\n'/}" ]]; then
  echo "AI map access audit failed:"
  printf '%s\n' "${unexpected_accesses}"
  exit 1
fi

if [[ -n "${all_accesses}" ]]; then
  echo "AI map access audit passed with approved authoritative accesses:"
  printf '%s\n' "${all_accesses}"
  exit 0
fi

echo "AI map access audit passed"
