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
  (map-utils/any-neighbor-matches? pos computer-map map-utils/neighbor-offsets pred))

(defn- flood-fill-connected
  [computer-map starts pred]
  (loop [queue (reduce conj clojure.lang.PersistentQueue/EMPTY starts)
         visited (set starts)]
    (if (empty? queue)
      visited
      (let [current (peek queue)
            neighbors (remove visited
                              (adjacent-cells current computer-map pred))]
        (recur (reduce conj (pop queue) neighbors)
               (into visited neighbors))))))

(defn- land-reachable-from-adjacent
  [start computer-map]
  (flood-fill-connected computer-map
                        (vec (adjacent-cells start computer-map land-or-city?))
                        land-or-city?))

(defn- adjacent-unclaimed-land-reachable?
  [pos computer-map reachable-land]
  (some reachable-land
        (adjacent-cells pos computer-map unclaimed-land?)))

(defn- connected-unclaimed-land
  [computer-map starts]
  (flood-fill-connected computer-map starts unclaimed-land?))

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

(defn- passable-sea-cell?
  [computer-map pos]
  (let [cell (get-in computer-map pos)]
    (and cell (= :sea (:type cell)))))

(defn- unowned-coast-hit?
  [start current computer-map]
  (and (not= current start)
       (adjacent-to-unowned? current computer-map)))

(defn bfs-to-unowned-coast
  "BFS from start over explored sea cells on computer-map to find nearest
   cell adjacent to non-computer land/city on computer-map."
  [start computer-map _game-map]
  (let [passable-sea? #(passable-sea-cell? computer-map %)]
    (when (passable-sea? start)
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
             visited #{start}
             came-from {}]
        (when (seq queue)
          (let [current (peek queue)
                rest-queue (pop queue)]
            (if (unowned-coast-hit? start current computer-map)
              (vec (rest (map-utils/reconstruct-path came-from start current)))
              (let [neighbors (core/bfs-sea-neighbors current visited passable-sea?)]
                (recur (into rest-queue neighbors)
                       (into visited neighbors)
                       (reduce #(assoc %1 %2 current) came-from neighbors))))))))))

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

(defn- enqueue-adjacent-target-neighbors
  [queue current depth neighbors]
  (reduce #(conj %1 [%2 (inc depth)]) (pop queue) neighbors))

(defn- add-adjacent-target-candidate
  [candidates current depth score]
  (if score
    (conj candidates (assoc score :pos current :depth depth))
    candidates))

(defn- adjacent-target-search-step
  [queue visited came-from candidates first-hit-depth computer-map target? passable-sea?]
  (let [[current depth] (peek queue)
        neighbors (core/bfs-sea-neighbors current visited passable-sea?)
        hit? (target? current)
        score (when hit? (unload-capacity-score current computer-map))]
    {:queue (enqueue-adjacent-target-neighbors queue current depth neighbors)
     :visited (into visited neighbors)
     :came-from (reduce #(assoc %1 %2 current) came-from neighbors)
     :candidates (add-adjacent-target-candidate candidates current depth score)
     :first-hit-depth (or first-hit-depth (when hit? depth))}))

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
          (let [{:keys [queue visited came-from candidates first-hit-depth]}
                (adjacent-target-search-step queue visited came-from candidates
                                             first-hit-depth computer-map target? passable-sea?)]
            (recur queue visited came-from candidates first-hit-depth)))))))

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
;; {:version 1, :tested-at "2026-05-07T16:14:01.217023-05:00", :module-hash "-292892047", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1139483106"} {:id "defn/sea-reaches-edge?", :kind "defn", :line 8, :end-line 29, :hash "-2132734889"} {:id "defn-/adjacent-to-unowned?", :kind "defn-", :line 31, :end-line 48, :hash "646172457"} {:id "defn-/unclaimed-land?", :kind "defn-", :line 50, :end-line 55, :hash "-1002774785"} {:id "defn-/claimed-land?", :kind "defn-", :line 57, :end-line 62, :hash "1424037850"} {:id "defn-/land-or-city?", :kind "defn-", :line 64, :end-line 66, :hash "-1450895749"} {:id "defn-/adjacent-cells", :kind "defn-", :line 68, :end-line 80, :hash "-191171460"} {:id "defn-/adjacent-to-land-kind?", :kind "defn-", :line 82, :end-line 84, :hash "212834926"} {:id "defn-/flood-fill-connected", :kind "defn-", :line 86, :end-line 96, :hash "1824893168"} {:id "defn-/land-reachable-from-adjacent", :kind "defn-", :line 98, :end-line 102, :hash "2108709975"} {:id "defn-/adjacent-unclaimed-land-reachable?", :kind "defn-", :line 104, :end-line 107, :hash "-1605652787"} {:id "defn-/connected-unclaimed-land", :kind "defn-", :line 109, :end-line 111, :hash "704258928"} {:id "defn-/unload-capacity-score", :kind "defn-", :line 113, :end-line 118, :hash "624483247"} {:id "defn-/outside-radius?", :kind "defn-", :line 120, :end-line 124, :hash "1876923270"} {:id "defn/bfs-to-unowned-coast", :kind "defn", :line 126, :end-line 153, :hash "-1761508707"} {:id "def/coast-lookahead", :kind "def", :line 155, :end-line 155, :hash "-1896136666"} {:id "defn-/bfs-past-lookahead?", :kind "defn-", :line 157, :end-line 162, :hash "-1287656638"} {:id "defn-/classify-coastal", :kind "defn-", :line 164, :end-line 171, :hash "1666016991"} {:id "defn-/select-best-candidate", :kind "defn-", :line 173, :end-line 181, :hash "-1946499112"} {:id "defn-/enqueue-adjacent-target-neighbors", :kind "defn-", :line 183, :end-line 185, :hash "507308901"} {:id "defn-/add-adjacent-target-candidate", :kind "defn-", :line 187, :end-line 191, :hash "-1602320296"} {:id "defn-/adjacent-target-search-step", :kind "defn-", :line 193, :end-line 203, :hash "418270877"} {:id "defn-/bfs-to-adjacent-target", :kind "defn-", :line 205, :end-line 219, :hash "-1961625399"} {:id "def/preferred-load-target-distance", :kind "def", :line 221, :end-line 221, :hash "-1722393738"} {:id "defn-/load-target-candidates", :kind "defn-", :line 223, :end-line 243, :hash "724999035"} {:id "defn-/choose-load-target", :kind "defn-", :line 245, :end-line 250, :hash "1685017029"} {:id "defn/bfs-to-unload-target", :kind "defn", :line 252, :end-line 261, :hash "1052541789"} {:id "defn/bfs-to-load-target", :kind "defn", :line 263, :end-line 270, :hash "-1066724809"} {:id "defn/bfs-to-coast-target", :kind "defn", :line 272, :end-line 278, :hash "1437684893"}]}
;; clj-mutate-manifest-end
