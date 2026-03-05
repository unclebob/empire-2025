;; mutation-tested: 2026-03-02
(ns empire.computer.transport-mission-handlers
  (:require [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.transport-loading :as loading]
            [empire.computer.transport-unloading :as unloading]
            [empire.computer.threat-response :as threat-response]
            [empire.application.ports.movement-execution :as movement-port]
            [empire.application.ports.pathfinding :as path-ports]))

(def ^:private invasion-army-search-max-distance 6)
(def ^:private invasion-load-timeout-rounds 5)

(defn load-for-invasion-start!
  [update-game-map! read-runtime-state pos]
  (update-game-map! update-in (conj pos :contents)
                    #(assoc % :transport-mission :load-for-invasion
                              :invasion-load-since (or (read-runtime-state :round-number) 0))))

(defn- loadable-army-neighbor?
  [world get-neighbors transport-pos]
  (some (fn [n]
          (let [unit (get-in world (conj n :contents))]
            (and unit
                 (= :computer (:owner unit))
                 (= :army (:type unit)))))
        (get-neighbors transport-pos)))

(defn passable-sea-cell?
  [cell]
  (and (= :sea (:type cell))
       (or (nil? (:contents cell))
           (= :computer (:owner (:contents cell))))))

(defn sea-load-points
  [world get-neighbors]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [cell (get-in world [i j])]
        :when (and cell
                   (passable-sea-cell? cell)
                   (loadable-army-neighbor? world get-neighbors [i j]))]
    [i j]))

(defn- coastal-army?
  [get-neighbors pos computer-map]
  (some (fn [n]
          (= :sea (:type (get-in computer-map n))))
        (get-neighbors pos)))

(defn- candidate-coastal-armies
  [world computer-map get-neighbors transport-pos]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [unit (get-in world [i j :contents])
              army-pos [i j]]
        :when (and unit
                   (= :army (:type unit))
                   (= :computer (:owner unit))
                   (<= (core/chebyshev-distance transport-pos army-pos)
                       invasion-army-search-max-distance)
                   (coastal-army? get-neighbors army-pos computer-map))]
    army-pos))

(defn- nearest-reachable-coastal-army
  [world read-runtime-state get-neighbors movement-services transport-pos]
  (let [computer-map (read-runtime-state :computer-map)
        candidates (candidate-coastal-armies world computer-map get-neighbors transport-pos)
        scored (keep (fn [army-pos]
                       (when-let [path (path-ports/movement-bfs-to-land-ho-target
                                        movement-services transport-pos army-pos computer-map)]
                         {:army-pos army-pos
                          :path path
                          :score [(count path)
                                  (core/chebyshev-distance transport-pos army-pos)
                                  army-pos]}))
                     candidates)]
    (first (sort-by :score scored))))

(defn- move-to-sea-step
  [move-unit-to movement-services load-adjacent-armies from step]
  (when (and step (move-unit-to from step))
    (movement-port/movement-update-cell-visibility movement-services from :computer)
    (movement-port/movement-update-cell-visibility movement-services step :computer)
    (load-adjacent-armies step)
    step))

(defn process-find-armies-for-invasion
  [{:keys [current-world
           read-runtime-state
           update-game-map!
           get-neighbors
           movement-services
           load-adjacent-armies
           coastal-crawl-move
           move-unit-to]} pos]
  (load-adjacent-armies pos)
  (let [world (current-world)
        transport (get-in world (conj pos :contents))
        army-count (:army-count transport 0)]
    (cond
      (pos? army-count)
      (load-for-invasion-start! update-game-map! read-runtime-state pos)

      (loadable-army-neighbor? world get-neighbors pos)
      (load-for-invasion-start! update-game-map! read-runtime-state pos)

      :else
      (if-let [{:keys [path]} (nearest-reachable-coastal-army world read-runtime-state get-neighbors movement-services pos)]
        (if (seq path)
          (or (move-to-sea-step move-unit-to movement-services load-adjacent-armies pos (first path))
              (coastal-crawl-move pos))
          (load-for-invasion-start! update-game-map! read-runtime-state pos))
        (update-game-map! update-in (conj pos :contents)
                          #(assoc %
                                  :transport-mission :loading
                                  :major-invasion-skip-revision
                                  (threat-response/major-invasion-target-revision)))))))

(defn process-load-for-invasion-with-armies
  [{:keys [transition-to-sailing
           transition-to-unloading
           has-nearby-unloadable-land?]}
   pos transport major-target in-unload-zone? timed-out?]
  (cond
    in-unload-zone?
    (transition-to-unloading pos major-target)

    timed-out?
    (transition-to-sailing pos)

    (has-nearby-unloadable-land? pos transport 5)
    (transition-to-sailing pos)

    :else nil))

(defn process-load-for-invasion-empty
  [update-game-map! transition-to-loading pos timed-out?]
  (when timed-out?
    (transition-to-loading pos)
    (update-game-map! update-in (conj pos :contents)
                      dissoc :invasion-load-since)))

