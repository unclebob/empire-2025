(ns empire.computer.transport.load-targeting
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.pathfinding-bfs.core :as bfs-core]
            [empire.computer.shared.grid :as grid]))

(def ^:private tile-size 5)
(def ^:private min-armies-near-target 4)

(declare neighborhood-tile-army-positions)

(defn- map-width
  [computer-map]
  (count computer-map))

(defn- map-height
  [computer-map]
  (count (first computer-map)))

(defn- in-bounds?
  [computer-map [x y]]
  (and (<= 0 x) (< x (map-width computer-map))
       (<= 0 y) (< y (map-height computer-map))))

(defn- tile-origin
  [[tile-x tile-y]]
  [(* tile-x tile-size) (* tile-y tile-size)])

(defn- tile-index-for-pos
  [[x y]]
  [(quot x tile-size) (quot y tile-size)])

(defn- tile-cells
  [computer-map tile-index]
  (let [[origin-x origin-y] (tile-origin tile-index)]
    (for [x (range origin-x (+ origin-x tile-size))
          y (range origin-y (+ origin-y tile-size))
          :when (in-bounds? computer-map [x y])]
      {:pos [x y]
       :cell (get-in computer-map [x y])})))

(defn- computer-claimed-coastal-cell?
  [computer-map pos cell]
  (and cell
       (= :land (:type cell))
       (some? (:country-id cell))
       (map-utils/adjacent-to-sea? pos computer-map)))

(defn- computer-army?
  [cell reserved-army-ids]
  (let [unit (:contents cell)]
    (and unit
         (= :army (:type unit))
         (= :computer (:owner unit))
         (not (contains? reserved-army-ids (:computer-unit-id unit))))))

(defn- computer-coastal-army?
  [computer-map pos cell reserved-army-ids]
  (and (computer-army? cell reserved-army-ids)
       (map-utils/adjacent-to-sea? pos computer-map)))

(defn- tile-summary
  [computer-map tile-index reserved-army-ids]
  (let [cells (tile-cells computer-map tile-index)
        coastal-targets (keep (fn [{:keys [pos cell]}]
                                (when (computer-claimed-coastal-cell? computer-map pos cell)
                                  pos))
                              cells)
        army-positions (keep (fn [{:keys [pos cell]}]
                               (when (computer-coastal-army? computer-map pos cell reserved-army-ids)
                                 pos))
                             cells)]
    {:tile tile-index
     :coastal-targets (vec coastal-targets)
     :army-positions (vec army-positions)}))

(defn- all-tiles
  [computer-map]
  (for [tile-x (range (long (Math/ceil (/ (double (map-width computer-map)) tile-size))))
        tile-y (range (long (Math/ceil (/ (double (map-height computer-map)) tile-size))))]
    [tile-x tile-y]))

(defn- tile-target
  [transport-pos {:keys [coastal-targets]}]
  (first (sort-by (juxt #(grid/chebyshev-distance transport-pos %)
                        identity)
                  coastal-targets)))

(defn- candidate-load-targets
  [computer-map reserved-coastal-cells excluded-country-ids]
  (->> (all-tiles computer-map)
       (map #(tile-summary computer-map % #{}))
       (mapcat :coastal-targets)
       distinct
       (remove #(contains? excluded-country-ids
                           (:country-id (get-in computer-map %))))
       (remove reserved-coastal-cells)))

(defn all-coastal-army-positions
  [computer-map reserved-army-ids]
  (->> (all-tiles computer-map)
       (map #(tile-summary computer-map % reserved-army-ids))
       (mapcat :army-positions)
       distinct))

(defn- target-summary
  [transport-pos target all-armies]
  (let [army-positions (->> all-armies
                            (filter #(<= (grid/chebyshev-distance target %) tile-size))
                            vec)]
    {:target target
     :army-positions army-positions
     :army-count (count army-positions)
     :distance (grid/chebyshev-distance transport-pos target)}))

(defn choose-load-target-cell
  ([transport-pos computer-map]
   (choose-load-target-cell transport-pos computer-map {}))
  ([transport-pos computer-map {:keys [reserved-coastal-cells reserved-army-ids excluded-country-ids]
                                :or {reserved-coastal-cells #{}
                                     excluded-country-ids #{}
                                     reserved-army-ids #{}}}]
   (let [all-armies (all-coastal-army-positions computer-map reserved-army-ids)
         candidates (->> (candidate-load-targets computer-map
                                                reserved-coastal-cells
                                                excluded-country-ids)
                         (map #(target-summary transport-pos % all-armies))
                         (filter #(>= (:army-count %) min-armies-near-target))
                         (sort-by (juxt :distance
                                        (comp - :army-count)
                                        :target)))]
     (:target (first candidates)))))

(defn- target-adjacent-sea?
  [computer-map start target pos]
  (and (bfs-core/transport-passable-sea? computer-map start pos)
       (some #{target} (for [[dx dy] map-utils/neighbor-offsets
                             :let [candidate [(+ (first pos) dx) (+ (second pos) dy)]]
                             :when (in-bounds? computer-map candidate)]
                         candidate))))

(defn path-to-load-target
  [start computer-map target]
  (let [passable-sea? #(bfs-core/transport-passable-sea? computer-map start %)]
    (when (and target (passable-sea? start))
      (if (target-adjacent-sea? computer-map start target start)
        []
        (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
               visited #{start}
               came-from {}]
          (when (seq queue)
            (let [current (peek queue)]
              (if (target-adjacent-sea? computer-map start target current)
                (vec (rest (map-utils/reconstruct-path came-from start current)))
                (let [neighbors (bfs-core/bfs-sea-neighbors current visited passable-sea?)
                      new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
                  (recur (into (pop queue) neighbors)
                         (into visited neighbors)
                         new-came-from))))))))))

(defn target-reached?
  [transport-pos target]
  (and target
       (<= (grid/chebyshev-distance transport-pos target) 1)))

(defn neighborhood-tile-army-positions
  ([target computer-map]
   (neighborhood-tile-army-positions target computer-map #{}))
  ([target computer-map reserved-army-ids]
  (let [[tile-x tile-y] (tile-index-for-pos target)
        neighbor-tiles (for [dx [-1 0 1]
                             dy [-1 0 1]
                             :let [tile [(+ tile-x dx) (+ tile-y dy)]]
                             :when (and (<= 0 (first tile))
                                        (<= 0 (second tile))
                                        (< (first tile) (long (Math/ceil (/ (double (map-width computer-map)) tile-size))))
                                        (< (second tile) (long (Math/ceil (/ (double (map-height computer-map)) tile-size)))))]
                         tile)]
    (vec (mapcat :army-positions
                 (map #(tile-summary computer-map % reserved-army-ids) neighbor-tiles))))))

(defn neighborhood-tile-coastal-targets
  [target computer-map]
  (let [[tile-x tile-y] (tile-index-for-pos target)
        neighbor-tiles (for [dx [-1 0 1]
                             dy [-1 0 1]
                             :let [tile [(+ tile-x dx) (+ tile-y dy)]]
                             :when (and (<= 0 (first tile))
                                        (<= 0 (second tile))
                                        (< (first tile) (long (Math/ceil (/ (double (map-width computer-map)) tile-size))))
                                        (< (second tile) (long (Math/ceil (/ (double (map-height computer-map)) tile-size)))))]
                         tile)]
    (->> neighbor-tiles
         (map #(tile-summary computer-map % #{}))
         (mapcat :coastal-targets)
         distinct
         vec)))
