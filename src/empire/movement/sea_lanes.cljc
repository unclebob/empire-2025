;; mutation-tested: 2026-02-22
(ns empire.movement.sea-lanes
  "Sea lane network for computer ship routing.
   Builds a persistent navigation graph from previously computed A* paths.
   Ships route cheaply through this graph instead of full-map A*."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]))

(def empty-network
  {:nodes {} :segments {} :pos->node {} :pos->seg {}
   :next-node-id 1 :next-segment-id 1})

;; --- Node/Segment creation helpers ---

(defn- create-node
  "Creates a node at pos in the network. Returns [updated-network node-id]."
  [network pos]
  (let [id (:next-node-id network)
        node {:id id :pos pos :segment-ids #{}}]
    [(-> network
         (assoc-in [:nodes id] node)
         (assoc-in [:pos->node pos] id)
         (update :next-node-id inc))
     id]))

(defn- get-or-create-node
  "Returns [updated-network node-id] for existing or new node at pos."
  [network pos]
  (if-let [existing-id (get-in network [:pos->node pos])]
    [network existing-id]
    (create-node network pos)))

(defn- link-segment-to-node
  "Adds segment-id to node's segment-ids set."
  [network node-id segment-id]
  (update-in network [:nodes node-id :segment-ids] conj segment-id))

(defn- index-interior-cells
  "Indexes interior cells (not endpoints) in pos->seg."
  [network cells segment-id]
  (let [interior (subvec cells 1 (dec (count cells)))]
    (reduce (fn [net pos] (assoc-in net [:pos->seg pos] segment-id))
            network interior)))

(defn- unindex-interior-cells
  "Removes interior cells from pos->seg."
  [network cells]
  (let [interior (subvec cells 1 (dec (count cells)))]
    (reduce (fn [net pos] (update net :pos->seg dissoc pos))
            network interior)))

(defn- register-segment
  "Registers a new segment between two nodes. Returns updated network."
  [network node-a-id node-b-id direction cells]
  (let [seg-id (:next-segment-id network)
        segment {:id seg-id
                 :node-a-id node-a-id
                 :node-b-id node-b-id
                 :direction direction
                 :cells cells
                 :length (dec (count cells))}]
    (-> network
        (assoc-in [:segments seg-id] segment)
        (link-segment-to-node node-a-id seg-id)
        (link-segment-to-node node-b-id seg-id)
        (index-interior-cells cells seg-id)
        (update :next-segment-id inc))))

(defn- duplicate-segment?
  "Returns true if a segment already exists between the two nodes with same length."
  [network node-a-id node-b-id length]
  (let [node-a (get-in network [:nodes node-a-id])
        seg-ids (:segment-ids node-a)]
    (some (fn [sid]
            (let [seg (get-in network [:segments sid])]
              (and (= length (:length seg))
                   (or (and (= node-a-id (:node-a-id seg)) (= node-b-id (:node-b-id seg)))
                       (and (= node-a-id (:node-b-id seg)) (= node-b-id (:node-a-id seg)))))))
          seg-ids)))

;; --- Intersection splitting ---

(defn- split-cells-at
  "Splits a cell vector at the given position. Returns [left right] where both
   include the split position as endpoint."
  [cells split-pos]
  (let [idx (.indexOf cells split-pos)]
    [(subvec cells 0 (inc idx))
     (subvec cells idx)]))

(defn- remove-segment
  "Removes a segment from the network, cleaning up node links and pos->seg index."
  [network segment-id]
  (let [seg (get-in network [:segments segment-id])
        node-a-id (:node-a-id seg)
        node-b-id (:node-b-id seg)]
    (-> network
        (unindex-interior-cells (:cells seg))
        (update-in [:nodes node-a-id :segment-ids] disj segment-id)
        (update-in [:nodes node-b-id :segment-ids] disj segment-id)
        (update :segments dissoc segment-id))))

(defn- split-segment-at
  "Splits an existing segment at intersection-pos. Creates a new node there,
   removes the old segment, registers two sub-segments if they meet minimum length."
  [network segment-id intersection-pos]
  (let [seg (get-in network [:segments segment-id])
        [left-cells right-cells] (split-cells-at (:cells seg) intersection-pos)
        net-removed (remove-segment network segment-id)
        [net-with-node mid-node-id] (get-or-create-node net-removed intersection-pos)
        left-len (dec (count left-cells))
        right-len (dec (count right-cells))]
    (cond-> net-with-node
      (>= left-len config/sea-lane-min-segment-length)
      (register-segment (:node-a-id seg) mid-node-id (:direction seg) left-cells)

      (>= right-len config/sea-lane-min-segment-length)
      (register-segment mid-node-id (:node-b-id seg) (:direction seg) right-cells))))

;; --- Path decomposition ---

(defn decompose-path
  "Breaks an A* path into straight-line candidate segments.
   Returns seq of {:start-pos :end-pos :direction :cells}."
  [path]
  (when (>= (count path) 2)
    (loop [i 1
           seg-start 0
           prev-dir (let [[r0 c0] (path 0) [r1 c1] (path 1)]
                      [(- r1 r0) (- c1 c0)])
           result []]
      (if (>= i (count path))
        ;; End of path - emit final segment
        (let [cells (subvec path seg-start)]
          (if (>= (dec (count cells)) config/sea-lane-min-segment-length)
            (conj result {:start-pos (path seg-start)
                          :end-pos (path (dec (count path)))
                          :direction prev-dir
                          :cells cells})
            result))
        ;; Check direction
        (let [[ri ci] (path (dec i))
              [rn cn] (path i)
              cur-dir [(- rn ri) (- cn ci)]]
          (if (= cur-dir prev-dir)
            (recur (inc i) seg-start prev-dir result)
            ;; Direction changed - emit segment if long enough
            (let [cells (subvec path seg-start i)
                  new-result (if (>= (dec (count cells)) config/sea-lane-min-segment-length)
                               (conj result {:start-pos (path seg-start)
                                             :end-pos (path (dec i))
                                             :direction prev-dir
                                             :cells cells})
                               result)]
              (recur (inc i) (dec i) cur-dir new-result))))))))

;; --- Path recording ---

(defn- network-at-capacity?
  "Returns true if the network has reached its size limits."
  [network]
  (or (>= (count (:nodes network)) config/max-sea-lane-nodes)
      (>= (count (:segments network)) config/max-sea-lane-segments)))

(defn- record-candidate-segment
  "Records a single candidate segment into the network, handling intersections."
  [network candidate]
  (if (network-at-capacity? network)
    network
    (let [{:keys [start-pos end-pos direction cells]} candidate
          seg-length (dec (count cells))
          ;; Check for exact duplicate before doing intersection work
          existing-a (get-in network [:pos->node start-pos])
          existing-b (get-in network [:pos->node end-pos])]
      (if (and existing-a existing-b
               (duplicate-segment? network existing-a existing-b seg-length))
        network
        (let [;; Check for intersections with existing segments
              interior (subvec cells 1 (dec (count cells)))
              intersections (keep (fn [pos]
                                    (when-let [seg-id (get-in network [:pos->seg pos])]
                                      [pos seg-id]))
                                  interior)
              ;; Split any intersected segments first
              net-after-splits (reduce (fn [net [pos seg-id]]
                                        (if (get-in net [:segments seg-id])
                                          (split-segment-at net seg-id pos)
                                          net))
                                      network intersections)
              [net-a node-a-id] (get-or-create-node net-after-splits start-pos)
              [net-b node-b-id] (get-or-create-node net-a end-pos)]
          (register-segment net-b node-a-id node-b-id direction cells))))))

(defn record-path!
  "Decomposes an A* path into segments and records them into the sea lane network."
  [path]
  (when (>= (count path) 3)
    (let [candidates (decompose-path path)]
      (when (seq candidates)
        (swap! atoms/sea-lane-network
               (fn [network]
                 (reduce record-candidate-segment network candidates)))))))

;; --- Dijkstra ---

(defn dijkstra
  "Finds shortest path through the sea lane graph from entry-node-id to exit-node-id.
   Returns ordered seq of segment-ids, or nil if no path found."
  [network entry-node-id exit-node-id]
  (if (= entry-node-id exit-node-id)
    []
    (loop [queue (sorted-set [0 0 entry-node-id])
           visited #{}
           best-dist {entry-node-id 0}
           came-from {}
           counter 1]
      (if (empty? queue)
        nil
        (let [[dist _cnt current-id] (first queue)
              queue (disj queue (first queue))]
          (cond
            (= current-id exit-node-id)
            ;; Reconstruct segment path
            (loop [nid exit-node-id
                   path '()]
              (if-let [[prev-nid seg-id] (came-from nid)]
                (recur prev-nid (cons seg-id path))
                (vec path)))

            (visited current-id)
            (recur queue visited best-dist came-from counter)

            :else
            (let [node (get-in network [:nodes current-id])
                  new-visited (conj visited current-id)
                  seg-ids (:segment-ids node)
                  {:keys [new-queue new-best new-came new-counter]}
                  (reduce
                    (fn [{:keys [new-queue new-best new-came new-counter]} seg-id]
                      (let [seg (get-in network [:segments seg-id])
                            neighbor-id (if (= current-id (:node-a-id seg))
                                          (:node-b-id seg)
                                          (:node-a-id seg))
                            new-dist (+ dist (:length seg))
                            existing (get new-best neighbor-id Long/MAX_VALUE)]
                        (if (< new-dist existing)
                          {:new-queue (conj new-queue [new-dist new-counter neighbor-id])
                           :new-best (assoc new-best neighbor-id new-dist)
                           :new-came (assoc new-came neighbor-id [current-id seg-id])
                           :new-counter (inc new-counter)}
                          {:new-queue new-queue
                           :new-best new-best
                           :new-came new-came
                           :new-counter new-counter})))
                    {:new-queue queue :new-best best-dist :new-came came-from :new-counter counter}
                    seg-ids)]
              (recur new-queue new-visited new-best new-came new-counter))))))))

;; --- Routing ---

(defn find-nearest-node
  "Finds the nearest network node within radius of pos.
   Returns node-id or nil."
  [network pos radius]
  (let [[pr pc] pos
        candidates (for [[node-pos node-id] (:pos->node network)
                         :let [[nr nc] node-pos
                               dist (max (Math/abs (- nr pr)) (Math/abs (- nc pc)))]
                         :when (<= dist radius)]
                     [dist node-id])]
    (when (seq candidates)
      (second (apply min-key first candidates)))))

(defn- assemble-segment-cells
  "Returns the cells for a segment traversed from from-node-id to to-node-id."
  [segment from-node-id]
  (if (= from-node-id (:node-a-id segment))
    (:cells segment)
    (vec (reverse (:cells segment)))))

(defn assemble-path
  "Builds a cell-by-cell path from a Dijkstra segment sequence.
   Returns vector of positions."
  [network segment-ids entry-node-id]
  (if (empty? segment-ids)
    (let [node (get-in network [:nodes entry-node-id])]
      [(:pos node)])
    (loop [remaining segment-ids
           current-node-id entry-node-id
           path []]
      (if (empty? remaining)
        path
        (let [seg-id (first remaining)
              seg (get-in network [:segments seg-id])
              cells (assemble-segment-cells seg current-node-id)
              ;; Skip first cell if it overlaps with previous path end
              cells-to-add (if (and (seq path) (= (last path) (first cells)))
                             (subvec cells 1)
                             cells)
              next-node-id (if (= current-node-id (:node-a-id seg))
                             (:node-b-id seg)
                             (:node-a-id seg))]
          (recur (rest remaining)
                 next-node-id
                 (into path cells-to-add)))))))

(defn- find-network-node
  [network pos]
  (or (find-nearest-node network pos config/sea-lane-local-radius)
      (find-nearest-node network pos config/sea-lane-extended-radius)))

(defn- find-route-segments
  [network start goal]
  (when-let [entry-id (find-network-node network start)]
    (when-let [exit-id (find-network-node network goal)]
      (when-let [seg-path (dijkstra network entry-id exit-id)]
        [entry-id exit-id seg-path]))))

(defn- compute-leg
  [from to unit-type game-map bounded-a-star-fn]
  (if (= from to) [from] (bounded-a-star-fn from to unit-type game-map)))

(defn- trim-overlap
  [left right]
  (if (and (seq left) (seq right) (= (last left) (first right)))
    (subvec (vec right) 1)
    right))

(defn- join-legs
  [first-leg middle last-leg]
  (let [combined (into (vec first-leg) (trim-overlap first-leg middle))]
    (into combined (trim-overlap combined last-leg))))

(defn route-through-network
  "Attempts to route from start to goal using the sea lane network.
   Returns a path vector or nil if network routing fails."
  [network start goal unit-type game-map bounded-a-star-fn]
  (when (>= (count (:nodes network)) config/sea-lane-min-network-nodes)
    (when-let [[entry-id exit-id seg-path] (find-route-segments network start goal)]
      (let [entry-pos (:pos (get-in network [:nodes entry-id]))
            exit-pos (:pos (get-in network [:nodes exit-id]))
            first-leg (compute-leg start entry-pos unit-type game-map bounded-a-star-fn)
            last-leg (compute-leg exit-pos goal unit-type game-map bounded-a-star-fn)]
        (when (and first-leg last-leg)
          (join-legs first-leg (assemble-path network seg-path entry-id) last-leg))))))
