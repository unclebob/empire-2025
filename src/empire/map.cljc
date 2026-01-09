(ns empire.map
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.menus :as menus]
            [empire.movement :as movement]
            [empire.production :as production]
            [quil.core :as q]))

(declare item-processed)

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
                      (let [item (:type contents)
                            unit-color (case (:mode contents)
                                         :awake config/awake-unit-color
                                         :sentry config/sentry-unit-color
                                         :explore config/explore-unit-color
                                         config/sleeping-unit-color)]
                        (apply q/fill unit-color)
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

(defn attempt-conquest
  "Attempts to conquer a city with an army. Returns true if conquest was attempted."
  [army-coords city-coords]
  (let [army-cell (get-in @atoms/game-map army-coords)
        city-cell (get-in @atoms/game-map city-coords)]
    (if (< (rand) 0.5)
      (do
        (swap! atoms/game-map assoc-in army-coords (dissoc army-cell :contents))
        (swap! atoms/game-map assoc-in city-coords (assoc city-cell :city-status :player))
        (movement/update-cell-visibility city-coords :player))
      (do
        (swap! atoms/game-map assoc-in army-coords (dissoc army-cell :contents))
        (movement/update-cell-visibility army-coords :player)
        (reset! atoms/line3-message (:conquest-failed config/messages))))
    true))

(defn hostile-city? [target-coords]
  (let [target-cell (get-in @atoms/game-map target-coords)]
    (and (= (:type target-cell) :city)
         (#{:free :computer} (:city-status target-cell)))))

(defn attempt-fighter-overfly
  "Fighter flies over hostile city and gets shot down."
  [fighter-coords city-coords]
  (let [fighter-cell (get-in @atoms/game-map fighter-coords)
        fighter (:contents fighter-cell)
        city-cell (get-in @atoms/game-map city-coords)
        shot-down-fighter (assoc fighter :mode :awake :hits 0 :steps-remaining 0 :reason :fighter-shot-down)]
    (swap! atoms/game-map assoc-in fighter-coords (dissoc fighter-cell :contents))
    (swap! atoms/game-map assoc-in city-coords (assoc city-cell :contents shot-down-fighter))
    (reset! atoms/line3-message (:fighter-destroyed-by-city config/messages))
    true))

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
            unit-type (:type (:contents attn-cell))
            [ax ay] attn-coords
            [cx cy] clicked-coords
            adjacent? (and (<= (abs (- ax cx)) 1) (<= (abs (- ay cy)) 1))]
        (cond
          (and (= :army unit-type) adjacent? (hostile-city? clicked-coords))
          (attempt-conquest attn-coords clicked-coords)

          (and (= :fighter unit-type) adjacent? (hostile-city? clicked-coords))
          (attempt-fighter-overfly attn-coords clicked-coords)

          :else
          (movement/set-unit-movement attn-coords clicked-coords))
        (item-processed)))))

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
  [x y button]
  (reset! atoms/line3-message "")
  (if-not (on-map? x y)
    (reset! atoms/line3-message (:not-on-map config/messages))
    (let [[cell-x cell-y] (determine-cell-coordinates x y)
          cell (get-in @atoms/game-map [cell-x cell-y])]
      (case button
        :right
        (cond
          (and (= (:type cell) :city)
               (= (:city-status cell) :player))
          (handle-city-click cell-x cell-y)

          (= (:owner (:contents cell)) :player)
          (movement/set-unit-mode [cell-x cell-y] :awake)

          :else
          (reset! atoms/line2-message (str cell)))

        :left
        (do
          (menus/dismiss-existing-menu x y)
          (let [clicked-item (menus/handle-menu-click x y)]
            (when clicked-item
              (when (= :production (:header @atoms/menu-state))
                (production/set-city-production (:coords @atoms/menu-state) clicked-item)
                (item-processed))
              (when (= :unit (:header @atoms/menu-state))
                (movement/set-unit-mode (:coords @atoms/menu-state) clicked-item)
                (item-processed)))
            (when-not clicked-item
              (reset! atoms/last-clicked-cell [cell-x cell-y])
              (handle-cell-click cell-x cell-y))))

        nil))))

