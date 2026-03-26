(ns empire.computer.shared.transport-query
  (:require [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
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

(def ^:private loading-transport-cache (atom nil))

(defn clear-loading-transport-cache! [] (reset! loading-transport-cache nil))

(defn scan-loading-transports
  [world army-unload-event-id]
  (vec (for [i (range (count world))
             j (range (count (first world)))
             :let [unit (:contents (get-in world [i j]))]
             :when (loading-transport? unit army-unload-event-id)]
         [i j])))

(defn- cached-loading-transports [army-unload-event-id]
  (or @loading-transport-cache
      (let [result (scan-loading-transports (sa/read-state :computer-map) army-unload-event-id)]
        (reset! loading-transport-cache result)
        result)))

(defn find-loading-transport
  ([] (find-loading-transport nil))
  ([army-unload-event-id]
   (first (cached-loading-transports army-unload-event-id))))

(defn find-nearby-loading-transport
  ([pos max-distance]
   (find-nearby-loading-transport pos max-distance nil))
  ([pos max-distance army-unload-event-id]
   (->> (cached-loading-transports army-unload-event-id)
        (filter #(<= (grid/chebyshev-distance pos %) max-distance))
        (sort-by #(vector (grid/chebyshev-distance pos %) %))
        first)))

(defn find-adjacent-loading-transport
  ([pos]
   (find-adjacent-loading-transport pos nil))
  ([pos army-unload-event-id]
   (let [world (sa/read-state :computer-map)]
     (first (filter (fn [neighbor]
                      (loading-transport? (:contents (get-in world neighbor)) army-unload-event-id))
                    (world-query/get-neighbors pos))))))
