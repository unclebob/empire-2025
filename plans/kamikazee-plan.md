# Kamikazee Plan

## Goal

Extend major invasion so fighters become persistent kamikazee army hunters supported by carriers and invasion production overrides.

## Behavior

- When major invasion starts, every computer fighter enters a kamikazee invasion mission.
- Each fighter computes the shortest legal refuel chain through computer cities and carriers.
- The route goal is a terminal refuel site from which the target area is reachable within one full fighter fuel load.
- Newly captured computer cities are valid refuel sites immediately.
- If no complete chain exists, the fighter moves to the closest reachable refuel site and waits for a continuation opportunity.
- Carriers reposition to sea cells between the terminal refuel sites and the target area.
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

1. Add acceptance scenarios for fighter routing, dynamic target updates, carrier support, and production overrides.
2. Add invasion fighter mission state and dynamic army-target registry to threat-response state.
3. Implement fighter refuel-chain planning and waiting-at-forward-site behavior.
4. Implement persistent army-hunt execution with non-backtracking random walk and recency-biased target choice.
5. Implement carrier forward positioning for fighter support.
6. Implement major-invasion production override and restoration when loaded transports are gone.
7. Run acceptance pipeline and targeted specs, then close any gaps with additional unit tests.

## Risks

- The current fighter threat executor assumes patrol and refuel-return behavior; kamikazee mode will need a separate movement path.
- Carrier positioning must not break existing carrier refuel semantics.
- Production override must layer on top of normal strategy without permanently corrupting city intent after invasion conditions change.
