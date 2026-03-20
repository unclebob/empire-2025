(ns empire.computer.ship-carrier
  "Computer carrier positioning - finding and navigating to holding positions."
  (:require [clojure.set :as set]
            [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.config.core :as config]
            [empire.computer.movement :as computer-movement]
            [empire.game-mechanics.visibility :as visibility]))

(defn- computer-unit-at
  [pos]
  (:contents (get-in (sa/read-state :computer-map) pos)))

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
               :when (> (core/distance a b) config/fighter-fuel)]
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
                                    (<= (core/distance [i j] city1) config/fighter-fuel)
                                    (<= (core/distance [i j] city2) config/fighter-fuel))]
                     [i j])]
    (when (seq candidates)
      (apply min-key #(core/distance % midpoint) candidates))))

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
                                 (core/get-neighbors pos)))
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
      (apply min-key #(+ (* 1000 (core/distance % midpoint))
                         (core/distance % city1)
                         (core/distance % city2))
             candidates))))

(defn find-refueling-sites
  "Returns positions of all computer cities and computer carriers."
  []
  (when (and (empty? (or (sa/read-state :computer-city-positions) #{}))
             (empty? (or (sa/read-state :computer-carrier-positions) #{})))
    (sa/rebuild-refueling-caches!))

  (concat (or (sa/read-state :computer-city-positions) #{})
          (or (sa/read-state :computer-carrier-positions) #{})))

(defn find-carrier-position
  "Finds a carrier position for an unreserved city pair.
   Returns {:position pos :pair city-pair} or nil if no pair needs a carrier."
  []
  (when-let [pair (find-unreserved-pair)]
    (when-let [pos (find-position-between-cities pair)]
      {:position pos :pair pair})))

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
    (core/move-unit-to pos next-pos)
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
        (core/move-unit-to pos next-pos)
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
;; {:version 1, :tested-at "2026-03-12T11:58:16.576503-05:00", :module-hash "1248080404", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-977787572"} {:id "defn-/find-computer-cities", :kind "defn-", :line 9, :end-line 18, :hash "-94501669"} {:id "defn/compute-distant-city-pairs", :kind "defn", :line 20, :end-line 30, :hash "-1403042797"} {:id "defn/update-distant-city-pairs!", :kind "defn", :line 32, :end-line 35, :hash "-1394890960"} {:id "defn/find-reserved-pairs", :kind "defn", :line 37, :end-line 49, :hash "-1571384935"} {:id "defn/find-unreserved-pair", :kind "defn", :line 51, :end-line 60, :hash "100655481"} {:id "defn/find-position-between-cities", :kind "defn", :line 62, :end-line 82, :hash "1244149587"} {:id "defn/find-refueling-sites", :kind "defn", :line 84, :end-line 93, :hash "402911791"} {:id "defn/find-carrier-position", :kind "defn", :line 95, :end-line 101, :hash "1347128345"} {:id "form/9/declare", :kind "declare", :line 103, :end-line 103, :hash "1504557565"} {:id "defn-/target-still-valid?", :kind "defn-", :line 105, :end-line 110, :hash "-1702101786"} {:id "defn-/position-carrier-with-target", :kind "defn-", :line 112, :end-line 131, :hash "-1384669858"} {:id "defn-/position-carrier-without-target", :kind "defn-", :line 133, :end-line 147, :hash "-1695841197"} {:id "defn-/reposition-carrier", :kind "defn-", :line 149, :end-line 163, :hash "1145040612"} {:id "defn-/pair-still-valid?", :kind "defn-", :line 165, :end-line 173, :hash "-2008126743"} {:id "defn/process-carrier", :kind "defn", :line 175, :end-line 197, :hash "-190807814"}]}
;; clj-mutate-manifest-end
