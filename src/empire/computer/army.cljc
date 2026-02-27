;; mutation-tested: 2026-02-23
(ns empire.computer.army
  "Computer army module - VMS Empire style army movement.
   Priority: Attack adjacent enemies > Find land objective > Board transport > Explore"
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.debug :as debug]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]))

;; Coast-walk helpers

(defn- adjacent-to-sea?
  "Returns true if position has at least one adjacent sea cell."
  [pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in @atoms/game-map neighbor))))
        (core/get-neighbors pos)))

(defn- count-unexplored-neighbors
  "Counts unexplored cells adjacent to position on computer-map."
  [pos]
  (count (filter (fn [neighbor]
                   (nil? (get-in @atoms/computer-map neighbor)))
                 (core/get-neighbors pos))))

(defn- update-backtrack
  "Adds pos to visited vector, keeping at most 10 entries."
  [visited pos]
  (let [v (conj (or visited []) pos)]
    (if (> (count v) 10)
      (subvec v (- (count v) 10))
      v)))

(defn- update-move-history
  "Adds pos to move-history vector, keeping at most 4 entries."
  [history pos]
  (let [v (conj (or history []) pos)]
    (if (> (count v) 4)
      (subvec v (- (count v) 4))
      v)))

(defn- terminate-coast-walk
  "Switches army from coast-walk to sentry (or awake if in a city)."
  [pos]
  (let [mode (if (= :city (:type (get-in @atoms/game-map pos))) :awake :sentry)]
    (swap! atoms/game-map update-in (conj pos :contents)
           #(-> % (assoc :mode mode)
                (dissoc :coast-direction :coast-start :coast-visited)))))

;; Standard army helpers

(defn- seed-coastal-registry
  "One-time full-map scan to populate coastal cell registry for country-id.
   Called only when the registry is empty for that country."
  [country-id]
  (let [gm @atoms/game-map
        coastal (for [i (range (count gm))
                      j (range (count (first gm)))
                      :let [cell (get-in gm [i j])]
                      :when (and (= :land (:type cell))
                                 (or (nil? (:country-id cell))
                                     (= country-id (:country-id cell)))
                                 (adjacent-to-sea? [i j]))]
                  [i j])]
    (swap! atoms/coastal-cells-by-country assoc country-id (set coastal))))

(defn- ensure-coastal-registry [country-id]
  (when (empty? (get @atoms/coastal-cells-by-country country-id))
    (seed-coastal-registry country-id)))

(defn- register-coastal-cells
  "Registers coastal land cells near pos for the given country-id.
   Checks pos + neighbors; adds any land cell adjacent to sea with matching country."
  [pos country-id]
  (when country-id
    (let [game-map @atoms/game-map
          coastal (filter (fn [p]
                            (let [cell (get-in game-map p)]
                              (and cell
                                   (= :land (:type cell))
                                   (or (nil? (:country-id cell))
                                       (= country-id (:country-id cell)))
                                   (adjacent-to-sea? p))))
                          (cons pos (core/get-neighbors pos)))]
      (when (seq coastal)
        (swap! atoms/coastal-cells-by-country update country-id
               (fn [s] (into (or s #{}) coastal)))))))

(defn- sovereign-passable?
  "Returns true if a computer army with country-id can enter the cell.
   Foreign land (different non-nil country-id) is blocked.
   Computer cities are blocked; free and player cities are passable."
  [country-id cell]
  (and cell
       (#{:land :city} (:type cell))
       (not= :computer (:city-status cell))
       (or (nil? country-id)
           (= :city (:type cell))
           (nil? (:country-id cell))
           (= country-id (:country-id cell)))))

(defn- get-passable-neighbors
  "Returns passable land neighbors for an army, respecting sovereignty."
  [pos country-id]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (sovereign-passable? country-id (get-in game-map neighbor)))
            (core/get-neighbors pos))))

(defn- get-empty-passable-neighbors
  "Returns passable land neighbors with no unit occupying them."
  [pos country-id]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (nil? (:contents cell))))
            (get-passable-neighbors pos country-id))))

(defn- find-nearest-unclaimed
  "Find nearest position from candidates not in claimed-objectives."
  [candidates pos]
  (let [unclaimed (remove @atoms/claimed-objectives candidates)]
    (when (seq unclaimed)
      (apply min-key #(core/distance pos %) unclaimed))))

(defn- find-adjacent-enemy
  "Finds an adjacent enemy unit or city to attack."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)]
                       (core/attackable-target? cell)))
                   (core/get-neighbors pos)))))

