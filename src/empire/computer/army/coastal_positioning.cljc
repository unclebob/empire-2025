(ns empire.computer.army.coastal-positioning
  "Shared coastal positioning and embarkation target queries."
  (:require [empire.computer.ship.lake-naval :as lake-naval]
            [empire.computer.army.coastal-invasion :as invasion]
            [empire.computer.army.movement :as movement]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(declare find-coast-target-once)

(defn- adjacent-to-computer-city?
  "Returns true if position has an adjacent computer city."
  [pos]
  (some (fn [neighbor]
          (let [cell (get-in (sa/read-state :computer-map) neighbor)]
            (and (= :city (:type cell))
                 (= :computer (:city-status cell)))))
        (world-query/get-neighbors pos)))

(defn- known-lake-cells
  []
  (lake-naval/lake-cells (sa/read-state :computer-map)
                         (sa/read-state :lake-max-cells)))

(defn adjacent-to-ocean?
  [pos]
  (let [world (sa/read-state :computer-map)
        lakes (known-lake-cells)]
    (some (fn [neighbor]
            (let [cell (get-in world neighbor)]
              (and (= :sea (:type cell))
                   (not (contains? lakes neighbor)))))
          (world-query/get-neighbors pos))))

(defn find-nearest-unoccupied-coastal-cell
  "Finds nearest coastal cell from registry with matching country-id, no unit.
   Excludes cells adjacent to computer cities to avoid blocking production."
  [pos country-id]
  (when country-id
    (movement/ensure-coastal-registry country-id)
    (let [coastal (get (sa/read-state :coastal-cells-by-country) country-id)
          game-map (sa/read-state :computer-map)
          candidates (filter (fn [p]
                               (let [cell (get-in game-map p)]
                                 (and (= :land (:type cell))
                                      (adjacent-to-ocean? p)
                                      (or (nil? (:country-id cell))
                                          (= country-id (:country-id cell)))
                                      (nil? (:contents cell)))))
                             coastal)
          away-from-city (remove adjacent-to-computer-city? candidates)]
      (first (sort-by #(grid/distance pos %)
                      (if (seq away-from-city) away-from-city candidates))))))

(defn should-sentry-on-coast? [pos country-id]
  (and country-id
       (adjacent-to-ocean? pos)
       (not= :city (:type (get-in (sa/read-state :computer-map) pos)))
       (not (adjacent-to-computer-city? pos))))

(defn can-settle-here? [pos country-id]
  (and country-id
       (adjacent-to-ocean? pos)
       (not= :city (:type (get-in (sa/read-state :computer-map) pos)))))

(defn- invasion-ctx
  []
  {:current-world #(sa/read-state :computer-map)
   :update-game-map! sa/update-world!
   :read-runtime-state sa/read-state
   :sync-ai-unit! identity
   :adjacent-to-ocean? adjacent-to-ocean?
   :should-sentry-on-coast? should-sentry-on-coast?
   :find-coast-target-once find-coast-target-once})

(defn find-coast-target-once
  "One-time land-only BFS target selection for invasion embarkation."
  [start country-id]
  (invasion/find-coast-target-once (invasion-ctx) start country-id))
