(ns empire.computer.transport.mission-handlers.invasion
  (:require [empire.computer.shared.grid :as grid]
            [empire.computer.transport.mission-handler-decisions :as handler-decisions]
            [empire.computer.transport.core :as tc]
            [empire.computer.threat-response-impl :as threat-response]
            [empire.state.api :as sa]))

(def ^:private invasion-army-search-max-distance 6)
(def ^:private invasion-load-timeout-rounds 5)

(defn- noop-sync! [_])

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

(defn- find-armies-context
  [{:keys [current-world read-computer-map read-runtime-state get-neighbors
           bfs-to-land-ho-target load-adjacent-armies]} pos]
  (load-adjacent-armies pos)
  (let [read-map (or read-computer-map current-world)
        world (read-map)
        transport (get-in world (conj pos :contents))
        nearest-army (nearest-reachable-coastal-army
                      world read-runtime-state get-neighbors bfs-to-land-ho-target pos)]
    {:read-map read-map
     :world world
     :transport transport
     :nearest-army nearest-army
     :action (handler-decisions/find-armies-for-invasion-action
              {:army-count (:army-count transport 0)
               :loadable-neighbor? (loadable-army-neighbor? world get-neighbors pos)
               :reachable-path? (boolean nearest-army)})}))

(defn- start-load-for-invasion-action!
  [{:keys [read-runtime-state update-game-map! sync-transport!]} pos _context]
  (load-for-invasion-start! update-game-map! read-runtime-state sync-transport! pos))

(defn- follow-nearest-army-path!
  [{:keys [read-runtime-state update-game-map! sync-transport!
           update-cell-visibility! load-adjacent-armies coastal-crawl-move move-unit-to]}
   pos {:keys [nearest-army]}]
  (if-let [{:keys [path]} nearest-army]
    (if (seq path)
      (or (move-to-sea-step move-unit-to update-cell-visibility! load-adjacent-armies pos (first path))
          (coastal-crawl-move pos))
      (load-for-invasion-start! update-game-map! read-runtime-state sync-transport! pos))
    nil))

(defn- revert-to-loading!
  [{:keys [update-game-map! sync-transport!]} pos {:keys [read-map]}]
  (let [from-mission (get-in (read-map) (conj pos :contents :transport-mission))]
    (update-game-map! update-in (conj pos :contents)
                      #(assoc %
                              :transport-mission :loading
                              :major-invasion-skip-revision
                              (threat-response/major-invasion-target-revision)))
    (sync-transport! pos)
    (tc/log-transport-mission-transition! pos from-mission :loading)))

(def ^:private find-armies-handlers
  {:start-load-for-invasion start-load-for-invasion-action!
   :follow-path follow-nearest-army-path!
   :revert-loading revert-to-loading!})

(defn process-find-armies-for-invasion
  [deps pos]
  (let [deps (update deps :sync-transport! #(or % noop-sync!))
        context (find-armies-context deps pos)]
    ((find-armies-handlers (:action context)) deps pos context)))

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

(defn- revert-or-hold-load-for-invasion
  [deps update-game-map! transition-to-loading pos timed-out? load-state]
  (if (= :revert-loading (handler-decisions/load-for-invasion-action load-state))
    (process-load-for-invasion-empty
     update-game-map!
     transition-to-loading
     (:sync-transport! deps)
     pos
     timed-out?)
    (when (:has-armies? load-state) nil)))

(defn- apply-load-for-invasion-action
  [deps update-game-map! transition-to-loading pos
   {:keys [major-target timed-out? load-state]}]
  (case (handler-decisions/load-for-invasion-action load-state)
    :unload ((:transition-to-unloading deps) pos major-target)
    :sail ((:transition-to-sailing deps) pos)
    (revert-or-hold-load-for-invasion
     deps update-game-map! transition-to-loading pos timed-out? load-state)))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:19:29.525937-05:00", :module-hash "-1153253409", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "188745959"} {:id "def/invasion-army-search-max-distance", :kind "def", :line 8, :end-line nil, :hash "-644390678"} {:id "def/invasion-load-timeout-rounds", :kind "def", :line 9, :end-line nil, :hash "1973653881"} {:id "defn-/noop-sync!", :kind "defn-", :line 11, :end-line nil, :hash "-837411842"} {:id "defn/load-for-invasion-start!", :kind "defn", :line 13, :end-line nil, :hash "18042517"} {:id "defn-/loadable-army-neighbor?", :kind "defn-", :line 24, :end-line nil, :hash "-1760752950"} {:id "defn-/coastal-army?", :kind "defn-", :line 33, :end-line nil, :hash "-1829698076"} {:id "defn-/candidate-coastal-armies", :kind "defn-", :line 39, :end-line nil, :hash "1189398605"} {:id "defn-/nearest-reachable-coastal-army", :kind "defn-", :line 53, :end-line nil, :hash "-598079234"} {:id "defn-/move-to-sea-step", :kind "defn-", :line 68, :end-line nil, :hash "-49456250"} {:id "defn-/find-armies-context", :kind "defn-", :line 76, :end-line nil, :hash "-1072934234"} {:id "defn-/start-load-for-invasion-action!", :kind "defn-", :line 94, :end-line nil, :hash "1007650070"} {:id "defn-/follow-nearest-army-path!", :kind "defn-", :line 98, :end-line nil, :hash "-433317648"} {:id "defn-/revert-to-loading!", :kind "defn-", :line 109, :end-line nil, :hash "1426609044"} {:id "def/find-armies-handlers", :kind "def", :line 120, :end-line nil, :hash "-1136331203"} {:id "defn/process-find-armies-for-invasion", :kind "defn", :line 125, :end-line nil, :hash "-1717439688"} {:id "defn/process-load-for-invasion-with-armies", :kind "defn", :line 131, :end-line nil, :hash "-709718108"} {:id "defn/process-load-for-invasion-empty", :kind "defn", :line 148, :end-line nil, :hash "-1100469602"} {:id "defn-/load-for-invasion-context", :kind "defn-", :line 158, :end-line nil, :hash "-1933132620"} {:id "defn-/revert-or-hold-load-for-invasion", :kind "defn-", :line 177, :end-line nil, :hash "-1463304295"} {:id "defn-/apply-load-for-invasion-action", :kind "defn-", :line 188, :end-line nil, :hash "-1184485884"} {:id "defn/process-load-for-invasion", :kind "defn", :line 197, :end-line nil, :hash "-1011516714"}]}
;; clj-mutate-manifest-end
