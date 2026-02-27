(ns empire.computer.ship-carrier
  "Computer carrier positioning, orbit ring, and carrier-group escort."
  (:require [clojure.set :as set]
            [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.ship-core :as ship-core]
            [empire.computer.ship-escort :as escort]
            [empire.config :as config]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]))

;; --- Carrier positioning ---

(defn- find-computer-cities
  "Returns positions of all computer cities."
  []
  (let [game-map @atoms/game-map]
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
  (reset! atoms/distant-city-pairs (compute-distant-city-pairs)))

(defn find-reserved-pairs
  "Returns set of city pairs already assigned to computer carriers.
   Includes carriers in :positioning and :holding modes."
  []
  (let [game-map @atoms/game-map]
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
  (when (nil? @atoms/distant-city-pairs)
    (update-distant-city-pairs!))
  (let [distant-pairs @atoms/distant-city-pairs
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
        game-map @atoms/game-map
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
  "Returns positions of all computer cities and holding carriers."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])]
          :when (or (and (= :city (:type cell))
                         (= :computer (:city-status cell)))
                    (and (= :carrier (get-in cell [:contents :type]))
                         (= :computer (get-in cell [:contents :owner]))
                         (= :holding (get-in cell [:contents :carrier-mode]))))]
      [i j])))

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
  (let [cell (get-in @atoms/game-map target)]
    (and (= :sea (:type cell))
         (nil? (:contents cell)))))

(defn- position-carrier-with-target
  "Handles carrier in positioning mode that has a target."
  [pos target]
  (cond
    (= pos target)
    (swap! atoms/game-map update-in (conj pos :contents)
           #(-> % (assoc :carrier-mode :holding) (dissoc :carrier-target)))

    (not (target-still-valid? target))
    (do (swap! atoms/game-map update-in (conj pos :contents) dissoc :carrier-target)
        (position-carrier-without-target pos))

    :else
    (when-let [next-pos (pathfinding/next-step pos target :carrier)]
      (core/move-unit-to pos next-pos)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-pos :computer)
      next-pos)))

(defn- position-carrier-without-target
  "Handles carrier in positioning mode without a target. Finds one or holds."
  [pos]
  (if-let [{:keys [position pair]} (find-carrier-position)]
    (do (swap! atoms/game-map update-in (conj pos :contents)
               assoc :carrier-target position :carrier-pair pair :refueling :position)
        (when-let [next-pos (pathfinding/next-step pos position :carrier)]
          (core/move-unit-to pos next-pos)
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility next-pos :computer)
          next-pos))
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :carrier-mode :holding)))

(defn- reposition-carrier
  "Handles carrier in repositioning mode. Finds new position or holds."
  [pos]
  (if-let [{:keys [position pair]} (find-carrier-position)]
    (do (swap! atoms/game-map update-in (conj pos :contents)
               assoc :carrier-mode :positioning :carrier-target position :carrier-pair pair :refueling :position)
        (when-let [next-pos (pathfinding/next-step pos position :carrier)]
          (core/move-unit-to pos next-pos)
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility next-pos :computer)
          next-pos))
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :carrier-mode :holding)))

(defn- pair-still-valid?
  "Returns true if both cities in the pair are still computer-owned."
  [pair]
  (let [game-map @atoms/game-map]
    (every? (fn [pos]
              (let [cell (get-in game-map pos)]
                (and (= :city (:type cell))
                     (= :computer (:city-status cell)))))
            pair)))

(defn process-carrier
  "Processes a computer carrier based on its carrier-mode."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
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
          (do (swap! atoms/game-map update-in (conj pos :contents)
                     #(-> % (assoc :carrier-mode :repositioning) (dissoc :carrier-pair)))
              nil)))

      :repositioning (reposition-carrier pos)

      nil)))

;; --- Carrier group escort (battleship + submarine) ---

(def orbit-ring
  "16 offsets forming a clockwise Chebyshev ring at radius 2."
  [[-2 -2] [-2 -1] [-2 0] [-2 1] [-2 2]
   [-1 2] [0 2] [1 2]
   [2 2] [2 1] [2 0] [2 -1] [2 -2]
   [1 -2] [0 -2] [-1 -2]])

(defn- find-carrier-with-open-slot
  "Finds the nearest computer carrier with an open slot for the given unit type."
  [pos unit-type]
  (let [game-map @atoms/game-map
        candidates (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :let [cell (get-in game-map [i j])
                               unit (:contents cell)]
                         :when (and unit
                                    (= :carrier (:type unit))
                                    (= :computer (:owner unit))
                                    (case unit-type
                                      :battleship (nil? (:group-battleship-id unit))
                                      :submarine (< (count (:group-submarine-ids unit [])) 2)
                                      false))]
                     [i j])]
    (when (seq candidates)
      (apply min-key (partial core/distance pos) candidates))))

(defn- initial-orbit-angle
  "Returns the starting orbit angle for a new escort."
  [unit-type carrier]
  (case unit-type
    :battleship 0
    :submarine (if (empty? (:group-submarine-ids carrier [])) 5 11)))

