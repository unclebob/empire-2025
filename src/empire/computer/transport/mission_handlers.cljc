(ns empire.computer.transport.mission-handlers
  (:require [empire.computer.shared.grid :as grid]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.transport.mission-handler-decisions :as handler-decisions]
            [empire.computer.transport.decisions :as decisions]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.loading :as loading]
            [empire.computer.transport.reservations :as reservations]
            [empire.computer.transport.sailing-support :as sailing-support]
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
   (let [from-mission (get-in (sa/read-state :computer-map) (conj pos :contents :transport-mission))]
     (update-game-map! update-in (conj pos :contents)
                       #(assoc % :transport-mission :load-for-invasion
                                 :invasion-load-since (or (read-runtime-state :round-number) 0)))
     (sync-transport! pos)
     (tc/log-transport-mission-transition! pos from-mission :load-for-invasion))))

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
        (let [from-mission (get-in (read-map) (conj pos :contents :transport-mission))]
          (update-game-map! update-in (conj pos :contents)
                            #(assoc %
                                    :transport-mission :loading
                                    :major-invasion-skip-revision
                                    (threat-response/major-invasion-target-revision)))
          (sync-transport! pos)
          (tc/log-transport-mission-transition! pos from-mission :loading))))))

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

(defn- planned-loading-action
  [start-sailing transition-to-loading pos transport']
  (let [army-count' (:army-count transport' 0)
        empty? (zero? army-count')]
    (cond
      (or (>= army-count' 6) (loading/manifest-empty? transport'))
      (if empty? (transition-to-loading pos) (start-sailing pos transport'))

      (loading/loading-stale? transport')
      (if (and (<= 2 army-count') (<= army-count' 3) (not empty?))
        (start-sailing pos transport')
        (transition-to-loading pos)))))

(defn process-loading-mission
  [{:keys [current-world
           read-computer-map
           load-adjacent-armies
           start-sailing
           transition-to-loading]}
   pos]
  (load-adjacent-armies pos)
  (let [read-map (or read-computer-map current-world)
        transport' (get-in (read-map) (conj pos :contents))]
    (if (loading/planned-loading? transport')
      (planned-loading-action start-sailing transition-to-loading pos transport')
      (transition-to-loading pos))))

(defn- prefer-pickup-over-unload?
  [transition-to-loading pos transport read-map]
  (let [transport-id (:transport-id transport)
        current-round (or (sa/read-state :round-number) 0)
        unloaded-last-round? (= (:last-unload-round transport) (dec current-round))
        computer-map (read-map)
        load-target-cell (load-targeting/choose-load-target-cell
                          pos computer-map
                          {:reserved-coastal-cells (reservations/reserved-coastal-cells transport-id)
                           :excluded-country-ids (disj #{(:pickup-country-id transport)} nil)
                           :reserved-army-ids (reservations/reserved-army-ids transport-id)})
        load-path (when load-target-cell
                    (or (load-targeting/path-to-load-target pos computer-map load-target-cell) []))
        unload-path (or (sailing-support/compute-sail-to-unload-path pos) [])]
    (and transition-to-loading
         (pos? (:army-count transport 0))
         (< (:army-count transport 0) 6)
         (not unloaded-last-round?)
         load-target-cell
         (or (seq load-path) (load-targeting/target-reached? pos load-target-cell))
         (or (empty? unload-path) (<= (count load-path) (count unload-path))))))

(defn- crawl-step-result
  "Returns :continue, :stop, or nil (blocked) after one crawl step."
  [read-map process-unloading-crawl try-opportunistic-unload current-pos]
  (when-let [next-pos (process-unloading-crawl current-pos)]
    (let [mission (:transport-mission (get-in (read-map) (conj next-pos :contents)))
          still-unloading? (= :unloading mission)
          unloaded? (and still-unloading? (boolean (try-opportunistic-unload next-pos)))]
      (if (and still-unloading? (not unloaded?))
        {:action :continue :pos next-pos}
        {:action :stop :pos next-pos}))))

(defn- unloading-crawl-loop
  [read-map process-unloading-crawl try-opportunistic-unload pos]
  (loop [current-pos pos
         moves-left (transport-speed)
         retried? false
         moved-any? false]
    (if (zero? moves-left)
      (when moved-any? current-pos)
      (if-let [{:keys [action pos]} (crawl-step-result read-map process-unloading-crawl
                                                        try-opportunistic-unload current-pos)]
        (case action
          :continue (recur pos (dec moves-left) false true)
          :stop pos)
        (if retried?
          (when moved-any? current-pos)
          (do
            (sa/update-world! assoc-in (conj current-pos :contents :crawl-history) [])
            (visibility/sync-ai-unit-to-computer-map! current-pos)
            (recur current-pos moves-left true moved-any?)))))))

(defn- clear-hold-and-crawl
  [read-map process-unloading-crawl try-opportunistic-unload pos hold-since-round]
  (when hold-since-round
    (sa/update-world! update-in (conj pos :contents) dissoc :unloading-hold-since-round)
    (visibility/sync-ai-unit-to-computer-map! pos))
  (unloading-crawl-loop read-map process-unloading-crawl try-opportunistic-unload pos))

(defn- process-unloading-with-armies
  [{:keys [current-world
           read-computer-map
           transition-to-loading
           process-unloading-crawl
           try-opportunistic-unload]} pos transport]
  (let [read-map (or read-computer-map current-world)
        hold-since-round (:unloading-hold-since-round transport)
        hold-active? (and hold-since-round
                          (< (- (or (sa/read-state :round-number) 0) hold-since-round) 4))]
    (if hold-active?
      (do (try-opportunistic-unload pos) pos)
      (do
        (when (try-opportunistic-unload pos)
          (visibility/sync-ai-unit-to-computer-map! pos))
        (let [transport' (get-in (read-map) (conj pos :contents))]
          (cond
            (not= :unloading (:transport-mission transport')) pos
            (prefer-pickup-over-unload? transition-to-loading pos transport read-map)
            (transition-to-loading pos)
            :else
            (clear-hold-and-crawl read-map process-unloading-crawl try-opportunistic-unload pos hold-since-round)))))))

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
            (let [from-mission (get-in (read-map) (conj pos :contents :transport-mission))]
              (update-game-map! update-in (conj step :contents)
                                #(assoc % :mode :sentry
                                        :transport-mission :land-locked))
              (sync-transport! step)
              (tc/log-transport-mission-transition! step from-mission :land-locked)))
          (do
            (let [from-mission (get-in (read-map) (conj pos :contents :transport-mission))]
              (update-game-map! update-in (conj pos :contents)
                                #(assoc % :mode :sentry
                                        :transport-mission :land-locked))
              (sync-transport! pos)
              (tc/log-transport-mission-transition! pos from-mission :land-locked))))
        (do
          (let [from-mission (get-in (read-map) (conj pos :contents :transport-mission))]
            (update-game-map! update-in (conj pos :contents)
                              #(assoc % :mode :sentry
                                      :transport-mission :land-locked))
            (sync-transport! pos)
            (tc/log-transport-mission-transition! pos from-mission :land-locked))))
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
    (set-transport-mission pos :sail-to-load)))

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
;; {:version 1, :tested-at "2026-03-26T20:52:11.142739-05:00", :module-hash "789527471", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 15, :hash "4575447"} {:id "def/invasion-army-search-max-distance", :kind "def", :line 17, :end-line 17, :hash "-644390678"} {:id "def/invasion-load-timeout-rounds", :kind "def", :line 18, :end-line 18, :hash "1973653881"} {:id "defn-/noop-sync!", :kind "defn-", :line 20, :end-line 21, :hash "-837411842"} {:id "defn-/transport-speed", :kind "defn-", :line 23, :end-line 25, :hash "-603549653"} {:id "defn/load-for-invasion-start!", :kind "defn", :line 27, :end-line 36, :hash "-1376801120"} {:id "defn-/loadable-army-neighbor?", :kind "defn-", :line 38, :end-line 45, :hash "-1760752950"} {:id "defn-/coastal-army?", :kind "defn-", :line 47, :end-line 51, :hash "-1829698076"} {:id "defn-/candidate-coastal-armies", :kind "defn-", :line 53, :end-line 65, :hash "1189398605"} {:id "defn-/nearest-reachable-coastal-army", :kind "defn-", :line 67, :end-line 80, :hash "-598079234"} {:id "defn-/move-to-sea-step", :kind "defn-", :line 82, :end-line 88, :hash "-49456250"} {:id "defn/process-find-armies-for-invasion", :kind "defn", :line 90, :end-line 131, :hash "-842756163"} {:id "defn/process-load-for-invasion-with-armies", :kind "defn", :line 133, :end-line 148, :hash "-709718108"} {:id "defn/process-load-for-invasion-empty", :kind "defn", :line 150, :end-line 158, :hash "-1100469602"} {:id "defn-/load-for-invasion-context", :kind "defn-", :line 160, :end-line 177, :hash "-1933132620"} {:id "defn-/apply-load-for-invasion-action", :kind "defn-", :line 179, :end-line 191, :hash "1509535104"} {:id "defn/process-load-for-invasion", :kind "defn", :line 193, :end-line 210, :hash "-1011516714"} {:id "defn-/planned-loading-action", :kind "defn-", :line 212, :end-line 223, :hash "-1340207686"} {:id "defn/process-loading-mission", :kind "defn", :line 225, :end-line 237, :hash "-1524428279"} {:id "defn-/prefer-pickup-over-unload?", :kind "defn-", :line 239, :end-line 259, :hash "-118771178"} {:id "defn-/crawl-step-result", :kind "defn-", :line 261, :end-line 270, :hash "-1532108500"} {:id "defn-/unloading-crawl-loop", :kind "defn-", :line 272, :end-line 290, :hash "1156326782"} {:id "defn-/clear-hold-and-crawl", :kind "defn-", :line 292, :end-line 297, :hash "-28062225"} {:id "defn-/process-unloading-with-armies", :kind "defn-", :line 299, :end-line 320, :hash "70882300"} {:id "defn/process-unloading-mission", :kind "defn", :line 322, :end-line 332, :hash "-1854438957"} {:id "defn/park-lake-transport-if-empty", :kind "defn", :line 334, :end-line 370, :hash "-1162915242"} {:id "defn/process-land-locked-mission", :kind "defn", :line 372, :end-line 389, :hash "1059021339"} {:id "defn/fix-idle-mission", :kind "defn", :line 391, :end-line 394, :hash "1763448864"} {:id "defn/maybe-handle-lake-transport", :kind "defn", :line 396, :end-line 426, :hash "-286489267"}]}
;; clj-mutate-manifest-end
