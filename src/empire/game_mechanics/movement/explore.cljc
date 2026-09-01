(ns empire.game-mechanics.movement.explore
  (:require [empire.config.core :as config]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

(defn- read-runtime-state
  [k]
  (sa/read-state k))

(defn valid-explore-cell?
  "Returns true if a cell is valid for army exploration (land, no city, no unit)."
  [cell]
  (map-utils/valid-empty-cell? :land cell))

(defn get-valid-explore-moves
  "Returns list of valid adjacent positions for exploration."
  [pos current-map]
  (map-utils/get-valid-empty-neighbor-moves pos current-map :land))

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
    (some #(when (seq %) (rand-nth %))
          [unexplored-moves coastal-moves unvisited-moves all-moves])))

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
                                                    (assoc :mode :awake :reason :steps-exhausted)
                                                    (dissoc :explore-steps :visited))))
        nil)
      ;; Try to move (return nil to limit to one step per round)
      (if-let [next-pos (pick-explore-move coords :game-map visited)]
        (let [next-cell (get-in (current-world) next-pos)
              found-city? (wake/near-hostile-city? next-pos :game-map)
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
                                                      (assoc :mode :awake :reason :steps-exhausted)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:08.525248-05:00", :module-hash "-1780020625", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-541209375"} {:id "defn-/update-game-map!", :kind "defn-", :line 8, :end-line nil, :hash "1805137569"} {:id "defn-/current-world", :kind "defn-", :line 12, :end-line nil, :hash "-640438772"} {:id "defn-/read-runtime-state", :kind "defn-", :line 16, :end-line nil, :hash "2315423"} {:id "defn/valid-explore-cell?", :kind "defn", :line 20, :end-line nil, :hash "-493955620"} {:id "defn/get-valid-explore-moves", :kind "defn", :line 25, :end-line nil, :hash "821431463"} {:id "defn/adjacent-to-unexplored?", :kind "defn", :line 30, :end-line nil, :hash "1658319555"} {:id "defn/get-unexplored-explore-moves", :kind "defn", :line 36, :end-line nil, :hash "1152911072"} {:id "defn/pick-explore-move", :kind "defn", :line 42, :end-line nil, :hash "1475213886"} {:id "defn/move-explore-unit", :kind "defn", :line 54, :end-line nil, :hash "-859763422"} {:id "defn/set-explore-mode", :kind "defn", :line 93, :end-line nil, :hash "750782466"}]}
;; clj-mutate-manifest-end
