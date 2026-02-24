(ns empire.computer.fighter
  "Computer fighter module - VMS Empire style fighter movement.
   Attack adjacent enemies, patrol within fuel range, return to city.
   Leg-based coverage: fly between refueling sites, prefer unflown/oldest legs."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.ship :as ship]
            [empire.combat :as combat]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]
            [empire.config :as config]))

(defn- get-passable-neighbors
  "Returns passable neighbors for a fighter (can fly over anything except off-map)."
  [pos]
  (let [game-map @atoms/game-map
        height (count game-map)
        width (count (first game-map))]
    (filter (fn [[r c]]
              (and (>= r 0) (< r height)
                   (>= c 0) (< c width)))
            (core/get-neighbors pos))))

(defn- occupied?
  "Returns true if the cell at pos has contents."
  [pos]
  (some? (get-in @atoms/game-map (conj pos :contents))))

(defn- diagonal-move?
  "Returns true if moving from pos to target involves both row and column change."
  [[r1 c1] [r2 c2]]
  (and (not= r1 r2) (not= c1 c2)))

(defn- friendly-occupied?
  "Returns true if the cell at pos has a computer-owned unit."
  [pos]
  (let [contents (get-in @atoms/game-map (conj pos :contents))]
    (and (some? contents) (= :computer (:owner contents)))))

