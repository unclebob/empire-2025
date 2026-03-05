;; mutation-tested: 2026-03-03
(ns empire.computer.transport-core
  "Shared transport helpers — no dependencies on other transport sub-modules."
  (:require [empire.application.state-access :as sa]
            [empire.computer.core :as core]))


(defmulti get-passable-sea-neighbors (fn [& _] :default))

(defmethod get-passable-sea-neighbors :default
  [pos]
  (let [game-map (sa/current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :computer (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defmulti recently-unloaded-country? (fn [& _] :default))

(defmethod recently-unloaded-country? :default
  [unloaded-countries country-id]
  (when-let [unload-round (get unloaded-countries country-id)]
    (< (- (or (sa/read-state :round-number) 0) unload-round) 10)))

(defmulti adjacent-to-land? (fn [& _] :default))

(defmethod adjacent-to-land? :default
  [pos]
  (let [game-map (sa/current-world)]
    (some (fn [n]
            (= :land (:type (get-in game-map n))))
          (core/get-neighbors pos))))

(defmulti find-adjacent-land-pos (fn [& _] :default))

(defmethod find-adjacent-land-pos :default
  [pos]
  (let [game-map (sa/current-world)]
    (first (filter (fn [n]
                     (let [cell (get-in game-map n)]
                       (and cell (#{:land :city} (:type cell)))))
                   (core/get-neighbors pos)))))

(defmulti set-transport-mission (fn [& _] :default))

(defmethod set-transport-mission :default
  [pos mission]
  (sa/update-world! assoc-in (conj pos :contents :transport-mission) mission)
  (when (= mission :loading)
    (sa/update-world! assoc-in (conj pos :contents :loading-since)
                      (or (sa/read-state :round-number) 0))))

(defmulti mint-unload-event-id (fn [& _] :default))

(defmethod mint-unload-event-id :default
  [pos _transport]
  (let [id (or (sa/read-state :next-unload-event-id) 0)]
    (sa/write-state! :next-unload-event-id (inc id))
    (sa/update-world! assoc-in
           (conj pos :contents :unload-event-id) id)))

(defmulti mint-unload-country-id (fn [& _] :default))

(defmethod mint-unload-country-id :default
  [pos]
  (let [cid (or (sa/read-state :next-country-id) 0)]
    (sa/write-state! :next-country-id (inc cid))
    (sa/update-world! assoc-in
           (conj pos :contents :unload-country-id) cid)))

(defmulti record-pickup-continent-pos (fn [& _] :default))

(defmethod record-pickup-continent-pos :default
  [pos transport]
  (when-not (:pickup-continent-pos transport)
    (when-let [land-pos (find-adjacent-land-pos pos)]
      (sa/update-world! assoc-in
             (conj pos :contents :pickup-continent-pos) land-pos)
      (when-let [cid (:country-id (get-in (sa/current-world) land-pos))]
        (sa/update-world! assoc-in
               (conj pos :contents :pickup-country-id) cid)))))
