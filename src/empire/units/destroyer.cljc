(ns empire.units.destroyer
  (:require [empire.units.dispatcher :as dispatcher]))

(defn can-move-to?
  "Destroyers can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Destroyers need attention when awake."
  [unit]
  (= (:mode unit) :awake))

(dispatcher/defunit :destroyer
  {:speed 2, :cost 20, :hits 3, :strength 1,
   :display-char "D", :visibility-radius 1}
  (fn [] {})
  can-move-to?
  needs-attention?)
