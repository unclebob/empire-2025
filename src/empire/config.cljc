(ns empire.config
  (:require [empire.units.dispatcher :as dispatcher]))

;; Default map size [cols rows]
(def default-map-size [100 60])

;; Cell dimensions (pixels) — derived from Courier New 18pt font metrics
(def cell-size [11 16])   ;; [width height] in pixels per map cell

;; Font for cell-size calculation and message area text
(def text-font-name "Courier New")
(def text-font-size 18)

;; Font for unit characters and production indicators within cells
(def cell-char-font-name "CourierNewPS-BoldMT")
(def cell-char-font-size 12)

;; Layout of text area below the map
(def text-area-rows 3)
(def text-area-gap 7)

;; Pixel offsets for characters drawn inside map cells
(def cell-char-x-offset 2)
(def cell-char-y-offset 12)

;; Error message display duration (milliseconds)
(def error-message-duration 10000)

;; Message area pixel offsets (relative to text-area top)
(def msg-left-padding 10)
(def msg-line-1-y 10)
(def msg-line-2-y 26)
(def msg-line-3-y 42)
(def msg-separator-offset 4)

;; Display area width fractions for three-region layout
(def game-info-width-fraction 0.375)    ;; Game Info (left)
(def debug-width-fraction 0.25)         ;; Debug (center)
(def game-status-width-fraction 0.375)  ;; Game Status (right)

(def smooth-count 10)

(def land-fraction 0.3)

(def number-of-cities 70)

(defn compute-size-constants
  "Computes constants derived from the map size [cols rows].
   Returns a map to be stored in atoms/map-size-constants."
  [cols rows]
  (let [area (* cols rows)
        ref-area 6000]
    {:cols cols
     :rows rows
     :number-of-cities (max 10 (int (* 70 (/ area ref-area))))}))

(def min-city-distance 5)

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

(def land-colors
  [[139 69 19]    ; saddle brown (default)
   [160 82 45]    ; sienna
   [120 66 18]    ; dark brown
   [180 100 50]   ; peru
   [101 67 33]    ; dark wood
   [170 120 60]   ; light wood
   [150 75 0]     ; brown orange
   [133 94 66]])  ; french beige

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
(def max-sidesteps 10)

(def carrier-spacing 22)  ;; 70% of fighter-fuel (0.7 * 32 = 22.4, rounded down)
(def bingo-fuel-divisor 4)
(def max-placement-attempts 1000)
(def min-surrounding-land 10)

;; --- Computer production thresholds ---

;; Per-country unit caps
(def armies-before-transport 6)        ;; army count before country starts building transports
(def max-patrol-boats-per-country 4)   ;; patrol boats per country

;; Global production gates for expensive units
(def carrier-city-threshold 10)        ;; computer needs >N cities before building carriers
(def max-live-carriers 8)              ;; global fleet cap on live carriers
(def max-carrier-producers 2)          ;; max cities simultaneously producing carriers
(def satellite-city-threshold 15)      ;; computer needs >N cities before building satellites
(def max-satellites 1)                 ;; global cap on live satellites

;; Game loop speed
(def advances-per-frame 10)            ;; max advance-game calls per frame

;; Messages and reasons
(def messages
  {:army-found-city "Army found a city!"
   :fighter-bingo "Bingo! Refuel?"
   :fighter-out-of-fuel "Fighter out of fuel."
   :fighter-landed-and-refueled "Landed and refueled."
   :fighter-over-defended-city "Fighter about to fly over defended city."
   :fighter-shot-down "Incoming anti-aircraft fire!"
   :fighter-destroyed-by-city "Fighter destroyed by city defenses."
   :fighter-crashed "Fighter crashed."
   :failed-to-conquer "Failed to conquer city."
   :conquest-failed "Conquest Failed"
   :cant-move-into-water "Can't move into water."
   :cant-move-into-city "Can't move into city."
   :ships-cant-drive-on-land "Ships don't drive on land."
   :transport-at-beach "At beach."
   :transport-found-land "Found land!"
   :found-a-bay "Found a bay!"
   :somethings-in-the-way "Something's in the way."
   :enemy-spotted "Enemy spotted."
   :city-needs-attention "City needs attention"
   :unit-needs-attention " needs attention"
   :not-on-map "That's not on the map!"
   :returned-to-start "Returned to start."
   :hit-edge "Hit map edge."
   :blocked "Blocked."
   :steps-exhausted "Lookaround limit reached."
   :not-near-coast "Not near coast."
   :skipping-this-round "Skipping this round."
   ;; Turn messages (displayed in Turn line via set-turn-message)
   :marching-orders-set "Marching orders set to %d,%d"
   :marching-orders-lookaround "Marching orders set to lookaround"
   :flight-path-set "Flight path set to %d,%d"
   :waypoint-placed "Waypoint placed at %d,%d"
   :waypoint-removed "Waypoint removed from %d,%d"
   :waypoint-orders-set "Waypoint orders set to %d,%d"
   :docked-for-repair "%s docked for repair."
   :combat-result "%s. %s destroyed."
   :destination "Dest: %d,%d"
   ;; Error messages (displayed in Error line via set-error-message)
   :coastal-city-required "Must be coastal city to produce %s."
   ;; Attention messages
   :fighter-airport-attention "Fighter needs attention - Landed and refueled.%s"
   :fighter-carrier-attention "Fighter needs attention - aboard carrier (%d fighters)%s"
   :army-transport-attention "Army needs attention - aboard transport (%d armies) - At beach."
   :damaged-unit-attention "Damaged %s needs attention%s%s%s"
   :unit-attention "%s needs attention%s%s%s"})

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
  (let [terrain-type (:type cell)]
    (if (= terrain-type :city)
      (cell-colors (case (:city-status cell)
                     :player :player-city
                     :computer :computer-city
                     :free :free-city))
      (if (and (= terrain-type :land) (:country-id cell))
        (nth land-colors (mod (:country-id cell) (count land-colors)))
        (cell-colors terrain-type)))))

(defn mode->color
  "Returns the RGB color for a unit mode."
  [mode]
  (case mode
    :awake awake-unit-color
    :sentry sentry-unit-color
    :explore explore-unit-color
    :coastline-follow explore-unit-color
    sleeping-unit-color))

(defn unit->color
  "Returns the RGB color for a unit based on owner, type, mission, and mode.
   Computer armies are always white."
  [unit]
  (cond
    (and (= :computer (:owner unit))
         (= :army (:type unit)))
    awake-unit-color

    (= :loading (:mission unit))
    sleeping-unit-color

    :else
    (mode->color (:mode unit))))


