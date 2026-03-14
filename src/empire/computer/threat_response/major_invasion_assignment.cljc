(ns empire.computer.threat-response.major-invasion-assignment
  "Major invasion unit assignment extracted from major-invasion."
  (:require [empire.computer.army.coastal :as army-coastal]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.state.api :as sa]))

(defn- assign-fighter-major-invasion!
  [ctx pos unit]
  (let [state (or (when-let [load-major-invasion-state (:load-major-invasion-state ctx)]
                    (load-major-invasion-state))
                  {})
        world (or (when-let [current-world (:current-world ctx)]
                    (current-world))
                  (sa/current-world))
        targets (kamikazee/ordered-army-target-positions state
                                                         (kamikazee/current-round ctx)
                                                         world)
        plan (kamikazee/plan-route state
                                   world
                                   pos
                                   (:fuel unit 32))]
    ((:update-game-map! ctx) update-in (conj pos :contents)
     #(-> %
          (assoc :major-invasion true
                 :kamikazee true
                 :major-invasion-target (when-let [nearest-major-target (:nearest-major-target ctx)]
                                          (nearest-major-target pos))
                 :kamikazee-targets targets
                 :kamikazee-route (:route plan)
                 :kamikazee-terminal-site (:terminal-site plan)
                 :kamikazee-stage (if (seq (:route plan)) :route :hunt))
          (dissoc :threat-mission :threat-center :threat-radius :threat-rounds-left)))))

(defn- assign-carrier-major-invasion!
  [ctx pos]
  (if (kamikazee/carrier-support-target ctx pos)
    ((:update-game-map! ctx) update-in (conj pos :contents)
     assoc :major-invasion true
     :mode :sentry
     :major-invasion-target pos)
    ((:update-game-map! ctx) update-in (conj pos :contents)
     assoc :major-invasion true
     :major-invasion-target ((:nearest-major-ship-target-fn ctx) pos))))

(defn- assign-ship-major-invasion!
  [ctx pos]
  ((:update-game-map! ctx) update-in (conj pos :contents)
   assoc :major-invasion true
   :major-invasion-target ((:nearest-major-ship-target-fn ctx) pos)))

(defn- assign-army-invasion-embark!
  [ctx pos unit]
  (let [country-id (:country-id unit)]
    (when-not (army-coastal/should-sentry-on-coast? pos country-id)
      (let [target (or (:coast-target unit)
                       (army-coastal/find-coast-target-once pos country-id))]
        ((:update-game-map! ctx) update-in (conj pos :contents)
         #(cond-> (assoc % :mode :move-to-coast-for-invasion)
            target (assoc :coast-target target)))))))

(defn apply-major-invasion-assignment!
  [ctx pos unit]
  (let [t (:type unit)]
    (cond
      (= :fighter t)
      (assign-fighter-major-invasion! ctx pos unit)

      ((:major-invasion-ship-types ctx) t)
      (if (= :carrier t)
        (assign-carrier-major-invasion! ctx pos)
        (assign-ship-major-invasion! ctx pos))

      (= :transport t)
      ((:prepare-transport-major-invasion!-fn ctx) pos unit)

      (= :army t)
      (assign-army-invasion-embark! ctx pos unit)

      :else nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T10:35:55.303589-05:00", :module-hash "-1407093608", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "696263856"} {:id "defn-/assign-fighter-major-invasion!", :kind "defn-", :line 7, :end-line 32, :hash "1635657189"} {:id "defn-/assign-carrier-major-invasion!", :kind "defn-", :line 34, :end-line 43, :hash "-1116661532"} {:id "defn-/assign-ship-major-invasion!", :kind "defn-", :line 45, :end-line 49, :hash "212256084"} {:id "defn-/assign-army-invasion-embark!", :kind "defn-", :line 51, :end-line 59, :hash "1603379616"} {:id "defn/apply-major-invasion-assignment!", :kind "defn", :line 61, :end-line 79, :hash "-851692958"}]}
;; clj-mutate-manifest-end
