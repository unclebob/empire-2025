(ns empire.computer.army.combat
  "Army combat helpers."
  (:require [empire.state.api :as sa]
            [empire.computer.army.movement :as movement]
            [empire.computer.shared.army-action-resolution :as army-action-resolution]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.visibility :as visibility]))

(defn find-adjacent-enemy
  "Finds an adjacent enemy unit or city to attack."
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)]
                       (world-query/attackable-target? cell)))
                   (world-query/get-neighbors pos)))))

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
          (when (:type (get-in (sa/current-world) (conj pos :contents)))
            (sa/update-world! update-in (conj pos :contents) dissoc :attack-target)
            (visibility/sync-ai-unit-to-computer-map! pos))
          nil))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:27:31.466447-05:00", :module-hash "-1285913656", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1781425452"} {:id "defn/find-adjacent-enemy", :kind "defn", :line 9, :end-line 16, :hash "-469903902"} {:id "defn/attack-enemy-result", :kind "defn", :line 18, :end-line 21, :hash "-1300851376"} {:id "defn/attack-enemy", :kind "defn", :line 23, :end-line 26, :hash "1908635276"} {:id "defn/process-attack-target", :kind "defn", :line 28, :end-line 40, :hash "-349334771"}]}
;; clj-mutate-manifest-end
