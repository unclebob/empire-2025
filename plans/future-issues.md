# Future Issues

- **Final invasion force**: When all free cities are taken, coordinate large-scale multi-unit attacks on player cities and positions.

## Acceptance Test Gaps

### Game Lifecycle
1. Game-over: player loses when no cities/units remain
2. Victory: player wins when no computer cities/units remain
3. Pause/resume: P key toggles, space steps one round when paused
4. Save/load: ! saves, ^ opens load menu

### Movement Modes
5. Explore mode: 50-step limit, priority for unexplored cells, coastline following, wake on stuck
6. Coastline-follow mode: transport/patrol-boat, 100-step limit, wake on return to start, wake on map edge, wake in bay
7. Waypoint following: unit reaches waypoint and continues to waypoint's marching-orders
8. Sidestep: blocked unit tries adjacent cells with 4-round look-ahead

### Wake Conditions
9. Transport "been-to-sea" flag: wakes at beach after being in open sea
10. Fighter bingo fuel with carrier-chase calculation (distance x 4/3)
11. Enemy unit entering visibility radius wakes sentry (tested for some ships, not all units)

### Combat
12. Carrier group escort system (battleship/submarine paired with carrier)
13. Escort death cascades (destroyer dies -> transport seeks; transport dies -> destroyer seeks)
14. Cargo drowning when container takes damage
15. Ship/fighter ownership flipping on city conquest

### Container Operations
16. Auto-load sentry armies onto transport
17. Army disembark with marching-orders target (enters :moving mode)
18. Army disembark to explore
19. Fighter launch from carrier toward specific target

### Computer AI
20. Territory sovereignty: armies can't cross foreign country-id land
21. Army boarding adjacent loading transport
22. Sentry wake-up radius with interior-explore-direction
23. Ship AI: escort modes, attack coordination, formation

### Player Commands
24. Map display toggle: + key cycles player/computer/actual map
25. Fighter fuel-based space key (shows fuel countdown)
26. Marching orders via m key + direction keys
27. Flight path via f key

### Ship Repair (Player Side)
28. Player ship docking at friendly city for repair
29. Undamaged ship rejected with "not damaged" message

### Satellite
30. Satellite destroyed after 50 turns
31. Satellite boundary bouncing behavior
