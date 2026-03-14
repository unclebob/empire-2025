(ns empire.computer.threat-response.major-invasion-manager
  "Major invasion lifecycle coordination extracted from threat-response."
  (:require [empire.computer.threat-response.invasion-decision :as invasion-decision]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.major-invasion :as major-invasion]))

(defn recompute-major-invasion-target-land!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))
        target-land (invasion-state/recompute-target-land
                     ((:current-world ctx))
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
        ((:update-major-invasion-state! ctx) assoc
         :active? true
         :decision :ready
         :failure-reason nil
         :next-review-round nil
         :first-landing-round nil
         :sea-reachable-detection-points (:sea-reachable-detection-points evaluation))
        (recompute-target-land! ctx)
        (recompute-sea-reachable! ctx)
        (refresh-major-invasion-assignments! ctx))
      ((:update-major-invasion-state! ctx) assoc
       :active? false
       :decision :deferred
       :failure-reason (:failure-reason evaluation)
       :next-review-round ((:next-review-round-fn ctx))
       :first-landing-round nil
       :sea-reachable-detection-points (:sea-reachable-detection-points evaluation)))))

(defn- maybe-record-major-invasion-detection!
  [ctx pos]
  (let [state ((:load-major-invasion-state ctx))
        nearby-existing? (some #(<= ((:chebyshev-distance-fn ctx) pos %) 2)
                               (:detection-points state))
        should-add? (or (not (:active? state))
                        (not nearby-existing?))]
    (when should-add?
      ((:update-major-invasion-state! ctx)
       (fn [s]
         (-> s
             (update :detection-points conj pos)
             (assoc :started-round (or (:started-round s) ((:current-round-fn ctx))))))))
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
    (when (and (= :deferred (:decision state))
               (#{:no-sea-path :insufficient-resources :unsustainable-losses}
                (:failure-reason state))
               (number? (:next-review-round state))
               (>= ((:current-round-fn ctx)) (:next-review-round state)))
      (evaluate-major-invasion-start! ctx))))

(defn refresh-major-invasion-assignments!
  [ctx]
  (when (:active? ((:load-major-invasion-state ctx)))
    (let [units ((:find-computer-unit-positions-fn ctx) (constantly true))
          world ((:current-world ctx))]
      (kamikazee/rebuild-routing-graph! ctx)
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
    (kamikazee/rebuild-routing-graph! ctx)
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
      (refresh-major-invasion-assignments! ctx))))

(defn- finalize-round-start!
  [ctx]
  ((:refresh-country-defense!-fn ctx))
  (maybe-review-deferred-major-invasion! ctx)
  (when (= :no-sea-path (:failure-reason ((:load-major-invasion-state ctx))))
    (force-patrol-boat-exploration! ctx)))

(defn on-round-start!
  [ctx]
  (dec-threat-rounds! ctx)
  (refresh-active-major-invasion! ctx)
  (finalize-round-start! ctx))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T11:10:36.720785-05:00", :module-hash "1170389015", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-329409651"} {:id "defn/recompute-major-invasion-target-land!", :kind "defn", :line 8, :end-line 21, :hash "1090175681"} {:id "defn-/recompute-target-land!", :kind "defn-", :line 23, :end-line 27, :hash "204964494"} {:id "defn-/recompute-sea-reachable!", :kind "defn-", :line 29, :end-line 33, :hash "-422920731"} {:id "defn-/mission-needs-reset?", :kind "defn-", :line 35, :end-line 39, :hash "2076506354"} {:id "defn-/clear-major-invasion-from-unit", :kind "defn-", :line 41, :end-line 64, :hash "135932616"} {:id "defn-/stand-down-major-invasion!", :kind "defn-", :line 66, :end-line 82, :hash "-913351683"} {:id "defn-/force-patrol-boat-exploration!", :kind "defn-", :line 84, :end-line 90, :hash "733963500"} {:id "form/8/declare", :kind "declare", :line 92, :end-line 92, :hash "11733860"} {:id "defn/evaluate-major-invasion-start!", :kind "defn", :line 94, :end-line 120, :hash "-1948405773"} {:id "defn-/maybe-record-major-invasion-detection!", :kind "defn-", :line 122, :end-line 135, :hash "-285504441"} {:id "defn/handle-major-invasion-detection!", :kind "defn", :line 137, :end-line 146, :hash "1599444521"} {:id "defn-/maybe-handle-unsustainable-losses!", :kind "defn-", :line 148, :end-line 166, :hash "1348944847"} {:id "defn-/maybe-review-deferred-major-invasion!", :kind "defn-", :line 168, :end-line 176, :hash "599877900"} {:id "defn/refresh-major-invasion-assignments!", :kind "defn", :line 178, :end-line 192, :hash "166179315"} {:id "defn/rebuild-kamikazee-routing!", :kind "defn", :line 194, :end-line 198, :hash "-1124454123"} {:id "defn-/dec-threat-rounds!", :kind "defn-", :line 200, :end-line 209, :hash "-1156496508"} {:id "defn-/refresh-active-major-invasion!", :kind "defn-", :line 211, :end-line 219, :hash "-1516483018"} {:id "defn-/finalize-round-start!", :kind "defn-", :line 221, :end-line 226, :hash "1428949634"} {:id "defn/on-round-start!", :kind "defn", :line 228, :end-line 232, :hash "-364680258"}]}
;; clj-mutate-manifest-end
