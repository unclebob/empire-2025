(ns empire.config
  (:require [clojure.set]))

(def smooth-count 10)

(def land-fraction 0.3)

(def number-of-cities 70)

(def min-city-distance 5)

;; Production items: keywords -> display strings
(def production-items->strings
  {:army "Army"
   :fighter "Fighter"
   :satellite "Satellite"
   :transport "Transport"
   :patrol-boat "Patrol Boat"
   :destroyer "Destroyer"
   :submarine "Submarine"
   :carrier "Carrier"
   :battleship "Battleship"})

;; Reverse lookup: display strings -> keywords
(def production-strings->items (clojure.set/map-invert production-items->strings))