;; mutation-tested: 2026-03-02
(ns empire.computer.transport
  "Computer transport module — facade delegating to sub-modules.
   Loading: coastal crawl, auto-load adjacent armies, sail when loaded
   Sailing: follow BFS path to unexplored coast, opportunistic unload
   Unloading: coast-crawl while dropping armies on empty land"
  (:require [empire.application.ports.movement-execution :as movement-port]
            [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.lake-naval :as lake-naval]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-loading :as loading]
            [empire.computer.transport-mission-handlers :as mission-handlers]
            [empire.computer.transport-sailing :as sailing]
            [empire.computer.transport-targeting :as targeting]
            [empire.computer.transport-unloading :as unloading]
            [empire.computer.threat-response :as threat-response]
            [empire.debug.logging :as debug]))

(defn- execution-port
  []
  (:execution-port (sa/state-ctx)))
(def find-unload-target targeting/find-unload-target)
(def unload-armies unloading/unload-armies)

(defn- move-toward-position
  "Move transport one step toward target using greedy neighbor selection."
  [pos target]
  (let [passable (tc/get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when (and closest (core/move-unit-to pos closest))
      (movement-port/movement-update-cell-visibility (execution-port) pos :computer)
      (movement-port/movement-update-cell-visibility (execution-port) closest :computer)
      (loading/load-adjacent-armies closest)
      closest)))

(defn- start-sailing
  "Transition transport from loading to sailing with BFS path."
  [pos transport]
  (tc/set-transport-mission pos :sailing)
  (tc/mint-unload-event-id pos transport)
  (when-not (sa/read-state :transport-fully-loaded?)
    (sa/write-state! :transport-fully-loaded? true))
  (tc/mint-unload-country-id pos)
  (tc/record-pickup-continent-pos pos transport)
  (when-let [path (sailing/compute-sail-path pos)]
    (sa/update-world! assoc-in
                      (conj pos :contents :sail-path) path)))

(defn- transition-to-loading
  "Switch an empty transport to loading mode and find next pickup continent."
  [pos]
  (let [transport (get-in (sa/current-world) (conj pos :contents))]
    (if (:never-reload? transport)
      (do
        (tc/set-transport-mission pos :sailing)
        (sa/update-world! update-in (conj pos :contents)
                          dissoc :unload-target-city :pickup-continent-pos))
      (do
        (tc/set-transport-mission pos :loading)
        (sa/update-world! update-in (conj pos :contents) dissoc :unload-target-city)
        (let [current-continent (when-let [lp (tc/find-adjacent-land-pos pos)]
                                  (land-objectives/flood-fill-continent lp))
              next-pickup (targeting/find-next-pickup-continent-pos pos current-continent)]
          (sa/update-world! assoc-in
                            (conj pos :contents :pickup-continent-pos) next-pickup))))))

(defn- load-for-invasion-start!
  [pos]
  (mission-handlers/load-for-invasion-start! sa/update-world! sa/read-state pos))

(defn- passable-sea-cell?
  [cell]
  (mission-handlers/passable-sea-cell? cell))
(defn- sea-load-points
  "All passable sea cells adjacent to at least one computer army."
  []
  (mission-handlers/sea-load-points (sa/current-world) core/get-neighbors))

(declare loading-crawl-move handle-stale-loading)

(defn- transition-load-for-invasion-to-sailing!
  [pos]
  (tc/set-transport-mission pos :sailing)
  (threat-response/prepare-transport! pos))

(defn- transition-load-for-invasion-to-unloading!
  [pos major-target]
  (sa/update-world! update-in (conj pos :contents)
                    #(assoc % :transport-mission :unloading
                              :invasion-target (or (:invasion-target %)
                                                   major-target))))

(defn- mission-handler-deps
  []
  {:current-world sa/current-world
   :read-runtime-state sa/read-state
   :update-game-map! sa/update-world!
   :execution-port (execution-port)
   :pathfinding-port (:pathfinding-port (sa/state-ctx))
   :get-neighbors core/get-neighbors
   :load-adjacent-armies loading/load-adjacent-armies
   :coastal-crawl-move loading/coastal-crawl-move
   :move-unit-to core/move-unit-to
   :set-transport-mission tc/set-transport-mission
   :transition-to-sailing transition-load-for-invasion-to-sailing!
   :transition-to-unloading transition-load-for-invasion-to-unloading!
   :has-nearby-unloadable-land? unloading/has-nearby-unloadable-land?
   :clear-pickup-continent-if-arrived loading/clear-pickup-continent-if-arrived
   :should-start-sailing? loading/should-start-sailing?
   :loading-stale? loading/loading-stale?
   :start-sailing start-sailing
   :handle-stale-loading handle-stale-loading
   :loading-crawl-move loading-crawl-move
   :process-unloading-crawl unloading/unloading-crawl-move
   :try-opportunistic-unload unloading/try-opportunistic-unload
   :try-opportunistic-unload-any-land unloading/try-opportunistic-unload-any-land
   :retreat-step-from-shore lake-naval/retreat-step-from-shore
   :deep-water? lake-naval/deep-water?
   :lake-cells lake-naval/lake-cells
   :transition-to-loading transition-to-loading})

