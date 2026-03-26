(ns empire.game-mechanics.visibility
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.containers.visibility-port :as containers-visibility-port]
            [empire.game-mechanics.combat-visibility-port :as visibility-port]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.spatial.neighbors :as neighbors]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

(defn- read-runtime-state
  [k]
  (sa/read-state k))

(defn- write-runtime-state!
  [k v]
  (sa/write-state! k v))

(defn- merge-continents!
  [stamp-id existing-cid]
  (sa/merge-continents! stamp-id existing-cid))

(defn- read-visible-map
  [visible-map-source]
  (if (keyword? visible-map-source)
    (read-runtime-state visible-map-source)
    @visible-map-source))

(defn- write-visible-map!
  [visible-map-source visible-map]
  (if (keyword? visible-map-source)
    (write-runtime-state! visible-map-source visible-map)
    (reset! visible-map-source visible-map)))

(defn- update-visible-map!
  [visible-map-source f & args]
  (write-visible-map! visible-map-source (apply f (read-visible-map visible-map-source) args)))

(defn- is-players?
  "Returns true if the cell is owned by the player."
  [cell]
  (or (= (:city-status cell) :player)
      (= (:owner (:contents cell)) :player)))

(defn- is-computers?
  "Returns true if the cell is owned by the computer."
  [cell]
  (or (= (:city-status cell) :computer)
      (= (:owner (:contents cell)) :computer)))

(defn- reveal-surrounding-cells!
  "Reveals cells within radius around cell [i,j] in the transient result map.
   Clamps to map boundaries."
  [result game-map i j height width radius]
  (let [coords (for [row (range (max 0 (- i radius)) (min height (+ i radius 1)))
                     col (range (max 0 (- j radius)) (min width (+ j radius 1)))]
                 [row col])]
    (reduce (fn [r [row col]]
              (let [cell ((game-map row) col)]
                (assoc! r row (assoc! (r row) col cell))))
            result
            coords)))

(defn- cell-visibility-radius
  "Returns the visibility radius for a cell based on its contents."
  [cell]
  (if-let [unit-type (:type (:contents cell))]
    (dispatcher/visibility-radius unit-type)
    1))

(defn- process-map-cells
  "Iterates over all cells, revealing surroundings for owned cells.
   Returns the updated transient result."
  [result game-map ownership-predicate height width]
  (let [coords (for [i (range height)
                     j (range width)]
                 [i j])]
    (reduce (fn [res [i j]]
              (let [cell ((game-map i) j)]
                (if (ownership-predicate cell)
                  (reveal-surrounding-cells! res game-map i j height width
                                             (cell-visibility-radius cell))
                  res)))
            result
            coords)))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units.
   Optimized to use direct vector access instead of get-in/assoc-in."
  [visible-map-source owner]
  (when-let [visible-map (read-visible-map visible-map-source)]
    (let [game-map (current-world)
          ownership-predicate (if (= owner :player) is-players? is-computers?)
          height (count game-map)
          width (count (first game-map))
          transient-map (transient (mapv transient visible-map))
          updated (process-map-cells transient-map game-map ownership-predicate height width)]
      (write-visible-map! visible-map-source (mapv persistent! (persistent! updated))))))

(defn update-combatant-map-state
  "Pure/state-level variant of combatant map update.
   Returns an updated visible-map from the provided visible-map and game-map."
  [visible-map owner game-map]
  (when visible-map
    (let [ownership-predicate (if (= owner :player) is-players? is-computers?)
          height (count game-map)
          width (count (first game-map))
          transient-map (transient (mapv transient visible-map))
          updated (process-map-cells transient-map game-map ownership-predicate height width)]
      (mapv persistent! (persistent! updated)))))

(defn- in-bounds?
  "Returns true if [row col] is within [0,height) x [0,width)."
  [row col height width]
  (and (>= row 0) (< row height)
       (>= col 0) (< col width)))

(defn- should-stamp-country?
  "Returns truthy if unit is a computer army with a country-id."
  [unit]
  (and unit
       (= :army (:type unit))
       (= :computer (:owner unit))
       (:country-id unit)))

