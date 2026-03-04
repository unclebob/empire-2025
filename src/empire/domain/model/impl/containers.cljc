;; mutation-tested: no
(ns empire.domain.model.impl.containers
  (:require [empire.config :as config]
            [empire.domain.model.containers :as containers]))

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

(defmethod containers/wake-transport-armies :default
  [transport]
  (-> transport
      (wake-all :army-count :awake-armies)
      (assoc :mode :sentry :steps-remaining 0)
      (dissoc :reason)))

(defmethod containers/sleep-transport-armies :default
  [transport]
  (-> transport
      (sleep-all :awake-armies)
      (assoc :mode :awake)
      (dissoc :reason)))

(defmethod containers/remove-awake-transport-army :default
  [transport]
  (let [after-remove (remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (has-awake? after-remove :awake-armies))]
    (cond-> after-remove
      no-more-awake? (assoc :mode :awake)
      no-more-awake? (dissoc :reason))))

(defmethod containers/disembarked-army :default
  [owner]
  {:type :army
   :mode :awake
   :owner owner
   :hits 1
   :steps-remaining (config/unit-speed :army)})

(defmethod containers/moving-disembarked-army :default
  [owner extended-target]
  {:type :army
   :mode :moving
   :owner owner
   :hits 1
   :steps-remaining 0
   :target extended-target})

(defmethod containers/exploring-disembarked-army :default
  [owner target-coords]
  {:type :army
   :mode :explore
   :owner owner
   :hits 1
   :steps-remaining (config/unit-speed :army)
   :explore-steps config/explore-steps
   :visited #{target-coords}})

(defmethod containers/wake-carrier-fighters :default
  [carrier]
  (-> carrier
      (wake-all :fighter-count :awake-fighters)
      (assoc :mode :sentry)
      (dissoc :reason)))

(defmethod containers/sleep-carrier-fighters :default
  [carrier]
  (-> carrier
      (sleep-all :awake-fighters)
      (assoc :mode :awake)
      (dissoc :reason)))

(defmethod containers/first-step-toward :default
  [[cx cy] [tx ty]]
  (let [dx (cond (zero? (- tx cx)) 0 (pos? (- tx cx)) 1 :else -1)
        dy (cond (zero? (- ty cy)) 0 (pos? (- ty cy)) 1 :else -1)]
    [(+ cx dx) (+ cy dy)]))

(defmethod containers/launched-fighter :default
  [owner target-coords steps-remaining]
  {:type :fighter
   :mode :moving
   :owner owner
   :fuel config/fighter-fuel
   :target target-coords
   :hits 1
   :steps-remaining steps-remaining})

(defn load-methods!
  []
  true)
