## Runaway Round Skip Diagnosis

- Evidence from `.worktrees/play-test/empire-units2026-03-30-115311.log` shows player-item decisions in round `296`, then no `:player-item-decision` entries at all for rounds `297` through `315`, and then the next player stop at round `316`.
- That means the skipped stretch was not caused by player items being processed and misclassified one by one. The player phase appears to have been skipped entirely during those rounds.
- During the skipped rounds, computer turns continued to execute normally. Coastline-follow patrol boats also continued to move, but normal player armies and fighters did not show player-item processing.
- The current leading hypotheses are:
  - `build-player-items` produced no player items for those rounds, or
  - `current-player-items` suppressed them to `[]`, most likely via handicap logic.

## Added Logging

- Main tree now logs `:round-start-state` entries into the unit log at round start.
- Each entry records:
  - `:handicap-rounds-remaining`
  - `:built-player-items-count`
  - `:current-player-items-count`
  - `:computer-items-count`

## How To Read The Next Run

- If `:built-player-items-count > 0` and `:current-player-items-count = 0`, player items were built and then suppressed.
- If both counts are `0`, no player items were built for that round.
- If `:current-player-items-count > 0` but there are still no following `:player-item-decision` entries, the advance loop is bypassing player processing after round start.

## Related Fix

- A separate stale-ghost bug was fixed in main: when a computer attack killed a player unit, `player-map` could retain the dead unit because combat visibility updates only refreshed the attacker's side. Combat visibility now refreshes both affected owners.
