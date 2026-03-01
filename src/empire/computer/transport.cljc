;; mutation-tested: 2026-02-28
(ns empire.computer.transport
  "Computer transport module — facade delegating to sub-modules.
   Loading: coastal crawl, auto-load adjacent armies, sail when loaded
   Sailing: follow BFS path to unexplored coast, opportunistic unload
   Unloading: coast-crawl while dropping armies on empty land"
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-loading :as loading]
            [empire.computer.transport-sailing :as sailing]
            [empire.computer.transport-targeting :as targeting]
            [empire.computer.transport-unloading :as unloading]
            [empire.computer.threat-response :as threat-response]
            [empire.debug :as debug]
            [empire.movement.visibility :as visibility]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  (let [store (runtime-state/runtime-state-store)]
    (ports/read-runtime-state store k)))

(defn- write-runtime-state!
  [k v]
  (let [store (runtime-state/runtime-state-store)]
    (ports/write-runtime-state! store k v)))

;; --- Re-exports for backward compatibility ---

(def find-unload-target targeting/find-unload-target)
(def unload-armies unloading/unload-armies)

;; --- Facade functions (cross-cutting, depend on multiple sub-modules) ---

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
  (when-not (read-runtime-state :transport-fully-loaded?)
    (write-runtime-state! :transport-fully-loaded? true))
  (tc/mint-unload-country-id pos)
  (tc/record-pickup-continent-pos pos transport)
  (when-let [path (sailing/compute-sail-path pos)]
    (update-game-map! assoc-in
                      (conj pos :contents :sail-path) path)))

(defn- transition-to-loading
  "Switch an empty transport to loading mode and find next pickup continent."
  [pos]
  (tc/set-transport-mission pos :loading)
  (update-game-map! update-in (conj pos :contents) dissoc :unload-target-city)
  (let [current-continent (when-let [lp (tc/find-adjacent-land-pos pos)]
                            (land-objectives/flood-fill-continent lp))
        next-pickup (targeting/find-next-pickup-continent-pos pos current-continent)]
    (update-game-map! assoc-in
                      (conj pos :contents :pickup-continent-pos) next-pickup)))

(defn- loading-crawl-move
  [pos]
  (let [move-one (fn [p]
                   (let [t (get-in (current-world) (conj p :contents))
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
      (update-game-map! assoc-in (conj pos :contents :pickup-continent-pos) new-pcp)
      (update-game-map! assoc-in (conj pos :contents :loading-since)
                        (or (read-runtime-state :round-number) 0))
      (loading-crawl-move pos))))

(defn- process-loading-mission
  [pos]
  (loading/load-adjacent-armies pos)
  (loading/clear-pickup-continent-if-arrived pos)
  (let [transport' (get-in (current-world) (conj pos :contents))
        army-count' (:army-count transport' 0)]
    (cond
      (loading/should-start-sailing? pos transport' army-count')
      (start-sailing pos transport')

      (loading/loading-stale? transport')
      (handle-stale-loading pos transport' army-count')

      :else
      (loading-crawl-move pos))))

(defn- process-unloading-mission
  [pos army-count]
  (if (zero? army-count)
    (transition-to-loading pos)
    (let [transport (get-in (current-world) (conj pos :contents))]
      (if (unloading/has-nearby-unloadable-land? pos transport 5)
        (if-let [pos1 (unloading/unloading-crawl-move pos)]
          (or (unloading/unloading-crawl-move pos1) pos1)
          (start-sailing pos transport))
        (start-sailing pos transport)))))

(defn- fix-idle-mission
  [pos mission]
  (when (or (nil? mission) (= :idle mission))
    (tc/set-transport-mission pos :loading)))

(defn- dispatch-transport-mission
  [pos transport]
  (let [army-count (:army-count transport 0)
        mission (:transport-mission transport)]
    (fix-idle-mission pos mission)
    (let [current-mission (or mission :loading)]
      (debug/log-computer-event! :transport-process pos
                                 {:mission current-mission :armies army-count
                                  :pcp (:pickup-continent-pos transport)})
      (cond
        (and (targeting/should-try-opportunistic-unload? army-count current-mission)
             (unloading/try-opportunistic-unload pos))
        true

        (= current-mission :invading)
        (sailing/process-invading-mission pos)

        (= current-mission :unloading)
        (process-unloading-mission pos army-count)

        (= current-mission :sailing)
        (sailing/process-sailing-mission pos)

        (= current-mission :loading)
        (process-loading-mission pos)

        :else nil))))

(defn process-transport
  "Processes a transport unit using simplified 3-state mission flow.
   Returns nil after processing — transports only move once per round."
  [pos]
  (let [transport (:contents (get-in (current-world) pos))]
    (when (and transport
               (= :computer (:owner transport))
               (= :transport (:type transport)))
      (threat-response/prepare-transport! pos)
      (dispatch-transport-mission pos (:contents (get-in (current-world) pos)))))
  nil)
