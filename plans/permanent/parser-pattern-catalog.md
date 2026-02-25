# Parser Pattern Catalog

Quick reference for acceptance test parser patterns and their IR output.
Read this BEFORE loading parser source files.

## Source Files
- `src/empire/acceptance/parser/helpers.cljc` -- shared utils, char sets, pattern dispatch
- `src/empire/acceptance/parser/given.cljc` -- GIVEN parsing
- `src/empire/acceptance/parser/when.cljc` -- WHEN parsing
- `src/empire/acceptance/parser/then.cljc` -- THEN parsing
- `src/empire/acceptance/parser.cljc` -- facade (split-into-tests, parse-test, parse-file, -main)

## GIVEN Patterns

### Map patterns (given-map-patterns)
Consumed first. Next non-blank lines matching `map-row?` chars become `:rows`.

| Input | IR |
|-------|-----|
| `[GIVEN] [game] map` | `{:type :map :target :game-map :rows [...]}` |
| `[GIVEN] game map` | same (explicit variant) |
| `[GIVEN] player map` | `{:type :map :target :player-map :rows [...]}` |
| `[GIVEN] computer map` | `{:type :map :target :computer-map :rows [...]}` |

### Directive patterns (given-directive-patterns)
Matched after keyword stripping. Context tracks `:units-with-mode` set.

| Input | IR |
|-------|-----|
| `[the] game is waiting for input` | `{:type :waiting-for-input-state}` |
| `<U> is waiting for input` | `{:type :waiting-for-input :unit U :set-mode <bool>}` (false if mode already set) |
| `production at <C> is <item> with <N> rounds remaining` | `{:type :production :city C :item :item :remaining-rounds N}` |
| `production at <C> is <item>` | `{:type :production :city C :item :item}` |
| `no production` | `{:type :no-production}` |
| `round <N>` | `{:type :round :value N}` |
| `destination [X Y]` | `{:type :destination :coords [X Y]}` |
| `cell [X Y] has <k> <v> [and <k2> <v2>]` | `{:type :cell-props :coords [X Y] :props {k v ...}}` |
| `player-items are <a>, <b>, ...` | `{:type :player-items :items [a b ...]}` |
| `player-items <item>` | `{:type :player-items :items [item]}` |
| `player units are <a>, <b>` | same as player-items multi |
| `player units <item>` | same as player-items single |
| `waiting-for-input` (bare) | `{:type :waiting-for-input-state}` |
| `<U>'s target is <T>` | `{:type :unit-target :unit U :target T}` |
| `<C> has a <player\|computer> <unit-type>` | `{:type :city-unit :city C :unit-type :kw :owner :kw}` |
| `<C> has a <ship> with <N> hits in its shipyard` | `{:type :shipyard-state :city C :ship-type :kw :hits N}` |
| `[the] computer controls <N> cities` | `{:type :stub :bindings [{:var "...count-computer-cities" :value "(constantly N)"}]}` |
| `[a] valid carrier position exists` | `{:type :stub :bindings [{:var "...find-carrier-position" :value "(constantly [0 0])"}]}` |
| `<ref> is visible to computer` | `{:type :visible-to-computer :ref ref}` |
| `<C> has city-status <status>` | `{:type :city-prop :city C :prop :city-status :value :kw}` |
| `territory around <C> belongs to country <N>` | `{:type :territory-around :city C :country-id N}` |
| `<ref> belongs to country <N>` | city: `{:type :city-prop :city ref :prop :country-id :value N}`, unit: `{:type :unit-props :unit ref :props {:country-id N}}` |
| `<ref> patrols [for] country <N>` | `{:type :unit-props :unit ref :props {:country-id N :patrol-mode :crawling}}` |

### Fallback: unit-prop extractors (parse-unit-props-line)
If no directive matches, lines like `<U> is <mode>` or `<U> has fuel <N>` are parsed via extractors:

| Fragment | Props set |
|----------|-----------|
| `is/has mode/mode <m>` | `:mode :m` |
| `[has] fuel <N>` / `with fuel <N>` | `:fuel N` |
| `army-count <N>` / `<word> army/armies` | `:army-count N` |
| `hits <N>` | `:hits N` |
| `fighter-count <N>` / `<word> fighters` | container `:fighter-count N` |
| `awake-fighters <N>` / `no awake fighters` / `<word> awake fighters` | container `:awake-fighters N` |
| `has/with mission <v>` | `:transport-mission :v` |
| `has/with [an] escort destroyer` | `:escort-destroyer-id 1` |
| `has/with heading <N>` | `:heading N` |
| `has/with [sail-]path [...]` | `:(sail-)path <edn-vec>` |
| `has/with <prop> [X Y]` | `:prop [X Y]` |
| `has/with <hyphenated-prop> <val>` | `:prop val` (catch-all) |

