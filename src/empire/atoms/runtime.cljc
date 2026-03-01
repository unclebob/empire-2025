;; mutation-tested: 2026-02-28
(ns empire.atoms.runtime)

;; Debug atoms

(def debug-drag-start
  "Screen coords [x y] when debug drag begins, or nil."
  (atom nil))

(def debug-drag-current
  "Current screen coords [x y] during debug drag."
  (atom nil))

(def debug-message
  "Message to display in the debug window (middle section).
   Remains until overwritten by another message."
  (atom ""))

(def claimed-objectives
  "Per-round set of objectives already claimed by computer armies."
  (atom #{}))

(def claimed-transport-targets
  "Per-round set of target cities already claimed by computer transports."
  (atom #{}))

(def claimed-patrol-targets
  "Per-round set of BFS targets already claimed by computer patrol boats."
  (atom #{}))

(def last-transport-city
  "Map of country-id to city-pos of last transport producer, for rotation."
  (atom {}))

(def fighter-leg-records
  "Global map tracking fighter coverage legs between refueling sites.
   Key is a set of two positions (unordered pair), value is {:last-flown round-number}."
  (atom {}))

(def computer-city-positions
  "Cached set of computer-owned city positions. Updated on conquest/loss."
  (atom #{}))

(def computer-carrier-positions
  "Cached set of computer carrier positions. Updated on spawn/move/death."
  (atom #{}))

(def country-stats
  "Per-country stats computed once at round start.
   {country-id {:army-count N :land-army-count N :coastal-cell-count N
                :patrol-boat-count N :has-waiting-armies? bool
                :has-unadopted-transport? bool :has-unoccupied-coastal-cells? bool
                :coastal-explored? bool :coastal-city-positions #{...}}}"
  (atom {}))

(def coastal-cells-by-country
  "Cache of coastal land cells per country. {country-id -> #{[r c] ...}}.
   Populated as armies move; used by find-nearest-unoccupied-coastal-cell."
  (atom {}))

(def coast-walkers-produced
  "Map of country-id -> count of coast-walk armies produced for that country.
   First army gets clockwise, second gets counter-clockwise, 3+ get normal explore."
  (atom {}))

(def patrol-boats-produced
  "Map of country-id -> count of patrol boats produced for that country.
   1st patrols coastline, 2nd-4th sail random headings."
  (atom {}))

(def seen-coast
  "Set of [col row] coastal cells visited by computer patrol boats.
   Shared across all patrol boats, accumulates for the entire game."
  (atom #{}))

(def land-ho-targets
  "Ordered list of [col row] free city positions discovered by computer units.
   FIFO queue consumed by transport invasion assignment at round start."
  (atom []))

(def major-invasion-state
  "Global major invasion state for threat-response coordination.
   {:active? bool :detection-points #{[r c] ...} :target-land-set #{[r c] ...}
    :started-round N-or-nil}"
  (atom {:active? false
         :detection-points #{}
         :target-land-set #{}
         :started-round nil}))

(def transport-fully-loaded?
  "Set true when any computer transport first reaches full army load. Never reset."
  (atom false))

(def early-patrol-boat-produced?
  "Set true when the early patrol boat enters production. Never reset."
  (atom false))

(def early-satellite-produced?
  "Set true when the early satellite enters production. Never reset."
  (atom false))

(def computer-event-log
  "Circular buffer of computer unit events for debugging. Capped at 2000 entries.
   Each entry is {:round N :event :keyword :pos [x y] :details {...}}."
  (atom []))

(def action-log
  "Circular buffer of recent game actions for debugging. Capped at 100 entries.
   Each entry is {:timestamp <ms> :action <vector describing the action>}."
  (atom []))

(def player-movement-log
  "Circular buffer of player unit movements for debugging. Capped at 500 entries.
   Each entry is {:round N :unit-type :keyword :from [x y] :to [x y]
                  :mode :keyword :event :move/:wake/:blocked :reason :keyword-or-nil}."
  (atom []))

(def distant-city-pairs
  "Set of computer city pairs where distance > fighter-fuel.
   Each pair is a set of two positions #{[r1 c1] [r2 c2]}.
   Updated when computer conquers or loses a city.
   nil means not yet computed; #{} means computed but empty."
  (atom nil))

;; Load menu state
(def load-menu-open
  "An atom indicating if the load game menu is open."
  (atom false))

(def load-menu-files
  "An atom containing the list of available save files."
  (atom []))

(def load-menu-hovered
  "An atom containing the index of the hovered file in the load menu, or nil."
  (atom nil))
