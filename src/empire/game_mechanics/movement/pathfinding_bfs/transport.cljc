(ns empire.game-mechanics.movement.pathfinding-bfs.transport
  "Transport and land-HO BFS helpers."
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.pathfinding-bfs.cache :as cache]
            [empire.game-mechanics.movement.pathfinding-bfs.core :as core]))

(defn adjacent-to-target-continent-land?
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
  "BFS from start over sea cells to find nearest empty sea cell adjacent
   to land on target-continent."
  [start target-continent]
  (let [game-map (sa/read-state :computer-map)]
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
  "BFS from start over sea cells to find nearest empty sea cell adjacent
   to land on target-continent. Cached per target-continent each round."
  [start target-continent]
  (if (cache/has-unload? target-continent)
    (cache/get-unload target-continent)
    (cache/put-unload! target-continent
                       (find-nearest-unload-uncached start target-continent))))

(defn- adjacent-to-target?
  "Returns true if any neighbor of pos equals target-city."
  [pos target-city]
  (let [[x y] pos]
    (some (fn [[dx dy]]
            (= target-city [(+ x dx) (+ y dy)]))
          map-utils/neighbor-offsets)))

(defn- land-ho-arrived?
  [start current target-city]
  (and (not= current start)
       (adjacent-to-target? current target-city)))

(defn- land-ho-search
  [start target-city passable-sea?]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
         visited #{start}
         came-from {}]
    (when (seq queue)
      (let [current (peek queue)]
        (if (land-ho-arrived? start current target-city)
          (vec (rest (map-utils/reconstruct-path came-from start current)))
          (let [neighbors (core/bfs-sea-neighbors current visited passable-sea?)]
            (recur (reduce conj (pop queue) neighbors)
                   (into visited neighbors)
                   (reduce #(assoc %1 %2 current) came-from neighbors))))))))

(defn bfs-to-land-ho-target
  "BFS over sea cells on computer-map from start toward target-city.
   Returns path of sea cells (excluding start) ending adjacent to target-city.
   Returns [] if start is already adjacent to target-city."
  [start target-city computer-map]
  (let [passable-sea? (fn [pos]
                        (let [cell (get-in computer-map pos)]
                          (and cell (= :sea (:type cell)))))]
    (if (adjacent-to-target? start target-city)
      []
      (when (passable-sea? start)
        (land-ho-search start target-city passable-sea?)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:59.71611-05:00", :module-hash "583664986", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "2073553519"} {:id "defn/adjacent-to-target-continent-land?", :kind "defn", :line 8, :end-line nil, :hash "1305370384"} {:id "defn-/find-nearest-unload-uncached", :kind "defn-", :line 21, :end-line nil, :hash "557224228"} {:id "defn/find-nearest-unload-position", :kind "defn", :line 43, :end-line nil, :hash "1788335098"} {:id "defn-/adjacent-to-target?", :kind "defn-", :line 52, :end-line nil, :hash "317433455"} {:id "defn-/land-ho-arrived?", :kind "defn-", :line 60, :end-line nil, :hash "304796543"} {:id "defn-/land-ho-search", :kind "defn-", :line 65, :end-line nil, :hash "95966172"} {:id "defn/bfs-to-land-ho-target", :kind "defn", :line 79, :end-line nil, :hash "2010513063"}]}
;; clj-mutate-manifest-end
