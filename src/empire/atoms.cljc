(ns empire.atoms)

(def map-size (atom [0 0]))

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

;; Message to display to the player
(def message
  "An atom containing the current message to display."
  (atom ""))

(def line2-message
  "An atom containing the message to display on line 2."
  (atom ""))

(def confirmation-until
  "An atom containing the timestamp until which line2-message should not be overwritten."
  (atom 0))

(def hover-message
  "An atom containing the hover info message to display on line 3."
  (atom ""))

(def line3-message
  "An atom containing the flashing warning message to display on line 3."
  (atom ""))

(def line3-until
  "An atom containing the timestamp until which line3-message should be displayed."
  (atom 0))

(defn set-line3-message
  "Sets a flashing message on line 3 that displays for the specified milliseconds."
  [msg ms]
  (reset! line3-message msg)
  (reset! line3-until (+ (System/currentTimeMillis) ms)))

(defn set-confirmation-message
  "Sets a confirmation message on line 2 that persists for the specified milliseconds.
   Use Long/MAX_VALUE for a permanent message."
  [msg ms]
  (reset! line2-message msg)
  (reset! confirmation-until (if (= ms Long/MAX_VALUE)
                               Long/MAX_VALUE
                               (+ (System/currentTimeMillis) ms))))

(def computer-map
  "An atom containing the computer's visible map areas."
  (atom {}))

(def destination
  "An atom containing the remembered destination coordinates for marching orders/flight paths."
  (atom nil))

(def paused
  "An atom indicating if the game is currently paused."
  (atom false))

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

(def reserved-beaches
  "An atom mapping beach coordinates to the transport-id that reserved them.
   Structure: {[beach-row beach-col] transport-id}"
  (atom {}))

(def used-unloading-beaches
  "An atom containing set of beach coordinates that have been used for unloading.
   Once a transport unloads at a beach, no other transport should use it."
  (atom #{}))

(def beach-army-orders
  "Tracks beaches needing army production.
   When a transport departs, the nearest city produces 6 armies for the beach.
   Structure: {beach-pos {:city-pos [r c], :remaining n}}"
  (atom {}))

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

(def action-log
  "Circular buffer of recent game actions for debugging. Capped at 100 entries.
   Each entry is {:timestamp <ms> :action <vector describing the action>}."
  (atom []))
