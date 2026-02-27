;; mutation-tested: 2026-02-25
(ns empire.movement.visibility
  (:require [empire.atoms :as atoms]
            [empire.units.dispatcher :as dispatcher]))

(defn- is-players?
  "Returns true if the cell is owned by the player."
  [cell]
  (or (= (:city-status cell) :player)
      (= (:owner (:contents cell)) :player)))

(defn- is-computers?
  "Returns true if the cell is owned by the computer."
  [cell]
  (or (= (:city-status cell) :computer)
      (= (:owner (:contents cell)) :computer)))

(defn- reveal-surrounding-cells!
  "Reveals cells within radius around cell [i,j] in the transient result map.
   Clamps to map boundaries."
  [result game-map i j height width radius]
  (let [coords (for [row (range (max 0 (- i radius)) (min height (+ i radius 1)))
                     col (range (max 0 (- j radius)) (min width (+ j radius 1)))]
                 [row col])]
    (reduce (fn [r [row col]]
              (let [cell ((game-map row) col)]
                (assoc! r row (assoc! (r row) col cell))))
            result
            coords)))

(defn- cell-visibility-radius
  "Returns the visibility radius for a cell based on its contents."
  [cell]
  (if-let [unit-type (:type (:contents cell))]
    (dispatcher/visibility-radius unit-type)
    1))

(defn- process-map-cells
  "Iterates over all cells, revealing surroundings for owned cells.
   Returns the updated transient result."
  [result game-map ownership-predicate height width]
  (let [coords (for [i (range height)
                     j (range width)]
                 [i j])]
    (reduce (fn [res [i j]]
              (let [cell ((game-map i) j)]
                (if (ownership-predicate cell)
                  (reveal-surrounding-cells! res game-map i j height width
                                             (cell-visibility-radius cell))
                  res)))
            result
            coords)))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units.
   Optimized to use direct vector access instead of get-in/assoc-in."
  [visible-map-atom owner]
  (when-let [visible-map @visible-map-atom]
    (let [game-map @atoms/game-map
          ownership-predicate (if (= owner :player) is-players? is-computers?)
          height (count game-map)
          width (count (first game-map))
          transient-map (transient (mapv transient visible-map))
          updated (process-map-cells transient-map game-map ownership-predicate height width)]
      (reset! visible-map-atom (mapv persistent! (persistent! updated))))))

(defn- in-bounds?
  "Returns true if [row col] is within [0,height) x [0,width)."
  [row col height width]
  (and (>= row 0) (< row height)
       (>= col 0) (< col width)))

(defn- should-stamp-country?
  "Returns truthy if unit is a computer army with a country-id."
  [unit]
  (and unit
       (= :army (:type unit))
       (= :computer (:owner unit))
       (:country-id unit)))

(defn- was-unexplored?
  "Returns true if the cell at [row col] in visible-map is nil or unexplored."
  [visible-map row col]
  (let [vis-cell (get-in visible-map [row col])]
    (or (nil? vis-cell)
        (= :unexplored (:type vis-cell)))))

(defn- reveal-cell!
  "Reveals game-cell at [row col] in visible-map-atom.
   If stamp-id is truthy and cell was unexplored land, stamps its country-id."
  [visible-map-atom row col game-cell stamp-id visible-map]
  (swap! visible-map-atom assoc-in [row col] game-cell)
  (when (and stamp-id
             (was-unexplored? visible-map row col)
             (= :land (:type game-cell)))
    (swap! atoms/game-map assoc-in [row col :country-id] stamp-id)))

(defn- should-track-free-city?
  "Returns true if owner is computer and unit is not an army."
  [owner unit-type]
  (and (= owner :computer)
       (not= :army unit-type)))

(defn- newly-discovered-free-city?
  "Returns true if game-cell is a free city and the same position
   on visible-map was unexplored."
  [visible-map row col game-cell]
  (and (= :city (:type game-cell))
       (= :free (:city-status game-cell))
       (was-unexplored? visible-map row col)))

(defn update-cell-visibility
  "Updates visibility around a specific cell for the given owner.
   Satellites reveal two rectangular rings (distances 1 and 2).
   When unit is a computer army with country-id, stamps newly-revealed land cells."
  ([pos owner] (update-cell-visibility pos owner nil))
  ([pos owner unit]
   (let [visible-map-atom (if (= owner :player) atoms/player-map atoms/computer-map)
         [x y] pos
         cell (get-in @atoms/game-map pos)
         radius (if (= :satellite (:type (:contents cell))) 2 1)
         stamp-id (should-stamp-country? unit)
         track-cities? (should-track-free-city? owner (:type (:contents cell)))]
     (when @visible-map-atom
       (let [height (count @atoms/game-map)
             width (count (first @atoms/game-map))
             visible-map @visible-map-atom]
         (doseq [di (range (- radius) (inc radius))
                 dj (range (- radius) (inc radius))]
           (let [ni (+ x di)
                 nj (+ y dj)]
             (when (in-bounds? ni nj height width)
               (let [game-cell (get-in @atoms/game-map [ni nj])]
                 (reveal-cell! visible-map-atom ni nj
                               game-cell stamp-id visible-map)
                 (when (and track-cities?
                            (newly-discovered-free-city?
                              visible-map ni nj game-cell))
                   (swap! atoms/land-ho-targets conj [ni nj])))))))))))