IR: `{:type :unit-props :unit U :props {...}}` and optionally `{:type :container-state :target U :props {...}}`

### Fallback: container-state (parse-container-state-line)
| Input | IR |
|-------|-----|
| `<T> has one fighter in its airport` | `{:type :container-state :target T :props {:fighter-count 1 :awake-fighters 1}}` |
| `<T> has no fighters` | `{:type :container-state :target T :props {:fighter-count 0}}` |
| `<T> has <N> fighters` | `{:type :container-state :target T :props {:fighter-count N}}` |

## WHEN Patterns

Handlers return vectors of IR nodes. Key dispatch uses `determine-key-type`: uppercase direction -> `:key-down`; lowercase direction + waiting-for-input context -> `:handle-key`; else `:key-down`. Combat type inferred from unit chars in context.

| Input | IR nodes |
|-------|----------|
| `mouse is at cell [X Y] and...backtick then <k>` | `[{:type :backtick :key :k :mouse-cell [X Y]}]` |
| `mouse is at cell [X Y] and...presses <k>` | `[{:type :mouse-at-key :coords [X Y] :key :k}]` |
| `<U> is waiting for input and the player presses <k>` | `[{:type :waiting-for-input :unit U :set-mode true} {:type :key-press :key :k :input-fn ...}]` |
| `player presses <k> and <wins\|loses> the battle` | `[{:type :battle :key :k :outcome :win/:lose :combat-type :army/:ship}]` |
| `player presses <k> and [game advances until] <U> is waiting for input` | `[{:type :key-press ...} {:type :advance-until-waiting :unit U}]` |
| `player presses <k>` | `[{:type :key-press :key :k :input-fn ...}]` |
| `player types <k1> <k2> ...` | `[{:type :key-press ...} ...]` (one per key) |
| `new round starts and <U> is waiting for input` | `[{:type :start-new-round} {:type :advance-until-waiting :unit U}]` |
| `new round starts` / `next round begins` | `[{:type :start-new-round}]` |
| `game advances one batch` | `[{:type :advance-game-batch}]` |
| `game advances` | `[{:type :advance-game}]` |
| `player items are processed` | `[{:type :process-player-items}]` |
| `cell visibility updates for <U>` | `[{:type :cell-visibility-update :unit U}]` |
| `visibility updates` | `[{:type :visibility-update}]` |
| `production for <C> is evaluated` | `[{:type :evaluate-production :city C}]` |
| `computer chooses production at <C>` | `[{:type :evaluate-production :city C}]` |
| `computer transport <U> is processed` | `[{:type :process-computer-transport :unit U}]` |
| `computer fighter <U> is processed` | `[{:type :process-computer-fighter :unit U}]` |
| `<N> computer rounds pass` | `[{:type :computer-rounds :count N}]` |
| `<U> is waiting for input` (standalone) | `[{:type :waiting-for-input :unit U :set-mode true}]` |

## THEN Patterns

Timing prefix `at [the] next round/step/move` adds `:at-next-round true` or `:at-next-step true` to the IR. Compound `and` clauses after `airport/aboard/cell` are auto-split.

