(ns empire.movement.pathfinding-bfs
  "BFS exploration utilities for computer AI.
   Provides flood-fill searches for unexplored territory, coast targets,
   and unload positions."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]))

(def bfs-unexplored-cache
  "Cache for BFS unexplored results: {unit-type result-pos-or-nil}"
  (atom {}))

(def bfs-unload-cache
  "Cache for BFS unload results: {target-continent result-pos-or-nil}"
  (atom {}))

(defn clear-bfs-caches
  "Clears both BFS caches. Called at start of each round."
  []
  (reset! bfs-unexplored-cache {})
  (reset! bfs-unload-cache {}))

(defn sea-reaches-edge?
  "BFS flood-fill from pos over sea cells. Returns true if any
   reachable sea cell is on the map edge. Short-circuits on first edge hit."
  [pos]
  (let [game-map @atoms/game-map
        rows (count game-map)
        cols (count (first game-map))]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY pos)
           visited #{pos}]
      (if (empty? queue)
        false
        (let [[r c] (peek queue)]
          (if (or (zero? r) (zero? c) (= r (dec rows)) (= c (dec cols)))
            true
            (let [neighbors (for [[dr dc] map-utils/neighbor-offsets
                                  :let [nr (+ r dr) nc (+ c dc)]
                                  :when (and (>= nr 0) (< nr rows)
                                             (>= nc 0) (< nc cols)
                                             (not (visited [nr nc]))
                                             (= :sea (:type (get-in game-map [nr nc]))))]
                              [nr nc])
                  new-visited (into visited neighbors)]
              (recur (into (pop queue) neighbors) new-visited))))))))

(defn- adjacent-to-unexplored?
  "Returns true if any neighbor of pos on the computer-map is unexplored.
   Handles both nil (test maps) and {:type :unexplored} (real game)."
  [pos computer-map]
  (let [[x y] pos
        height (count computer-map)
        width (count (first computer-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (let [cell (get-in computer-map [nx ny])]
                     (or (nil? cell)
                         (= :unexplored (:type cell)))))))
          map-utils/neighbor-offsets)))

(defn- find-nearest-unexplored-uncached
  "BFS from start over passable cells to find nearest cell adjacent to unexplored.
   Returns the target position, or nil if none found.
   Skips the start position so the result is a meaningful movement target."
  [start unit-type]
  (let [game-map @atoms/game-map
        computer-map @atoms/computer-map]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (when (seq queue)
        (let [current (peek queue)
              rest-queue (pop queue)]
          (if (and (not= current start)
                   (adjacent-to-unexplored? current computer-map))
            current
            (let [neighbors (remove visited
                                    (map-utils/get-passable-neighbors current unit-type game-map))
                  new-visited (into visited neighbors)
                  new-queue (into rest-queue neighbors)]
              (recur new-queue new-visited))))))))

(defn find-nearest-unexplored
  "BFS from start over passable cells to find nearest cell adjacent to unexplored.
   Returns the target position, or nil if none found.
   Caches result per unit-type so all units of the same type share one BFS per round.
   unit-type determines which cells are passable (e.g. :transport for sea,
   :fighter for all terrain)."
  [start unit-type]
  (let [cache @bfs-unexplored-cache]
    (if (contains? cache unit-type)
      (get cache unit-type)
      (let [result (find-nearest-unexplored-uncached start unit-type)]
        (swap! bfs-unexplored-cache assoc unit-type result)
        result))))

