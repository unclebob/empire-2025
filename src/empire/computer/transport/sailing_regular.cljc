(ns empire.computer.transport.sailing-regular
  (:require [empire.computer.army.assignment :as army-assignment]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.load-targeting :as load-targeting]
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
  (let [transport-id (get-in (sa/read-state :computer-map) (conj pos :contents :transport-id))]
    (reservations/release! transport-id)
    (sa/update-world! update-in (conj pos :contents)
                      #(-> %
                           (assoc :transport-mission :leave-city
                                  :load-target-cell nil
                                  :load-manifest nil
                                  :load-plan-failure nil
                                  :hold-sail-to-load-since-round nil
                                  :loading-since-round nil
                                  :sail-path [])
                           (dissoc :unload-target-city)))
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn enter-sail-to-load!
  [pos]
  (let [transport-id (get-in (sa/read-state :computer-map) (conj pos :contents :transport-id))
        _ (reservations/release! transport-id)
        computer-map (sa/read-state :computer-map)
        load-target-cell (load-targeting/choose-load-target-cell
                          pos
                          computer-map
                          {:reserved-coastal-cells (reservations/reserved-coastal-cells transport-id)
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
          (sa/update-world! update-in (conj pos :contents)
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
      (sa/update-world! assoc-in
                        (conj retreat :contents :sail-path)
                        (vec (cons pos sail-path)))
      (visibility/sync-ai-unit-to-computer-map! retreat)
      retreat)))

(defn- transport-speed
  []
  (dispatcher/speed :transport))

(defn- sync-sail-path!
  [pos sail-path]
  (sa/update-world! assoc-in
                    (conj pos :contents :sail-path)
                    (vec sail-path))
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- next-sail-step
  [previous-pos current-pos sail-path]
  (or (first sail-path)
      (when previous-pos
        (sailing-path/continue-pos (sa/read-state :computer-map) previous-pos current-pos))))

(defn- remaining-sail-path
  [sail-path]
  (if (seq sail-path) (vec (rest sail-path)) []))

(defn- sail-follow-path
  [pos sail-path maybe-unload?]
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
              (let [unloaded? (and maybe-unload?
                                   (unloading/try-opportunistic-unload next-pos))
                    transport (get-in (sa/read-state :computer-map) (conj next-pos :contents))]
                (if (or (zero? (dec moves-left))
                        unloaded?
                        (zero? (:army-count transport 0)))
                  next-pos
                  (recur next-pos current-pos path-after-step (dec moves-left) true))))
            (if moved-any?
              (do
                (sync-sail-path! current-pos remaining-path)
                current-pos)
              (sail-retreat pos sail-path))))
        (when moved-any?
          current-pos)))))

(defn- compute-and-follow-path!
  [pos path-fn maybe-unload?]
  (when-let [new-path (seq (path-fn pos))]
    (let [sail-path (vec new-path)]
      (sa/update-world! assoc-in (conj pos :contents :sail-path) sail-path)
      (visibility/sync-ai-unit-to-computer-map! pos)
      (sail-follow-path pos sail-path maybe-unload?))))

(defn- handle-launch-and-follow!
  [pos transport path-fn maybe-unload?]
  (if-let [sea-pos (launch-from-city-to-sea pos transport)]
    (compute-and-follow-path! sea-pos path-fn maybe-unload?)
    (compute-and-follow-path! pos path-fn maybe-unload?)))

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
        (sa/update-world! assoc-in (conj pos :contents :hold-sail-to-load-since-round) nil)
        (sa/update-world! assoc-in (conj pos :contents :load-plan-failure) nil)
        (sa/update-world! assoc-in (conj pos :contents :loading-since-round)
                          (or (sa/read-state :round-number) 0))
        (sa/update-world! assoc-in (conj pos :contents :sail-path) [])
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
      (sa/update-world! assoc-in (conj pos :contents :sail-path) (vec new-path))
      (visibility/sync-ai-unit-to-computer-map! pos)
      (sail-follow-path pos (vec new-path) false))))

