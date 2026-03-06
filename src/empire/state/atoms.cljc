;; mutation-tested: 2026-02-28
(ns empire.state.atoms
  (:require [empire.state.runtime :as runtime]
            [empire.config.domain.core.continents :as continents]
            [empire.config.domain.core.messages :as messages]
            [empire.config.domain.core.refueling :as refueling]))

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
  [msg ms]
  (reset! error-message msg)
  (reset! error-until (messages/expires-at (System/currentTimeMillis) ms)))

(defn set-turn-message
  [msg ms]
  (reset! turn-message msg)
  (reset! turn-message-until (if (= ms Long/MAX_VALUE)
                               Long/MAX_VALUE
                               (messages/expires-at (System/currentTimeMillis) ms))))

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

(def continent-groups
  "Union-find for country-ids on the same landmass.
   {country-id -> canonical-country-id}"
  (atom {}))

(defn on-same-continent?
  [cid1 cid2]
  (continents/on-same-continent? @continent-groups cid1 cid2))

(defn merge-continents!
  [cid1 cid2]
  (swap! continent-groups continents/merge-continents cid1 cid2))

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

(def debug-drag-start runtime/debug-drag-start)
(def debug-drag-current runtime/debug-drag-current)
(def debug-message runtime/debug-message)
(def claimed-objectives runtime/claimed-objectives)
(def claimed-transport-targets runtime/claimed-transport-targets)
(def claimed-patrol-targets runtime/claimed-patrol-targets)
(def last-transport-city runtime/last-transport-city)
(def fighter-leg-records runtime/fighter-leg-records)
(def computer-city-positions runtime/computer-city-positions)
(def computer-carrier-positions runtime/computer-carrier-positions)

(defn computer-city-cell?
  [cell]
  (refueling/computer-city-cell? cell))

(defn computer-carrier-cell?
  [cell]
  (refueling/computer-carrier-cell? cell))

(defn rebuild-refueling-caches!
  []
  (let [{:keys [cities carriers]}
        (refueling/scan-refueling-positions @game-map)]
    (reset! computer-city-positions cities)
    (reset! computer-carrier-positions carriers)))

(def country-stats runtime/country-stats)
(def coastal-cells-by-country runtime/coastal-cells-by-country)
(def coast-walkers-produced runtime/coast-walkers-produced)
(def patrol-boats-produced runtime/patrol-boats-produced)
(def seen-coast runtime/seen-coast)
(def land-ho-targets runtime/land-ho-targets)
(def major-invasion-state runtime/major-invasion-state)
(def transport-fully-loaded? runtime/transport-fully-loaded?)
(def early-patrol-boat-produced? runtime/early-patrol-boat-produced?)
(def early-satellite-produced? runtime/early-satellite-produced?)
(def computer-event-log runtime/computer-event-log)
(def action-log runtime/action-log)
(def player-movement-log runtime/player-movement-log)
(def distant-city-pairs runtime/distant-city-pairs)
(def lake-max-cells runtime/lake-max-cells)
(def known-lake-cells runtime/known-lake-cells)
(def load-menu-open runtime/load-menu-open)
(def load-menu-files runtime/load-menu-files)
(def load-menu-hovered runtime/load-menu-hovered)