(defn- at-exploration-frontier?
  "Returns true if pos is a sea cell adjacent to both:
   - at least one unexplored (nil or {:type :unexplored}) cell on computer-map
   - at least one known land/city cell on computer-map
   This identifies coastal frontier positions worth exploring."
  [pos computer-map]
  (let [[x y] pos
        height (count computer-map)
        width (count (first computer-map))
        has-unexplored (some (fn [[dx dy]]
                               (let [nx (+ x dx) ny (+ y dy)]
                                 (and (>= nx 0) (< nx height)
                                      (>= ny 0) (< ny width)
                                      (let [cell (get-in computer-map [nx ny])]
                                        (or (nil? cell)
                                            (= :unexplored (:type cell)))))))
                             map-utils/neighbor-offsets)
        has-known-land (some (fn [[dx dy]]
                               (let [nx (+ x dx) ny (+ y dy)
                                     cell (get-in computer-map [nx ny])]
                                 (and cell
                                      (#{:land :city} (:type cell)))))
                             map-utils/neighbor-offsets)]
    (and has-unexplored has-known-land)))

(defn- find-nearest-unexplored-coastline-uncached
  "BFS from start over passable sea cells to find nearest cell at the
   exploration frontier (adjacent to both unexplored and known land)."
  [start unit-type]
  (let [game-map @atoms/game-map
        computer-map @atoms/computer-map]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (when (seq queue)
        (let [current (peek queue)
              rest-queue (pop queue)]
          (if (and (not= current start)
                   (at-exploration-frontier? current computer-map))
            current
            (let [neighbors (remove visited
                                    (map-utils/get-passable-neighbors current unit-type game-map))
                  new-visited (into visited neighbors)
                  new-queue (into rest-queue neighbors)]
              (recur new-queue new-visited))))))))

(defn find-nearest-unexplored-coastline
  "BFS to find nearest sea cell at a coastal exploration frontier.
   Cached per round like find-nearest-unexplored."
  [start unit-type]
  (let [cache @bfs-unexplored-cache
        cache-key [:coastline unit-type]]
    (if (contains? cache cache-key)
      (get cache cache-key)
      (let [result (find-nearest-unexplored-coastline-uncached start unit-type)]
        (swap! bfs-unexplored-cache assoc cache-key result)
        result))))

(defn bfs-to-unexplored-coast
  "BFS from start over explored sea cells on computer-map to find the nearest
   cell adjacent to unexplored territory. Returns the path (vector of positions
   excluding start) or nil if unreachable. Single pass — no separate A* needed."
  [start computer-map]
  (let [passable-sea? (fn [pos]
                        (let [cell (get-in computer-map pos)]
                          (and cell (= :sea (:type cell)))))]
    (when (passable-sea? start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
             visited #{start}
             came-from {}]
        (when (seq queue)
          (let [current (peek queue)
                rest-queue (pop queue)]
            (if (and (not= current start)
                     (adjacent-to-unexplored? current computer-map))
              (vec (rest (map-utils/reconstruct-path came-from start current)))
              (let [[x y] current
                    neighbors (for [[dx dy] map-utils/neighbor-offsets
                                    :let [nx (+ x dx) ny (+ y dy) n [nx ny]]
                                    :when (and (not (visited n))
                                               (passable-sea? n))]
                                n)
                    new-visited (into visited neighbors)
                    new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
                (recur (into rest-queue neighbors)
                       new-visited
                       new-came-from)))))))))

(defn- adjacent-to-unowned?
  "Returns true if any neighbor of pos on the given map is non-computer land/city."
  [pos game-map]
  (let [[x y] pos
        height (count game-map)
        width (count (first game-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (let [cell (get-in game-map [nx ny])]
                     (and cell
                          (or (and (= :city (:type cell))
                                   (#{:free :player} (:city-status cell)))
                              (and (= :land (:type cell))
                                   (nil? (:country-id cell)))))))))
          map-utils/neighbor-offsets)))

(defn bfs-to-unowned-coast
  "BFS from start over explored sea cells on computer-map to find the nearest
   cell adjacent to non-computer land/city on game-map. Returns the path
   (vector of positions excluding start) or nil if unreachable."
  [start computer-map game-map]
  (let [passable-sea? (fn [pos]
                        (let [cell (get-in computer-map pos)]
                          (and cell (= :sea (:type cell)))))]
    (when (passable-sea? start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
             visited #{start}
             came-from {}]
        (when (seq queue)
          (let [current (peek queue)
                rest-queue (pop queue)]
            (if (and (not= current start)
                     (adjacent-to-unowned? current game-map))
              (vec (rest (map-utils/reconstruct-path came-from start current)))
              (let [[x y] current
                    neighbors (for [[dx dy] map-utils/neighbor-offsets
                                    :let [nx (+ x dx) ny (+ y dy) n [nx ny]]
                                    :when (and (not (visited n))
                                               (passable-sea? n))]
                                n)
                    new-visited (into visited neighbors)
                    new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
                (recur (into rest-queue neighbors)
                       new-visited
                       new-came-from)))))))))

(def ^:private coast-lookahead 4)

(defn- bfs-past-lookahead?
  [queue first-hit-depth]
  (or (empty? queue)
      (and first-hit-depth
           (> (second (peek queue))
              (+ first-hit-depth coast-lookahead)))))

(defn- update-first-match
  [flag? current-best new-value]
  (if (and flag? (nil? current-best)) new-value current-best))

(defn- classify-coastal
  [current start computer-map]
  (if (= current start)
    [false false]
    [(adjacent-to-unowned? current computer-map)
     (adjacent-to-unexplored? current computer-map)]))

(defn- bfs-sea-neighbors
  [current visited passable-sea?]
  (let [[x y] current]
    (for [[dx dy] map-utils/neighbor-offsets
          :let [nx (+ x dx) ny (+ y dy) n [nx ny]]
          :when (and (not (visited n))
                     (passable-sea? n))]
      n)))

(defn- build-coast-path
  [best-unowned best-unexplored came-from start]
  (when-let [target (or best-unowned best-unexplored)]
    (vec (rest (map-utils/reconstruct-path came-from start target)))))

(defn bfs-to-coast-target
  "Combined BFS over explored sea cells seeking cells adjacent to
   unowned land/city or unexplored territory, both on computer-map.
   Continues coast-lookahead levels past the first hit so that a
   slightly farther unowned coast beats a nearer unexplored cell."
  [start computer-map]
  (let [passable-sea? (fn [pos]
                        (let [cell (get-in computer-map pos)]
                          (and cell (= :sea (:type cell)))))]
    (when (passable-sea? start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start 0])
             visited #{start}
             came-from {}
             first-hit-depth nil
             best-unowned nil
             best-unexplored nil]
        (if (bfs-past-lookahead? queue first-hit-depth)
          (build-coast-path best-unowned best-unexplored came-from start)
          (let [[current depth] (peek queue)
                [unowned? unexplored?] (classify-coastal current start computer-map)
                hit? (or unowned? unexplored?)
                neighbors (bfs-sea-neighbors current visited passable-sea?)
                new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
            (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) neighbors)
                   (into visited neighbors)
                   new-came-from
                   (update-first-match hit? first-hit-depth depth)
                   (update-first-match unowned? best-unowned current)
                   (update-first-match unexplored? best-unexplored current))))))))

