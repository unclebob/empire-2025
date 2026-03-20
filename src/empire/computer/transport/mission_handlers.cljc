(ns empire.computer.transport.mission-handlers
  (:require [empire.computer.shared.grid :as grid]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.transport.mission-handler-decisions :as handler-decisions]
            [empire.computer.transport.decisions :as decisions]
            [empire.computer.transport.loading :as loading]
            [empire.computer.transport.unloading :as unloading]
            [empire.computer.threat-response-impl :as threat-response]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(def ^:private invasion-army-search-max-distance 6)
(def ^:private invasion-load-timeout-rounds 5)

(defn- noop-sync!
  [_])

(defn- transport-speed
  []
  (dispatcher/speed :transport))

(defn load-for-invasion-start!
  ([update-game-map! read-runtime-state pos]
   (load-for-invasion-start! update-game-map! read-runtime-state noop-sync! pos))
  ([update-game-map! read-runtime-state sync-transport! pos]
   (update-game-map! update-in (conj pos :contents)
                     #(assoc % :transport-mission :load-for-invasion
                               :invasion-load-since (or (read-runtime-state :round-number) 0)))
   (sync-transport! pos)))

(defn- loadable-army-neighbor?
  [world get-neighbors transport-pos]
  (some (fn [n]
          (let [unit (get-in world (conj n :contents))]
            (and unit
                 (= :computer (:owner unit))
                 (= :army (:type unit)))))
        (get-neighbors transport-pos)))

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
                   (<= (grid/chebyshev-distance transport-pos army-pos)
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
                                  (grid/chebyshev-distance transport-pos army-pos)
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
           read-computer-map
           read-runtime-state
           update-game-map!
           sync-transport!
           get-neighbors
           update-cell-visibility!
           bfs-to-land-ho-target
           load-adjacent-armies
           coastal-crawl-move
           move-unit-to]} pos]
  (load-adjacent-armies pos)
  (let [read-map (or read-computer-map current-world)
        sync-transport! (or sync-transport! noop-sync!)
        world (read-map)
        transport (get-in world (conj pos :contents))
        army-count (:army-count transport 0)
        nearest-army (nearest-reachable-coastal-army world read-runtime-state get-neighbors bfs-to-land-ho-target pos)]
    (case (handler-decisions/find-armies-for-invasion-action
           {:army-count army-count
            :loadable-neighbor? (loadable-army-neighbor? world get-neighbors pos)
            :reachable-path? (boolean nearest-army)})
      :start-load-for-invasion
      (load-for-invasion-start! update-game-map! read-runtime-state sync-transport! pos)
      :follow-path
      (if-let [{:keys [path]} nearest-army]
        (if (seq path)
          (or (move-to-sea-step move-unit-to update-cell-visibility! load-adjacent-armies pos (first path))
              (coastal-crawl-move pos))
          (load-for-invasion-start! update-game-map! read-runtime-state sync-transport! pos))
        nil)
      :revert-loading
      (do
        (update-game-map! update-in (conj pos :contents)
                          #(assoc %
                                  :transport-mission :loading
                                  :major-invasion-skip-revision
                                  (threat-response/major-invasion-target-revision)))
        (sync-transport! pos)))))

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
  ([update-game-map! transition-to-loading pos timed-out?]
   (process-load-for-invasion-empty update-game-map! transition-to-loading noop-sync! pos timed-out?))
  ([update-game-map! transition-to-loading sync-transport! pos timed-out?]
   (when timed-out?
     (transition-to-loading pos)
     (update-game-map! update-in (conj pos :contents)
                       dissoc :invasion-load-since)
     (sync-transport! pos))))

(defn- load-for-invasion-context
  [deps pos transport]
  (let [army-count (:army-count transport 0)
        major-target (:major-invasion-target transport)
        in-unload-zone? (and major-target
                             (<= (grid/chebyshev-distance pos major-target) 2))
        now (or ((:read-runtime-state deps) :round-number) 0)
        started (or (:invasion-load-since transport) now)
        elapsed (- now started)
        timed-out? (>= elapsed invasion-load-timeout-rounds)]
    {:major-target major-target
     :timed-out? timed-out?
     :load-state (handler-decisions/load-for-invasion-state
                  {:army-count army-count
                   :in-unload-zone? in-unload-zone?
                   :timed-out? timed-out?
                   :nearby-unloadable-land? (and (pos? army-count)
                                                 ((:has-nearby-unloadable-land? deps) pos transport 5))})}))

