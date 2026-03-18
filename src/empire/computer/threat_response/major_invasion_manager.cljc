(ns empire.computer.threat-response.major-invasion-manager
  "Major invasion lifecycle coordination extracted from threat-response."
  (:require [empire.computer.threat-response.invasion-decision :as invasion-decision]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.major-invasion-manager-decisions :as decisions]
            [empire.computer.threat-response.major-invasion :as major-invasion]
            [empire.computer.threat-response.probe :as probe]))

(defn- computer-city-count
  [world]
  (count (for [x (range (count world))
               y (range (count (first world)))
               :let [cell (get-in world [x y])]
               :when (and (= :city (:type cell))
                          (= :computer (:city-status cell)))]
           [x y])))

(defn- rebuild-routing-if-needed!
  [ctx]
  (let [world ((:current-world ctx))
        state ((:load-major-invasion-state ctx))
        current-city-count (computer-city-count world)
        previous-city-count (:kamikazee-routing-city-count state)]
    (when (decisions/rebuild-routing? previous-city-count current-city-count)
      (kamikazee/rebuild-routing-graph! ctx)
      ((:update-major-invasion-state! ctx) assoc
       :kamikazee-routing-city-count current-city-count)
      true)))

(defn recompute-major-invasion-target-land!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))
        target-land (invasion-state/recompute-target-land
                     ((:read-runtime-state ctx) :computer-map)
                     (:detection-points state))
        current-target-land (:target-land-set state)
        changed? (not= current-target-land target-land)
        next-revision (if changed?
                        (inc (or (:target-land-revision state) 0))
                        (or (:target-land-revision state) 0))]
    ((:update-major-invasion-state! ctx) assoc
     :target-land-set target-land
     :target-land-revision next-revision)))

(defn- recompute-target-land!
  [ctx]
  (if-let [f (:recompute-major-invasion-target-land!-fn ctx)]
    (f)
    (recompute-major-invasion-target-land! ctx)))

(defn- recompute-sea-reachable!
  [ctx]
  (if-let [f (:recompute-sea-reachable-detection-points!-fn ctx)]
    (f)
    (major-invasion/recompute-sea-reachable-detection-points! ctx)))

(defn- mission-needs-reset?
  [unit]
  (and (= :transport (:type unit))
       (#{:invading :unloading :load-for-invasion :find-armies-for-invasion}
        (:transport-mission unit))))

(defn- clear-major-invasion-from-unit
  [unit]
  (let [base (dissoc unit :major-invasion
                     :major-invasion-target
                     :kamikazee
                     :kamikazee-targets
                     :kamikazee-route
                     :kamikazee-terminal-site
                     :kamikazee-stage
                     :kamikazee-wait-site
                     :kamikazee-hunt-resume-pos
                     :kamikazee-trail)]
    (if (= :transport (:type base))
      (let [transport (-> base
                          (dissoc :major-invasion-find-armies-round
                                  :major-invasion-skip-revision
                                  :invasion-target
                                  :invasion-path
                                  :invasion-path-origin
                                  :invasion-plan-revision
                                  :invasion-load-since))]
        (if (mission-needs-reset? transport)
          (assoc transport :transport-mission :sailing)
          transport))
      base)))

(defn- stand-down-major-invasion!
  [ctx failure-reason]
  (let [game-map ((:current-world ctx))]
    (doseq [x (range (count game-map))
            y (range (count (first game-map)))
            :let [unit (get-in game-map [x y :contents])]
            :when (and unit
                       (= :computer (:owner unit))
                       (or (:major-invasion unit)
                           (mission-needs-reset? unit)))]
      ((:update-game-map! ctx) update-in [x y :contents] clear-major-invasion-from-unit)))
  ((:update-major-invasion-state! ctx) assoc
   :active? false
   :decision :deferred
   :failure-reason failure-reason
   :next-review-round ((:next-review-round-fn ctx))
   :first-landing-round nil))

