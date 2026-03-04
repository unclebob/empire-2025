;; mutation-tested: 2026-03-02
(ns empire.computer.core.transport-search)

(defn- transport-compatible?
  "Returns true if the transport doesn't have a matching unload-event-id as the army.
   An army should not board the same transport that unloaded it."
  [transport-unit army-unload-event-id]
  (or (nil? army-unload-event-id)
      (nil? (:unload-event-id transport-unit))
      (not= (:unload-event-id transport-unit) army-unload-event-id)))

(defn find-loading-transport
  "Finds a transport in loading state that has room.
   When army-unload-event-id is provided, excludes transports with matching ID."
  [world army-unload-event-id]
  (first (for [i (range (count world))
               j (range (count (first world)))
               :let [cell (get-in world [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= :loading (:transport-mission unit))
                          (< (:army-count unit 0) 6)
                          (transport-compatible? unit army-unload-event-id))]
           [i j])))

(defn find-adjacent-loading-transport
  "Finds an adjacent loading transport with room.
   When army-unload-event-id is provided, excludes transports with matching ID."
  [world get-neighbors-fn pos army-unload-event-id]
  (first (filter (fn [neighbor]
                   (let [cell (get-in world neighbor)
                         unit (:contents cell)]
                     (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= :loading (:transport-mission unit))
                          (< (:army-count unit 0) 6)
                          (transport-compatible? unit army-unload-event-id))))
                 (get-neighbors-fn pos))))
