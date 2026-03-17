(ns empire.ui.util.input.dispatch
  (:require [empire.game-mechanics.debug.logging :as debug]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.state.api :as sa]
            [empire.ui.util.input.actions :as actions]
            [empire.ui.util.input.dispatch-keys :as keys]
            [empire.ui.util.input.dispatch-mouse :as mouse]))

(defn handle-key [k] (actions/handle-key k))

(defn handle-unit-click [clicked-coords attention-coords]
  (mouse/handle-unit-click clicked-coords attention-coords))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (mouse/handle-cell-click cell-x cell-y))

(defn handle-load-menu-click
  "Handles a mouse click when the load menu is open.
   Loads the hovered file if one is selected."
  []
  (mouse/handle-load-menu-click))

(defn mouse-down
  "Handles mouse click events."
  [x y button]
  (mouse/mouse-down x y button))

;; Debug drag functions

(defn modifier-held?
  "Returns true if a modifier key (ctrl, meta, alt) is held."
  [modifiers]
  (mouse/modifier-held? modifiers))

(defn debug-drag-start!
  "Starts a debug drag operation at the given screen coordinates."
  [x y]
  (mouse/debug-drag-start! x y))

(defn debug-drag-update!
  "Updates the current drag position. Only updates if a drag is active."
  [x y]
  (mouse/debug-drag-update! x y))

(defn debug-drag-end!
  "Ends a debug drag operation and triggers the dump if ctrl is held and selection has area.
   Converts screen coordinates to cell range and writes the dump file."
  [x y modifiers]
  (mouse/debug-drag-end! x y modifiers))

(defn dispatch-key [k cell-coords]
  (debug/log-action! [:key-pressed k])
  (cond
    (sa/read-state :save-menu-open)   (keys/dispatch-save-menu-key k)
    (sa/read-state :load-menu-open)   (keys/dispatch-load-menu-key k)
    (sa/read-state :backtick-pressed) (keys/dispatch-backtick-key k cell-coords)
    :else                   (keys/dispatch-normal-key k cell-coords)))

(defn key-down
  "Process a key press with explicit mouse coordinates."
  [k mouse-x mouse-y]
  (let [cell-coords (when (map-utils/on-map? mouse-x mouse-y)
                      (map-utils/determine-cell-coordinates mouse-x mouse-y))]
    (dispatch-key k cell-coords)))
