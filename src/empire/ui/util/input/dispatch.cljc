(ns empire.ui.util.input.dispatch
  (:require [empire.adapters.runtime.save-load :as save-load]
            [empire.application.runtime :as app-runtime]
            [empire.config :as config]
            [empire.debug :as debug]
            [empire.game-loop :as game-loop]
            [empire.movement.map-utils :as map-utils]
            [empire.player.attention :as player-attention]
            [empire.player.commands :as player-commands]
            [empire.player.orders :as player-orders]
            [empire.ui.util.input.actions :as actions]))

(defonce ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn handle-key [k] (actions/handle-key k))

(defn handle-unit-click [clicked-coords attention-coords]
  (player-commands/handle-unit-click clicked-coords attention-coords))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (let [attention-coords (read-runtime-state :cells-needing-attention)
        clicked-coords [cell-x cell-y]]
    (when (player-attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click clicked-coords attention-coords))))

(defn handle-load-menu-click
  "Handles a mouse click when the load menu is open.
   Loads the hovered file if one is selected."
  []
  (when-let [idx (read-runtime-state :load-menu-hovered)]
    (let [files (read-runtime-state :load-menu-files)]
      (when (< idx (count files))
        (save-load/load-game! (nth files idx))))))

(defn mouse-down
  "Handles mouse click events."
  [x y button]
  (cond
    ;; Load menu is open - handle menu click
    (read-runtime-state :load-menu-open)
    (when (= button :left)
      (handle-load-menu-click))

    ;; Normal map click
    (and (= button :left) (map-utils/on-map? x y))
    (let [[cell-x cell-y] (map-utils/determine-cell-coordinates x y)]
      (write-runtime-state! :last-clicked-cell [cell-x cell-y])
      (handle-cell-click cell-x cell-y))))

;; Debug drag functions

(defn modifier-held?
  "Returns true if a modifier key (ctrl, meta, alt) is held."
  [modifiers]
  (or (:ctrl modifiers) (:meta modifiers) (:alt modifiers)))

(defn debug-drag-start!
  "Starts a debug drag operation at the given screen coordinates."
  [x y]
  (write-runtime-state! :debug-drag-start [x y])
  (write-runtime-state! :debug-drag-current [x y]))

(defn debug-drag-update!
  "Updates the current drag position. Only updates if a drag is active."
  [x y]
  (when (read-runtime-state :debug-drag-start)
    (write-runtime-state! :debug-drag-current [x y])))

(defn- has-area?
  "Returns true if the cell range covers more than one cell."
  [[[start-row start-col] [end-row end-col]]]
  (or (not= start-row end-row)
      (not= start-col end-col)))

(defn debug-drag-end!
  "Ends a debug drag operation and triggers the dump if ctrl is held and selection has area.
   Converts screen coordinates to cell range and writes the dump file."
  [x y modifiers]
  (when (read-runtime-state :debug-drag-start)
    (when (modifier-held? modifiers)
      (let [start (read-runtime-state :debug-drag-start)
            end [x y]
            cell-range (debug/screen-coords-to-cell-range start end)]
        (when (has-area? cell-range)
          (let [filename (debug/write-dump! (first cell-range) (second cell-range))]
            (write-runtime-state! :debug-message (str "Debug: " filename))))))
    (write-runtime-state! :debug-drag-start nil)
    (write-runtime-state! :debug-drag-current nil)))

(def ^:private backtick-unit-map
  {:A [:army :player] :F [:fighter :player] :Z [:satellite :player]
   :T [:transport :player] :P [:patrol-boat :player] :D [:destroyer :player]
   :S [:submarine :player] :C [:carrier :player] :B [:battleship :player]
   :a [:army :computer] :f [:fighter :computer] :z [:satellite :computer]
   :t [:transport :computer] :p [:patrol-boat :computer] :d [:destroyer :computer]
   :s [:submarine :computer] :c [:carrier :computer] :b [:battleship :computer]})

(def ^:private standing-order-handlers
  {(keyword ".") (fn [coords] (player-orders/set-destination-at coords))
   (keyword "*") (fn [coords] (player-orders/set-waypoint-at coords))
   :l (fn [coords] (player-orders/set-city-lookaround coords))
   :u (fn [coords] (player-orders/wake-at coords))
   :m (fn [coords] (player-orders/set-marching-orders-at coords))
   :f (fn [coords] (player-orders/set-flight-path-at coords))})

(defn- dispatch-load-menu-key [k]
  (when (= k :escape) (save-load/close-load-menu!)))

(defn- dispatch-backtick-key [k cell-coords]
  (write-runtime-state! :backtick-pressed false)
  (when cell-coords
    (if-let [[unit-type owner] (backtick-unit-map k)]
      (player-orders/add-unit-at cell-coords unit-type owner)
      (when (= k :o) (player-orders/own-city-at cell-coords)))))

(def ^:private backtick-key (keyword "`"))
(def ^:private bang-key (keyword "!"))
(def ^:private caret-key (keyword "^"))

(defn- cycle-map-display
  [current]
  ({:player-map :computer-map
    :computer-map :actual-map
    :actual-map :player-map}
   current))

(defn- dispatch-game-control-key [k]
  (cond
    (= k backtick-key) (do (write-runtime-state! :backtick-pressed true) true)
    (= k :P) (do (game-loop/toggle-pause) true)
    (= k :+) (do (update-runtime-state! :map-to-display cycle-map-display) true)
    (and (= k :space) (read-runtime-state :paused)) (do (game-loop/step-one-round) true)))

(defn- dispatch-save-load-key [k]
  (cond
    (= k bang-key) (do (write-runtime-state! :turn-message
                                             (str "Saved to " (save-load/save-game!)))
                       (write-runtime-state! :turn-message-until (+ (System/currentTimeMillis) 3000))
                       true)
    (= k caret-key) (do (save-load/open-load-menu!) true)))

(defn- dispatch-standing-order-key [k cell-coords]
  (when-let [f (standing-order-handlers k)]
    (when cell-coords (f cell-coords))))

(defn- dispatch-coord-key [k cell-coords]
  (when cell-coords
    (or (dispatch-standing-order-key k cell-coords)
        (player-orders/set-city-marching-orders-by-direction-at cell-coords k))))

(defn- dispatch-normal-key [k cell-coords]
  (or (dispatch-game-control-key k)
      (dispatch-save-load-key k)
      (dispatch-coord-key k cell-coords)
      (actions/handle-key k)))

(defn dispatch-key [k cell-coords]
  (debug/log-action! [:key-pressed k])
  (cond
    (read-runtime-state :load-menu-open)   (dispatch-load-menu-key k)
    (read-runtime-state :backtick-pressed) (dispatch-backtick-key k cell-coords)
    :else                   (dispatch-normal-key k cell-coords)))
