(ns empire.computer.ship.carrier
  "Computer carrier positioning - finding and navigating to holding positions."
  (:require [clojure.set :as set]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.visibility :as visibility]))

(defn- computer-unit-at
  [pos]
  (:contents (get-in (sa/read-state :computer-map) pos)))

(def ^:private carrier-position-cache (atom ::unset))

(defn clear-carrier-caches! []
  (reset! carrier-position-cache ::unset))

(defn- find-computer-cities
  "Returns positions of all computer cities."
  []
  (let [game-map (sa/read-state :computer-map)]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])]
          :when (and (= :city (:type cell))
                     (= :computer (:city-status cell)))]
      [i j])))

(defn compute-distant-city-pairs
  "Returns set of computer city pairs where distance > fighter-fuel.
   Each pair is a set of two positions #{[r1 c1] [r2 c2]}."
  []
  (let [cities (vec (find-computer-cities))]
    (set (for [i (range (count cities))
               j (range (inc i) (count cities))
               :let [a (nth cities i)
                     b (nth cities j)]
               :when (> (grid/distance a b) config/fighter-fuel)]
           #{a b}))))

(defn update-distant-city-pairs!
  "Updates the distant-city-pairs atom from current game map."
  []
  (sa/write-state! :distant-city-pairs (compute-distant-city-pairs)))

(defn find-reserved-pairs
  "Returns set of city pairs already assigned to computer carriers.
   Includes carriers in :positioning and :holding modes."
  []
  (let [game-map (sa/read-state :computer-map)]
    (set (for [i (range (count game-map))
               j (range (count (first game-map)))
               :let [unit (get-in game-map [i j :contents])]
               :when (and (= :carrier (:type unit))
                          (= :computer (:owner unit))
                          (#{:positioning :holding} (:carrier-mode unit))
                          (:carrier-pair unit))]
           (:carrier-pair unit)))))

(defn find-unreserved-pair
  "Returns a city pair that needs a carrier but has none assigned.
   Returns nil if all distant pairs have carriers or no distant pairs exist."
  []
  (when (nil? (sa/read-state :distant-city-pairs))
    (update-distant-city-pairs!))
  (let [distant-pairs (or (sa/read-state :distant-city-pairs) #{})
        reserved-pairs (find-reserved-pairs)
        unreserved (set/difference distant-pairs reserved-pairs)]
    (first unreserved)))

(defn find-position-between-cities
  "Finds a sea position between two cities where a carrier can refuel fighters.
   Returns a position within fighter-fuel distance of both cities, closest to midpoint.
   Returns nil if no such position exists."
  [city-pair]
  (let [[city1 city2] (vec city-pair)
        midpoint [(quot (+ (first city1) (first city2)) 2)
                  (quot (+ (second city1) (second city2)) 2)]
        computer-map (sa/read-state :computer-map)
        cols (count computer-map)
        rows (count (first computer-map))
        candidates (for [i (range cols)
                         j (range rows)
                         :let [cell (get-in computer-map [i j])]
                         :when (and (= :sea (:type cell))
                                    (nil? (:contents cell))
                                    (<= (grid/distance [i j] city1) config/fighter-fuel)
                                    (<= (grid/distance [i j] city2) config/fighter-fuel))]
                     [i j])]
    (when (seq candidates)
      (apply min-key #(grid/distance % midpoint) candidates))))

(defn- frontier-sea-target
  "Finds a visible sea frontier near the midpoint of a city pair.
   Used when no revealed refueling midpoint exists yet."
  [city-pair current-pos]
  (let [[city1 city2] (vec city-pair)
        midpoint [(quot (+ (first city1) (first city2)) 2)
                  (quot (+ (second city1) (second city2)) 2)]
        computer-map (sa/read-state :computer-map)
        cols (count computer-map)
        rows (count (first computer-map))
        hidden-neighbor? (fn [pos]
                           (some (fn [neighbor]
                                   (let [cell (get-in computer-map neighbor)]
                                     (or (nil? cell)
                                         (= :unexplored (:type cell)))))
                                 (world-query/get-neighbors pos)))
        candidates (for [i (range cols)
                         j (range rows)
                         :let [pos [i j]
                               cell (get-in computer-map pos)]
                         :when (and (= :sea (:type cell))
                                    (nil? (:contents cell))
                                    (not= pos current-pos)
                                    (hidden-neighbor? pos))]
                     pos)]
    (when (seq candidates)
      (apply min-key #(+ (* 1000 (grid/distance % midpoint))
                         (grid/distance % city1)
                         (grid/distance % city2))
             candidates))))

(defn find-refueling-sites
  "Returns positions of all computer cities and computer carriers."
  []
  (when (and (empty? (or (sa/read-state :computer-city-positions) #{}))
             (empty? (or (sa/read-state :computer-carrier-positions) #{})))
    (sa/rebuild-refueling-caches!))

  (concat (or (sa/read-state :computer-city-positions) #{})
          (or (sa/read-state :computer-carrier-positions) #{})))

(defn- compute-carrier-position []
  (when-let [pair (find-unreserved-pair)]
    (when-let [pos (find-position-between-cities pair)]
      {:position pos :pair pair})))

(defn find-carrier-position
  "Finds a carrier position for an unreserved city pair.
   Returns {:position pos :pair city-pair} or nil if no pair needs a carrier."
  []
  (if (= ::unset @carrier-position-cache)
    (let [result (compute-carrier-position)]
      (reset! carrier-position-cache result)
      result)
    @carrier-position-cache))

(defn- find-carrier-exploration-target
  "Finds a frontier sea target for a known city pair when the midpoint is still unrevealed."
  [current-pos]
  (when-let [pair (find-unreserved-pair)]
    (when-let [pos (frontier-sea-target pair current-pos)]
      {:position pos :pair pair})))

(declare position-carrier-without-target)

(defn- assign-carrier-target-and-move
  [pos position pair refueling]
  (sa/update-world! update-in (conj pos :contents)
                    assoc :carrier-target position :carrier-pair pair :refueling refueling)
  (visibility/sync-ai-unit-to-computer-map! pos)
  (when-let [next-pos (computer-movement/next-step pos position :carrier)]
    (action-resolution/move-unit-to pos next-pos)
    (sa/update-state! :computer-carrier-positions disj pos)
    (sa/update-state! :computer-carrier-positions (fnil conj #{}) next-pos)
    (computer-movement/update-cell-visibility! pos :computer)
    (computer-movement/update-cell-visibility! next-pos :computer)
    (visibility/sync-ai-unit-to-computer-map! next-pos)
    next-pos))

(defn- target-still-valid?
  "Returns true if the carrier target is still a valid sea cell."
  [target]
  (let [cell (get-in (sa/read-state :computer-map) target)]
    (and (= :sea (:type cell))
         (nil? (:contents cell)))))

(defn- position-carrier-with-target
  "Handles carrier in positioning mode that has a target."
  [pos target]
  (let [unit (computer-unit-at pos)]
    (cond
      (= pos target)
      (if (= :explore (:refueling unit))
        (do
          (sa/update-world! update-in (conj pos :contents) dissoc :carrier-target)
          (position-carrier-without-target pos))
        (do
          (sa/update-world! update-in (conj pos :contents)
                            #(-> % (assoc :carrier-mode :holding) (dissoc :carrier-target)))
          (visibility/sync-ai-unit-to-computer-map! pos)))

      (not (target-still-valid? target))
      (do
        (sa/update-world! update-in (conj pos :contents) dissoc :carrier-target)
        (visibility/sync-ai-unit-to-computer-map! pos)
        (position-carrier-without-target pos))

      :else
      (when-let [next-pos (computer-movement/next-step pos target :carrier)]
        (action-resolution/move-unit-to pos next-pos)
        (sa/update-state! :computer-carrier-positions disj pos)
        (sa/update-state! :computer-carrier-positions (fnil conj #{}) next-pos)
        (computer-movement/update-cell-visibility! pos :computer)
        (computer-movement/update-cell-visibility! next-pos :computer)
        (visibility/sync-ai-unit-to-computer-map! next-pos)
        next-pos))))

(defn- position-carrier-without-target
  "Handles carrier in positioning mode without a target. Finds one or holds."
  [pos]
  (if-let [{:keys [position pair refueling]} (or (when-let [{:keys [position pair]} (find-carrier-position)]
                                                   {:position position :pair pair :refueling :position})
                                                 (when-let [{:keys [position pair]} (find-carrier-exploration-target pos)]
                                                   {:position position :pair pair :refueling :explore}))]
    (assign-carrier-target-and-move pos position pair refueling)
    (do
      (sa/update-world! update-in (conj pos :contents)
                        assoc :carrier-mode :holding)
      (visibility/sync-ai-unit-to-computer-map! pos))))

(defn- reposition-carrier
  "Handles carrier in repositioning mode. Finds new position or holds."
  [pos]
  (if-let [{:keys [position pair refueling]} (or (when-let [{:keys [position pair]} (find-carrier-position)]
                                                   {:position position :pair pair :refueling :position})
                                                 (when-let [{:keys [position pair]} (find-carrier-exploration-target pos)]
                                                   {:position position :pair pair :refueling :explore}))]
    (do
      (sa/update-world! update-in (conj pos :contents)
                        assoc :carrier-mode :positioning)
      (visibility/sync-ai-unit-to-computer-map! pos)
      (assign-carrier-target-and-move pos position pair refueling))
    (do
      (sa/update-world! update-in (conj pos :contents)
                        assoc :carrier-mode :holding)
      (visibility/sync-ai-unit-to-computer-map! pos))))

(defn- pair-still-valid?
  "Returns true if both cities in the pair are still computer-owned."
  [pair]
  (let [game-map (sa/read-state :computer-map)]
    (every? (fn [pos]
              (let [cell (get-in game-map pos)]
                (and (= :city (:type cell))
                     (= :computer (:city-status cell)))))
            pair)))

(defn process-carrier
  "Processes a computer carrier based on its carrier-mode."
  [pos]
  (let [unit (computer-unit-at pos)
        mode (:carrier-mode unit)]
    (case mode
      :positioning
      (let [target (:carrier-target unit)]
        (if target
          (position-carrier-with-target pos target)
          (position-carrier-without-target pos)))

      :holding
      (let [pair (:carrier-pair unit)]
        (if (or (nil? pair) (pair-still-valid? pair))
          nil
          (do (sa/update-world! update-in (conj pos :contents)
                                #(-> % (assoc :carrier-mode :repositioning) (dissoc :carrier-pair)))
              (visibility/sync-ai-unit-to-computer-map! pos)
              nil)))

      :repositioning (reposition-carrier pos)

      nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T21:37:48.924317-05:00", :module-hash "1897242990", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "-1973393762"} {:id "defn-/computer-unit-at", :kind "defn-", :line 12, :end-line 14, :hash "-1795563674"} {:id "def/carrier-position-cache", :kind "def", :line 16, :end-line 16, :hash "-2064982932"} {:id "defn/clear-carrier-caches!", :kind "defn", :line 18, :end-line 19, :hash "1606368289"} {:id "defn-/find-computer-cities", :kind "defn-", :line 21, :end-line 30, :hash "246426453"} {:id "defn/compute-distant-city-pairs", :kind "defn", :line 32, :end-line 42, :hash "2090717578"} {:id "defn/update-distant-city-pairs!", :kind "defn", :line 44, :end-line 47, :hash "-1394890960"} {:id "defn/find-reserved-pairs", :kind "defn", :line 49, :end-line 61, :hash "541218213"} {:id "defn/find-unreserved-pair", :kind "defn", :line 63, :end-line 72, :hash "100655481"} {:id "defn/find-position-between-cities", :kind "defn", :line 74, :end-line 94, :hash "235474931"} {:id "defn-/frontier-sea-target", :kind "defn-", :line 96, :end-line 125, :hash "777658136"} {:id "defn/find-refueling-sites", :kind "defn", :line 127, :end-line 135, :hash "-668032173"} {:id "defn-/compute-carrier-position", :kind "defn-", :line 137, :end-line 140, :hash "-161309352"} {:id "defn/find-carrier-position", :kind "defn", :line 142, :end-line 150, :hash "-1061749396"} {:id "defn-/find-carrier-exploration-target", :kind "defn-", :line 152, :end-line 157, :hash "650850915"} {:id "form/15/declare", :kind "declare", :line 159, :end-line 159, :hash "1504557565"} {:id "defn-/assign-carrier-target-and-move", :kind "defn-", :line 161, :end-line 173, :hash "-1486377626"} {:id "defn-/target-still-valid?", :kind "defn-", :line 175, :end-line 180, :hash "-1803899194"} {:id "defn-/position-carrier-with-target", :kind "defn-", :line 182, :end-line 211, :hash "1819739286"} {:id "defn-/position-carrier-without-target", :kind "defn-", :line 213, :end-line 224, :hash "-2003185995"} {:id "defn-/reposition-carrier", :kind "defn-", :line 226, :end-line 241, :hash "-446269813"} {:id "defn-/pair-still-valid?", :kind "defn-", :line 243, :end-line 251, :hash "-1538645015"} {:id "defn/process-carrier", :kind "defn", :line 253, :end-line 276, :hash "1759784082"}]}
;; clj-mutate-manifest-end
