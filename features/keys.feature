# keys 1
# keys 2
# keys 3
# keys 4
# keys 5
# keys 6
Feature: keys

  Background:
    Given the game is showing the map

  # keys 1
  Scenario Outline: keys 1
    When the player presses the <key> key
    Then the help window is <visibility>
    And the help window has a <button> button

    Examples:
      | key | visibility | button  |
      | ?   | visible    | Dismiss |

  # keys 2
  Scenario Outline: keys 2
    Given the help window is visible
    Then the help window lists the keystroke <keystroke> with explanation <explanation>

    Examples:
      | keystroke | explanation |
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

  # keys 3
  Scenario Outline: keys 3
    Given the help window is visible
    When the player clicks the <button> button
    Then the help window is <visibility>

    Examples:
      | button  | visibility  |
      | Dismiss | not visible |

  # keys 4
  Scenario Outline: keys 4
    Then the help window is <visibility>

    Examples:
      | visibility  |
      | not visible |

  # keys 5
  Scenario Outline: keys 5
    Given the help window is visible
    And the player clicks the <button> button
    When the player presses the <key> key
    Then the help window is <visibility>

    Examples:
      | button  | key | visibility |
      | Dismiss | ?   | visible    |

  # keys 6
  Scenario Outline: keys 6
    Given a player army is waiting for orders
    And the help window is visible
    When the player presses the <key> key
    Then the help window is <visibility>
    And no order was taken

    Examples:
      | key | visibility |
      | s   | visible    |
      | q   | visible    |
      | P   | visible    |
