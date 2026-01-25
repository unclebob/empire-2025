(ns empire.computer.army
  "Computer army module - executes exploration missions assigned by Lieutenant."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.fsm.engine :as engine]
            [empire.fsm.base-establishment :as base]))

(def explore-steps 50)

;; --- Reporting to Lieutenant ---

(defn- find-lieutenant-for-army
  "Find the Lieutenant that should receive reports from this army.
   Currently returns the first Lieutenant (armies don't track their commander yet)."
  []
  (when-let [general @atoms/commanding-general]
    (first (:lieutenants general))))

(defn- update-lieutenant!
  "Update a Lieutenant in the General's lieutenants list."
  [updated-lt]
  (when-let [general @atoms/commanding-general]
    (swap! atoms/commanding-general
           update :lieutenants
           (fn [lts]
             (mapv (fn [lt]
                     (if (= (:name lt) (:name updated-lt))
                       updated-lt
                       lt))
                   lts)))))

(defn- find-adjacent-free-cities
  "Find free cities adjacent to the given position."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/computer-map neighbor)]
              (and (= :city (:type cell))
                   (= :free (:city-status cell)))))
          (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets some?)))

(defn- on-coastline?
  "Returns true if position is land adjacent to sea."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and (= :land (:type cell))
         (map-utils/adjacent-to-sea? pos atoms/game-map))))

(defn- report-discoveries!
  "Report any discoveries at the current position to the Lieutenant."
  [pos]
  (when-let [lt (find-lieutenant-for-army)]
    ;; Report free cities
    (doseq [city-pos (find-adjacent-free-cities pos)]
      (let [updated-lt (engine/post-event lt {:type :free-city-found
                                               :priority :high
                                               :data {:coords city-pos}})]
        (update-lieutenant! updated-lt)))
    ;; Report coastline/beach candidates
    (when (and (on-coastline? pos)
               (base/valid-beach-candidate? pos))
      (let [lt-fresh (find-lieutenant-for-army)  ; Re-fetch after possible update
            updated-lt (engine/post-event lt-fresh {:type :coastline-mapped
                                                     :priority :normal
                                                     :data {:coords pos}})]
        (update-lieutenant! updated-lt)))))

(defn- valid-explore-cell?
  "Returns true if a cell is valid for army exploration (land, no city, no unit)."
  [cell]
  (and cell
       (= :land (:type cell))
       (nil? (:contents cell))))

(defn- get-valid-explore-moves
  "Returns list of valid adjacent positions for exploration."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    valid-explore-cell?))

(defn- adjacent-to-unexplored?
  "Returns true if the position has an adjacent unexplored cell on computer-map."
  [pos]
  (map-utils/any-neighbor-matches? pos @atoms/computer-map map-utils/neighbor-offsets
                                   #(or (nil? %) (= :unexplored (:type %)))))

(defn- pick-explore-move
  "Picks the next explore move - prefers unexplored, then coast following, then random."
  [pos visited]
  (let [all-moves (get-valid-explore-moves pos)
        unvisited-moves (remove visited all-moves)
        unexplored-moves (filter adjacent-to-unexplored? unvisited-moves)
        on-coast? (map-utils/adjacent-to-sea? pos atoms/game-map)
        coastal-moves (when on-coast?
                        (filter #(map-utils/adjacent-to-sea? % atoms/game-map) unvisited-moves))]
    (cond
      (seq unexplored-moves) (rand-nth unexplored-moves)
      (seq coastal-moves) (rand-nth coastal-moves)
      (seq unvisited-moves) (rand-nth unvisited-moves)
      (seq all-moves) (rand-nth all-moves)
      :else nil)))

(defn- assign-exploration-mission
  "Assigns exploration mission to an awake army."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        updated-unit (-> unit
                         (assoc :mode :explore
                                :explore-steps explore-steps
                                :visited #{pos})
                         (dissoc :reason))]
    (swap! atoms/game-map assoc-in pos (assoc cell :contents updated-unit))))

(defn- execute-exploration
  "Executes one exploration step. Returns new position or nil."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        remaining-steps (dec (:explore-steps unit explore-steps))
        visited (or (:visited unit) #{})]
    (if (<= remaining-steps 0)
      ;; Done exploring - go back to awake
      (do
        (swap! atoms/game-map assoc-in pos
               (assoc cell :contents (-> unit
                                         (assoc :mode :awake)
                                         (dissoc :explore-steps :visited))))
        nil)
      ;; Try to move
      (if-let [next-pos (pick-explore-move pos visited)]
        (let [next-cell (get-in @atoms/game-map next-pos)
              moved-unit (-> unit
                             (assoc :explore-steps remaining-steps)
                             (assoc :visited (conj visited next-pos)))]
          (swap! atoms/game-map assoc-in pos (dissoc cell :contents))
          (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
          (visibility/update-cell-visibility next-pos :computer)
          ;; Report any discoveries at new position
          (report-discoveries! next-pos)
          next-pos)
        ;; Stuck - wake up
        (do
          (swap! atoms/game-map assoc-in pos
                 (assoc cell :contents (-> unit
                                           (assoc :mode :awake)
                                           (dissoc :explore-steps :visited))))
          nil)))))

(defn process-army
  "Processes a computer army's turn.
   - Awake armies get assigned exploration missions
   - Exploring armies execute one step
   Returns new position if moved, nil otherwise."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      (case (:mode unit)
        :awake (do (assign-exploration-mission pos) nil)
        :explore (execute-exploration pos)
        nil))))
