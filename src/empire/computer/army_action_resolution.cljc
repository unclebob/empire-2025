(ns empire.computer.army-action-resolution
  (:require [empire.computer.core :as core]
            [empire.computer.movement :as computer-movement]
            [empire.computer.threat-response :as threat-response]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.game-mechanics.services.combat :as combat]
            [empire.state.api :as sa]))

(defn attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if army died."
  [army-pos enemy-pos]
  (let [game-map (sa/read-state :computer-map)
        enemy-cell (get-in game-map enemy-pos)]
    (cond
      (= :city (:type enemy-cell))
      (do
        (debug/log-computer-event! :army-attack-city army-pos {:target enemy-pos})
        (core/attempt-conquest-computer army-pos enemy-pos)
        (when (= :computer (:city-status (get-in (sa/read-state :computer-map) enemy-pos)))
          (threat-response/rebuild-kamikazee-routing!)))

      (:contents enemy-cell)
      (let [attacker (get-in game-map (conj army-pos :contents))
            defender (:contents enemy-cell)
            result (combat/resolve-combat attacker defender)]
        (sa/update-world! update-in army-pos dissoc :contents)
        (if (= :attacker (:winner result))
          (do
            (debug/log-computer-event! :army-kill army-pos
                                       {:to enemy-pos :killed (name (:type defender))})
            (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
            (core/stamp-territory enemy-pos (:survivor result))
            (computer-movement/update-cell-visibility! army-pos :computer)
            (computer-movement/update-cell-visibility! enemy-pos :computer)
            enemy-pos)
          (do
            (debug/log-computer-event! :army-died army-pos
                                       {:killed-by (name (:type defender)) :at enemy-pos})
            (computer-movement/update-cell-visibility! army-pos :computer)
            nil)))

      :else nil)))
