## Transport Visibility Follow-Up

Current status:

- `clj -M:spec` passes: `3981 examples, 0 failures`
- Acceptance pipeline does not pass
- `clj -M:parse-tests`: passed
- `clj -M:generate-specs`: passed
- `clj -M:spec generated-acceptance-specs/`: `296 examples, 11 failures`

Acceptance failures:

- `computer-ai.txt:21` - Computer army boards adjacent loading transport
  - expected `1`, got `0`
- `computer-ai.txt:35` - Sentry army woken near transport sets interior-explore-direction
  - expected `1`, got `0`
- `computer-threat-response.txt:188` - Empty transport opts out when no coastal army is within 6
  - expected `:loading`, got `:find-armies-for-invasion`
- `computer-threat-response.txt:362` - Invasion loading with armies near target joins invasion immediately
  - expected `:unloading`, got `:load-for-invasion`
- `computer-transport.txt:96` - direct fog corridor case
  - expected final pos `[2 0]`, got `[0 2]`
- `sailing-transport.txt:33` - Sailing transport moves two steps along its stored path
  - expected `[2 0]`, got `[0 0]`
- `sailing-transport.txt:48` - Sailing transport arrives at unowned land and unloads
  - expected army-count `5`, got `6`
- `sailing-transport.txt:63` - Sailing transport with armies and exhausted path transitions to unloading
  - expected `:unloading`, got `:sailing`
- `sailing-transport.txt:93` - Sailing transport blocked by enemy retreats along path
  - expected `[1 0]`, got `[2 0]`
- `transport.txt:125` - Transport unloads one army per turn onto adjacent land
  - expected `:loading`, got `:unloading`
- `transport.txt:139` - Sailing transport moves two steps (speed 2) continuing direction
  - expected `[0 0]`, got `[2 0]`

What changed just before these failures:

- Converted only the non-mutation transport reads from `sa/current-world` to `sa/read-state :computer-map` in:
  - `src/empire/computer/transport.cljc`
  - `src/empire/computer/transport_unloading.cljc`
  - `src/empire/computer/transport_sailing_regular.cljc`
- Updated many unit specs to seed `computer-map` explicitly after mutating `game-map`

Important unresolved mutation-coupled exceptions intentionally not fixed yet:

These authoritative-map rereads still survive because they depend on same-round writes not yet mirrored into `computer-map`.

### `src/empire/computer/transport.cljc`

- line ~96: `mission-handler-deps` still injects `:current-world sa/current-world`
- line ~142: `loading-crawl-move` rereads moved transport for `:pickup-continent-pos`
- line ~207: `dispatch-transport-mission` rereads current mission after mutation
- line ~245: `process-transport-random-walk` checks empty neighbors via authoritative occupancy
- line ~266: `process-active-transport` rereads current transport before dispatch
- line ~289: random-walk status reread after `maybe-enter-transport-random-walk!`
- line ~291: active transport passed via authoritative reread

### `src/empire/computer/transport_unloading.cljc`

- line ~135: `record-unloaded-country!` reads target `:country-id` from authoritative map after unload/stamping
- line ~234: `unloading-crawl-move` still reads world for current unit/occupancy/history and likely needs the same `computer-map` write-through fix as loading crawl

### `src/empire/computer/transport_sailing_regular.cljc`

- line ~94: after city launch, rereads moved transport from authoritative map

### `src/empire/computer/transport_sailing_invasion.cljc`

- line ~59: retreat candidate occupancy from authoritative map
- line ~89: rereads transport after up to two invasion steps
- line ~119: `choose-invading-step` reads `:invasion-last-pos` and occupancy from authoritative map
- line ~163: initial transport fetch for invading mission from authoritative map

Likely needed before those mutation exceptions can be removed:

- mirror AI-owned transport metadata into `computer-map` whenever updated
- likely fields:
  - `:transport-mission`
  - `:pickup-continent-pos`
  - `:loading-since`
  - `:crawl-history`
  - `:sail-path`
  - `:invasion-path`
  - `:invasion-path-origin`
  - `:invasion-last-pos`
  - `:never-reload?`
  - `:unload-event-id`
  - `:unload-country-id`
  - `:army-count`
  - `:unloaded-countries`

Useful observed audit counts after the non-mutation pass:

- `src/empire/computer/transport.cljc`: `7`
- `src/empire/computer/transport_sailing_invasion.cljc`: `4`
- `src/empire/computer/transport_unloading.cljc`: `2`
- `src/empire/computer/transport_sailing_regular.cljc`: `1`

Interpretation to carry forward:

- Unit suite is green because specs were updated to seed `computer-map`
- Acceptance failures suggest the runtime path still lacks some visibility synchronization for transports, especially sailing, invasion, loading, and adjacent-army boarding behavior
