(ns empire.config
  (:require [empire.units.dispatcher :as dispatcher]))

(def smooth-count 10)

(def land-fraction 0.3)

(def number-of-cities 70)

(def min-city-distance 5)

;; Naval unit types - delegate to dispatcher
(def naval-unit? dispatcher/naval-units)

;; Hostile city status (not player-owned)
(def hostile-city? #{:free :computer})

;; Production rounds required for each item - delegate to dispatcher
(defn item-cost [unit-type]
  (dispatcher/cost unit-type))

;; Production item display characters - delegate to dispatcher
(defn item-chars [unit-type]
  (dispatcher/display-char unit-type))

;; Item hit points - delegate to dispatcher
(defn item-hits [unit-type]
  (dispatcher/hits unit-type))

;; Cell colors for map rendering
(def cell-colors
  {:player-city [0 255 0]      ; green for player's city
   :computer-city [255 0 0]    ; red for computer's city
   :free-city [255 255 255]    ; white for free cities
   :unexplored [0 0 0]         ; black for unexplored
   :land [139 69 19]           ; brown for land
   :sea [0 191 255]})          ; deep sky blue for water

(def production-color [128 128 128])
(def waypoint-color [0 255 0])
(def awake-unit-color [255 255 255])
(def sleeping-unit-color [0 0 0])
(def sentry-unit-color [255 128 128])
(def explore-unit-color [144 238 144])

;; Unit-specific constants - import from unit modules via dispatcher
(def fighter-fuel 32)       ; empire.units.fighter/fuel
(def transport-capacity 6)  ; empire.units.transport/capacity
(def carrier-capacity 8)    ; empire.units.carrier/capacity
(def explore-steps 50)
(def coastline-steps 100)
(def satellite-turns 50)    ; empire.units.satellite/turns

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
   :transport-found-land "Found land!"
   :somethings-in-the-way "Something's in the way."
   :city-needs-attention "City needs attention"
   :unit-needs-attention " needs attention"
   :not-on-map "That's not on the map!"
   :returned-to-start "Returned to start."
   :hit-edge "Hit map edge."
   :blocked "Blocked."
   :steps-exhausted "Lookaround limit reached."
   :not-near-coast "Not near coast."
   :skipping-this-round "Skipping this round."})

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

;; Unit speeds (cells per turn) - delegate to dispatcher
(defn unit-speed [unit-type]
  (dispatcher/speed unit-type))

(defn color-of
  "Returns the RGB color for a cell based on its type and status."
  [cell]
  (let [terrain-type (:type cell)
        cell-color (if (= terrain-type :city)
                     (case (:city-status cell)
                       :player :player-city
                       :computer :computer-city
                       :free :free-city)
                     terrain-type)]
    (cell-colors cell-color)))

(defn mode->color
  "Returns the RGB color for a unit mode."
  [mode]
  (case mode
    :awake awake-unit-color
    :sentry sentry-unit-color
    :explore explore-unit-color
    :coastline-follow explore-unit-color
    sleeping-unit-color))


