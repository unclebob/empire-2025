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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:31.551134-05:00", :module-hash "736930817", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1683551464"} {:id "def/bfs-unexplored-cache", :kind "def", :line 4, :end-line 6, :hash "672637355"} {:id "def/bfs-unload-cache", :kind "def", :line 8, :end-line 10, :hash "1766421358"} {:id "defn/clear-bfs-caches", :kind "defn", :line 12, :end-line 16, :hash "1190664237"} {:id "defn/get-unexplored", :kind "defn", :line 18, :end-line 19, :hash "-334736636"} {:id "defn/has-unexplored?", :kind "defn", :line 21, :end-line 22, :hash "-1598842624"} {:id "defn/put-unexplored!", :kind "defn", :line 24, :end-line 26, :hash "942893796"} {:id "defn/get-unload", :kind "defn", :line 28, :end-line 29, :hash "290524889"} {:id "defn/has-unload?", :kind "defn", :line 31, :end-line 32, :hash "-1936821585"} {:id "defn/put-unload!", :kind "defn", :line 34, :end-line 36, :hash "-250845156"}]}
;; clj-mutate-manifest-end
