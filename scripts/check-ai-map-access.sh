#!/usr/bin/env bash
set -euo pipefail

# Reports direct authoritative game-map reads in AI-related code.
# This is an audit guard, not a hard boundary yet.
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

violations="$(
  rg -n \
    -e '\bsa/current-world\b' \
    -e '\bsa/read-state\s+:game-map\b' \
    "${scope[@]}" \
    || true
)"

if [[ -n "${violations}" ]]; then
  echo "AI map access audit warnings:"
  printf '%s\n' "${violations}"
  exit 0
fi

echo "AI map access audit passed"
