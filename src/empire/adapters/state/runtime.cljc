;; mutation-tested: no
(ns empire.adapters.state.runtime
  "Atom-backed runtime state adapter for non-world state."
  (:require [empire.application.ports :as ports]
            [empire.atoms :as atoms]
            [empire.domain.world.continents :as continents]
            [empire.domain.world.messages :as messages]
            [empire.domain.world.refueling :as refueling]))

(def ^:private runtime-key->atom
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

(def ^:private runtime-state*
  (atom {}))

(defn refresh-runtime-state!
  "Refreshes the consolidated runtime-state snapshot from legacy atoms.
   This keeps adapter reads coherent even while some outer adapters still
   read/write atom vars directly."
  []
  (reset! runtime-state*
          (into {}
                (map (fn [[k a]] [k @a]))
                runtime-key->atom)))

(defonce ^:private initialized?
  (do
    (refresh-runtime-state!)
    true))

(defn- require-runtime-atom
  [k]
  (or (get runtime-key->atom k)
      (throw (ex-info (str "Unsupported runtime-state key: " k) {:key k}))))

(defn- read-key
  [k]
  (let [a (require-runtime-atom k)
        v @a]
    (swap! runtime-state* assoc k v)
    v))

(defn- write-key!
  [k v]
  (let [a (require-runtime-atom k)]
    (reset! a v)
    (swap! runtime-state* assoc k v)
    v))

(defrecord AtomRuntimeStateStore []
  ports/RuntimeStatePort
  (read-runtime-state [_ k]
    (read-key k))
  (write-runtime-state! [_ k v]
    (write-key! k v))
  ports/MajorInvasionStorePort
  (load-major-invasion-state [_]
    (read-key :major-invasion-state))
  (save-major-invasion-state! [_ state]
    (write-key! :major-invasion-state state)))

(defn runtime-state-store
  []
  (->AtomRuntimeStateStore))

(defn read-runtime-state
  [ctx k]
  ((:read-runtime-state ctx) k))

(defn write-runtime-state!
  [ctx k v]
  ((:write-runtime-state! ctx) k v))

(defn update-runtime-state!
  [ctx k f & args]
  (write-runtime-state! ctx k (apply f (read-runtime-state ctx k) args)))

(defn rebuild-refueling-caches!
  []
  (let [{:keys [cities carriers]}
        (refueling/scan-refueling-positions @atoms/game-map)]
    (reset! atoms/computer-city-positions cities)
    (reset! atoms/computer-carrier-positions carriers)))

(defn set-turn-message!
  [msg ms]
  (reset! atoms/turn-message msg)
  (reset! atoms/turn-message-until (if (= ms Long/MAX_VALUE)
                                     Long/MAX_VALUE
                                     (messages/expires-at (System/currentTimeMillis) ms))))

(defn set-error-message!
  [msg ms]
  (reset! atoms/error-message msg)
  (reset! atoms/error-until (messages/expires-at (System/currentTimeMillis) ms)))

(defn merge-continents!
  [stamp-id existing-cid]
  (swap! atoms/continent-groups continents/merge-continents stamp-id existing-cid))

(defn on-same-continent?
  [country-a country-b]
  (continents/on-same-continent? @atoms/continent-groups country-a country-b))

(defn game-map-atom
  []
  atoms/game-map)

(defn player-map-atom
  []
  atoms/player-map)

(defn computer-map-atom
  []
  atoms/computer-map)
