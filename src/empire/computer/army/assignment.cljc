(ns empire.computer.army.assignment
  "Attack-target assignment for computer armies."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
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
        assigned (atom #{})]
    (doseq [city cities]
        (let [city-continent (land-objectives/flood-fill-continent city)
            available (remove #(contains? @assigned (:pos %)) armies)
            reachable (filter #(contains? city-continent (:pos %)) available)
            closest (take 6 (sort-by #(core/distance (:pos %) city) reachable))]
        (doseq [{:keys [pos]} closest]
          (sa/update-world! assoc-in (conj pos :contents :attack-target) city)
          (swap! assigned conj pos))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:09.256671-05:00", :module-hash "1492647378", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1934577566"} {:id "defn-/find-assignable-armies", :kind "defn-", :line 7, :end-line 19, :hash "-102020325"} {:id "defn-/find-visible-target-cities", :kind "defn-", :line 21, :end-line 32, :hash "-1927185487"} {:id "defn/assign-city-attacks", :kind "defn", :line 34, :end-line 47, :hash "-1372810118"}]}
;; clj-mutate-manifest-end
