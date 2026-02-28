;; mutation-tested: 2026-02-28
(ns empire.computer.transport-core
  "Shared transport helpers — no dependencies on other transport sub-modules."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]))

(defn get-passable-sea-neighbors
  "Returns passable sea neighbors for a transport."
  [pos]
  (let [game-map @atoms/game-map]
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
    (< (- @atoms/round-number unload-round) 10)))

(defn adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (map-utils/adjacent-to-land? pos atoms/game-map))

(defn find-adjacent-land-pos
  "Returns the first adjacent land or city position, or nil."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [n]
                     (let [cell (get-in game-map n)]
                       (and cell (#{:land :city} (:type cell)))))
                   (core/get-neighbors pos)))))

(defn set-transport-mission
  "Set the transport's mission state."
  [pos mission]
  (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) mission)
  (when (= mission :loading)
    (swap! atoms/game-map assoc-in (conj pos :contents :loading-since) @atoms/round-number)))

(defn mint-unload-event-id
  "Mint a new unload-event-id each time transport transitions to unloading.
   Always mints a fresh ID so armies from previous unload cycles can be loaded."
  [pos _transport]
  (let [id @atoms/next-unload-event-id]
    (swap! atoms/next-unload-event-id inc)
    (swap! atoms/game-map assoc-in
           (conj pos :contents :unload-event-id) id)))

(defn mint-unload-country-id
  "Mint a new country-id for armies unloaded in this sailing cycle."
  [pos]
  (let [cid @atoms/next-country-id]
    (swap! atoms/next-country-id inc)
    (swap! atoms/game-map assoc-in
           (conj pos :contents :unload-country-id) cid)))

(defn record-pickup-continent-pos
  "When transport becomes full, record the nearest adjacent land position
   as the pickup continent reference point and its country-id."
  [pos transport]
  (when-not (:pickup-continent-pos transport)
    (when-let [land-pos (find-adjacent-land-pos pos)]
      (swap! atoms/game-map assoc-in
             (conj pos :contents :pickup-continent-pos) land-pos)
      (when-let [cid (:country-id (get-in @atoms/game-map land-pos))]
        (swap! atoms/game-map assoc-in
               (conj pos :contents :pickup-country-id) cid)))))
