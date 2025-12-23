(ns empire.init
  (:require [empire.map :as map]))

(defn smooth-cell
  "Calculates the smoothed value for a cell at position i j."
  [i j the-map]
  (let [neighbors (for [di [-1 0 1]
                        dj [-1 0 1]]
                    (let [ni (+ i di)
                          nj (+ j dj)
                          default (get-in the-map [i j] 0)]
                      (get-in the-map [ni nj] default)))]
    (Math/round (double (/ (apply + neighbors) 9.0)))))

(defn smooth-map
  "Takes a map and returns a smoothed version where each cell is the rounded average
   of itself and its 8 surrounding cells. Edge cells use their own value for missing neighbors."
  [input-map]
  (map/process-map input-map smooth-cell))

(defn make-map
  "Creates and initializes the game map with random integers, then applies smoothing."
  [width height smooth-count]
  (let [random-map (vec (for [_ (range height)]
                          (vec (for [_ (range width)]
                                 (rand-int 1001)))))
        smoothed-map (loop [m random-map cnt smooth-count]
                       (if (zero? cnt)
                         m
                         (recur (smooth-map m) (dec cnt))))]
    smoothed-map))

(defn finalize-map
  "Converts a height map to a terrain map with land/sea types."
  [the-map sea-level]
  (map/process-map the-map (fn [i j the-map]
                             (let [height (get-in the-map [i j])
                                   terrain-type (if (> height sea-level) :land :sea)]
                               [terrain-type :empty]))))

(defn find-sea-level
  "Finds the sea-level threshold for a given land fraction."
  [the-map land-fraction]
  (let [flattened (flatten the-map)
        sorted-heights (sort flattened)
        total (count flattened)
        target-land (Math/round (* land-fraction total))
        sea-level-idx (max 0 (min (dec total) (- total target-land)))
        sea-level (nth sorted-heights sea-level-idx)]
    sea-level))


(defn occupy-random-free-city
  "Occupies a random free city with the given city type (:player-city or :computer-city)."
  [the-map city-type]
  (let [free-city-positions (map/filter-map the-map (fn [cell] (= :free-city (second cell))))
        num-free (count free-city-positions)]
    (if (> num-free 0)
      (let [idx (rand-int num-free)
            [i j] (nth free-city-positions idx)]
        (assoc-in the-map [i j] [:land city-type]))
      the-map)))

(defn generate-cities
  "Places free cities on land cells with minimum distance constraints."
  [the-map number-of-cities min-city-distance]
  (let [land-positions (map/filter-map the-map (fn [cell] (= :land (first cell))))
        land-positions-vec (vec land-positions)
        num-land (count land-positions-vec)]
    (loop [placed-cities []
           attempts 0]
      (cond
        (>= (count placed-cities) number-of-cities)
        ;; Update the map with cities
        (reduce (fn [m [i j]]
                  (assoc-in m [i j] [:land :free-city]))
                the-map
                placed-cities)

        (>= attempts 1000)                                  ; Prevent infinite loop by stopping placement
        (reduce (fn [m [i j]]
                  (assoc-in m [i j] [:land :free-city]))
                the-map
                placed-cities)

        :else
        (let [idx (rand-int num-land)
              [i j] (land-positions-vec idx)
              too-close? (some (fn [[pi pj]]
                                 (< (+ (Math/abs (- i pi)) (Math/abs (- j pj))) min-city-distance))
                               placed-cities)]
          (if too-close?
            (recur placed-cities (inc attempts))
            (recur (conj placed-cities [i j]) 0)))))))

(defn make-initial-map
  "Creates and initializes the complete game map with terrain and free cities."
  [map-size smooth-count land-fraction number-of-cities min-city-distance]
  (let [[width height] map-size
        the-map (make-map width height smooth-count)
        sea-level (find-sea-level the-map land-fraction)
        finalized-map (finalize-map the-map sea-level)
        map-with-cities (generate-cities finalized-map number-of-cities min-city-distance)
        map-with-player-city (occupy-random-free-city map-with-cities :player-city)
        map-with-computer-city (occupy-random-free-city map-with-player-city :computer-city)
        visibility-map (vec (for [_ (range height)]
                              (vec (for [_ (range width)]
                                     [:unexplored :empty]))))]
    (reset! map/game-map map-with-computer-city)
    (reset! map/player-map visibility-map)
    (reset! map/computer-map visibility-map)))