(defn- process-sail-to-unload-mission
  [pos transport]
  (if (unloading/try-opportunistic-unload pos)
    (tc/set-transport-mission pos :unloading)
    (let [computer-map (sa/read-state :computer-map)
          city-cell? (= :city (:type (get-in computer-map pos)))
          sail-path (:sail-path transport)]
      (cond
        city-cell?
        (handle-launch-and-follow! pos transport support/compute-sail-to-unload-path true)

        (adjacent-claimed-land? pos)
        (compute-and-follow-path! pos support/compute-sail-to-unload-path true)

        (seq sail-path)
        (sail-follow-path pos sail-path true)

        :else
        (compute-and-follow-path! pos support/compute-sail-to-unload-path true)))))

(defn- process-sail-to-load-mission
  [pos transport]
  (let [transport (if (or (:load-target-cell transport)
                          (seq (:sail-path transport))
                          (contains? transport :load-manifest))
                    transport
                    (enter-sail-to-load! pos))
        mission (:transport-mission transport)
        computer-map (sa/read-state :computer-map)
        city-cell? (= :city (:type (get-in computer-map pos)))
        sail-path (:sail-path transport)
        load-target-cell (:load-target-cell transport)]
    (cond
      (= :hold-sail-to-load mission)
      nil

      city-cell?
      (process-leave-city-mission pos transport)

      (if load-target-cell
        (load-targeting/target-reached? pos load-target-cell)
        (adjacent-claimed-land? pos))
      (transition-to-loading! pos)

      (or (and (vector? (:load-manifest transport))
               (empty? (:load-manifest transport)))
          (empty? sail-path))
      (do
        (reservations/release! (:transport-id transport))
        (tc/enter-hold-sail-to-load! pos))

      (seq sail-path)
      (sail-follow-path pos sail-path false)

      :else
      (compute-and-follow-load-target-path! pos transport))))

(defn- follow-path-action
  [pos sail-path]
  (sail-follow-path pos sail-path true))

(defn process-sailing-mission
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        mission (:transport-mission transport)]
    (case mission
      :sailing (if (zero? (:army-count transport 0))
                 (process-sail-to-load-mission pos transport)
                 (process-sail-to-unload-mission pos transport))
      :leave-city (process-leave-city-mission pos transport)
      :hold-sail-to-load (process-hold-sail-to-load-mission pos transport)
      :sail-to-load (process-sail-to-load-mission pos transport)
      :sail-to-unload (process-sail-to-unload-mission pos transport)
      (when-let [sail-path (:sail-path transport)]
        (follow-path-action pos sail-path)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:01:30.75969-05:00", :module-hash "-66091184", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "913815156"} {:id "defn-/launch-from-city-to-sea", :kind "defn-", :line 10, :end-line 31, :hash "334254023"} {:id "defn-/sail-retreat", :kind "defn-", :line 33, :end-line 42, :hash "-1450208503"} {:id "defn-/sail-take-second-step", :kind "defn-", :line 44, :end-line 60, :hash "239957856"} {:id "defn-/sail-follow-path", :kind "defn-", :line 62, :end-line 70, :hash "-14709246"} {:id "defn-/compute-and-follow-sail-path!", :kind "defn-", :line 72, :end-line 77, :hash "-1373004350"} {:id "defn-/maybe-unload-or-sail!", :kind "defn-", :line 79, :end-line 89, :hash "-297549670"} {:id "defn-/handle-loaded-transport-without-path!", :kind "defn-", :line 91, :end-line 96, :hash "689794971"} {:id "defn-/loaded-no-path-action", :kind "defn-", :line 98, :end-line 109, :hash "-1817014670"} {:id "defn-/follow-path-action", :kind "defn-", :line 111, :end-line 113, :hash "-482966761"} {:id "defn-/empty-never-reload-action", :kind "defn-", :line 115, :end-line 119, :hash "196121059"} {:id "defn-/mission-handler", :kind "defn-", :line 121, :end-line 127, :hash "1693608890"} {:id "defn/process-sailing-mission", :kind "defn", :line 129, :end-line 137, :hash "792323798"}]}
;; clj-mutate-manifest-end
