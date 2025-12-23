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

