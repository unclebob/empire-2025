;; mutation-tested: 2026-02-26
(ns empire.application.initialization
  (:require [empire.config.core :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.application.city-production :as city-production]))

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

(defn- place-cities-on-map [the-map placed-cities]
  (reduce (fn [m [i j]]
            (assoc-in m [i j] {:type :city :contents nil :city-status :free}))
          the-map placed-cities))

(defn too-close-to-any? [pos placed-cities min-distance]
  (some (fn [[pi pj]]
          (let [[i j] pos]
            (< (+ (Math/abs (- i pi)) (Math/abs (- j pj))) min-distance)))
        placed-cities))

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
        (place-cities-on-map the-map placed-cities)

        (>= attempts config/max-placement-attempts)
        (place-cities-on-map the-map placed-cities)

        :else
        (let [pos (land-positions-vec (rand-int num-land))]
          (if (too-close-to-any? pos placed-cities min-city-distance)
            (recur placed-cities (inc attempts))
            (recur (conj placed-cities pos) 0)))))))

(defn find-city-position
  "Finds the position of a city with the given owner."
  [the-map owner]
  (first (map-utils/filter-map the-map (fn [cell] (and (= :city (:type cell)) (= owner (:city-status cell)))))))

(defn- compute-lake-max-cells
  [width height]
  (int (Math/floor (* 0.10 width height))))

(defn make-initial-map
  "Creates and initializes the complete game map with terrain and free cities."
  [map-size smooth-count land-fraction number-of-cities min-city-distance]
  (let [[width height] map-size
        the-map (make-map width height smooth-count)
        sea-level (find-sea-level the-map land-fraction)
        finalized-map (finalize-map the-map sea-level)
        map-with-cities (generate-cities finalized-map number-of-cities min-city-distance)
        map-with-player-city (occupy-random-free-city map-with-cities :player nil config/min-surrounding-land)
        [px py] (find-city-position map-with-player-city :player)
        min-start-distance (quot width 2)
        map-with-computer-city (occupy-random-free-city map-with-player-city :computer [px py min-start-distance] config/min-surrounding-land)
        visibility-map (vec (for [_ (range width)]
                              (vec (for [_ (range height)]
                                     {:type :unexplored}))))]
    (reset! (sa/world-atom) map-with-computer-city)
    ;; Assign country-id 1 to computer's starting city and begin army production
    (when-let [computer-city-pos (find-city-position map-with-computer-city :computer)]
      (sa/update-world! assoc-in (conj computer-city-pos :country-id) 1)
      (sa/write-state! :next-country-id 2)
      (city-production/set-city-production computer-city-pos :army))
    (sa/write-state! :lake-max-cells (compute-lake-max-cells width height))
    (sa/write-state! :known-lake-cells #{})
    (sa/write-state! :player-map visibility-map)
    (sa/write-state! :computer-map visibility-map)
    ;; Initialize visibility around starting positions
    (let [world (sa/current-world)]
      (when-let [updated (visibility/update-combatant-map-state
                          (sa/read-state :player-map) :player world)]
        (sa/write-state! :player-map updated))
      (when-let [updated (visibility/update-combatant-map-state
                          (sa/read-state :computer-map) :computer world)]
        (sa/write-state! :computer-map updated)))
    (sa/rebuild-refueling-caches!)
    map-with-computer-city))
