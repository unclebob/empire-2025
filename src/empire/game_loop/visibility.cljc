(ns empire.game-loop.visibility
  "Visibility map updates for player and computer.

   Updates the fog-of-war maps by revealing cells near owned units."
  (:require [empire.atoms :as atoms]
            [empire.movement.visibility :as visibility]))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (visibility/update-combatant-map atoms/player-map :player))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (visibility/update-combatant-map atoms/computer-map :computer))
