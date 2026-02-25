;; mutation-tested: 2026-02-25
(ns empire.player.production
  (:require [empire.atoms :as atoms]
            [empire.computer.stamping :as computer-stamping]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (swap! atoms/production assoc coords {:item item :remaining-rounds (config/item-cost item)}))

(defn- create-base-unit
  "Creates a base unit with type, hits, mode, and owner."
  [item owner]
  {:type item :hits (config/item-hits item) :mode :awake :owner owner})

(defn- apply-unit-type-attributes
  "Adds type-specific attributes (fuel, turns)."
  [unit item]
  (cond-> unit
    (= item :fighter)
    (assoc :fuel config/fighter-fuel)

    (= item :satellite)
    (assoc :turns-remaining config/satellite-turns)))

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

(defn stamp-unit-fields
  "Applies all type-specific fields to a unit based on its :type and :owner.
   Used by spawn-unit and launch-ship-from-shipyard to ensure computer ships
   get their required fields (carrier-mode, escort-id, etc.).
   cell is the city cell (needed for country-id / patrol fields); may be nil."
  [unit cell]
  (-> unit
      (apply-unit-type-attributes (:type unit))
      (computer-stamping/stamp-computer-fields cell)))

(defn- stamp-adjacent-land
  "Stamps country-id on land cells adjacent to a city when a computer army spawns."
  [coords country-id]
  (let [game-map @atoms/game-map
        neighbors (map-utils/get-matching-neighbors coords game-map
                                                    map-utils/neighbor-offsets some?)]
    (doseq [n neighbors]
      (let [cell (get-in game-map n)]
        (when (and (= :land (:type cell)) (nil? (:country-id cell)))
          (swap! atoms/game-map assoc-in (conj n :country-id) country-id))))))

(defn- spawn-unit
  "Creates and places a unit at the given city coordinates."
  [coords cell item]
  (let [owner (:city-status cell)
        marching-orders (:marching-orders cell)
        flight-path (:flight-path cell)
        unit (-> (create-base-unit item owner)
                 (stamp-unit-fields cell)
                 (computer-stamping/apply-coast-walk-fields item cell coords)
                 (computer-stamping/apply-random-explore-fields item cell)
                 (apply-movement-orders item marching-orders flight-path)
                 (cond-> (= item :transport) (assoc :produced-at coords)))]
    (swap! atoms/game-map assoc-in (conj coords :contents) unit)
    (when (and (= owner :computer) (= item :army) (:country-id cell))
      (stamp-adjacent-land coords (:country-id cell)))
    owner))

(defn- handle-production-complete
  "Handles production completion: spawns unit and updates production state."
  [coords prod item]
  (let [cell (get-in @atoms/game-map coords)
        owner (spawn-unit coords cell item)]
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