(defn- passable-sea?
  "Returns true if pos is a known sea cell on the given map."
  [the-map pos]
  (let [cell (get-in the-map pos)]
    (and cell (= :sea (:type cell)))))

(def ^:private min-explore-depth 4)

(defn- adjacent-to-land-or-city?
  "Returns true if any neighbor of pos on the given map is land or city."
  [pos game-map]
  (let [[x y] pos]
    (some (fn [[dx dy]]
            (let [nx (+ x dx) ny (+ y dy)
                  cell (get-in game-map [nx ny])]
              (and cell (#{:land :city} (:type cell)))))
          map-utils/neighbor-offsets)))

(defn- unseen-coast?
  "Returns true if pos is adjacent to land/city and not in seen-coast."
  [pos game-map seen-coast]
  (and (not (contains? seen-coast pos))
       (adjacent-to-land-or-city? pos game-map)))

(defn- available-for-target?
  "Returns true if current cell is deep enough, not start, and not excluded."
  [current start depth excluded]
  (and (>= depth min-explore-depth)
       (not= current start)
       (not (contains? excluded current))))

(defn- unexplored-target?
  "Returns true if no best-unexplored yet and current is adjacent to unexplored."
  [current best-unexplored computer-map]
  (and (nil? best-unexplored)
       (adjacent-to-unexplored? current computer-map)))

(defn- classify-unseen-step
  "Classifies current BFS position for unseen-coast search.
   Returns [new-coast new-unexplored]."
  [current start depth excluded best-coast best-unexplored computer-map seen-coast]
  (if-not (available-for-target? current start depth excluded)
    [best-coast best-unexplored]
    [(update-first-match
       (unseen-coast? current computer-map seen-coast) best-coast current)
     (update-first-match
       (unexplored-target? current best-unexplored computer-map) best-unexplored current)]))

(defn bfs-to-unseen-coast
  "BFS from start over passable sea cells to find the nearest unseen coastal
   cell (adjacent to land/city on computer-map, not in seen-coast) or cell
   adjacent to unexplored territory. Skips targets within min-explore-depth
   levels and targets in excluded set. Prefers unseen coast over unexplored.
   Returns path excluding start, or nil."
  [start computer-map excluded]
  (let [seen-coast @atoms/seen-coast
        sea? (partial passable-sea? computer-map)]
    (when (passable-sea? computer-map start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start 0])
             visited #{start}
             came-from {}
             best-coast nil
             best-unexplored nil]
        (if (empty? queue)
          (build-coast-path best-coast best-unexplored came-from start)
          (let [[current depth] (peek queue)
                [new-coast new-unexplored]
                (classify-unseen-step current start depth excluded
                                     best-coast best-unexplored computer-map seen-coast)]
            (if new-coast
              (build-coast-path new-coast new-unexplored came-from start)
              (let [neighbors (bfs-sea-neighbors current visited sea?)
                    new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
                (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) neighbors)
                       (into visited neighbors)
                       new-came-from
                       new-coast
                       new-unexplored)))))))))

