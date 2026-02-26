# Future Issues

- **Fighters prefer unexplored cells**: Fighter pathfinding should prefer routes that cross unexplored cells to maximize scouting.
- **Fighters spread to conquered cities**: Fighters should distribute themselves to conquered cities and explore outward from there.
- **Early satellites**: Produce satellites early in the game for rapid map exploration.
- **Final invasion force**: When all free cities are taken, transports should target player cities for invasion.
- **Massive attack**: Coordinate large-scale multi-unit attacks on player cities and positions.
- **Carrier movement optimization**: Carriers currently use full A* for every step. Consider caching or other optimization if performance is an issue.
- **Too many moving armies**: The computer produces too many armies that march around without clear objectives.

## Acceptance Test Gaps

### Game Lifecycle
- Game-over: player loses when no cities/units remain
- Victory: player wins when no computer cities/units remain
- Pause/resume: P key toggles, space steps one round when paused
- Save/load: ! saves, ^ opens load menu

### Movement Modes
- Explore mode: 50-step limit, priority for unexplored cells, coastline following, wake on stuck
- Coastline-follow mode: transport/patrol-boat, 100-step limit, wake on return to start, wake on map edge, wake in bay
- Waypoint following: unit reaches waypoint and continues to waypoint's marching-orders
- Sidestep: blocked unit tries adjacent cells with 4-round look-ahead

### Wake Conditions
- Transport "been-to-sea" flag: wakes at beach after being in open sea
- Fighter bingo fuel with carrier-chase calculation (distance x 4/3)
- Enemy unit entering visibility radius wakes sentry (tested for some ships, not all units)

### Combat
- Carrier group escort system (battleship/submarine paired with carrier)
- Escort death cascades (destroyer dies -> transport seeks; transport dies -> destroyer seeks)
- Cargo drowning when container takes damage
- Ship/fighter ownership flipping on city conquest

### Container Operations
- Auto-load sentry armies onto transport
- Army disembark with marching-orders target (enters :moving mode)
- Army disembark to explore
- Fighter launch from carrier toward specific target

### Computer AI
- Territory sovereignty: armies can't cross foreign country-id land
- Army boarding adjacent loading transport
- Sentry wake-up radius with interior-explore-direction
- Ship AI: escort modes, attack coordination, formation

### Player Commands
- Map display toggle: + key cycles player/computer/actual map
- Fighter fuel-based space key (shows fuel countdown)
- Marching orders via m key + direction keys
- Flight path via f key

### Ship Repair (Player Side)
- Player ship docking at friendly city for repair
- Undamaged ship rejected with "not damaged" message

### Satellite
- Satellite destroyed after 50 turns
- Satellite boundary bouncing behavior
