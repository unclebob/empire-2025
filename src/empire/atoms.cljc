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

(def line3-message
  "An atom containing the message to display on line 3."
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
  "Sets a confirmation message on line 2 that persists for the specified milliseconds."
  [msg ms]
  (reset! line2-message msg)
  (reset! confirmation-until (+ (System/currentTimeMillis) ms)))

(def computer-map
  "An atom containing the computer's visible map areas."
  (atom {}))

(def destination
  "An atom containing the remembered destination coordinates for marching orders/flight paths."
  (atom nil))
