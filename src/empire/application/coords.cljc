;; mutation-tested: no
(ns empire.application.coords)

(defmulti screen->cell
  "Converts screen pixel coordinates to map cell coordinates [row col].
   Note: Uses legacy formula where width is divided by rows and height by cols."
  (fn [& _] :default))
