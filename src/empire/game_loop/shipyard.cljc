(ns empire.game-loop.shipyard
  "Ship repair in city shipyards.

   Damaged ships dock at friendly cities and are repaired 1 hit per round.
   Fully repaired ships are launched back onto the map."
  (:require [empire.atoms :as atoms]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]))

(defn- repair-city-ships
  "Repairs all ships in a city's shipyard by 1 hit each.
   Launches fully repaired ships if the city cell is empty.
   Returns indices of ships that were launched (in reverse order for safe removal)."
  [city-coords]
  (let [cell (get-in @atoms/game-map city-coords)
        shipyard (uc/get-shipyard-ships cell)]
    (when (seq shipyard)
      ;; First, repair all ships
      (let [repaired-ships (mapv uc/repair-ship shipyard)]
        (swap! atoms/game-map assoc-in (conj city-coords :shipyard) repaired-ships))
      ;; Then, launch fully repaired ships if city is empty
      ;; Process from end to avoid index shifting issues
      (let [updated-cell (get-in @atoms/game-map city-coords)
            updated-shipyard (uc/get-shipyard-ships updated-cell)]
        (doseq [i (reverse (range (count updated-shipyard)))]
          (let [current-cell (get-in @atoms/game-map city-coords)
                ship (get-in current-cell [:shipyard i])]
            (when (and (uc/ship-fully-repaired? ship)
                       (nil? (:contents current-cell)))
              (container-ops/launch-ship-from-shipyard city-coords i))))))))

(defn repair-damaged-ships
  "Repairs ships in all friendly city shipyards by 1 hit per round.
   Launches fully repaired ships onto the map if the city cell is empty."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])]
          :when (and (= (:type cell) :city)
                     (#{:player :computer} (:city-status cell))
                     (seq (uc/get-shipyard-ships cell)))]
    (repair-city-ships [i j])))