(defn- process-find-armies-for-invasion
  [pos]
  (mission-handlers/process-find-armies-for-invasion (mission-handler-deps) pos))
(defn- process-load-for-invasion-with-armies
  [pos transport major-target in-unload-zone? timed-out?]
  (mission-handlers/process-load-for-invasion-with-armies
   (mission-handler-deps) pos transport major-target in-unload-zone? timed-out?))

(defn- process-load-for-invasion-empty
  [pos timed-out?]
  (mission-handlers/process-load-for-invasion-empty sa/update-world! transition-to-loading pos timed-out?))
(defn- process-load-for-invasion
  [pos]
  (mission-handlers/process-load-for-invasion
   (mission-handler-deps) sa/update-world! transition-to-loading pos))

(defn- loading-crawl-move
  [pos]
  (let [move-one (fn [p]
                   (let [t (get-in (sa/current-world) (conj p :contents))
                         pcp (:pickup-continent-pos t)]
                     (if pcp
                       (or (move-toward-position p pcp)
                           (loading/coastal-crawl-move p))
                       (loading/coastal-crawl-move p))))]
    (when-let [pos1 (move-one pos)]
      (or (move-one pos1) pos1))))

(defn- handle-stale-loading
  "When loading has stalled, sail with what we have or find a new pcp."
  [pos transport army-count]
  (if (pos? army-count)
    (start-sailing pos transport)
    (let [new-pcp (targeting/find-next-pickup-continent-pos pos nil 0)]
      (sa/update-world! assoc-in (conj pos :contents :pickup-continent-pos) new-pcp)
      (sa/update-world! assoc-in (conj pos :contents :loading-since)
                        (or (sa/read-state :round-number) 0))
      (loading-crawl-move pos))))
(defn- process-loading-mission
  [pos]
  (mission-handlers/process-loading-mission (mission-handler-deps) pos))

(defn- process-unloading-mission
  [pos army-count]
  (mission-handlers/process-unloading-mission (mission-handler-deps) pos army-count))
(defn- park-lake-transport-if-empty
  [pos lake-cells-set]
  (mission-handlers/park-lake-transport-if-empty (mission-handler-deps) pos lake-cells-set))

(defn- process-land-locked-mission
  [pos lake-cells-set]
  (mission-handlers/process-land-locked-mission (mission-handler-deps) pos lake-cells-set))

(defn- fix-idle-mission
  [pos mission]
  (mission-handlers/fix-idle-mission tc/set-transport-mission pos mission))

(defn- run-transport-mission
  [pos current-mission army-count]
  (if-let [handler (get {:invading #(sailing/process-invading-mission pos)
                         :find-armies-for-invasion #(process-find-armies-for-invasion pos)
                         :load-for-invasion #(process-load-for-invasion pos)
                         :land-locked #(process-land-locked-mission pos
                                                                    (lake-naval/lake-cells
                                                                     (sa/read-state :computer-map)
                                                                     (sa/read-state :lake-max-cells)))
                         :unloading #(process-unloading-mission pos army-count)
                         :sailing #(sailing/process-sailing-mission pos)
                         :loading #(process-loading-mission pos)}
                        current-mission)]
    (handler)
    nil))

(defn- dispatch-transport-mission
  [pos transport]
  (let [army-count (:army-count transport 0)
        mission (:transport-mission transport)]
    (fix-idle-mission pos mission)
    (when (and (= :loading (or mission :loading))
               (:never-reload? transport))
      (tc/set-transport-mission pos :sailing))
    (let [current-mission (or (:transport-mission (get-in (sa/current-world) (conj pos :contents)))
                              mission
                              :loading)]
      (debug/log-computer-event! :transport-process pos
                                 {:mission current-mission :armies army-count
                                  :pcp (:pickup-continent-pos transport)})
      (if (and (targeting/should-try-opportunistic-unload? army-count current-mission)
               (unloading/try-opportunistic-unload pos))
        true
        (run-transport-mission pos current-mission army-count)))))

(defn- maybe-handle-lake-transport
  [pos transport]
  (mission-handlers/maybe-handle-lake-transport (mission-handler-deps) pos transport))

(defn process-transport
  "Processes a transport unit using simplified 3-state mission flow.
   Returns nil after processing — transports only move once per round."
  [pos]
  (let [transport (:contents (get-in (sa/current-world) pos))]
    (when (and transport
               (= :computer (:owner transport))
               (= :transport (:type transport)))
      (when-not (or (= :sentry (:mode transport))
                    (maybe-handle-lake-transport pos transport))
        (threat-response/prepare-transport! pos)
        (dispatch-transport-mission pos (:contents (get-in (sa/current-world) pos))))))
  nil)
