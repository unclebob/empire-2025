(ns empire.computer.transport.sailing-regular
  (:require [empire.computer.army.assignment :as army-assignment]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.loading :as loading]
            [empire.computer.transport.reservations :as reservations]
            [empire.computer.transport.sailing-path :as sailing-path]
            [empire.computer.transport.sailing-support :as support]
            [empire.computer.transport.unloading :as unloading]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(defn enter-leave-city!
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        from-mission (get-in computer-map (conj pos :contents :transport-mission))
        transport-id (get-in computer-map (conj pos :contents :transport-id))]
    (reservations/release! transport-id)
    (when (tc/update-transport-contents!
           pos
           #(-> %
                (assoc :transport-mission :leave-city
                       :load-target-cell nil
                       :load-manifest nil
                       :load-plan-failure nil
                       :hold-sail-to-load-since-round nil
                       :loading-since-round nil
                       :sail-path [])
                (dissoc :unload-target-city)))
      (visibility/sync-ai-unit-to-computer-map! pos)
      (tc/log-transport-mission-transition! pos from-mission :leave-city))))

(defn enter-sail-to-load!
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        from-mission (get-in computer-map (conj pos :contents :transport-mission))
        transport-id (get-in computer-map (conj pos :contents :transport-id))
        _ (reservations/release! transport-id)
        load-target-cell (load-targeting/choose-load-target-cell
                          pos
                          computer-map
                          {:reserved-coastal-cells (reservations/reserved-coastal-cells transport-id)
                           :excluded-country-ids (disj #{(:pickup-country-id (get-in computer-map (conj pos :contents)))}
                                                       nil)
                           :reserved-army-ids (reservations/reserved-army-ids transport-id)})
        sail-path (if load-target-cell
                    (or (load-targeting/path-to-load-target pos computer-map load-target-cell)
                        [])
                    (or (support/compute-sail-to-load-path pos)
                        []))
        path-ready? (and load-target-cell
                         (or (seq sail-path)
                             (load-targeting/target-reached? pos load-target-cell)))]
    (let [manifest (vec (army-assignment/assign-returning-transport-staging-at! pos load-target-cell))
          failure (tc/load-plan-failure pos load-target-cell sail-path manifest path-ready?)]
      (if (and load-target-cell
               (seq manifest)
               path-ready?)
        (do
          (when (tc/update-transport-contents!
                 pos
                 #(-> %
                      (assoc :transport-mission :sail-to-load)
                      (assoc :load-target-cell load-target-cell
                             :load-manifest manifest
                             :load-plan-failure nil
                             :hold-sail-to-load-since-round nil
                             :loading-since-round nil
                             :sail-path (vec sail-path))
                      (dissoc :unload-target-city)))
            (reservations/reserve! transport-id load-target-cell manifest)
            (visibility/sync-ai-unit-to-computer-map! pos)
            (tc/log-transport-mission-transition! pos from-mission :sail-to-load))
          (assoc (get-in (sa/read-state :computer-map) (conj pos :contents))
                 :transport-mission :sail-to-load
                 :load-target-cell load-target-cell
                 :load-manifest manifest
                 :load-plan-failure nil
                 :sail-path (vec sail-path)))
        (do
          (tc/enter-hold-sail-to-load! pos failure)
          (get-in (sa/read-state :computer-map) (conj pos :contents)))))))

(defn- launch-from-city-to-sea
  [pos transport]
  (let [world (sa/read-state :computer-map)
        cell-type (get-in world (conj pos :type))]
    (when (= :city cell-type)
      (let [target-ref (or (:invasion-target transport)
                           (:major-invasion-target transport)
                           pos)
            options (->> (world-query/get-neighbors pos)
                         (filter (fn [n]
                                   (let [c (get-in world n)]
                                     (and c
                                          (= :sea (:type c))
                                          (nil? (:contents c))))))
                         (sort-by (fn [n]
                                    [(grid/chebyshev-distance n target-ref) n])))]
        (when-let [sea-pos (first options)]
          (when (action-resolution/move-unit-to pos sea-pos)
            (support/update-cell-visibility! pos :computer)
            (support/update-cell-visibility! sea-pos :computer)
            (visibility/sync-ai-unit-to-computer-map! sea-pos)
            sea-pos))))))

(defn- sail-retreat
  [pos sail-path]
  (let [retreat (first (tc/get-passable-sea-neighbors pos))]
      (when (action-resolution/move-unit-to pos retreat)
      (support/update-cell-visibility! pos :computer)
      (support/update-cell-visibility! retreat :computer)
      (tc/assoc-transport-field! retreat :sail-path (vec (cons pos sail-path)))
      (visibility/sync-ai-unit-to-computer-map! retreat)
      retreat)))

