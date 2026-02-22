(ns empire.atoms)

(def random-seed
  "Random seed for reproducible map generation, or nil for random."
  (atom nil))

(def map-size (atom [0 0]))

(def map-size-constants
  "Map of all constants derived from the [cols rows] map size."
  (atom {}))

(def last-key (atom nil))

(def backtick-pressed (atom false))

(def map-screen-dimensions (atom [0 0]))

(def text-area-dimensions (atom [0 0 0 0]))

(def map-to-display (atom :player-map))

(def round-number (atom 0))

(def last-clicked-cell (atom nil))

;; Fonts
(def text-font (atom nil))
(def production-char-font (atom nil))

;; Production map: coordinates -> production status
(def production (atom {}))

;; Game maps
(def game-map
  "A 2D atom containing vectors representing the game map."
  (atom nil))

(def player-map
  "An atom containing the player's visible map areas."
  (atom {}))

;; Coordinates of cells needing attention
(def cells-needing-attention
  "An atom containing coordinates of player's awake units and cities with no production."
  (atom []))

;; List of player items to process this round
(def player-items
  "An atom containing list of player city/unit coords to process."
  (atom []))

;; Flag indicating we're waiting for user input
(def waiting-for-input
  "An atom indicating if we're waiting for user input on current item."
  (atom false))

;; Attention message to display to the player
(def attention-message
  "An atom containing the attention message to display."
  (atom ""))

(def turn-message
  "An atom containing the turn message to display (row 2, Game Info region)."
  (atom ""))

(def turn-message-until
  "An atom containing the timestamp until which turn-message should not be overwritten."
  (atom 0))

(def hover-message
  "An atom containing the hover info message to display on line 3."
  (atom ""))

(def error-message
  "An atom containing the error message to display (row 3, Game Info region). Red, flashing, timed."
  (atom ""))

(def error-until
  "An atom containing the timestamp until which error-message should be displayed."
  (atom 0))

(defn set-error-message
  "Sets a flashing error message (row 3) that displays for the specified milliseconds."
  [msg ms]
  (reset! error-message msg)
  (reset! error-until (+ (System/currentTimeMillis) ms)))

(defn set-turn-message
  "Sets a turn message (row 2) that persists for the specified milliseconds.
   Use Long/MAX_VALUE for a permanent message."
  [msg ms]
  (reset! turn-message msg)
  (reset! turn-message-until (if (= ms Long/MAX_VALUE)
                               Long/MAX_VALUE
                               (+ (System/currentTimeMillis) ms))))

(def production-status
  "Formatted string showing player unit counts and exploration %."
  (atom ""))

(def computer-map
  "An atom containing the computer's visible map areas."
  (atom {}))

(def destination
  "An atom containing the remembered destination coordinates for marching orders/flight paths."
  (atom nil))

(def paused
  "An atom indicating if the game is currently paused."
  (atom false))

(def game-over-check-enabled
  "An atom to enable/disable game-over detection. Set to false in tests."
  (atom true))

(def pause-requested
  "An atom indicating a pause has been requested at end of current round."
  (atom false))

(def computer-items
  "An atom containing list of computer city/unit coords to process."
  (atom []))

(def computer-turn
  "An atom indicating if we're currently processing the computer's turn."
  (atom false))

(def next-transport-id
  "An atom containing the next unique ID to assign to a computer transport."
  (atom 1))

(def next-country-id
  "An atom containing the next unique country ID to assign."
  (atom 1))

(def next-unload-event-id
  "An atom containing the next unique ID for transport unload cycles."
  (atom 1))

(def next-destroyer-id
  "An atom containing the next unique ID to assign to a computer destroyer."
  (atom 1))

(def next-carrier-id
  "An atom containing the next unique ID to assign to a computer carrier."
  (atom 1))

(def next-escort-id
  "An atom containing the next unique ID to assign to carrier group escorts (battleships/submarines)."
  (atom 1))

(def sea-lane-network
  "Persistent navigation graph built from A* paths for computer ship routing."
  (atom {:nodes {} :segments {} :pos->node {} :pos->seg {}
         :next-node-id 1 :next-segment-id 1}))

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

(def last-transport-city
  "Map of country-id to city-pos of last transport producer, for rotation."
  (atom {}))

(def fighter-leg-records
  "Global map tracking fighter coverage legs between refueling sites.
   Key is a set of two positions (unordered pair), value is {:last-flown round-number}."
  (atom {}))

(def coast-walkers-produced
  "Map of country-id -> count of coast-walk armies produced for that country.
   First army gets clockwise, second gets counter-clockwise, 3+ get normal explore."
  (atom {}))

(def patrol-boats-produced
  "Map of country-id -> count of patrol boats produced for that country.
   1st patrols coastline, 2nd-4th sail random headings."
  (atom {}))

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
