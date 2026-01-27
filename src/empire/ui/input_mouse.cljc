(ns empire.ui.input-mouse
  "Mouse click handling and debug drag operations.

   Handles all mouse interactions including:
   - Clicking on map cells to control units
   - Debug region selection for map dumps
   - Coordinate-based input functions (non-Quil dependent)"
  (:require [empire.atoms :as atoms]
            [empire.debug :as debug]
            [empire.player.attention :as attention]
            [empire.player.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.game-loop :as game-loop]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [quil.core :as q]))

;; Unit click handling

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [clicked-coords attention-coords]
  (let [attn-coords (first attention-coords)
        attn-cell (get-in @atoms/game-map attn-coords)
        active-unit (movement/get-active-unit attn-cell)
        unit-type (:type active-unit)
        is-airport-fighter? (movement/is-fighter-from-airport? active-unit)
        is-army-aboard? (movement/is-army-aboard-transport? active-unit)
        target-cell (get-in @atoms/game-map clicked-coords)
        [ax ay] attn-coords
        [cx cy] clicked-coords
        adjacent? (and (<= (abs (- ax cx)) 1) (<= (abs (- ay cy)) 1))]
    (cond
      is-airport-fighter?
      (let [fighter-pos (container-ops/launch-fighter-from-airport attn-coords clicked-coords)]
        (reset! atoms/waiting-for-input false)
        (reset! atoms/message "")
        (reset! atoms/cells-needing-attention [])
        (swap! atoms/player-items #(cons fighter-pos (rest %))))

      (and is-army-aboard? adjacent? (= (:type target-cell) :land) (not (:contents target-cell)))
      (do
        (container-ops/disembark-army-from-transport attn-coords clicked-coords)
        (game-loop/item-processed))

      is-army-aboard?
      nil ;; Awake army aboard - ignore invalid disembark targets

      (and (= :army unit-type) adjacent? (combat/hostile-city? clicked-coords))
      (combat/attempt-conquest attn-coords clicked-coords)

      (and (= :fighter unit-type) adjacent? (combat/hostile-city? clicked-coords))
      (combat/attempt-fighter-overfly attn-coords clicked-coords)

      :else
      (movement/set-unit-movement attn-coords clicked-coords))
    (game-loop/item-processed)))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (let [attention-coords @atoms/cells-needing-attention
        clicked-coords [cell-x cell-y]]
    (when (attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click clicked-coords attention-coords))))

(defn mouse-down
  "Handles mouse click events."
  [x y button]
  (when (and (= button :left) (map-utils/on-map? x y))
    (let [[cell-x cell-y] (map-utils/determine-cell-coordinates x y)]
      (reset! atoms/last-clicked-cell [cell-x cell-y])
      (handle-cell-click cell-x cell-y))))

;; Debug drag handling for region selection dumps

(defn modifier-held?
  "Returns true if Ctrl modifier is in the modifiers set."
  [modifiers]
  (boolean (and modifiers
                (or (contains? modifiers :ctrl)
                    (contains? modifiers :control)))))

(defn debug-drag-start!
  "Starts a debug drag operation at the given screen coordinates."
  [x y]
  (reset! atoms/debug-drag-start [x y])
  (reset! atoms/debug-drag-current [x y]))

(defn debug-drag-update!
  "Updates the current drag position. Only updates if a drag is active."
  [x y]
  (when @atoms/debug-drag-start
    (reset! atoms/debug-drag-current [x y])))

(defn- has-area?
  "Returns true if the cell range covers more than one cell."
  [[[start-row start-col] [end-row end-col]]]
  (or (not= start-row end-row)
      (not= start-col end-col)))

(defn debug-drag-cancel!
  "Cancels a debug drag operation without writing a dump."
  []
  (reset! atoms/debug-drag-start nil)
  (reset! atoms/debug-drag-current nil))

(defn debug-drag-end!
  "Ends a debug drag operation and triggers the dump if ctrl is held and selection has area.
   Converts screen coordinates to cell range and writes the dump file."
  [x y modifiers]
  (when @atoms/debug-drag-start
    (when (modifier-held? modifiers)
      (let [start @atoms/debug-drag-start
            end [x y]
            cell-range (debug/screen-coords-to-cell-range start end)]
        (when (has-area? cell-range)
          (let [filename (debug/write-dump! (first cell-range) (second cell-range))]
            (reset! atoms/debug-message (str "Debug: " filename))))))
    (reset! atoms/debug-drag-start nil)
    (reset! atoms/debug-drag-current nil)))

;; Mouse-position-based functions (Quil dependent)

(defn add-unit
  "Adds a unit at the given cell coordinates."
  ([coords unit-type] (add-unit coords unit-type :player))
  ([coords unit-type owner]
   (movement/add-unit-at coords unit-type owner)))

(defn add-unit-at-mouse
  ([unit-type] (add-unit-at-mouse unit-type :player))
  ([unit-type owner]
   (let [x (q/mouse-x)
         y (q/mouse-y)]
     (when (map-utils/on-map? x y)
       (add-unit (map-utils/determine-cell-coordinates x y) unit-type owner)))))

(defn wake
  "Wakes a unit at the given cell coordinates."
  [coords]
  (movement/wake-at coords))

(defn wake-at-mouse []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (wake (map-utils/determine-cell-coordinates x y)))))

(defn own-city
  "Sets a city at the given coordinates to player-owned."
  [[cx cy]]
  (let [cell (get-in @atoms/game-map [cx cy])]
    (when (= (:type cell) :city)
      (swap! atoms/game-map assoc-in [cx cy :city-status] :player)
      true)))

(defn own-city-at-mouse []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (own-city (map-utils/determine-cell-coordinates x y)))))
