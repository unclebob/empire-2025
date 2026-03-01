;; mutation-tested: no
(ns empire.adapters.state.runtime
  "Atom-backed runtime state adapter for non-world state."
  (:require [empire.application.ports :as ports]
            [empire.atoms :as atoms]))

(defn- read-key
  [k]
  (case k
    :production @atoms/production
    :round-number @atoms/round-number
    :waiting-for-input @atoms/waiting-for-input
    :cells-needing-attention @atoms/cells-needing-attention
    :claimed-objectives @atoms/claimed-objectives
    :claimed-transport-targets @atoms/claimed-transport-targets
    :claimed-patrol-targets @atoms/claimed-patrol-targets
    :player-items @atoms/player-items
    :computer-items @atoms/computer-items
    :game-over-check-enabled @atoms/game-over-check-enabled
    :paused @atoms/paused
    :error-message @atoms/error-message
    :error-until @atoms/error-until
    :map-screen-dimensions @atoms/map-screen-dimensions
    :map-to-display @atoms/map-to-display
    :attention-message @atoms/attention-message
    :turn-message @atoms/turn-message
    :turn-message-until @atoms/turn-message-until
    :production-status @atoms/production-status
    :pause-requested @atoms/pause-requested
    :destination @atoms/destination
    :load-menu-open @atoms/load-menu-open
    :load-menu-files @atoms/load-menu-files
    :load-menu-hovered @atoms/load-menu-hovered
    :player-map @atoms/player-map
    :computer-city-positions @atoms/computer-city-positions
    :computer-carrier-positions @atoms/computer-carrier-positions
    :computer-map @atoms/computer-map
    :computer-turn @atoms/computer-turn
    :next-transport-id @atoms/next-transport-id
    :next-country-id @atoms/next-country-id
    :next-unload-event-id @atoms/next-unload-event-id
    :next-destroyer-id @atoms/next-destroyer-id
    :next-carrier-id @atoms/next-carrier-id
    :next-escort-id @atoms/next-escort-id
    :fighter-leg-records @atoms/fighter-leg-records
    :last-transport-city @atoms/last-transport-city
    :country-stats @atoms/country-stats
    :seen-coast @atoms/seen-coast
    :coast-walkers-produced @atoms/coast-walkers-produced
    :coastal-cells-by-country @atoms/coastal-cells-by-country
    :land-ho-targets @atoms/land-ho-targets
    :transport-fully-loaded? @atoms/transport-fully-loaded?
    :early-patrol-boat-produced? @atoms/early-patrol-boat-produced?
    :early-satellite-produced? @atoms/early-satellite-produced?
    :distant-city-pairs @atoms/distant-city-pairs
    (throw (ex-info (str "Unsupported runtime-state key: " k) {:key k}))))

(defn- write-key!
  [k v]
  (case k
    :production (reset! atoms/production v)
    :round-number (reset! atoms/round-number v)
    :waiting-for-input (reset! atoms/waiting-for-input v)
    :cells-needing-attention (reset! atoms/cells-needing-attention v)
    :claimed-objectives (reset! atoms/claimed-objectives v)
    :claimed-transport-targets (reset! atoms/claimed-transport-targets v)
    :claimed-patrol-targets (reset! atoms/claimed-patrol-targets v)
    :player-items (reset! atoms/player-items v)
    :computer-items (reset! atoms/computer-items v)
    :game-over-check-enabled (reset! atoms/game-over-check-enabled v)
    :paused (reset! atoms/paused v)
    :error-message (reset! atoms/error-message v)
    :error-until (reset! atoms/error-until v)
    :map-screen-dimensions (reset! atoms/map-screen-dimensions v)
    :map-to-display (reset! atoms/map-to-display v)
    :attention-message (reset! atoms/attention-message v)
    :turn-message (reset! atoms/turn-message v)
    :turn-message-until (reset! atoms/turn-message-until v)
    :production-status (reset! atoms/production-status v)
    :pause-requested (reset! atoms/pause-requested v)
    :destination (reset! atoms/destination v)
    :load-menu-open (reset! atoms/load-menu-open v)
    :load-menu-files (reset! atoms/load-menu-files v)
    :load-menu-hovered (reset! atoms/load-menu-hovered v)
    :player-map (reset! atoms/player-map v)
    :computer-city-positions (reset! atoms/computer-city-positions v)
    :computer-carrier-positions (reset! atoms/computer-carrier-positions v)
    :computer-map (reset! atoms/computer-map v)
    :computer-turn (reset! atoms/computer-turn v)
    :next-transport-id (reset! atoms/next-transport-id v)
    :next-country-id (reset! atoms/next-country-id v)
    :next-unload-event-id (reset! atoms/next-unload-event-id v)
    :next-destroyer-id (reset! atoms/next-destroyer-id v)
    :next-carrier-id (reset! atoms/next-carrier-id v)
    :next-escort-id (reset! atoms/next-escort-id v)
    :fighter-leg-records (reset! atoms/fighter-leg-records v)
    :last-transport-city (reset! atoms/last-transport-city v)
    :country-stats (reset! atoms/country-stats v)
    :seen-coast (reset! atoms/seen-coast v)
    :coast-walkers-produced (reset! atoms/coast-walkers-produced v)
    :coastal-cells-by-country (reset! atoms/coastal-cells-by-country v)
    :land-ho-targets (reset! atoms/land-ho-targets v)
    :transport-fully-loaded? (reset! atoms/transport-fully-loaded? v)
    :early-patrol-boat-produced? (reset! atoms/early-patrol-boat-produced? v)
    :early-satellite-produced? (reset! atoms/early-satellite-produced? v)
    :distant-city-pairs (reset! atoms/distant-city-pairs v)
    (throw (ex-info (str "Unsupported runtime-state key: " k) {:key k})))
  v)

(defrecord AtomRuntimeStateStore []
  ports/RuntimeStatePort
  (read-runtime-state [_ k]
    (read-key k))
  (write-runtime-state! [_ k v]
    (write-key! k v)))

(defn runtime-state-store
  []
  (->AtomRuntimeStateStore))

(defn rebuild-refueling-caches!
  []
  (atoms/rebuild-refueling-caches!))

(defn set-turn-message!
  [msg ms]
  (atoms/set-turn-message msg ms))

(defn set-error-message!
  [msg ms]
  (atoms/set-error-message msg ms))

(defn merge-continents!
  [stamp-id existing-cid]
  (atoms/merge-continents! stamp-id existing-cid))

(defn on-same-continent?
  [country-a country-b]
  (atoms/on-same-continent? country-a country-b))

(defn game-map-atom
  []
  atoms/game-map)

(defn player-map-atom
  []
  atoms/player-map)

(defn computer-map-atom
  []
  atoms/computer-map)
