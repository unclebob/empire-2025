(ns empire.computer.transport
  "Computer transport module - VMS Empire style transport movement.
   Loading state: move toward armies
   Unloading state: move toward enemy cities on other continents"
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.continent :as continent]
            [empire.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]
            [empire.movement.map-utils :as map-utils]))

(defn- get-passable-sea-neighbors
  "Returns passable sea neighbors for a transport."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :computer (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn- find-armies-to-load
  "Find computer armies that should board transports."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :army (:type unit)))]
      [i j])))

(defn- find-nearest-army
  "Find the nearest army to the transport."
  [transport-pos]
  (let [armies (find-armies-to-load)]
    (when (seq armies)
      (apply min-key
             (fn [[r c]]
               (let [[tr tc] transport-pos]
                 (+ (Math/abs (- r tr)) (Math/abs (- c tc)))))
             armies))))

(defn- adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (map-utils/adjacent-to-land? pos atoms/game-map))

(defn- find-adjacent-land-pos
  "Returns the first adjacent land or city position, or nil."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [n]
                     (let [cell (get-in game-map n)]
                       (and cell (#{:land :city} (:type cell)))))
                   (core/get-neighbors pos)))))

(defn- score-target-city
  "Score a target city for a transport. Lower = more attractive.
   Factors: distance, continent attackable cities, computer presence."
  [transport-pos target-city]
  (let [dist (core/distance transport-pos target-city)
        target-continent (continent/flood-fill-continent target-city)
        scan (when target-continent (continent/scan-continent target-continent))
        attackable (+ (:player-cities scan 0) (:free-cities scan 0))
        continent-factor (if (pos? attackable)
                           (/ 100.0 attackable)
                           100.0)
        presence-penalty (if (pos? (:computer-cities scan 0)) 10.0 1.0)]
    (* dist continent-factor presence-penalty)))

(defn find-unload-target
  "Find best enemy city to unload near, excluding origin continent.
   Prefers unclaimed targets to spread transports."
  [origin-continent transport-pos]
  (let [player-cities (core/find-visible-cities #{:player})
        free-cities (core/find-visible-cities #{:free})
        all-targets (concat player-cities free-cities)
        off-continent (if origin-continent
                        (remove #(contains? origin-continent %) all-targets)
                        all-targets)]
    (when (seq off-continent)
      (let [claimed @atoms/claimed-transport-targets
            unclaimed (remove claimed off-continent)
            candidates (if (seq unclaimed) unclaimed off-continent)
            best (apply min-key
                        #(score-target-city transport-pos %)
                        candidates)]
        (when best
          (swap! atoms/claimed-transport-targets conj best)
          best)))))

(defn- find-unload-position
  "Find a sea cell adjacent to land near the target city.
   Excludes positions whose adjacent land is on the origin continent."
  [target-city origin-continent]
  (let [game-map @atoms/game-map
        [tr tc] target-city]
    ;; Find sea cells within range that are adjacent to land
    (first
     (for [dr (range -3 4)
           dc (range -3 4)
           :let [pos [(+ tr dr) (+ tc dc)]
                 cell (get-in game-map pos)]
           :when (and cell
                      (= :sea (:type cell))
                      (adjacent-to-land? pos)
                      (nil? (:contents cell))
                      (or (nil? origin-continent)
                          (let [adj-land (find-adjacent-land-pos pos)]
                            (not (contains? origin-continent adj-land)))))]
       pos))))

(defn- move-toward-position
  "Move transport one step toward target. Returns new position."
  [pos target]
  (if-let [next-step (pathfinding/next-step pos target :transport)]
    (do
      (core/move-unit-to pos next-step)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-step :computer)
      next-step)
    ;; No path - try direct movement
    (let [passable (get-passable-sea-neighbors pos)
          closest (core/move-toward pos target passable)]
      (when closest
        (core/move-unit-to pos closest)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility closest :computer)
        closest))))

(defn- explore-sea
  "Move transport toward unexplored sea. Fallback when no cross-continent target exists."
  [pos]
  (let [passable (get-passable-sea-neighbors pos)
        frontier (filter core/adjacent-to-computer-unexplored? passable)]
    (when-let [target (or (first frontier) (first passable))]
      (core/move-unit-to pos target)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility target :computer)
      target)))

