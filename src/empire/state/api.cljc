(ns empire.state.api
  "Direct atom-backed state access. Public boundary for all game state."
  (:require [empire.application.state.atoms :as atoms]
            [empire.application.state.runtime :as runtime]
            [empire.application.runtime :as app-runtime]
            [empire.domain.core.continents :as continents]))

(def ^:private key->atom
  {:random-seed atoms/random-seed
   :map-size atoms/map-size
   :map-size-constants atoms/map-size-constants
   :last-key atoms/last-key
   :production atoms/production
   :round-number atoms/round-number
   :backtick-pressed atoms/backtick-pressed
   :last-clicked-cell atoms/last-clicked-cell
   :waiting-for-input atoms/waiting-for-input
   :cells-needing-attention atoms/cells-needing-attention
   :claimed-objectives atoms/claimed-objectives
   :claimed-transport-targets atoms/claimed-transport-targets
   :claimed-patrol-targets atoms/claimed-patrol-targets
   :player-items atoms/player-items
   :computer-items atoms/computer-items
   :game-over-check-enabled atoms/game-over-check-enabled
   :paused atoms/paused
   :error-message atoms/error-message
   :error-until atoms/error-until
   :map-screen-dimensions atoms/map-screen-dimensions
   :text-area-dimensions atoms/text-area-dimensions
   :map-to-display atoms/map-to-display
   :attention-message atoms/attention-message
   :turn-message atoms/turn-message
   :turn-message-until atoms/turn-message-until
   :hover-message atoms/hover-message
   :production-status atoms/production-status
   :pause-requested atoms/pause-requested
   :destination atoms/destination
   :text-font atoms/text-font
   :production-char-font atoms/production-char-font
   :game-map atoms/game-map
   :load-menu-open atoms/load-menu-open
   :load-menu-files atoms/load-menu-files
   :load-menu-hovered atoms/load-menu-hovered
   :debug-drag-start atoms/debug-drag-start
   :debug-drag-current atoms/debug-drag-current
   :debug-message atoms/debug-message
   :player-map atoms/player-map
   :computer-city-positions atoms/computer-city-positions
   :computer-carrier-positions atoms/computer-carrier-positions
   :computer-map atoms/computer-map
   :computer-turn atoms/computer-turn
   :next-transport-id atoms/next-transport-id
   :next-country-id atoms/next-country-id
   :continent-groups atoms/continent-groups
   :next-unload-event-id atoms/next-unload-event-id
   :next-destroyer-id atoms/next-destroyer-id
   :next-carrier-id atoms/next-carrier-id
   :next-escort-id atoms/next-escort-id
   :fighter-leg-records atoms/fighter-leg-records
   :last-transport-city atoms/last-transport-city
   :country-stats atoms/country-stats
   :patrol-boats-produced atoms/patrol-boats-produced
   :seen-coast atoms/seen-coast
   :coast-walkers-produced atoms/coast-walkers-produced
   :coastal-cells-by-country atoms/coastal-cells-by-country
   :land-ho-targets atoms/land-ho-targets
   :major-invasion-state atoms/major-invasion-state
   :transport-fully-loaded? atoms/transport-fully-loaded?
   :early-patrol-boat-produced? atoms/early-patrol-boat-produced?
   :early-satellite-produced? atoms/early-satellite-produced?
   :computer-event-log atoms/computer-event-log
   :action-log atoms/action-log
   :player-movement-log atoms/player-movement-log
   :distant-city-pairs atoms/distant-city-pairs
   :lake-max-cells atoms/lake-max-cells
   :known-lake-cells atoms/known-lake-cells})

(defn- resolve-atom [k]
  (or (get key->atom k)
      (throw (ex-info (str "Unknown state key: " k) {:key k}))))

(defn current-world [] @atoms/game-map)

(defn update-world! [f & args]
  (apply swap! atoms/game-map f args))

(defn read-state [k] @(resolve-atom k))

(defn write-state! [k v] (reset! (resolve-atom k) v))

(defn update-state! [k f & args]
  (apply swap! (resolve-atom k) f args))

(defn merge-continents! [stamp-id existing-cid]
  (swap! atoms/continent-groups continents/merge-continents stamp-id existing-cid))

(defn world-atom [] atoms/game-map)

;; Transition: kept until ports/adapters are removed (step C)
(def ^:private ctx (delay (app-runtime/default-state-ctx)))

(defn state-ctx [] @ctx)

(defn context-fn [k] (get @ctx k))
