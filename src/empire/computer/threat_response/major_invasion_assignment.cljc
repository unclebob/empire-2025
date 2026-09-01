(ns empire.computer.threat-response.major-invasion-assignment
  "Major invasion unit assignment extracted from major-invasion."
  (:require [empire.computer.army.coastal-positioning :as coastal-positioning]
            [empire.computer.threat-response.major-invasion-assignment-decisions :as decisions]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.state.api :as sa]))

(defn- ctx-major-invasion-state
  [ctx]
  (or (when-let [load-major-invasion-state (:load-major-invasion-state ctx)]
        (load-major-invasion-state))
      {}))

(defn- ctx-visible-world
  [ctx]
  (or (when-let [read-runtime-state (:read-runtime-state ctx)]
        (read-runtime-state :computer-map))
      (sa/read-state :computer-map)))

(defn- assign-fighter-major-invasion!
  [ctx pos unit]
  (let [state (ctx-major-invasion-state ctx)
        visible-world (ctx-visible-world ctx)
        targets (kamikazee/ordered-army-target-positions state
                                                         (kamikazee/current-round ctx)
                                                         visible-world)
        plan (kamikazee/plan-route state
                                   visible-world
                                   pos
                                   (:fuel unit 32))
        updates (decisions/fighter-assignment
                 {:major-target (when-let [nearest-major-target (:nearest-major-target ctx)]
                                  (nearest-major-target pos))
                  :targets targets
                  :plan plan})]
    ((:update-game-map! ctx) update-in (conj pos :contents)
     (fn [current]
       (when (:type current)
         (apply dissoc
                (merge current (dissoc updates :clear-keys))
                (:clear-keys updates)))))))

(defn- assign-carrier-major-invasion!
  [ctx pos]
  (let [support-target (kamikazee/carrier-support-target ctx pos)
        updates (decisions/carrier-assignment
                 {:support-target support-target
                  :ship-target (when-not support-target
                                 ((:nearest-major-ship-target-fn ctx) pos))})]
    ((:update-game-map! ctx) update-in (conj pos :contents)
     #(when (:type %) (merge % updates)))))

(defn- assign-ship-major-invasion!
  [ctx pos]
  (let [updates (decisions/ship-assignment ((:nearest-major-ship-target-fn ctx) pos))]
    ((:update-game-map! ctx) update-in (conj pos :contents)
     #(when (:type %) (merge % updates)))))

(defn- assign-army-invasion-embark!
  [ctx pos unit]
  (let [country-id (:country-id unit)]
    (when-not (coastal-positioning/should-sentry-on-coast? pos country-id)
      (let [target (or (:coast-target unit)
                       (coastal-positioning/find-coast-target-once pos country-id))]
        ((:update-game-map! ctx) update-in (conj pos :contents)
         #(when (:type %) (merge % (decisions/army-coast-assignment target))))))))

(defn- apply-remaining-invasion-assignment!
  [ctx pos unit action]
  (case action
    :transport ((:prepare-transport-major-invasion!-fn ctx) pos unit)
    :army (assign-army-invasion-embark! ctx pos unit)
    nil))

(defn apply-major-invasion-assignment!
  [ctx pos unit]
  (let [action (decisions/assignment-action {:type (:type unit)
                                             :major-invasion-ship-types (:major-invasion-ship-types ctx)})]
    (case action
      :fighter (assign-fighter-major-invasion! ctx pos unit)
      :carrier (assign-carrier-major-invasion! ctx pos)
      :ship (assign-ship-major-invasion! ctx pos)
      (apply-remaining-invasion-assignment! ctx pos unit action))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:05:14.989665-05:00", :module-hash "-978222493", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "486995880"} {:id "defn-/ctx-major-invasion-state", :kind "defn-", :line 8, :end-line nil, :hash "-1624058170"} {:id "defn-/ctx-visible-world", :kind "defn-", :line 14, :end-line nil, :hash "381317637"} {:id "defn-/assign-fighter-major-invasion!", :kind "defn-", :line 20, :end-line nil, :hash "1428107125"} {:id "defn-/assign-carrier-major-invasion!", :kind "defn-", :line 43, :end-line nil, :hash "-735873106"} {:id "defn-/assign-ship-major-invasion!", :kind "defn-", :line 53, :end-line nil, :hash "1156235942"} {:id "defn-/assign-army-invasion-embark!", :kind "defn-", :line 59, :end-line nil, :hash "1578551572"} {:id "defn-/apply-remaining-invasion-assignment!", :kind "defn-", :line 68, :end-line nil, :hash "1261319316"} {:id "defn/apply-major-invasion-assignment!", :kind "defn", :line 75, :end-line nil, :hash "-1714457472"}]}
;; clj-mutate-manifest-end
