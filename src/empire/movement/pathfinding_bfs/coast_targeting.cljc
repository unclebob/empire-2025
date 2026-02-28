(ns empire.movement.pathfinding-bfs.coast-targeting
  "Coastal BFS target selection over sea routes."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.pathfinding-bfs.core :as core]
            [empire.movement.pathfinding-bfs.exploration :as exploration]))

(defn sea-reaches-edge?
  "BFS flood-fill from pos over sea cells. Returns true if any reachable sea cell is on map edge."
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
  "BFS from start over explored sea cells on computer-map to find nearest
   cell adjacent to non-computer land/city on game-map."
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

(defn- classify-coastal
  [current start computer-map]
  (if (= current start)
    [false false]
    [(adjacent-to-unowned? current computer-map)
     (exploration/adjacent-to-unexplored? current computer-map)]))

(defn bfs-to-coast-target
  "Combined BFS over explored sea cells seeking cells adjacent to
   unowned land/city or unexplored territory, both on computer-map.
   Continues coast-lookahead levels past first hit to prefer unowned coast."
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
          (core/build-coast-path best-unowned best-unexplored came-from start)
          (let [[current depth] (peek queue)
                [unowned? unexplored?] (classify-coastal current start computer-map)
                hit? (or unowned? unexplored?)
                neighbors (core/bfs-sea-neighbors current visited passable-sea?)
                new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
            (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) neighbors)
                   (into visited neighbors)
                   new-came-from
                   (core/update-first-match hit? first-hit-depth depth)
                   (core/update-first-match unowned? best-unowned current)
                   (core/update-first-match unexplored? best-unexplored current))))))))
