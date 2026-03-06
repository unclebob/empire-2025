(ns empire.game-mechanics.movement.pathfinding-bfs.cache
  "Per-round caches for BFS target selection.")

(def bfs-unexplored-cache
  "Cache for BFS unexplored results: {cache-key result-pos-or-nil}"
  (atom {}))

(def bfs-unload-cache
  "Cache for BFS unload results: {target-continent result-pos-or-nil}"
  (atom {}))

(defn clear-bfs-caches
  "Clears both BFS caches. Called at start of each round."
  []
  (reset! bfs-unexplored-cache {})
  (reset! bfs-unload-cache {}))

(defn get-unexplored [k]
  (get @bfs-unexplored-cache k))

(defn has-unexplored? [k]
  (contains? @bfs-unexplored-cache k))

(defn put-unexplored! [k v]
  (swap! bfs-unexplored-cache assoc k v)
  v)

(defn get-unload [k]
  (get @bfs-unload-cache k))

(defn has-unload? [k]
  (contains? @bfs-unload-cache k))

(defn put-unload! [k v]
  (swap! bfs-unload-cache assoc k v)
  v)
