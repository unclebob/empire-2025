(ns empire.config.rendering)

(def cell-size [11 16])
(def text-font-name "Courier New")
(def text-font-size 18)
(def cell-char-font-name "CourierNewPS-BoldMT")
(def cell-char-font-size 12)
(def text-area-rows 3)
(def text-area-gap 7)
(def cell-char-x-offset 2)
(def cell-char-y-offset 12)
(def msg-left-padding 10)
(def msg-line-1-y 10)
(def msg-line-2-y 26)
(def msg-line-3-y 42)
(def msg-separator-offset 4)
(def game-info-width-fraction 0.375)
(def debug-width-fraction 0.25)
(def game-status-width-fraction 0.375)

(def cell-colors
  {:player-city [0 255 0]
   :computer-city [255 0 0]
   :free-city [255 255 255]
   :unexplored [0 0 0]
   :land [139 69 19]
   :sea [0 191 255]})

(def land-colors
  [[139 69 19]
   [160 82 45]
   [120 66 18]
   [180 100 50]
   [101 67 33]
   [170 120 60]
   [150 75 0]
   [133 94 66]])

(def production-color [128 128 128])
(def waypoint-color [0 255 0])
(def awake-unit-color [255 255 255])
(def sleeping-unit-color [0 0 0])
(def sentry-unit-color [255 128 128])
(def explore-unit-color [144 238 144])

(defn city-color-key [city-status]
  (case city-status
    :player :player-city
    :computer :computer-city
    :free :free-city))

(defn country-land-color [country-id]
  (nth land-colors (mod country-id (count land-colors))))

(defn color-of
  "Returns the RGB color for a cell based on its type and status."
  [cell]
  (let [terrain-type (:type cell)]
    (cond
      (= terrain-type :city) (cell-colors (city-color-key (:city-status cell)))
      (and (= terrain-type :land) (:country-id cell)) (country-land-color (:country-id cell))
      :else (cell-colors terrain-type))))

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
