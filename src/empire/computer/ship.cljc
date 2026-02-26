;; mutation-tested: 2026-02-22
(ns empire.computer.ship
  "Computer ship module - VMS Empire style ship movement.
   Attack adjacent enemies, explore sea, protect transports, patrol."
  (:require [clojure.set :as set]
            [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.containers.helpers :as uc]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]))

(declare find-carrier-by-id)

(defn- get-passable-sea-neighbors
  "Returns passable sea neighbors for a ship."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :player (:owner (:contents cell)))))))  ; Can attack player
            (core/get-neighbors pos))))

(defn- find-adjacent-enemy-ship
  "Finds an adjacent enemy ship to attack."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (#{:patrol-boat :destroyer :submarine :transport
                               :carrier :battleship} (:type unit)))))
                   (core/get-neighbors pos)))))

(defn- attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if ship died."
  [ship-pos enemy-pos]
  (let [attacker (get-in @atoms/game-map (conj ship-pos :contents))
        defender (get-in @atoms/game-map (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)
        dead-unit (if (= :attacker (:winner result)) defender attacker)]
    ;; Remove attacker from original position
    (swap! atoms/game-map update-in ship-pos dissoc :contents)
    (if (= :attacker (:winner result))
      ;; Attacker won - move to enemy position
      (do
        (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
        (visibility/update-cell-visibility ship-pos :computer)
        (visibility/update-cell-visibility enemy-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        enemy-pos)
      ;; Attacker lost
      (do
        (visibility/update-cell-visibility ship-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        nil))))

(defn- find-computer-transports
  "Find computer transports to protect."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :transport (:type unit)))]
      [i j])))

(defn- find-nearest-transport
  "Find the nearest computer transport."
  [pos]
  (let [transports (find-computer-transports)]
    (when (seq transports)
      (apply min-key (partial core/distance pos) transports))))

(defn- move-toward
  "Move ship one step toward target."
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when closest
      (core/move-unit-to pos closest)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility closest :computer)
      closest)))

(defn- explore-sea
  "Move toward unexplored sea via BFS. Stays put if all sea is explored."
  [pos ship-type]
  (when-let [target (pathfinding-bfs/find-nearest-unexplored pos ship-type)]
    (move-toward pos target)))

(defn- find-player-ship-sighting
  "Find the nearest visible player ship position."
  [pos]
  (let [player-units (core/find-visible-player-units)]
    (when (seq player-units)
      (apply min-key (partial core/distance pos) player-units))))

(defn- retreat-if-damaged
  "If damaged and under threat, retreat toward friendly city."
  [pos unit]
  (when (threat/should-retreat? pos unit @atoms/computer-map)
    (let [passable (get-passable-sea-neighbors pos)]
      (threat/retreat-move pos unit @atoms/computer-map passable))))

;; --- Patrol boat helpers ---

(defn- find-adjacent-player-transport
  "Finds an adjacent player transport to attack."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (= :transport (:type unit)))))
                   (core/get-neighbors pos)))))

(defn- find-adjacent-non-transport-enemy
  "Finds an adjacent player unit that is not a transport."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (not= :transport (:type unit)))))
                   (core/get-neighbors pos)))))

(defn- adjacent-to-land?
  "Returns true if the given position has at least one adjacent land or city cell."
  [pos]
  (let [game-map @atoms/game-map]
    (some (fn [neighbor]
            (let [cell (get-in game-map neighbor)]
              (and cell (#{:land :city} (:type cell)))))
          (core/get-neighbors pos))))

(defn- flee-from
  "Move patrol boat away from the given enemy position."
  [pos enemy-pos]
  (let [passable (get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in @atoms/game-map n))))
                               passable)]
    (when (seq empty-passable)
      (let [farthest (apply max-key (partial core/distance enemy-pos) empty-passable)]
        (core/move-unit-to pos farthest)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility farthest :computer)
        farthest))))

(defn patrol-crawl-step
  "Crawl along coastline. Records position in seen-coast.
   Prefers unseen coastal cells. Switches to :exploring when
   all coastal neighbors are seen or at map edge with none unseen.
   Returns new position or nil."
  [pos]
  (swap! atoms/seen-coast conj pos)
  (let [passable (get-passable-sea-neighbors pos)
        empty-passable (filter #(nil? (:contents (get-in @atoms/game-map %))) passable)
        coastal (filter adjacent-to-land? empty-passable)
        unseen (remove @atoms/seen-coast coastal)
        targets (if (seq unseen) unseen coastal)
        switch? (empty? unseen)]
    (when (seq targets)
      (let [target (rand-nth targets)]
        (core/move-unit-to pos target)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility target :computer)
        (when switch?
          (swap! atoms/game-map assoc-in
                 (conj target :contents :patrol-mode) :exploring))
        target))))

