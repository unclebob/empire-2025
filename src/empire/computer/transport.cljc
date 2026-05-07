(ns empire.computer.transport
  "Computer transport module — facade delegating to sub-modules.
   Loading: coastal crawl, auto-load adjacent armies, sail when loaded
   Sailing: follow BFS path to unexplored coast, opportunistic unload
   Unloading: coast-crawl while dropping armies on empty land"
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.army.assignment :as army-assignment]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.computer.ship.lake-naval :as lake-naval]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.transport.decisions :as decisions]
            [empire.computer.transport.process-decisions :as process-decisions]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.loading :as loading]
            [empire.computer.transport.mission-handlers :as mission-handlers]
            [empire.computer.transport.reservations :as reservations]
            [empire.computer.transport.sailing :as sailing]
            [empire.computer.transport.sailing-path :as sailing-path]
            [empire.computer.transport.targeting :as targeting]
            [empire.computer.transport.unloading :as unloading]
            [empire.computer.threat-response-impl :as threat-response]
            [empire.game-mechanics.debug.logging :as debug]))
(def find-unload-target targeting/find-unload-target)
(def unload-armies unloading/unload-armies)

(defn- transport-speed
  []
  (dispatcher/speed :transport))

(defn- move-toward-position
  "Move transport one step toward target using greedy neighbor selection."
  [pos target]
  (let [passable (tc/get-passable-sea-neighbors pos)
        closest (grid/move-toward pos target passable)]
    (when (and closest (action-resolution/move-unit-to pos closest))
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility closest :computer)
      (loading/load-adjacent-armies closest)
      closest)))

(defn- start-sailing
  "Transition transport from loading to sailing with BFS path."
  [pos transport]
  (when-let [path (seq (sailing/compute-sail-path pos (:army-count transport 0)))]
    (reservations/release! (:transport-id transport))
    (tc/set-transport-mission pos :sail-to-unload)
    (tc/assoc-transport-field! pos :load-target-cell nil)
    (tc/assoc-transport-field! pos :load-manifest nil)
    (tc/assoc-transport-field! pos :load-plan-failure nil)
    (tc/assoc-transport-field! pos :hold-sail-to-load-since-round nil)
    (tc/assoc-transport-field! pos :loading-since-round nil)
    (tc/mint-unload-event-id pos transport)
    (when-not (sa/read-state :transport-fully-loaded?)
      (sa/write-state! :transport-fully-loaded? true))
    (tc/mint-unload-country-id pos)
    (tc/assoc-transport-field! pos :sail-path (vec path))
    (visibility/sync-ai-unit-to-computer-map! pos)
    true))

(defn- transition-to-loading
  "Switch an empty transport to the return-to-load sailing state."
  [pos]
  (let [cell-type (get-in (sa/read-state :computer-map) (conj pos :type))]
    (if (= :city cell-type)
      (sailing/enter-leave-city! pos)
      (sailing/enter-sail-to-load! pos))))

(defn- load-for-invasion-start!
  [pos]
  (mission-handlers/load-for-invasion-start! sa/update-world! sa/read-state pos))

(defn- transition-load-for-invasion-to-sailing!
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        empty? (zero? (:army-count transport 0))
        computer-map (sa/read-state :computer-map)]
    (if empty?
      (transition-to-loading pos)
      (when-let [sail-path (seq (sailing-path/compute-sail-to-unload-path pos computer-map))]
        (reservations/release! (:transport-id transport))
        (tc/set-transport-mission pos :sail-to-unload)
        (tc/assoc-transport-field! pos :load-target-cell nil)
        (tc/assoc-transport-field! pos :load-manifest nil)
        (tc/assoc-transport-field! pos :load-plan-failure nil)
        (tc/assoc-transport-field! pos :loading-since-round nil)
        (tc/assoc-transport-field! pos :sail-path (vec sail-path))
        (visibility/sync-ai-unit-to-computer-map! pos)
        (threat-response/prepare-transport! pos)))))

