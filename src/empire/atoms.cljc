(ns empire.atoms)

(def map-size (atom [0 0]))

(def last-key (atom nil))

(def test-mode (atom false))

(def map-screen-dimensions (atom [0 0]))

(def text-area-dimensions (atom [0 0 0 0]))

(def map-to-display (atom :player-map))

(def round-number (atom 0))

(def last-clicked-cell (atom nil))

(def menu-state (atom {:visible false :x 0 :y 0 :items []}))

;; Fonts
(def text-font (atom nil))
(def menu-header-font (atom nil))
(def menu-item-font (atom nil))
(def production-char-font (atom nil))

;; Production map: coordinates -> production status
(def production (atom {}))

;; Cell coordinates that opened the current menu
(def menu-cell (atom nil))

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

;; Message to display to the player
(def message
  "An atom containing the current message to display."
  (atom ""))

(def computer-map
  "An atom containing the computer's visible map areas."
  (atom {}))

(def message (atom nil))