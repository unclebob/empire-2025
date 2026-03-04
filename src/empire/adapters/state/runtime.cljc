;; mutation-tested: no
(ns empire.adapters.state.runtime
  "Atom-backed runtime state adapter for non-world state."
  (:require [empire.application.ports.runtime-state :as ports]
            [empire.domain.core.continents :as continents]
            [empire.domain.core.messages :as messages]
            [empire.domain.core.refueling :as refueling]))

(def ^:private runtime-key->atom-sym
  {:random-seed 'empire.atoms/random-seed
   :map-size 'empire.atoms/map-size
   :map-size-constants 'empire.atoms/map-size-constants
   :last-key 'empire.atoms/last-key
   :production 'empire.atoms/production
   :round-number 'empire.atoms/round-number
   :backtick-pressed 'empire.atoms/backtick-pressed
   :last-clicked-cell 'empire.atoms/last-clicked-cell
   :waiting-for-input 'empire.atoms/waiting-for-input
   :cells-needing-attention 'empire.atoms/cells-needing-attention
   :claimed-objectives 'empire.atoms/claimed-objectives
   :claimed-transport-targets 'empire.atoms/claimed-transport-targets
   :claimed-patrol-targets 'empire.atoms/claimed-patrol-targets
   :player-items 'empire.atoms/player-items
   :computer-items 'empire.atoms/computer-items
   :game-over-check-enabled 'empire.atoms/game-over-check-enabled
   :paused 'empire.atoms/paused
   :error-message 'empire.atoms/error-message
   :error-until 'empire.atoms/error-until
   :map-screen-dimensions 'empire.atoms/map-screen-dimensions
   :text-area-dimensions 'empire.atoms/text-area-dimensions
   :map-to-display 'empire.atoms/map-to-display
   :attention-message 'empire.atoms/attention-message
   :turn-message 'empire.atoms/turn-message
   :turn-message-until 'empire.atoms/turn-message-until
   :hover-message 'empire.atoms/hover-message
   :production-status 'empire.atoms/production-status
   :pause-requested 'empire.atoms/pause-requested
   :destination 'empire.atoms/destination
   :text-font 'empire.atoms/text-font
   :production-char-font 'empire.atoms/production-char-font
   :game-map 'empire.atoms/game-map
   :load-menu-open 'empire.atoms/load-menu-open
   :load-menu-files 'empire.atoms/load-menu-files
   :load-menu-hovered 'empire.atoms/load-menu-hovered
   :debug-drag-start 'empire.atoms/debug-drag-start
   :debug-drag-current 'empire.atoms/debug-drag-current
   :debug-message 'empire.atoms/debug-message
   :player-map 'empire.atoms/player-map
   :computer-city-positions 'empire.atoms/computer-city-positions
   :computer-carrier-positions 'empire.atoms/computer-carrier-positions
   :computer-map 'empire.atoms/computer-map
   :computer-turn 'empire.atoms/computer-turn
   :next-transport-id 'empire.atoms/next-transport-id
   :next-country-id 'empire.atoms/next-country-id
   :continent-groups 'empire.atoms/continent-groups
   :next-unload-event-id 'empire.atoms/next-unload-event-id
   :next-destroyer-id 'empire.atoms/next-destroyer-id
   :next-carrier-id 'empire.atoms/next-carrier-id
   :next-escort-id 'empire.atoms/next-escort-id
   :fighter-leg-records 'empire.atoms/fighter-leg-records
   :last-transport-city 'empire.atoms/last-transport-city
   :country-stats 'empire.atoms/country-stats
   :patrol-boats-produced 'empire.atoms/patrol-boats-produced
   :seen-coast 'empire.atoms/seen-coast
   :coast-walkers-produced 'empire.atoms/coast-walkers-produced
   :coastal-cells-by-country 'empire.atoms/coastal-cells-by-country
   :land-ho-targets 'empire.atoms/land-ho-targets
   :major-invasion-state 'empire.atoms/major-invasion-state
   :transport-fully-loaded? 'empire.atoms/transport-fully-loaded?
   :early-patrol-boat-produced? 'empire.atoms/early-patrol-boat-produced?
   :early-satellite-produced? 'empire.atoms/early-satellite-produced?
   :computer-event-log 'empire.atoms/computer-event-log
   :action-log 'empire.atoms/action-log
   :player-movement-log 'empire.atoms/player-movement-log
   :distant-city-pairs 'empire.atoms/distant-city-pairs
   :lake-max-cells 'empire.atoms/lake-max-cells
   :known-lake-cells 'empire.atoms/known-lake-cells})

(def ^:private runtime-state*
  (atom {}))

(defn- resolve-atom
  [sym]
  (or (some-> sym requiring-resolve var-get)
      (throw (ex-info (str "Unable to resolve legacy atom var: " sym) {:symbol sym}))))

(defn- runtime-atom
  [k]
  (let [sym (get runtime-key->atom-sym k)]
    (or (and sym (resolve-atom sym))
        (throw (ex-info (str "Unsupported runtime-state key: " k) {:key k})))))

(defn refresh-runtime-state!
  []
  (reset! runtime-state*
          (into {}
                (map (fn [[k sym]] [k @(resolve-atom sym)]))
                runtime-key->atom-sym)))

(defonce ^:private initialized? true)

(defn- read-key
  [k]
  (let [a (runtime-atom k)
        v @a]
    (swap! runtime-state* assoc k v)
    v))

(defn- write-key!
  [k v]
  (let [a (runtime-atom k)]
    (reset! a v)
    (swap! runtime-state* assoc k v)
    v))

(defrecord AtomRuntimeStateStore []
  ports/RuntimeStatePort
  (read-runtime-state [_ k]
    (read-key k))
  (write-runtime-state! [_ k v]
    (write-key! k v))
  (on-same-continent? [_ country-a country-b]
    (continents/on-same-continent? @(runtime-atom :continent-groups) country-a country-b))
  (merge-continents! [_ stamp-id existing-cid]
    (swap! (runtime-atom :continent-groups) continents/merge-continents stamp-id existing-cid))
  (rebuild-refueling-caches! [_]
    (let [{:keys [cities carriers]}
          (refueling/scan-refueling-positions @(runtime-atom :game-map))]
      (reset! (runtime-atom :computer-city-positions) cities)
      (reset! (runtime-atom :computer-carrier-positions) carriers)))
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
        (refueling/scan-refueling-positions @(runtime-atom :game-map))]
    (reset! (runtime-atom :computer-city-positions) cities)
    (reset! (runtime-atom :computer-carrier-positions) carriers)))

(defn set-turn-message!
  [msg ms]
  (reset! (runtime-atom :turn-message) msg)
  (reset! (runtime-atom :turn-message-until)
          (if (= ms Long/MAX_VALUE)
            Long/MAX_VALUE
            (messages/expires-at (System/currentTimeMillis) ms))))

(defn set-error-message!
  [msg ms]
  (reset! (runtime-atom :error-message) msg)
  (reset! (runtime-atom :error-until) (messages/expires-at (System/currentTimeMillis) ms)))

(defn merge-continents!
  [stamp-id existing-cid]
  (swap! (runtime-atom :continent-groups) continents/merge-continents stamp-id existing-cid))

(defn on-same-continent?
  [country-a country-b]
  (continents/on-same-continent? @(runtime-atom :continent-groups) country-a country-b))

(defn game-map-atom
  []
  (runtime-atom :game-map))

(defn player-map-atom
  []
  (runtime-atom :player-map))

(defn computer-map-atom
  []
  (runtime-atom :computer-map))
