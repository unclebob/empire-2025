(ns empire.map
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.menus :as menus]
            [empire.production :as production]
            [quil.core :as q]))

(defn process-map
  "Processes the map by applying f to each cell, where f takes i j and the-map."
  [the-map f]
  (vec (for [i (range (count the-map))]
         (vec (for [j (range (count (first the-map)))]
                (f i j the-map))))))

(defn filter-map
  "Scans the map and returns positions [i j] where the predicate is true."
  [the-map pred]
  (for [i (range (count the-map))
        j (range (count (first the-map)))
        :let [current (get-in the-map [i j])]
        :when (pred current)]
    [i j]))

(defn is-players?
  "Returns true if the cell is owned by the player."
  [cell]
  (= (:owner cell) :player))

(defn is-computers?
  "Returns true if the cell is owned by the computer."
  [cell]
  (= (:owner cell) :computer))

(defn color-of [cell]
  (let
    [terrain-type (:type cell)
     cell-color (if (= terrain-type :city)
                  (case (:owner cell)
                    :player :player-city
                    :computer :computer-city
                    :free-city)
                  terrain-type)]
    (config/cell-colors cell-color)))

(defn draw-production-indicators
  "Draws production indicator for a city cell."
  [i j cell cell-w cell-h]
  (when (= :city (:type cell))
    (when-let [prod (@atoms/production [j i])]
      (when (and (map? prod) (:item prod))
        ;; Draw production progress thermometer
        (let [total (config/item-cost (:item prod))
              remaining (:remaining-rounds prod)
              progress (/ (- total remaining) (double total))
              base-color (color-of cell)
              dark-color (mapv #(* % 0.5) base-color)]
          (when (and (> progress 0) (> remaining 0))
            (apply q/fill (conj dark-color 128))            ; semi-transparent darker version
            (let [bar-height (* cell-h progress)]
              (q/rect (* j cell-w) (+ (* i cell-h) (- cell-h bar-height)) cell-w bar-height))))
        ;; Draw production character
        (apply q/fill config/production-color)
        (q/text-font @atoms/production-char-font)
        (q/text (config/item-chars (:item prod)) (+ (* j cell-w) 2) (+ (* i cell-h) 12))))))

(defn draw-map
  "Draws the map on the screen."
  [the-map]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        cols (count the-map)
        rows (count (first the-map))
        cell-w (/ map-w cols)
        cell-h (/ map-h rows)

        draw-unit (fn [col row cell]
                    (when-let [contents (get-in cell [:contents])]
                      (let [item (:type contents)]
                        (apply q/fill config/unit-color)
                        (q/text-font @atoms/production-char-font)
                        (q/text (config/item-chars item) (+ (* col cell-w) 2) (+ (* row cell-h) 12)))))]

    (doseq [col (range cols)
            row (range rows)]
      (let [cell (get-in the-map [col row])]
        (when (not= :unexplored (:type cell))
          (let [color (color-of cell)
                attention-coords @atoms/cells-needing-attention
                current [col row]
                should-flash-black (and (seq attention-coords) (= current (first attention-coords)))
                completed? (and (= (:type cell) :city) (:owner cell)
                                (let [prod (@atoms/production [col row])]
                                  (and (map? prod) (= (:remaining-rounds prod) 0))))
                blink-white? (and completed? (even? (quot (System/currentTimeMillis) 500)))
                blink-black? (and should-flash-black (= (mod (q/frame-count) 4) 0))
                final-color (cond blink-black? [0 0 0]
                                  blink-white? [255 255 255]
                                  :else color)]
            (apply q/fill final-color)
            (q/rect (* col cell-w) (* row cell-h) (inc cell-w) (inc cell-h))
            (draw-production-indicators row col cell cell-w cell-h)
            (draw-unit col row cell)))))))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units."
  [visible-map-atom ownership-predicate]
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
          (swap! visible-map-atom assoc-in [ni nj] (get-in game-map-val [ni nj])))))))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (update-combatant-map atoms/player-map is-players?))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (update-combatant-map atoms/computer-map is-computers?))

(defn on-coast?
  "Checks if a cell is adjacent to sea."
  [cell-x cell-y]
  (let [game-map-val @atoms/game-map
        cols (count game-map-val)
        rows (count (first game-map-val))]
    (some (fn [[dx dy]]
            (let [nx (+ cell-x dx)
                  ny (+ cell-y dy)]
              (and (>= nx 0) (< nx cols)
                   (>= ny 0) (< ny rows)
                   (= :sea (:type (get-in game-map-val [nx ny]))))))
          [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])))

