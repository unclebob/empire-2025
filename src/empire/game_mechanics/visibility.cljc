(ns empire.game-mechanics.visibility
  (:require [empire.game-mechanics.visibility.core :as core]
            [empire.game-mechanics.visibility.sync :as sync]
            [empire.game-mechanics.visibility.territory :as territory]
            [empire.game-mechanics.combat-visibility-port :as visibility-port]
            [empire.game-mechanics.containers.visibility-port :as containers-visibility-port]))

;; -- core --
(def update-combatant-map-state core/update-combatant-map-state)

;; -- sync & detection --
(def sync-ai-unit-to-computer-map! sync/sync-ai-unit-to-computer-map!)
(def sync-ai-cell-to-computer-map! sync/sync-ai-cell-to-computer-map!)
(def authoritative-computer-unit-at sync/authoritative-computer-unit-at)
(def drain-detections! sync/drain-detections!)
(def update-cell-visibility sync/update-cell-visibility)

;; -- territory --
(def stamp-exposed-territory! territory/stamp-exposed-territory!)
(def refresh-visible-map! territory/refresh-visible-map!)
(def update-combatant-map territory/update-combatant-map)

;; -- port wiring --
(defrecord MovementCombatVisibilityPort []
  visibility-port/CombatVisibilityPort
  (update-visibility! [_ pos owner]
    (update-cell-visibility pos owner)))

(defrecord MovementContainerVisibilityPort []
  containers-visibility-port/ContainerVisibilityPort
  (update-container-visibility! [_ pos owner]
    (update-cell-visibility pos owner)))

(visibility-port/set-combat-visibility-port! (->MovementCombatVisibilityPort))
(containers-visibility-port/set-container-visibility-port! (->MovementContainerVisibilityPort))
