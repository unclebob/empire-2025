(ns empire.units.submarine
  (:require [empire.units.dispatcher :as dispatcher]))

(defn can-move-to?
  "Submarines can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Submarines need attention when awake."
  [unit]
  (= (:mode unit) :awake))

(dispatcher/defunit :submarine
  {:speed 2, :cost 20, :hits 2, :strength 3,
   :display-char "S", :visibility-radius 1}
  (fn [] {})
  can-move-to?
  needs-attention?)