(defn- was-unexplored?
  "Returns true if the cell at [row col] in visible-map is nil or unexplored."
  [visible-map row col]
  (let [vis-cell (get-in visible-map [row col])]
    (or (nil? vis-cell)
        (= :unexplored (:type vis-cell)))))

(defn- reveal-cell!
  "Reveals game-cell at [row col] in visible-map-atom.
   If stamp-id is truthy and cell was unexplored land, stamps its country-id."
  [visible-map-source row col game-cell stamp-id visible-map]
  (update-visible-map! visible-map-source assoc-in [row col] game-cell)
  (when (and stamp-id
             (was-unexplored? visible-map row col)
             (= :land (:type game-cell)))
    (let [existing-cid (:country-id game-cell)]
      (when (and existing-cid (not= stamp-id existing-cid))
        (merge-continents! stamp-id existing-cid)))
    (update-game-map! assoc-in [row col :country-id] stamp-id)))

(defn- claimed-country-id
  [cell]
  (let [cell-cid (:country-id cell)
        unit (:contents cell)
        unit-cid (when (and (= :computer (:owner unit))
                            (= :army (:type unit)))
                   (:country-id unit))]
    (when (or cell-cid unit-cid)
      (min (or cell-cid Long/MAX_VALUE)
           (or unit-cid Long/MAX_VALUE)))))

(defn- unclaimed-visible-land?
  [visible-map pos]
  (let [cell (get-in visible-map pos)]
    (and (= :land (:type cell))
         (nil? (:country-id cell))
         (nil? (claimed-country-id cell)))))

