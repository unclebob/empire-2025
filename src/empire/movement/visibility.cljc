(ns empire.movement.visibility
  (:require [empire.atoms :as atoms]))

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

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units.
   Optimized to use direct vector access instead of get-in/assoc-in."
  [visible-map-atom owner]
  (when-let [visible-map @visible-map-atom]
    (let [game-map @atoms/game-map
          ownership-predicate (if (= owner :player) is-players? is-computers?)
          height (count game-map)
          width (count (first game-map))
          max-i (dec height)
          max-j (dec width)]
      (loop [i 0
             result (transient (mapv transient visible-map))]
        (if (>= i height)
          (reset! visible-map-atom (mapv persistent! (persistent! result)))
          (let [game-row (game-map i)]
            (recur
              (inc i)
              (loop [j 0
                     res result]
                (if (>= j width)
                  res
                  (if (ownership-predicate (game-row j))
                    ;; This cell is owned - reveal 9 surrounding cells
                    (let [min-di (if (zero? i) 0 -1)
                          max-di (if (= i max-i) 0 1)
                          min-dj (if (zero? j) 0 -1)
                          max-dj (if (= j max-j) 0 1)]
                      (recur
                        (inc j)
                        (loop [di min-di
                               r res]
                          (if (> di max-di)
                            r
                            (recur
                              (inc di)
                              (loop [dj min-dj
                                     r2 r]
                                (if (> dj max-dj)
                                  r2
                                  (let [ni (+ i di)
                                        nj (+ j dj)
                                        cell ((game-map ni) nj)]
                                    (recur
                                      (inc dj)
                                      (assoc! r2 ni (assoc! (r2 ni) nj cell)))))))))))
                    (recur (inc j) res)))))))))))

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
