(ns empire.player.production
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.debug :as debug]))

(defn- find-beach-order-for-city
  "Returns [beach-pos order] if city has a beach order, nil otherwise."
  [city-pos]
  (first (filter (fn [[_ order]] (= city-pos (:city-pos order)))
                 @atoms/beach-army-orders)))

(defn- apply-beach-order-to-army
  "If city has a beach order, sets army target/mission and decrements order.
   Returns the updated unit."
  [unit city-pos]
  (if-let [[beach-pos order] (find-beach-order-for-city city-pos)]
    (do
      (let [new-remaining (dec (:remaining order))]
        (if (zero? new-remaining)
          (swap! atoms/beach-army-orders dissoc beach-pos)
          (swap! atoms/beach-army-orders assoc beach-pos
                 (assoc order :remaining new-remaining))))
      (assoc unit :target beach-pos :mission :loading))
    unit))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (swap! atoms/production assoc coords {:item item :remaining-rounds (config/item-cost item)}))

(defn- create-base-unit
  "Creates a base unit with type, hits, mode, and owner."
  [item owner]
  {:type item :hits (config/item-hits item) :mode :awake :owner owner})

(defn- apply-unit-type-attributes
  "Adds type-specific attributes (fuel, turns, transport mission, transport id)."
  [unit item owner]
  (cond-> unit
    (= item :fighter)
    (assoc :fuel config/fighter-fuel)

    (= item :satellite)
    (assoc :turns-remaining config/satellite-turns)

    (= item :transport)
    (assoc :transport-mission :idle :origin-beach nil)

    (and (= item :transport) (= owner :computer))
    (assoc :transport-id (let [id @atoms/next-transport-id]
                           (swap! atoms/next-transport-id inc)
                           id))))

(defn- apply-movement-orders
  "Applies marching orders or flight path to unit."
  [unit item marching-orders flight-path]
  (cond
    (and (= item :army) (= marching-orders :lookaround))
    (assoc unit :mode :explore :explore-steps 50)

    (and (= item :army) marching-orders)
    (assoc unit :mode :moving :target marching-orders)

    (and (= item :fighter) flight-path)
    (assoc unit :mode :moving :target flight-path)

    :else unit))

(defn- apply-computer-army-beach-order
  "Applies beach order to computer armies."
  [unit item owner coords]
  (if (and (= item :army) (= owner :computer))
    (apply-beach-order-to-army unit coords)
    unit))

(defn- spawn-unit
  "Creates and places a unit at the given city coordinates."
  [coords cell item]
  (let [owner (:city-status cell)
        marching-orders (:marching-orders cell)
        flight-path (:flight-path cell)
        unit (-> (create-base-unit item owner)
                 (apply-unit-type-attributes item owner)
                 (apply-movement-orders item marching-orders flight-path)
                 (apply-computer-army-beach-order item owner coords))]
    (swap! atoms/game-map assoc-in (conj coords :contents) unit)
    owner))

(defn- handle-production-complete
  "Handles production completion: spawns unit and updates production state."
  [coords prod item]
  (let [cell (get-in @atoms/game-map coords)
        owner (spawn-unit coords cell item)]
    (debug/log-action! [:production-complete coords item owner])
    (if (= owner :computer)
      (swap! atoms/production dissoc coords)
      (swap! atoms/production assoc coords
             (assoc prod :remaining-rounds (config/item-cost item))))))

(defn- update-city-production
  "Updates production for a single city."
  [coords prod]
  (let [cell (get-in @atoms/game-map coords)]
    (when-not (:contents cell)
      (let [item (:item prod)
            remaining (dec (:remaining-rounds prod))]
        (if (zero? remaining)
          (handle-production-complete coords prod item)
          (swap! atoms/production assoc coords
                 (assoc prod :remaining-rounds remaining)))))))

(defn update-production
  "Updates production for all cities by decrementing remaining rounds."
  []
  (doseq [[coords prod] @atoms/production]
    (when (map? prod)
      (update-city-production coords prod))))
