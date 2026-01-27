(ns empire.units.battleship
  (:require [empire.units.dispatcher :as dispatcher]))

(defn can-move-to?
  "Battleships can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Battleships need attention when awake."
  [unit]
  (= (:mode unit) :awake))

(dispatcher/defunit :battleship
  {:speed 2, :cost 40, :hits 10, :strength 2,
   :display-char "B", :visibility-radius 1}
  (fn [] {})
  can-move-to?
  needs-attention?)
