(ns empire.units.army)

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
