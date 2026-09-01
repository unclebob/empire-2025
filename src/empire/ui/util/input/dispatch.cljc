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
  (sa/write-state! :warning-message "")
  (sa/write-state! :command-message "")
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
    (sa/read-state :help-open)        (keys/dispatch-help-key k)
    (sa/read-state :backtick-pressed) (keys/dispatch-backtick-key k cell-coords)
    :else                   (keys/dispatch-normal-key k cell-coords)))

(defn key-down
  "Process a key press with explicit mouse coordinates."
  [k mouse-x mouse-y]
  (sa/write-state! :warning-message "")
  (sa/write-state! :command-message "")
  (let [cell-coords (when (map-utils/on-map? mouse-x mouse-y)
                      (map-utils/determine-cell-coordinates mouse-x mouse-y))]
    (dispatch-key k cell-coords)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:58:11.891715-05:00", :module-hash "-730155646", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "504870212"} {:id "defn/handle-key", :kind "defn", :line 9, :end-line 9, :hash "-464059951"} {:id "defn/handle-unit-click", :kind "defn", :line 11, :end-line 12, :hash "-19049557"} {:id "defn/handle-cell-click", :kind "defn", :line 14, :end-line 17, :hash "-379632507"} {:id "defn/handle-load-menu-click", :kind "defn", :line 19, :end-line 23, :hash "404954814"} {:id "defn/mouse-down", :kind "defn", :line 25, :end-line 28, :hash "-855754345"} {:id "defn/modifier-held?", :kind "defn", :line 32, :end-line 35, :hash "1979782730"} {:id "defn/debug-drag-start!", :kind "defn", :line 37, :end-line 40, :hash "-1454085744"} {:id "defn/debug-drag-update!", :kind "defn", :line 42, :end-line 45, :hash "-963926980"} {:id "defn/debug-drag-end!", :kind "defn", :line 47, :end-line 51, :hash "6691163"} {:id "defn/dispatch-key", :kind "defn", :line 53, :end-line 59, :hash "-1872497246"} {:id "defn/key-down", :kind "defn", :line 61, :end-line 66, :hash "-871610466"}]}
;; clj-mutate-manifest-end
