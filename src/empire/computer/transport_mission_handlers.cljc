(ns empire.computer.transport-mission-handlers
  (:require [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.transport-decisions :as decisions]
            [empire.computer.transport-loading :as loading]
            [empire.computer.transport-unloading :as unloading]
            [empire.computer.threat-response :as threat-response]))

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
  [world read-runtime-state get-neighbors bfs-to-land-ho-target transport-pos]
  (let [computer-map (read-runtime-state :computer-map)
        candidates (candidate-coastal-armies world computer-map get-neighbors transport-pos)
        scored (keep (fn [army-pos]
                       (when-let [path (bfs-to-land-ho-target
                                        transport-pos army-pos computer-map)]
                         {:army-pos army-pos
                          :path path
                          :score [(count path)
                                  (core/chebyshev-distance transport-pos army-pos)
                                  army-pos]}))
                     candidates)]
    (first (sort-by :score scored))))

(defn- move-to-sea-step
  [move-unit-to update-cell-visibility! load-adjacent-armies from step]
  (when (and step (move-unit-to from step))
    (update-cell-visibility! from :computer)
    (update-cell-visibility! step :computer)
    (load-adjacent-armies step)
    step))

(defn process-find-armies-for-invasion
  [{:keys [current-world
           read-runtime-state
           update-game-map!
           get-neighbors
           update-cell-visibility!
           bfs-to-land-ho-target
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
      (if-let [{:keys [path]} (nearest-reachable-coastal-army world read-runtime-state get-neighbors bfs-to-land-ho-target pos)]
        (if (seq path)
          (or (move-to-sea-step move-unit-to update-cell-visibility! load-adjacent-armies pos (first path))
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
        has-armies? (pos? army-count)
        action (decisions/load-for-invasion-action
                {:has-armies? has-armies?
                 :in-unload-zone? in-unload-zone?
                 :timed-out? timed-out?
                 :nearby-unloadable-land? (and has-armies? ((:has-nearby-unloadable-land? deps) pos transport 5))})]
    (case action
      :unload ((:transition-to-unloading deps) pos major-target)
      :sail ((:transition-to-sailing deps) pos)
      :revert-loading (process-load-for-invasion-empty update-game-map! transition-to-loading pos timed-out?)
      (when has-armies?
        nil))))

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
    (case (decisions/loading-mission-action
           {:should-start-sailing? (should-start-sailing? pos transport' army-count')
            :loading-stale? (loading-stale? transport')})
      :start-sailing (start-sailing pos transport')
      :handle-stale (handle-stale-loading pos transport' army-count')
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
  (case (decisions/unloading-mission-action {:army-count army-count})
    :transition-to-loading (transition-to-loading pos)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T15:54:36.734152-05:00", :module-hash "-42292458", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "980126051"} {:id "def/invasion-army-search-max-distance", :kind "def", :line 9, :end-line 9, :hash "-644390678"} {:id "def/invasion-load-timeout-rounds", :kind "def", :line 10, :end-line 10, :hash "1973653881"} {:id "defn/load-for-invasion-start!", :kind "defn", :line 12, :end-line 16, :hash "1657335532"} {:id "defn-/loadable-army-neighbor?", :kind "defn-", :line 18, :end-line 25, :hash "-1760752950"} {:id "defn/passable-sea-cell?", :kind "defn", :line 27, :end-line 31, :hash "-618562919"} {:id "defn/sea-load-points", :kind "defn", :line 33, :end-line 41, :hash "1374456034"} {:id "defn-/coastal-army?", :kind "defn-", :line 43, :end-line 47, :hash "-1829698076"} {:id "defn-/candidate-coastal-armies", :kind "defn-", :line 49, :end-line 61, :hash "2124573952"} {:id "defn-/nearest-reachable-coastal-army", :kind "defn-", :line 63, :end-line 76, :hash "358071700"} {:id "defn-/move-to-sea-step", :kind "defn-", :line 78, :end-line 84, :hash "-49456250"} {:id "defn/process-find-armies-for-invasion", :kind "defn", :line 86, :end-line 117, :hash "778029330"} {:id "defn/process-load-for-invasion-with-armies", :kind "defn", :line 119, :end-line 134, :hash "-709718108"} {:id "defn/process-load-for-invasion-empty", :kind "defn", :line 136, :end-line 141, :hash "1323918587"} {:id "defn/process-load-for-invasion", :kind "defn", :line 143, :end-line 172, :hash "-1036328156"} {:id "defn/process-loading-mission", :kind "defn", :line 174, :end-line 195, :hash "1991527787"} {:id "defn-/take-second-unloading-step", :kind "defn-", :line 197, :end-line 211, :hash "-1531560164"} {:id "defn-/process-unloading-with-armies", :kind "defn-", :line 213, :end-line 227, :hash "842924283"} {:id "defn/process-unloading-mission", :kind "defn", :line 229, :end-line 237, :hash "1747391872"} {:id "defn/park-lake-transport-if-empty", :kind "defn", :line 239, :end-line 260, :hash "8083485"} {:id "defn/process-land-locked-mission", :kind "defn", :line 262, :end-line 277, :hash "-1454317422"} {:id "defn/fix-idle-mission", :kind "defn", :line 279, :end-line 282, :hash "-346291120"} {:id "defn/maybe-handle-lake-transport", :kind "defn", :line 284, :end-line 305, :hash "-664804461"}]}
;; clj-mutate-manifest-end
