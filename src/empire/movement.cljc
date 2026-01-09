(ns empire.movement
  (:require [empire.atoms :as atoms]
            [empire.config :as config]))

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
          height (count @atoms/game-map)
          width (count (first @atoms/game-map))]
      (doseq [i (range height)
              j (range width)
              :when (ownership-predicate (get-in @atoms/game-map [i j]))]
        (doseq [di [-1 0 1]
                dj [-1 0 1]]
          (let [ni (max 0 (min (dec height) (+ i di)))
                nj (max 0 (min (dec width) (+ j dj)))]
            (swap! visible-map-atom assoc-in [ni nj] (get-in @atoms/game-map [ni nj]))))))))

(defn update-cell-visibility [pos owner]
  "Updates visibility around a specific cell for the given owner."
  (let [visible-map-atom (if (= owner :player) atoms/player-map atoms/computer-map)
        [x y] pos]
    (when @visible-map-atom
      (let [height (count @atoms/game-map)
            width (count (first @atoms/game-map))]
        (doseq [di [-1 0 1]
                dj [-1 0 1]]
          (let [ni (max 0 (min (dec height) (+ x di)))
                nj (max 0 (min (dec width) (+ y dj)))
                game-cell (get-in @atoms/game-map [ni nj])]
            (swap! visible-map-atom assoc-in [ni nj] game-cell)))))))

(defn wake-before-move [unit next-cell]
  (cond
    (:contents next-cell)
    [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :somethings-in-the-way) true]

    (and (= (:type unit) :army) (= (:type next-cell) :sea))
    [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :cant-move-into-water) true]

    (and (= (:type unit) :army) (= (:type next-cell) :city) (= (:city-status next-cell) :player))
    [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :cant-move-into-city) true]

    (and (= (:type unit) :fighter)
         (= (:type next-cell) :city)
         (config/hostile-city? (:city-status next-cell)))
    [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :fighter-over-defended-city) true]

    (and (config/naval-unit? (:type unit)) (= (:type next-cell) :land))
    [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :ships-cant-drive-on-land) true]

    :else [unit false]))

(defn near-hostile-city? [pos current-map]
  (some (fn [[di dj]]
          (let [ni (+ (first pos) di)
                nj (+ (second pos) dj)
                adj-cell (get-in @current-map [ni nj])]
            (and adj-cell
                 (= (:type adj-cell) :city)
                 (config/hostile-city? (:city-status adj-cell)))))
        (for [di [-1 0 1] dj [-1 0 1]] [di dj])))

(defn friendly-city-in-range? [pos max-dist current-map]
  (let [[px py] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[i j]]
            (let [cell (get-in @current-map [i j])]
              (and (= (:type cell) :city)
                   (= (:city-status cell) :player)
                   (<= (max (abs (- i px)) (abs (- j py))) max-dist))))
          (for [i (range height) j (range width)] [i j]))))

(defn wake-after-move [unit final-pos current-map]
  (let [is-at-target? (= final-pos (:target unit))
        dest-cell (get-in @current-map final-pos)
        [unit-wakes?
         reason refuel?
         shot-down?] (case (:type unit)
                       :army [(near-hostile-city? final-pos current-map) :army-found-city false false]
                       :fighter (let [entering-city? (= (:type dest-cell) :city)
                                      friendly-city? (= (:city-status dest-cell) :player)
                                      hostile-city? (and entering-city? (not friendly-city?))
                                      fuel (:fuel unit config/fighter-fuel)
                                      low-fuel? (<= fuel 1)
                                      bingo-fuel? (and (<= fuel (quot config/fighter-fuel 4))
                                                       (friendly-city-in-range? final-pos fuel current-map))]
                                  (cond
                                    hostile-city? [true :fighter-shot-down false true]
                                    entering-city? [true :fighter-landed-and-refueled true false]
                                    low-fuel? [true :fighter-out-of-fuel false false]
                                    bingo-fuel? [true :fighter-bingo false false]
                                    :else [false nil false false]))
                       [false nil false false])
        wake-up? (or is-at-target? unit-wakes?)]
    (when shot-down?
      (reset! atoms/line3-message (:fighter-destroyed-by-city config/messages)))
    (if wake-up?
      (dissoc (cond-> (assoc unit :mode :awake)
                      (and unit-wakes? reason) (assoc :reason reason)
                      refuel? (assoc :fuel config/fighter-fuel)
                      shot-down? (assoc :hits 0 :steps-remaining 0)) :target)
      unit)))

(defn process-consumables [unit to-cell]
  (if (and unit (= (:type unit) :fighter))
    (if (= (:type to-cell) :city)
      unit
      (let [current-fuel (:fuel unit config/fighter-fuel)
            new-fuel (dec current-fuel)]
        (if (<= new-fuel -1)
          nil
          (assoc unit :fuel new-fuel))))
    unit))

