(ns empire.player.production
  (:require [empire.game-mechanics.services.city-production :as city-production]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.game-mechanics.unit-stamping :as unit-stamping]
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
  (let [owner (:city-status cell)]
    (if (= item :fighter)
      (sa/update-world! update-in (conj coords :fighter-count) (fnil inc 0))
      (let [marching-orders (:marching-orders cell)
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
          (sa/update-state! :computer-carrier-positions conj coords))))
    owner))

(defn- handle-production-complete
  "Handles production completion: spawns unit and updates production state."
  [coords prod item]
  (let [cell (get-in (sa/current-world) coords)
        owner (spawn-unit coords cell item)
        decision (decisions/production-complete-action owner coords prod)]
    (case (:action decision)
      :clear-production
      (sa/update-state! :production dissoc coords)

      :reset-production
      (sa/update-state! :production assoc coords (:production decision))
      nil)))

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
;; {:version 1, :tested-at "2026-03-27T02:29:39.661819-05:00", :module-hash "1768183939", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "130223524"} {:id "defn-/stamp-computer-fields", :kind "defn-", :line 8, :end-line 10, :hash "1182657874"} {:id "defn-/apply-coast-walk-fields", :kind "defn-", :line 12, :end-line 14, :hash "2087648991"} {:id "defn-/apply-random-explore-fields", :kind "defn-", :line 16, :end-line 18, :hash "531005707"} {:id "defn/set-city-production", :kind "defn", :line 20, :end-line 23, :hash "781392752"} {:id "defn/stamp-unit-fields", :kind "defn", :line 25, :end-line 33, :hash "-1071172957"} {:id "defn-/stamp-adjacent-land", :kind "defn-", :line 35, :end-line 44, :hash "-2092654768"} {:id "defn-/spawn-unit", :kind "defn-", :line 46, :end-line 62, :hash "954807850"} {:id "defn-/handle-production-complete", :kind "defn-", :line 64, :end-line 76, :hash "369126627"} {:id "defn-/update-city-production", :kind "defn-", :line 78, :end-line 87, :hash "394048806"} {:id "defn/update-production", :kind "defn", :line 89, :end-line 94, :hash "1482439119"}]}
;; clj-mutate-manifest-end