(defn- arrived-at-unseen-coast?
  "Returns true if pos is adjacent to land/city on computer-map and not in seen-coast."
  [pos]
  (and (not (contains? @atoms/seen-coast pos))
       (some (fn [neighbor]
               (let [cell (get-in @atoms/computer-map neighbor)]
                 (and cell (#{:land :city} (:type cell)))))
             (core/get-neighbors pos))))

(defn- run-bfs-and-store-path
  "Run BFS to find unseen coast, store full path on unit. Returns path or nil.
   Excludes targets already claimed by other patrol boats this round."
  [pos]
  (when-let [path (pathfinding-bfs/bfs-to-unseen-coast
                    pos @atoms/computer-map @atoms/claimed-patrol-targets)]
    (swap! atoms/claimed-patrol-targets conj (last path))
    (swap! atoms/game-map assoc-in
           (conj pos :contents :explore-path) (vec path))
    path))

(defn- switch-to-crawling [next-pos]
  "Switch patrol boat to crawling mode and clear explore state."
  (swap! atoms/game-map update-in (conj next-pos :contents)
         #(-> % (assoc :patrol-mode :crawling)
              (dissoc :explore-path))))

(defn- follow-explore-path
  "Take one step along stored BFS path. Returns new pos or nil."
  [pos path]
  (let [next-pos (first path)
        rest-path (vec (rest path))]
    (if (core/move-unit-to pos next-pos)
      (do (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility next-pos :computer)
          (if (arrived-at-unseen-coast? next-pos)
            (switch-to-crawling next-pos)
            (swap! atoms/game-map assoc-in
                   (conj next-pos :contents :explore-path) rest-path))
          next-pos)
      (do (swap! atoms/game-map update-in
                 (conj pos :contents) dissoc :explore-path)
          nil))))

(defn patrol-explore-step
  "Explore toward unseen coast. Stores BFS path and follows it step by step.
   Switches to crawling on arrival at unseen coast.
   Returns new position or nil."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        path (:explore-path unit)]
    (if (seq path)
      (follow-explore-path pos path)
      (when (run-bfs-and-store-path pos)
        (let [new-path (:explore-path
                         (get-in @atoms/game-map (conj pos :contents)))]
          (follow-explore-path pos new-path))))))

(defn- patrol-boat-step
  "Execute one step of patrol boat movement. Returns new position or nil."
  [pos]
  (if-let [transport-pos (find-adjacent-player-transport pos)]
    (attack-enemy pos transport-pos)
    (if-let [enemy-pos (find-adjacent-non-transport-enemy pos)]
      (flee-from pos enemy-pos)
      (let [unit (get-in @atoms/game-map (conj pos :contents))]
        (case (or (:patrol-mode unit) :crawling)
          :crawling (patrol-crawl-step pos)
          :exploring (patrol-explore-step pos))))))

(defn- process-patrol-boat
  "Processes a computer patrol boat. Moves up to speed 4 steps per round.
   Nil results (reflections, blocked) consume a step but loop continues.
   Stops if unit is destroyed in combat."
  [pos]
  (loop [current-pos pos steps-left 4]
    (if (or (zero? steps-left)
            (nil? (get-in @atoms/game-map (conj current-pos :contents))))
      current-pos
      (if-let [new-pos (patrol-boat-step current-pos)]
        (recur new-pos (dec steps-left))
        (recur current-pos (dec steps-left))))))

;; --- Destroyer escort helpers ---

(defn- find-unadopted-transport
  "Finds the nearest computer transport without an escort-destroyer-id."
  [pos]
  (let [game-map @atoms/game-map
        candidates (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :let [cell (get-in game-map [i j])
                               unit (:contents cell)]
                         :when (and unit
                                    (= :computer (:owner unit))
                                    (= :transport (:type unit))
                                    (nil? (:escort-destroyer-id unit)))]
                     [i j])]
    (when (seq candidates)
      (apply min-key (partial core/distance pos) candidates))))

(defn- adopt-transport
  "Pairs a destroyer at pos with a transport at transport-pos."
  [pos transport-pos]
  (let [destroyer (get-in @atoms/game-map (conj pos :contents))
        transport (get-in @atoms/game-map (conj transport-pos :contents))
        d-id (:destroyer-id destroyer)
        t-id (:transport-id transport)]
    (swap! atoms/game-map update-in (conj pos :contents)
           #(assoc % :escort-transport-id t-id :escort-mode :intercepting))
    (swap! atoms/game-map update-in (conj transport-pos :contents)
           #(assoc % :escort-destroyer-id d-id))))

(defn- find-transport-by-id
  "Finds the position of a transport with the given transport-id."
  [transport-id]
  (let [game-map @atoms/game-map]
    (first (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])
                       unit (:contents cell)]
                 :when (and unit
                            (= :transport (:type unit))
                            (= transport-id (:transport-id unit)))]
             [i j]))))

(defn- find-enemy-near-positions
  "Finds a player ship adjacent to any of the given positions.
   Returns enemy position or nil."
  [positions]
  (let [game-map @atoms/game-map]
    (first (for [gpos positions
                 neighbor (core/get-neighbors gpos)
                 :let [cell (get-in game-map neighbor)
                       contents (:contents cell)]
                 :when (and contents
                            (= :player (:owner contents))
                            (#{:patrol-boat :destroyer :submarine :transport
                               :carrier :battleship} (:type contents)))]
             neighbor))))