(defn do-move [from-coords final-pos cell final-unit]
  (let [from-cell (dissoc cell :contents)
        to-cell (get-in @atoms/game-map final-pos)
        processed-unit (process-consumables final-unit to-cell)
        updated-to-cell (if processed-unit (assoc to-cell :contents processed-unit) (dissoc to-cell :contents))]
    (swap! atoms/game-map assoc-in from-coords from-cell)
    (swap! atoms/game-map assoc-in final-pos updated-to-cell)
    (update-cell-visibility final-pos (:owner (:contents cell)))))

(defn move-unit [from-coords target-coords cell current-map]
  (let [unit (:contents cell)
        next-pos (next-step-pos from-coords target-coords)
        next-cell (get-in @current-map next-pos)
        [unit woke?] (wake-before-move unit next-cell)]
    (if woke?
      (let [updated-cell (assoc cell :contents unit)]
        (swap! atoms/game-map assoc-in from-coords updated-cell)
        (update-cell-visibility from-coords (:owner unit)))
      ;; Normal move
      (let [final-unit (wake-after-move unit next-pos current-map)]
        (do-move from-coords next-pos cell final-unit)))))

(defn set-unit-movement [unit-coords target-coords]
  (let [first-cell (get-in @atoms/game-map unit-coords)
        unit (:contents first-cell)
        updated-contents (-> unit
                             (assoc :mode :moving :target target-coords)
                             (dissoc :reason))]
    (swap! atoms/game-map assoc-in unit-coords (assoc first-cell :contents updated-contents))))

(defn set-unit-mode [coords mode]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (dissoc (assoc unit :mode mode) :reason)
        updated-cell (assoc cell :contents updated-unit)]
    (swap! atoms/game-map assoc-in coords updated-cell)))

(defn set-explore-mode [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (-> unit
                         (assoc :mode :explore
                                :explore-steps config/explore-steps
                                :visited #{coords})
                         (dissoc :reason :target))]
    (swap! atoms/game-map assoc-in coords (assoc cell :contents updated-unit))))

(defn valid-explore-cell?
  "Returns true if a cell is valid for army exploration (land, no city, no unit)."
  [cell]
  (and cell
       (= :land (:type cell))
       (nil? (:contents cell))))

(defn adjacent-to-sea?
  "Returns true if the position has an adjacent sea cell."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (= :sea (:type (get-in @current-map [nx ny]))))))
          [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])))

(defn get-valid-explore-moves
  "Returns list of valid adjacent positions for exploration."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (for [[dx dy] [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]
          :let [nx (+ x dx)
                ny (+ y dy)
                cell (when (and (>= nx 0) (< nx height)
                                (>= ny 0) (< ny width))
                       (get-in @current-map [nx ny]))]
          :when (valid-explore-cell? cell)]
      [nx ny])))

(defn get-coastal-explore-moves
  "Returns valid moves that keep the army on coast (adjacent to sea)."
  [pos current-map]
  (filter #(adjacent-to-sea? % current-map)
          (get-valid-explore-moves pos current-map)))

(defn adjacent-to-unexplored?
  "Returns true if the position has an adjacent unexplored cell."
  [pos]
  (let [[x y] pos
        height (count @atoms/player-map)
        width (count (first @atoms/player-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (nil? (get-in @atoms/player-map [nx ny])))))
          [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])))

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
        on-coast? (adjacent-to-sea? pos current-map)
        coastal-moves (when on-coast? (filter #(adjacent-to-sea? % current-map) unvisited-moves))]
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
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        remaining-steps (dec (:explore-steps unit config/explore-steps))
        visited (or (:visited unit) #{})]
    (if (<= remaining-steps 0)
      ;; Wake up after 50 steps
      (do
        (swap! atoms/game-map assoc-in coords
               (assoc cell :contents (-> unit
                                         (assoc :mode :awake)
                                         (dissoc :explore-steps :visited))))
        nil)
      ;; Try to move (return nil to limit to one step per round)
      (if-let [next-pos (pick-explore-move coords atoms/game-map visited)]
        (let [next-cell (get-in @atoms/game-map next-pos)
              found-city? (near-hostile-city? next-pos atoms/game-map)
              moved-unit (if found-city?
                           (-> unit
                               (assoc :mode :awake :reason :army-found-city)
                               (dissoc :explore-steps :visited))
                           (-> unit
                               (assoc :explore-steps remaining-steps)
                               (assoc :visited (conj visited next-pos))))]
          (swap! atoms/game-map assoc-in coords (dissoc cell :contents))
          (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
          (update-cell-visibility next-pos (:owner unit))
          nil)
        ;; Stuck - wake up
        (do
          (swap! atoms/game-map assoc-in coords
                 (assoc cell :contents (-> unit
                                           (assoc :mode :awake)
                                           (dissoc :explore-steps :visited))))
          nil)))))