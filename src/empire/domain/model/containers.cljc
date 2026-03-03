;; mutation-tested: no
(ns empire.domain.model.containers
  (:require [empire.config :as config]))

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

(defn wake-transport-armies
  [transport]
  (-> transport
      (wake-all :army-count :awake-armies)
      (assoc :mode :sentry :steps-remaining 0)
      (dissoc :reason)))

(defn sleep-transport-armies
  [transport]
  (-> transport
      (sleep-all :awake-armies)
      (assoc :mode :awake)
      (dissoc :reason)))

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
   :steps-remaining (config/unit-speed :army)})

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
   :steps-remaining (config/unit-speed :army)
   :explore-steps config/explore-steps
   :visited #{target-coords}})

(defn wake-carrier-fighters
  [carrier]
  (-> carrier
      (wake-all :fighter-count :awake-fighters)
      (assoc :mode :sentry)
      (dissoc :reason)))

(defn sleep-carrier-fighters
  [carrier]
  (-> carrier
      (sleep-all :awake-fighters)
      (assoc :mode :awake)
      (dissoc :reason)))

(defn first-step-toward
  [[cx cy] [tx ty]]
  (let [dx (cond (zero? (- tx cx)) 0 (pos? (- tx cx)) 1 :else -1)
        dy (cond (zero? (- ty cy)) 0 (pos? (- ty cy)) 1 :else -1)]
    [(+ cx dx) (+ cy dy)]))

(defn launched-fighter
  [owner target-coords steps-remaining]
  {:type :fighter
   :mode :moving
   :owner owner
   :fuel config/fighter-fuel
   :target target-coords
   :hits 1
   :steps-remaining steps-remaining})
