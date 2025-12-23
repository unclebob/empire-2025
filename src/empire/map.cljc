(ns empire.map
  (:require [quil.core :as q]
            [empire.atoms :as atoms]))

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

(def game-map
  "A 2D atom containing vectors representing the game map."
  (atom nil))

(def player-map
  "An atom containing the player's visible map areas."
  (atom {}))

(def computer-map
  "An atom containing the computer's visible map areas."
  (atom {}))

(defn is-players?
  "Returns true if the value is a keyword that starts with 'player-'"
  [v]
  (and (keyword? v) (clojure.string/starts-with? (name v) "player-")))

(defn is-computers?
  "Returns true if the value is a keyword that starts with 'computer-'"
  [v]
  (and (keyword? v) (clojure.string/starts-with? (name v) "computer-")))

(defn draw-map
  "Draws the map on the screen."
  [the-map]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        height (count the-map)
        width (count (first the-map))
        cell-w (/ map-w width)
        cell-h (/ map-h height)]
    (doseq [i (range height)
            j (range width)]
      (let [[terrain-type contents] (get-in the-map [i j])
            color (cond
                    (= contents :player-city) [0 255 0]         ; green for player's city
                    (= contents :computer-city) [255 0 0]        ; red for computer's city
                    (= contents :free-city) [255 255 255]   ; white for free cities
                    (= terrain-type :unexplored) [0 0 0]     ; black for unexplored
                    (= terrain-type :land) [139 69 19]      ; brown for land
                    (= terrain-type :sea) [25 25 112])]     ; midnight blue for water
        (apply q/fill color)
        (q/rect (* j cell-w) (* i cell-h) cell-w cell-h)))))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units."
  [visible-map-atom ownership-predicate]
  (let [game-map-val @game-map
        height (count game-map-val)
        width (count (first game-map-val))]
    (doseq [i (range height)
            j (range width)
            :when (ownership-predicate (second (get-in game-map-val [i j])))]
      (doseq [di [-1 0 1]
              dj [-1 0 1]]
        (let [ni (max 0 (min (dec height) (+ i di)))
              nj (max 0 (min (dec width) (+ j dj)))]
          (swap! visible-map-atom assoc-in [ni nj] (get-in game-map-val [ni nj])))))))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (update-combatant-map player-map is-players?))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (update-combatant-map computer-map is-computers?))

(defn dismiss-existing-menu
  "Dismisses the menu if clicking outside it."
  [x y]
  (when (:visible @atoms/menu-state)
    (let [menu @atoms/menu-state
          menu-x (:x menu)
          menu-y (:y menu)
          items (:items menu)
          menu-width 150
          menu-height (* (count items) 20)]
      (when-not (and (>= x menu-x) (< x (+ menu-x menu-width))
                      (>= y menu-y) (< y (+ menu-y menu-height)))
        (swap! atoms/menu-state assoc :visible false)))))

(defn on-coast?
  "Checks if a cell is adjacent to sea."
  [cell-x cell-y]
  (let [game-map-val @game-map
        height (count game-map-val)
        width (count (first game-map-val))]
    (some (fn [[dx dy]]
            (let [nx (+ cell-x dx)
                  ny (+ cell-y dy)]
              (and (>= nx 0) (< nx width)
                   (>= ny 0) (< ny height)
                   (= :sea (first (get-in game-map-val [ny nx]))))))
          [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])))

(defn show-menu
  "Displays a menu with the given header and items positioned relative to a cell."
  [cell-x cell-y header items]
  (let [menu-width 150
        menu-height (+ 30 (* (count items) 20))
        [map-w map-h] @atoms/map-screen-dimensions
        width (count (first @game-map))
        height (count @game-map)
        cell-left (* cell-x (/ map-w width))
        cell-top (* cell-y (/ map-h height))
        cell-bottom (+ cell-top (/ map-h height))
        [_ text-y _ _] @atoms/text-area-dimensions
        screen-w (q/width)
        menu-x (min cell-left (- screen-w menu-width))
        menu-y (if (<= (+ cell-bottom menu-height) (min map-h text-y))
                 cell-bottom
                 (max 0 (- cell-top menu-height)))]
    (reset! atoms/menu-state {:visible true
                              :x menu-x
                              :y menu-y
                              :header header
                              :items items})))

(defn handle-city-click
  "Handles clicking on a city cell."
  [cell-x cell-y]
  (let [header "Produce"
        coastal-city? (on-coast? cell-x cell-y)
        items (cond-> ["Army" "Fighter" "Satellite"]
                      coastal-city? (into ["Transport" "Patrol Boat" "Destroyer" "Submarine" "Carrier" "Battleship"]))]
    (show-menu cell-x cell-y header items)))

(defn handle-cell-click
  "Handles clicking on a map cell."
  [cell-x cell-y]
  (let [contents (second (get-in @game-map [cell-y cell-x]))]
    (condp = contents
      :player-city (handle-city-click cell-x cell-y)
      nil)))

(defn determine-cell-coordinates
  "Converts mouse coordinates to map cell coordinates."
  [x y]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        height (count @game-map)
        width (count (first @game-map))
        cell-w (/ map-w width)
        cell-h (/ map-h height)]
    [(int (Math/floor (/ x cell-w))) (int (Math/floor (/ y cell-h)))]))

(defn mouse-down
  "Handles mouse click events."
  [x y]
  (dismiss-existing-menu x y)
  ;; Determine which map cell was clicked
  (let [[cell-x cell-y] (determine-cell-coordinates x y)]
    (reset! atoms/last-clicked-cell [cell-x cell-y])
    (handle-cell-click cell-x cell-y)))

(defn do-a-round
  "Performs one round of game actions."
  []
  ;; Placeholder for round logic
  (swap! atoms/round-number inc)
  (println "Doing round" @atoms/round-number "..."))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map))

