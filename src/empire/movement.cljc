(ns empire.movement
  (:require [empire.atoms :as atoms]))

(defn is-players?
  "Returns true if the cell is owned by the player."
  [cell]
  (or (= (:city-status cell) :player)
      (= (:owner (:contents cell)) :player)))

(defn is-computers?
  "Returns true if the cell is owned by the computer."
  [cell]
  (or (= (:city-status cell) :computer)
      (= (:owner (:contents cell)) :computer)))

(defn next-step-pos [pos target]
  (let [[x y] pos
        [tx ty] target
        dx (cond (zero? (- tx x)) 0
                 (pos? (- tx x)) 1
                 :else -1)
        dy (cond (zero? (- ty y)) 0
                 (pos? (- ty y)) 1
                 :else -1)]
    [(+ x dx) (+ y dy)]))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units."
  [visible-map-atom owner]
  (when @visible-map-atom
    (let [ownership-predicate (if (= owner :player) is-players? is-computers?)
          game-map-val @atoms/game-map
          height (count game-map-val)
          width (count (first game-map-val))]
      (doseq [i (range height)
              j (range width)
              :when (ownership-predicate (get-in game-map-val [i j]))]
        (doseq [di [-1 0 1]
                dj [-1 0 1]]
          (let [ni (max 0 (min (dec height) (+ i di)))
                nj (max 0 (min (dec width) (+ j dj)))]
            (swap! visible-map-atom assoc-in [ni nj] (get-in game-map-val [ni nj]))))))))

(defn update-cell-visibility [pos owner]
  "Updates visibility around a specific cell for the given owner."
  (let [visible-map-atom (if (= owner :player) atoms/player-map atoms/computer-map)
        [x y] pos]
    (when @visible-map-atom
      (let [game-map-val @atoms/game-map
            height (count game-map-val)
            width (count (first game-map-val))]
        (doseq [di [-1 0 1]
                dj [-1 0 1]]
          (let [ni (max 0 (min (dec height) (+ x di)))
                nj (max 0 (min (dec width) (+ y dj)))]
            (swap! visible-map-atom assoc-in [ni nj] (get-in game-map-val [ni nj]))))))))

(defn move-unit [from-coords target-coords cell current-map]
  (let [unit (:contents cell)
        next-pos (next-step-pos from-coords target-coords)
        next-cell (get-in current-map next-pos)]
    (if (= (:type next-cell) :sea)
      ;; Wake up without moving
      (let [woken-unit (dissoc (assoc unit :mode :awake) :target)
            updated-cell (assoc cell :contents woken-unit)]
        (swap! atoms/game-map assoc-in from-coords updated-cell)
        (update-cell-visibility from-coords (:owner unit)))
      ;; Normal move
      (let [is-at-target (= next-pos target-coords)
            final-pos (if is-at-target target-coords next-pos)
            ;; Check if near enemy or free city
            near-enemy-city? (some (fn [[di dj]]
                                     (let [ni (+ (first final-pos) di)
                                           nj (+ (second final-pos) dj)
                                           adj-cell (get-in current-map [ni nj])]
                                       (and adj-cell
                                            (= (:type adj-cell) :city)
                                            (#{:free :computer} (:city-status adj-cell)))))
                                   (for [di [-1 0 1] dj [-1 0 1]] [di dj]))
            wake-up? (or is-at-target near-enemy-city?)
            updated-unit (if wake-up?
                           (dissoc (assoc unit :mode :awake) :target)
                           unit)
            from-cell (assoc cell :contents nil)
            to-cell (or (get-in current-map final-pos) {:type :land})
            updated-to-cell (assoc to-cell :contents updated-unit)]
        (swap! atoms/game-map assoc-in from-coords from-cell)
        (swap! atoms/game-map assoc-in final-pos updated-to-cell)
        (update-cell-visibility final-pos (:owner unit))))))

(defn move-units []
  (let [current-map @atoms/game-map
        moves (for [x (range (count current-map))
                    y (range (count (get current-map 0)))
                    :let [coords [x y]
                          cell (get-in current-map coords)
                          contents (:contents cell)]
                    :when (and contents (= :moving (:mode contents)))]
                [coords (:target contents) cell])]
    (doseq [[from-coords target-coords cell] moves]
      (move-unit from-coords target-coords cell current-map))))

(defn set-unit-movement [unit-coords target-coords]
  (let [first-cell (get-in @atoms/game-map unit-coords)
        updated-contents (assoc (:contents first-cell) :mode :moving :target target-coords)]
    (swap! atoms/game-map assoc-in unit-coords (assoc first-cell :contents updated-contents))))

(defn set-unit-mode [coords mode]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (assoc unit :mode mode)
        updated-cell (assoc cell :contents updated-unit)]
    (swap! atoms/game-map assoc-in coords updated-cell)))