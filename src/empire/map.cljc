(ns empire.map
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.menus :as menus]
            [empire.movement :as movement]
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



(defn color-of [cell]
  (let
    [terrain-type (:type cell)
     cell-color (if (= terrain-type :city)
                  (case (:city-status cell)
                    :player :player-city
                    :computer :computer-city
                    :free :free-city)
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
                completed? (and (= (:type cell) :city) (not= :free (:city-status cell))
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

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (movement/update-combatant-map atoms/player-map :player))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (movement/update-combatant-map atoms/computer-map :computer))

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

(defn handle-city-click
  "Handles clicking on a city cell."
  [cell-x cell-y]
  (let [cell (get-in @atoms/game-map [cell-x cell-y])
        header (if (= (:city-status cell) :player) :production :city-info)
        coastal-city? (on-coast? cell-x cell-y)
        basic-items (if (= header :production)
                      [:army :fighter :satellite]
                      ["City Status" (name (:city-status cell))])
        coastal-items (if (= header :production)
                        [:transport :patrol-boat :destroyer :submarine :carrier :battleship]
                        [])
        all-items (cond-> basic-items coastal-city? (into coastal-items))
        items all-items]
    (menus/show-menu cell-x cell-y header items)))

(defn is-unit-needing-attention?
  "Returns true if there is an attention-needing unit."
  [attention-coords]
  (and (seq attention-coords)
       (let [first-cell (get-in @atoms/game-map (first attention-coords))]
         (:contents first-cell))))

(defn is-city-needing-attention?
  "Returns true if the cell needs city handling as the first attention item."
  [cell clicked-coords attention-coords]
  (and (= (:city-status cell) :player)
       (= (:type cell) :city)
       (= clicked-coords (first attention-coords))))


(defn needs-attention?
  "Returns true if the cell at [i j] needs attention (awake unit or city with no production)."
  [i j]
  (let [cell (get-in @atoms/player-map [i j])
        mode (:mode (:contents cell))]
    (and (or (= (:city-status cell) :player)
             (= (:owner (:contents cell)) :player))
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

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [cell-x cell-y clicked-coords attention-coords]
  (let [attn-coords (first attention-coords)]
    (if (= clicked-coords attn-coords)
      ;; Clicked on the unit: show menu
      (let [header :unit
            items [:explore :sentry]]
        (menus/show-menu cell-x cell-y header items))
      ;; Clicked elsewhere
      (let [attn-cell (get-in @atoms/game-map attn-coords)
            target-cell (get-in @atoms/game-map clicked-coords)
            [ax ay] attn-coords
            [cx cy] clicked-coords
            adjacent? (and (<= (abs (- ax cx)) 1) (<= (abs (- ay cy)) 1))]
        (if (and (= :army (:type (:contents attn-cell)))
                 adjacent?
                 (= (:type target-cell) :city)
                 (#{:free :computer} (:city-status target-cell)))
        ;; Attempt conquest
        (if (< (rand) 0.5)
          (let [city-cell (get-in @atoms/game-map clicked-coords)
                army-cell (get-in @atoms/game-map attn-coords)]
            (swap! atoms/game-map assoc-in attn-coords (dissoc army-cell :contents))
            (swap! atoms/game-map assoc-in clicked-coords (assoc city-cell :city-status :player))
            (movement/update-cell-visibility clicked-coords :player)
            (reset! atoms/cells-needing-attention (cells-needing-attention)))
          ;; Fail: remove army and display message
          (do
            (swap! atoms/game-map assoc-in attn-coords (dissoc attn-cell :contents))
            (movement/update-cell-visibility attn-coords :player)
            (reset! atoms/cells-needing-attention (cells-needing-attention))
            (reset! atoms/line3-message (:conquest-failed config/messages))))
        ;; Normal movement
          (movement/set-unit-movement attn-coords clicked-coords)))))
  (swap! atoms/cells-needing-attention rest))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (let [cell (get-in @atoms/game-map [cell-x cell-y])
        clicked-coords [cell-x cell-y]
        attention-coords @atoms/cells-needing-attention]
    (cond
      (is-unit-needing-attention? attention-coords)
      (handle-unit-click cell-x cell-y clicked-coords attention-coords)

      (is-city-needing-attention? cell clicked-coords attention-coords)
      (handle-city-click cell-x cell-y)

      (= (:type cell) :city)
      (handle-city-click cell-x cell-y)

      ;; Otherwise, do nothing
      :else nil)))

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

(defn on-map? [x y]
  "Returns true if the pixel coordinates are within the map display area."
  (let [[map-w map-h] @atoms/map-screen-dimensions]
    (and (>= x 0) (< x map-w)
         (>= y 0) (< y map-h))))

(defn mouse-down
  "Handles mouse click events."
  [x y]
  (reset! atoms/line3-message "")
  (let [[cell-x cell-y] (determine-cell-coordinates x y)]
    (menus/dismiss-existing-menu x y)
    (let [clicked-item (menus/handle-menu-click x y)]
      (when clicked-item
        (when (= :production (:header @atoms/menu-state))
          (production/set-city-production (:coords @atoms/menu-state) clicked-item)
          (when (seq @atoms/cells-needing-attention)
            (swap! atoms/cells-needing-attention rest)))
        (when (= :unit (:header @atoms/menu-state))
          (movement/set-unit-mode (:coords @atoms/menu-state) clicked-item)
          (swap! atoms/cells-needing-attention rest)))
      (when-not clicked-item
        (if (on-map? x y)
          (do
            (reset! atoms/last-clicked-cell [cell-x cell-y])
            (handle-cell-click cell-x cell-y))
          (reset! atoms/line3-message (:not-on-map config/messages)))))))

(defn handle-city-production-key [k]
  (when-let [item (config/key->production-item k)]
    (when-let [coords (first @atoms/cells-needing-attention)]
      (let [cell (get-in @atoms/game-map coords)]
        (when (and (= (:type cell) :city)
                   (= (:city-status cell) :player)
                   (not (:contents cell)))
          (production/set-city-production coords item)
          (swap! atoms/cells-needing-attention rest)
          true)))))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                contents (:contents cell)]
          :when (and contents (<= (:hits contents 1) 0))]
    (swap! atoms/game-map assoc-in [i j] (dissoc cell :contents))))

(defn do-a-round
  "Performs one round of game actions."
  []
  ;; Placeholder for round logic
  (swap! atoms/round-number inc)
  (remove-dead-units)
  (movement/move-units)
  (production/update-production)
  (reset! atoms/cells-needing-attention (cells-needing-attention))
  (while (seq @atoms/cells-needing-attention)
    (let [first-cell-coords (first @atoms/cells-needing-attention)
          first-cell (get-in @atoms/game-map first-cell-coords)
          [ax ay] first-cell-coords
          adjacent-enemy-city? (and (= :army (:type (:contents first-cell)))
                                    (some (fn [[di dj]]
                                            (let [ni (+ ax di)
                                                  nj (+ ay dj)
                                                  adj-cell (get-in @atoms/game-map [ni nj])]
                                              (and adj-cell
                                                   (= (:type adj-cell) :city)
                                                   (#{:free :computer} (:city-status adj-cell)))))
                                          (for [di [-1 0 1] dj [-1 0 1]] [di dj])))]
      (reset! atoms/message (if (:contents first-cell)
                              (let [unit (:contents first-cell)
                                    unit-name (name (:type unit))
                                    reason-key (or (:reason unit)
                                                   (when adjacent-enemy-city? :army-found-city))
                                    reason-str (when reason-key (reason-key config/messages))]
                                (str unit-name (:unit-needs-attention config/messages) (if reason-str (str " - " reason-str) "")))
                              (:city-needs-attention config/messages))))
    (Thread/sleep 100)
    (reset! atoms/message "")))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  )

(defn game-loop []
  (Thread/sleep ^long config/round-delay)
  (do-a-round)
  (recur))

