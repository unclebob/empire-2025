(ns empire.map-utils
  (:require [empire.atoms :as atoms]))

(defn get-cell
  "Returns the cell from atoms/game-map at the given coordinates."
  ([x y]
   (get-in @atoms/game-map [y x]))
  ([[x y]]
   (get-cell x y)))

(defn set-cell
  "Sets the cell in atoms/game-map at the given coordinates to the new cell value."
  ([x y cell]
   (swap! atoms/game-map assoc-in [y x] cell))
  ([[x y] cell]
   (set-cell x y cell)))