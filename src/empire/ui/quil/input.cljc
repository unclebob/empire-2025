(ns empire.ui.quil.input
  (:require [empire.movement.map-utils :as map-utils]
            [empire.ui.util.input.dispatch :as dispatch]
            [quil.core :as q]))

(defn mouse->cell []
  (let [x (q/mouse-x) y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (map-utils/determine-cell-coordinates x y))))

(defn key-down [k]
  (dispatch/dispatch-key k (mouse->cell)))
