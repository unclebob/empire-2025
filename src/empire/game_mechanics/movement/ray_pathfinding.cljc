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
                     (apply min-key
                            (fn [[r c]]
                              (+ (Math/abs (- r (first target)))
                                 (Math/abs (- c (second target)))))
                            nbrs))]
          (when best
            (recur best
                   (conj visited best)
                   (conj coast-path best)
                   (inc steps))))))))

(defn- dual-crawl-to-clear-ray
  "Crawl both directions along coast from start-cell.
   Returns the coast-path from the direction that finds a clear ray first."
  [game-map neighbors start-cell target max-steps]
  (let [all-nbrs (vec (get neighbors start-cell))
        results (keep (fn [first-step]
                        (crawl-to-clear-ray game-map neighbors first-step target max-steps))
                      all-nbrs)]
    (first (sort-by (comp count second) results))))

(defn- ray-crawl-path
  "Build a path using ray+crawl. Returns path vector or nil.
   Tries up to max-rays rays with coast crawls between them."
  [game-map coastal-index start target max-rays]
  (let [{:keys [coastal-sea-cells coastal-sea-neighbors]} coastal-index
        max-crawl-steps (* 2 (+ (Math/abs (- (first start) (first target)))
                                 (Math/abs (- (second start) (second target)))))]
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
        (let [hit (first-land-hit game-map current target)]
          (when hit
            (let [coast-entry (nearest-coastal-sea coastal-sea-cells hit)]
              (when coast-entry
                (let [ray-to-coast (vec (rest (bresenham-line current coast-entry)))
                      crawl-result (dual-crawl-to-clear-ray
                                    game-map coastal-sea-neighbors
                                    coast-entry target max-crawl-steps)]
                  (when crawl-result
                    (let [[exit-cell coast-path] crawl-result
                          new-path (into (into path ray-to-coast) (rest coast-path))]
                      (recur exit-cell new-path (inc rays-used)))))))))))))

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
            (loop [path [] pos target]
              (if (= pos start)
                (vec (reverse path))
                (recur (conj path pos) (get came-from pos))))
            (let [neighbors (for [[dr dc] map-utils/neighbor-offsets
                                  :let [nr (+ (first current) dr)
                                        nc (+ (second current) dc)
                                        npos [nr nc]]
                                  :when (and (not (visited npos))
                                             (let [cell (get-in game-map npos)]
                                               (and cell (= :sea (:type cell)))))]
                              npos)
                  new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
              (recur (into (pop queue) neighbors)
                     (into visited neighbors)
                     new-came-from))))))))

(defn find-sea-path
  "Find a sea path from start to target using ray+crawl with BFS fallback.
   Returns vector of positions (excluding start, including target), or nil."
  [start target]
  (let [game-map (sa/current-world)
        coastal-index (sa/read-state :coastal-index)]
    (if (= start target)
      []
      (or (when coastal-index
            (ray-crawl-path game-map coastal-index start target 4))
          (bfs-sea-path game-map start target)))))