(defn- transition-load-for-invasion-to-unloading!
  [pos major-target]
  (let [from-mission (get-in (sa/read-state :computer-map) (conj pos :contents :transport-mission))]
    (reservations/release! (get-in (sa/read-state :computer-map)
                                   (conj pos :contents :transport-id)))
    (when (tc/update-transport-contents!
           pos
           #(assoc % :transport-mission :unloading
                     :load-target-cell nil
                     :load-manifest nil
                     :loading-since-round nil
                     :invasion-target (or (:invasion-target %)
                                          major-target)))
      (tc/log-transport-mission-transition! pos from-mission :unloading)))
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- mission-handler-deps
  []
  {:read-computer-map #(sa/read-state :computer-map)
   :read-runtime-state sa/read-state
   :update-game-map! sa/update-world!
   :sync-transport! visibility/sync-ai-unit-to-computer-map!
   :update-cell-visibility! visibility/update-cell-visibility
   :bfs-to-land-ho-target pathfinding-bfs/bfs-to-land-ho-target
   :get-neighbors world-query/get-neighbors
   :load-adjacent-armies loading/load-adjacent-armies
   :coastal-crawl-move loading/coastal-crawl-move
   :move-unit-to action-resolution/move-unit-to
   :set-transport-mission tc/set-transport-mission
   :transition-to-sailing transition-load-for-invasion-to-sailing!
   :transition-to-unloading transition-load-for-invasion-to-unloading!
   :has-nearby-unloadable-land? unloading/has-nearby-unloadable-land?
   :should-start-sailing? loading/should-start-sailing?
   :start-sailing start-sailing
   :transition-to-loading transition-to-loading
   :process-unloading-crawl unloading/unloading-crawl-move
   :try-opportunistic-unload unloading/try-opportunistic-unload
   :try-opportunistic-unload-any-land unloading/try-opportunistic-unload-any-land
   :retreat-step-from-shore lake-naval/retreat-step-from-shore
   :deep-water? lake-naval/deep-water?
   :lake-cells lake-naval/lake-cells})

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
  (let [handler-key (process-decisions/transport-mission-handler current-mission)
        handlers {:invading #(sailing/process-invading-mission pos)
                  :find-armies-for-invasion #(process-find-armies-for-invasion pos)
                  :load-for-invasion #(process-load-for-invasion pos)
                  :land-locked #(process-land-locked-mission pos
                                                            (lake-naval/lake-cells
                                                             (sa/read-state :computer-map)
                                                             (sa/read-state :lake-max-cells)))
                  :unloading #(process-unloading-mission pos army-count)
                  :sailing #(sailing/process-sailing-mission pos)
                  :sail-to-unload #(sailing/process-sailing-mission pos)
                  :leave-city #(sailing/process-sailing-mission pos)
                  :sail-to-load #(sailing/process-sailing-mission pos)
                  :hold-sail-to-load #(sailing/process-sailing-mission pos)
                  :loading #(process-loading-mission pos)}]
    (when-let [handler (get handlers handler-key)]
      (handler))))

(defn- apply-idle-fix!
  [pos initial-mission fix-idle?]
  (when fix-idle?
    (fix-idle-mission pos initial-mission)))