(defn- attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if army died."
  [army-pos enemy-pos]
  (let [enemy-cell (get-in @atoms/game-map enemy-pos)]
    (cond
      ;; Attacking a city
      (= :city (:type enemy-cell))
      (do (debug/log-computer-event! :army-attack-city army-pos {:target enemy-pos})
          (core/attempt-conquest-computer army-pos enemy-pos))

      ;; Attacking a unit
      (:contents enemy-cell)
      (let [attacker (get-in @atoms/game-map (conj army-pos :contents))
            defender (:contents enemy-cell)
            result (combat/resolve-combat attacker defender)]
        ;; Remove attacker from original position
        (swap! atoms/game-map update-in army-pos dissoc :contents)
        (if (= :attacker (:winner result))
          ;; Attacker won - move to enemy position
          (do
            (debug/log-computer-event! :army-kill army-pos
                                       {:to enemy-pos :killed (name (:type defender))})
            (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
            (core/stamp-territory enemy-pos (:survivor result))
            (visibility/update-cell-visibility army-pos :computer)
            (visibility/update-cell-visibility enemy-pos :computer)
            enemy-pos)
          ;; Attacker lost
          (do
            (debug/log-computer-event! :army-died army-pos
                                       {:killed-by (name (:type defender)) :at enemy-pos})
            (visibility/update-cell-visibility army-pos :computer)
            nil)))

      :else nil)))

(defn- find-city-objective
  "Find a city objective not already claimed by another army.
   Targets player and free cities only (not unexplored territory)."
  [pos]
  (let [cont-positions (land-objectives/flood-fill-continent pos)
        all-objectives (land-objectives/find-all-objectives-on-continent cont-positions)
        comp-map @atoms/computer-map
        player-cities (filter #(= :player (:city-status (get-in comp-map %))) all-objectives)
        free-cities (filter #(= :free (:city-status (get-in comp-map %))) all-objectives)
        cities (concat player-cities free-cities)
        target (or (find-nearest-unclaimed player-cities pos)
                   (find-nearest-unclaimed free-cities pos)
                   (when (seq cities)
                     (apply min-key #(core/distance pos %) cities)))]
    (when target
      (swap! atoms/claimed-objectives conj target)
      target)))

(defn- try-move
  "Attempt to move army from pos to target. Returns target if moved, nil if blocked."
  [pos target]
  (when (core/move-unit-to pos target)
    (debug/log-computer-event! :army-move pos {:to target})
    (swap! atoms/game-map update-in (conj target :contents :move-history)
           update-move-history pos)
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility target :computer)
    (register-coastal-cells target
                            (:country-id (get-in @atoms/game-map (conj target :contents))))
    target))

(defn- sovereignty-passability-fn
  "Returns a passability function for A* that respects sovereignty for the given country-id."
  [country-id]
  (fn [cell] (sovereign-passable? country-id cell)))

(defn- move-toward-objective
  "Move army one step toward objective. If preferred step is occupied,
   try other empty neighbors sorted by distance to objective.
   Filters out cells in move-history to prevent oscillation."
  [pos objective country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        history (set (:move-history unit))
        pass-fn (when country-id (sovereignty-passability-fn country-id))
        preferred (pathfinding/next-step pos objective :army pass-fn country-id)]
    (or (when (and preferred (not (history preferred)))
          (try-move pos preferred))
        (let [empty-neighbors (get-empty-passable-neighbors pos country-id)
              filtered (remove history empty-neighbors)]
          (when (seq filtered)
            (let [sorted (sort-by #(core/distance % objective) filtered)]
              (try-move pos (first sorted))))))))

(defn- find-and-board-transport
  "Look for a loading transport and move toward/board it.
   Excludes transports with matching unload-event-id to prevent
   re-boarding the same transport that unloaded this army."
  [pos country-id]
  (let [army (get-in @atoms/game-map (conj pos :contents))
        army-unload-id (:unload-event-id army)]
    ;; Check for adjacent loading transport first (excluding parent transport)
    (if-let [transport-pos (core/find-adjacent-loading-transport pos army-unload-id)]
      (do
        (debug/log-computer-event! :army-board pos {:transport transport-pos})
        (core/board-transport pos transport-pos)
        (visibility/update-cell-visibility pos :computer)
        nil)  ; Army is now on transport, return nil
      ;; Move toward nearest loading transport (excluding parent transport)
      (when-let [transport-pos (core/find-loading-transport army-unload-id)]
        (move-toward-objective pos transport-pos country-id)))))

(defn- explore-randomly
  "Move toward any unexplored territory adjacent to computer's explored area.
   Only considers empty cells. Randomizes to avoid all armies picking the same cell.
   Filters out cells in move-history to prevent oscillation."
  [pos country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        history (set (:move-history unit))
        empty (get-empty-passable-neighbors pos country-id)
        filtered (remove history empty)
        pool (if (seq filtered) filtered empty)
        frontier (filter core/adjacent-to-computer-unexplored? pool)]
    (when-let [target (if (seq frontier)
                        (rand-nth frontier)
                        (when (seq pool) (rand-nth pool)))]
      (try-move pos target))))

(defn- coast-walk-candidates
  "Returns empty land/city neighbors that are adjacent to sea."
  [pos country-id]
  (filter adjacent-to-sea? (get-empty-passable-neighbors pos country-id)))

(defn- process-coast-walk
  "Handles coast-walk movement. Returns new position or nil."
  [pos country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        coast-start (:coast-start unit)
        visited (set (:coast-visited unit))
        candidates (coast-walk-candidates pos country-id)]
    (if (empty? candidates)
      (do (terminate-coast-walk pos) nil)
      (let [not-visited (remove visited candidates)
            pool (if (seq not-visited) not-visited candidates)
            scored (map (fn [c] [c (count-unexplored-neighbors c)]) pool)
            best-score (apply max (map second scored))
            best (map first (filter #(= best-score (second %)) scored))
            target (if (= 1 (count best)) (first best) (rand-nth (vec best)))]
        (when (try-move pos target)
          (swap! atoms/game-map update-in (conj target :contents)
                 #(assoc % :coast-visited (update-backtrack (:coast-visited %) target)))
          (if (= target coast-start)
            (do (terminate-coast-walk target) target)
            target))))))

(defn- adjacent-to-computer-city?
  "Returns true if position has an adjacent computer city."
  [pos]
  (some (fn [neighbor]
          (let [cell (get-in @atoms/game-map neighbor)]
            (and (= :city (:type cell))
                 (= :computer (:city-status cell)))))
        (core/get-neighbors pos)))

(defn- find-nearest-unoccupied-coastal-cell
  "Finds nearest coastal cell from registry with matching country-id, no unit.
   Excludes cells adjacent to computer cities to avoid blocking production."
  [pos country-id]
  (when country-id
    (ensure-coastal-registry country-id)
    (let [coastal (get @atoms/coastal-cells-by-country country-id)
          game-map @atoms/game-map
          candidates (filter (fn [p]
                               (let [cell (get-in game-map p)]
                                 (and (= :land (:type cell))
                                      (or (nil? (:country-id cell))
                                          (= country-id (:country-id cell)))
                                      (nil? (:contents cell)))))
                             coastal)
          away-from-city (remove adjacent-to-computer-city? candidates)]
      (first (sort-by #(core/distance pos %)
                      (if (seq away-from-city) away-from-city candidates))))))

(defn- find-nearest-cell-close-to-coast
  "Finds nearest empty land cell within 1 step of a registered coastal cell.
   Used for transport queue — army lines up near coast."
  [pos country-id]
  (when country-id
    (ensure-coastal-registry country-id)
    (let [coastal (get @atoms/coastal-cells-by-country country-id)]
      (when (seq coastal)
        (let [expanded (into (set coastal) (mapcat core/get-neighbors coastal))
              game-map @atoms/game-map
              candidates (filter (fn [p]
                                   (let [cell (get-in game-map p)]
                                     (and (= :land (:type cell))
                                          (or (nil? (:country-id cell))
                                              (= country-id (:country-id cell)))
                                          (nil? (:contents cell)))))
                                 expanded)
              with-coast-dist (keep (fn [c]
                                      (let [d (if (contains? coastal c) 0
                                                (if (some (partial contains? coastal)
                                                          (core/get-neighbors c))
                                                  1 -1))]
                                        (when (>= d 0) [c d])))
                                    candidates)]
          (when (seq with-coast-dist)
            (let [best-coast-dist (apply min (map second with-coast-dist))
                  near-coast (map first (filter #(= best-coast-dist (second %))
                                                with-coast-dist))]
              (first (sort-by #(core/distance pos %) near-coast)))))))))

(defn- should-sentry-on-coast? [pos country-id]
  (and country-id
       (adjacent-to-sea? pos)
       (not= :city (:type (get-in @atoms/game-map pos)))
       (not (adjacent-to-computer-city? pos))))

(defn- can-settle-here? [pos country-id]
  (and country-id
       (adjacent-to-sea? pos)
       (not= :city (:type (get-in @atoms/game-map pos)))))

(defn- try-move-to-coastal-cell [pos country-id]
  (when-let [target (find-nearest-unoccupied-coastal-cell pos country-id)]
    (move-toward-objective pos target country-id)))

(defn- try-settle-on-coast [pos country-id]
  (when (can-settle-here? pos country-id)
    (debug/log-computer-event! :army-sentry pos {:reason :no-coastal-cell-available})
    (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
    pos))

(defn- try-queue-near-coast [pos country-id]
  (when-let [target (find-nearest-cell-close-to-coast pos country-id)]
    (or (move-toward-objective pos target country-id)
        (do (debug/log-computer-event! :army-sentry pos {:reason :transport-queue})
            (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
            pos))))

(defn- try-wake-nearby [pos]
  (when (pos? (core/wake-nearby-sentries pos 3))
    (debug/log-computer-event! :army-wake-sentries pos {:reason :stuck})
    nil))

(defn- fill-coastal-cell
  "If army is on a coastal cell away from cities, go sentry.
   Otherwise move toward nearest unoccupied coastal cell.
   If no coastal cell available, queue near coast and go sentry.
   If truly stuck, wake nearby sentries."
  [pos country-id]
  (if (should-sentry-on-coast? pos country-id)
    (do (debug/log-computer-event! :army-sentry pos {:reason :coastal-fill :country-id country-id})
        (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
        pos)
    (or (try-move-to-coastal-cell pos country-id)
        (try-settle-on-coast pos country-id)
        (try-queue-near-coast pos country-id)
        (try-wake-nearby pos))))

(defn- in-bounds? [pos]
  (let [[c r] pos
        game-map @atoms/game-map]
    (and (>= c 0) (>= r 0)
         (< c (count game-map))
         (< r (count (first game-map))))))

(defn- try-interior-move
  "Attempts to move in a direction, clearing direction if blocked or at coast."
  [pos target]
  (let [target-cell (get-in @atoms/game-map target)]
    (if (and (in-bounds? target)
             (#{:land :city} (:type target-cell))
             (not= :computer (:city-status target-cell))
             (try-move pos target))
    (do (when (adjacent-to-sea? target)
          (swap! atoms/game-map update-in (conj target :contents) dissoc :interior-explore-direction))
        target)
    (do (swap! atoms/game-map update-in (conj pos :contents) dissoc :interior-explore-direction)
        nil))))

(defn- start-interior-exploration
  "Picks a random direction and takes first step of interior exploration."
  [pos _country-id]
  (let [direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])
        [dc dr] direction
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (swap! atoms/game-map assoc-in (conj pos :contents :interior-explore-direction) direction)
    (try-interior-move pos target)))

(defn- process-interior-explore
  "Continues interior exploration in stored direction."
  [pos _country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        [dc dr] (:interior-explore-direction unit)
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (try-interior-move pos target)))

(defn- find-and-execute-land-action [pos country-id]
  (or (when-let [objective (find-city-objective pos)]
        (move-toward-objective pos objective country-id))
      (when (and country-id (< (rand) 1/3))
        (start-interior-exploration pos country-id))
      (fill-coastal-cell pos country-id)
      (find-and-board-transport pos country-id)
      (explore-randomly pos country-id)))

(defn- process-move-inland
  "Moves army one step away from coast. Switches to :random-explore once not adjacent to sea.
   If blocked, stays in :move-inland and skips the turn."
  [pos country-id]
  (if-not (adjacent-to-sea? pos)
    (do (swap! atoms/game-map update-in (conj pos :contents)
               assoc :mode :random-explore
                     :random-explore-direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])
                     :random-explore-rounds 0)
        nil)
    (let [candidates (filter (fn [n]
                               (let [cell (get-in @atoms/game-map n)]
                                 (and (sovereign-passable? country-id cell)
                                      (nil? (:contents cell))
                                      (not (adjacent-to-sea? n)))))
                             (core/get-neighbors pos))
          target (when (seq candidates) (rand-nth (vec candidates)))]
      (when target (try-move pos target)))))

(defn- at-sea-coast? [pos]
  (and (adjacent-to-sea? pos)
       (not= :city (:type (get-in @atoms/game-map pos)))))

(defn- clear-random-explore-state [pos]
  (swap! atoms/game-map update-in (conj pos :contents)
         #(-> % (assoc :mode :awake)
              (dissoc :random-explore-direction :random-explore-rounds))))

(defn- try-random-direction-move [pos country-id unit]
  (let [[dc dr] (:random-explore-direction unit)
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (when (and (in-bounds? target)
               (sovereign-passable? country-id (get-in @atoms/game-map target))
               (nil? (:contents (get-in @atoms/game-map target)))
               (try-move pos target))
      (when (at-sea-coast? target)
        (swap! atoms/game-map assoc-in (conj target :contents :mode) :sentry))
      target)))

(defn- handle-blocked-random-explore [pos country-id]
  (if (= :city (:type (get-in @atoms/game-map pos)))
    (when-let [neighbors (seq (get-empty-passable-neighbors pos country-id))]
      (try-move pos (rand-nth (vec neighbors))))
    (do (clear-random-explore-state pos) nil)))

(defn- process-random-explore
  "Moves army in stored random-explore direction. Goes sentry on coast or when blocked.
   Times out after 10 rounds and transitions to fill-coastal-cell."
  [pos country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        rounds (:random-explore-rounds unit 0)]
    (if (>= rounds 10)
      (do (clear-random-explore-state pos) nil)
      (do (swap! atoms/game-map update-in (conj pos :contents)
                 update :random-explore-rounds (fnil inc 0))
          (cond
            (at-sea-coast? pos)
            (do (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry) pos)

            :else
            (or (try-random-direction-move pos country-id unit)
                (handle-blocked-random-explore pos country-id)))))))

(defn- process-attack-target
  "Moves army toward its attack-target city. Clears target if conquered or gone."
  [pos country-id]
  (let [target (get-in @atoms/game-map (conj pos :contents :attack-target))
        comp-cell (get-in @atoms/computer-map target)]
    (if (and comp-cell
             (= :city (:type comp-cell))
             (#{:free :player} (:city-status comp-cell)))
      (move-toward-objective pos target country-id)
      ;; Target conquered or not visible → clear it
      (do (swap! atoms/game-map update-in (conj pos :contents) dissoc :attack-target)
          nil))))

(defn- exit-city
  "If army is in a city, move to an empty passable neighbor.
   Returns new position, or original pos if unable to exit."
  [pos country-id]
  (let [cell (get-in @atoms/game-map pos)]
    (if (= :city (:type cell))
      (if-let [exit (first (get-empty-passable-neighbors pos country-id))]
        (or (try-move pos exit) pos)
        pos)
      pos)))

(defn- process-sentry-in-city [pos country-id cell]
  (when (= :city (:type cell))
    (fill-coastal-cell pos country-id)))

(defn- process-unowned-army [pos]
  (or (when-let [obj (find-city-objective pos)]
        (move-toward-objective pos obj nil))
      (explore-randomly pos nil)))

(defn- build-army-actions [pos country-id mode unit cell enemy-pos]
  [[enemy-pos                              #(attack-enemy pos enemy-pos)]
   [(:attack-target unit)                  #(process-attack-target pos country-id)]
   [(= :coast-walk mode)                   #(process-coast-walk pos country-id)]
   [(= :move-inland mode)                  #(process-move-inland pos country-id)]
   [(= :random-explore mode)               #(process-random-explore pos country-id)]
   [(= :sentry mode)                       #(process-sentry-in-city pos country-id cell)]
   [(:interior-explore-direction unit)      #(process-interior-explore pos country-id)]
   [(nil? country-id)                      #(process-unowned-army pos)]
   [true                                   #(find-and-execute-land-action pos country-id)]])

(defn process-army
  "Processes a computer army's turn.
   Priority: Exit city > Attack > Attack-target > Coast-walk > Random-explore > Coastal fill
   Returns nil after processing - armies only move once per round."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      (let [pos (exit-city pos (:country-id unit))
            cell (get-in @atoms/game-map pos)
            unit (:contents cell)
            enemy-pos (find-adjacent-enemy pos)
            country-id (:country-id unit)
            mode (:mode unit)
            eid (:unload-event-id unit)]
        (debug/log-computer-event! :army-process pos
                                   (cond-> {:mode mode}
                                     country-id (assoc :cid country-id)
                                     eid (assoc :eid eid)))
        (let [actions (build-army-actions pos country-id mode unit cell enemy-pos)]
          (reduce (fn [_ [pred action]] (when pred (reduced (action)))) nil actions))))
    nil))

(defn- find-assignable-armies
  "Finds all computer armies eligible for city attack assignment (not coast-walking)."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :army (:type unit))
                     (not= :coast-walk (:mode unit)))]
      {:pos [i j] :unit unit})))

(defn- find-visible-target-cities
  "Finds free/player cities visible on the computer-map."
  []
  (let [comp-map @atoms/computer-map]
    (when (vector? comp-map)
      (for [i (range (count comp-map))
            j (range (count (first comp-map)))
            :let [cell (get-in comp-map [i j])]
            :when (and cell
                       (= :city (:type cell))
                       (#{:free :player} (:city-status cell)))]
        [i j]))))

(defn assign-city-attacks
  "Scans computer-map for visible free/player cities and assigns up to 6 closest armies each."
  []
  (let [cities (find-visible-target-cities)
        armies (find-assignable-armies)
        assigned (atom #{})]
    (doseq [city cities]
      (let [city-continent (land-objectives/flood-fill-continent city)
            available (remove #(contains? @assigned (:pos %)) armies)
            reachable (filter #(contains? city-continent (:pos %)) available)
            closest (take 6 (sort-by #(core/distance (:pos %) city) reachable))]
        (doseq [{:keys [pos]} closest]
          (swap! atoms/game-map assoc-in (conj pos :contents :attack-target) city)
          (swap! assigned conj pos))))))
