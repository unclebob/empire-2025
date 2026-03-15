# Early Game Strategy

## Phase 1

Phase 1 is the exploration and conquest of the starting continent.

Assumptions:

- The game starts with one coastal city.
- Phase 1 should produce armies, building both territorial control and an army stockpile for phase 2.
- Coastal cities discovered during phase 1 are critical because phase 2 transport strategy depends on them.

Primary goals:

- Find and capture new cities on the starting continent.
- Prioritize discovery and capture of coastal cities until there are at least three.
- Build up an army backlog that can later feed transports in phase 2.

Production policy:

- All cities should remain on army production during phase 1.
- Producing fighters or naval units in phase 1 should be avoided by default because it slows both city capture and army stockpile growth.
- Exception for newly conquered theaters with `C = 0`: keep producing armies until there are six armies on that landmass, then switch one city to fighter production while the rest remain `CA`.

Exploration policy:

- Most armies should bias toward coastal exploration because discovered coastal cities are the key enabler for phase 2.
- A smaller but still meaningful fraction of armies should probe inland to find landlocked cities.

Breakpoint rule:

- While the number of discovered/captured coastal cities is less than three, coastal exploration has strategic priority.
- Once the number of coastal cities reaches three, the marginal value of additional coastal cities drops.
- After that point, inland exploration should increase in priority because total production capacity matters more than adding further ports.

## Phase 2

Phase 2 begins after the first-phase exploration and conquest of the starting continent.

Assumptions:

- Phase 1 mostly produced armies.
- Switching city production discards partial progress.
- Phase 2 should therefore use stable, committed production roles rather than frequent switching.
- Loaded transports may launch without a fixed target.
- Patrol boats provide long-range sea discovery.
- Fighters provide regional reconnaissance.

Stable production roles:

- `CA` = committed army city
- `CF` = committed fighter city
- `CT` = committed transport city
- `CP` = committed patrol-boat city

The core planning question for phase 2 is:

- Which cities stop being army cities now?
- Which of those become transport cities?
- Is there enough army stockpile and replacement army production to support that switch?

Army movement timing:

- Phase 2 city production can switch into naval and reconnaissance roles early.
- Army repositioning to the coast should lag behind those production switches.
- Since transports take substantial time to complete, armies should continue conquering and exploring instead of massing on the coast immediately.
- Large-scale coastal staging should begin only when armies can arrive at the embarkation point just as the transport is produced.

### By Coastal Count

#### `C = 1`

- One-port economy.
- If `L <= 1`, the port cannot become pure transport immediately; it must remain army-capable until enough armies are staged.
- If `L >= 2`, the port can switch earlier into a committed transport role because inland support exists.

#### `C = 2`

- Two-port economy.
- One port can commit to transports earlier.
- The second port may need to remain army production if inland army support is thin.
- Once inland support is adequate, the second port can either stay army or switch to patrol support depending on army backlog.

#### `C >= 3`

- Transport throughput becomes the main coastal priority.
- Keep one coastal city for patrol-boat production.
- The remaining coastal cities should mostly become transport cities, except where coastal army production is still required because inland supply is too weak.

### By Landlocked Count

#### `L = 0`

- No inland support.
- Armies that fill transports must continue to come from coastal cities.
- Recon also competes for coastal production.

#### `L = 1`

- The one inland city is usually best used as a committed fighter city.
- Coastal cities still provide most embarked armies.

#### `L >= 2`

- One inland city should usually become a committed fighter city.
- Remaining inland cities should stay committed army cities.
- This is the first point where the coast can specialize strongly.

### Army Source Rules

#### `L = 0`

- Embarked armies come from coastal cities only.

#### `L = 1`

- Embarked armies come primarily from coastal cities, supplemented by the phase-1 army stockpile.

#### `L = 2`

- Embarked armies come from one inland army city plus coastal supplement and the phase-1 army stockpile.

#### `L >= 3`

- Embarked armies come mainly from inland army cities plus the phase-1 army stockpile.
- Coastal army production should become occasional rather than structural.

### Recon Rules