(def naval-units #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn- handle-city-production-key [k coords cell]
  (when-let [item (config/key->production-item k)]
    (when (and (= (:type cell) :city)
               (= (:city-status cell) :player)
               (not (:contents cell)))
      (let [[x y] coords
            coastal? (on-coast? x y)
            naval? (naval-units item)]
        (if (and naval? (not coastal?))
          (do
            (reset! atoms/line3-message (format "Must be coastal city to produce %s." (name item)))
            true)
          (do
            (production/set-city-production coords item)
            (item-processed)
            true))))))

(defn- calculate-extended-target [coords [dx dy]]
  (let [height (count @atoms/game-map)
        width (count (first @atoms/game-map))
        [x y] coords]
    (loop [tx x ty y]
      (let [nx (+ tx dx)
            ny (+ ty dy)]
        (if (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
          (recur nx ny)
          [tx ty])))))

(defn- handle-unit-movement-key [k coords cell]
  (let [direction (or (config/key->direction k)
                      (config/key->extended-direction k))
        extended? (config/key->extended-direction k)]
    (when direction
      (let [unit (:contents cell)]
        (when (and unit (= (:owner unit) :player))
          (let [[x y] coords
                [dx dy] direction
                adjacent-target [(+ x dx) (+ y dy)]
                target (if extended?
                         (calculate-extended-target coords direction)
                         adjacent-target)]
            (cond
              (and (= :army (:type unit))
                   (not extended?)
                   (hostile-city? adjacent-target))
              (do
                (attempt-conquest coords adjacent-target)
                (item-processed)
                true)

              (and (= :fighter (:type unit))
                   (not extended?)
                   (hostile-city? adjacent-target))
              (do
                (attempt-fighter-overfly coords adjacent-target)
                (item-processed)
                true)

              :else
              (do
                (movement/set-unit-movement coords target)
                (item-processed)
                true))))))))

(defn handle-key [k]
  (when-let [coords (first @atoms/cells-needing-attention)]
    (let [cell (get-in @atoms/game-map coords)]
      (if (:contents cell)
        (cond
          (and (= k :s) (not= :city (:type cell)))
          (do
            (movement/set-unit-mode coords :sentry)
            (item-processed)
            true)

          (and (= k :x) (= :army (:type (:contents cell))))
          (do
            (movement/set-explore-mode coords)
            (item-processed)
            true)

          :else
          (handle-unit-movement-key k coords cell))
        (handle-city-production-key k coords cell)))))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                contents (:contents cell)]
          :when (and contents (<= (:hits contents 1) 0))]
    (swap! atoms/game-map assoc-in [i j] (dissoc cell :contents))
    (movement/update-cell-visibility [i j] (:owner contents))))

(defn build-player-items
  "Builds list of player city/unit coordinates to process this round."
  []
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [cell (get-in @atoms/game-map [i j])]
        :when (or (= (:city-status cell) :player)
                  (= (:owner (:contents cell)) :player))]
    [i j]))

(defn item-needs-attention?
  "Returns true if the item at coords needs user input."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)]
    (or (= (:mode unit) :awake)
        (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (@atoms/production coords))))))

