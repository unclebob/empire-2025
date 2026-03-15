(ns empire.player.production
  (:require [empire.game-mechanics.services.city-production :as city-production]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.game-mechanics.services.unit-stamping :as unit-stamping]
            [empire.config.core :as config]
            [empire.player.production-decisions :as decisions]))

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

(defn stamp-unit-fields
  "Applies all type-specific fields to a unit based on its :type and :owner.
   Used by spawn-unit and launch-ship-from-shipyard to ensure computer ships
   get their required fields (carrier-mode, escort-id, etc.).
   cell is the city cell (needed for country-id / patrol fields); may be nil."
  [unit cell]
  (-> unit
      (decisions/apply-unit-type-attributes (:type unit))
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
        unit (-> (decisions/build-produced-unit item owner marching-orders flight-path)
                 (stamp-unit-fields cell)
                 (apply-coast-walk-fields item cell coords)
                 (apply-random-explore-fields item cell coords)
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
    (let [decision (decisions/city-production-step cell prod)]
      (case (:action decision)
      :blocked nil
      :complete (handle-production-complete coords prod (:item prod))
      :decrement (sa/update-state! :production assoc coords (:production decision))
      nil))))

(defn update-production
  "Updates production for all cities by decrementing remaining rounds."
  []
  (doseq [[coords prod] (sa/read-state :production)]
    (when (map? prod)
      (update-city-production coords prod))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T15:52:27.980175-05:00", :module-hash "-56941808", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "552785745"} {:id "defn-/stamp-computer-fields", :kind "defn-", :line 9, :end-line 11, :hash "1182657874"} {:id "defn-/apply-coast-walk-fields", :kind "defn-", :line 13, :end-line 15, :hash "2087648991"} {:id "defn-/apply-random-explore-fields", :kind "defn-", :line 17, :end-line 19, :hash "531005707"} {:id "defn/set-city-production", :kind "defn", :line 21, :end-line 24, :hash "781392752"} {:id "defn/stamp-unit-fields", :kind "defn", :line 26, :end-line 34, :hash "-1071172957"} {:id "defn-/stamp-adjacent-land", :kind "defn-", :line 36, :end-line 45, :hash "-2092654768"} {:id "defn-/spawn-unit", :kind "defn-", :line 47, :end-line 63, :hash "954807850"} {:id "defn-/handle-production-complete", :kind "defn-", :line 65, :end-line 73, :hash "1308681376"} {:id "defn-/update-city-production", :kind "defn-", :line 75, :end-line 84, :hash "394048806"} {:id "defn/update-production", :kind "defn", :line 86, :end-line 91, :hash "1482439119"}]}
;; clj-mutate-manifest-end
