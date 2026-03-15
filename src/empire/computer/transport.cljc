(ns empire.computer.transport
  "Computer transport module — facade delegating to sub-modules.
   Loading: coastal crawl, auto-load adjacent armies, sail when loaded
   Sailing: follow BFS path to unexplored coast, opportunistic unload
   Unloading: coast-crawl while dropping armies on empty land"
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.lake-naval :as lake-naval]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.oscillation :as oscillation]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-loading :as loading]
            [empire.computer.transport-mission-handlers :as mission-handlers]
            [empire.computer.transport-sailing :as sailing]
            [empire.computer.transport-targeting :as targeting]
            [empire.computer.transport-unloading :as unloading]
            [empire.computer.threat-response :as threat-response]
            [empire.game-mechanics.debug.logging :as debug]))
(def find-unload-target targeting/find-unload-target)
(def unload-armies unloading/unload-armies)

(defn- move-toward-position
  "Move transport one step toward target using greedy neighbor selection."
  [pos target]
  (let [passable (tc/get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when (and closest (core/move-unit-to pos closest))
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility closest :computer)
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
   :update-cell-visibility! visibility/update-cell-visibility
   :bfs-to-land-ho-target pathfinding-bfs/bfs-to-land-ho-target
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

(def ^:private transport-random-walk-restore-keys
  [:transport-mission
   :sail-path
   :pickup-continent-pos
   :loading-since
   :invasion-target
   :invasion-path
   :invasion-path-origin
   :invasion-plan-revision
   :invasion-load-since
   :major-invasion-find-armies-round
   :major-invasion-skip-revision])

(defn- maybe-enter-transport-random-walk!
  [pos]
  (sa/update-world! update-in (conj pos :contents)
                    #(oscillation/maybe-enter-random-walk % transport-random-walk-restore-keys
                                                          {:unit-type :transport
                                                           :pos pos})))

(defn- process-transport-random-walk
  [pos]
  (let [passable (tc/get-passable-sea-neighbors pos)
        empty-passable (filter #(nil? (:contents (get-in (sa/current-world) %))) passable)
        final-pos (if-let [target (when (seq empty-passable) (rand-nth empty-passable))]
                    (if (core/move-unit-to pos target)
                      (do
                        (visibility/update-cell-visibility pos :computer)
                        (visibility/update-cell-visibility target :computer)
                        target)
                      pos)
                    pos)]
    (sa/update-world! update-in (conj final-pos :contents)
                      #(-> %
                           oscillation/dec-random-walk
                           oscillation/maybe-restore))))

(defn- process-active-transport
  [pos transport]
  (when-not (or (= :sentry (:mode transport))
                (maybe-handle-lake-transport pos transport))
    (threat-response/prepare-transport! pos)
    (dispatch-transport-mission pos (:contents (get-in (sa/current-world) pos)))))

(defn process-transport
  "Processes a transport unit using simplified 3-state mission flow.
   Returns nil after processing — transports only move once per round."
  [pos]
  (let [transport (:contents (get-in (sa/current-world) pos))]
    (when (and transport
               (= :computer (:owner transport))
               (= :transport (:type transport)))
      (maybe-enter-transport-random-walk! pos)
      (let [transport (get-in (sa/current-world) (conj pos :contents))]
        (if (oscillation/in-random-walk? transport)
          (process-transport-random-walk pos)
          (process-active-transport pos transport)))))
  nil)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:52.719082-05:00", :module-hash "710387990", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 20, :hash "580626063"} {:id "def/find-unload-target", :kind "def", :line 21, :end-line 21, :hash "-818482077"} {:id "def/unload-armies", :kind "def", :line 22, :end-line 22, :hash "-1839983843"} {:id "defn-/move-toward-position", :kind "defn-", :line 24, :end-line 33, :hash "-438378926"} {:id "defn-/start-sailing", :kind "defn-", :line 35, :end-line 46, :hash "-552073504"} {:id "defn-/transition-to-loading", :kind "defn-", :line 48, :end-line 64, :hash "888261217"} {:id "defn-/load-for-invasion-start!", :kind "defn-", :line 66, :end-line 68, :hash "-1386709183"} {:id "defn-/passable-sea-cell?", :kind "defn-", :line 70, :end-line 72, :hash "-1121586364"} {:id "defn-/sea-load-points", :kind "defn-", :line 73, :end-line 76, :hash "-852838570"} {:id "form/9/declare", :kind "declare", :line 78, :end-line 78, :hash "2015927613"} {:id "defn-/transition-load-for-invasion-to-sailing!", :kind "defn-", :line 80, :end-line 83, :hash "-1240826066"} {:id "defn-/transition-load-for-invasion-to-unloading!", :kind "defn-", :line 85, :end-line 90, :hash "-70678409"} {:id "defn-/mission-handler-deps", :kind "defn-", :line 92, :end-line 119, :hash "754516218"} {:id "defn-/process-find-armies-for-invasion", :kind "defn-", :line 121, :end-line 123, :hash "-2079022115"} {:id "defn-/process-load-for-invasion-with-armies", :kind "defn-", :line 124, :end-line 127, :hash "-1658821931"} {:id "defn-/process-load-for-invasion-empty", :kind "defn-", :line 129, :end-line 131, :hash "532265959"} {:id "defn-/process-load-for-invasion", :kind "defn-", :line 132, :end-line 135, :hash "-849233059"} {:id "defn-/loading-crawl-move", :kind "defn-", :line 137, :end-line 147, :hash "1534227730"} {:id "defn-/handle-stale-loading", :kind "defn-", :line 149, :end-line 158, :hash "-565701435"} {:id "defn-/process-loading-mission", :kind "defn-", :line 159, :end-line 161, :hash "391544837"} {:id "defn-/process-unloading-mission", :kind "defn-", :line 163, :end-line 165, :hash "1988449558"} {:id "defn-/park-lake-transport-if-empty", :kind "defn-", :line 166, :end-line 168, :hash "77373283"} {:id "defn-/process-land-locked-mission", :kind "defn-", :line 170, :end-line 172, :hash "-1060592190"} {:id "defn-/fix-idle-mission", :kind "defn-", :line 174, :end-line 176, :hash "2095014349"} {:id "defn-/run-transport-mission", :kind "defn-", :line 178, :end-line 192, :hash "2030313739"} {:id "defn-/dispatch-transport-mission", :kind "defn-", :line 194, :end-line 211, :hash "-2057346255"} {:id "defn-/maybe-handle-lake-transport", :kind "defn-", :line 213, :end-line 215, :hash "-1517017432"} {:id "defn/process-transport", :kind "defn", :line 217, :end-line 260, :hash "-18001674"}]}
;; clj-mutate-manifest-end