(defn- blocked-follow-result
  [pos]
  {:blocked? true
   :pos pos})

(defn- blocked-follow?
  [result]
  (true? (:blocked? result)))

(defn- transport-speed
  []
  (dispatcher/speed :transport))

(defn- sync-sail-path!
  [pos sail-path]
  (when (tc/assoc-transport-field! pos :sail-path (vec sail-path))
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn- next-sail-step
  [_previous-pos _current-pos sail-path]
  (first sail-path))

(defn- remaining-sail-path
  [sail-path]
  (if (seq sail-path) (vec (rest sail-path)) []))

(defn- sail-follow-path
  [pos sail-path]
  (loop [current-pos pos
         previous-pos nil
         remaining-path (vec sail-path)
         moves-left (transport-speed)
         moved-any? false]
    (if (zero? moves-left)
      (when moved-any? current-pos)
      (if-let [next-pos (next-sail-step previous-pos current-pos remaining-path)]
        (let [path-after-step (remaining-sail-path remaining-path)]
          (if (action-resolution/move-unit-to current-pos next-pos)
            (do
              (support/update-cell-visibility! current-pos :computer)
              (support/update-cell-visibility! next-pos :computer)
              (sync-sail-path! next-pos path-after-step)
              (let [transport (get-in (sa/read-state :computer-map) (conj next-pos :contents))]
                (if (or (zero? (dec moves-left))
                        (zero? (:army-count transport 0)))
                  next-pos
                  (recur next-pos current-pos path-after-step (dec moves-left) true))))
            (blocked-follow-result current-pos)))
        (when moved-any?
          current-pos)))))

(defn- compute-and-follow-path!
  [pos path-fn]
  (when-let [new-path (seq (path-fn pos))]
    (let [sail-path (vec new-path)]
      (tc/assoc-transport-field! pos :sail-path sail-path)
      (visibility/sync-ai-unit-to-computer-map! pos)
      (sail-follow-path pos sail-path))))

(defn- replan-sail-path!
  [pos path-fn]
  (if-let [new-path (seq (path-fn pos))]
    (do
      (tc/assoc-transport-field! pos :sail-path (vec new-path))
      (visibility/sync-ai-unit-to-computer-map! pos)
      pos)
    pos))

(defn- handle-launch-and-follow!
  [pos transport path-fn]
  (if-let [sea-pos (launch-from-city-to-sea pos transport)]
    (compute-and-follow-path! sea-pos path-fn)
    (compute-and-follow-path! pos path-fn)))

(defn- claimed-land?
  [cell]
  (and cell
       (or (and (= :land (:type cell))
                (some? (:country-id cell)))
           (and (= :city (:type cell))
                (= :computer (:city-status cell))))))

(defn- adjacent-claimed-land?
  [pos]
  (some (fn [n]
          (claimed-land? (get-in (sa/read-state :computer-map) n)))
        (world-query/get-neighbors pos)))

(defn- transition-to-loading!
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))]
    (if (vector? (:load-manifest transport))
      (do
        (tc/set-transport-mission pos :loading)
        (tc/assoc-transport-field! pos :hold-sail-to-load-since-round nil)
        (tc/assoc-transport-field! pos :load-plan-failure nil)
        (tc/assoc-transport-field! pos :loading-since-round
                                   (or (sa/read-state :round-number) 0))
        (tc/assoc-transport-field! pos :sail-path [])
        (visibility/sync-ai-unit-to-computer-map! pos))
      (tc/enter-hold-sail-to-load! pos))))

(defn- process-hold-sail-to-load-mission
  [pos transport]
  (when (tc/hold-sail-to-load-elapsed? transport)
    (enter-sail-to-load! pos)))

(defn- process-leave-city-mission
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)]
    (if (= :city (:type (get-in computer-map pos)))
      (when-let [sea-pos (launch-from-city-to-sea pos transport)]
        (enter-sail-to-load! sea-pos))
      (enter-sail-to-load! pos))))

(defn- compute-and-follow-load-target-path!
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)
        load-target-cell (:load-target-cell transport)
        sail-path (or (when load-target-cell
                        (load-targeting/path-to-load-target pos computer-map load-target-cell))
                      (support/compute-sail-to-load-path pos))]
    (when-let [new-path (seq sail-path)]
      (tc/assoc-transport-field! pos :sail-path (vec new-path))
      (visibility/sync-ai-unit-to-computer-map! pos)
      (sail-follow-path pos (vec new-path)))))

