(ns empire.computer.threat-response.major-invasion-assignment
  "Major invasion unit assignment extracted from major-invasion."
  (:require [empire.computer.army.coastal-positioning :as coastal-positioning]
            [empire.computer.threat-response.major-invasion-assignment-decisions :as decisions]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.state.api :as sa]))

(defn- assign-fighter-major-invasion!
  [ctx pos unit]
  (let [state (or (when-let [load-major-invasion-state (:load-major-invasion-state ctx)]
                    (load-major-invasion-state))
                  {})
        visible-world (or (when-let [read-runtime-state (:read-runtime-state ctx)]
                            (read-runtime-state :computer-map))
                          (sa/read-state :computer-map))
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

(defn apply-major-invasion-assignment!
  [ctx pos unit]
  (case (decisions/assignment-action {:type (:type unit)
                                      :major-invasion-ship-types (:major-invasion-ship-types ctx)})
    :fighter
      (assign-fighter-major-invasion! ctx pos unit)

    :carrier
    (assign-carrier-major-invasion! ctx pos)

    :ship
    (assign-ship-major-invasion! ctx pos)

    :transport
    ((:prepare-transport-major-invasion!-fn ctx) pos unit)

    :army
    (assign-army-invasion-embark! ctx pos unit)

    nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:49:21.138539-05:00", :module-hash "-707312276", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "486995880"} {:id "defn-/assign-fighter-major-invasion!", :kind "defn-", :line 8, :end-line 32, :hash "-539796309"} {:id "defn-/assign-carrier-major-invasion!", :kind "defn-", :line 34, :end-line 42, :hash "1752602620"} {:id "defn-/assign-ship-major-invasion!", :kind "defn-", :line 44, :end-line 47, :hash "2445993"} {:id "defn-/assign-army-invasion-embark!", :kind "defn-", :line 49, :end-line 56, :hash "672693173"} {:id "defn/apply-major-invasion-assignment!", :kind "defn", :line 58, :end-line 77, :hash "-1384849297"}]}
;; clj-mutate-manifest-end
