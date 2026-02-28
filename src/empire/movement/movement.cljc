;; mutation-tested: 2026-02-28
(ns empire.movement.movement
  (:require [empire.movement.movement-execution :as execution]
            [empire.movement.movement-pathing :as pathing]
            [empire.movement.movement-resolution :as resolution]
            [empire.movement.movement-state :as state]))

(defn next-step-pos [pos target]
  (pathing/next-step-pos pos target))

(defn chebyshev-distance
  "Returns the Chebyshev (chessboard) distance between two positions."
  [[x1 y1] [x2 y2]]
  (pathing/chebyshev-distance [x1 y1] [x2 y2]))

(def ^:private diagonal? pathing/diagonal?)
(def ^:private get-sidestep-directions pathing/get-sidestep-directions)

(defn find-best-sidestep [from-pos target unit-type blocked-dir current-map]
  (pathing/find-best-sidestep from-pos target unit-type blocked-dir current-map))

(defn process-consumables [unit to-cell]
  (execution/process-consumables unit to-cell))

(def ^:private update-destination-cell execution/update-destination-cell)

(defn do-move [from-coords final-pos cell final-unit]
  (execution/do-move from-coords final-pos cell final-unit))

(defn move-unit [from-coords target-coords cell current-map]
  (resolution/move-unit from-coords target-coords cell current-map))

(defn set-unit-movement
  ([unit-coords target-coords] (resolution/set-unit-movement unit-coords target-coords))
  ([unit-coords target-coords extended?]
   (resolution/set-unit-movement unit-coords target-coords extended?)))

(def ^:private transport-with-awake-armies? state/transport-with-awake-armies?)
(def ^:private carrier-with-awake-fighters? state/carrier-with-awake-fighters?)
(def ^:private awake-unit? state/awake-unit?)

(defn get-active-unit [cell]
  (state/get-active-unit cell))

(defn is-army-aboard-transport?
  "Returns true if the active unit is an army aboard a transport."
  [active-unit]
  (state/is-army-aboard-transport? active-unit))

(defn is-fighter-from-airport?
  "Returns true if the active unit is a fighter from the airport."
  [active-unit]
  (state/is-fighter-from-airport? active-unit))

(defn is-fighter-from-carrier?
  "Returns true if the active unit is a fighter from a carrier."
  [active-unit]
  (state/is-fighter-from-carrier? active-unit))

(defn movement-context
  "Determines the movement context for a cell and active unit.
   Returns :airport-fighter, :carrier-fighter, :army-aboard, or :standard-unit."
  [cell active-unit]
  (state/movement-context cell active-unit))

(defn set-unit-mode [coords mode]
  (state/set-unit-mode coords mode))

(defn add-unit-at
  "Adds a unit of the given type at the specified cell coordinates.
   Only adds if the cell is empty. Owner defaults to :player.
   Updates visibility for the owner after adding."
  ([[cx cy] unit-type] (state/add-unit-at [cx cy] unit-type))
  ([[cx cy] unit-type owner] (state/add-unit-at [cx cy] unit-type owner)))

(defn wake-at
  "Wakes a city (removes production so it needs attention) or a sleeping unit.
   For transports with armies, also wakes the armies aboard.
   Returns true if something was woken, nil otherwise."
  [[cx cy]]
  (state/wake-at [cx cy]))