(defn- force-patrol-boat-exploration!
  [ctx]
  (doseq [pos ((:find-computer-unit-positions-fn ctx) #(= :patrol-boat (:type %)))]
    ((:update-game-map! ctx) update-in (conj pos :contents)
     #(-> %
          (assoc :patrol-mode :exploring)
          (dissoc :explore-path)))))

(declare refresh-major-invasion-assignments!)

(defn evaluate-major-invasion-start!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))
        evaluation (invasion-decision/evaluate-invasion-start
                    {:world ((:current-world ctx))
                     :computer-map ((:read-runtime-state ctx) :computer-map)
                     :detection-points (:detection-points state)
                     :computer-sea-unit-types (:computer-sea-unit-types ctx)})]
    (if (= :ready (:decision evaluation))
      (do
        (probe/log-event! :major-invasion-activated
                          {:detection-points (:detection-points state)
                           :evaluation evaluation})
        ((:update-major-invasion-state! ctx) merge
         (decisions/invasion-start-update
          {:decision (:decision evaluation)
           :sea-reachable-detection-points (:sea-reachable-detection-points evaluation)}))
        (recompute-target-land! ctx)
        (recompute-sea-reachable! ctx)
        (rebuild-routing-if-needed! ctx)
        (refresh-major-invasion-assignments! ctx))
      ((:update-major-invasion-state! ctx) merge
       (decisions/invasion-start-update
        {:decision (:decision evaluation)
         :failure-reason (:failure-reason evaluation)
         :sea-reachable-detection-points (:sea-reachable-detection-points evaluation)
         :next-review-round ((:next-review-round-fn ctx))})))))

(defn- maybe-record-major-invasion-detection!
  [ctx pos]
  (let [state ((:load-major-invasion-state ctx))
        nearby-existing? (some #(<= ((:chebyshev-distance-fn ctx) pos %) 2)
                               (:detection-points state))
        should-add? (decisions/should-record-detection?
                     {:active? (:active? state)
                      :nearby-existing? nearby-existing?})]
    (when should-add?
      ((:update-major-invasion-state! ctx)
       (fn [s]
         (-> s
             (update :detection-points conj pos)
             (assoc :started-round (or (:started-round s) ((:current-round-fn ctx)))))))
      (probe/log-event! :major-invasion-detection-recorded
                        {:pos pos
                         :existing-detection-points (:detection-points state)
                         :active? (:active? state)
                         :decision (:decision state)}))
    should-add?))

(defn handle-major-invasion-detection!
  [ctx pos]
  (when (maybe-record-major-invasion-detection! ctx pos)
    (if (:active? ((:load-major-invasion-state ctx)))
      (do
        (recompute-target-land! ctx)
        (recompute-sea-reachable! ctx)
        (refresh-major-invasion-assignments! ctx))
      (when (nil? (:decision ((:load-major-invasion-state ctx))))
        (evaluate-major-invasion-start! ctx)))))

(defn- maybe-handle-unsustainable-losses!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))
        target-land-set (:target-land-set state)]
    (when (and (:active? state)
               (seq target-land-set))
      (let [world ((:current-world ctx))
            armies-on-target (invasion-decision/invasion-armies-on-target-continent world target-land-set)]
        (when (and (nil? (:first-landing-round state))
                   (pos? armies-on-target))
          ((:update-major-invasion-state! ctx) assoc :first-landing-round ((:current-round-fn ctx))))
        (let [updated-state ((:load-major-invasion-state ctx))
              armies-in-transit (invasion-decision/armies-in-transports-to-target-continent
                                 world
                                 target-land-set)]
          (when (and (:first-landing-round updated-state)
                     (zero? armies-on-target)
                     (zero? armies-in-transit))
            (stand-down-major-invasion! ctx :unsustainable-losses)))))))

(defn- maybe-review-deferred-major-invasion!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))]
    (when (decisions/should-review-deferred?
           {:decision (:decision state)
            :failure-reason (:failure-reason state)
            :next-review-round (:next-review-round state)
            :current-round ((:current-round-fn ctx))})
      (evaluate-major-invasion-start! ctx))))

(defn refresh-major-invasion-assignments!
  [ctx]
  (when (:active? ((:load-major-invasion-state ctx)))
    (let [units ((:find-computer-unit-positions-fn ctx) (constantly true))
          world ((:current-world ctx))]
      (doseq [pos units
              :let [unit (get-in world (conj pos :contents))]
              :when (= :fighter (:type unit))]
        ((:apply-major-invasion-assignment!-fn ctx) pos unit))
      (kamikazee/refresh-army-targets! ctx)
      (doseq [pos units
              :let [unit (get-in world (conj pos :contents))]
              :when (and unit (not= :fighter (:type unit)))]
        ((:apply-major-invasion-assignment!-fn ctx) pos unit)))))

(defn rebuild-kamikazee-routing!
  [ctx]
  (when (:active? ((:load-major-invasion-state ctx)))
    (rebuild-routing-if-needed! ctx)
    (refresh-major-invasion-assignments! ctx)))

