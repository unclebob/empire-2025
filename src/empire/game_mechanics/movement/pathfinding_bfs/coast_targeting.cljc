(ns empire.game-mechanics.movement.pathfinding-bfs.coast-targeting
  "Coastal BFS target selection over sea routes."
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.pathfinding-bfs.core :as core]
            [empire.game-mechanics.movement.pathfinding-bfs.exploration :as exploration]))

(defn sea-reaches-edge?
  "BFS flood-fill from pos over sea cells. Returns true if any reachable sea cell is on map edge."
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        rows (count computer-map)
        cols (count (first computer-map))]
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
                                             (= :sea (:type (get-in computer-map [nr nc]))))]
                              [nr nc])
                  new-visited (into visited neighbors)]
              (recur (into (pop queue) neighbors) new-visited))))))))

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

(defn- unclaimed-land?
  [cell]
  (or (and (= :land (:type cell))
           (nil? (:country-id cell)))
      (and (= :city (:type cell))
           (#{:free :computer} (:city-status cell)))))

(defn- claimed-land?
  [cell]
  (and (= :land (:type cell))
       (some? (:country-id cell))))

(defn- adjacent-to-land-kind?
  [pos computer-map pred]
  (let [[x y] pos
        height (count computer-map)
        width (count (first computer-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (pred (get-in computer-map [nx ny])))))
          map-utils/neighbor-offsets)))

(defn- outside-radius?
  [start pos radius]
  (> (max (Math/abs (long (- (first pos) (first start))))
          (Math/abs (long (- (second pos) (second start)))))
     radius))

(defn bfs-to-unowned-coast
  "BFS from start over explored sea cells on computer-map to find nearest
   cell adjacent to non-computer land/city on computer-map."
  [start computer-map _game-map]
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
                     (adjacent-to-unowned? current computer-map))
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

(defn- classify-coastal
  [current start computer-map army-count]
  (if (= current start)
    false
    (if (pos? army-count)
      (adjacent-to-land-kind? current computer-map unclaimed-land?)
      (and (outside-radius? start current coast-lookahead)
           (adjacent-to-land-kind? current computer-map claimed-land?)))))

(defn- bfs-to-adjacent-target
  [start computer-map target?]
  (let [passable-sea? (fn [pos]
                        (let [cell (get-in computer-map pos)]
                          (and cell (= :sea (:type cell)))))]
    (when (passable-sea? start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start 0])
             visited #{start}
             came-from {}
             best-target nil]
        (if (or (empty? queue) best-target)
          (when best-target
            (vec (rest (map-utils/reconstruct-path came-from start best-target))))
          (let [[current depth] (peek queue)
                neighbors (core/bfs-sea-neighbors current visited passable-sea?)
                new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
            (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) neighbors)
                   (into visited neighbors)
                   new-came-from
                   (core/update-first-match (target? current) best-target current))))))))

(defn bfs-to-unload-target
  "Loaded transports seek the nearest reachable sea cell adjacent to unclaimed land.
   If none exists, they fall back to the nearest reachable unexplored coast."
  [start computer-map]
  (or (bfs-to-adjacent-target start computer-map
                              #(adjacent-to-land-kind? % computer-map unclaimed-land?))
      (exploration/bfs-to-unexplored-coast start computer-map)))

(defn bfs-to-load-target
  "Empty transports seek the nearest reachable sea cell adjacent to claimed land."
  [start computer-map]
  (bfs-to-adjacent-target start computer-map
                          #(adjacent-to-land-kind? % computer-map claimed-land?)))

(defn bfs-to-coast-target
  "Compatibility wrapper for older callers.
   Loaded transports seek unload targets; empty transports seek load targets."
  [start computer-map army-count]
  (if (pos? army-count)
    (bfs-to-unload-target start computer-map)
    (bfs-to-load-target start computer-map)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:34.617378-05:00", :module-hash "267691541", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1139483106"} {:id "defn/sea-reaches-edge?", :kind "defn", :line 8, :end-line 29, :hash "-359414710"} {:id "defn-/adjacent-to-unowned?", :kind "defn-", :line 31, :end-line 48, :hash "646172457"} {:id "defn/bfs-to-unowned-coast", :kind "defn", :line 50, :end-line 77, :hash "1116181976"} {:id "def/coast-lookahead", :kind "def", :line 79, :end-line 79, :hash "-1896136666"} {:id "defn-/bfs-past-lookahead?", :kind "defn-", :line 81, :end-line 86, :hash "-1287656638"} {:id "defn-/classify-coastal", :kind "defn-", :line 88, :end-line 93, :hash "-1071986533"} {:id "defn/bfs-to-coast-target", :kind "defn", :line 95, :end-line 122, :hash "2129782618"}]}
;; clj-mutate-manifest-end
