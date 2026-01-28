(ns empire.fsm.patrol-boat
  "Patrol Boat FSM - naval reconnaissance for world exploration.
   Explores oceans, maps coastlines, discovers continents, and reports enemies.
   Flees from threats rather than engaging."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.fsm.explorer-utils :as utils]))

;; --- Helper Functions ---

(defn- neighbors
  "Get all 8 neighboring positions."
  [[r c]]
  (for [dr [-1 0 1]
        dc [-1 0 1]
        :when (not (and (zero? dr) (zero? dc)))]
    [(+ r dr) (+ c dc)]))

(defn- valid-sea-cell?
  "Returns true if cell is valid sea (exists and is :sea type)."
  [game-map pos]
  (let [cell (get-in game-map pos)]
    (and cell (= :sea (:type cell)))))

(defn- land-at?
  "Returns true if the cell at pos is land or city."
  [ctx pos]
  (let [cell (get-in (:game-map ctx) pos)]
    (and cell (or (= :land (:type cell)) (= :city (:type cell))))))

(defn- enemy-ship-at?
  "Returns true if there's a player unit at the given position."
  [ctx pos]
  (let [cell (get-in (:game-map ctx) pos)
        contents (:contents cell)]
    (and contents (= :player (:owner contents)))))

(defn- unexplored-sea-at?
  "Returns true if the cell at pos is unexplored sea."
  [pos]
  (let [game-cell (get-in @atoms/game-map pos)
        comp-cell (get-in @atoms/computer-map pos)]
    (and game-cell
         (= :sea (:type game-cell))
         comp-cell
         (= :unexplored (:type comp-cell)))))

(defn- manhattan-distance
  "Calculate Manhattan distance between two positions."
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn- find-all-enemies
  "Find all enemy positions visible in game-map."
  [ctx]
  (let [game-map (:game-map ctx)
        height (count game-map)
        width (count (first game-map))]
    (for [r (range height)
          c (range width)
          :let [cell (get-in game-map [r c])
                contents (:contents cell)]
          :when (and contents (= :player (:owner contents)))]
      [r c])))

(defn- find-adjacent-enemies
  "Find all enemy positions adjacent to pos."
  [ctx pos]
  (filter (partial enemy-ship-at? ctx) (neighbors pos)))

(defn- find-flee-direction
  "Find a sea cell that moves away from enemies."
  [ctx pos enemies]
  (let [game-map (:game-map ctx)
        valid-neighbors (filter (partial valid-sea-cell? game-map) (neighbors pos))]
    (when (seq valid-neighbors)
      (if (empty? enemies)
        (first valid-neighbors)
        ;; Pick the one furthest from any enemy
        (let [scored (map (fn [p]
                            (let [min-dist (apply min (map #(manhattan-distance p %) enemies))]
                              [p min-dist]))
                          valid-neighbors)
              max-dist (apply max (map second scored))
              best (filter #(= max-dist (second %)) scored)]
          (first (rand-nth best)))))))

(defn- step-toward
  "Return next position one step toward target."
  [from to]
  (let [[fr fc] from
        [tr tc] to
        dr (cond (< fr tr) 1 (> fr tr) -1 :else 0)
        dc (cond (< fc tc) 1 (> fc tc) -1 :else 0)]
    [(+ fr dr) (+ fc dc)]))

;; --- Guards ---

(defn enemy-adjacent?
  "Guard: Enemy ship in adjacent cell."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (boolean (seq (find-adjacent-enemies ctx pos)))))

(defn land-adjacent?
  "Guard: Land cell adjacent (discovered new continent)."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (boolean (some (partial land-at? ctx) (neighbors pos)))))

(defn unexplored-sea-nearby?
  "Guard: Unexplored sea cell within range."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (boolean (some unexplored-sea-at? (neighbors pos)))))

(defn coast-complete?
  "Guard: Returned to starting position of coast follow (circumnavigated land)."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        start (get-in ctx [:entity :fsm-data :coastline-data :start-pos])
        visited (get-in ctx [:entity :fsm-data :coastline-data :coast-visited] #{})]
    (and (= pos start)
         (> (count visited) 4))))

(defn safe-distance?
  "Guard: No enemies within 3 cells."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        enemies (find-all-enemies ctx)]
    (not-any? #(<= (manhattan-distance pos %) 3) enemies)))

;; --- Actions ---

(defn- explore-sea-action
  "Action: Move to adjacent unexplored sea cell."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        unexplored (filter unexplored-sea-at? (neighbors pos))
        target (first unexplored)]
    (when target
      {:move-to target})))

(defn- seek-unexplored-action
  "Action: No adjacent unexplored - head toward distant unexplored area."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        game-map (:game-map ctx)
        ;; Simple: move in explore-direction if valid sea
        dir (get-in ctx [:entity :fsm-data :explore-direction])
        [r c] pos
        [dr dc] dir
        next-pos [(+ r dr) (+ c dc)]]
    (if (valid-sea-cell? game-map next-pos)
      {:move-to next-pos}
      ;; Try other directions
      (let [valid-neighbors (filter (partial valid-sea-cell? game-map) (neighbors pos))]
        (when (seq valid-neighbors)
          {:move-to (rand-nth valid-neighbors)})))))

