(ns empire.computer.army
  "Computer army module - VMS Empire style army movement.
   Priority: Attack adjacent enemies > Find land objective > Board transport > Explore"
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
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

(defn- terminate-coast-walk
  "Switches army from coast-walk to sentry mode."
  [pos]
  (swap! atoms/game-map update-in (conj pos :contents)
         #(-> % (assoc :mode :sentry)
              (dissoc :coast-direction :coast-start :coast-visited))))

;; Standard army helpers

(defn- sovereign-passable?
  "Returns true if a computer army with country-id can enter the cell.
   Foreign land (different non-nil country-id) is blocked. Cities are always passable."
  [country-id cell]
  (and cell
       (#{:land :city} (:type cell))
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
      (core/attempt-conquest-computer army-pos enemy-pos)

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
            (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
            (core/stamp-territory enemy-pos (:survivor result))
            (visibility/update-cell-visibility army-pos :computer)
            (visibility/update-cell-visibility enemy-pos :computer)
            enemy-pos)
          ;; Attacker lost
          (do
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
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility target :computer)
    target))

(defn- sovereignty-passability-fn
  "Returns a passability function for A* that respects sovereignty for the given country-id."
  [country-id]
  (fn [cell] (sovereign-passable? country-id cell)))

(defn- move-toward-objective
  "Move army one step toward objective. If preferred step is occupied,
   try other empty neighbors sorted by distance to objective."
  [pos objective country-id]
  (let [pass-fn (when country-id (sovereignty-passability-fn country-id))
        preferred (pathfinding/next-step pos objective :army pass-fn country-id)]
    (or (when preferred (try-move pos preferred))
        ;; Preferred blocked or no path - try empty neighbors closest to objective
        (let [empty-neighbors (get-empty-passable-neighbors pos country-id)]
          (when (seq empty-neighbors)
            (let [sorted (sort-by #(core/distance % objective) empty-neighbors)]
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
        (core/board-transport pos transport-pos)
        (visibility/update-cell-visibility pos :computer)
        nil)  ; Army is now on transport, return nil
      ;; Move toward nearest loading transport (excluding parent transport)
      (when-let [transport-pos (core/find-loading-transport army-unload-id)]
        (move-toward-objective pos transport-pos country-id)))))

(defn- explore-randomly
  "Move toward any unexplored territory adjacent to computer's explored area.
   Only considers empty cells. Randomizes to avoid all armies picking the same cell."
  [pos country-id]
  (let [empty (get-empty-passable-neighbors pos country-id)
        frontier (filter core/adjacent-to-computer-unexplored? empty)]
    (when-let [target (if (seq frontier)
                        (rand-nth frontier)
                        (when (seq empty) (rand-nth empty)))]
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

(defn- find-nearest-unoccupied-coastal-cell
  "Finds nearest land cell with matching country-id, adjacent to sea, with no unit."
  [pos country-id]
  (when country-id
    (let [game-map @atoms/game-map]
      (first
        (sort-by #(core/distance pos %)
                 (for [i (range (count game-map))
                       j (range (count (first game-map)))
                       :let [cell (get-in game-map [i j])]
                       :when (and (= :land (:type cell))
                                  (= country-id (:country-id cell))
                                  (nil? (:contents cell))
                                  (adjacent-to-sea? [i j]))]
                   [i j]))))))

(defn- fill-coastal-cell
  "If army is on a coastal cell, go sentry. Otherwise move toward nearest unoccupied one."
  [pos country-id]
  (cond
    ;; Already on an empty coastal cell (not a city) → go sentry
    (and country-id (adjacent-to-sea? pos)
         (not= :city (:type (get-in @atoms/game-map pos))))
    (do (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
        pos)

    ;; Find nearest unoccupied coastal cell and move toward it
    :else
    (when-let [target (find-nearest-unoccupied-coastal-cell pos country-id)]
      (move-toward-objective pos target country-id))))

(defn- in-bounds? [pos]
  (let [[c r] pos
        game-map @atoms/game-map]
    (and (>= c 0) (>= r 0)
         (< c (count game-map))
         (< r (count (first game-map))))))

(defn- try-interior-move
  "Attempts to move in a direction, clearing direction if blocked or at coast."
  [pos target]
  (if (and (in-bounds? target)
           (#{:land :city} (:type (get-in @atoms/game-map target)))
           (try-move pos target))
    (do (when (adjacent-to-sea? target)
          (swap! atoms/game-map update-in (conj target :contents) dissoc :interior-explore-direction))
        target)
    (do (swap! atoms/game-map update-in (conj pos :contents) dissoc :interior-explore-direction)
        nil)))

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

(defn- process-random-explore
  "Moves army in stored random-explore direction. Goes sentry on coast or when blocked."
  [pos country-id]
  (if (and (adjacent-to-sea? pos)
           (not= :city (:type (get-in @atoms/game-map pos))))
    (do (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
        pos)
    (let [unit (get-in @atoms/game-map (conj pos :contents))
          [dc dr] (:random-explore-direction unit)
          [c r] pos
          target [(+ c dc) (+ r dr)]]
      (if (and (in-bounds? target)
               (sovereign-passable? country-id (get-in @atoms/game-map target))
               (nil? (:contents (get-in @atoms/game-map target)))
               (try-move pos target))
        (when (adjacent-to-sea? target)
          (swap! atoms/game-map assoc-in (conj target :contents :mode) :sentry)
          target)
        ;; Blocked or off-map → go sentry
        (do (swap! atoms/game-map assoc-in (conj pos :contents :mode) :sentry)
            pos)))))

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

(defn process-army
  "Processes a computer army's turn.
   Priority: Attack > Attack-target > Sentry > Coast-walk > Random-explore > Coastal fill
   Returns nil after processing - armies only move once per round."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      (let [enemy-pos (find-adjacent-enemy pos)
            country-id (:country-id unit)]
        (cond
          enemy-pos (attack-enemy pos enemy-pos)
          (:attack-target unit) (process-attack-target pos country-id)
          (= :sentry (:mode unit)) nil
          (= :coast-walk (:mode unit)) (process-coast-walk pos country-id)
          (= :random-explore (:mode unit)) (process-random-explore pos country-id)
          (:interior-explore-direction unit) (process-interior-explore pos country-id)
          :else (fill-coastal-cell pos country-id))))
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
      (let [available (remove #(contains? @assigned (:pos %)) armies)
            closest (take 6 (sort-by #(core/distance (:pos %) city) available))]
        (doseq [{:keys [pos]} closest]
          (swap! atoms/game-map assoc-in (conj pos :contents :attack-target) city)
          (swap! assigned conj pos))))))
