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

;; Production rounds required for each item
(def item-cost
  {:army 5
   :fighter 10
   :satellite 50
   :transport 30
   :patrol-boat 15
   :destroyer 20
   :submarine 20
   :carrier 30
   :battleship 40})

;; Production item display characters
(def item-chars
  {:army "A"
   :fighter "F"
   :satellite "Z"
   :transport "T"
   :patrol-boat "P"
   :destroyer "D"
   :submarine "S"
   :carrier "C"
   :battleship "B"})

;; Item hit points
(def item-hits
  {:army 1
   :fighter 1
   :satellite 1
   :transport 1
   :patrol-boat 1
   :destroyer 3
   :submarine 2
   :carrier 8
   :battleship 10})

;; Cell colors for map rendering
(def cell-colors
  {:player-city [0 255 0]      ; green for player's city
   :computer-city [255 0 0]    ; red for computer's city
   :free-city [255 255 255]    ; white for free cities
   :unexplored [0 0 0]         ; black for unexplored
   :land [139 69 19]           ; brown for land
   :sea [25 25 112]})          ; midnight blue for water

(def production-color [128 128 128])