(defn- apply-load-for-invasion-action
  [deps update-game-map! transition-to-loading pos
   {:keys [major-target timed-out? load-state]}]
  (case (handler-decisions/load-for-invasion-action load-state)
    :unload ((:transition-to-unloading deps) pos major-target)
    :sail ((:transition-to-sailing deps) pos)
    :revert-loading (process-load-for-invasion-empty
                     update-game-map!
                     transition-to-loading
                     (:sync-transport! deps)
                     pos
                     timed-out?)
    (when (:has-armies? load-state) nil)))

(defn process-load-for-invasion
  [{:keys [current-world
           read-computer-map
           read-runtime-state
           load-adjacent-armies]
    :as deps}
   update-game-map!
   transition-to-loading
   pos]
  (load-adjacent-armies pos)
  (let [read-map (or read-computer-map current-world)
        transport (get-in (read-map) (conj pos :contents))]
    (apply-load-for-invasion-action
     deps
     update-game-map!
     transition-to-loading
     pos
     (load-for-invasion-context deps pos transport))))

(defn process-loading-mission
  [{:keys [current-world
           read-computer-map
           load-adjacent-armies
           should-start-sailing?
           start-sailing
           loading-crawl-move
           transition-to-loading]}
   pos]
  (load-adjacent-armies pos)
  (let [read-map (or read-computer-map current-world)
        transport' (get-in (read-map) (conj pos :contents))
        army-count' (:army-count transport' 0)]
    (case (decisions/loading-mission-action
           {:should-start-sailing? (should-start-sailing? pos transport' army-count')})
      :start-sailing (start-sailing pos transport')
      (or (loading-crawl-move pos)
          (transition-to-loading pos)))))

(defn- process-unloading-with-armies
  [{:keys [current-world
           read-computer-map
           process-unloading-crawl
           try-opportunistic-unload]} pos transport]
  (let [read-map (or read-computer-map current-world)]
    (loop [current-pos pos
           moves-left (transport-speed)
           retried? false
           moved-any? false]
      (if (zero? moves-left)
        (when moved-any? current-pos)
        (if-let [next-pos (process-unloading-crawl current-pos)]
          (let [still-unloading? (= :unloading
                                    (:transport-mission (get-in (read-map) (conj next-pos :contents))))
                unloaded-now? (and still-unloading?
                                   (boolean (try-opportunistic-unload next-pos)))
                continue? (and still-unloading? (not unloaded-now?))]
            (if continue?
              (recur next-pos (dec moves-left) false true)
              next-pos))
          (if retried?
            (when moved-any? current-pos)
            (do
              (sa/update-world! assoc-in (conj current-pos :contents :crawl-history) [])
              (visibility/sync-ai-unit-to-computer-map! current-pos)
              (recur current-pos moves-left true moved-any?))))))))

(defn process-unloading-mission
  [{:keys [current-world
           read-computer-map
           transition-to-loading]
    :as deps}
  pos army-count]
  (case (decisions/unloading-mission-action {:army-count army-count})
    :transition-to-loading (transition-to-loading pos)
    (let [read-map (or read-computer-map current-world)
          transport (get-in (read-map) (conj pos :contents))]
      (process-unloading-with-armies deps pos transport))))

(defn park-lake-transport-if-empty
  [{:keys [current-world
           read-computer-map
           update-game-map!
           sync-transport!
           move-unit-to
           retreat-step-from-shore
           deep-water?]}
   pos lake-cells-set]
  (let [read-map (or read-computer-map current-world)
        sync-transport! (or sync-transport! noop-sync!)
        unit (get-in (read-map) (conj pos :contents))]
    (if (zero? (:army-count unit 0))
      (if-let [step (retreat-step-from-shore (read-map) lake-cells-set pos)]
        (if (move-unit-to pos step)
          (when (deep-water? (read-map) step)
            (update-game-map! update-in (conj step :contents)
                              #(assoc % :mode :sentry
                                      :transport-mission :land-locked))
            (sync-transport! step))
          (do
            (update-game-map! update-in (conj pos :contents)
                              #(assoc % :mode :sentry
                                      :transport-mission :land-locked))
            (sync-transport! pos)))
        (do
          (update-game-map! update-in (conj pos :contents)
                            #(assoc % :mode :sentry
                                    :transport-mission :land-locked))
          (sync-transport! pos)))
      false)))

