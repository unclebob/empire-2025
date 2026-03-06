;; mutation-tested: 2026-02-28
;; mutation-tested: 2026-02-28
(ns empire.movement.movement-execution
  (:require [empire.config.core :as config]
            [empire.containers.helpers :as uc]
            [empire.containers.ops :as container-ops]
            [empire.state.api :as sa]
            [empire.movement.visibility :as visibility]
            [empire.units.dispatcher :as dispatcher]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

(defn process-consumables [unit to-cell]
  (if (and unit (= (:type unit) :fighter))
    (if (= (:type to-cell) :city)
      unit
      (let [current-fuel (:fuel unit config/fighter-fuel)
            new-fuel (dec current-fuel)]
        (if (<= new-fuel -1)
          nil
          (assoc unit :fuel new-fuel))))
    unit))

(defn- fighter-landing-city? [unit to-cell]
  (and unit
       (= (:type unit) :fighter)
       (= (:type to-cell) :city)
       (= (:city-status to-cell) :player)))

(defn- fighter-landing-carrier? [unit to-cell]
  (let [to-contents (:contents to-cell)]
    (and unit
         (= (:type unit) :fighter)
         (= (:type to-contents) :carrier)
         (= (:owner to-contents) (:owner unit))
         (not (uc/full? to-contents :fighter-count (dispatcher/effective-capacity :carrier (:hits to-contents)))))))

(defn- classify-move [processed-unit to-cell _original-target _final-pos]
  (cond
    (nil? processed-unit) :unit-destroyed
    (fighter-landing-city? processed-unit to-cell) :fighter-land-at-city
    (fighter-landing-carrier? processed-unit to-cell) :fighter-land-on-carrier
    :else :normal-move))

(defn- land-fighter-at-city [to-cell _unit]
  (uc/add-unit to-cell :fighter-count))

(defn- land-fighter-on-carrier [to-cell _unit]
  (update to-cell :contents uc/add-unit :fighter-count))

(def ^:private destination-updaters
  {:unit-destroyed (fn [to-cell _unit] to-cell)
   :fighter-land-at-city land-fighter-at-city
   :fighter-land-on-carrier land-fighter-on-carrier
   :normal-move (fn [to-cell unit] (assoc to-cell :contents unit))})

(defn update-destination-cell [move-type to-cell processed-unit]
  ((destination-updaters move-type) to-cell processed-unit))

(defn do-move [from-coords final-pos cell final-unit]
  (let [from-cell (dissoc cell :contents)
        to-cell (get-in (current-world) final-pos)
        processed-unit (process-consumables final-unit to-cell)
        owner (get-in cell [:contents :owner])
        original-target (:target (:contents cell))
        move-type (classify-move processed-unit to-cell original-target final-pos)
        updated-to-cell (update-destination-cell move-type to-cell processed-unit)]
    (update-game-map! assoc-in from-coords from-cell)
    (update-game-map! assoc-in final-pos updated-to-cell)
    (visibility/update-cell-visibility from-coords owner)
    (visibility/update-cell-visibility final-pos owner)
    (when (= (:type processed-unit) :transport)
      (container-ops/load-adjacent-sentry-armies final-pos))))
