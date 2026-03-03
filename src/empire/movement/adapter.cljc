(ns empire.movement.adapter
  (:require [empire.application.ports :as ports]
            [empire.movement.api :as movement]))

(defrecord MovementAdapter []
  ports/MovementPort
  (movement-move-unit [_ coords target cell current-map]
    (movement/move-unit coords target cell current-map))
  (movement-get-active-unit [_ cell]
    (movement/get-active-unit cell))
  (movement-context [_ cell active-unit]
    (movement/movement-context cell active-unit))
  (movement-set-unit-movement [_ coords target extended?]
    (if extended?
      (movement/set-unit-movement coords target true)
      (movement/set-unit-movement coords target))))

(defn movement-port []
  (->MovementAdapter))
