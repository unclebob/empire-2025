(ns empire.game-mechanics.movement.ray-pathfinding
  "Ray+crawl sea pathfinding using precomputed coastal index.
   Fires rays toward target, crawls coastline when blocked,
   falls back to BFS after 4 rays."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]))

(defn bresenham-line
  "Returns vector of [row col] cells along a line from start to end."
  [[r1 c1] [r2 c2]]
  (let [dr (Math/abs (- r2 r1))
        dc (Math/abs (- c2 c1))
        sr (if (< r1 r2) 1 -1)
        sc (if (< c1 c2) 1 -1)]
    (loop [r r1 c c1 err (- dr dc) cells []]
      (let [cells (conj cells [r c])]
        (if (and (= r r2) (= c c2))
          cells
          (let [e2 (* 2 err)
                [r' err'] (if (> e2 (- dc))
                            [(+ r sr) (- err dc)]
                            [r err])
                [c' err''] (if (< e2 dr)
                             [(+ c sc) (+ err' dr)]
                             [c err'])]
            (recur r' c' err'' cells)))))))

(defn ray-clear?
  "Returns true if all cells along the ray from start to end are sea."
  [game-map start end]
  (every? (fn [pos]
            (let [cell (get-in game-map pos)]
              (and cell (= :sea (:type cell)))))
          (bresenham-line start end)))

(defn- first-land-hit
  "Returns the first cell along the ray that is not sea, or nil."
  [game-map start end]
  (first (filter (fn [pos]
                   (let [cell (get-in game-map pos)]
                     (or (nil? cell)
                         (not= :sea (:type cell)))))
                 (rest (bresenham-line start end)))))

(defn- nearest-coastal-sea
  "Finds the nearest coastal-sea-cell to pos."
  [coastal-sea-cells pos]
  (when (seq coastal-sea-cells)
    (apply min-key (fn [[r c]]
                     (+ (Math/abs (- r (first pos)))
                        (Math/abs (- c (second pos)))))
           coastal-sea-cells)))

(defn- manhattan-distance
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2))
     (Math/abs (- c1 c2))))

(defn- crawl-to-clear-ray
  "Walk the coastal-sea neighbor map in one direction from start-cell,
   looking for a cell with a clear ray to target.
   Returns [coastal-cell path-along-coast] or nil."
  [game-map neighbors start-cell target max-steps]
  (loop [current start-cell
         visited #{start-cell}
         coast-path [start-cell]
         steps 0]
    (when (< steps max-steps)
      (if (ray-clear? game-map current target)
        [current coast-path]
        (let [nbrs (remove visited (get neighbors current))
              ;; prefer neighbor that reduces distance to target
              best (when (seq nbrs)
                     (apply min-key #(manhattan-distance % target) nbrs))]
         (when best
            (let [next-steps (inc steps)]
              (recur best
                     (conj visited best)
                     (conj coast-path best)
                     next-steps))))))))

(defn- dual-crawl-to-clear-ray
  "Crawl both directions along coast from start-cell.
   Returns the coast-path from the direction that finds a clear ray first."
  [game-map neighbors start-cell target max-steps]
  (let [all-nbrs (vec (get neighbors start-cell))
        results (keep (fn [first-step]
                        (crawl-to-clear-ray game-map neighbors first-step target max-steps))
                      all-nbrs)]
    (first (sort-by (comp count second) results))))

(defn- max-crawl-steps
  [start target]
  (* 2 (manhattan-distance start target)))

(defn- ray-crawl-continuation
  [game-map coastal-sea-cells coastal-sea-neighbors current target max-crawl-steps]
  (when-let [hit (first-land-hit game-map current target)]
    (when-let [coast-entry (nearest-coastal-sea coastal-sea-cells hit)]
      (when-let [[exit-cell coast-path] (dual-crawl-to-clear-ray
                                         game-map coastal-sea-neighbors
                                         coast-entry target max-crawl-steps)]
        {:exit-cell exit-cell
         :ray-to-coast (vec (rest (bresenham-line current coast-entry)))
         :coast-path coast-path}))))

(defn- append-ray-crawl-continuation
  [path {:keys [ray-to-coast coast-path]}]
  (into (into path ray-to-coast) (rest coast-path)))

(defn- ray-crawl-path
  "Build a path using ray+crawl. Returns path vector or nil.
   Tries up to max-rays rays with coast crawls between them."
  [game-map coastal-index start target max-rays]
  (let [{:keys [coastal-sea-cells coastal-sea-neighbors]} coastal-index
        max-crawl-steps (max-crawl-steps start target)]
    (loop [current start
           path []
           rays-used 0]
      (cond
        (= current target)
        (conj path target)

        (>= rays-used max-rays)
        nil

        (ray-clear? game-map current target)
        (into path (rest (bresenham-line current target)))

        :else
        (when-let [{:keys [exit-cell] :as continuation}
                   (ray-crawl-continuation
                    game-map coastal-sea-cells coastal-sea-neighbors
                    current target max-crawl-steps)]
          (let [next-rays-used (inc rays-used)]
            (recur exit-cell
                   (append-ray-crawl-continuation path continuation)
                   next-rays-used)))))))

(defn- reconstruct-path
  [came-from start target]
  (loop [path [] pos target]
    (if (= pos start)
      (vec (reverse path))
      (recur (conj path pos) (get came-from pos)))))

(defn- sea-neighbors
  [game-map visited current]
  (for [[dr dc] map-utils/neighbor-offsets
        :let [nr (+ (first current) dr)
              nc (+ (second current) dc)
              npos [nr nc]]
        :when (and (not (visited npos))
                   (let [cell (get-in game-map npos)]
                     (and cell (= :sea (:type cell)))))]
    npos))

(defn- bfs-sea-path
  "BFS fallback for complex geography. Returns path or nil."
  [game-map start target]
  (when (= :sea (:type (get-in game-map start)))
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}
           came-from {}]
      (when (seq queue)
        (let [current (peek queue)]
          (if (= current target)
            (reconstruct-path came-from start target)
            (let [neighbors (sea-neighbors game-map visited current)
                  new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
              (recur (into (pop queue) neighbors)
                     (into visited neighbors)
                     new-came-from))))))))

(defn find-sea-path
  "Find a sea path from start to target using ray+crawl with BFS fallback.
   Returns vector of positions (excluding start, including target), or nil.
   Uses game-map by default; pass explicit map for fog-of-war pathfinding."
  ([start target]
   (find-sea-path start target (sa/current-world)))
  ([start target game-map]
   (let [coastal-index (sa/read-state :coastal-index)]
     (if (= start target)
       []
       (or (when coastal-index
             (ray-crawl-path game-map coastal-index start target 4))
           (bfs-sea-path game-map start target))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T16:41:01.0952-05:00", :module-hash "-690621581", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "2125353959"} {:id "defn/bresenham-line", :kind "defn", :line 8, :end-line 26, :hash "-1791718926"} {:id "defn/ray-clear?", :kind "defn", :line 28, :end-line 34, :hash "484701493"} {:id "defn-/first-land-hit", :kind "defn-", :line 36, :end-line 43, :hash "-2041629946"} {:id "defn-/nearest-coastal-sea", :kind "defn-", :line 45, :end-line 52, :hash "129153497"} {:id "defn-/manhattan-distance", :kind "defn-", :line 54, :end-line 57, :hash "36846928"} {:id "defn-/crawl-to-clear-ray", :kind "defn-", :line 59, :end-line 80, :hash "-878740197"} {:id "defn-/dual-crawl-to-clear-ray", :kind "defn-", :line 82, :end-line 90, :hash "821179749"} {:id "defn-/max-crawl-steps", :kind "defn-", :line 92, :end-line 94, :hash "-1393701337"} {:id "defn-/ray-crawl-continuation", :kind "defn-", :line 96, :end-line 105, :hash "-1354202066"} {:id "defn-/append-ray-crawl-continuation", :kind "defn-", :line 107, :end-line 109, :hash "-864411927"} {:id "defn-/ray-crawl-path", :kind "defn-", :line 111, :end-line 138, :hash "1505200449"} {:id "defn-/reconstruct-path", :kind "defn-", :line 140, :end-line 145, :hash "-2011862459"} {:id "defn-/sea-neighbors", :kind "defn-", :line 147, :end-line 156, :hash "1840207519"} {:id "defn-/bfs-sea-path", :kind "defn-", :line 158, :end-line 173, :hash "795317324"} {:id "defn/find-sea-path", :kind "defn", :line 175, :end-line 187, :hash "1803816889"}]}
;; clj-mutate-manifest-end