(defn- find-enemy-near-destroyer-group
  "Finds a player ship adjacent to destroyer or its escorted transport."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        transport-pos (when (:escort-transport-id unit)
                        (find-transport-by-id (:escort-transport-id unit)))]
    (find-enemy-near-positions (filter some? [pos transport-pos]))))

(defn- begin-pursuit
  "Switches an escort ship to pursuing mode, targeting the enemy."
  [pos enemy-pos]
  (swap! atoms/game-map update-in (conj pos :contents)
         assoc :escort-mode :pursuing
               :pursuit-target enemy-pos
               :pursuit-steps-remaining 5)
  (move-toward pos enemy-pos))

(defn- group-positions
  "Returns positions of all ships in the escort group (self + transport or carrier)."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        transport-pos (when (:escort-transport-id unit)
                        (find-transport-by-id (:escort-transport-id unit)))
        carrier-pos (when (:escort-carrier-id unit)
                      (find-carrier-by-id (:escort-carrier-id unit)))]
    (filter some? [pos transport-pos carrier-pos])))

(defn- visible-to-group?
  "Returns true if pos is within visibility (Chebyshev 1) of any group member."
  [target-pos group-poss]
  (some (fn [gpos] (<= (core/chebyshev-distance target-pos gpos) 1)) group-poss))

(defn- end-pursuit
  "Reverts a pursuing ship back to its pre-pursuit mode.
   Destroyers return to :escorting, carrier group escorts to :orbiting."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        return-mode (if (:escort-carrier-id unit) :orbiting :escorting)]
    (swap! atoms/game-map update-in (conj pos :contents)
           #(-> % (assoc :escort-mode return-mode)
                (dissoc :pursuit-target :pursuit-steps-remaining)))))

(defn- process-pursuit
  "Continues pursuit: move toward a cell the enemy could have gone to,
   excluding cells visible to group members. Decrements steps remaining."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        target (:pursuit-target unit)
        steps (:pursuit-steps-remaining unit)
        group (group-positions pos)
        ;; Cells the enemy could have moved to (neighbors + stayed)
        candidates (conj (set (core/get-neighbors target)) target)
        ;; Filter to sea cells not visible to group
        sea-candidates (filter (fn [c]
                                 (let [cell (get-in @atoms/game-map c)]
                                   (and cell (= :sea (:type cell))
                                        (not (visible-to-group? c group)))))
                               candidates)]
    (if (or (<= steps 1) (empty? sea-candidates))
      (end-pursuit pos)
      (let [chosen (rand-nth (vec sea-candidates))
            new-pos (move-toward pos chosen)]
        (when new-pos
          (swap! atoms/game-map update-in (conj new-pos :contents)
                 assoc :pursuit-target chosen
                       :pursuit-steps-remaining (dec steps)))))))

