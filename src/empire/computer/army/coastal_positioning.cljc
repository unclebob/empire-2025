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
  (let [cached-cities (or (sa/read-state :computer-city-positions) #{})]
    (if (seq cached-cities)
      (some cached-cities (world-query/get-neighbors pos))
      (some (fn [neighbor]
              (let [cell (get-in (sa/read-state :computer-map) neighbor)]
                (and (= :city (:type cell))
                     (= :computer (:city-status cell)))))
            (world-query/get-neighbors pos)))))

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

(defn- coastal-cell-for-country?
  [pos country-id]
  (when country-id
    (movement/ensure-coastal-registry country-id)
    (let [computer-map (sa/read-state :computer-map)
          cached-coastal (get (sa/read-state :coastal-cells-by-country) country-id #{})
          cell (get-in computer-map pos)]
      (or (and (contains? cached-coastal pos)
               (adjacent-to-ocean? pos))
          (and (= :land (:type cell))
               (or (nil? (:country-id cell))
                   (= country-id (:country-id cell)))
               (adjacent-to-ocean? pos))))))

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
       (coastal-cell-for-country? pos country-id)
       (not= :city (:type (get-in (sa/read-state :computer-map) pos)))
       (not (adjacent-to-computer-city? pos))))

(defn can-settle-here? [pos country-id]
  (and country-id
       (coastal-cell-for-country? pos country-id)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:25:25.081199-05:00", :module-hash "764694871", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-2139048253"} {:id "form/1/declare", :kind "declare", :line 10, :end-line 10, :hash "1944609974"} {:id "defn-/adjacent-to-computer-city?", :kind "defn-", :line 12, :end-line 22, :hash "-2103982590"} {:id "defn-/known-lake-cells", :kind "defn-", :line 24, :end-line 27, :hash "-816529896"} {:id "defn/adjacent-to-ocean?", :kind "defn", :line 29, :end-line 37, :hash "1188595514"} {:id "defn-/coastal-cell-for-country?", :kind "defn-", :line 39, :end-line 51, :hash "50964835"} {:id "defn/find-nearest-unoccupied-coastal-cell", :kind "defn", :line 53, :end-line 71, :hash "1045570962"} {:id "defn/should-sentry-on-coast?", :kind "defn", :line 73, :end-line 77, :hash "-1124014177"} {:id "defn/can-settle-here?", :kind "defn", :line 79, :end-line 82, :hash "-43219301"} {:id "defn-/invasion-ctx", :kind "defn-", :line 84, :end-line 92, :hash "1148739429"} {:id "defn/find-coast-target-once", :kind "defn", :line 94, :end-line 97, :hash "126024350"}]}
;; clj-mutate-manifest-end
