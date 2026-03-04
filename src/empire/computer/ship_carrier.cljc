;; mutation-tested: 2026-03-02
;; mutation-tested: 2026-02-27
(ns empire.computer.ship-carrier
  "Computer carrier positioning - finding and navigating to holding positions."
  (:require [clojure.set :as set]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.config :as config]
            [empire.computer.movement :as computer-movement]
            [empire.computer.movement :as computer-movement]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn- rebuild-refueling-caches!
  []
  ((:rebuild-refueling-caches! @state-ctx)))

(defn- find-computer-cities
  "Returns positions of all computer cities."
  []
  (let [game-map (current-world)]
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
  (write-runtime-state! :distant-city-pairs (compute-distant-city-pairs)))

(defn find-reserved-pairs
  "Returns set of city pairs already assigned to computer carriers.
   Includes carriers in :positioning and :holding modes."
  []
  (let [game-map (current-world)]
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
  (when (nil? (read-runtime-state :distant-city-pairs))
    (update-distant-city-pairs!))
  (let [distant-pairs (or (read-runtime-state :distant-city-pairs) #{})
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
        game-map (current-world)
        cols (count game-map)
        rows (count (first game-map))
        candidates (for [i (range cols)
                         j (range rows)
                         :let [cell (get-in game-map [i j])]
                         :when (and (= :sea (:type cell))
                                    (nil? (:contents cell))
                                    (<= (core/distance [i j] city1) config/fighter-fuel)
                                    (<= (core/distance [i j] city2) config/fighter-fuel))]
                     [i j])]
    (when (seq candidates)
      (apply min-key #(core/distance % midpoint) candidates))))

(defn find-refueling-sites
  "Returns positions of all computer cities and computer carriers."
  []
  (when (and (empty? (or (read-runtime-state :computer-city-positions) #{}))
             (empty? (or (read-runtime-state :computer-carrier-positions) #{}))
             (current-world))
    (rebuild-refueling-caches!))
  (concat (or (read-runtime-state :computer-city-positions) #{})
          (or (read-runtime-state :computer-carrier-positions) #{})))

(defn find-carrier-position
  "Finds a carrier position for an unreserved city pair.
   Returns {:position pos :pair city-pair} or nil if no pair needs a carrier."
  []
  (when-let [pair (find-unreserved-pair)]
    (when-let [pos (find-position-between-cities pair)]
      {:position pos :pair pair})))

(declare position-carrier-without-target)

(defn- target-still-valid?
  "Returns true if the carrier target is still a valid sea cell."
  [target]
  (let [cell (get-in (current-world) target)]
    (and (= :sea (:type cell))
         (nil? (:contents cell)))))

(defn- position-carrier-with-target
  "Handles carrier in positioning mode that has a target."
  [pos target]
  (cond
    (= pos target)
    (update-game-map! update-in (conj pos :contents)
                      #(-> % (assoc :carrier-mode :holding) (dissoc :carrier-target)))

    (not (target-still-valid? target))
    (do (update-game-map! update-in (conj pos :contents) dissoc :carrier-target)
        (position-carrier-without-target pos))

    :else
    (when-let [next-pos (computer-movement/next-step pos target :carrier)]
      (core/move-unit-to pos next-pos)
      (update-runtime-state! :computer-carrier-positions disj pos)
      (update-runtime-state! :computer-carrier-positions (fnil conj #{}) next-pos)
      (computer-movement/update-cell-visibility! pos :computer)
      (computer-movement/update-cell-visibility! next-pos :computer)
      next-pos)))

(defn- position-carrier-without-target
  "Handles carrier in positioning mode without a target. Finds one or holds."
  [pos]
  (if-let [{:keys [position pair]} (find-carrier-position)]
    (do (update-game-map! update-in (conj pos :contents)
                          assoc :carrier-target position :carrier-pair pair :refueling :position)
        (when-let [next-pos (computer-movement/next-step pos position :carrier)]
          (core/move-unit-to pos next-pos)
          (update-runtime-state! :computer-carrier-positions disj pos)
          (update-runtime-state! :computer-carrier-positions (fnil conj #{}) next-pos)
          (computer-movement/update-cell-visibility! pos :computer)
          (computer-movement/update-cell-visibility! next-pos :computer)
          next-pos))
    (update-game-map! update-in (conj pos :contents)
                      assoc :carrier-mode :holding)))

(defn- reposition-carrier
  "Handles carrier in repositioning mode. Finds new position or holds."
  [pos]
  (if-let [{:keys [position pair]} (find-carrier-position)]
    (do (update-game-map! update-in (conj pos :contents)
                          assoc :carrier-mode :positioning :carrier-target position :carrier-pair pair :refueling :position)
        (when-let [next-pos (computer-movement/next-step pos position :carrier)]
          (core/move-unit-to pos next-pos)
          (update-runtime-state! :computer-carrier-positions disj pos)
          (update-runtime-state! :computer-carrier-positions (fnil conj #{}) next-pos)
          (computer-movement/update-cell-visibility! pos :computer)
          (computer-movement/update-cell-visibility! next-pos :computer)
          next-pos))
    (update-game-map! update-in (conj pos :contents)
                      assoc :carrier-mode :holding)))

(defn- pair-still-valid?
  "Returns true if both cities in the pair are still computer-owned."
  [pair]
  (let [game-map (current-world)]
    (every? (fn [pos]
              (let [cell (get-in game-map pos)]
                (and (= :city (:type cell))
                     (= :computer (:city-status cell)))))
            pair)))

(defn process-carrier
  "Processes a computer carrier based on its carrier-mode."
  [pos]
  (let [unit (get-in (current-world) (conj pos :contents))
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
          (do (update-game-map! update-in (conj pos :contents)
                                #(-> % (assoc :carrier-mode :repositioning) (dissoc :carrier-pair)))
              nil)))

      :repositioning (reposition-carrier pos)

      nil)))
