(ns empire.computer.army.assignment
  "Attack-target assignment for computer armies."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.army.assignment-decisions :as decisions]
            [empire.computer.land-objectives :as land-objectives]))

(defn- find-assignable-armies
  "Finds all computer armies eligible for city attack assignment (not coast-walking)."
  []
  (let [game-map (sa/current-world)]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :army (:type unit))
                     (not= :coast-walk (:mode unit)))]
      {:pos [i j] :unit unit})))

(defn- find-visible-target-cities
  "Finds free/player cities visible on the computer-map."
  []
  (let [comp-map (sa/read-state :computer-map)]
    (when (vector? comp-map)
      (for [i (range (count comp-map))
            j (range (count (first comp-map)))
            :let [cell (get-in comp-map [i j])]
            :when (and cell
                       (= :city (:type cell))
                       (#{:free :player} (:city-status cell)))]
        [i j]))))

(defn assign-city-attacks
  "Scans computer-map for visible free/player cities and assigns up to 6 closest armies each."
  []
  (let [cities (find-visible-target-cities)
        armies (find-assignable-armies)
        assignments (:assignments
                     (decisions/city-attack-assignments cities
                                                        armies
                                                        contains?
                                                        land-objectives/flood-fill-continent
                                                        core/distance))]
    (doseq [{:keys [pos target]} assignments]
      (sa/update-world! assoc-in (conj pos :contents :attack-target) target))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:53:15.107864-05:00", :module-hash "-982490196", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-72483662"} {:id "defn-/find-assignable-armies", :kind "defn-", :line 8, :end-line 20, :hash "-102020325"} {:id "defn-/find-visible-target-cities", :kind "defn-", :line 22, :end-line 33, :hash "-1927185487"} {:id "defn/assign-city-attacks", :kind "defn", :line 35, :end-line 47, :hash "2000790922"}]}
;; clj-mutate-manifest-end
