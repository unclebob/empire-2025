(ns empire.config.core
  (:require [empire.config.units.config :as units-config]
            [empire.config.units.ships :as ships]
            [empire.config.rendering :as rendering]
            [empire.config.generation :as generation]
            [empire.config.ai :as ai]
            [empire.config.keys :as keys]
            [empire.config.messages :as msg]))

;; --- rendering re-exports ---
(def cell-size rendering/cell-size)
(def text-font-name rendering/text-font-name)
(def text-font-size rendering/text-font-size)
(def cell-char-font-name rendering/cell-char-font-name)
(def cell-char-font-size rendering/cell-char-font-size)
(def text-area-rows rendering/text-area-rows)
(def text-area-gap rendering/text-area-gap)
(def cell-char-x-offset rendering/cell-char-x-offset)
(def cell-char-y-offset rendering/cell-char-y-offset)
(def msg-left-padding rendering/msg-left-padding)
(def msg-line-1-y rendering/msg-line-1-y)
(def msg-line-2-y rendering/msg-line-2-y)
(def msg-line-3-y rendering/msg-line-3-y)
(def msg-separator-offset rendering/msg-separator-offset)
(def game-info-width-fraction rendering/game-info-width-fraction)
(def debug-width-fraction rendering/debug-width-fraction)
(def game-status-width-fraction rendering/game-status-width-fraction)
(def cell-colors rendering/cell-colors)
(def land-colors rendering/land-colors)
(def production-color rendering/production-color)
(def waypoint-color rendering/waypoint-color)
(def awake-unit-color rendering/awake-unit-color)
(def sleeping-unit-color rendering/sleeping-unit-color)
(def sentry-unit-color rendering/sentry-unit-color)
(def explore-unit-color rendering/explore-unit-color)
(def city-color-key rendering/city-color-key)
(def country-land-color rendering/country-land-color)
(def color-of rendering/color-of)
(def mode->color rendering/mode->color)
(def unit->color rendering/unit->color)

;; --- generation re-exports ---
(def default-map-size generation/default-map-size)
(def smooth-count generation/smooth-count)
(def land-fraction generation/land-fraction)
(def number-of-cities generation/number-of-cities)
(def min-city-distance generation/min-city-distance)
(def max-placement-attempts generation/max-placement-attempts)
(def min-surrounding-land generation/min-surrounding-land)
(def compute-size-constants generation/compute-size-constants)

;; --- ai re-exports ---
(def armies-before-transport ai/armies-before-transport)
(def max-patrol-boats-per-country ai/max-patrol-boats-per-country)
(def carrier-city-threshold ai/carrier-city-threshold)
(def max-live-carriers ai/max-live-carriers)
(def max-carrier-producers ai/max-carrier-producers)
(def satellite-city-threshold ai/satellite-city-threshold)
(def max-satellites ai/max-satellites)
(def advances-per-frame ai/advances-per-frame)

;; --- keys re-exports ---
(def key->direction keys/key->direction)
(def key->extended-direction keys/key->extended-direction)
(def key->production-item keys/key->production-item)

;; --- messages re-exports ---
(def error-message-duration msg/error-message-duration)
(def messages msg/messages)

;; --- facade-owned defs (depend on units-config/ships) ---