- Patrol boats should usually come from one committed coastal patrol city.
- Fighters should usually come from one committed inland fighter city whenever inland cities exist.
- Patrol boats provide strategic sea discovery.
- Fighters provide operational reconnaissance around likely invasion corridors.

### Stable Phase-2 Band Rules

#### `C = 1, L = 0`

- Coastal city stays `CA` for a while, then switches once to `CT` after enough armies are staged nearby to fill at least one transport.
- There is no clean committed `CP` role here without delaying transport heavily.

#### `C = 1, L = 1`

- Coastal city remains `CA` initially, then switches once to `CT`.
- The one landlocked city becomes `CF`.
- Embarked armies come from the army stockpile accumulated in phase 1 plus continued coastal army production before the switch.

#### `C = 1, L >= 2`

- Coastal city can switch earlier to `CT`.
- One landlocked city becomes `CF`.
- Remaining landlocked cities remain `CA`.
- Embarked armies come mainly from inland army cities plus the phase-1 stockpile.

#### `C = 2, L = 0`

- One coastal city stays `CA`.
- One coastal city switches to `CT`.
- Armies loaded into transports come from the coastal army city and the phase-1 army stockpile.

#### `C = 2, L = 1`

- One coastal city stays `CA`.
- One coastal city switches to `CT`.
- The landlocked city becomes `CF`.
- Armies still come mostly from the coastal army city and stockpiled armies.

#### `C = 2, L >= 2`

- One coastal city switches to `CT`.
- The second coastal city either remains `CA` or becomes `CP`, depending on how much army backlog phase 1 created.
- One landlocked city becomes `CF`.
- Remaining landlocked cities stay `CA`.
- If army backlog is strong, use `CT + CP`; if not, use `CT + CA`.

#### `C >= 3, L = 0`

- One coastal city remains `CA`.
- One coastal city becomes `CT`.
- One coastal city may become `CP`.
- Any extra coastal cities can become `CT`.

#### `C >= 3, L = 1`

- One inland city becomes `CF`.
- One coastal city remains `CA`.
- One coastal city becomes `CP`.
- Remaining coastal cities can become `CT`.

#### `C >= 3, L = 2`

- One inland city becomes `CF`.
- One inland city remains `CA`.
- One coastal city becomes `CP`.
- Remaining coastal cities can mostly become `CT`.

#### `C >= 3, L >= 3`

- One inland city becomes `CF`.
- Remaining inland cities stay `CA`.
- One coastal city becomes `CP`.
- All other coastal cities become `CT`.
- This is the clean specialized phase-2 economy.

### Satellite Exception

- After round 50, one city that would otherwise remain `CA` should build exactly one satellite.
- After the satellite is completed, that city returns to `CA`.
- This is a one-time exception layered on top of the normal phase-2 role bands.

### Summary

- If `L = 0`, keep at least one coastal army city alive.
- If `L = 1`, the inland city is usually fighter production, so the coast still carries most army production.
- If `L >= 2`, one inland fighter city plus inland army cities can support a much more specialized coast.
- If `C >= 3`, dedicate one coastal city to patrol boats and let the rest of the coast focus on transports unless army supply is still too weak.
- If `C = 0`, stay on armies until there are six armies on the landmass, then add one fighter city.
- After round 50, one otherwise-`CA` city should build a one-time satellite and then return to `CA`.

## Implementation Plan

This plan is deliberately scoped to the current early-game country/continent code paths and must not alter the current late-game invasion logic.

Late-game logic to leave untouched:

- transport mission behaviors after transport creation/loading
- invasion assignment and evaluation logic
- late-game capital ship / carrier / satellite production heuristics

Relevant current hooks:

- Country identity and production statistics are already tracked per `:country-id` in `src/empire/computer/production/stats.cljc`.
- Connected-land continent detection is already recomputed via flood fill in `src/empire/computer/land_objectives.cljc`.
- Early army exploration stamping currently happens per country in `src/empire/game_mechanics/services/unit_stamping.cljc`.
- Production decisions currently happen mainly in `src/empire/computer/production/decisions.cljc`.
- Army action selection currently routes through `src/empire/computer/army.cljc`.

### Design Boundary

The new early-game system should be implemented as an overlay that influences:

- city production choice in the opening
- initial army exploration bias
- early army coastal staging timing