### Bare patterns (then-bare-patterns)
| Input | IR |
|-------|-----|
| `after <N> moves <U> will be at <T>` | `{:type :unit-after-moves :unit U :moves N :target T}` |
| `after <N> steps there is a <U> at [X Y]` | `{:type :unit-after-steps :unit U :steps N :coords [X Y]}` |
| `after <N> steps there is a <U> at <T>` | `{:type :unit-after-steps :unit U :steps N :target T}` |
| `<U> is waiting for input` | `{:type :unit-waiting-for-input :unit U}` |
| `<U> wakes up and asks for input` | same |
| `<U> is at [X Y] in mode <m>` | `[{:type :unit-at :unit U :coords [X Y]} {:type :unit-prop :unit U :property :mode :expected :m}]` |
| `<U> is at [X Y]` | `{:type :unit-at :unit U :coords [X Y]}` |
| `<U> is at <T>` | `{:type :unit-at :unit U :target T}` |
| `eventually <U> will be at <T>` | `{:type :unit-eventually-at :unit U :target T}` |
| `there is no <U> on the map` | `{:type :unit-absent :unit U}` |
| `there is no <attention\|turn\|error> message` | `{:type :no-message :area :kw}` |
| `<area> message for <U> contains :<key>` | `{:type :message-for-unit :area :kw :unit U :config-key :key}` |
| `<area> message contains "<text>"` | `{:type :message-contains :area :kw :text text}` |
| `<area> message contains :<key>` | `{:type :message-contains :area :kw :config-key :key}` |
| `<area> message is (fmt :<key> <args>)` | `{:type :message-is :area :kw :format {:key :key :args [...]}}` |
| `<area> message is :<key>` | `{:type :message-is :area :kw :config-key :key}` |
| `message contains "<text>"` | `{:type :message-contains :area :attention :text text}` |
| `message contains :<key>` | `{:type :message-contains :area :attention :config-key :key}` |
| `out-of-fuel message is displayed` | `{:type :message-contains :area :attention :config-key :fighter-out-of-fuel}` |
| `player-map cell [X Y] is not nil` / `player can see [X Y]` | `{:type :player-map-cell-not-nil :coords [X Y]}` |
| `player-map cell [X Y] is nil` / `player cannot see [X Y]` | `{:type :player-map-cell-nil :coords [X Y]}` |
| `<C> has a <ship> with <N> hits in its shipyard` | `{:type :shipyard-has-ship :city C :ship-type :kw :hits N}` |
| `<C> has no ship in its shipyard` | `{:type :shipyard-empty :city C}` |
| `[the] map is <ref>` | `{:type :map-is :expected ref}` |
| `cell [X Y] has <prop> <val>` | `{:type :cell-prop :coords [X Y] :property :prop :expected :val}` |
| `on computer-map cell [X Y] is a <player\|computer> city` | `{:type :cell-prop ... :target :computer-map}` |
| `cell [X Y] is a <player\|computer> city` | `{:type :cell-prop :coords [X Y] :property :city-status :expected :kw}` |
| `cell [X Y] is [a] <type>` | `{:type :cell-type :coords [X Y] :expected :type}` |
| `waiting-for-input` / `game is waiting for input` | `{:type :waiting-for-input :expected true}` |
| `not waiting-for-input` / `game is not waiting for input` | `{:type :waiting-for-input :expected false}` |
| `game is paused` | `{:type :game-paused :expected true}` |
| `round is <N>` | `{:type :round :expected N}` |
| `destination is [X Y]` | `{:type :destination :expected [X Y]}` |
| `production at <C> is <item> with <N> rounds remaining` | `{:type :production-with-rounds :city C :expected :item :remaining-rounds N}` |
| `production at <C> is not <item>` | `{:type :production-not :city C :excluded :item}` |
| `production at <C> is <item>` | `{:type :production :city C :expected :item}` |
| `[there is] no production at <C>` | `{:type :no-production :city C}` |
| `no unit at [X Y]` | `{:type :no-unit-at :coords [X Y]}` |
| `there are <N> computer armies on the map` | `{:type :computer-army-count :expected N}` |

### Timed patterns (then-timed-patterns)
Tried only after bare patterns fail, using timing-stripped text.

| Input | IR |
|-------|-----|
| `<U> will be at <T>` | `{:type :unit-at-next-round :unit U :target T}` or `:coords` |
| `<U> occupies the <V> cell` | `{:type :unit-occupies-cell :unit U :target-unit V}` |
| `<U> remains unmoved` | `{:type :unit-unmoved :unit U}` |
| `<T> has one fighter in its airport` | `{:type :container-prop :target T :property :fighter-count :expected 1 :lookup :city}` |
| `<T> has one fighter aboard` | `{:type :container-prop :target T :property :fighter-count :expected 1 :lookup :unit}` |
| `<T> has no fighters` | `{:type :container-prop :target T :property :fighter-count :expected 0 :lookup :city/:unit}` |
| `<T> has <N> awake fighters` | `{:type :container-prop :target T :property :awake-fighters :expected N :lookup ...}` |
| `there is no <U>` | `{:type :unit-absent :unit U}` |
| `there is a <U> at [X Y]` | `{:type :unit-present :unit U :coords [X Y]}` |
| `there is a <U> at <T>` | `{:type :unit-present :unit U :target T}` |
| `<U> has no mission` | `{:type :unit-prop-absent :unit U :property :transport-mission}` |
| `<U> has <N> army/armies` | `{:type :unit-prop :unit U :property :army-count :expected N}` |
| `<U> has <N> turns remaining` | `{:type :unit-prop :unit U :property :turns-remaining :expected N}` |
| `<U> has mission <val>` | `{:type :unit-prop :unit U :property :transport-mission :expected :val}` |
| `<U> has refueling position near <T>` | `{:type :refueling-position-near :unit U :target T}` |
| `<U> has no <prop>` | `{:type :unit-prop-absent :unit U :property :prop}` |
| `<U> has <prop> <val>` | `{:type :unit-prop :unit U :property :prop :expected val}` |
| `<U> has mode/is <mode>` | `{:type :unit-prop :unit U :property :mode :expected :mode}` |
| `<U> does not have <prop>` | `{:type :unit-prop-absent :unit U :property :prop}` |

### Map block patterns (extract-then-map-blocks)
| Input | IR |
|-------|-----|
| `THEN player map` + map rows | `{:type :player-map-visibility :rows [...]}` |
| `THEN territory map` + territory rows | `{:type :territory-map :rows [...]}` |