(def hostile-city? #{:free :computer})

(def fighter-fuel units-config/fighter-fuel)
(def transport-capacity units-config/transport-capacity)
(def carrier-capacity units-config/carrier-capacity)
(def explore-steps 50)
(def coastline-steps 100)
(def satellite-turns units-config/satellite-turns)
(def max-sidesteps 10)
(def carrier-spacing 22)
(def bingo-fuel-divisor 4)

(defn item-cost [unit-type]
  (case unit-type
    :army units-config/army-cost
    :fighter units-config/fighter-cost
    :satellite units-config/satellite-cost
    :transport units-config/transport-cost
    :carrier units-config/carrier-cost
    :patrol-boat (ships/config :patrol-boat :cost)
    :destroyer (ships/config :destroyer :cost)
    :submarine (ships/config :submarine :cost)
    :battleship (ships/config :battleship :cost)
    nil))

(defn item-chars [unit-type]
  (case unit-type
    :army units-config/army-display-char
    :fighter units-config/fighter-display-char
    :satellite units-config/satellite-display-char
    :transport units-config/transport-display-char
    :carrier units-config/carrier-display-char
    :patrol-boat (ships/config :patrol-boat :display-char)
    :destroyer (ships/config :destroyer :display-char)
    :submarine (ships/config :submarine :display-char)
    :battleship (ships/config :battleship :display-char)
    nil))

(defn item-hits [unit-type]
  (case unit-type
    :army units-config/army-hits
    :fighter units-config/fighter-hits
    :satellite units-config/satellite-hits
    :transport units-config/transport-hits
    :carrier units-config/carrier-hits
    :patrol-boat (ships/config :patrol-boat :hits)
    :destroyer (ships/config :destroyer :hits)
    :submarine (ships/config :submarine :hits)
    :battleship (ships/config :battleship :hits)
    nil))

(defn unit-speed [unit-type]
  (case unit-type
    :army units-config/army-speed
    :fighter units-config/fighter-speed
    :satellite units-config/satellite-speed
    :transport units-config/transport-speed
    :carrier units-config/carrier-speed
    :patrol-boat (ships/config :patrol-boat :speed)
    :destroyer (ships/config :destroyer :speed)
    :submarine (ships/config :submarine :speed)
    :battleship (ships/config :battleship :speed)
    nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:17.573163-05:00", :module-hash "1354836083", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "1317349218"} {:id "def/cell-size", :kind "def", :line 11, :end-line 11, :hash "1897738713"} {:id "def/text-font-name", :kind "def", :line 12, :end-line 12, :hash "-913810046"} {:id "def/text-font-size", :kind "def", :line 13, :end-line 13, :hash "-894138035"} {:id "def/cell-char-font-name", :kind "def", :line 14, :end-line 14, :hash "307820473"} {:id "def/cell-char-font-size", :kind "def", :line 15, :end-line 15, :hash "-924048540"} {:id "def/text-area-rows", :kind "def", :line 16, :end-line 16, :hash "774776371"} {:id "def/text-area-gap", :kind "def", :line 17, :end-line 17, :hash "1490110385"} {:id "def/cell-char-x-offset", :kind "def", :line 18, :end-line 18, :hash "271076075"} {:id "def/cell-char-y-offset", :kind "def", :line 19, :end-line 19, :hash "90699102"} {:id "def/msg-left-padding", :kind "def", :line 20, :end-line 20, :hash "-1001760717"} {:id "def/msg-line-1-y", :kind "def", :line 21, :end-line 21, :hash "1277986083"} {:id "def/msg-line-2-y", :kind "def", :line 22, :end-line 22, :hash "1835111758"} {:id "def/msg-line-3-y", :kind "def", :line 23, :end-line 23, :hash "1828897060"} {:id "def/msg-separator-offset", :kind "def", :line 24, :end-line 24, :hash "-325942503"} {:id "def/game-info-width-fraction", :kind "def", :line 25, :end-line 25, :hash "-1333783236"} {:id "def/debug-width-fraction", :kind "def", :line 26, :end-line 26, :hash "-500679957"} {:id "def/game-status-width-fraction", :kind "def", :line 27, :end-line 27, :hash "18293044"} {:id "def/cell-colors", :kind "def", :line 28, :end-line 28, :hash "715865396"} {:id "def/land-colors", :kind "def", :line 29, :end-line 29, :hash "-317775587"} {:id "def/production-color", :kind "def", :line 30, :end-line 30, :hash "-763262875"} {:id "def/waypoint-color", :kind "def", :line 31, :end-line 31, :hash "1442328082"} {:id "def/awake-unit-color", :kind "def", :line 32, :end-line 32, :hash "-156426935"} {:id "def/sleeping-unit-color", :kind "def", :line 33, :end-line 33, :hash "1504503242"} {:id "def/sentry-unit-color", :kind "def", :line 34, :end-line 34, :hash "-954456232"} {:id "def/explore-unit-color", :kind "def", :line 35, :end-line 35, :hash "180213673"} {:id "def/city-color-key", :kind "def", :line 36, :end-line 36, :hash "1377028806"} {:id "def/country-land-color", :kind "def", :line 37, :end-line 37, :hash "37753932"} {:id "def/color-of", :kind "def", :line 38, :end-line 38, :hash "-491550803"} {:id "def/mode->color", :kind "def", :line 39, :end-line 39, :hash "-788426429"} {:id "def/unit->color", :kind "def", :line 40, :end-line 40, :hash "21165386"} {:id "def/default-map-size", :kind "def", :line 43, :end-line 43, :hash "1698154822"} {:id "def/smooth-count", :kind "def", :line 44, :end-line 44, :hash "1570632302"} {:id "def/land-fraction", :kind "def", :line 45, :end-line 45, :hash "-882198519"} {:id "def/number-of-cities", :kind "def", :line 46, :end-line 46, :hash "1955339145"} {:id "def/min-city-distance", :kind "def", :line 47, :end-line 47, :hash "1419645827"} {:id "def/max-placement-attempts", :kind "def", :line 48, :end-line 48, :hash "-522914277"} {:id "def/min-surrounding-land", :kind "def", :line 49, :end-line 49, :hash "1918490689"} {:id "def/compute-size-constants", :kind "def", :line 50, :end-line 50, :hash "1616267575"} {:id "def/armies-before-transport", :kind "def", :line 53, :end-line 53, :hash "-1998610899"} {:id "def/max-patrol-boats-per-country", :kind "def", :line 54, :end-line 54, :hash "1967430288"} {:id "def/carrier-city-threshold", :kind "def", :line 55, :end-line 55, :hash "-997733730"} {:id "def/max-live-carriers", :kind "def", :line 56, :end-line 56, :hash "1467475278"} {:id "def/max-carrier-producers", :kind "def", :line 57, :end-line 57, :hash "-1562160009"} {:id "def/satellite-city-threshold", :kind "def", :line 58, :end-line 58, :hash "-605188148"} {:id "def/max-satellites", :kind "def", :line 59, :end-line 59, :hash "944595067"} {:id "def/advances-per-frame", :kind "def", :line 60, :end-line 60, :hash "930196155"} {:id "def/key->direction", :kind "def", :line 63, :end-line 63, :hash "-1223648533"} {:id "def/key->extended-direction", :kind "def", :line 64, :end-line 64, :hash "1891190200"} {:id "def/key->production-item", :kind "def", :line 65, :end-line 65, :hash "-1608071271"} {:id "def/error-message-duration", :kind "def", :line 68, :end-line 68, :hash "225873367"} {:id "def/messages", :kind "def", :line 69, :end-line 69, :hash "890621778"} {:id "def/hostile-city?", :kind "def", :line 73, :end-line 73, :hash "103875572"} {:id "def/fighter-fuel", :kind "def", :line 75, :end-line 75, :hash "606971676"} {:id "def/transport-capacity", :kind "def", :line 76, :end-line 76, :hash "887746622"} {:id "def/carrier-capacity", :kind "def", :line 77, :end-line 77, :hash "954999350"} {:id "def/explore-steps", :kind "def", :line 78, :end-line 78, :hash "-2088706086"} {:id "def/coastline-steps", :kind "def", :line 79, :end-line 79, :hash "-1869488606"} {:id "def/satellite-turns", :kind "def", :line 80, :end-line 80, :hash "-1073474344"} {:id "def/max-sidesteps", :kind "def", :line 81, :end-line 81, :hash "210954880"} {:id "def/carrier-spacing", :kind "def", :line 82, :end-line 82, :hash "3175941"} {:id "def/bingo-fuel-divisor", :kind "def", :line 83, :end-line 83, :hash "-1625408944"} {:id "defn/item-cost", :kind "defn", :line 85, :end-line 96, :hash "-1885514032"} {:id "defn/item-chars", :kind "defn", :line 98, :end-line 109, :hash "260821821"} {:id "defn/item-hits", :kind "defn", :line 111, :end-line 122, :hash "-1778293884"} {:id "defn/unit-speed", :kind "defn", :line 124, :end-line 135, :hash "733898404"}]}
;; clj-mutate-manifest-end
