(ns empire.computer.shared.transport-query
  (:require [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(defn- transport-compatible?
  "Returns true if the transport doesn't have a matching unload-event-id as the army.
   An army should not board the same transport that unloaded it."
  [transport-unit army-unload-event-id]
  (or (nil? army-unload-event-id)
      (nil? (:unload-event-id transport-unit))
      (not= (:unload-event-id transport-unit) army-unload-event-id)))

(defn- loading-transport?
  [unit army-unload-event-id]
  (and unit
       (= :computer (:owner unit))
       (= :transport (:type unit))
       (= :loading (:transport-mission unit))
       (< (:army-count unit 0) 6)
       (transport-compatible? unit army-unload-event-id)))

(defn find-loading-transport
  ([] (find-loading-transport nil))
  ([army-unload-event-id]
   (let [world (sa/read-state :computer-map)]
     (first (for [i (range (count world))
                  j (range (count (first world)))
                  :let [unit (:contents (get-in world [i j]))]
                  :when (loading-transport? unit army-unload-event-id)]
              [i j])))))

(defn find-adjacent-loading-transport
  ([pos]
   (find-adjacent-loading-transport pos nil))
  ([pos army-unload-event-id]
   (let [world (sa/read-state :computer-map)]
     (first (filter (fn [neighbor]
                      (loading-transport? (:contents (get-in world neighbor)) army-unload-event-id))
                    (world-query/get-neighbors pos))))))