(defn set-attention-message
  "Sets the message for the current item needing attention."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        [ax ay] coords
        adjacent-enemy-city? (and (= :army (:type (:contents cell)))
                                  (some (fn [[di dj]]
                                          (let [ni (+ ax di)
                                                nj (+ ay dj)
                                                adj-cell (get-in @atoms/game-map [ni nj])]
                                            (and adj-cell
                                                 (= (:type adj-cell) :city)
                                                 (#{:free :computer} (:city-status adj-cell)))))
                                        (for [di [-1 0 1] dj [-1 0 1]] [di dj])))]
    (reset! atoms/message (if (:contents cell)
                            (let [unit (:contents cell)
                                  unit-name (name (:type unit))
                                  reason-key (or (:reason unit)
                                                 (when adjacent-enemy-city? :army-found-city))
                                  reason-str (when reason-key (reason-key config/messages))]
                              (str unit-name (:unit-needs-attention config/messages) (if reason-str (str " - " reason-str) "")))
                            (:city-needs-attention config/messages)))))

(defn move-current-unit
  "Moves the unit at coords one step. Returns new coords if still moving or awoke with steps, nil if done."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)]
    (when (= (:mode unit) :moving)
      (let [target (:target unit)]
        (movement/move-unit coords target cell atoms/game-map)
        (let [next-pos (movement/next-step-pos coords target)
              moved-cell (get-in @atoms/game-map next-pos)
              moved-unit (:contents moved-cell)]
          (when moved-unit
            (let [new-steps (dec (:steps-remaining moved-unit 1))]
              (swap! atoms/game-map assoc-in (conj next-pos :contents :steps-remaining) new-steps)
              (when (> new-steps 0)
                next-pos))))))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit (= (:owner unit) :player))]
    (let [steps (get config/unit-speed (:type unit) 1)]
      (swap! atoms/game-map assoc-in [i j :contents :steps-remaining] steps))))

(defn consume-sentry-fighter-fuel
  "Consumes fuel for sentry fighters each round, applying fuel warnings."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :fighter (:type unit))
                     (= :sentry (:mode unit)))]
    (let [current-fuel (:fuel unit config/fighter-fuel)
          new-fuel (dec current-fuel)
          pos [i j]
          bingo-threshold (quot config/fighter-fuel 4)
          low-fuel? (<= new-fuel 1)
          bingo-fuel? (and (<= new-fuel bingo-threshold)
                           (movement/friendly-city-in-range? pos new-fuel atoms/game-map))]
      (cond
        (<= new-fuel 0)
        (swap! atoms/game-map assoc-in [i j :contents :hits] 0)

        low-fuel?
        (swap! atoms/game-map update-in [i j :contents]
               #(assoc % :fuel new-fuel :mode :awake :reason :fighter-out-of-fuel))

        bingo-fuel?
        (swap! atoms/game-map update-in [i j :contents]
               #(assoc % :fuel new-fuel :mode :awake :reason :fighter-bingo))

        :else
        (swap! atoms/game-map assoc-in [i j :contents :fuel] new-fuel)))))

(defn start-new-round
  "Starts a new round by building player items list and updating game state."
  []
  (swap! atoms/round-number inc)
  (consume-sentry-fighter-fuel)
  (remove-dead-units)
  (production/update-production)
  (reset-steps-remaining)
  (reset! atoms/player-items (vec (build-player-items)))
  (reset! atoms/waiting-for-input false)
  (reset! atoms/message "")
  (reset! atoms/cells-needing-attention []))

(defn move-explore-unit
  "Moves an exploring unit. Returns new coords if still exploring, nil if done."
  [coords]
  (movement/move-explore-unit coords))

(defn advance-game
  "Advances the game by processing the current item or starting new round."
  []
  (if (empty? @atoms/player-items)
    (start-new-round)
    (when-not @atoms/waiting-for-input
      (let [coords (first @atoms/player-items)
            cell (get-in @atoms/game-map coords)
            unit (:contents cell)]
        (if (item-needs-attention? coords)
          (do
            (reset! atoms/cells-needing-attention [coords])
            (set-attention-message coords)
            (reset! atoms/waiting-for-input true))
          (let [new-coords (case (:mode unit)
                             :explore (move-explore-unit coords)
                             :moving (move-current-unit coords)
                             nil)]
            (if new-coords
              (swap! atoms/player-items #(cons new-coords (rest %)))
              (swap! atoms/player-items rest))))))))

(defn item-processed
  "Called when user input has been processed for current item."
  []
  (reset! atoms/waiting-for-input false)
  (reset! atoms/message "")
  (reset! atoms/cells-needing-attention [])
  )

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  (advance-game))