(defn unload-armies
  "Unload armies onto adjacent land, excluding origin continent. Returns true if any unloaded."
  [pos origin-continent]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        army-count (:army-count transport 0)]
    (when (pos? army-count)
      (let [land-neighbors (filter (fn [neighbor]
                                     (let [cell (get-in @atoms/game-map neighbor)]
                                       (and cell
                                            (#{:land :city} (:type cell))
                                            (nil? (:contents cell))
                                            (or (nil? origin-continent)
                                                (not (contains? origin-continent neighbor))))))
                                   (core/get-neighbors pos))
            to-unload (min army-count (count land-neighbors))]
        (when (pos? to-unload)
          ;; Unload armies onto land cells
          (doseq [land-pos (take to-unload land-neighbors)]
            (swap! atoms/game-map assoc-in (conj land-pos :contents)
                   {:type :army :owner :computer :mode :awake :hits 1})
            (visibility/update-cell-visibility land-pos :computer))
          ;; Update transport army count
          (swap! atoms/game-map update-in (conj pos :contents :army-count) - to-unload)
          ;; If fully unloaded, change mission to loading and clear origin
          (when (<= (- army-count to-unload) 0)
            (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) :loading)
            (swap! atoms/game-map update-in (conj pos :contents) dissoc :origin-continent-pos))
          true)))))

(defn- set-transport-mission
  "Set the transport's mission state."
  [pos mission]
  (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) mission))

(defn- record-origin-continent-pos
  "When transport becomes full, record the nearest adjacent land position
   as the origin continent reference point."
  [pos transport]
  (when-not (:origin-continent-pos transport)
    (when-let [land-pos (find-adjacent-land-pos pos)]
      (swap! atoms/game-map assoc-in
             (conj pos :contents :origin-continent-pos) land-pos))))

(defn- move-toward-unload-or-explore
  "Try to move toward a cross-continent unload target. If none, explore sea."
  [pos origin-continent]
  (if-let [target (find-unload-target origin-continent pos)]
    (when-let [unload-pos (find-unload-position target origin-continent)]
      (move-toward-position pos unload-pos))
    (explore-sea pos)))

(defn process-transport
  "Processes a transport unit using VMS Empire style logic.
   Loading: move toward armies, collect them
   Unloading: move toward enemy cities on OTHER continents, drop armies
   Returns nil after processing - transports only move once per round."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        transport (:contents cell)]
    (when (and transport
               (= :computer (:owner transport))
               (= :transport (:type transport)))
      (let [army-count (:army-count transport 0)
            mission (:transport-mission transport :idle)]

        ;; Determine mission if idle
        (when (= mission :idle)
          (set-transport-mission pos :loading))

        (let [current-mission (or (:transport-mission transport) :loading)]
          (cond
            ;; Full transport - go unload on a different continent
            (>= army-count 6)
            (do
              (set-transport-mission pos :unloading)
              (record-origin-continent-pos pos transport)
              (let [updated-transport (get-in @atoms/game-map (conj pos :contents))
                    origin-continent (when-let [ocp (:origin-continent-pos updated-transport)]
                                      (continent/flood-fill-continent ocp))]
                (if (adjacent-to-land? pos)
                  (when-not (unload-armies pos origin-continent)
                    (move-toward-unload-or-explore pos origin-continent))
                  (move-toward-unload-or-explore pos origin-continent))))

            ;; Loading transport - go get armies
            (= current-mission :loading)
            (if-let [army-pos (find-nearest-army pos)]
              (move-toward-position pos army-pos)
              ;; No armies found - patrol near coast
              (let [passable (get-passable-sea-neighbors pos)
                    coastal (filter adjacent-to-land? passable)]
                (when-let [target (or (first coastal) (first passable))]
                  (core/move-unit-to pos target)
                  (visibility/update-cell-visibility pos :computer)
                  (visibility/update-cell-visibility target :computer))))

            ;; Unloading transport - continue to target on different continent
            (= current-mission :unloading)
            (let [origin-continent (when-let [ocp (:origin-continent-pos transport)]
                                     (continent/flood-fill-continent ocp))]
              (if (adjacent-to-land? pos)
                (when-not (unload-armies pos origin-continent)
                  (move-toward-unload-or-explore pos origin-continent))
                (move-toward-unload-or-explore pos origin-continent)))

            :else nil)))))
  nil)
