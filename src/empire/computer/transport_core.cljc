;; mutation-tested: 2026-03-03
(ns empire.computer.transport-core
  "Shared transport helpers — no dependencies on other transport sub-modules."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]))


(defn get-passable-sea-neighbors
  [pos]
  (let [game-map (sa/current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :computer (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn recently-unloaded-country?
  [unloaded-countries country-id]
  (when-let [unload-round (get unloaded-countries country-id)]
    (< (- (or (sa/read-state :round-number) 0) unload-round) 10)))

(defn adjacent-to-land?
  [pos]
  (let [game-map (sa/current-world)]
    (some (fn [n]
            (= :land (:type (get-in game-map n))))
          (core/get-neighbors pos))))

(defn find-adjacent-land-pos
  [pos]
  (let [game-map (sa/current-world)]
    (first (filter (fn [n]
                     (let [cell (get-in game-map n)]
                       (and cell (#{:land :city} (:type cell)))))
                   (core/get-neighbors pos)))))

(defn set-transport-mission
  [pos mission]
  (sa/update-world! assoc-in (conj pos :contents :transport-mission) mission)
  (when (= mission :loading)
    (sa/update-world! assoc-in (conj pos :contents :loading-since)
                      (or (sa/read-state :round-number) 0))))

(defn mint-unload-event-id
  [pos _transport]
  (let [id (or (sa/read-state :next-unload-event-id) 0)]
    (sa/write-state! :next-unload-event-id (inc id))
    (sa/update-world! assoc-in
           (conj pos :contents :unload-event-id) id)))

(defn mint-unload-country-id
  [pos]
  (let [cid (or (sa/read-state :next-country-id) 0)]
    (sa/write-state! :next-country-id (inc cid))
    (sa/update-world! assoc-in
           (conj pos :contents :unload-country-id) cid)))

(defn record-pickup-continent-pos
  [pos transport]
  (when-not (:pickup-continent-pos transport)
    (when-let [land-pos (find-adjacent-land-pos pos)]
      (sa/update-world! assoc-in
             (conj pos :contents :pickup-continent-pos) land-pos)
      (when-let [cid (:country-id (get-in (sa/current-world) land-pos))]
        (sa/update-world! assoc-in
               (conj pos :contents :pickup-country-id) cid)))))
