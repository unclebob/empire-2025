# Kamikazee Plan

## Goal

Extend major invasion so fighters become persistent kamikazee army hunters routed by a city-based forward graph, with limited carrier bridging and invasion production overrides.

## Behavior

- When major invasion starts, every computer fighter enters a kamikazee invasion mission.
- Build a reverse routing graph over computer cities only.
- Choose one computer city closest to the invasion target area as the root city.
- Working backward from the root, mark every city that can reach an already-marked city within one full fighter fuel load.
- Each marked city stores the next hop toward the root city.
- Fighters in a city follow that stored next-hop chain.
- Fighters not in a city first fly to a marked city they can reach, then join the chain.
- If an otherwise unreachable city can connect to the marked graph using a single carrier bridge, add that carrier as the next hop.
- Carriers used as city bridges are put into sentry mode and left fixed in place.
- At the root city, fighters check for a carrier closer to the target than the root city; if reachable, they refuel at that carrier before continuing toward the target.
- Rebuild the fighter routing graph whenever the computer conquers a new city.
- Fighters keep a dynamic target list of detected player armies during the invasion.
- New army detections update that list immediately, including while fighters are still en route.
- Fighter targets are chosen randomly from the list with a bias toward newer detections.
- Target entries age so stale detections become less preferred over time.
- Once a fighter reaches the target area it hunts armies until destroyed.
- Search movement in the target area is a non-backtracking random walk.
- Every computer city already within fighter fuel range of the target switches to fighter production immediately.
- While at least one invasion transport remains loaded, every other city also switches to fighter production.
- If no loaded invasion transports remain, non-target-range cities return to normal production policy.

## Implementation Order

1. Add acceptance scenarios for city-hop routing, carrier bridging, dynamic target updates, and production overrides.
2. Add invasion fighter routing-graph state to major-invasion state.
3. Implement reverse city graph construction rooted at the forward-most city.
4. Implement single-carrier bridge insertion and forward-carrier launch from the root city.
5. Retarget fighter execution to follow city and carrier next hops before entering hunt behavior.
6. Rebuild the graph on computer city conquest.
7. Run acceptance pipeline and targeted specs, then close any gaps with additional unit tests.

## Risks

- The current fighter executor assumes free-form refuel search; converting it to graph-following needs to preserve existing hunt behavior after arrival.
- Carrier bridge selection must stay limited to a single fixed carrier and must not steal arbitrary carriers away from other roles.
- Rebuilding the graph on conquest must happen after the city actually flips to computer ownership.
