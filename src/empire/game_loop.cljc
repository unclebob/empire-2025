(ns empire.game-loop
  "Main game loop facade.

   This module coordinates the game loop by delegating to focused submodules:
   - core: Main advance-game function and movement handlers
   - visibility: Fog-of-war map updates
   - satellites: Satellite movement
   - shipyard: Ship repair
   - sentry: Sentry unit management
   - round-init: Round initialization
   - item-processing: Player and computer item processing"
  (:require [empire.game-loop.core :as core]
            [empire.game-loop.item-processing :as item-processing]
            [empire.game-loop.round-init :as round-init]
            [empire.game-loop.satellites :as satellites]
            [empire.game-loop.sentry :as sentry]
            [empire.game-loop.shipyard :as shipyard]
            [empire.game-loop.visibility :as visibility]))

;; Re-export visibility functions
(def update-player-map visibility/update-player-map)
(def update-computer-map visibility/update-computer-map)

;; Re-export round-init functions
(def remove-dead-units round-init/remove-dead-units)
(def build-player-items round-init/build-player-items)
(def build-computer-items round-init/build-computer-items)
(def reset-steps-remaining round-init/reset-steps-remaining)
(def item-processed round-init/item-processed)
(def start-new-round round-init/start-new-round)

;; Re-export sentry functions
(def consume-sentry-fighter-fuel sentry/consume-sentry-fighter-fuel)
(def wake-sentries-seeing-enemy sentry/wake-sentries-seeing-enemy)
(def wake-airport-fighters sentry/wake-airport-fighters)
(def wake-carrier-fighters sentry/wake-carrier-fighters)

;; Re-export satellite functions
(def move-satellites satellites/move-satellites)

;; Re-export shipyard functions
(def repair-damaged-ships shipyard/repair-damaged-ships)

;; Re-export item-processing functions
(def move-explore-unit item-processing/move-explore-unit)
(def move-coastline-unit item-processing/move-coastline-unit)

;; Re-export core functions
(def handle-sidestep-result core/handle-sidestep-result)
(def handle-normal-move-result core/handle-normal-move-result)
(def handle-combat-result core/handle-combat-result)
(def move-current-unit core/move-current-unit)
(def toggle-pause core/toggle-pause)
(def step-one-round core/step-one-round)
(def advance-game core/advance-game)
(def update-map core/update-map)
