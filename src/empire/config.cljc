(ns empire.config)

(def smooth-count 10)

(def land-fraction 0.3)

(def number-of-cities 70)

(def min-city-distance 5)

;; Naval unit types (require coastal cities to produce, can only travel on water)
(def naval-unit? #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

;; Hostile city status (not player-owned)
(def hostile-city? #{:free :computer})

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
(def sentry-unit-color [255 128 128])
(def explore-unit-color [144 238 144])

(def fighter-fuel 32)

(def transport-capacity 6)
(def carrier-capacity 8)

(def explore-steps 50)

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
   :ships-cant-drive-on-land "Ships don't drive on land."
   :transport-at-beach "At beach."
   :somethings-in-the-way "Something's in the way."
   :city-needs-attention "City needs attention"
   :unit-needs-attention " needs attention"
   :not-on-map "That's not on the map!"})

;; Key to movement direction mapping [dx dy]
(def key->direction
  {:q [-1 -1]   ; northwest
   :w [0 -1]    ; north
   :e [1 -1]    ; northeast
   :a [-1 0]    ; west
   :d [1 0]     ; east
   :z [-1 1]    ; southwest
   :x [0 1]     ; south
   :c [1 1]})   ; southeast

;; Shifted keys for extended movement (to map edge)
(def key->extended-direction
  {:Q [-1 -1]   ; far northwest
   :W [0 -1]    ; far north
   :E [1 -1]    ; far northeast
   :A [-1 0]    ; far west
   :D [1 0]     ; far east
   :Z [-1 1]    ; far southwest
   :X [0 1]     ; far south
   :C [1 1]})   ; far southeast

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