;; mutation-tested: no
(ns empire.adapters.state.runtime
  "Atom-backed runtime state adapter for non-world state."
  (:require [empire.application.ports :as ports]
            [empire.atoms :as atoms]))

(def ^:private runtime-key->atom
  {:production atoms/production
   :round-number atoms/round-number
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
   :map-to-display atoms/map-to-display
   :attention-message atoms/attention-message
   :turn-message atoms/turn-message
   :turn-message-until atoms/turn-message-until
   :production-status atoms/production-status
   :pause-requested atoms/pause-requested
   :destination atoms/destination
   :load-menu-open atoms/load-menu-open
   :load-menu-files atoms/load-menu-files
   :load-menu-hovered atoms/load-menu-hovered
   :player-map atoms/player-map
   :computer-city-positions atoms/computer-city-positions
   :computer-carrier-positions atoms/computer-carrier-positions
   :computer-map atoms/computer-map
   :computer-turn atoms/computer-turn
   :next-transport-id atoms/next-transport-id
   :next-country-id atoms/next-country-id
   :next-unload-event-id atoms/next-unload-event-id
   :next-destroyer-id atoms/next-destroyer-id
   :next-carrier-id atoms/next-carrier-id
   :next-escort-id atoms/next-escort-id
   :fighter-leg-records atoms/fighter-leg-records
   :last-transport-city atoms/last-transport-city
   :country-stats atoms/country-stats
   :seen-coast atoms/seen-coast
   :coast-walkers-produced atoms/coast-walkers-produced
   :coastal-cells-by-country atoms/coastal-cells-by-country
   :land-ho-targets atoms/land-ho-targets
   :major-invasion-state atoms/major-invasion-state
   :transport-fully-loaded? atoms/transport-fully-loaded?
   :early-patrol-boat-produced? atoms/early-patrol-boat-produced?
   :early-satellite-produced? atoms/early-satellite-produced?
   :distant-city-pairs atoms/distant-city-pairs})

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