(defn begin-coast-follow-action
  "Action: Found land - start following coastline, report discovery."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        land-pos (first (filter (partial land-at? ctx) (neighbors pos)))
        reported (get-in ctx [:entity :fsm-data :continents-reported] #{})]
    (if (contains? reported land-pos)
      ;; Already reported this continent
      {:coastline-data {:land-found land-pos
                        :coast-visited #{pos}
                        :start-pos pos}}
      ;; New continent discovery
      {:coastline-data {:land-found land-pos
                        :coast-visited #{pos}
                        :start-pos pos}
       :continents-reported (conj reported land-pos)
       :events [{:type :continent-found
                 :priority :high
                 :to lt-id
                 :data {:discovery-coords land-pos
                        :discovered-by :patrol-boat}}]})))

(defn- follow-coast-action
  "Action: Continue following coastline, keeping land on one side."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        game-map (:game-map ctx)
        visited (get-in ctx [:entity :fsm-data :coastline-data :coast-visited] #{})
        ;; Find sea cells adjacent to both current position and land
        valid-neighbors (filter (partial valid-sea-cell? game-map) (neighbors pos))
        ;; Prefer unvisited cells that are still adjacent to land
        coastal-neighbors (filter (fn [p]
                                    (some (partial land-at? ctx) (neighbors p)))
                                  valid-neighbors)
        unvisited (remove visited coastal-neighbors)
        next-pos (if (seq unvisited)
                   (first unvisited)
                   (first coastal-neighbors))]
    (when next-pos
      {:move-to next-pos
       :coastline-data (update (get-in ctx [:entity :fsm-data :coastline-data])
                               :coast-visited conj next-pos)})))

(defn flee-and-report-action
  "Action: Enemy detected - report and flee."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        enemies (find-adjacent-enemies ctx pos)
        reported (get-in ctx [:entity :fsm-data :enemies-reported] #{})
        new-enemies (remove reported enemies)
        flee-pos (find-flee-direction ctx pos enemies)]
    {:move-to (or flee-pos pos)
     :enemies-reported (into reported new-enemies)
     :events (mapv (fn [e]
                     {:type :enemy-spotted
                      :priority :high
                      :to lt-id
                      :data {:enemy-coords e
                             :spotted-by :patrol-boat}})
                   new-enemies)}))

(defn- flee-action
  "Action: Continue fleeing from enemy."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        enemies (find-all-enemies ctx)
        nearby (filter #(<= (manhattan-distance pos %) 4) enemies)
        flee-pos (find-flee-direction ctx pos nearby)]
    {:move-to (or flee-pos pos)}))

(defn resume-explore-action
  "Action: Return to exploration after coast follow or fleeing."
  [_ctx]
  {:coastline-data nil})

;; --- FSM Definition ---

(def patrol-boat-fsm
  "FSM transitions for patrol boat exploration.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :exploring        - Moving through sea, seeking unexplored areas
   - :following-coast  - Discovered land, mapping coastline
   - :fleeing          - Enemy detected, moving away"
  [;; Exploring transitions
   [:exploring  enemy-adjacent?        :fleeing          flee-and-report-action]
   [:exploring  land-adjacent?         :following-coast  begin-coast-follow-action]
   [:exploring  unexplored-sea-nearby? :exploring        explore-sea-action]
   [:exploring  utils/always                 :exploring        seek-unexplored-action]

   ;; Following-coast transitions
   [:following-coast  enemy-adjacent?  :fleeing    flee-and-report-action]
   [:following-coast  coast-complete?  :exploring  resume-explore-action]
   [:following-coast  utils/always           :following-coast  follow-coast-action]

   ;; Fleeing transitions
   [:fleeing  safe-distance?  :exploring  resume-explore-action]
   [:fleeing  utils/always          :fleeing    flee-action]])

;; --- Create Patrol Boat ---

(defn create-patrol-boat
  "Create a patrol boat for world exploration.
   patrol-boat-id - unique identifier
   start-pos - [r c] starting position
   lieutenant-id - commanding officer for reports"
  [patrol-boat-id start-pos lieutenant-id]
  {:fsm patrol-boat-fsm
   :fsm-state :exploring
   :fsm-data {:patrol-boat-id patrol-boat-id
              :position start-pos
              :lieutenant-id lieutenant-id
              :explore-direction [0 1]
              :coastline-data nil
              :enemies-reported #{}
              :continents-reported #{}}
   :event-queue []})