(defn- follow-unload-sail-path
  [pos sail-path]
  (let [result (sail-follow-path pos sail-path)]
    (cond
      (blocked-follow? result)
      (replan-sail-path! pos support/compute-sail-to-unload-path)

      (and result
           (unloading/has-nearby-unloadable-land?
            result
            (get-in (sa/read-state :computer-map) (conj result :contents))
            0))
      (tc/set-transport-mission result :unloading)

      :else result)))

(defn- process-sail-to-unload-mission
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)
        city-cell? (= :city (:type (get-in computer-map pos)))
        sail-path (:sail-path transport)]
    (cond
      (unloading/has-nearby-unloadable-land? pos transport 0)
      (tc/set-transport-mission pos :unloading)

      city-cell?
      (handle-launch-and-follow! pos transport support/compute-sail-to-unload-path)

      (seq sail-path)
      (follow-unload-sail-path pos sail-path)

      :else
      (compute-and-follow-path! pos support/compute-sail-to-unload-path))))

(defn- enter-sail-to-unload!
  [pos transport]
  (when-let [path (seq (support/compute-sail-to-unload-path pos))]
    (reservations/release! (:transport-id transport))
    (tc/set-transport-mission pos :sail-to-unload)
    (tc/assoc-transport-field! pos :load-target-cell nil)
    (tc/assoc-transport-field! pos :load-manifest nil)
    (tc/assoc-transport-field! pos :load-plan-failure nil)
    (tc/assoc-transport-field! pos :hold-sail-to-load-since-round nil)
    (tc/assoc-transport-field! pos :loading-since-round nil)
    (tc/mint-unload-event-id pos transport)
    (tc/mint-unload-country-id pos)
    (tc/assoc-transport-field! pos :sail-path (vec path))
    (visibility/sync-ai-unit-to-computer-map! pos)
    true))

(defn- ensure-sail-to-load-transport
  [pos transport]
  (if (or (:load-target-cell transport)
          (seq (:sail-path transport))
          (contains? transport :load-manifest))
    transport
    (enter-sail-to-load! pos)))

(defn- arrived-at-load-target?
  [pos transport]
  (if-let [target (:load-target-cell transport)]
    (load-targeting/target-reached? pos target)
    (adjacent-claimed-land? pos)))

(defn- sail-path-exhausted?
  [transport]
  (or (and (vector? (:load-manifest transport))
           (empty? (:load-manifest transport)))
      (empty? (:sail-path transport))))

(defn- follow-load-sail-path
  [pos sail-path load-target-cell]
  (let [computer-map (sa/read-state :computer-map)
        result (sail-follow-path pos sail-path)]
    (if (blocked-follow? result)
      (replan-sail-path! pos #(or (when load-target-cell
                                    (load-targeting/path-to-load-target % computer-map load-target-cell))
                                  (support/compute-sail-to-load-path %)))
      result)))

(defn- process-sail-to-load-mission
  [pos transport]
  (let [transport (ensure-sail-to-load-transport pos transport)
        mission (:transport-mission transport)
        city-cell? (= :city (:type (get-in (sa/read-state :computer-map) pos)))]
    (cond
      (= :hold-sail-to-load mission) nil
      city-cell? (process-leave-city-mission pos transport)
      (arrived-at-load-target? pos transport) (transition-to-loading! pos)
      (sail-path-exhausted? transport)
      (do (reservations/release! (:transport-id transport))
          (tc/enter-hold-sail-to-load! pos))
      (seq (:sail-path transport))
      (follow-load-sail-path pos (:sail-path transport) (:load-target-cell transport))
      :else
      (compute-and-follow-load-target-path! pos transport))))

(defn- follow-path-action
  [pos sail-path]
  (let [result (sail-follow-path pos sail-path)]
    (cond
      (blocked-follow? result)
      (sail-retreat pos sail-path)

      (and result
           (pos? (get-in (sa/read-state :computer-map) (conj result :contents :army-count) 0))
           (unloading/has-nearby-unloadable-land?
            result
            (get-in (sa/read-state :computer-map) (conj result :contents))
            0))
      (tc/set-transport-mission result :unloading)

      :else
      result)))

