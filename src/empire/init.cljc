(ns empire.init
  (:require [empire.movement.map-utils :as map-utils]
            [empire.atoms :as atoms]
            [empire.movement.visibility :as visibility]
            [empire.fsm.integration :as integration]))

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
  (map-utils/process-map input-map smooth-cell))

(defn make-map
  "Creates and initializes the game map with random integers, then applies smoothing."
  [width height smooth-count]
  (let [random-map (vec (for [_ (range width)]
                          (vec (for [_ (range height)]
                                 (rand-int 1001)))))
        smoothed-map (loop [m random-map cnt smooth-count]
                       (if (zero? cnt)
                         m
                         (recur (smooth-map m) (dec cnt))))]
    smoothed-map))

(defn finalize-map
  "Converts a height map to a terrain map with land/sea types."
  [the-map sea-level]
  (map-utils/process-map the-map (fn [i j the-map]
                             (let [height (get-in the-map [i j])
                                   terrain-type (if (> height sea-level) :land :sea)]
                               {:type terrain-type}))))

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


(defn coastal?
  "Returns true if the position [i j] is adjacent to a sea cell."
  [[i j] the-map]
  (let [height (count the-map)
        width (count (first the-map))]
    (some (fn [[di dj]]
            (let [ni (+ i di)
                  nj (+ j dj)]
              (and (>= ni 0) (< ni height)
                   (>= nj 0) (< nj width)
                   (= :sea (:type (get-in the-map [ni nj]))))))
          map-utils/neighbor-offsets)))

(defn count-surrounding-land
  "Counts land cells in a 5x5 area centered on [i j], excluding the center."
  [[i j] the-map]
  (let [height (count the-map)
        width (count (first the-map))]
    (count (for [di [-2 -1 0 1 2]
                 dj [-2 -1 0 1 2]
                 :when (not (and (zero? di) (zero? dj)))
                 :let [ni (+ i di)
                       nj (+ j dj)]
                 :when (and (>= ni 0) (< ni height)
                            (>= nj 0) (< nj width))
                 :let [cell (get-in the-map [ni nj])]
                 :when (or (= :land (:type cell))
                           (= :city (:type cell)))]
             [ni nj]))))

(defn occupy-random-free-city
  "Occupies a random free coastal city with the given owner (:player or :computer).
   If min-distance-from is provided as [x y dist], only considers cities at least dist away.
   If min-surrounding-land is provided, only considers cities with at least that many land cells nearby."
  ([the-map owner]
   (occupy-random-free-city the-map owner nil 0))
  ([the-map owner min-distance-from]
   (occupy-random-free-city the-map owner min-distance-from 0))
  ([the-map owner min-distance-from min-surrounding-land]
   (let [free-city-positions (map-utils/filter-map the-map (fn [cell] (and (= :city (:type cell)) (= :free (:city-status cell)))))
         coastal-cities (filter #(coastal? % the-map) free-city-positions)
         with-enough-land (filter #(>= (count-surrounding-land % the-map) min-surrounding-land) coastal-cities)
         filtered-cities (if min-distance-from
                           (let [[fx fy min-dist] min-distance-from]
                             (filter (fn [[i j]]
                                       (>= (+ (Math/abs (- i fx)) (Math/abs (- j fy))) min-dist))
                                     with-enough-land))
                           with-enough-land)
         num-filtered (count filtered-cities)]
     (if (> num-filtered 0)
       (let [idx (rand-int num-filtered)
             [i j] (nth filtered-cities idx)]
         (assoc-in the-map [i j] {:type :city :contents nil :city-status owner}))
       the-map))))

(defn generate-cities
  "Places free cities on land cells with minimum distance constraints."
  [the-map number-of-cities min-city-distance]
  (let [land-positions (map-utils/filter-map the-map (fn [cell] (= :land (:type cell))))
        land-positions-vec (vec land-positions)
        num-land (count land-positions-vec)]
    (loop [placed-cities []
           attempts 0]
      (cond
        (>= (count placed-cities) number-of-cities)
        ;; Update the map with cities
        (reduce (fn [m [i j]]
                  (assoc-in m [i j] {:type :city :contents nil :city-status :free}))
                the-map
                placed-cities)

        (>= attempts 1000)                                  ; Prevent infinite loop by stopping placement
        (reduce (fn [m [i j]]
                  (assoc-in m [i j] {:type :city :contents nil :city-status :free}))
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

(defn find-city-position
  "Finds the position of a city with the given owner."
  [the-map owner]
  (first (map-utils/filter-map the-map (fn [cell] (and (= :city (:type cell)) (= owner (:city-status cell)))))))

(defn make-initial-map
  "Creates and initializes the complete game map with terrain and free cities."
  [map-size smooth-count land-fraction number-of-cities min-city-distance]
  (let [[width height] map-size
        the-map (make-map width height smooth-count)
        sea-level (find-sea-level the-map land-fraction)
        finalized-map (finalize-map the-map sea-level)
        map-with-cities (generate-cities finalized-map number-of-cities min-city-distance)
        min-surrounding-land 10
        map-with-player-city (occupy-random-free-city map-with-cities :player nil min-surrounding-land)
        [px py] (find-city-position map-with-player-city :player)
        min-start-distance (quot width 2)
        map-with-computer-city (occupy-random-free-city map-with-player-city :computer [px py min-start-distance] min-surrounding-land)
        visibility-map (vec (for [_ (range width)]
                              (vec (for [_ (range height)]
                                     {:type :unexplored}))))]
    (reset! atoms/game-map map-with-computer-city)
    (reset! atoms/player-map visibility-map)
    (reset! atoms/computer-map visibility-map)
    ;; Initialize visibility around starting positions
    (visibility/update-combatant-map atoms/player-map :player)
    (visibility/update-combatant-map atoms/computer-map :computer)
    ;; Initialize Commanding General for computer player
    (integration/initialize-general)
    map-with-computer-city))