(defn- connected-visible-land-component
  [visible-map start valid-positions]
  (loop [frontier [start]
         seen #{}
         component #{}]
    (if-let [pos (peek frontier)]
      (let [frontier (pop frontier)]
        (if (seen pos)
          (recur frontier seen component)
          (let [neighbors (filter valid-positions
                                  (neighbors/get-matching-neighbors pos visible-map neighbors/neighbor-offsets
                                                                   (constantly true)))]
            (recur (into frontier neighbors)
                   (conj seen pos)
                   (conj component pos)))))
      component)))

(defn- adjacent-claimed-cids
  [visible-map positions]
  (->> positions
       (mapcat #(neighbors/get-matching-neighbors % visible-map neighbors/neighbor-offsets
                                                  (comp some? claimed-country-id)))
       (map #(claimed-country-id (get-in visible-map %)))
       (remove nil?)
       set))

(defn- stamp-exposed-territory!
  [visible-map-source exposed-positions]
  (let [visible-map (read-visible-map visible-map-source)
        exposed-land (set (filter #(unclaimed-visible-land? visible-map %) exposed-positions))
        visible-unclaimed-land (set (for [row (range (count visible-map))
                                          col (range (count (first visible-map)))
                                          :let [pos [row col]]
                                          :when (unclaimed-visible-land? visible-map pos)]
                                      pos))]
    (loop [remaining exposed-land]
      (when-let [start (first remaining)]
        (let [component (connected-visible-land-component visible-map start visible-unclaimed-land)
              candidate-cids (adjacent-claimed-cids visible-map component)]
          (when-let [claim-cid (when (seq candidate-cids) (apply min candidate-cids))]
            (doseq [pos component]
              (update-game-map! assoc-in (conj pos :country-id) claim-cid)
              (update-visible-map! visible-map-source assoc-in (conj pos :country-id) claim-cid)))
          (when (> (count candidate-cids) 1)
            (let [lowest-cid (apply min candidate-cids)]
              (doseq [other-cid (disj candidate-cids lowest-cid)]
                (merge-continents! lowest-cid other-cid))))
          (recur (reduce disj remaining component)))))))

(defn- newly-discovered-free-city?
  "Returns true if game-cell is a free city and the same position
   on visible-map was unexplored."
  [visible-map row col game-cell]
  (and (= :city (:type game-cell))
       (= :free (:city-status game-cell))
       (was-unexplored? visible-map row col)))

(defn- visible-map-key-for
  "Returns runtime-state key for the given owner's visible map."
  [owner]
  (if (= owner :player) :player-map :computer-map))

(def ^:private detection-queue (atom []))

(defn drain-detections!
  "Returns and clears accumulated detection events. Each event is {:pos [r c] :cell cell-map}."
  []
  (let [events @detection-queue]
    (reset! detection-queue [])
    events))

(defn- queue-detection!
  [coords cell]
  (swap! detection-queue conj {:pos coords :cell cell}))

(defn- reveal-and-track!
  "Reveals a single cell and queues threat detections for newly visible cells."
  [visible-map-source ni nj stamp-id detect-threats? visible-map]
  (let [was-unexplored (was-unexplored? visible-map ni nj)
        game-cell (get-in (current-world) [ni nj])]
    (reveal-cell! visible-map-source ni nj game-cell stamp-id visible-map)
    (when was-unexplored
      (debug-logging/record-active-computer-unit-discovery! 1))
    (when (and detect-threats? was-unexplored)
      (queue-detection! [ni nj] game-cell))))

(defn sync-ai-unit-to-computer-map!
  [pos]
  (let [computer-map (read-runtime-state :computer-map)
        cell (get-in (current-world) pos)
        unit (:contents cell)]
    (when (and computer-map
               cell
               unit
               (= :computer (:owner unit)))
      (update-visible-map! :computer-map assoc-in pos cell))))

(defn sync-ai-cell-to-computer-map!
  [pos]
  (let [computer-map (read-runtime-state :computer-map)
        cell (get-in (current-world) pos)]
    (when (and computer-map cell)
      (update-visible-map! :computer-map assoc-in pos cell))))

(defn authoritative-computer-unit-at
  [pos]
  (let [unit (get-in (current-world) (conj pos :contents))]
    (when (= :computer (:owner unit))
      unit)))

(defn refresh-visible-map!
  [owner]
  (let [game-map (current-world)
        visible-map-key (visible-map-key-for owner)
        current-map (read-runtime-state visible-map-key)
        visible-map (if (and (vector? current-map)
                             (= (count current-map) (count game-map))
                             (= (count (first current-map))
                                (count (first game-map))))
                      current-map
                      (vec (repeat (count game-map)
                                   (vec (repeat (count (first game-map)) nil)))))]
    (when-let [updated (update-combatant-map-state visible-map owner game-map)]
      (write-runtime-state! visible-map-key updated))))

(defn update-cell-visibility
  "Updates visibility around a specific cell for the given owner.
   Satellites reveal two rectangular rings (distances 1 and 2).
   When unit is a computer army with country-id, stamps newly-revealed land cells."
  ([pos owner] (update-cell-visibility pos owner nil))
  ([pos owner unit]
   (let [visible-map-key (visible-map-key-for owner)
         game-map (current-world)
         cell (get-in game-map pos)
         radius (cell-visibility-radius cell)
         stamp-id (should-stamp-country? unit)
         detect-threats? (= owner :computer)]
     (when-let [visible-map (read-runtime-state visible-map-key)]
      (let [[x y] pos
             height (count game-map)
             width (count (first game-map))
             exposed-positions (atom [])]
         (doseq [di (range (- radius) (inc radius))
                 dj (range (- radius) (inc radius))
                 :let [ni (+ x di) nj (+ y dj)]
                 :when (in-bounds? ni nj height width)]
           (when (was-unexplored? visible-map ni nj)
             (swap! exposed-positions conj [ni nj]))
           (reveal-and-track! visible-map-key ni nj
                              stamp-id detect-threats? visible-map))
         (when (= owner :computer)
           (stamp-exposed-territory! visible-map-key @exposed-positions))
         nil)))))

(defrecord MovementCombatVisibilityPort []
  visibility-port/CombatVisibilityPort
  (update-visibility! [_ pos owner]
    (update-cell-visibility pos owner)))

(defrecord MovementContainerVisibilityPort []
  containers-visibility-port/ContainerVisibilityPort
  (update-container-visibility! [_ pos owner]
    (update-cell-visibility pos owner)))

(visibility-port/set-combat-visibility-port! (->MovementCombatVisibilityPort))
(containers-visibility-port/set-container-visibility-port! (->MovementContainerVisibilityPort))
