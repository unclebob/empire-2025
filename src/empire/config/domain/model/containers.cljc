;; mutation-tested: no
(ns empire.config.domain.model.containers
  (:require [empire.config.domain.core.unit-config :as unit-config]))

(defn- wake-all
  [entity count-key awake-key]
  (assoc entity awake-key (get entity count-key 0)))

(defn- sleep-all
  [entity awake-key]
  (assoc entity awake-key 0))

(defn- remove-awake-unit
  [entity count-key awake-key]
  (-> entity
      (update count-key (fnil dec 0))
      (update awake-key (fnil dec 0))))

(defn- has-awake?
  [entity awake-key]
  (pos? (get entity awake-key 0)))

(defn- wake-contained-units
  [entity count-key awake-key extra-fields]
  (-> entity
      (wake-all count-key awake-key)
      (merge extra-fields)
      (dissoc :reason)))

(defn- sleep-contained-units
  [entity awake-key]
  (-> entity
      (sleep-all awake-key)
      (assoc :mode :awake)
      (dissoc :reason)))

(defn wake-transport-armies
  [transport]
  (wake-contained-units transport :army-count :awake-armies
                        {:mode :sentry :steps-remaining 0}))

(defn sleep-transport-armies
  [transport]
  (sleep-contained-units transport :awake-armies))

(defn remove-awake-transport-army
  [transport]
  (let [after-remove (remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (has-awake? after-remove :awake-armies))]
    (cond-> after-remove
      no-more-awake? (assoc :mode :awake)
      no-more-awake? (dissoc :reason))))

(defn disembarked-army
  [owner]
  {:type :army
   :mode :awake
   :owner owner
   :hits 1
   :steps-remaining unit-config/army-speed})

(defn moving-disembarked-army
  [owner extended-target]
  {:type :army
   :mode :moving
   :owner owner
   :hits 1
   :steps-remaining 0
   :target extended-target})

(defn exploring-disembarked-army
  [owner target-coords]
  {:type :army
   :mode :explore
   :owner owner
   :hits 1
   :steps-remaining unit-config/army-speed
   :explore-steps unit-config/explore-steps
   :visited #{target-coords}})

(defn wake-carrier-fighters
  [carrier]
  (wake-contained-units carrier :fighter-count :awake-fighters {:mode :sentry}))

(defn sleep-carrier-fighters
  [carrier]
  (sleep-contained-units carrier :awake-fighters))

(defn- step-delta
  [from to]
  (cond
    (zero? (- to from)) 0
    (pos? (- to from)) 1
    :else -1))

(defn first-step-toward
  [[cx cy] [tx ty]]
  [(+ cx (step-delta cx tx)) (+ cy (step-delta cy ty))])

(defn launched-fighter
  [owner target-coords steps-remaining]
  {:type :fighter
   :mode :moving
   :owner owner
   :fuel unit-config/fighter-fuel
   :target target-coords
   :hits 1
   :steps-remaining steps-remaining})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:53:58.33421-05:00", :module-hash "2006441365", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line nil, :hash "889907958"} {:id "defn-/wake-all", :kind "defn-", :line 5, :end-line nil, :hash "-1741431791"} {:id "defn-/sleep-all", :kind "defn-", :line 9, :end-line nil, :hash "-10660096"} {:id "defn-/remove-awake-unit", :kind "defn-", :line 13, :end-line nil, :hash "2054284563"} {:id "defn-/has-awake?", :kind "defn-", :line 19, :end-line nil, :hash "-604107264"} {:id "defn-/wake-contained-units", :kind "defn-", :line 23, :end-line nil, :hash "1432206982"} {:id "defn-/sleep-contained-units", :kind "defn-", :line 30, :end-line nil, :hash "-1697356290"} {:id "defn/wake-transport-armies", :kind "defn", :line 37, :end-line nil, :hash "1100762019"} {:id "defn/sleep-transport-armies", :kind "defn", :line 42, :end-line nil, :hash "303328654"} {:id "defn/remove-awake-transport-army", :kind "defn", :line 46, :end-line nil, :hash "172609969"} {:id "defn/disembarked-army", :kind "defn", :line 54, :end-line nil, :hash "-1289144351"} {:id "defn/moving-disembarked-army", :kind "defn", :line 62, :end-line nil, :hash "-389344812"} {:id "defn/exploring-disembarked-army", :kind "defn", :line 71, :end-line nil, :hash "-884342201"} {:id "defn/wake-carrier-fighters", :kind "defn", :line 81, :end-line nil, :hash "2057897789"} {:id "defn/sleep-carrier-fighters", :kind "defn", :line 85, :end-line nil, :hash "1136274136"} {:id "defn-/step-delta", :kind "defn-", :line 89, :end-line nil, :hash "3083924"} {:id "defn/first-step-toward", :kind "defn", :line 96, :end-line nil, :hash "-1558959883"} {:id "defn/launched-fighter", :kind "defn", :line 100, :end-line nil, :hash "-2087120874"}]}
;; clj-mutate-manifest-end
