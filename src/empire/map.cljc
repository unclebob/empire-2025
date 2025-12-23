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

(def visible-map
  "An atom containing the visible map areas."
  (atom {}))

(defn is-players?
  "Returns true if the value is a keyword that starts with 'player-'"
  [v]
  (and (keyword? v) (clojure.string/starts-with? (name v) "player-")))

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

(defn see-cells-near-my-units
  "Reveals cells near player-owned units on the visible map."
  []
  (let [game-map-val @game-map
        height (count game-map-val)
        width (count (first game-map-val))]
    (doseq [i (range height)
            j (range width)
            :when (is-players? (second (get-in game-map-val [i j])))]
      (doseq [di [-1 0 1]
              dj [-1 0 1]]
        (let [ni (max 0 (min (dec height) (+ i di)))
              nj (max 0 (min (dec width) (+ j dj)))]
          (swap! visible-map assoc-in [ni nj] (get-in game-map-val [ni nj])))))))

(defn update-map
  "Updates the game map state."
  []
  (see-cells-near-my-units))