(defn process-load-for-invasion
  [{:keys [current-world
           read-runtime-state
           load-adjacent-armies]
    :as deps}
   update-game-map!
   transition-to-loading
   pos]
  (load-adjacent-armies pos)
  (let [transport (get-in (current-world) (conj pos :contents))
        army-count (:army-count transport 0)
        major-target (:major-invasion-target transport)
        in-unload-zone? (and major-target
                             (<= (core/chebyshev-distance pos major-target) 2))
        now (or (read-runtime-state :round-number) 0)
        started (or (:invasion-load-since transport) now)
        elapsed (- now started)
        timed-out? (>= elapsed invasion-load-timeout-rounds)
        has-armies? (pos? army-count)]
    (if has-armies?
      (process-load-for-invasion-with-armies deps pos transport major-target in-unload-zone? timed-out?)
      (process-load-for-invasion-empty update-game-map! transition-to-loading pos timed-out?))))

(defn process-loading-mission
  [{:keys [current-world
           read-runtime-state
           update-game-map!
           load-adjacent-armies
           clear-pickup-continent-if-arrived
           should-start-sailing?
           loading-stale?
           start-sailing
           handle-stale-loading
           loading-crawl-move]}
   pos]
  (load-adjacent-armies pos)
  (clear-pickup-continent-if-arrived pos)
  (let [transport' (get-in (current-world) (conj pos :contents))
        army-count' (:army-count transport' 0)]
    (cond
      (should-start-sailing? pos transport' army-count')
      (start-sailing pos transport')

      (loading-stale? transport')
      (handle-stale-loading pos transport' army-count')

      :else
      (loading-crawl-move pos))))

(defn- take-second-unloading-step
  [current-world process-unloading-crawl try-opportunistic-unload pos1 after-first]
  (let [unit1 (get-in (current-world) (conj pos1 :contents))
        can-second? (and unit1
                         (= :unloading (:transport-mission unit1))
                         (not after-first))]
    (if can-second?
      (if-let [pos2 (process-unloading-crawl pos1)]
        (do
          (when (= :unloading
                   (:transport-mission (get-in (current-world) (conj pos2 :contents))))
            (try-opportunistic-unload pos2))
          pos2)
        pos1)
      pos1)))

(defn- process-unloading-with-armies
  [{:keys [current-world
           has-nearby-unloadable-land?
           process-unloading-crawl
           try-opportunistic-unload
           start-sailing]} pos transport]
  (if (has-nearby-unloadable-land? pos transport 5)
    (if-let [pos1 (process-unloading-crawl pos)]
      (let [after-first (or (when (= :unloading
                                   (:transport-mission (get-in (current-world) (conj pos1 :contents))))
                              (try-opportunistic-unload pos1))
                            false)]
        (take-second-unloading-step current-world process-unloading-crawl try-opportunistic-unload pos1 after-first))
      (start-sailing pos transport))
    (start-sailing pos transport)))

(defn process-unloading-mission
  [{:keys [current-world
           transition-to-loading]
    :as deps}
  pos army-count]
  (if (zero? army-count)
    (transition-to-loading pos)
    (let [transport (get-in (current-world) (conj pos :contents))]
      (process-unloading-with-armies deps pos transport))))

(defn park-lake-transport-if-empty
  [{:keys [current-world
           update-game-map!
           move-unit-to
           retreat-step-from-shore
           deep-water?]}
   pos lake-cells-set]
  (let [unit (get-in (current-world) (conj pos :contents))]
    (if (zero? (:army-count unit 0))
      (if-let [step (retreat-step-from-shore (current-world) lake-cells-set pos)]
        (if (move-unit-to pos step)
          (when (deep-water? (current-world) step)
            (update-game-map! update-in (conj step :contents)
                              #(assoc % :mode :sentry
                                      :transport-mission :land-locked)))
          (update-game-map! update-in (conj pos :contents)
                            #(assoc % :mode :sentry
                                    :transport-mission :land-locked)))
        (update-game-map! update-in (conj pos :contents)
                          #(assoc % :mode :sentry
                                  :transport-mission :land-locked)))
      false)))

(defn process-land-locked-mission
  [{:keys [current-world
           process-unloading-crawl
           try-opportunistic-unload-any-land]
    :as deps}
   pos lake-cells-set]
  (let [unloaded-now? (boolean (try-opportunistic-unload-any-land pos))]
    (or (park-lake-transport-if-empty deps pos lake-cells-set)
        (let [unit (get-in (current-world) (conj pos :contents))
              army-count (:army-count unit 0)]
          (when (pos? army-count)
            (if-let [next-pos (process-unloading-crawl pos)]
              (do
                (try-opportunistic-unload-any-land next-pos)
                (park-lake-transport-if-empty deps next-pos lake-cells-set))
              unloaded-now?))))))

(defn fix-idle-mission
  [set-transport-mission pos mission]
  (when (or (nil? mission) (= :idle mission))
    (set-transport-mission pos :loading)))

(defn maybe-handle-lake-transport
  [{:keys [current-world
           read-runtime-state
           update-game-map!
           set-transport-mission
           lake-cells]
    :as deps}
   pos transport]
  (if (= :sentry (:mode transport))
    true
    (when (:lake-locked? transport)
      (let [lake-cells-set (lake-cells (read-runtime-state :computer-map)
                                       (read-runtime-state :lake-max-cells))]
        (update-game-map! assoc-in (conj pos :contents :never-reload?) true)
        (let [unit (get-in (current-world) (conj pos :contents))
              army-count (:army-count unit 0)]
          (if (pos? army-count)
            (do
              (set-transport-mission pos :land-locked)
              (process-land-locked-mission deps pos lake-cells-set))
            (park-lake-transport-if-empty deps pos lake-cells-set)))
        true))))
