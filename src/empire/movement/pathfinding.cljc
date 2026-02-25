;; mutation-tested: 2026-02-22
(ns empire.movement.pathfinding
  "A* pathfinding for computer AI units.
   Provides efficient pathfinding that respects terrain constraints."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.config :as config]
            [empire.units.dispatcher :as dispatcher]
            [empire.movement.sea-lanes :as sea-lanes]))

(def path-cache
  "Cache for computed paths: {[start goal unit-type] path-vector}"
  (atom {}))

(def bfs-unexplored-cache
  "Cache for BFS unexplored results: {unit-type result-pos-or-nil}"
  (atom {}))

(def bfs-unload-cache
  "Cache for BFS unload results: {target-continent result-pos-or-nil}"
  (atom {}))

(defn clear-path-cache
  "Clears entire path cache. Called at start of each round."
  []
  (reset! path-cache {})
  (reset! bfs-unexplored-cache {})
  (reset! bfs-unload-cache {}))

(def heuristic
  "Manhattan distance heuristic for A*."
  core/distance)

(defn passable?
  "Returns true if unit-type can move through the cell."
  [unit-type cell]
  (and cell
       (not= (:type cell) :unexplored)
       (dispatcher/can-move-to? unit-type cell)))

(def ^:private neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]         [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn get-passable-neighbors
  "Returns neighbors that the unit type can traverse.
   When passability-fn is provided, uses it instead of the default passable? check."
  ([pos unit-type game-map]
   (get-passable-neighbors pos unit-type game-map nil))
  ([pos unit-type game-map passability-fn]
   (let [[x y] pos
         check-fn (or passability-fn (partial passable? unit-type))]
     (filter (fn [[nx ny]]
               (let [cell (get-in game-map [nx ny])]
                 (check-fn cell)))
             (map (fn [[dx dy]] [(+ x dx) (+ y dy)]) neighbor-offsets)))))

(defn- reconstruct-path
  "Walks came-from map from goal back to start, returns path vector [start ... goal]."
  [came-from start goal]
  (loop [pos goal
         path (list goal)]
    (if (= pos start)
      (vec path)
      (let [prev (came-from pos)]
        (recur prev (cons prev path))))))

(defn- try-improve-neighbor
  "If new-g improves n's best known g, records the improvement in acc."
  [new-g current {:keys [better new-best-g new-came-from new-counter] :as acc} n]
  (if (< new-g (get new-best-g n Long/MAX_VALUE))
    {:better (conj better [n new-counter])
     :new-best-g (assoc new-best-g n new-g)
     :new-came-from (assoc new-came-from n current)
     :new-counter (inc new-counter)}
    acc))

(defn- expand-node
  "Processes current node: skips if already closed, otherwise collects valid
   neighbors, improves g-values, builds new open-set entries.
   Returns [open-set closed-set best-g came-from counter]."
  [current g open-set closed-set best-g came-from counter
   unit-type game-map passability-fn neighbor-filter goal]
  (if (closed-set current)
    [(disj open-set (first open-set)) closed-set best-g came-from counter]
    (let [new-closed (conj closed-set current)
          valid-neighbors (cond->> (remove closed-set
                                          (get-passable-neighbors current unit-type game-map passability-fn))
                            neighbor-filter (filter neighbor-filter))
          new-g (inc g)
          {:keys [better new-best-g new-came-from new-counter]}
          (reduce (partial try-improve-neighbor new-g current)
                  {:better [] :new-best-g best-g :new-came-from came-from :new-counter counter}
                  valid-neighbors)
          new-entries (for [[n cnt] better
                            :let [new-f (+ new-g (heuristic n goal))]]
                        [new-f new-g cnt n])]
      [(into (disj open-set (first open-set)) new-entries)
       new-closed new-best-g new-came-from new-counter])))