(defn- process-escort-destroyer
  "Processes a destroyer in escort mode."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        mode (:escort-mode unit)]
    ;; Escorting destroyers check for enemies near group before normal behavior
    (if-let [enemy-pos (when (= :escorting mode)
                         (find-enemy-near-destroyer-group pos))]
      (begin-pursuit pos enemy-pos)
      (case mode
        :seeking
        (when-let [transport-pos (find-unadopted-transport pos)]
          (adopt-transport pos transport-pos)
          (move-toward pos transport-pos))

        :intercepting
        (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
          (if (<= (core/distance pos transport-pos) 1)
            ;; Adjacent - switch to escorting
            (swap! atoms/game-map update-in (conj pos :contents)
                   assoc :escort-mode :escorting)
            (move-toward pos transport-pos))
          ;; Transport gone - back to seeking
          (do (swap! atoms/game-map update-in (conj pos :contents)
                     #(-> % (assoc :escort-mode :seeking)
                          (dissoc :escort-transport-id)))
              nil))

        :escorting
        (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
          ;; Stay adjacent - if already adjacent, stay put. If not, move closer.
          (when (> (core/distance pos transport-pos) 1)
            (move-toward pos transport-pos))
          ;; Transport gone - back to seeking
          (do (swap! atoms/game-map update-in (conj pos :contents)
                     #(-> % (assoc :escort-mode :seeking)
                          (dissoc :escort-transport-id)))
              nil))

        :pursuing (process-pursuit pos)

        ;; Default (including nil) - fall through to normal ship behavior
        nil))))

;; --- Carrier positioning helpers ---

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
  "Handles carrier in positioning mode that has a target.
   Uses pathfinding for movement. CC=3."
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

(defn- process-carrier
  "Processes a computer carrier based on its carrier-mode.
   CC=5: case(3 branches + default) + if(target)."
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
          nil  ; Stay holding - no pair or pair is valid
          (do (swap! atoms/game-map update-in (conj pos :contents)
                     #(-> % (assoc :carrier-mode :repositioning) (dissoc :carrier-pair)))
              nil)))

      :repositioning (reposition-carrier pos)

      nil)))

;; --- Carrier group escort helpers (battleship + submarine) ---

(def orbit-ring
  "16 offsets forming a clockwise Chebyshev ring at radius 2."
  [[-2 -2] [-2 -1] [-2 0] [-2 1] [-2 2]
   [-1 2] [0 2] [1 2]
   [2 2] [2 1] [2 0] [2 -1] [2 -2]
   [1 -2] [0 -2] [-1 -2]])

(defn- find-carrier-by-id
  "Finds the position of a carrier with the given carrier-id."
  [carrier-id]
  (let [game-map @atoms/game-map]
    (first (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])
                       unit (:contents cell)]
                 :when (and unit
                            (= :carrier (:type unit))
                            (= carrier-id (:carrier-id unit)))]
             [i j]))))

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
  (let [escort (get-in @atoms/game-map (conj pos :contents))
        carrier (get-in @atoms/game-map (conj carrier-pos :contents))
        carrier-id (:carrier-id carrier)
        escort-id (:escort-id escort)
        angle (initial-orbit-angle unit-type carrier)]
    ;; Update escort
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :escort-carrier-id carrier-id
                 :escort-mode :intercepting
                 :orbit-angle angle)
    ;; Update carrier group
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
    (move-toward pos carrier-pos)))

(defn- process-escort-intercepting
  "Escort intercepting: move toward carrier, transition to orbiting at radius 2."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))]
    (if-let [carrier-pos (find-carrier-by-id (:escort-carrier-id unit))]
      (if (<= (core/chebyshev-distance pos carrier-pos) 2)
        ;; At orbit radius - start orbiting
        (let [angle (or (:orbit-angle unit) 0)
              valid-angle (find-next-orbit-angle carrier-pos angle)]
          (if valid-angle
            (let [target (orbit-target-pos carrier-pos valid-angle)]
              (when (not= pos target)
                (move-toward pos target))
              (swap! atoms/game-map update-in
                     (conj (or (when (not= pos target) target) pos) :contents)
                     assoc :escort-mode :orbiting :orbit-angle valid-angle))
            ;; No valid orbit position - stay and orbit
            (swap! atoms/game-map update-in (conj pos :contents)
                   assoc :escort-mode :orbiting)))
        ;; Not at radius yet - move closer
        (move-toward pos carrier-pos))
      ;; Carrier gone
      (revert-escort-to-seeking pos))))

(defn- process-escort-orbiting
  "Escort orbiting: advance one step along the orbit ring."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))]
    (if-let [carrier-pos (find-carrier-by-id (:escort-carrier-id unit))]
      (let [current-angle (or (:orbit-angle unit) 0)
            next-angle (find-next-orbit-angle carrier-pos (inc current-angle))]
        (if next-angle
          (let [target (orbit-target-pos carrier-pos next-angle)]
            (if (= pos target)
              ;; Already at target, just update angle
              (swap! atoms/game-map update-in (conj pos :contents)
                     assoc :orbit-angle next-angle)
              ;; Move to next orbit position
              (when (valid-orbit-pos? target)
                (core/move-unit-to pos target)
                (visibility/update-cell-visibility pos :computer)
                (visibility/update-cell-visibility target :computer)
                (swap! atoms/game-map update-in (conj target :contents)
                       assoc :orbit-angle next-angle))))
          ;; No valid orbit positions - stay put
          nil))
      ;; Carrier gone
      (revert-escort-to-seeking pos))))