It should not replace or rewrite:

- `transport-loading`
- `transport-targeting`
- `transport-unloading`
- `land-ho`
- threat-response invasion evaluation

Once the opening overlay expires because an invasion has begun, the existing country and invasion logic should resume full control.

### Concrete Opening Rules

- The opening overlay remains active until an invasion begins.
- A theater is in phase 1 until any of these conditions becomes true:
  - round `>= 30`
  - armies on that landmass `>= 6`
  - cities conquered on that landmass `>= 2`
- A theater is in phase 2 after phase 1 ends and before the opening overlay expires.
- `C` counts owned computer coastal cities on the current known connected landmass.
- `L` counts owned computer landlocked cities on the current known connected landmass.
- Coastal versus landlocked classification is derived from the computer's known map.
- Army stockpile means all computer armies on that landmass.
- When multiple cities are eligible for the same role, choose randomly.
- During phase 2, any city without a special assignment defaults to `CA`.
- When two known landmasses merge into one known connected continent, recompute the phase-2 plan for all cities as they finish their current production.
- A city keeps its opening role until it completes its current production, then it may be reassigned.
- Exception: if a city loses true coastal access and is now effectively on a lake, reclassify it immediately as inland and allow immediate reassignment.

### Step 1: Add an explicit opening-phase model

Create a small early-game policy module, for example:

- `src/empire/computer/early_game/strategy.cljc`

Responsibilities:

- determine whether the computer is still in the opening window
- determine whether a given land theater is in phase 1 or phase 2
- compute coastal-city and landlocked-city counts for the current known continent
- compute army counts and conquered-city counts for the current known continent
- expose stable role recommendations for the opening

Keep the first implementation simple:

- opening window: active until an invasion begins
- phase-1/phase-2 split: phase 1 ends as soon as `round >= 30`, or armies on the landmass reach `6`, or conquered cities on the landmass reach `2`
- treat newly discovered land the same way, but per connected known continent

### Step 2: Introduce continent-level opening stats without removing country stats

Add a continent-oriented summary module or helper on top of `land-objectives` and `country-stats`.

Needed derived values per connected known continent:

- set of member `country-id`s currently on that continent
- `C` = number of computer coastal cities on that continent
- `L` = number of computer inland cities on that continent
- armies on that continent
- conquered-city count on that continent
- number of committed army cities
- number of committed fighter cities
- number of committed transport cities
- number of committed patrol cities
- current army stockpile on that continent

Important:

- preserve `country-id` accounting exactly as it is now
- continent-level strategy should only aggregate and coordinate countries; it should not erase country identity

### Step 3: Persist stable opening production roles on cities

Add a city-level opening role marker, for example:

- `:opening-role :CA|:CF|:CT|:CP`

This role should be written only when a city first commits to a phase-2 opening role or when it remains default army production.

The purpose is to respect the no-partial-progress-loss rule:

- production should not bounce around opportunistically
- opening-role assignment should be sticky for a substantial window
- role changes should normally occur only when a city finishes its current production

Exception:

- if a city that was counted as coastal becomes lake-locked and therefore no longer qualifies as coastal strategy infrastructure, reclassify and reassign it immediately

Suggested placement:

- store the role on the city cell, because production decisions already inspect city cells

### Step 4: Make production decisions consult opening roles first

Extend `src/empire/computer/production/decisions.cljc` so that during the opening window:

- opening-role strategy is consulted before existing country production heuristics
- if a city has a committed opening role, production follows that role
- if a city has no assigned role yet, the opening strategy module may assign one

Opening-role policy:

- phase 1:
  - all cities default to `CA`
  - for a theater with `C = 0`, continue `CA` until there are six armies on that landmass, then assign one city to `CF`
- phase 2:
  - assign `CA`, `CF`, `CT`, `CP` using the band rules already documented
  - after round 50, allow one otherwise-`CA` city to build exactly one satellite, then restore it to `CA`
  - choose among equally eligible cities randomly

Crucially:

- when the opening window ends, production falls back to existing logic
- the existing late-game transport/carrier/destroyer/satellite decisions remain unchanged

### Step 5: Refine phase-1 army exploration bias

