(ns empire.game-mechanics.movement.pathfinding-bfs.exploration
  "BFS exploration and unseen-coast target selection."
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.pathfinding-bfs.cache :as cache]
            [empire.game-mechanics.movement.pathfinding-bfs.core :as core]))

(defn adjacent-to-unexplored?
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

(defn at-exploration-frontier?
  "Returns true if pos is a sea cell adjacent to both unexplored and known land."
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

(defn- find-nearest-unexplored-uncached
  "BFS from start over passable cells to find nearest cell adjacent to unexplored."
  [start unit-type]
  (let [computer-map (sa/read-state :computer-map)]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (when (seq queue)
        (let [current (peek queue)
              rest-queue (pop queue)]
          (if (and (not= current start)
                   (adjacent-to-unexplored? current computer-map))
            current
            (let [neighbors (remove visited
                                    (map-utils/get-passable-neighbors current unit-type computer-map))
                  new-visited (into visited neighbors)
                  new-queue (into rest-queue neighbors)]
              (recur new-queue new-visited))))))))

(defn find-nearest-unexplored
  "BFS from start over passable cells to find nearest cell adjacent to unexplored.
   Cached per unit-type each round."
  [start unit-type]
  (if (cache/has-unexplored? unit-type)
    (cache/get-unexplored unit-type)
    (cache/put-unexplored! unit-type (find-nearest-unexplored-uncached start unit-type))))

(defn- find-nearest-unexplored-coastline-uncached
  "BFS from start over passable sea cells to find nearest coastal exploration frontier."
  [start unit-type]
  (let [computer-map (sa/read-state :computer-map)]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (when (seq queue)
        (let [current (peek queue)
              rest-queue (pop queue)]
          (if (and (not= current start)
                   (at-exploration-frontier? current computer-map))
            current
            (let [neighbors (remove visited
                                    (map-utils/get-passable-neighbors current unit-type computer-map))
                  new-visited (into visited neighbors)
                  new-queue (into rest-queue neighbors)]
              (recur new-queue new-visited))))))))

(defn find-nearest-unexplored-coastline
  "BFS to find nearest sea cell at a coastal exploration frontier.
   Cached per unit-type each round."
  [start unit-type]
  (let [cache-key [:coastline unit-type]]
    (if (cache/has-unexplored? cache-key)
      (cache/get-unexplored cache-key)
      (cache/put-unexplored! cache-key (find-nearest-unexplored-coastline-uncached start unit-type)))))

(defn- unexplored-coast-hit?
  [start current computer-map]
  (and (not= current start)
       (adjacent-to-unexplored? current computer-map)))

(defn- search-unexplored-coast
  [start computer-map passable-sea?]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
         visited #{start}
         came-from {}]
    (when (seq queue)
      (let [current (peek queue)
            rest-queue (pop queue)]
        (if (unexplored-coast-hit? start current computer-map)
          (vec (rest (map-utils/reconstruct-path came-from start current)))
          (let [neighbors (core/bfs-sea-neighbors current visited passable-sea?)]
            (recur (into rest-queue neighbors)
                   (into visited neighbors)
                   (reduce #(assoc %1 %2 current) came-from neighbors))))))))

(defn bfs-to-unexplored-coast
  "BFS from start over explored sea cells on computer-map to find nearest
   cell adjacent to unexplored territory. Returns path excluding start."
  ([start computer-map]
   (bfs-to-unexplored-coast start computer-map
                            #(core/passable-sea? computer-map %)))
  ([start computer-map passable-sea?]
    (when (passable-sea? start)
      (search-unexplored-coast start computer-map passable-sea?))))

(def ^:private min-explore-depth 4)
(def ^:private max-bfs-cells 1500)

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

(defn available-for-target?
  "Returns true if current cell is deep enough, not start, and not excluded."
  [current start depth excluded]
  (and (>= depth min-explore-depth)
       (not= current start)
       (not (contains? excluded current))))

(defn unexplored-target?
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
    [(core/update-first-match
       (unseen-coast? current computer-map seen-coast) best-coast current)
     (core/update-first-match
       (unexplored-target? current best-unexplored computer-map) best-unexplored current)]))

(defn bfs-to-unseen-coast
  "BFS from start over passable sea cells to find unseen coast or unexplored-adjacent cell.
   Returns path excluding start, or nil."
  [start computer-map excluded]
  (let [seen-coast (sa/read-state :seen-coast)
        sea? (partial core/passable-sea? computer-map)]
    (when (core/passable-sea? computer-map start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start 0])
             visited #{start}
             came-from {}
             best-coast nil
             best-unexplored nil
             cells-remaining max-bfs-cells]
        (if (or (empty? queue) (zero? cells-remaining))
          (core/build-coast-path best-coast best-unexplored came-from start)
          (let [[current depth] (peek queue)
                [new-coast new-unexplored]
                (classify-unseen-step current start depth excluded
                                      best-coast best-unexplored computer-map seen-coast)]
            (if new-coast
              (core/build-coast-path new-coast new-unexplored came-from start)
              (let [neighbors (core/bfs-sea-neighbors current visited sea?)
                    new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
                (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) neighbors)
                       (into visited neighbors)
                       new-came-from
                       new-coast
                       new-unexplored
                       (dec cells-remaining))))))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:19:01.033063-05:00", :module-hash "-711537965", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1191705977"} {:id "defn/adjacent-to-unexplored?", :kind "defn", :line 8, :end-line nil, :hash "-269625387"} {:id "defn/at-exploration-frontier?", :kind "defn", :line 25, :end-line nil, :hash "320684338"} {:id "defn-/find-nearest-unexplored-uncached", :kind "defn-", :line 47, :end-line nil, :hash "1911745697"} {:id "defn/find-nearest-unexplored", :kind "defn", :line 65, :end-line nil, :hash "2143864113"} {:id "defn-/find-nearest-unexplored-coastline-uncached", :kind "defn-", :line 73, :end-line nil, :hash "239588976"} {:id "defn/find-nearest-unexplored-coastline", :kind "defn", :line 91, :end-line nil, :hash "1170803962"} {:id "defn-/unexplored-coast-hit?", :kind "defn-", :line 100, :end-line nil, :hash "1418961009"} {:id "defn-/search-unexplored-coast", :kind "defn-", :line 105, :end-line nil, :hash "1808074972"} {:id "defn/bfs-to-unexplored-coast", :kind "defn", :line 120, :end-line nil, :hash "-1424824958"} {:id "def/min-explore-depth", :kind "def", :line 130, :end-line nil, :hash "285210168"} {:id "def/max-bfs-cells", :kind "def", :line 131, :end-line nil, :hash "-429683916"} {:id "defn-/adjacent-to-land-or-city?", :kind "defn-", :line 133, :end-line nil, :hash "168818448"} {:id "defn-/unseen-coast?", :kind "defn-", :line 143, :end-line nil, :hash "-759884621"} {:id "defn/available-for-target?", :kind "defn", :line 149, :end-line nil, :hash "-116792986"} {:id "defn/unexplored-target?", :kind "defn", :line 156, :end-line nil, :hash "-357351276"} {:id "defn-/classify-unseen-step", :kind "defn-", :line 162, :end-line nil, :hash "-235221637"} {:id "defn/bfs-to-unseen-coast", :kind "defn", :line 173, :end-line nil, :hash "952412364"}]}
;; clj-mutate-manifest-end
