(ns empire.game.loop.round-setup.repair
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game.loop.round-setup.lakes :as lakes]))

(defn- repair-city-ships
  "Repairs all ships in a city's shipyard by 1 hit each.
   Launches fully repaired ships to adjacent sea when possible."
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
              (let [launch-pos (or (lakes/find-adjacent-empty-sea city-coords)
                                   (when (nil? (:contents current-cell))
                                     city-coords))]
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:03:42.433894-05:00", :module-hash "-1017178576", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-740500162"} {:id "defn-/repair-city-ships", :kind "defn-", :line 7, :end-line 29, :hash "1023429212"} {:id "defn/repair-damaged-ships", :kind "defn", :line 31, :end-line 42, :hash "-752989877"}]}
;; clj-mutate-manifest-end
