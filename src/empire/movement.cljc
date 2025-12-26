(ns empire.movement
  (:require [empire.atoms :as atoms]))

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
  [visible-map-atom ownership-predicate]
  (when @visible-map-atom
    (let [game-map-val @atoms/game-map
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
  (prn 'move-unit from-coords target-coords cell)
  (let [unit (:contents cell)
        next-pos (next-step-pos from-coords target-coords)
        is-at-target (= next-pos target-coords)
        final-pos (if is-at-target target-coords next-pos)
        updated-unit (if is-at-target
                       (dissoc (assoc unit :mode :awake) :target)
                       unit)
        from-cell (assoc cell :contents nil)
        to-cell (or (get-in current-map final-pos) {:type :land :owner nil})
        updated-to-cell (assoc to-cell :contents updated-unit :owner (:owner cell))]
    (swap! atoms/game-map assoc-in from-coords from-cell)
    (swap! atoms/game-map assoc-in final-pos updated-to-cell)
    (update-cell-visibility final-pos (:owner cell))))

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