Replace the current simple country-local stamping heuristic in `unit_stamping.cljc`:

- currently first two armies per country are coast-walk
- remaining armies get `1/3` random interior explore chance

with an opening-strategy-aware policy:

- while continent coastal-city count is below `3`, prefer coastal exploration
- once coastal-city count reaches `3`, increase inland exploration share
- use the current known connected landmass, not hidden world state

Implementation shape:

- keep existing movement modes (`:coast-walk`, `:random-explore`, interior exploration)
- change only the assignment heuristic for newly produced armies
- do not rewrite the existing coast-walk and exploration execution logic in this first slice

This keeps the risk low while making the exploration mix strategy-aware.

### Step 6: Delay army coastal staging during early phase 2

Add an opening-phase guard around any early “move to coast for invasion” retasking.

Policy:

- phase-2 production can commit cities to `CT` and `CP` early
- armies should continue conquering and exploring until they can begin moving toward embarkation and arrive just as the transport completes
- only then should armies begin mass repositioning toward embarkation points

Implementation target:

- the army routing/orchestration path in `src/empire/computer/army.cljc` and nearby helpers

Do not change:

- transport loading rules themselves
- sailing start rules for already-produced transports

Only change:

- when armies begin preferring embarkation over continued local conquest/exploration during the opening

### Step 7: Handle newly conquered land as continent-scoped opening theaters

Reuse the same opening strategy on newly conquered land with two extra rules:

- if `C = 0`, keep all cities on `CA` until there are six armies on that landmass, then switch one city to `CF` while still seeking the first port
- if two previously separate countries are later found to be on the same connected continent, strategy should merge at the continent level automatically

Because continent flood-fill is already recomputed dynamically, the implementation should:

- compute opening policy from current connected known land
- not permanently bind a strategy to invasion origin

That gives the desired behavior:

- countries preserve identity
- connected continents can adopt a common opening strategy

### Step 8: Add acceptance scenarios before code changes

Add new opening-strategy acceptance scenarios covering:

- phase 1:
  - computer keeps producing armies
  - coastal discovery is favored until three coastal cities are found
- phase 2:
  - once production roles switch, transport cities stay committed
  - armies are not massed on the coast too early
  - inland fighter city appears only when inland support exists
  - one otherwise-`CA` city builds a one-time satellite after round 50
- newly conquered land:
  - `C = 0` theaters stay on armies until six armies exist, then add one fighter city while still seeking a first coastal city
  - connected-theater discovery causes a shared strategy outcome

Because existing acceptance tests require permission before `.txt` edits, this step should be explicitly approved before editing those files.

### Step 9: Add narrow unit/spec coverage for each seam

Targeted spec areas:

- opening strategy role assignment
- continent aggregation from country stats
- opening production override behavior
- army exploration-mode assignment rules
- delayed coast-staging trigger rules

Keep the tests focused on policy decisions, not giant end-to-end orchestration where possible.

### Step 10: Roll out in low-risk order

Recommended implementation order:

1. add opening strategy policy module and pure role-selection helpers
2. add continent-level aggregation helpers
3. add specs for band rules and role assignment
4. wire opening-role override into production decisions
5. wire opening-aware army stamping for phase 1 exploration
6. wire delayed coastal staging in early phase 2
7. add newly conquered land / continent-merge behavior
8. run full verification and then tune constants only if needed

This order keeps late-game invasion logic isolated until the end, and ideally untouched entirely.

### Non-goals

- rewriting transport loading heuristics
- rewriting transport targeting and invasion missions
- altering threat-response invasion evaluation
- changing late-game ship mix logic
- replacing the existing country identity model

### Exit Criteria

The implementation is successful if:

- phase 1 remains army-heavy and discovers coastal cities aggressively until three are found
- phase 2 assigns stable city production roles without oscillating
- armies are not staged on coasts long before transports are ready
- newly conquered land can use the same opening logic, including `C = 0`
- `C = 0` theaters add fighter production only after six armies are present
- after round 50, one otherwise-`CA` city produces one satellite and then returns to `CA`
- connected countries on the same discovered continent converge on a shared opening strategy
- late-game invasion behavior remains unchanged
