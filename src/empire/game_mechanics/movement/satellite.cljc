(ns empire.game-mechanics.movement.satellite
  (:require [empire.game-mechanics.movement.satellite-impl :as impl]))

(defn calculate-satellite-target
  "For satellites, extends the target to the map boundary in the direction of travel."
  [unit-coords target-coords]
  (impl/calculate-satellite-target unit-coords target-coords))

(defn- boundary-type
  [pos map-height map-width]
  (impl/boundary-type pos map-height map-width))

(defn- inward-bounce-step?
  [[x y] [dx dy] at-top? at-bottom? at-left? at-right? map-height map-width]
  (let [nx (+ x dx)
        ny (+ y dy)]
    (and (or (not at-top?) (>= dx 0))
         (or (not at-bottom?) (<= dx 0))
         (or (not at-left?) (>= dy 0))
         (or (not at-right?) (<= dy 0))
         (>= nx 0) (< nx map-height)
         (>= ny 0) (< ny map-width))))

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
                        (inward-bounce-step? [x y] [dx dy]
                                             at-top? at-bottom? at-left? at-right?
                                             map-height map-width))
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
;; {:version 1, :tested-at "2026-09-01T15:37:13.040272-05:00", :module-hash "151569377", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-1503290521"} {:id "defn/calculate-satellite-target", :kind "defn", :line 4, :end-line nil, :hash "1275272722"} {:id "defn-/boundary-type", :kind "defn-", :line 9, :end-line nil, :hash "2053793298"} {:id "defn-/inward-bounce-step?", :kind "defn-", :line 13, :end-line nil, :hash "1291153051"} {:id "defn-/bounce-direction", :kind "defn-", :line 24, :end-line nil, :hash "-1443231634"} {:id "defn/move-satellite", :kind "defn", :line 41, :end-line nil, :hash "165262647"}]}
;; clj-mutate-manifest-end
