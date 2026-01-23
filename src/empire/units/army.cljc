(ns empire.units.army
  (:require [empire.units.dispatcher :as dispatcher]))

;; Configuration
(def speed 1)
(def cost 5)
(def hits 1)
(def strength 1)
(def display-char "A")
(def visibility-radius 1)

(defn initial-state
  "Returns initial state fields for a new army."
  []
  {})

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

;; Register with dispatcher
(defmethod dispatcher/speed :army [_] speed)
(defmethod dispatcher/cost :army [_] cost)
(defmethod dispatcher/hits :army [_] hits)
(defmethod dispatcher/strength :army [_] strength)
(defmethod dispatcher/display-char :army [_] display-char)
(defmethod dispatcher/visibility-radius :army [_] visibility-radius)
(defmethod dispatcher/initial-state :army [_] (initial-state))
(defmethod dispatcher/can-move-to? :army [_ cell] (can-move-to? cell))
(defmethod dispatcher/needs-attention? :army [unit] (needs-attention? unit))