(defn show-menu
  "Displays a menu with the given header and items positioned relative to a cell."
  [cell-x cell-y header items]
  (let [menu-width 150
        menu-height (+ 45 (* (count items) 20))
        [map-w map-h] @atoms/map-screen-dimensions
        cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))
        cell-left (* cell-x (/ map-w cols))
        cell-top (* cell-y (/ map-h rows))
        cell-bottom (+ cell-top (/ map-h rows))
        [_ text-y _ _] @atoms/text-area-dimensions
        screen-w (q/width)
        menu-x (min cell-left (- screen-w menu-width))
        menu-y (if (<= (+ cell-bottom menu-height) (min map-h text-y))
                 cell-bottom
                 (max 0 (- cell-top menu-height)))
        display-items (map config/production-items->strings items)]
    (reset! atoms/menu-cell [cell-x cell-y])
    (reset! atoms/menu-state {:visible true
                              :x menu-x
                              :y menu-y
                              :header header
                              :items display-items})))

(defn handle-city-click
  "Handles clicking on a city cell."
  [cell-x cell-y]
  (let [header "Produce"
        coastal-city? (on-coast? cell-x cell-y)
        basic-items [:army :fighter :satellite]
        coastal-items [:transport :patrol-boat :destroyer :submarine :carrier :battleship]
        all-items (cond-> basic-items coastal-city? (into coastal-items))
        items all-items]
    (show-menu cell-x cell-y header items)))

(defn handle-cell-click
  "Handles clicking on a map cell."
  [cell-x cell-y]
  (let [cell (get-in @atoms/game-map [cell-x cell-y])]
    (when (= (:owner cell) :player)
      (handle-city-click cell-x cell-y))))

(defn city?
  "Returns true if the cell at coords is a city."
  [[x y]]
  (= :city (:type (get-in @atoms/game-map [x y]))))

(defn determine-cell-coordinates
  "Converts mouse coordinates to map cell coordinates."
  [x y]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))
        cell-w (/ map-w cols)
        cell-h (/ map-h rows)]
    [(int (Math/floor (/ x cell-w))) (int (Math/floor (/ y cell-h)))]))

(defn mouse-down
  "Handles mouse click events."
  [x y]
  (let [[cell-x cell-y] (determine-cell-coordinates x y)]
    (menus/dismiss-existing-menu x y)
    (let [clicked-item (menus/handle-menu-click x y)]
      (when clicked-item
        (when (city? @atoms/menu-cell)
          (production/set-city-production @atoms/menu-cell clicked-item)
          (when (seq @atoms/cells-needing-attention)
            (swap! atoms/cells-needing-attention rest))))
      (when-not clicked-item
        (reset! atoms/last-clicked-cell [cell-x cell-y])
        (handle-cell-click cell-x cell-y)))))

(defn needs-attention?
  "Returns true if the cell at [i j] needs attention (awake unit or city with no production)."
  [i j]
  (let [cell (get-in @atoms/player-map [i j])
        mode (:mode (:contents cell))]
    (and (= (:owner cell) :player)
         (or (= mode :awake)
             (and (= (:type cell) :city)
                  (not (@atoms/production [i j])))))))

(defn cells-needing-attention
  "Returns coordinates of player's units and cities with no production."
  []
  (for [i (range (count @atoms/player-map))
        j (range (count (first @atoms/player-map)))
        :when (needs-attention? i j)]
    [i j]))

(defn do-a-round
  "Performs one round of game actions."
  []
  ;; Placeholder for round logic
  (swap! atoms/round-number inc)
  (production/update-production)
  (let [player-coords (cells-needing-attention)]
    (reset! atoms/cells-needing-attention player-coords)
    ;; Use player-coords as needed, e.g., for future logic
    (println "Player's units and cities at:" player-coords))
  (while (seq @atoms/cells-needing-attention)
    (let [first-cell-coords (first @atoms/cells-needing-attention)
          first-cell (get-in @atoms/game-map first-cell-coords)]
      (reset! atoms/message (if (:contents first-cell)
                              (str (name (:type (:contents first-cell))) " needs attention")
                              "City needs attention")))
    (Thread/sleep 100)
    (reset! atoms/message "")))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map))

(defn game-loop []
  (Thread/sleep ^long config/round-delay)
  (do-a-round)
  (recur))

