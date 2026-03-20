(ns empire.computer.shared.army-action-resolution
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.movement :as computer-movement]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.game-mechanics.services.combat :as combat]
            [empire.state.api :as sa]))

(defn attack-enemy
  "Attack an adjacent enemy. Returns outcome data for the caller."
  [army-pos enemy-pos]
  (let [game-map (sa/read-state :computer-map)
        enemy-cell (get-in game-map enemy-pos)]
    (cond
      (= :city (:type enemy-cell))
      (do
        (debug/log-computer-event! :army-attack-city army-pos {:target enemy-pos})
        (action-resolution/attempt-conquest-computer army-pos enemy-pos)
        {:position (when (= :computer (:city-status (get-in (sa/read-state :computer-map) enemy-pos)))
                     enemy-pos)
         :conquered-city? (= :computer (:city-status (get-in (sa/read-state :computer-map) enemy-pos)))})

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
            (action-resolution/stamp-territory enemy-pos (:survivor result))
            (computer-movement/update-cell-visibility! army-pos :computer)
            (computer-movement/update-cell-visibility! enemy-pos :computer)
            {:position enemy-pos
             :conquered-city? false})
          (do
            (debug/log-computer-event! :army-died army-pos
                                       {:killed-by (name (:type defender)) :at enemy-pos})
            (computer-movement/update-cell-visibility! army-pos :computer)
            {:position nil
             :conquered-city? false})))

      :else {:position nil
             :conquered-city? false})))
