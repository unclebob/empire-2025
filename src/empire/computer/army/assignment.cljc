(ns empire.computer.army.assignment
  "Attack-target assignment for computer armies."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]))

(defn- find-assignable-armies
  "Finds all computer armies eligible for city attack assignment (not coast-walking)."
  []
  (let [game-map @atoms/game-map]
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
  (let [comp-map @atoms/computer-map]
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
          (swap! atoms/game-map assoc-in (conj pos :contents :attack-target) city)
          (swap! assigned conj pos))))))
