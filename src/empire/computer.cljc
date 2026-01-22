(ns empire.computer
  "Computer AI coordinator - dispatches to specialized modules for unit processing."
  (:require [empire.atoms :as atoms]
            [empire.computer.army :as army]
            [empire.computer.core :as core]
            [empire.computer.fighter :as fighter]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.computer.threat :as threat]
            [empire.computer.transport :as transport]))

;; Re-export public API for backwards compatibility

;; Core utilities
(def distance core/distance)
(def adjacent-to-computer-unexplored? core/adjacent-to-computer-unexplored?)

;; Threat assessment
(def unit-threat threat/unit-threat)
(def threat-level threat/threat-level)
(def safe-moves threat/safe-moves)
(def should-retreat? threat/should-retreat?)
(def retreat-move threat/retreat-move)

;; Production
(def city-is-coastal? production/city-is-coastal?)
(def count-computer-units production/count-computer-units)
(def decide-production production/decide-production)
(def process-computer-city production/process-computer-city)

;; Transport beach operations
(def count-land-neighbors transport/count-land-neighbors)
(def good-beach? transport/good-beach?)
(def reserve-beach transport/reserve-beach)
(def release-beach-for-transport transport/release-beach-for-transport)
(def completely-surrounded-by-sea? transport/completely-surrounded-by-sea?)
(def directions-away-from-land transport/directions-away-from-land)
(def directions-along-wall transport/directions-along-wall)
(def find-good-beach-near-city transport/find-good-beach-near-city)
(def find-unloading-beach-for-invasion transport/find-unloading-beach-for-invasion)
(def find-loading-dock transport/find-loading-dock)
(def find-invasion-target transport/find-invasion-target)
(def find-disembark-target transport/find-disembark-target)
(def adjacent-to-land? transport/adjacent-to-land?)
(def disembark-army-to-explore transport/disembark-army-to-explore)
(def can-unload-at? transport/can-unload-at?)
(def decide-transport-move transport/decide-transport-move)
(def find-nearest-armies transport/find-nearest-armies)
(def direct-armies-to-beach transport/direct-armies-to-beach)

;; Unit decision functions
(def decide-army-move army/decide-army-move)
(def decide-ship-move ship/decide-ship-move)
(def decide-fighter-move fighter/decide-fighter-move)

;; Main dispatch function

(defn process-computer-unit
  "Processes a single computer unit's turn. Returns nil when done."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= (:owner unit) :computer))
      (case (:type unit)
        :army (army/process-army pos)
        :fighter (fighter/process-fighter pos unit)
        :transport (transport/process-transport pos)
        (:destroyer :submarine :patrol-boat :carrier :battleship)
        (ship/process-ship pos (:type unit))
        ;; Satellite - no processing needed
        nil))))
