(ns empire.game-mechanics.containers.launch
  (:require [empire.game-mechanics.spatial.neighbors :as neighbors]))

(defn launch-steps-toward
  [[cx cy] [tx ty]]
  (let [distance (fn [[x y]]
                   (+ (Math/abs (long (- tx x)))
                      (Math/abs (long (- ty y)))))]
    (->> neighbors/neighbor-offsets
         (map (fn [[dx dy]] [(+ cx dx) (+ cy dy)]))
         (sort-by (juxt distance identity))
         vec)))
