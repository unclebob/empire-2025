# QA: keys

Headed end-to-end checks for the `?` keystroke help window. Operate only through the game window and documented command-line flags. Do not call project APIs, inspect atoms, or run headless mode.

## Launch

From the project root, start the real UI:

```text
clj -M:run --handicap=0 --seed=1
```

`--handicap=0` and `--seed=1` are player-facing launch flags. Wait until the Empire window titled `Empire: Global Conquest` is visible and the map is drawn. Focus that window before every keystroke.

Do not use `--headless`. Close the window when the suite is finished.

## keys 1 — Open help

1. Confirm no help window is showing over the map.
2. Press `?`.
3. A help window appears over the map.
4. The window includes a clickable button labeled `Dismiss`.

Fail if `?` does nothing, if the list is only a status-line message, or if there is no `Dismiss` button.

## keys 2 — Listed keystrokes and explanations

With the help window open, read every row in the window. Scroll if the list is longer than the window. Every keystroke below must appear, and its explanation must match the text in the Explanation column.

| Keystroke | Explanation |
|-----------|-------------|
| q | Move one square northwest |
| w | Move one square north |
| e | Move one square northeast |
| a | Move one square west. At a city needing production: produce an army |
| d | Move one square east. At a city needing production: produce a destroyer |
| z | Move one square southwest. At a city needing production: produce a satellite |
| x | Move one square south. At a city needing production: cancel production |
| c | Move one square southeast. At a city needing production: produce a carrier |
| Q | Set a destination at the northwest map edge. On a city under the mouse: set marching orders to that edge |
| W | Set a destination at the north map edge. On a city under the mouse: set marching orders to that edge |
| E | Set a destination at the northeast map edge. On a city under the mouse: set marching orders to that edge |
| A | Set a destination at the west map edge. On a city under the mouse: set marching orders to that edge |
| D | Set a destination at the east map edge. On a city under the mouse: set marching orders to that edge |
| Z | Set a destination at the southwest map edge. On a city under the mouse: set marching orders to that edge |
| X | Set a destination at the south map edge. On a city under the mouse: set marching orders to that edge |
| C | Set a destination at the southeast map edge. On a city under the mouse: set marching orders to that edge |
| Space | Skip this turn without moving. On a fighter: spend fuel to wait. At a city: leave production unchanged. While paused: advance one round |
| s | Sentry: sleep until an enemy appears. On airport fighters: dismiss the remaining queue. At a city needing production: produce a submarine |
| l | Explore or look around. Armies auto-explore unexplored land. Transports and patrol boats near a coast follow the coastline. On a city under the mouse: set lookaround marching orders |
| u | Unload or wake cargo. Wakes armies on a transport, fighters on a carrier, or airport fighters. On a cell under the mouse: wake that container or city |
| f | At a city needing production: produce a fighter. On a city or carrier under the mouse: set or clear a flight path to the destination |
| t | At a city needing production: produce a transport |
| p | At a city needing production: produce a patrol boat. On a city under the mouse: clear production |
| b | At a city needing production: produce a battleship |
| . | Set the destination marker at the mouse. If the mouse is off the map: clear the destination |
| * | Create or remove a waypoint at the mouse |
| m | Set or clear marching orders on the city or transport under the mouse using the destination |
| P | Pause or unpause the game |
| ! | Open the save game dialog |
| ^ | Open the load game menu |
| Escape | Close the load menu or cancel the save dialog |
| Enter | Save using the name in the save dialog |
| Backspace | Delete the last character in the save dialog name |
| Delete | Delete the last character in the save dialog name |
| ? | Open this help window |
| + | Cycle the map view among player map, computer map, and actual map |
| Ctrl | Hold and drag on the map to write a debug dump of the selected region |
| Cmd | Hold and drag on the map to write a debug dump of the selected region |
| Alt | Hold and drag on the map to write a debug dump of the selected region |
| ` | Prefix for debug commands. The next keystroke acts at the mouse |
| `A | Add a player army at the mouse |
| `F | Add a player fighter at the mouse |
| `Z | Add a player satellite at the mouse |
| `T | Add a player transport at the mouse |
| `P | Add a player patrol boat at the mouse |
| `D | Add a player destroyer at the mouse |
| `S | Add a player submarine at the mouse |
| `C | Add a player carrier at the mouse |
| `B | Add a player battleship at the mouse |
| `a | Add a computer army at the mouse |
| `f | Add a computer fighter at the mouse |
| `z | Add a computer satellite at the mouse |
| `t | Add a computer transport at the mouse |
| `p | Add a computer patrol boat at the mouse |
| `d | Add a computer destroyer at the mouse |
| `s | Add a computer submarine at the mouse |
| `c | Add a computer carrier at the mouse |
| `b | Add a computer battleship at the mouse |
| `o | Convert the city under the mouse to player ownership |

Fail if any keystroke is missing, if a backtick command is omitted, or if an explanation does not match.

## keys 3 — Dismiss

1. With the help window open, click `Dismiss`.
2. The help window is gone.
3. The map is visible again.

Fail if the button does not close the window, or if only the button disappears while the list remains.

## keys 4 — Closed by default

1. Quit and launch a fresh game with the same command as Launch.
2. Focus the window. Do not press `?`.
3. No help window is showing.

## keys 5 — Reopen after dismiss

1. Press `?` so the help window is visible.
2. Click `Dismiss` so the help window is gone.
3. Press `?` again.
4. The help window is visible again, with the same keystroke list and a `Dismiss` button.

## keys 6 — Game keys do not act while help is open

Use a position where a player army is flashing and waiting for orders. If the opening city is waiting for production instead, produce an army with `a`, then wait until that army is flashing.

1. Confirm a player army is waiting for orders.
2. Press `?` so the help window is visible.
3. Press `s`. The help window stays open. The army is still waiting; it is not in sentry.
4. Press `q`. The help window stays open. The army has not moved.
5. Press `P`. The help window stays open. The game is not paused (no `PAUSED` indicator).
6. Click `Dismiss`. The army is still waiting for orders.

Fail if any of those keys closes help, sentries or moves the army, or pauses the game.

## Pass

The suite passes only when keys 1 through 6 all pass on the headed UI.