(defn- best-neighbor-toward
  "Picks the best neighbor toward target using distance + diagonal preference.
   Considers all passable neighbors, not just unoccupied ones."
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (let [scored (map (fn [n]
                        [n (core/distance n target) (if (diagonal-move? pos n) 0 1)])
                      passable-neighbors)
          best-dist (apply min (map second scored))
          at-best-dist (filter #(= best-dist (second %)) scored)
          best-diag (apply min (map #(nth % 2) at-best-dist))
          candidates (filter #(= best-diag (nth % 2)) at-best-dist)]
      (first (last candidates)))))

(defn- direction-from
  "Returns the unit direction vector [dr dc] from pos to neighbor."
  [[r1 c1] [r2 c2]]
  [(Integer/signum (- r2 r1)) (Integer/signum (- c2 c1))])

(defn- in-bounds?
  "Returns true if pos is within game-map bounds."
  [[r c]]
  (let [game-map @atoms/game-map
        height (count game-map)
        width (count (first game-map))]
    (and (>= r 0) (< r height)
         (>= c 0) (< c width))))

(defn hop-over-friendly
  "When the best neighbor toward target is occupied by a computer unit, scan
   forward along the direction of travel, skipping all consecutive
   friendly-occupied cells. Land on the first empty passable cell, or
   attack an enemy at the end of the chain.
   Returns {:dest pos :hops n} or {:dest pos :hops n :attack true} or nil."
  [pos target]
  (let [passable (get-passable-neighbors pos)
        [br bc :as best] (best-neighbor-toward pos target passable)]
    (when best
      (if-not (occupied? best)
        {:dest best :hops 1}
        (when (friendly-occupied? best)
          (let [[dr dc] (direction-from pos best)]
            (loop [sr br sc bc hops 1]
              (let [next-pos [(+ sr dr) (+ sc dc)]]
                (when (in-bounds? next-pos)
                  (if-not (occupied? next-pos)
                    {:dest next-pos :hops (inc hops)}
                    (if (friendly-occupied? next-pos)
                      (recur (+ sr dr) (+ sc dc) (inc hops))
                      {:dest next-pos :hops (inc hops) :attack true})))))))))))

(defn- move-toward-with-sidestep
  "Like core/move-toward but excludes occupied cells and prefers diagonals on ties.
   When distance is equal, diagonals are preferred. Among same-distance same-type moves,
   the last one in input order wins (matching min-key behavior)."
  [pos target passable-neighbors]
  (let [unoccupied (filter (complement occupied?) passable-neighbors)]
    (when (seq unoccupied)
      (let [scored (map (fn [n]
                          [n (core/distance n target) (if (diagonal-move? pos n) 0 1)])
                        unoccupied)
            best-dist (apply min (map second scored))
            at-best-dist (filter #(= best-dist (second %)) scored)
            best-diag (apply min (map #(nth % 2) at-best-dist))
            candidates (filter #(= best-diag (nth % 2)) at-best-dist)]
        (first (last candidates))))))

(defn- find-adjacent-enemy
  "Finds an adjacent enemy unit to attack (not cities - fighters can't conquer)."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit)))))
                   (core/get-neighbors pos)))))

(defn- attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if fighter died."
  [fighter-pos enemy-pos]
  (let [attacker (get-in @atoms/game-map (conj fighter-pos :contents))
        defender (get-in @atoms/game-map (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)]
    ;; Remove attacker from original position
    (swap! atoms/game-map update-in fighter-pos dissoc :contents)
    (if (= :attacker (:winner result))
      ;; Attacker won - move to enemy position
      (do
        (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
        (visibility/update-cell-visibility fighter-pos :computer)
        (visibility/update-cell-visibility enemy-pos :computer)
        enemy-pos)
      ;; Attacker lost
      (do
        (visibility/update-cell-visibility fighter-pos :computer)
        nil))))

(defn- find-nearest-refueling-site
  "Find the nearest refueling site (computer city or holding carrier)."
  [pos]
  (let [sites (ship/find-refueling-sites)]
    (when (seq sites)
      (apply min-key (partial core/distance pos) sites))))

(defn- distance-to
  "Manhattan distance between two positions."
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn- fuel-to-return
  "Calculate fuel needed to return to nearest refueling site."
  [pos]
  (if-let [site (find-nearest-refueling-site pos)]
    (distance-to pos site)
    999))

(defn- should-return-to-refuel?
  "Returns true if fighter should head back to refuel."
  [pos fuel]
  (let [return-distance (fuel-to-return pos)]
    (<= fuel (+ return-distance 2))))

(defn- find-patrol-target
  "Find something interesting to patrol toward.
   Uses BFS to find nearest unexplored territory without directional bias."
  [pos]
  (let [player-units (core/find-visible-player-units)]
    (if (seq player-units)
      (apply min-key (partial distance-to pos) player-units)
      (pathfinding/find-nearest-unexplored pos :fighter))))

(defn- do-patrol
  "Execute one patrol step toward a target or unexplored area.
   Stays put if no target found (all territory explored, no enemies)."
  [pos]
  (when-let [target (find-patrol-target pos)]
    (let [passable (get-passable-neighbors pos)
          closest (move-toward-with-sidestep pos target passable)]
      (when (and closest (core/move-unit-to pos closest))
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility closest :computer)
        closest))))

(def ^:private fighter-speed 8)

(defn- land-at-city
  "Land fighter at city to refuel."
  [pos city-pos]
  (let [_fighter (get-in @atoms/game-map (conj pos :contents))]
    ;; Remove from current position
    (swap! atoms/game-map update-in pos dissoc :contents)
    ;; Add to city's airport
    (swap! atoms/game-map update-in (conj city-pos :fighter-count) (fnil inc 0))
    (visibility/update-cell-visibility pos :computer)
    :landed))

(defn- consume-fighter-fuel
  "Decrement fuel on the fighter at pos. Returns false if fighter died."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        new-fuel (dec (:fuel unit config/fighter-fuel))]
    (if (<= new-fuel 0)
      (do (swap! atoms/game-map update-in pos dissoc :contents)
          (visibility/update-cell-visibility pos :computer)
          false)
      (do (swap! atoms/game-map assoc-in (conj pos :contents :fuel) new-fuel)
          true))))

;; --- Exploration helpers ---

(def ^:private compass-directions
  "All 8 compass directions as [dr dc] vectors."
  [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])

(defn- count-unexplored-neighbors
  "Count neighbors of pos that are unexplored on computer-map."
  [pos]
  (let [computer-map @atoms/computer-map
        [r c] pos
        height (count @atoms/game-map)
        width (count (first @atoms/game-map))]
    (count (filter (fn [[dr dc]]
                     (let [nr (+ r dr) nc (+ c dc)]
                       (and (>= nr 0) (< nr height)
                            (>= nc 0) (< nc width)
                            (nil? (get-in computer-map [nr nc])))))
                   compass-directions))))

