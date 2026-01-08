(ns empire.config
  (:require [clojure.set]))

(def smooth-count 10)

(def land-fraction 0.3)

(def number-of-cities 70)

(def min-city-distance 5)

(def round-delay 1000)

;; Menu items: keywords -> display strings
(def menu-items->strings
  {:army "Army"
   :fighter "Fighter"
   :satellite "Satellite"
   :transport "Transport"
   :patrol-boat "Patrol Boat"
   :destroyer "Destroyer"
   :submarine "Submarine"
   :carrier "Carrier"
   :battleship "Battleship"
   :explore "Explore"
   :sentry "Sentry"})

(def menu-header->string{:production "Set Production"
                          :unit "Unit Command"})

;; Reverse lookup: display strings -> keywords
(def menu-strings->items (clojure.set/map-invert menu-items->strings))

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
   :sea [0 191 255]})          ; deep sky blue for water

(def production-color [128 128 128])
(def awake-unit-color [255 255 255])
(def sleeping-unit-color [0 0 0])

(def fighter-fuel 32)

;; Messages and reasons
(def messages
  {:army-found-city "Army found a city!"
   :fighter-bingo "Bingo! Refuel?"
   :fighter-out-of-fuel "Fighter out of fuel."
   :fighter-landed-and-refueled "Landed and refueled."
   :fighter-over-defended-city "Fighter about to fly over defended city."
   :fighter-shot-down "Incoming anti-aircraft fire!"
   :fighter-destroyed-by-city "Fighter destroyed by city defenses."
   :failed-to-conquer "Failed to conquer city."
   :conquest-failed "Conquest Failed"
   :cant-move-into-water "Can't move into water."
   :cant-move-into-city "Can't move into city."
   :somethings-in-the-way "Something's in the way."
   :city-needs-attention "City needs attention"
   :unit-needs-attention " needs attention"
   :not-on-map "That's not on the map!"})

;; Key to movement direction mapping [dx dy]
(def key->direction
  {:u [-1 -1]   ; northwest
   :i [0 -1]    ; north
   :o [1 -1]    ; northeast
   :j [-1 0]    ; west
   :l [1 0]     ; east
   :m [-1 1]    ; southwest
   (keyword ",") [0 1]     ; south
   :. [1 1]})   ; southeast

;; Shifted keys for extended movement (to map edge)
(def key->extended-direction
  {:U [-1 -1]   ; far northwest
   :I [0 -1]    ; far north
   :O [1 -1]    ; far northeast
   :J [-1 0]    ; far west
   :L [1 0]     ; far east
   :M [-1 1]    ; far southwest
   :< [0 1]     ; far south
   :> [1 1]})   ; far southeast

;; Key to production item mapping
(def key->production-item
  {:a :army
   :f :fighter
   :z :satellite
   :t :transport
   :p :patrol-boat
   :d :destroyer
   :s :submarine
   :c :carrier
   :b :battleship})

;; Unit speeds (cells per turn)
(def unit-speed
  {:army 1
   :fighter 8
   :satellite 10
   :transport 2
   :patrol-boat 4
   :destroyer 2
   :submarine 2
   :carrier 2
   :battleship 2})