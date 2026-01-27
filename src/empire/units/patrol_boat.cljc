(ns empire.units.patrol-boat
  (:require [empire.units.dispatcher :as dispatcher]))

(defn can-move-to?
  "Patrol boats can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Patrol boats need attention when awake."
  [unit]
  (= (:mode unit) :awake))

(dispatcher/defunit :patrol-boat
  {:speed 4, :cost 15, :hits 1, :strength 1,
   :display-char "P", :visibility-radius 1}
  (fn [] {})
  can-move-to?
  needs-attention?)
