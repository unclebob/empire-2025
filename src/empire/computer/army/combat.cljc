;; mutation-tested: 2026-03-02
(ns empire.computer.army.combat
  "Army combat helpers."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.combat :as combat]
            [empire.computer.army.movement :as movement]
            [empire.computer.core :as core]
            [empire.debug :as debug]
            [empire.movement.visibility :as visibility]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn find-adjacent-enemy
  "Finds an adjacent enemy unit or city to attack."
  [pos]
  (let [game-map (current-world)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)]
                       (core/attackable-target? cell)))
                   (core/get-neighbors pos)))))

(defn attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if army died."
  [army-pos enemy-pos]
  (let [game-map (current-world)
        enemy-cell (get-in game-map enemy-pos)]
    (cond
      (= :city (:type enemy-cell))
      (do (debug/log-computer-event! :army-attack-city army-pos {:target enemy-pos})
          (core/attempt-conquest-computer army-pos enemy-pos))

      (:contents enemy-cell)
      (let [attacker (get-in game-map (conj army-pos :contents))
            defender (:contents enemy-cell)
            result (combat/resolve-combat attacker defender)]
        (update-game-map! update-in army-pos dissoc :contents)
        (if (= :attacker (:winner result))
          (do
            (debug/log-computer-event! :army-kill army-pos
                                       {:to enemy-pos :killed (name (:type defender))})
            (update-game-map! assoc-in (conj enemy-pos :contents) (:survivor result))
            (core/stamp-territory enemy-pos (:survivor result))
            (visibility/update-cell-visibility army-pos :computer)
            (visibility/update-cell-visibility enemy-pos :computer)
            enemy-pos)
          (do
            (debug/log-computer-event! :army-died army-pos
                                       {:killed-by (name (:type defender)) :at enemy-pos})
            (visibility/update-cell-visibility army-pos :computer)
            nil)))

      :else nil)))

(defn process-attack-target
  "Moves army toward its attack-target city. Clears target if conquered or gone."
  [pos country-id]
  (let [target (get-in (current-world) (conj pos :contents :attack-target))
        comp-cell (get-in (read-runtime-state :computer-map) target)]
    (if (and comp-cell
             (= :city (:type comp-cell))
             (#{:free :player} (:city-status comp-cell)))
      (movement/move-toward-objective pos target country-id)
      (do (update-game-map! update-in (conj pos :contents) dissoc :attack-target)
          nil))))
