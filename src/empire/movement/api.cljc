(ns empire.movement.api
  (:require [empire.movement.movement :as movement]
            [empire.movement.service :as service]))

(defn next-step-pos [pos target]
  (movement/path-next-step-pos (service/current-services) pos target))

(defn chebyshev-distance
  "Returns the Chebyshev (chessboard) distance between two positions."
  [from-pos to-pos]
  (movement/path-chebyshev-distance (service/current-services) from-pos to-pos))

(defn find-best-sidestep [from-pos target unit-type blocked-dir current-map]
  (movement/path-find-best-sidestep (service/current-services) from-pos target unit-type blocked-dir current-map))

(defn process-consumables [unit to-cell]
  (movement/exec-process-consumables (service/current-services) unit to-cell))

(defn do-move [from-coords final-pos cell final-unit]
  (movement/exec-do-move (service/current-services) from-coords final-pos cell final-unit))

(defn move-unit [from-coords target-coords cell current-map]
  (movement/resolve-move-unit (service/current-services) from-coords target-coords cell current-map))

(defn set-unit-movement
  ([unit-coords target-coords]
   (movement/resolve-set-unit-movement (service/current-services) unit-coords target-coords false))
  ([unit-coords target-coords extended?]
   (movement/resolve-set-unit-movement (service/current-services) unit-coords target-coords extended?)))

(defn get-active-unit [cell]
  (movement/state-get-active-unit (service/current-services) cell))

(defn is-army-aboard-transport?
  "Returns true if the active unit is an army aboard a transport."
  [active-unit]
  (movement/state-is-army-aboard-transport? (service/current-services) active-unit))

(defn is-fighter-from-airport?
  "Returns true if the active unit is a fighter from the airport."
  [active-unit]
  (movement/state-is-fighter-from-airport? (service/current-services) active-unit))

(defn is-fighter-from-carrier?
  "Returns true if the active unit is a fighter from a carrier."
  [active-unit]
  (movement/state-is-fighter-from-carrier? (service/current-services) active-unit))

(defn movement-context
  "Determines the movement context for a cell and active unit.
   Returns :airport-fighter, :carrier-fighter, :army-aboard, or :standard-unit."
  [cell active-unit]
  (movement/state-movement-context (service/current-services) cell active-unit))

(defn set-unit-mode [coords mode]
  (movement/state-set-unit-mode (service/current-services) coords mode))

(defn add-unit-at
  "Adds a unit of the given type at the specified cell coordinates.
   Only adds if the cell is empty. Owner defaults to :player.
   Updates visibility for the owner after adding."
  ([coords unit-type]
   (movement/state-add-unit-at (service/current-services) coords unit-type :player))
  ([coords unit-type owner]
   (movement/state-add-unit-at (service/current-services) coords unit-type owner)))

(defn wake-at
  "Wakes a city (removes production so it needs attention) or a sleeping unit.
   For transports with armies, also wakes the armies aboard.
   Returns true if something was woken, nil otherwise."
  [coords]
  (movement/state-wake-at (service/current-services) coords))
