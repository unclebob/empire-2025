(ns empire.computer.army.combat
  "Army combat helpers."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.services.combat :as combat]
            [empire.computer.army.movement :as movement]
            [empire.computer.core :as core]
            [empire.computer.threat-response :as threat-response]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.computer.movement :as computer-movement]))

(defn find-adjacent-enemy
  "Finds an adjacent enemy unit or city to attack."
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)]
                       (core/attackable-target? cell)))
                   (core/get-neighbors pos)))))

(defn attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if army died."
  [army-pos enemy-pos]
  (let [game-map (sa/current-world)
        enemy-cell (get-in game-map enemy-pos)]
    (cond
      (= :city (:type enemy-cell))
      (do (debug/log-computer-event! :army-attack-city army-pos {:target enemy-pos})
          (core/attempt-conquest-computer army-pos enemy-pos)
          (when (= :computer (:city-status (get-in (sa/current-world) enemy-pos)))
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

(defn process-attack-target
  "Moves army toward its attack-target city. Clears target if conquered or gone."
  [pos country-id]
  (let [target (get-in (sa/current-world) (conj pos :contents :attack-target))
        comp-cell (get-in (sa/read-state :computer-map) target)]
    (if (and comp-cell
             (= :city (:type comp-cell))
             (#{:free :player} (:city-status comp-cell)))
      (movement/move-toward-objective pos target country-id)
      (do (sa/update-world! update-in (conj pos :contents) dissoc :attack-target)
          nil))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:17.012619-05:00", :module-hash "-1097092887", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-594025972"} {:id "defn/find-adjacent-enemy", :kind "defn", :line 10, :end-line 17, :hash "672814064"} {:id "defn/attack-enemy", :kind "defn", :line 19, :end-line 49, :hash "-1633234980"} {:id "defn/process-attack-target", :kind "defn", :line 51, :end-line 61, :hash "304727400"}]}
;; clj-mutate-manifest-end