(defn- adopt-carrier-escort
  "Pairs a battleship or submarine escort with a carrier."
  [pos carrier-pos unit-type]
  (let [escort-unit (get-in @atoms/game-map (conj pos :contents))
        carrier (get-in @atoms/game-map (conj carrier-pos :contents))
        carrier-id (:carrier-id carrier)
        escort-id (:escort-id escort-unit)
        angle (initial-orbit-angle unit-type carrier)]
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :escort-carrier-id carrier-id
                 :escort-mode :intercepting
                 :orbit-angle angle)
    (case unit-type
      :battleship
      (swap! atoms/game-map update-in (conj carrier-pos :contents)
             assoc :group-battleship-id escort-id)
      :submarine
      (swap! atoms/game-map update-in (conj carrier-pos :contents)
             update :group-submarine-ids conj escort-id))))

(defn- orbit-target-pos
  "Computes the absolute position for an orbit angle around carrier."
  [carrier-pos angle]
  (let [[dr dc] (nth orbit-ring (mod angle 16))]
    [(+ (first carrier-pos) dr) (+ (second carrier-pos) dc)]))

(defn- valid-orbit-pos?
  "Returns true if pos is a valid empty sea cell on the game map."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and cell (= :sea (:type cell)) (nil? (:contents cell)))))

(defn- find-next-orbit-angle
  "Finds the next orbit angle with a valid sea position, starting from start-angle.
   Returns nil if all 16 positions are invalid."
  [carrier-pos start-angle]
  (first (for [i (range 16)
               :let [angle (mod (+ start-angle i) 16)
                     pos (orbit-target-pos carrier-pos angle)]
               :when (valid-orbit-pos? pos)]
           angle)))

(defn- revert-escort-to-seeking
  "Reverts an escort to seeking mode, clearing carrier reference."
  [pos]
  (swap! atoms/game-map update-in (conj pos :contents)
         #(-> % (assoc :escort-mode :seeking)
              (dissoc :escort-carrier-id :orbit-angle))))

(defn- process-escort-seeking
  "Escort seeking: find a carrier with an open slot and adopt it."
  [pos unit-type]
  (when-let [carrier-pos (find-carrier-with-open-slot pos unit-type)]
    (adopt-carrier-escort pos carrier-pos unit-type)
    (ship-core/move-toward pos carrier-pos)))

(defn- transition-to-orbiting
  "Transitions an escort to orbiting mode."
  [pos carrier-pos unit]
  (let [angle (or (:orbit-angle unit) 0)
        valid-angle (find-next-orbit-angle carrier-pos angle)]
    (if valid-angle
      (let [target (orbit-target-pos carrier-pos valid-angle)]
        (when (not= pos target)
          (ship-core/move-toward pos target))
        (swap! atoms/game-map update-in
               (conj (or (when (not= pos target) target) pos) :contents)
               assoc :escort-mode :orbiting :orbit-angle valid-angle))
      (swap! atoms/game-map update-in (conj pos :contents)
             assoc :escort-mode :orbiting))))

(defn- process-escort-intercepting
  "Escort intercepting: move toward carrier, transition to orbiting at radius 2."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))]
    (if-let [carrier-pos (escort/find-carrier-by-id (:escort-carrier-id unit))]
      (if (<= (core/chebyshev-distance pos carrier-pos) 2)
        (transition-to-orbiting pos carrier-pos unit)
        (ship-core/move-toward pos carrier-pos))
      (revert-escort-to-seeking pos))))

(defn- process-escort-orbiting
  "Escort orbiting: advance one step along the orbit ring."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))]
    (if-let [carrier-pos (escort/find-carrier-by-id (:escort-carrier-id unit))]
      (let [current-angle (or (:orbit-angle unit) 0)
            next-angle (find-next-orbit-angle carrier-pos (inc current-angle))]
        (if next-angle
          (let [target (orbit-target-pos carrier-pos next-angle)]
            (if (= pos target)
              (swap! atoms/game-map update-in (conj pos :contents)
                     assoc :orbit-angle next-angle)
              (when (valid-orbit-pos? target)
                (core/move-unit-to pos target)
                (visibility/update-cell-visibility pos :computer)
                (visibility/update-cell-visibility target :computer)
                (swap! atoms/game-map update-in (conj target :contents)
                       assoc :orbit-angle next-angle))))
          nil))
      (revert-escort-to-seeking pos))))

(defn- find-enemy-near-carrier-group
  "Finds a player ship adjacent to escort or its carrier."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        carrier-pos (when (:escort-carrier-id unit)
                      (escort/find-carrier-by-id (:escort-carrier-id unit)))]
    (escort/find-enemy-near-positions (filter some? [pos carrier-pos]))))

(defn process-carrier-group-escort
  "Processes a battleship or submarine in carrier group escort mode."
  [pos unit-type]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        mode (:escort-mode unit)]
    (if-let [enemy-pos (when (= :orbiting mode)
                         (find-enemy-near-carrier-group pos))]
      (escort/begin-pursuit pos enemy-pos)
      (case mode
        :seeking (process-escort-seeking pos unit-type)
        :intercepting (process-escort-intercepting pos)
        :orbiting (process-escort-orbiting pos)
        :pursuing (escort/process-pursuit pos)
        nil))))
