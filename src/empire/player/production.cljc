(ns empire.player.production
  (:require [empire.game-mechanics.services.city-production :as city-production]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.game-mechanics.services.unit-stamping :as unit-stamping]
            [empire.config.core :as config]))

(defn- stamp-computer-fields
  [unit cell]
  (unit-stamping/stamp-computer-fields unit cell))

(defn- apply-coast-walk-fields
  [unit item cell coords]
  (unit-stamping/apply-coast-walk-fields unit item cell coords))

(defn- apply-random-explore-fields
  [unit item cell coords]
  (unit-stamping/apply-random-explore-fields unit item cell coords))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (city-production/set-city-production coords item))

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

(defn- army-with-lookaround? [item marching-orders]
  (and (= item :army) (= marching-orders :lookaround)))

(defn- army-with-marching-orders? [item marching-orders]
  (and (= item :army) marching-orders))

(defn- fighter-with-flight-path? [item flight-path]
  (and (= item :fighter) flight-path))

(defn- apply-movement-orders
  "Applies marching orders or flight path to unit."
  [unit item marching-orders flight-path]
  (cond
    (army-with-lookaround? item marching-orders)
    (assoc unit :mode :explore :explore-steps 50)

    (army-with-marching-orders? item marching-orders)
    (assoc unit :mode :moving :target marching-orders)

    (fighter-with-flight-path? item flight-path)
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
      (stamp-computer-fields cell)))

(defn- stamp-adjacent-land
  "Stamps country-id on land cells adjacent to a city when a computer army spawns."
  [coords country-id]
  (let [game-map (sa/current-world)
        neighbors (map-utils/get-matching-neighbors coords game-map
                                                            map-utils/neighbor-offsets some?)]
    (doseq [n neighbors]
      (let [cell (get-in game-map n)]
        (when (and (= :land (:type cell)) (nil? (:country-id cell)))
          (sa/update-world! assoc-in (conj n :country-id) country-id))))))

(defn- spawn-unit
  "Creates and places a unit at the given city coordinates."
  [coords cell item]
  (let [owner (:city-status cell)
        marching-orders (:marching-orders cell)
        flight-path (:flight-path cell)
        unit (-> (create-base-unit item owner)
                 (stamp-unit-fields cell)
                 (apply-coast-walk-fields item cell coords)
                 (apply-random-explore-fields item cell coords)
                 (apply-movement-orders item marching-orders flight-path)
                 (cond-> (= item :transport) (assoc :produced-at coords)))]
    (sa/update-world! assoc-in (conj coords :contents) unit)
    (when (and (= owner :computer) (= item :army) (:country-id cell))
      (stamp-adjacent-land coords (:country-id cell)))
    (when (and (= owner :computer) (= item :carrier))
      (sa/update-state! :computer-carrier-positions conj coords))
    owner))

(defn- handle-production-complete
  "Handles production completion: spawns unit and updates production state."
  [coords prod item]
  (let [cell (get-in (sa/current-world) coords)
        owner (spawn-unit coords cell item)]
    (if (= owner :computer)
      (sa/update-state! :production dissoc coords)
      (sa/update-state! :production assoc coords
                             (assoc prod :remaining-rounds (config/item-cost item))))))

(defn- update-city-production
  "Updates production for a single city."
  [coords prod]
  (let [cell (get-in (sa/current-world) coords)]
    (when-not (:contents cell)
      (let [item (:item prod)
            remaining (dec (:remaining-rounds prod))]
        (if (zero? remaining)
          (handle-production-complete coords prod item)
          (sa/update-state! :production assoc coords
                                 (assoc prod :remaining-rounds remaining)))))))

(defn update-production
  "Updates production for all cities by decrementing remaining rounds."
  []
  (doseq [[coords prod] (sa/read-state :production)]
    (when (map? prod)
      (update-city-production coords prod))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T15:26:11.782437-05:00", :module-hash "455477232", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1032698653"} {:id "defn-/stamp-computer-fields", :kind "defn-", :line 8, :end-line 10, :hash "1182657874"} {:id "defn-/apply-coast-walk-fields", :kind "defn-", :line 12, :end-line 14, :hash "2087648991"} {:id "defn-/apply-random-explore-fields", :kind "defn-", :line 16, :end-line 18, :hash "531005707"} {:id "defn/set-city-production", :kind "defn", :line 20, :end-line 23, :hash "781392752"} {:id "defn-/create-base-unit", :kind "defn-", :line 25, :end-line 28, :hash "466336624"} {:id "defn-/apply-unit-type-attributes", :kind "defn-", :line 30, :end-line 38, :hash "-327359930"} {:id "defn-/army-with-lookaround?", :kind "defn-", :line 40, :end-line 41, :hash "1805255192"} {:id "defn-/army-with-marching-orders?", :kind "defn-", :line 43, :end-line 44, :hash "939802950"} {:id "defn-/fighter-with-flight-path?", :kind "defn-", :line 46, :end-line 47, :hash "2030388680"} {:id "defn-/apply-movement-orders", :kind "defn-", :line 49, :end-line 62, :hash "-1706259954"} {:id "defn/stamp-unit-fields", :kind "defn", :line 64, :end-line 72, :hash "532239762"} {:id "defn-/stamp-adjacent-land", :kind "defn-", :line 74, :end-line 83, :hash "-2092654768"} {:id "defn-/spawn-unit", :kind "defn-", :line 85, :end-line 102, :hash "-1533249468"} {:id "defn-/handle-production-complete", :kind "defn-", :line 104, :end-line 112, :hash "1308681376"} {:id "defn-/update-city-production", :kind "defn-", :line 114, :end-line 124, :hash "1240282712"} {:id "defn/update-production", :kind "defn", :line 126, :end-line 131, :hash "1482439119"}]}
;; clj-mutate-manifest-end
