(ns empire.game-mechanics.movement.pathfinding
  "A* pathfinding for computer AI units.
   Provides efficient pathfinding that respects terrain constraints."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]))

(def path-cache
  "Cache for computed paths: {[start goal unit-type] path-vector}"
  (atom {}))

(defn- current-world
  []
  (sa/current-world))

(defn clear-path-cache
  "Clears the A* path cache. Called at start of each round."
  []
  (reset! path-cache {}))

(defn heuristic
  "Manhattan distance heuristic for A*."
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1))
     (Math/abs (- y2 y1))))

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
                                          (map-utils/get-passable-neighbors current unit-type game-map passability-fn))
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
        (map-utils/reconstruct-path came-from start goal)
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

(defn- cache-sub-paths!
  "Caches all sub-paths of a computed path so subsequent steps are O(1) lookups.
   cache-key-extra distinguishes paths with different passability constraints."
  [path goal unit-type cache-key-extra]
  (loop [remaining path]
    (when (>= (count remaining) 2)
      (swap! path-cache assoc [(first remaining) goal unit-type cache-key-extra] remaining)
      (recur (subvec remaining 1)))))

(defn- compute-a-star-step
  "Computes A* path and returns next step."
  [start goal unit-type passability-fn cache-key-extra]
  (when-let [path (a-star start goal unit-type (current-world) passability-fn)]
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
       (compute-a-star-step start goal unit-type passability-fn cache-key-extra)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:25.111534-05:00", :module-hash "-1680245561", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "1366657360"} {:id "def/path-cache", :kind "def", :line 7, :end-line 9, :hash "1265249271"} {:id "defn-/current-world", :kind "defn-", :line 11, :end-line 13, :hash "-640438772"} {:id "defn/clear-path-cache", :kind "defn", :line 15, :end-line 18, :hash "-2059314297"} {:id "defn/heuristic", :kind "defn", :line 20, :end-line 24, :hash "1585468598"} {:id "defn-/try-improve-neighbor", :kind "defn-", :line 26, :end-line 34, :hash "-939436980"} {:id "defn-/expand-node", :kind "defn-", :line 36, :end-line 57, :hash "1177631109"} {:id "defn-/a-star-loop", :kind "defn-", :line 59, :end-line 73, :hash "1306611729"} {:id "defn/a-star", :kind "defn", :line 75, :end-line 86, :hash "-870325352"} {:id "defn-/cache-sub-paths!", :kind "defn-", :line 88, :end-line 95, :hash "-952852928"} {:id "defn-/compute-a-star-step", :kind "defn-", :line 97, :end-line 102, :hash "1360303580"} {:id "defn/next-step", :kind "defn", :line 104, :end-line 112, :hash "-2119560801"}]}
;; clj-mutate-manifest-end