(defn- a-star-loop
  "Core A* search loop. Returns path vector or nil."
  [start goal unit-type game-map passability-fn neighbor-filter]
  (loop [open-set (sorted-set [(heuristic start goal) 0 0 start])
         closed-set #{}
         best-g {start 0}
         came-from {}
         counter 1]
    (when-let [[_f g _cnt current] (first open-set)]
      (if (= current goal)
        (reconstruct-path came-from start goal)
        (let [[os cs bg cf ct]
              (expand-node current g open-set closed-set best-g came-from counter
                           unit-type game-map passability-fn neighbor-filter goal)]
          (recur os cs bg cf ct))))))

(defn a-star
  "Finds shortest path from start to goal for unit-type.
   Returns vector of positions from start to goal (inclusive), or nil if no path.
   When passability-fn is provided, uses it instead of default passable? check."
  ([start goal unit-type game-map]
   (a-star-loop start goal unit-type game-map nil nil))
  ([start goal unit-type game-map passability-fn]
   (a-star-loop start goal unit-type game-map passability-fn nil))
  ([start goal unit-type game-map passability-fn neighbor-filter]
   (if (= start goal)
     [start]
     (a-star-loop start goal unit-type game-map passability-fn neighbor-filter))))

(defn bounded-a-star
  "Radius-limited variant of a-star. Only explores cells within radius of the
   midpoint between start and goal. Returns path vector or nil."
  [start goal unit-type game-map]
  (let [[sr sc] start
        [gr gc] goal
        mid-r (quot (+ sr gr) 2)
        mid-c (quot (+ sc gc) 2)
        radius (+ (max (Math/abs (- sr gr)) (Math/abs (- sc gc))) 5)
        in-bounds? (fn [[r c]]
                     (and (<= (Math/abs (- r mid-r)) radius)
                          (<= (Math/abs (- c mid-c)) radius)))]
    (a-star start goal unit-type game-map nil in-bounds?)))

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
            (let [neighbors (for [[dr dc] neighbor-offsets
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
          neighbor-offsets)))

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
                                    (get-passable-neighbors current unit-type game-map))
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
                             neighbor-offsets)
        has-known-land (some (fn [[dx dy]]
                               (let [nx (+ x dx) ny (+ y dy)
                                     cell (get-in computer-map [nx ny])]
                                 (and cell
                                      (#{:land :city} (:type cell)))))
                             neighbor-offsets)]
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
                                    (get-passable-neighbors current unit-type game-map))
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
              (vec (rest (reconstruct-path came-from start current)))
              (let [[x y] current
                    neighbors (for [[dx dy] neighbor-offsets
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
          neighbor-offsets)))

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
              (vec (rest (reconstruct-path came-from start current)))
              (let [[x y] current
                    neighbors (for [[dx dy] neighbor-offsets
                                    :let [nx (+ x dx) ny (+ y dy) n [nx ny]]
                                    :when (and (not (visited n))
                                               (passable-sea? n))]
                                n)
                    new-visited (into visited neighbors)
                    new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
                (recur (into rest-queue neighbors)
                       new-visited
                       new-came-from)))))))))

;; BFS lookahead: after finding the first target, continue for 4 more
;; BFS levels collecting alternatives. Prefer unowned coast (invasion
;; target) over unexplored coast (scouting target); within each
;; category pick the nearest.
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
    (for [[dx dy] neighbor-offsets
          :let [nx (+ x dx) ny (+ y dy) n [nx ny]]
          :when (and (not (visited n))
                     (passable-sea? n))]
      n)))

(defn- build-coast-path
  [best-unowned best-unexplored came-from start]
  (when-let [target (or best-unowned best-unexplored)]
    (vec (rest (reconstruct-path came-from start target)))))

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

(def ^:private min-explore-depth
  "Minimum BFS levels before accepting a target in patrol explore mode."
  4)