(defn- apply-mission-start!
  [pos initial-mission mission]
  (when (and mission
             (not= mission initial-mission)
             (#{nil :idle} initial-mission))
    (tc/set-transport-mission pos mission)))

(defn- apply-force-sailing!
  [pos transport army-count force-sailing?]
  (when force-sailing?
    (if (zero? army-count)
      (transition-to-loading pos)
      (start-sailing pos transport))))

(defn- current-transport-mission
  [pos mission]
  (or (:transport-mission (get-in (sa/read-state :computer-map) (conj pos :contents)))
      mission
      :loading))

(defn- log-transport-process!
  [pos transport army-count current-mission]
  (debug/log-computer-event! :transport-process pos
                             {:mission current-mission
                              :armies army-count
                              :manifest (:load-manifest transport)
                              :reservation (when-let [transport-id (:transport-id transport)]
                                             (get (sa/read-state :transport-load-reservations) transport-id))}))

(defn- dispatch-transport-mission
  [pos transport]
  (let [army-count (:army-count transport 0)
        initial-mission (:transport-mission transport)
        {:keys [fix-idle? force-sailing? mission]} (process-decisions/transport-mission-action
                                                    {:mission initial-mission
                                                     :army-count army-count
                                                     :never-reload? (:never-reload? transport)})]
    (apply-idle-fix! pos initial-mission fix-idle?)
    (apply-mission-start! pos initial-mission mission)
    (apply-force-sailing! pos transport army-count force-sailing?)
    (let [current-mission (current-transport-mission pos mission)]
      (log-transport-process! pos transport army-count current-mission)
      (run-transport-mission pos current-mission army-count))))

(defn- maybe-handle-lake-transport
  [pos transport]
  (mission-handlers/maybe-handle-lake-transport (mission-handler-deps) pos transport))

(def ^:private transport-random-walk-restore-keys
  [:transport-mission
   :load-target-cell
   :sail-path
   :invasion-target
   :invasion-path
   :invasion-path-origin
   :invasion-plan-revision
   :invasion-load-since
   :major-invasion-find-armies-round
   :major-invasion-skip-revision])

(defn- maybe-enter-transport-random-walk!
  [pos]
  (computer-movement/update-unit-and-sync!
   pos
   #(oscillation/maybe-enter-random-walk % transport-random-walk-restore-keys
                                         {:unit-type :transport
                                          :pos pos})))

(defn- process-transport-random-walk
  [pos]
  (let [passable (tc/get-passable-sea-neighbors pos)
        computer-map (sa/read-state :computer-map)
        empty-passable (filter #(nil? (:contents (get-in computer-map %))) passable)
        final-pos (if-let [target (when (seq empty-passable) (rand-nth empty-passable))]
                    (if (action-resolution/move-unit-to pos target)
                      (do
                        (visibility/update-cell-visibility pos :computer)
                        (visibility/update-cell-visibility target :computer)
                        (visibility/sync-ai-unit-to-computer-map! target)
                        target)
                      pos)
                    pos)]
    (sa/update-world! update-in (conj final-pos :contents)
                      #(-> %
                           oscillation/dec-random-walk
                           oscillation/maybe-restore))
    (visibility/sync-ai-unit-to-computer-map! final-pos)))

(defn- process-active-transport
  [pos transport]
  (case (process-decisions/active-transport-action
         {:sentry? (= :sentry (:mode transport))
          :lake-handled? (maybe-handle-lake-transport pos transport)})
    :dispatch (do
                (threat-response/prepare-transport! pos)
                (visibility/sync-ai-unit-to-computer-map! pos)
                (dispatch-transport-mission pos (:contents (get-in (sa/read-state :computer-map) pos))))
    nil))

(defn- ensure-mission-assigned! [pos transport]
  (when (and (= :transport (:type transport))
             (= :computer (:owner transport))
             (nil? (:transport-mission transport)))
    (transition-to-loading pos)))

(defn- check-random-walk [pos transport']
  (when (and transport'
             (= :computer (:owner transport'))
             (= :transport (:type transport')))
    (maybe-enter-transport-random-walk! pos))
  (oscillation/in-random-walk? (get-in (sa/read-state :computer-map) (conj pos :contents))))

(defn process-transport
  "Processes a transport unit using simplified 3-state mission flow.
   Returns nil after processing — transports only move once per round."
  [pos]
  (visibility/update-cell-visibility pos :computer)
  (visibility/sync-ai-unit-to-computer-map! pos)
  (let [transport (:contents (get-in (sa/read-state :computer-map) pos))]
    (ensure-mission-assigned! pos transport)
    (let [transport' (:contents (get-in (sa/read-state :computer-map) pos))]
      (case (process-decisions/transport-process-action
             {:transport? (= :transport (:type transport'))
              :computer-owned? (= :computer (:owner transport'))
              :random-walk? (check-random-walk pos transport')})
        :random-walk (process-transport-random-walk pos)
        :active (process-active-transport pos transport')
        nil)))
  nil)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T20:49:23.406641-05:00", :module-hash "-568090775", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 29, :hash "1320822215"} {:id "def/find-unload-target", :kind "def", :line 30, :end-line 30, :hash "-818482077"} {:id "def/unload-armies", :kind "def", :line 31, :end-line 31, :hash "-1839983843"} {:id "defn-/transport-speed", :kind "defn-", :line 33, :end-line 35, :hash "-603549653"} {:id "defn-/move-toward-position", :kind "defn-", :line 37, :end-line 46, :hash "2078952593"} {:id "defn-/start-sailing", :kind "defn-", :line 48, :end-line 65, :hash "-1340170357"} {:id "defn-/transition-to-loading", :kind "defn-", :line 67, :end-line 73, :hash "-1694968006"} {:id "defn-/load-for-invasion-start!", :kind "defn-", :line 75, :end-line 77, :hash "-1386709183"} {:id "defn-/transition-load-for-invasion-to-sailing!", :kind "defn-", :line 79, :end-line 95, :hash "1566931736"} {:id "defn-/transition-load-for-invasion-to-unloading!", :kind "defn-", :line 97, :end-line 111, :hash "1312805193"} {:id "defn-/mission-handler-deps", :kind "defn-", :line 113, :end-line 137, :hash "423084880"} {:id "defn-/process-find-armies-for-invasion", :kind "defn-", :line 139, :end-line 141, :hash "-2079022115"} {:id "defn-/process-load-for-invasion-with-armies", :kind "defn-", :line 142, :end-line 145, :hash "-1658821931"} {:id "defn-/process-load-for-invasion-empty", :kind "defn-", :line 147, :end-line 149, :hash "532265959"} {:id "defn-/process-load-for-invasion", :kind "defn-", :line 150, :end-line 153, :hash "-849233059"} {:id "defn-/process-loading-mission", :kind "defn-", :line 155, :end-line 157, :hash "391544837"} {:id "defn-/process-unloading-mission", :kind "defn-", :line 159, :end-line 161, :hash "1988449558"} {:id "defn-/park-lake-transport-if-empty", :kind "defn-", :line 162, :end-line 164, :hash "77373283"} {:id "defn-/process-land-locked-mission", :kind "defn-", :line 166, :end-line 168, :hash "-1060592190"} {:id "defn-/fix-idle-mission", :kind "defn-", :line 170, :end-line 172, :hash "2095014349"} {:id "defn-/run-transport-mission", :kind "defn-", :line 174, :end-line 192, :hash "456387860"} {:id "defn-/dispatch-transport-mission", :kind "defn-", :line 194, :end-line 221, :hash "-1240962185"} {:id "defn-/maybe-handle-lake-transport", :kind "defn-", :line 223, :end-line 225, :hash "-1517017432"} {:id "def/transport-random-walk-restore-keys", :kind "def", :line 227, :end-line 237, :hash "455701777"} {:id "defn-/maybe-enter-transport-random-walk!", :kind "defn-", :line 239, :end-line 245, :hash "-1050129822"} {:id "defn-/process-transport-random-walk", :kind "defn-", :line 247, :end-line 265, :hash "341477329"} {:id "defn-/process-active-transport", :kind "defn-", :line 267, :end-line 276, :hash "2138440582"} {:id "defn-/ensure-mission-assigned!", :kind "defn-", :line 278, :end-line 282, :hash "-110514649"} {:id "defn-/check-random-walk", :kind "defn-", :line 284, :end-line 289, :hash "480835819"} {:id "defn/process-transport", :kind "defn", :line 291, :end-line 307, :hash "708236272"}]}
;; clj-mutate-manifest-end