(defn- count-unexplored-along-direction
  "Project n steps from start in direction [dr dc], count unexplored cells
   (including their visibility neighbors) along the ray."
  [start direction n]
  (let [computer-map @atoms/computer-map
        game-map @atoms/game-map
        height (count game-map)
        width (count (first game-map))
        [dr dc] direction]
    (reduce (fn [total step]
              (let [pr (+ (first start) (* step dr))
                    pc (+ (second start) (* step dc))]
                (if (and (>= pr 0) (< pr height)
                         (>= pc 0) (< pc width))
                  (+ total
                     (count (filter (fn [[vr vc]]
                                      (let [nr (+ pr vr) nc (+ pc vc)]
                                        (and (>= nr 0) (< nr height)
                                             (>= nc 0) (< nc width)
                                             (nil? (get-in computer-map [nr nc])))))
                                    (conj compass-directions [0 0]))))
                  total)))
            0
            (range 1 (inc n)))))

(defn- best-exploration-heading
  "Score all 8 compass directions by unexplored cell count, return best.
   Tie-break with rand-nth."
  [pos projection-length]
  (let [scored (map (fn [dir]
                      [dir (count-unexplored-along-direction pos dir projection-length)])
                    compass-directions)
        best-score (apply max (map second scored))
        best-dirs (map first (filter #(= best-score (second %)) scored))]
    (rand-nth (vec best-dirs))))

(defn- explore-move-step
  "Shared movement logic for sorties and drones.
   Pick unoccupied passable neighbor with most unexplored neighbors;
   break ties by proximity to endpoint. Move, update visibility, consume fuel."
  [pos endpoint]
  (let [passable (get-passable-neighbors pos)
        unoccupied (filter (complement occupied?) passable)]
    (when (seq unoccupied)
      (let [scored (map (fn [n]
                          [n (count-unexplored-neighbors n) (distance-to n endpoint)])
                        unoccupied)
            best-unexplored (apply max (map second scored))
            at-best (filter #(= best-unexplored (second %)) scored)
            best-pos (first (first (sort-by #(nth % 2) at-best)))]
        (when (and best-pos (core/move-unit-to pos best-pos))
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility best-pos :computer)
          (when (consume-fighter-fuel best-pos)
            best-pos))))))

(defn- explore-step
  "One outbound sortie step. Calls explore-move-step, decrements steps-remaining.
   At zero, switches to :regular mode targeting origin."
  [pos unit]
  (let [endpoint (:flight-target-site unit)
        result (explore-move-step pos endpoint)]
    (when result
      (let [remaining (dec (:explore-steps-remaining unit))]
        (swap! atoms/game-map assoc-in
               (conj result :contents :explore-steps-remaining) remaining)
        (if (<= remaining 0)
          (swap! atoms/game-map update-in (conj result :contents)
                 assoc :flight-mode :regular
                 :flight-target-site (:explore-origin unit))
          nil))
      result)))

(defn- drone-step
  "One drone step. Delegates to explore-move-step."
  [pos unit]
  (explore-move-step pos (:flight-target-site unit)))

;; --- Leg-based coverage ---

(defn- current-refueling-site
  "Returns the refueling site position the fighter at pos is at, or nil.
   A fighter is 'at' a city if on it, or 'at' a carrier if adjacent to it."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (cond
      (and (= :city (:type cell)) (= :computer (:city-status cell)))
      pos

      :else
      (first (filter (fn [n]
                       (let [ncell (get-in @atoms/game-map n)]
                         (and (= :carrier (get-in ncell [:contents :type]))
                              (= :computer (get-in ncell [:contents :owner]))
                              (= :holding (get-in ncell [:contents :carrier-mode])))))
                     (core/get-neighbors pos))))))

(defn- choose-leg
  "Choose the best leg from current-site. Returns target site position or nil.
   Prefers unflown legs (absent from records), then oldest (lowest :last-flown)."
  [current-site]
  (let [sites (ship/find-refueling-sites)
        reachable (filter #(and (not= % current-site)
                                (<= (distance-to current-site %) config/fighter-fuel))
                          sites)
        leg-records @atoms/fighter-leg-records
        scored (map (fn [target]
                      (let [leg-key #{current-site target}
                            record (get leg-records leg-key)
                            last-flown (:last-flown record -1)]
                        [target last-flown]))
                    reachable)]
    (when (seq scored)
      (first (first (sort-by second scored))))))

(defn- assign-regular-leg
  "Assign a regular leg flight: choose-leg, set target, origin, and :flight-mode :regular."
  [pos site-pos]
  (when-let [target (choose-leg site-pos)]
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :flight-target-site target
           :flight-origin-site site-pos
           :flight-mode :regular)))

(def ^:private sortie-half-steps 16)

(defn- assign-exploration-flight
  "Assign exploration sortie or drone. Roll 1/20 for drone.
   Pick heading, set exploration fields, project endpoint."
  [pos site-pos]
  (let [heading (best-exploration-heading pos sortie-half-steps)
        drone? (< (rand) 0.05)
        mode (if drone? :drone :explore)
        [dr dc] heading
        endpoint [(+ (first pos) (* sortie-half-steps dr))
                  (+ (second pos) (* sortie-half-steps dc))]]
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :flight-mode mode
           :explore-origin site-pos
           :explore-heading heading
           :explore-steps-remaining sortie-half-steps
           :flight-target-site endpoint
           :flight-origin-site site-pos)))

(defn- ensure-flight-target
  "If fighter at pos is at a refueling site with no flight-mode or target,
   refuel and assign either a regular leg or exploration flight."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))]
    (when (and unit (nil? (:flight-mode unit)) (nil? (:flight-target-site unit)))
      (when-let [site-pos (current-refueling-site pos)]
        (swap! atoms/game-map assoc-in (conj pos :contents :fuel) config/fighter-fuel)
        (if (>= (rand) 0.5)
          (assign-regular-leg pos site-pos)
          (assign-exploration-flight pos site-pos))))))

(defn- at-flight-target?
  "True if pos has reached the flight target. City: at position. Carrier: adjacent."
  [pos target]
  (let [target-cell (get-in @atoms/game-map target)]
    (or (= pos target)
        (and (= :carrier (get-in target-cell [:contents :type]))
             (<= (distance-to pos target) 1)))))

(defn- handle-arrival
  "Process arrival at target refueling site. Record leg, refuel, pick new leg.
   Returns current pos (no movement this step)."
  [pos unit]
  (let [target (:flight-target-site unit)
        origin (:flight-origin-site unit)]
    ;; Record completed leg (skip degenerate self-loops from returning sorties)
    (when (and origin (not= origin target))
      (swap! atoms/fighter-leg-records assoc #{origin target}
             {:last-flown @atoms/round-number}))
    ;; Refuel
    (swap! atoms/game-map assoc-in (conj pos :contents :fuel) config/fighter-fuel)
    ;; Clear exploration fields and pick new leg from target site
    (let [new-target (choose-leg target)]
      (swap! atoms/game-map update-in (conj pos :contents)
             #(-> %
                  (dissoc :explore-origin :explore-heading :explore-steps-remaining :flight-mode)
                  (assoc :flight-target-site new-target :flight-origin-site target))))
    pos))

