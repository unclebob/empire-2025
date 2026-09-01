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

(defn- cached-positions
  [state-key]
  (or (sa/read-state state-key) #{}))

(defn- refueling-caches-empty?
  []
  (and (empty? (cached-positions :computer-city-positions))
       (empty? (cached-positions :computer-carrier-positions))))

(defn find-refueling-sites
  "Returns positions of all computer cities and computer carriers."
  []
  (when (refueling-caches-empty?)
    (sa/rebuild-refueling-caches!))
  (concat (cached-positions :computer-city-positions)
          (cached-positions :computer-carrier-positions)))

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

(defn- next-carrier-assignment
  [pos]
  (or (when-let [{:keys [position pair]} (find-carrier-position)]
        {:position position :pair pair :refueling :position})
      (when-let [{:keys [position pair]} (find-carrier-exploration-target pos)]
        {:position position :pair pair :refueling :explore})))

(defn- hold-carrier! [pos]
  (sa/update-world! update-in (conj pos :contents)
                    assoc :carrier-mode :holding)
  (visibility/sync-ai-unit-to-computer-map! pos))

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

(defn- arrive-at-carrier-target
  [pos unit]
  (if (= :explore (:refueling unit))
    (do
      (sa/update-world! update-in (conj pos :contents) dissoc :carrier-target)
      (position-carrier-without-target pos))
    (do
      (sa/update-world! update-in (conj pos :contents)
                        #(-> % (assoc :carrier-mode :holding) (dissoc :carrier-target)))
      (visibility/sync-ai-unit-to-computer-map! pos))))

(defn- position-carrier-with-target
  "Handles carrier in positioning mode that has a target."
  [pos target]
  (let [unit (computer-unit-at pos)]
    (cond
      (= pos target)
      (arrive-at-carrier-target pos unit)

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
  (if-let [{:keys [position pair refueling]} (next-carrier-assignment pos)]
    (assign-carrier-target-and-move pos position pair refueling)
    (hold-carrier! pos)))

(defn- reposition-carrier
  "Handles carrier in repositioning mode. Finds new position or holds."
  [pos]
  (if-let [{:keys [position pair refueling]} (next-carrier-assignment pos)]
    (do
      (sa/update-world! update-in (conj pos :contents)
                        assoc :carrier-mode :positioning)
      (visibility/sync-ai-unit-to-computer-map! pos)
      (assign-carrier-target-and-move pos position pair refueling))
    (hold-carrier! pos)))

(defn- pair-still-valid?
  "Returns true if both cities in the pair are still computer-owned."
  [pair]
  (let [game-map (sa/read-state :computer-map)]
    (every? (fn [pos]
              (let [cell (get-in game-map pos)]
                (and (= :city (:type cell))
                     (= :computer (:city-status cell)))))
            pair)))

(defn- process-carrier-positioning
  [pos unit]
  (if-let [target (:carrier-target unit)]
    (position-carrier-with-target pos target)
    (position-carrier-without-target pos)))

(defn- process-carrier-holding
  [pos unit]
  (let [pair (:carrier-pair unit)]
    (when-not (or (nil? pair) (pair-still-valid? pair))
      (sa/update-world! update-in (conj pos :contents)
                        #(-> % (assoc :carrier-mode :repositioning) (dissoc :carrier-pair)))
      (visibility/sync-ai-unit-to-computer-map! pos)
      nil)))

(defn process-carrier
  "Processes a computer carrier based on its carrier-mode."
  [pos]
  (let [unit (computer-unit-at pos)]
    (case (:carrier-mode unit)
      :positioning (process-carrier-positioning pos unit)
      :holding (process-carrier-holding pos unit)
      :repositioning (reposition-carrier pos)
      nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:34:59.14189-05:00", :module-hash "-1046377840", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-1973393762"} {:id "defn-/computer-unit-at", :kind "defn-", :line 12, :end-line nil, :hash "-1795563674"} {:id "def/carrier-position-cache", :kind "def", :line 16, :end-line nil, :hash "-1533895491"} {:id "defn/clear-carrier-caches!", :kind "defn", :line 18, :end-line nil, :hash "-1478414295"} {:id "defn-/find-computer-cities", :kind "defn-", :line 21, :end-line nil, :hash "246426453"} {:id "defn/compute-distant-city-pairs", :kind "defn", :line 32, :end-line nil, :hash "2090717578"} {:id "defn/update-distant-city-pairs!", :kind "defn", :line 44, :end-line nil, :hash "-1394890960"} {:id "defn/find-reserved-pairs", :kind "defn", :line 49, :end-line nil, :hash "541218213"} {:id "defn/find-unreserved-pair", :kind "defn", :line 63, :end-line nil, :hash "100655481"} {:id "defn/find-position-between-cities", :kind "defn", :line 74, :end-line nil, :hash "-388175718"} {:id "defn-/frontier-sea-target", :kind "defn-", :line 96, :end-line nil, :hash "1312077096"} {:id "defn-/cached-positions", :kind "defn-", :line 127, :end-line nil, :hash "1175254664"} {:id "defn-/refueling-caches-empty?", :kind "defn-", :line 131, :end-line nil, :hash "1633314233"} {:id "defn/find-refueling-sites", :kind "defn", :line 136, :end-line nil, :hash "1693846021"} {:id "defn-/compute-carrier-position", :kind "defn-", :line 144, :end-line nil, :hash "-161309352"} {:id "defn/find-carrier-position", :kind "defn", :line 149, :end-line nil, :hash "88505382"} {:id "defn-/find-carrier-exploration-target", :kind "defn-", :line 159, :end-line nil, :hash "650850915"} {:id "defn-/next-carrier-assignment", :kind "defn-", :line 166, :end-line nil, :hash "915181879"} {:id "defn-/hold-carrier!", :kind "defn-", :line 173, :end-line nil, :hash "1032603825"} {:id "form/19/declare", :kind "declare", :line 178, :end-line nil, :hash "1504557565"} {:id "defn-/assign-carrier-target-and-move", :kind "defn-", :line 180, :end-line nil, :hash "-1486377626"} {:id "defn-/target-still-valid?", :kind "defn-", :line 194, :end-line nil, :hash "-1803899194"} {:id "defn-/arrive-at-carrier-target", :kind "defn-", :line 201, :end-line nil, :hash "-912031354"} {:id "defn-/position-carrier-with-target", :kind "defn-", :line 212, :end-line nil, :hash "-1237325648"} {:id "defn-/position-carrier-without-target", :kind "defn-", :line 236, :end-line nil, :hash "1272730292"} {:id "defn-/reposition-carrier", :kind "defn-", :line 243, :end-line nil, :hash "1045270937"} {:id "defn-/pair-still-valid?", :kind "defn-", :line 254, :end-line nil, :hash "-1538645015"} {:id "defn-/process-carrier-positioning", :kind "defn-", :line 264, :end-line nil, :hash "567348370"} {:id "defn-/process-carrier-holding", :kind "defn-", :line 270, :end-line nil, :hash "-1961322609"} {:id "defn/process-carrier", :kind "defn", :line 279, :end-line nil, :hash "278509496"}]}
;; clj-mutate-manifest-end
