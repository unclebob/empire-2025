(ns empire.config.rendering)

(def cell-size [11 16])
(def text-font-name "Courier New")
(def text-font-size 18)
(def cell-char-font-name "CourierNewPS-BoldMT")
(def cell-char-font-size 12)
(def text-area-rows 5)
(def text-area-gap 7)
(def cell-char-x-offset 2)
(def cell-char-y-offset 12)
(def msg-left-padding 14)
(def msg-line-1-y 16)
(def msg-line-2-y 40)
(def msg-line-3-y 60)
(def msg-line-4-y 76)
(def msg-separator-offset 4)
(def msg-banner-separator-y 26)
(def status-left-padding 14)
(def status-right-padding 14)
(def game-info-width-fraction 0.375)
(def debug-width-fraction 0.25)
(def game-status-width-fraction 0.375)
(def hud-background-color [14 18 22])
(def hud-top-separator-color [120 128 136])
(def hud-banner-separator-color [54 60 66])

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
(def coastline-follow-unit-color [0 120 0])

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
    :coastline-follow coastline-follow-unit-color
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:38:37.923002-05:00", :module-hash "-1720225195", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1392507748"} {:id "def/cell-size", :kind "def", :line 3, :end-line 3, :hash "204013741"} {:id "def/text-font-name", :kind "def", :line 4, :end-line 4, :hash "1614273045"} {:id "def/text-font-size", :kind "def", :line 5, :end-line 5, :hash "2069760490"} {:id "def/cell-char-font-name", :kind "def", :line 6, :end-line 6, :hash "-1082246515"} {:id "def/cell-char-font-size", :kind "def", :line 7, :end-line 7, :hash "1234779007"} {:id "def/text-area-rows", :kind "def", :line 8, :end-line 8, :hash "-9214672"} {:id "def/text-area-gap", :kind "def", :line 9, :end-line 9, :hash "-168737704"} {:id "def/cell-char-x-offset", :kind "def", :line 10, :end-line 10, :hash "-1449922027"} {:id "def/cell-char-y-offset", :kind "def", :line 11, :end-line 11, :hash "-1673915125"} {:id "def/msg-left-padding", :kind "def", :line 12, :end-line 12, :hash "1790142310"} {:id "def/msg-line-1-y", :kind "def", :line 13, :end-line 13, :hash "-1182305383"} {:id "def/msg-line-2-y", :kind "def", :line 14, :end-line 14, :hash "-783626909"} {:id "def/msg-line-3-y", :kind "def", :line 15, :end-line 15, :hash "2019800321"} {:id "def/msg-line-4-y", :kind "def", :line 16, :end-line 16, :hash "1247959813"} {:id "def/msg-separator-offset", :kind "def", :line 17, :end-line 17, :hash "108870732"} {:id "def/msg-banner-separator-y", :kind "def", :line 18, :end-line 18, :hash "701370455"} {:id "def/status-left-padding", :kind "def", :line 19, :end-line 19, :hash "-1169710755"} {:id "def/status-right-padding", :kind "def", :line 20, :end-line 20, :hash "116399438"} {:id "def/game-info-width-fraction", :kind "def", :line 21, :end-line 21, :hash "1160100555"} {:id "def/debug-width-fraction", :kind "def", :line 22, :end-line 22, :hash "-2046183720"} {:id "def/game-status-width-fraction", :kind "def", :line 23, :end-line 23, :hash "1466381634"} {:id "def/hud-background-color", :kind "def", :line 24, :end-line 24, :hash "-1223945215"} {:id "def/hud-top-separator-color", :kind "def", :line 25, :end-line 25, :hash "1381179992"} {:id "def/hud-banner-separator-color", :kind "def", :line 26, :end-line 26, :hash "2101175403"} {:id "def/cell-colors", :kind "def", :line 28, :end-line 34, :hash "-839042888"} {:id "def/land-colors", :kind "def", :line 36, :end-line 44, :hash "1517045395"} {:id "def/production-color", :kind "def", :line 46, :end-line 46, :hash "-692714243"} {:id "def/waypoint-color", :kind "def", :line 47, :end-line 47, :hash "35280989"} {:id "def/awake-unit-color", :kind "def", :line 48, :end-line 48, :hash "1587342543"} {:id "def/sleeping-unit-color", :kind "def", :line 49, :end-line 49, :hash "1685493832"} {:id "def/sentry-unit-color", :kind "def", :line 50, :end-line 50, :hash "-468268812"} {:id "def/explore-unit-color", :kind "def", :line 51, :end-line 51, :hash "1287241637"} {:id "defn/city-color-key", :kind "defn", :line 53, :end-line 57, :hash "644523243"} {:id "defn/country-land-color", :kind "defn", :line 59, :end-line 60, :hash "2096438486"} {:id "defn/color-of", :kind "defn", :line 62, :end-line 69, :hash "1428681441"} {:id "defn/mode->color", :kind "defn", :line 71, :end-line 79, :hash "1652908601"} {:id "defn/unit->color", :kind "defn", :line 81, :end-line 94, :hash "634640844"}]}
;; clj-mutate-manifest-end