(defn- adjacent-to-land-or-city?
  "Returns true if any neighbor of pos on the given map is land or city."
  [pos game-map]
  (let [[x y] pos]
    (some (fn [[dx dy]]
            (let [nx (+ x dx) ny (+ y dy)
                  cell (get-in game-map [nx ny])]
              (and cell (#{:land :city} (:type cell)))))
          neighbor-offsets)))

(defn- unseen-coast?
  "Returns true if pos is adjacent to land/city and not in seen-coast."
  [pos game-map seen-coast]
  (and (not (contains? seen-coast pos))
       (adjacent-to-land-or-city? pos game-map)))

(defn bfs-to-unseen-coast
  "BFS from start over passable sea cells to find the nearest unseen coastal
   cell (adjacent to land/city on computer-map, not in seen-coast) or cell
   adjacent to unexplored territory. Skips targets within min-explore-depth
   levels and targets in excluded set. Prefers unseen coast over unexplored.
   Returns path excluding start, or nil."
  [start computer-map excluded]
  (let [seen-coast @atoms/seen-coast
        passable-sea? (fn [pos]
                        (let [cell (get-in computer-map pos)]
                          (and cell (= :sea (:type cell)))))]
    (when (passable-sea? start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start 0])
             visited #{start}
             came-from {}
             best-coast nil
             best-unexplored nil]
        (if (empty? queue)
          (build-coast-path best-coast best-unexplored came-from start)
          (let [[current depth] (peek queue)
                deep-enough? (>= depth min-explore-depth)
                available? (and deep-enough?
                                (not= current start)
                                (not (contains? excluded current)))
                is-coast? (and available?
                               (unseen-coast? current computer-map seen-coast))
                is-unexplored? (and available?
                                    (nil? best-unexplored)
                                    (adjacent-to-unexplored? current computer-map))
                new-coast (if (and is-coast? (nil? best-coast)) current best-coast)
                new-unexplored (if is-unexplored? current best-unexplored)]
            (if new-coast
              (build-coast-path new-coast new-unexplored came-from start)
              (let [neighbors (bfs-sea-neighbors current visited passable-sea?)
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
          neighbor-offsets)))

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
            (let [neighbors (get-passable-neighbors current :transport game-map)
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

(defn- cache-sub-paths!
  "Caches all sub-paths of a computed path so subsequent steps are O(1) lookups.
   cache-key-extra distinguishes paths with different passability constraints."
  [path goal unit-type cache-key-extra]
  (loop [remaining path]
    (when (>= (count remaining) 2)
      (swap! path-cache assoc [(first remaining) goal unit-type cache-key-extra] remaining)
      (recur (subvec remaining 1)))))

(defn- chebyshev
  "Chebyshev (chessboard) distance between two positions."
  [[sr sc] [gr gc]]
  (max (Math/abs (- sr gr)) (Math/abs (- sc gc))))

(def ^:private naval-types
  #{:transport :destroyer :submarine :carrier :battleship :patrol-boat})

(defn- try-network-route
  "Attempts to route through the sea lane network for naval units.
   Returns a path or nil."
  [start goal unit-type]
  (when (naval-types unit-type)
    (let [network @atoms/sea-lane-network
          game-map @atoms/game-map]
      (sea-lanes/route-through-network network start goal unit-type game-map bounded-a-star))))

(defn- compute-network-step
  "Tries sea-lane network route for distant naval goals. Returns next step or nil."
  [start goal unit-type cache-key-extra]
  (when (> (chebyshev start goal) config/sea-lane-local-radius)
    (when-let [net-path (try-network-route start goal unit-type)]
      (cache-sub-paths! net-path goal unit-type cache-key-extra)
      (second net-path))))

(defn- compute-a-star-step
  "Computes A* path and returns next step. Records naval paths to sea-lane network."
  [start goal unit-type passability-fn cache-key-extra]
  (when-let [path (a-star start goal unit-type @atoms/game-map passability-fn)]
    (when (and (naval-types unit-type) (not passability-fn))
      (sea-lanes/record-path! path))
    (cache-sub-paths! path goal unit-type cache-key-extra)
    (second path)))

(defn next-step
  "Returns the next step toward goal, or nil if unreachable or already at goal."
  ([start goal unit-type]
   (next-step start goal unit-type nil nil))
  ([start goal unit-type passability-fn cache-key-extra]
   (when (not= start goal)
     (if-let [cached (get @path-cache [start goal unit-type cache-key-extra])]
       (second cached)
       (or (when-not passability-fn
             (compute-network-step start goal unit-type cache-key-extra))
           (compute-a-star-step start goal unit-type passability-fn cache-key-extra))))))
