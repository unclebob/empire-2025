;; mutation-tested: 2026-02-25
(ns empire.movement.explore
  (:require [empire.config.core :as config]
            [empire.application.state-access :as sa]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.movement.wake-conditions :as wake]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

(defn- read-runtime-state
  [k]
  (sa/read-state k))

(defn- world-atom []
  (sa/world-atom))

(defn valid-explore-cell?
  "Returns true if a cell is valid for army exploration (land, no city, no unit)."
  [cell]
  (and cell
       (= :land (:type cell))
       (nil? (:contents cell))))

(defn get-valid-explore-moves
  "Returns list of valid adjacent positions for exploration."
  [pos current-map]
  (map-utils/get-matching-neighbors pos (map-utils/resolve-map-source current-map) map-utils/neighbor-offsets
                                    valid-explore-cell?))

(defn adjacent-to-unexplored?
  "Returns true if the position has an adjacent unexplored cell."
  [pos]
  (map-utils/any-neighbor-matches? pos (read-runtime-state :player-map) map-utils/neighbor-offsets
                                   nil?))

(defn get-unexplored-explore-moves
  "Returns valid moves that are adjacent to unexplored cells."
  [pos current-map]
  (filter adjacent-to-unexplored?
          (get-valid-explore-moves pos current-map)))

(defn pick-explore-move
  "Picks the next explore move - prefers unexplored, then coast following, then random.
   Avoids visited cells unless no other option."
  [pos current-map visited]
  (let [all-moves (get-valid-explore-moves pos current-map)
        unvisited-moves (remove visited all-moves)
        unexplored-moves (filter adjacent-to-unexplored? unvisited-moves)
        on-coast? (map-utils/adjacent-to-sea? pos current-map)
        coastal-moves (when on-coast? (filter #(map-utils/adjacent-to-sea? % current-map) unvisited-moves))]
    (cond
      ;; Prefer moves towards unexplored areas
      (seq unexplored-moves)
      (rand-nth unexplored-moves)

      ;; On coast with coastal moves available - follow coast
      (seq coastal-moves)
      (rand-nth coastal-moves)

      ;; Unvisited random walk
      (seq unvisited-moves)
      (rand-nth unvisited-moves)

      ;; All visited - allow revisiting as last resort
      (seq all-moves)
      (rand-nth all-moves)

      ;; No valid moves - stuck
      :else nil)))

(defn move-explore-unit
  "Moves an exploring army one step. Returns new position or nil if done/stuck."
  [coords]
  (let [world (current-world)
        cell (get-in world coords)
        unit (:contents cell)
        remaining-steps (dec (:explore-steps unit config/explore-steps))
        visited (or (:visited unit) #{})]
    (if (<= remaining-steps 0)
      ;; Wake up after 50 steps
      (do
        (update-game-map! assoc-in coords
                          (assoc cell :contents (-> unit
                                                    (assoc :mode :awake)
                                                    (dissoc :explore-steps :visited))))
        nil)
      ;; Try to move (return nil to limit to one step per round)
      (if-let [next-pos (pick-explore-move coords (world-atom) visited)]
        (let [next-cell (get-in (current-world) next-pos)
              found-city? (wake/near-hostile-city? next-pos (world-atom))
              moved-unit (if found-city?
                           (-> unit
                               (assoc :mode :awake :reason :army-found-city)
                               (dissoc :explore-steps :visited))
                           (-> unit
                               (assoc :explore-steps remaining-steps)
                               (assoc :visited (conj visited next-pos))))]
          (update-game-map! assoc-in coords (dissoc cell :contents))
          (update-game-map! assoc-in next-pos (assoc next-cell :contents moved-unit))
          (visibility/update-cell-visibility next-pos (:owner unit))
          nil)
        ;; Stuck - wake up
        (do
          (update-game-map! assoc-in coords
                            (assoc cell :contents (-> unit
                                                      (assoc :mode :awake)
                                                      (dissoc :explore-steps :visited))))
          nil)))))

(defn set-explore-mode
  "Sets a unit to explore mode with initial state."
  [coords]
  (let [cell (get-in (current-world) coords)
        unit (:contents cell)
        updated-unit (-> unit
                         (assoc :mode :explore
                                :explore-steps config/explore-steps
                                :visited #{coords})
                         (dissoc :reason :target))]
    (update-game-map! assoc-in coords (assoc cell :contents updated-unit))))
