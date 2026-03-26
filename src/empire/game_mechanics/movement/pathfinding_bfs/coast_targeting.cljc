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
           (#{:free :player} (:city-status cell)))))

(defn- claimed-land?
  [cell]
  (or (and (= :land (:type cell))
           (some? (:country-id cell)))
      (and (= :city (:type cell))
           (= :computer (:city-status cell)))))

(defn- land-or-city?
  [cell]
  (contains? #{:land :city} (:type cell)))

(defn- adjacent-cells
  [pos computer-map pred]
  (let [[x y] pos
        height (count computer-map)
        width (count (first computer-map))]
    (for [[dx dy] map-utils/neighbor-offsets
          :let [nx (+ x dx)
                ny (+ y dy)
                neighbor [nx ny]]
          :when (and (>= nx 0) (< nx height)
                     (>= ny 0) (< ny width)
                     (pred (get-in computer-map neighbor)))]
      neighbor)))

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

(defn- land-reachable-from-adjacent
  [start computer-map]
  (let [starts (vec (adjacent-cells start computer-map land-or-city?))]
    (loop [queue (reduce conj clojure.lang.PersistentQueue/EMPTY starts)
           visited (set starts)]
      (if (empty? queue)
        visited
        (let [current (peek queue)
              neighbors (remove visited
                                (adjacent-cells current computer-map land-or-city?))]
          (recur (reduce conj (pop queue) neighbors)
                 (into visited neighbors)))))))

(defn- adjacent-unclaimed-land-reachable?
  [pos computer-map reachable-land]
  (some reachable-land
        (adjacent-cells pos computer-map unclaimed-land?)))

(defn- connected-unclaimed-land
  [computer-map starts]
  (loop [queue (reduce conj clojure.lang.PersistentQueue/EMPTY starts)
         visited (set starts)]
    (if (empty? queue)
      visited
      (let [current (peek queue)
            neighbors (remove visited
                              (adjacent-cells current computer-map unclaimed-land?))]
        (recur (reduce conj (pop queue) neighbors)
               (into visited neighbors))))))

(defn- unload-capacity-score
  [pos computer-map]
  (let [adjacent-land (vec (adjacent-cells pos computer-map unclaimed-land?))
        connected-land (connected-unclaimed-land computer-map adjacent-land)]
    {:immediate-slots (count adjacent-land)
     :connected-land-size (count connected-land)}))

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

(defn- select-best-candidate [candidates came-from start]
  (let [preferred (or (seq (filter #(>= (:immediate-slots %) 2) candidates))
                      candidates)]
    (when-let [best (first (sort-by (juxt :depth
                                          (comp - :immediate-slots)
                                          (comp - :connected-land-size)
                                          :pos)
                                    preferred))]
      (vec (rest (map-utils/reconstruct-path came-from start (:pos best)))))))

(defn- bfs-to-adjacent-target
  [start computer-map target?]
  (let [passable-sea? #(core/transport-passable-sea? computer-map start %)]
    (when (passable-sea? start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start 0])
             visited #{start}
             came-from {}
             candidates []
             first-hit-depth nil]
        (if (or (empty? queue) (bfs-past-lookahead? queue first-hit-depth))
          (select-best-candidate candidates came-from start)
          (let [[current depth] (peek queue)
                neighbors (core/bfs-sea-neighbors current visited passable-sea?)
                new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)
                hit? (target? current)
                score (when hit? (unload-capacity-score current computer-map))]
            (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) neighbors)
                   (into visited neighbors)
                   new-came-from
                   (cond-> candidates
                     hit? (conj (assoc score :pos current :depth depth)))
                   (or first-hit-depth (when hit? depth)))))))))

(def ^:private preferred-load-target-distance 4)

(defn- load-target-candidates
  [start computer-map]
  (let [passable-sea? #(core/transport-passable-sea? computer-map start %)]
    (when (passable-sea? start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start 0])
             visited #{start}
             came-from {}
             candidates []]
        (if (empty? queue)
          {:came-from came-from
           :candidates candidates}
          (let [[current depth] (peek queue)
                candidate? (and (not= current start)
                                (adjacent-to-land-kind? current computer-map claimed-land?))
                neighbors (core/bfs-sea-neighbors current visited passable-sea?)
                new-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
            (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) neighbors)
                   (into visited neighbors)
                   new-came-from
                   (cond-> candidates
                     candidate? (conj {:pos current :depth depth})))))))))

(defn- choose-load-target
  [candidates]
  (first
   (sort-by (juxt #(Math/abs (long (- (:depth %) preferred-load-target-distance)))
                  :depth)
            candidates)))

(defn bfs-to-unload-target
  "Loaded transports seek the nearest reachable sea cell adjacent to unclaimed land.
   If none exists, they fall back to the nearest reachable unexplored coast."
  [start computer-map]
  (let [reachable-land (land-reachable-from-adjacent start computer-map)
        passable-sea? #(core/transport-passable-sea? computer-map start %)]
    (or (bfs-to-adjacent-target start computer-map
                                #(and (adjacent-to-land-kind? % computer-map unclaimed-land?)
                                      (not (adjacent-unclaimed-land-reachable? % computer-map reachable-land))))
        (exploration/bfs-to-unexplored-coast start computer-map passable-sea?))))

(defn bfs-to-load-target
  "Empty transports seek a reachable sea cell adjacent to claimed land.
   Prefer distance 4, degrading as the target is closer or farther."
  [start computer-map]
  (let [{:keys [came-from candidates]} (load-target-candidates start computer-map)
        target (choose-load-target candidates)]
    (when target
      (vec (rest (map-utils/reconstruct-path came-from start (:pos target)))))))

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