(defn- navigate-toward-target
  "Move one step toward target, preferring unexplored cells when fuel allows."
  [pos target fuel]
  (let [passable (get-passable-neighbors pos)
        direct-dist (distance-to pos target)
        fuel-margin? (> fuel (+ direct-dist 2))
        unexplored-toward (when fuel-margin?
                            (seq (filter (fn [n]
                                          (and (not (occupied? n))
                                               (nil? (get-in @atoms/computer-map n))
                                               (<= (distance-to n target) (inc direct-dist))))
                                        passable)))
        next-pos (or (when unexplored-toward
                       (apply min-key (partial distance-to target) unexplored-toward))
                     (move-toward-with-sidestep pos target passable))]
    (when (and next-pos (core/move-unit-to pos next-pos))
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-pos :computer)
      (if (consume-fighter-fuel next-pos) next-pos nil))))

(defn- refuel-at-site
  "Refuel fighter in place, recording origin site for leg tracking."
  [pos site-pos]
  (swap! atoms/game-map assoc-in (conj pos :contents :fuel) config/fighter-fuel)
  (swap! atoms/game-map update-in (conj pos :contents)
         assoc :flight-origin-site site-pos)
  pos)

(defn- move-and-consume-toward
  "Move one step toward target and consume fuel. Returns new pos or nil."
  [pos target]
  (let [passable (get-passable-neighbors pos)
        closest (move-toward-with-sidestep pos target passable)]
    (when (and closest (core/move-unit-to pos closest))
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility closest :computer)
      (if (consume-fighter-fuel closest) closest nil))))

