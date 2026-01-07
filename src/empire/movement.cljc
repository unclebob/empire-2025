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
  (if (and (= (:type unit) :army) (= (:type next-cell) :sea))
    [(dissoc (assoc unit :mode :awake) :target) true]
    [unit false]))

(defn wake-after-move [unit final-pos current-map is-at-target]
  (let [unit-wakes? (case (:type unit)
                      :army (and (not is-at-target)
                                 (some (fn [[di dj]]
                                         (let [ni (+ (first final-pos) di)
                                               nj (+ (second final-pos) dj)
                                               adj-cell (get-in @current-map [ni nj])]
                                           (and adj-cell
                                                (= (:type adj-cell) :city)
                                                (#{:free :computer} (:city-status adj-cell)))))
                                       (for [di [-1 0 1] dj [-1 0 1]] [di dj])))
                      false)
        wake-up? (or is-at-target unit-wakes?)
        reason (when (and (= (:type unit) :army) unit-wakes?) (:army-found-city config/messages))
        updated-unit (if wake-up?
                       (dissoc (cond-> (assoc unit :mode :awake)
                                 reason (assoc :reason reason)) :target)
                       unit)
        ;; Handle fighter fuel
        final-unit (let [is-fighter (= (:type updated-unit) :fighter)
                         current-fuel (if is-fighter (:fuel updated-unit config/fighter-fuel) nil)
                         new-fuel (if is-fighter (dec current-fuel) nil)]
                     (cond (and is-fighter (<= new-fuel -1)) nil
                           is-fighter (let [waking? (<= new-fuel 0)
                                            unit (if waking?
                                                   (assoc (dissoc (assoc updated-unit :mode :awake) :target) :reason (:fighter-out-of-fuel config/messages))
                                                   updated-unit)]
                                        (assoc unit :fuel new-fuel))
                           :else updated-unit))]
    final-unit))

(defn do-move [from-coords final-pos cell final-unit]
  (let [from-cell (dissoc cell :contents)
        to-cell (get-in @atoms/game-map final-pos)
        updated-to-cell (if final-unit (assoc to-cell :contents final-unit) (dissoc to-cell :contents))]
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
      (let [is-at-target (= next-pos target-coords)
            final-pos (if is-at-target target-coords next-pos)
            final-unit (wake-after-move unit final-pos current-map is-at-target)]
        (do-move from-coords final-pos cell final-unit)))))

(defn get-moves []
  (let [current-map @atoms/game-map]
    (for [x (range (count current-map))
          y (range (count (get current-map 0)))
          :let [coords [x y]
                cell (get-in current-map coords)
                contents (:contents cell)]
          :when (and contents (= :moving (:mode contents)))]
      [coords (:target contents) (:type contents)])))

(defn do-moves [moves]
  (doseq [[from-coords target-coords unit-type] moves]
    (let [steps (get config/unit-speed unit-type 1)]
      (loop [current-from from-coords
             current-target target-coords
             remaining-steps steps]
        (when (> remaining-steps 0)
          (let [current-cell (get-in @atoms/game-map current-from)]
            (move-unit current-from current-target current-cell atoms/game-map)
            ;; The unit moved to next-pos towards current-target
            (let [next-pos (next-step-pos current-from current-target)
                  moved-cell (get-in @atoms/game-map next-pos)
                  moved-contents (:contents moved-cell)]
              (when (and moved-contents (= :moving (:mode moved-contents)))
                (let [new-target (:target moved-contents)]
                  (recur next-pos new-target (dec remaining-steps)))))))))))

(defn move-units []
  (do-moves (get-moves)))

(defn set-unit-movement [unit-coords target-coords]
  (let [first-cell (get-in @atoms/game-map unit-coords)
        updated-contents (assoc (:contents first-cell) :mode :moving :target target-coords)]
    (swap! atoms/game-map assoc-in unit-coords (assoc first-cell :contents updated-contents))))

(defn set-unit-mode [coords mode]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (dissoc (assoc unit :mode mode) :reason)
        updated-cell (assoc cell :contents updated-unit)]
    (swap! atoms/game-map assoc-in coords updated-cell)))