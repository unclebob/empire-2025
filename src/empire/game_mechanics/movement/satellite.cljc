(ns empire.game-mechanics.movement.satellite
  (:require [empire.game-mechanics.movement.satellite-impl :as impl]))

(defn calculate-satellite-target
  "For satellites, extends the target to the map boundary in the direction of travel."
  [unit-coords target-coords]
  (impl/calculate-satellite-target unit-coords target-coords))

(defn- boundary-type
  [pos map-height map-width]
  (impl/boundary-type pos map-height map-width))

(defn- bounce-direction
  "Returns a random direction vector pointing away from the map edge.
   Filters the 8 compass directions to those that move inward from the edge."
  [[x y] map-height map-width]
  (let [at-top? (zero? x)
        at-bottom? (= x (dec map-height))
        at-left? (zero? y)
        at-right? (= y (dec map-width))
        directions [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]
        valid (filter (fn [[dx dy]]
                        (let [nx (+ x dx) ny (+ y dy)]
                          (and (if at-top? (>= dx 0) true)
                               (if at-bottom? (<= dx 0) true)
                               (if at-left? (>= dy 0) true)
                               (if at-right? (<= dy 0) true)
                               (>= nx 0) (< nx map-height)
                               (>= ny 0) (< ny map-width))))
                      directions)]
    (when (seq valid)
      (rand-nth (vec valid)))))

(defn move-satellite
  "Moves a satellite one step toward its target.
   Computer satellites with :direction move in a fixed straight line.
   When at target (always on boundary), calculates new target on opposite boundary.
  Satellites without a target don't move - they wait for user input."
  [[x y]]
  (impl/move-satellite bounce-direction [x y]))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:29:40.795483-05:00", :module-hash "1967884930", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-1503290521"} {:id "defn/calculate-satellite-target", :kind "defn", :line 4, :end-line 7, :hash "1275272722"} {:id "defn-/boundary-type", :kind "defn-", :line 9, :end-line 11, :hash "2053793298"} {:id "defn-/bounce-direction", :kind "defn-", :line 13, :end-line 32, :hash "1823438433"} {:id "defn/move-satellite", :kind "defn", :line 34, :end-line 40, :hash "165262647"}]}
;; clj-mutate-manifest-end
