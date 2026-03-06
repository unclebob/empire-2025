(ns empire.config.core
  (:require [empire.units.config :as units-config]
            [empire.units.ships :as ships]
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