(defn- adjacent-to-target-continent-land?
  "Returns true if any neighbor of pos is land/city on target-continent."
  [pos target-continent game-map]
  (let [[x y] pos]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)
                  cell (get-in game-map [nx ny])]
              (and cell
                   (#{:land :city} (:type cell))
                   (contains? target-continent [nx ny]))))
          map-utils/neighbor-offsets)))

(defn- find-nearest-unload-uncached
  "BFS from start over sea cells to find nearest empty sea cell
   adjacent to land on target-continent."
  [start target-continent]
  (let [game-map @atoms/game-map]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (when (seq queue)
        (let [current (peek queue)
              rest-queue (pop queue)
              cell (get-in game-map current)]
          (if (and (not= current start)
                   (= :sea (:type cell))
                   (nil? (:contents cell))
                   (adjacent-to-target-continent-land? current target-continent game-map))
            current
            (let [neighbors (map-utils/get-passable-neighbors current :transport game-map)
                  new-neighbors (remove visited neighbors)
                  new-visited (into visited new-neighbors)
                  new-queue (into rest-queue new-neighbors)]
              (recur new-queue new-visited))))))))

(defn find-nearest-unload-position
  "BFS from start over sea cells to find nearest empty sea cell
   adjacent to land on target-continent. VMS-style global search.
   Caches result per target-continent so all transports heading to the same
   continent share one BFS per round."
  [start target-continent]
  (let [cache @bfs-unload-cache]
    (if (contains? cache target-continent)
      (get cache target-continent)
      (let [result (find-nearest-unload-uncached start target-continent)]
        (swap! bfs-unload-cache assoc target-continent result)
        result))))

(defn- adjacent-to-target?
  "Returns true if any neighbor of pos equals target-city."
  [pos target-city]
  (let [[x y] pos]
    (some (fn [[dx dy]]
            (= target-city [(+ x dx) (+ y dy)]))
          map-utils/neighbor-offsets)))

(defn bfs-to-land-ho-target
  "BFS over sea cells on computer-map from start toward target-city.
   Returns a path (excluding start) of sea cells ending at a sea cell
   adjacent to target-city. Returns nil if no sea path exists.
   Returns [] if start is already adjacent to target-city."
  [start target-city computer-map]
  (let [passable-sea? (fn [pos]
                        (let [cell (get-in computer-map pos)]
                          (and cell (= :sea (:type cell)))))]
    (if (adjacent-to-target? start target-city)
      []
      (when (passable-sea? start)
        (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
               visited #{start}
               came-from {}]
          (when (seq queue)
            (let [current (peek queue)]
              (if (and (not= current start)
                       (adjacent-to-target? current target-city))
                (vec (rest (map-utils/reconstruct-path came-from start current)))
                (let [neighbors (bfs-sea-neighbors current visited passable-sea?)
                      new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
                  (recur (reduce conj (pop queue) neighbors)
                         (into visited neighbors)
                         new-came-from))))))))))