(defn- handle-low-fuel
  "Handle low-fuel: return to nearest refueling site or patrol desperately."
  [pos]
  (let [site (find-nearest-refueling-site pos)]
    (cond
      ;; At or adjacent to city → land
      (and site
           (= :city (:type (get-in @atoms/game-map site)))
           (<= (distance-to pos site) 1))
      (land-at-city pos site)

      ;; Adjacent to non-city refueling site (carrier) → refuel in place
      (and site (<= (distance-to pos site) 1))
      (refuel-at-site pos site)

      ;; Move toward nearest site
      site
      (move-and-consume-toward pos site)

      ;; No site → patrol desperately
      :else
      (when-let [new-pos (do-patrol pos)]
        (if (consume-fighter-fuel new-pos) new-pos nil)))))

(defn- handle-patrol
  "Execute one patrol step, consuming fuel."
  [pos]
  (when-let [new-pos (do-patrol pos)]
    (if (consume-fighter-fuel new-pos) new-pos nil)))

(defn- exploring?
  "True if unit is on an outbound exploration sortie with steps remaining."
  [unit]
  (and (= :explore (:flight-mode unit))
       (pos? (:explore-steps-remaining unit 0))))

(defn- handle-exploration-or-drone
  "Dispatch to drone-step or explore-step based on flight-mode."
  [pos unit]
  (if (= :drone (:flight-mode unit))
    (drone-step pos unit)
    (explore-step pos unit)))

(defn- move-fighter-toward-objective
  "Non-combat movement priorities: explore/drone > arrival > low fuel > navigate > patrol. CC=5."
  [pos unit]
  (let [fuel (:fuel unit config/fighter-fuel)
        target (:flight-target-site unit)]
    (cond
      ;; Priority: Exploration sortie or drone in progress
      (or (exploring? unit) (= :drone (:flight-mode unit)))
      (handle-exploration-or-drone pos unit)

      ;; Arrived at target refueling site
      (and target (at-flight-target? pos target))
      (handle-arrival pos unit)

      ;; Low fuel → return to nearest refueling site
      (should-return-to-refuel? pos fuel)
      (handle-low-fuel pos)

      ;; Navigate toward target
      target
      (navigate-toward-target pos target fuel)

      :else
      (handle-patrol pos))))

(defn- move-fighter-once
  "Execute one step of fighter priority logic. CC=2.
   Returns new position if moved and alive, :landed if landed, nil if died or stuck."
  [pos unit]
  (let [enemy-pos (find-adjacent-enemy pos)]
    (if enemy-pos
      (when-let [new-pos (attack-enemy pos enemy-pos)]
        (if (consume-fighter-fuel new-pos) new-pos nil))
      (move-fighter-toward-objective pos unit))))

(defn- fighter-at?
  "Returns true if a fighter exists at pos on the game map."
  [pos]
  (= :fighter (get-in @atoms/game-map (conj pos :contents :type))))

(defn- burn-stuck-fuel
  "Burns fuel for a stuck fighter at pos. Returns pos if survived, nil if died."
  [pos]
  (when (and (fighter-at? pos) (consume-fighter-fuel pos))
    pos))

(defn- step-fighter
  "Execute one step. Returns [new-pos :continue], [nil :stop], or recur-ready values."
  [current-pos]
  (when (fighter-at? current-pos)
    (let [unit (get-in @atoms/game-map (conj current-pos :contents))
          result (move-fighter-once current-pos unit)]
      (cond
        (= result :landed) nil
        result result
        :else (burn-stuck-fuel current-pos)))))

(defn process-fighter
  "Processes a computer fighter using VMS Empire style logic.
   Moves up to fighter-speed (8) cells per round, consuming fuel each step.
   Priority each step: Attack > Arrive at target > Return if low fuel > Navigate/Patrol
   Returns nil."
  [pos unit]
  (when (and unit (= :computer (:owner unit)) (= :fighter (:type unit)))
    (ensure-flight-target pos)
    (loop [current-pos pos
           steps-remaining fighter-speed]
      (when (pos? steps-remaining)
        (when-let [next-pos (step-fighter current-pos)]
          (recur next-pos (dec steps-remaining))))))
  nil)
