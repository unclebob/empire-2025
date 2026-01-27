(ns empire.movement.visibility
  "Fog-of-war visibility management for player and computer maps.

   Each combatant maintains a separate visible map that gets revealed
   as their units move around. Units reveal cells within their visibility
   radius (typically 1 cell, or 2 for satellites).

   Key functions:
   - update-combatant-map: Bulk reveal all cells around owned units
   - update-cell-visibility: Reveal cells around a specific position"
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]))

(defn- reveal-surrounding-cells!
  "Reveals the 3x3 area around cell [i,j] in the transient result map.
   Clamps to map boundaries."
  [result game-map i j height width]
  (let [coords (for [row (range (max 0 (dec i)) (min height (+ i 2)))
                     col (range (max 0 (dec j)) (min width (+ j 2)))]
                 [row col])]
    (reduce (fn [r [row col]]
              (let [cell ((game-map row) col)]
                (assoc! r row (assoc! (r row) col cell))))
            result
            coords)))

(defn- process-map-cells
  "Iterates over all cells, revealing surroundings for owned cells.
   Returns the updated transient result."
  [result game-map ownership-predicate height width]
  (let [coords (for [i (range height)
                     j (range width)]
                 [i j])]
    (reduce (fn [res [i j]]
              (if (ownership-predicate ((game-map i) j))
                (reveal-surrounding-cells! res game-map i j height width)
                res))
            result
            coords)))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units.
   Optimized to use direct vector access instead of get-in/assoc-in."
  [visible-map-atom owner]
  (when-let [visible-map @visible-map-atom]
    (let [game-map @atoms/game-map
          ownership-predicate (if (= owner :player) map-utils/is-players? map-utils/is-computers?)
          height (count game-map)
          width (count (first game-map))
          transient-map (transient (mapv transient visible-map))
          updated (process-map-cells transient-map game-map ownership-predicate height width)]
      (reset! visible-map-atom (mapv persistent! (persistent! updated))))))

(defn update-cell-visibility
  "Updates visibility around a specific cell for the given owner.
   Satellites reveal two rectangular rings (distances 1 and 2)."
  [pos owner]
  (let [visible-map-atom (if (= owner :player) atoms/player-map atoms/computer-map)
        [x y] pos
        cell (get-in @atoms/game-map pos)
        is-satellite? (= :satellite (:type (:contents cell)))
        radius (if is-satellite? 2 1)]
    (when @visible-map-atom
      (let [height (count @atoms/game-map)
            width (count (first @atoms/game-map))]
        (doseq [di (range (- radius) (inc radius))
                dj (range (- radius) (inc radius))]
          (let [ni (+ x di)
                nj (+ y dj)]
            (when (and (>= ni 0) (< ni height)
                       (>= nj 0) (< nj width))
              (let [game-cell (get-in @atoms/game-map [ni nj])]
                (swap! visible-map-atom assoc-in [ni nj] game-cell)))))))))