(defn process-land-locked-mission
  [{:keys [current-world
           read-computer-map
           process-unloading-crawl
           try-opportunistic-unload-any-land]
    :as deps}
   pos lake-cells-set]
  (let [unloaded-now? (boolean (try-opportunistic-unload-any-land pos))]
    (or (park-lake-transport-if-empty deps pos lake-cells-set)
        (let [read-map (or read-computer-map current-world)
              unit (get-in (read-map) (conj pos :contents))
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
           read-computer-map
           read-runtime-state
           update-game-map!
           sync-transport!
           set-transport-mission
           lake-cells]
    :as deps}
   pos transport]
  (case (handler-decisions/lake-transport-action
         {:sentry? (= :sentry (:mode transport))
          :lake-locked? (:lake-locked? transport)
          :has-armies? (pos? (:army-count transport 0))})
    :already-handled true
    (:land-locked-unload :park-empty)
      (let [read-map (or read-computer-map current-world)
            sync-transport! (or sync-transport! noop-sync!)
            lake-cells-set (lake-cells (read-runtime-state :computer-map)
                                       (read-runtime-state :lake-max-cells))]
        (update-game-map! assoc-in (conj pos :contents :never-reload?) true)
        (sync-transport! pos)
        (let [unit (get-in (read-map) (conj pos :contents))
              army-count (:army-count unit 0)]
          (if (pos? army-count)
            (do
              (set-transport-mission pos :land-locked)
              (process-land-locked-mission deps pos lake-cells-set))
            (park-lake-transport-if-empty deps pos lake-cells-set)))
        true)
    nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T11:20:14.123361-05:00", :module-hash "-482953173", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-2112571116"} {:id "def/invasion-army-search-max-distance", :kind "def", :line 10, :end-line 10, :hash "-644390678"} {:id "def/invasion-load-timeout-rounds", :kind "def", :line 11, :end-line 11, :hash "1973653881"} {:id "defn/load-for-invasion-start!", :kind "defn", :line 13, :end-line 17, :hash "1657335532"} {:id "defn-/loadable-army-neighbor?", :kind "defn-", :line 19, :end-line 26, :hash "-1760752950"} {:id "defn/passable-sea-cell?", :kind "defn", :line 28, :end-line 32, :hash "-618562919"} {:id "defn/sea-load-points", :kind "defn", :line 34, :end-line 42, :hash "1374456034"} {:id "defn-/coastal-army?", :kind "defn-", :line 44, :end-line 48, :hash "-1829698076"} {:id "defn-/candidate-coastal-armies", :kind "defn-", :line 50, :end-line 62, :hash "2124573952"} {:id "defn-/nearest-reachable-coastal-army", :kind "defn-", :line 64, :end-line 77, :hash "358071700"} {:id "defn-/move-to-sea-step", :kind "defn-", :line 79, :end-line 85, :hash "-49456250"} {:id "defn/process-find-armies-for-invasion", :kind "defn", :line 87, :end-line 119, :hash "1715961578"} {:id "defn/process-load-for-invasion-with-armies", :kind "defn", :line 121, :end-line 136, :hash "-709718108"} {:id "defn/process-load-for-invasion-empty", :kind "defn", :line 138, :end-line 143, :hash "1323918587"} {:id "defn-/load-for-invasion-context", :kind "defn-", :line 145, :end-line 162, :hash "1719826319"} {:id "defn-/apply-load-for-invasion-action", :kind "defn-", :line 164, :end-line 171, :hash "715718714"} {:id "defn/process-load-for-invasion", :kind "defn", :line 173, :end-line 188, :hash "1852571157"} {:id "defn/process-loading-mission", :kind "defn", :line 190, :end-line 211, :hash "1991527787"} {:id "defn-/take-second-unloading-step", :kind "defn-", :line 213, :end-line 227, :hash "-1531560164"} {:id "defn-/process-unloading-with-armies", :kind "defn-", :line 229, :end-line 245, :hash "-2090759"} {:id "defn/process-unloading-mission", :kind "defn", :line 247, :end-line 255, :hash "1747391872"} {:id "defn/park-lake-transport-if-empty", :kind "defn", :line 257, :end-line 278, :hash "8083485"} {:id "defn/process-land-locked-mission", :kind "defn", :line 280, :end-line 295, :hash "-1454317422"} {:id "defn/fix-idle-mission", :kind "defn", :line 297, :end-line 300, :hash "-346291120"} {:id "defn/maybe-handle-lake-transport", :kind "defn", :line 302, :end-line 327, :hash "-158690834"}]}
;; clj-mutate-manifest-end