(defn- find-enemy-near-carrier-group
  "Finds a player ship adjacent to escort or its carrier."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        carrier-pos (when (:escort-carrier-id unit)
                      (find-carrier-by-id (:escort-carrier-id unit)))]
    (find-enemy-near-positions (filter some? [pos carrier-pos]))))

(defn- process-carrier-group-escort
  "Processes a battleship or submarine in carrier group escort mode."
  [pos unit-type]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        mode (:escort-mode unit)]
    ;; Orbiting escorts check for enemies near carrier group
    (if-let [enemy-pos (when (= :orbiting mode)
                         (find-enemy-near-carrier-group pos))]
      (begin-pursuit pos enemy-pos)
      (case mode
        :seeking (process-escort-seeking pos unit-type)
        :intercepting (process-escort-intercepting pos)
        :orbiting (process-escort-orbiting pos)
        :pursuing (process-pursuit pos)
        nil))))

(defn- find-adjacent-dock-city
  "Finds an adjacent friendly city where a damaged ship can dock for repair."
  [pos unit]
  (first (filter (fn [neighbor]
                   (uc/ship-can-dock? unit (get-in @atoms/game-map neighbor)))
                 (core/get-neighbors pos))))

(defn- dock-computer-ship
  "Docks a damaged computer ship into a friendly city's shipyard."
  [ship-pos city-pos]
  (let [cell (get-in @atoms/game-map ship-pos)
        unit (:contents cell)
        city-cell (get-in @atoms/game-map city-pos)
        updated-city (uc/add-ship-to-shipyard city-cell (:type unit) (:hits unit))]
    (swap! atoms/game-map assoc-in ship-pos (dissoc cell :contents))
    (swap! atoms/game-map assoc-in city-pos updated-city)
    (visibility/update-cell-visibility city-pos :computer)
    city-pos))

(defn- try-dock [pos unit]
  (when-let [city (find-adjacent-dock-city pos unit)]
    (dock-computer-ship pos city)))

(defn- try-retreat [pos unit]
  (when-let [rp (retreat-if-damaged pos unit)]
    (core/move-unit-to pos rp)
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility rp :computer)))

(defn- try-attack-adjacent [pos]
  (when-let [ep (find-adjacent-enemy-ship pos)]
    (attack-enemy pos ep)))

(defn- try-escort [pos ship-type unit]
  (when (:escort-mode unit)
    (cond
      (= :destroyer ship-type)
      (or (process-escort-destroyer pos) (explore-sea pos ship-type))

      (#{:battleship :submarine} ship-type)
      (or (process-carrier-group-escort pos ship-type)
          (explore-sea pos ship-type)))))

(defn- try-escort-transport [pos ship-type]
  (when (= :destroyer ship-type)
    (when-let [transport-pos (find-nearest-transport pos)]
      (if (> (core/distance pos transport-pos) 2)
        (move-toward pos transport-pos)
        (explore-sea pos ship-type)))))

(defn- try-hunt-player-ship [pos]
  (when-let [sighting (find-player-ship-sighting pos)]
    (move-toward pos sighting)))

(defn- dispatch-ship-action [pos ship-type unit]
  (cond
    (= :patrol-boat ship-type)
    (process-patrol-boat pos)

    (and (= :carrier ship-type) (:carrier-mode unit))
    (process-carrier pos)

    :else
    (or (try-dock pos unit)
        (try-retreat pos unit)
        (try-attack-adjacent pos)
        (try-escort pos ship-type unit)
        (try-escort-transport pos ship-type)
        (try-hunt-player-ship pos)
        (explore-sea pos ship-type))))

(defn process-ship
  "Processes a computer ship using VMS Empire style logic.
   Priority: Retreat if damaged > Attack adjacent > Escort transports > Hunt enemies > Explore
   Returns nil after processing - ships only move once per round."
  [pos ship-type]
  (let [unit (:contents (get-in @atoms/game-map pos))]
    (when (and unit
               (= :computer (:owner unit))
               (= ship-type (:type unit)))
      (dispatch-ship-action pos ship-type unit)))
  nil)
