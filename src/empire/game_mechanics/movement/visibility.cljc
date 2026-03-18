(ns empire.game-mechanics.movement.visibility
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.containers.visibility-port :as containers-visibility-port]
            [empire.game-mechanics.services.combat-visibility-port :as visibility-port]
            [empire.config.units.dispatcher :as dispatcher]))

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

(defn- should-track-free-city?
  "Returns true if owner is computer and unit is not an army."
  [owner unit-type]
  (and (= owner :computer)
       (not= :army unit-type)))

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
  "Reveals a single cell and tracks newly-discovered free cities."
  [visible-map-source ni nj stamp-id track-cities? detect-threats? visible-map]
  (let [game-cell (get-in (current-world) [ni nj])]
    (reveal-cell! visible-map-source ni nj game-cell stamp-id visible-map)
    (when (and detect-threats?
               (was-unexplored? visible-map ni nj))
      (queue-detection! [ni nj] game-cell))
    (when (and track-cities?
               (newly-discovered-free-city? visible-map ni nj game-cell))
      (let [targets (or (read-runtime-state :land-ho-targets) [])]
        (write-runtime-state! :land-ho-targets (conj targets [ni nj]))))))

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
         track-cities? (should-track-free-city? owner (:type (:contents cell)))
         detect-threats? (= owner :computer)]
     (when-let [visible-map (read-runtime-state visible-map-key)]
       (let [[x y] pos
             height (count game-map)
             width (count (first game-map))]
         (doseq [di (range (- radius) (inc radius))
                 dj (range (- radius) (inc radius))
                 :let [ni (+ x di) nj (+ y dj)]
                 :when (in-bounds? ni nj height width)]
           (reveal-and-track! visible-map-key ni nj
                              stamp-id track-cities? detect-threats? visible-map))
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:49.900696-05:00", :module-hash "-1694055601", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1976398220"} {:id "defn-/update-game-map!", :kind "defn-", :line 7, :end-line 9, :hash "1805137569"} {:id "defn-/current-world", :kind "defn-", :line 11, :end-line 13, :hash "-640438772"} {:id "defn-/read-runtime-state", :kind "defn-", :line 15, :end-line 17, :hash "2315423"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 19, :end-line 21, :hash "1105581680"} {:id "defn-/merge-continents!", :kind "defn-", :line 23, :end-line 25, :hash "664159696"} {:id "defn-/read-visible-map", :kind "defn-", :line 27, :end-line 31, :hash "-487726468"} {:id "defn-/write-visible-map!", :kind "defn-", :line 33, :end-line 37, :hash "1884574587"} {:id "defn-/update-visible-map!", :kind "defn-", :line 39, :end-line 41, :hash "121566456"} {:id "defn-/is-players?", :kind "defn-", :line 43, :end-line 47, :hash "-596807574"} {:id "defn-/is-computers?", :kind "defn-", :line 49, :end-line 53, :hash "-1321378004"} {:id "defn-/reveal-surrounding-cells!", :kind "defn-", :line 55, :end-line 66, :hash "-1675979798"} {:id "defn-/cell-visibility-radius", :kind "defn-", :line 68, :end-line 73, :hash "-1008136283"} {:id "defn-/process-map-cells", :kind "defn-", :line 75, :end-line 89, :hash "1587628407"} {:id "defn/update-combatant-map", :kind "defn", :line 91, :end-line 102, :hash "-2099142623"} {:id "defn/update-combatant-map-state", :kind "defn", :line 104, :end-line 114, :hash "297751115"} {:id "defn-/in-bounds?", :kind "defn-", :line 116, :end-line 120, :hash "-1051206257"} {:id "defn-/should-stamp-country?", :kind "defn-", :line 122, :end-line 128, :hash "1012699648"} {:id "defn-/was-unexplored?", :kind "defn-", :line 130, :end-line 135, :hash "-740862970"} {:id "defn-/reveal-cell!", :kind "defn-", :line 137, :end-line 148, :hash "346507348"} {:id "defn-/should-track-free-city?", :kind "defn-", :line 150, :end-line 154, :hash "-275811907"} {:id "defn-/newly-discovered-free-city?", :kind "defn-", :line 156, :end-line 162, :hash "-609222107"} {:id "defn-/visible-map-key-for", :kind "defn-", :line 164, :end-line 167, :hash "1852393816"} {:id "def/detection-queue", :kind "def", :line 169, :end-line 169, :hash "303696996"} {:id "defn/drain-detections!", :kind "defn", :line 171, :end-line 176, :hash "1195271324"} {:id "defn-/queue-detection!", :kind "defn-", :line 178, :end-line 180, :hash "-319377426"} {:id "defn-/reveal-and-track!", :kind "defn-", :line 182, :end-line 193, :hash "1371855602"} {:id "defn/update-cell-visibility", :kind "defn", :line 195, :end-line 218, :hash "2013525316"} {:id "form/28/defrecord", :kind "defrecord", :line 220, :end-line 223, :hash "555209419"} {:id "form/29/defrecord", :kind "defrecord", :line 225, :end-line 228, :hash "321098609"} {:id "form/30/visibility-port/set-combat-visibility-port!", :kind "visibility-port/set-combat-visibility-port!", :line 230, :end-line 230, :hash "-1521686240"} {:id "form/31/containers-visibility-port/set-container-visibility-port!", :kind "containers-visibility-port/set-container-visibility-port!", :line 231, :end-line 231, :hash "-464490745"}]}
;; clj-mutate-manifest-end
