;; mutation-tested: 2026-03-03
(ns empire.computer.transport-core
  "Shared transport helpers — no dependencies on other transport sub-modules."
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  (let [store (runtime-state/runtime-state-store)]
    (ports/read-runtime-state store k)))

(defn- write-runtime-state!
  [k v]
  (let [store (runtime-state/runtime-state-store)]
    (ports/write-runtime-state! store k v)))

(defn get-passable-sea-neighbors
  "Returns passable sea neighbors for a transport."
  [pos]
  (let [game-map (current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :computer (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn recently-unloaded-country?
  "Returns true if the country-id was unloaded to within the last 10 rounds."
  [unloaded-countries country-id]
  (when-let [unload-round (get unloaded-countries country-id)]
    (< (- (or (read-runtime-state :round-number) 0) unload-round) 10)))

(defn adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (map-utils/adjacent-to-land? pos (runtime-state/game-map-atom)))

(defn find-adjacent-land-pos
  "Returns the first adjacent land or city position, or nil."
  [pos]
  (let [game-map (current-world)]
    (first (filter (fn [n]
                     (let [cell (get-in game-map n)]
                       (and cell (#{:land :city} (:type cell)))))
                   (core/get-neighbors pos)))))

(defn set-transport-mission
  "Set the transport's mission state."
  [pos mission]
  (update-game-map! assoc-in (conj pos :contents :transport-mission) mission)
  (when (= mission :loading)
    (update-game-map! assoc-in (conj pos :contents :loading-since)
                      (or (read-runtime-state :round-number) 0))))

(defn mint-unload-event-id
  "Mint a new unload-event-id each time transport transitions to unloading.
   Always mints a fresh ID so armies from previous unload cycles can be loaded."
  [pos _transport]
  (let [id (or (read-runtime-state :next-unload-event-id) 0)]
    (write-runtime-state! :next-unload-event-id (inc id))
    (update-game-map! assoc-in
           (conj pos :contents :unload-event-id) id)))

(defn mint-unload-country-id
  "Mint a new country-id for armies unloaded in this sailing cycle."
  [pos]
  (let [cid (or (read-runtime-state :next-country-id) 0)]
    (write-runtime-state! :next-country-id (inc cid))
    (update-game-map! assoc-in
           (conj pos :contents :unload-country-id) cid)))

(defn record-pickup-continent-pos
  "When transport becomes full, record the nearest adjacent land position
   as the pickup continent reference point and its country-id."
  [pos transport]
  (when-not (:pickup-continent-pos transport)
    (when-let [land-pos (find-adjacent-land-pos pos)]
      (update-game-map! assoc-in
             (conj pos :contents :pickup-continent-pos) land-pos)
      (when-let [cid (:country-id (get-in (current-world) land-pos))]
        (update-game-map! assoc-in
               (conj pos :contents :pickup-country-id) cid)))))
