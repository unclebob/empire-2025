(ns empire.computer.army.combat
  "Army combat helpers."
  (:require [empire.state.api :as sa]
            [empire.computer.army.movement :as movement]
            [empire.computer.army-action-resolution :as army-action-resolution]
            [empire.computer.core :as core]
            [empire.game-mechanics.visibility :as visibility]))

(defn find-adjacent-enemy
  "Finds an adjacent enemy unit or city to attack."
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)]
                       (core/attackable-target? cell)))
                   (core/get-neighbors pos)))))

(defn attack-enemy-result
  "Attack an adjacent enemy. Returns outcome data for the caller."
  [army-pos enemy-pos]
  (army-action-resolution/attack-enemy army-pos enemy-pos))

(defn attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if army died."
  [army-pos enemy-pos]
  (:position (attack-enemy-result army-pos enemy-pos)))

(defn process-attack-target
  "Moves army toward its attack-target city. Clears target if conquered or gone."
  [pos country-id]
  (let [target (get-in (sa/read-state :computer-map) (conj pos :contents :attack-target))
        comp-cell (get-in (sa/read-state :computer-map) target)]
    (if (and comp-cell
             (= :city (:type comp-cell))
             (#{:free :player} (:city-status comp-cell)))
      (movement/move-toward-objective pos target country-id)
      (do
          (sa/update-world! update-in (conj pos :contents) dissoc :attack-target)
          (visibility/sync-ai-unit-to-computer-map! pos)
          nil))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:17.012619-05:00", :module-hash "-1097092887", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-594025972"} {:id "defn/find-adjacent-enemy", :kind "defn", :line 10, :end-line 17, :hash "672814064"} {:id "defn/attack-enemy", :kind "defn", :line 19, :end-line 49, :hash "-1633234980"} {:id "defn/process-attack-target", :kind "defn", :line 51, :end-line 61, :hash "304727400"}]}
;; clj-mutate-manifest-end
