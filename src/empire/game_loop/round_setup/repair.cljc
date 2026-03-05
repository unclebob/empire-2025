(ns empire.game-loop.round-setup.repair
  (:require [empire.application.state-access :as sa]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.game-loop.round-setup.lakes :as lakes]))

(defn- repair-city-ships
  "Repairs all ships in a city's shipyard by 1 hit each.
   Launches fully repaired ships to city cell or adjacent sea."
  [city-coords]
  (let [cell (get-in (sa/current-world) city-coords)
        shipyard (uc/get-shipyard-ships cell)]
    (when (seq shipyard)
      ;; First, repair all ships
      (let [repaired-ships (mapv uc/repair-ship shipyard)]
        (sa/update-world! assoc-in (conj city-coords :shipyard) repaired-ships))
      ;; Then, launch fully repaired ships
      ;; Process from end to avoid index shifting issues
      (let [updated-cell (get-in (sa/current-world) city-coords)
            updated-shipyard (uc/get-shipyard-ships updated-cell)]
        (doseq [i (reverse (range (count updated-shipyard)))]
          (let [current-cell (get-in (sa/current-world) city-coords)
                ship (get-in current-cell [:shipyard i])]
            (when (uc/ship-fully-repaired? ship)
              (let [launch-pos (if (nil? (:contents current-cell))
                                 city-coords
                                 (lakes/find-adjacent-empty-sea city-coords))]
                (when launch-pos
                  (container-ops/launch-ship-from-shipyard city-coords i launch-pos))))))))))

(defn repair-damaged-ships
  "Repairs ships in all friendly city shipyards by 1 hit per round.
   Launches fully repaired ships onto the map if the city cell is empty."
  []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])]
            :when (and (= (:type cell) :city)
                       (#{:player :computer} (:city-status cell))
                       (seq (uc/get-shipyard-ships cell)))]
      (repair-city-ships [i j]))))