(defn- process-hold-sail-to-load-mission
  [pos transport]
  (loading/load-adjacent-armies pos)
  (let [transport' (get-in (sa/read-state :computer-map) (conj pos :contents))
        army-count' (:army-count transport' 0)]
    (cond
      (>= army-count' 6)
      (enter-sail-to-unload! pos transport')

      (tc/hold-sail-to-load-elapsed? transport')
      (enter-sail-to-load! pos))))

(defn- process-sailing-default
  [pos transport]
  (if (seq (:sail-path transport))
    (follow-path-action pos (:sail-path transport))
    (if (zero? (:army-count transport 0))
      (process-sail-to-load-mission pos transport)
      (process-sail-to-unload-mission pos transport))))

(defn process-sailing-mission
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        mission (:transport-mission transport)]
    (case mission
      :sailing (process-sailing-default pos transport)
      :leave-city (process-leave-city-mission pos transport)
      :hold-sail-to-load (process-hold-sail-to-load-mission pos transport)
      :sail-to-load (process-sail-to-load-mission pos transport)
      :sail-to-unload (process-sail-to-unload-mission pos transport)
      (when-let [sail-path (:sail-path transport)]
        (follow-path-action pos sail-path)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T20:56:26.134946-05:00", :module-hash "-25284020", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 15, :hash "-870074759"} {:id "defn/enter-leave-city!", :kind "defn", :line 17, :end-line 35, :hash "1089066290"} {:id "defn/enter-sail-to-load!", :kind "defn", :line 37, :end-line 86, :hash "1151641005"} {:id "defn-/launch-from-city-to-sea", :kind "defn-", :line 88, :end-line 109, :hash "1818022953"} {:id "defn-/sail-retreat", :kind "defn-", :line 111, :end-line 119, :hash "498662628"} {:id "defn-/blocked-follow-result", :kind "defn-", :line 121, :end-line 124, :hash "111755018"} {:id "defn-/blocked-follow?", :kind "defn-", :line 126, :end-line 128, :hash "1071352765"} {:id "defn-/transport-speed", :kind "defn-", :line 130, :end-line 132, :hash "-603549653"} {:id "defn-/sync-sail-path!", :kind "defn-", :line 134, :end-line 137, :hash "319936993"} {:id "defn-/next-sail-step", :kind "defn-", :line 139, :end-line 141, :hash "1089332052"} {:id "defn-/remaining-sail-path", :kind "defn-", :line 143, :end-line 145, :hash "-2073188554"} {:id "defn-/sail-follow-path", :kind "defn-", :line 147, :end-line 170, :hash "220348093"} {:id "defn-/compute-and-follow-path!", :kind "defn-", :line 172, :end-line 178, :hash "1587943536"} {:id "defn-/replan-sail-path!", :kind "defn-", :line 180, :end-line 187, :hash "390047002"} {:id "defn-/handle-launch-and-follow!", :kind "defn-", :line 189, :end-line 193, :hash "-2012393178"} {:id "defn-/claimed-land?", :kind "defn-", :line 195, :end-line 201, :hash "1523367167"} {:id "defn-/adjacent-claimed-land?", :kind "defn-", :line 203, :end-line 207, :hash "-1519096910"} {:id "defn-/transition-to-loading!", :kind "defn-", :line 209, :end-line 221, :hash "1110981181"} {:id "defn-/process-hold-sail-to-load-mission", :kind "defn-", :line 223, :end-line 226, :hash "1576238367"} {:id "defn-/process-leave-city-mission", :kind "defn-", :line 228, :end-line 234, :hash "1030666859"} {:id "defn-/compute-and-follow-load-target-path!", :kind "defn-", :line 236, :end-line 246, :hash "-1583508911"} {:id "defn-/follow-unload-sail-path", :kind "defn-", :line 248, :end-line 262, :hash "-1479128302"} {:id "defn-/process-sail-to-unload-mission", :kind "defn-", :line 264, :end-line 280, :hash "-261017460"} {:id "defn-/enter-sail-to-unload!", :kind "defn-", :line 282, :end-line 296, :hash "2108051663"} {:id "defn-/ensure-sail-to-load-transport", :kind "defn-", :line 298, :end-line 304, :hash "10576469"} {:id "defn-/arrived-at-load-target?", :kind "defn-", :line 306, :end-line 310, :hash "-69419407"} {:id "defn-/sail-path-exhausted?", :kind "defn-", :line 312, :end-line 316, :hash "1525548043"} {:id "defn-/follow-load-sail-path", :kind "defn-", :line 318, :end-line 326, :hash "-2101230280"} {:id "defn-/process-sail-to-load-mission", :kind "defn-", :line 328, :end-line 343, :hash "916674908"} {:id "defn-/follow-path-action", :kind "defn-", :line 345, :end-line 361, :hash "-1052665708"} {:id "defn-/process-hold-sail-to-load-mission", :kind "defn-", :line 363, :end-line 373, :hash "-1027326766"} {:id "defn-/process-sailing-default", :kind "defn-", :line 375, :end-line 381, :hash "204494069"} {:id "defn/process-sailing-mission", :kind "defn", :line 383, :end-line 394, :hash "-142050804"}]}
;; clj-mutate-manifest-end
