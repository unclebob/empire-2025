(ns empire.units.army
  (:require [empire.units.dispatcher :as dispatcher]))

(defn can-move-to?
  "Armies can move on land and into non-player cities."
  [cell]
  (and cell
       (or (= (:type cell) :land)
           (and (= (:type cell) :city)
                (not= (:city-status cell) :player)))))

(defn needs-attention?
  "Armies need attention when awake."
  [unit]
  (= (:mode unit) :awake))

(dispatcher/defunit :army
  {:speed 1, :cost 5, :hits 1, :strength 1,
   :display-char "A", :visibility-radius 1}
  (fn [] {})
  can-move-to?
  needs-attention?)
