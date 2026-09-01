(ns empire.ui.util.input.dispatch-mouse
  (:require [empire.game.save-load :as save-load]
            [empire.state.api :as sa]
            [empire.game-mechanics.debug.dump :as debug-dump]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.player.attention :as player-attention]
            [empire.player.commands :as player-commands]
            [empire.ui.util.help :as help]))

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

(defn- left-click?
  [button]
  (= button :left))

(defn- handle-help-mouse
  [x y button]
  (when (left-click? button)
    (help/handle-help-click x y)))

(defn- handle-load-menu-mouse
  [button]
  (when (left-click? button)
    (handle-load-menu-click)))

(defn- handle-map-mouse
  [x y button]
  (when (and (left-click? button) (map-utils/on-map? x y))
    (let [[cell-x cell-y] (map-utils/determine-cell-coordinates x y)]
      (sa/write-state! :last-clicked-cell [cell-x cell-y])
      (handle-cell-click cell-x cell-y))))

(defn mouse-down
  [x y button]
  (cond
    (sa/read-state :help-open) (handle-help-mouse x y button)
    (sa/read-state :save-menu-open) nil
    (sa/read-state :load-menu-open) (handle-load-menu-mouse button)
    :else (handle-map-mouse x y button)))

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
            (sa/write-state! :command-message (str "Debug log written: " filename))))))
    (sa/write-state! :debug-drag-start nil)
    (sa/write-state! :debug-drag-current nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:56:19.378804-05:00", :module-hash "985387243", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "497689046"} {:id "defn/handle-unit-click", :kind "defn", :line 9, :end-line 10, :hash "371623234"} {:id "defn/handle-cell-click", :kind "defn", :line 12, :end-line 17, :hash "-1311379753"} {:id "defn/handle-load-menu-click", :kind "defn", :line 19, :end-line 24, :hash "-1509754453"} {:id "defn/mouse-down", :kind "defn", :line 26, :end-line 39, :hash "1339333740"} {:id "defn/modifier-held?", :kind "defn", :line 41, :end-line 43, :hash "1950750113"} {:id "defn/debug-drag-start!", :kind "defn", :line 45, :end-line 48, :hash "2030933344"} {:id "defn/debug-drag-update!", :kind "defn", :line 50, :end-line 53, :hash "-1958305618"} {:id "defn/has-area?", :kind "defn", :line 55, :end-line 58, :hash "-1706725064"} {:id "defn/debug-drag-end!", :kind "defn", :line 60, :end-line 73, :hash "-775191317"}]}
;; clj-mutate-manifest-end
