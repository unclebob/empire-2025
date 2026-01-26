(ns empire.movement.pathfinding
  "Pathfinding for computer armies over land terrain.
   Uses BFS to find shortest path, returns next step or full path."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]))

(defn- can-traverse?
  "Returns true if the cell at pos can be traversed by an army."
  [pos game-map]
  (let [cell (get-in game-map pos)]
    (and cell
         (#{:land :city} (:type cell)))))

(defn- get-traversable-neighbors
  "Returns list of adjacent positions that can be traversed."
  [pos game-map]
  (filter #(can-traverse? % game-map)
          (map (fn [[dr dc]]
                 [(+ (first pos) dr) (+ (second pos) dc)])
               map-utils/neighbor-offsets)))

(defn find-path
  "Find path from start to goal using BFS.
   Returns list of positions from start (exclusive) to goal (inclusive),
   or nil if no path exists. Returns empty list if already at goal."
  [start goal]
  (if (= start goal)
    []
    (let [game-map @atoms/game-map]
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start []])
             visited #{start}]
        (if (empty? queue)
          nil  ; No path found
          (let [[current path] (peek queue)
                queue (pop queue)
                neighbors (get-traversable-neighbors current game-map)]
            (if-let [goal-neighbor (first (filter #(= % goal) neighbors))]
              ;; Found goal - return path including goal
              (conj path goal)
              ;; Continue searching
              (let [unvisited (remove visited neighbors)
                    new-visited (into visited unvisited)
                    new-entries (map (fn [n] [n (conj path n)]) unvisited)]
                (recur (into queue new-entries)
                       new-visited)))))))))

(defn next-step-toward
  "Returns the next position to move to on the path from start toward goal,
   or nil if already at goal or no path exists."
  [start goal]
  (when-let [path (find-path start goal)]
    (first path)))