(defn- dec-threat-rounds!
  [ctx]
  (let [game-map ((:current-world ctx))]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [unit (get-in game-map [i j :contents])]
            :when (and unit
                       (= :computer (:owner unit))
                       (:threat-mission unit))]
      ((:update-game-map! ctx) update-in [i j :contents] (:dec-threat-rounds-fn ctx)))))

(defn- refresh-active-major-invasion!
  [ctx]
  (when (:active? ((:load-major-invasion-state ctx)))
    (recompute-target-land! ctx)
    (recompute-sea-reachable! ctx)
    (kamikazee/refresh-army-targets! ctx)
    (maybe-handle-unsustainable-losses! ctx)
    (when (:active? ((:load-major-invasion-state ctx)))
      (rebuild-routing-if-needed! ctx)
      (refresh-major-invasion-assignments! ctx))))

(defn- finalize-round-start!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))
        actions (decisions/round-start-actions
                 {:active? (:active? state)
                  :review-deferred? (decisions/should-review-deferred?
                                     {:decision (:decision state)
                                      :failure-reason (:failure-reason state)
                                      :next-review-round (:next-review-round state)
                                      :current-round ((:current-round-fn ctx))})
                  :failure-reason (:failure-reason state)})]
    ((:refresh-country-defense!-fn ctx))
    (when (:review-deferred? actions)
      (maybe-review-deferred-major-invasion! ctx))
    (when (:force-patrol-exploration? actions)
      (force-patrol-boat-exploration! ctx))))

(defn on-round-start!
  [ctx]
  (dec-threat-rounds! ctx)
  (when (:refresh-active? (decisions/round-start-actions
                           {:active? (:active? ((:load-major-invasion-state ctx)))
                            :review-deferred? false
                            :failure-reason (:failure-reason ((:load-major-invasion-state ctx)))}))
    (refresh-active-major-invasion! ctx))
  (finalize-round-start! ctx))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T11:27:21.706187-05:00", :module-hash "-2056063138", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-159037560"} {:id "defn-/computer-city-count", :kind "defn-", :line 9, :end-line 16, :hash "1021744181"} {:id "defn-/rebuild-routing-if-needed!", :kind "defn-", :line 18, :end-line 28, :hash "-1979926472"} {:id "defn/recompute-major-invasion-target-land!", :kind "defn", :line 30, :end-line 43, :hash "1090175681"} {:id "defn-/recompute-target-land!", :kind "defn-", :line 45, :end-line 49, :hash "204964494"} {:id "defn-/recompute-sea-reachable!", :kind "defn-", :line 51, :end-line 55, :hash "-422920731"} {:id "defn-/mission-needs-reset?", :kind "defn-", :line 57, :end-line 61, :hash "2076506354"} {:id "defn-/clear-major-invasion-from-unit", :kind "defn-", :line 63, :end-line 87, :hash "-30374017"} {:id "defn-/stand-down-major-invasion!", :kind "defn-", :line 89, :end-line 105, :hash "-913351683"} {:id "defn-/force-patrol-boat-exploration!", :kind "defn-", :line 107, :end-line 113, :hash "733963500"} {:id "form/10/declare", :kind "declare", :line 115, :end-line 115, :hash "11733860"} {:id "defn/evaluate-major-invasion-start!", :kind "defn", :line 117, :end-line 140, :hash "662760004"} {:id "defn-/maybe-record-major-invasion-detection!", :kind "defn-", :line 142, :end-line 156, :hash "700769299"} {:id "defn/handle-major-invasion-detection!", :kind "defn", :line 158, :end-line 167, :hash "1599444521"} {:id "defn-/maybe-handle-unsustainable-losses!", :kind "defn-", :line 169, :end-line 187, :hash "1348944847"} {:id "defn-/maybe-review-deferred-major-invasion!", :kind "defn-", :line 189, :end-line 197, :hash "-2100257739"} {:id "defn/refresh-major-invasion-assignments!", :kind "defn", :line 199, :end-line 212, :hash "-26363854"} {:id "defn/rebuild-kamikazee-routing!", :kind "defn", :line 214, :end-line 218, :hash "-680401485"} {:id "defn-/dec-threat-rounds!", :kind "defn-", :line 220, :end-line 229, :hash "-1156496508"} {:id "defn-/refresh-active-major-invasion!", :kind "defn-", :line 231, :end-line 240, :hash "1093038917"} {:id "defn-/finalize-round-start!", :kind "defn-", :line 242, :end-line 257, :hash "-427648184"} {:id "defn/on-round-start!", :kind "defn", :line 259, :end-line 267, :hash "-606016937"}]}
;; clj-mutate-manifest-end
