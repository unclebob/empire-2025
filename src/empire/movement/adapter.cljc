(ns empire.movement.adapter
  (:require [empire.application.ports.movement :as ports]
            [empire.movement.api :as movement]))

(defrecord MovementAdapter []
  ports/MovementPort
  (movement-move-unit [_ coords target cell current-map]
    (movement/move-unit coords target cell current-map))
  (movement-get-active-unit [_ cell]
    (movement/get-active-unit cell))
  (movement-is-army-aboard-transport? [_ active-unit]
    (movement/is-army-aboard-transport? active-unit))
  (movement-is-fighter-from-airport? [_ active-unit]
    (movement/is-fighter-from-airport? active-unit))
  (movement-is-fighter-from-carrier? [_ active-unit]
    (movement/is-fighter-from-carrier? active-unit))
  (movement-context [_ cell active-unit]
    (movement/movement-context cell active-unit))
  (movement-set-unit-mode [_ coords mode]
    (movement/set-unit-mode coords mode))
  (movement-add-unit-at [_ coords unit-type owner]
    (movement/add-unit-at coords unit-type owner))
  (movement-wake-at [_ coords]
    (movement/wake-at coords))
  (movement-set-unit-movement [_ coords target extended?]
    (if extended?
      (movement/set-unit-movement coords target true)
      (movement/set-unit-movement coords target))))

(defn movement-port []
  (->MovementAdapter))
