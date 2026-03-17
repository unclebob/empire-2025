(ns empire.ui.util.input.dispatch-mouse
  (:require [empire.game.save-load :as save-load]
            [empire.state.api :as sa]
            [empire.game-mechanics.debug.dump :as debug-dump]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.player.attention :as player-attention]
            [empire.player.commands :as player-commands]))

(defn handle-unit-click [clicked-coords attention-coords]
  (player-commands/handle-unit-click clicked-coords attention-coords))

(defn handle-cell-click
  [cell-x cell-y]
  (let [attention-coords (sa/read-state :cells-needing-attention)
        clicked-coords [cell-x cell-y]]
    (when (player-attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click clicked-coords attention-coords))))

(defn handle-load-menu-click
  []
  (when-let [idx (sa/read-state :load-menu-hovered)]
    (let [files (sa/read-state :load-menu-files)]
      (when (< idx (count files))
        (save-load/load-game! (nth files idx))))))

(defn mouse-down
  [x y button]
  (cond
    (sa/read-state :save-menu-open)
    nil

    (sa/read-state :load-menu-open)
    (when (= button :left)
      (handle-load-menu-click))

    (and (= button :left) (map-utils/on-map? x y))
    (let [[cell-x cell-y] (map-utils/determine-cell-coordinates x y)]
      (sa/write-state! :last-clicked-cell [cell-x cell-y])
      (handle-cell-click cell-x cell-y))))

(defn modifier-held?
  [modifiers]
  (or (:ctrl modifiers) (:meta modifiers) (:alt modifiers)))

(defn debug-drag-start!
  [x y]
  (sa/write-state! :debug-drag-start [x y])
  (sa/write-state! :debug-drag-current [x y]))

(defn debug-drag-update!
  [x y]
  (when (sa/read-state :debug-drag-start)
    (sa/write-state! :debug-drag-current [x y])))

(defn has-area?
  [[[start-row start-col] [end-row end-col]]]
  (or (not= start-row end-row)
      (not= start-col end-col)))

(defn debug-drag-end!
  [x y modifiers]
  (when (sa/read-state :debug-drag-start)
    (when (modifier-held? modifiers)
      (let [start (sa/read-state :debug-drag-start)
            end [x y]
            cell-range (debug-dump/screen-coords-to-cell-range start end)]
        (when (has-area? cell-range)
          (let [filename (debug-dump/write-dump! (first cell-range) (second cell-range))]
            (sa/write-state! :debug-message (str "Debug: " filename))
            (sa/write-state! :turn-message (str "Debug log written: " filename))
            (sa/write-state! :turn-message-until (+ (System/currentTimeMillis) 5000))))))
    (sa/write-state! :debug-drag-start nil)
    (sa/write-state! :debug-drag-current nil)))
