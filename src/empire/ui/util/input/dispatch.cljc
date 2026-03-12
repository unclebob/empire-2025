(ns empire.ui.util.input.dispatch
  (:require [clojure.string :as string]
            [empire.game.save-load :as save-load]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game-mechanics.debug.dump :as debug-dump]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.player.attention :as player-attention]
            [empire.player.commands :as player-commands]
            [empire.player.orders :as player-orders]
            [empire.ui.util.input.actions :as actions]))

(defn handle-key [k] (actions/handle-key k))

(defn handle-unit-click [clicked-coords attention-coords]
  (player-commands/handle-unit-click clicked-coords attention-coords))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (let [attention-coords (sa/read-state :cells-needing-attention)
        clicked-coords [cell-x cell-y]]
    (when (player-attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click clicked-coords attention-coords))))

(defn handle-load-menu-click
  "Handles a mouse click when the load menu is open.
   Loads the hovered file if one is selected."
  []
  (when-let [idx (sa/read-state :load-menu-hovered)]
    (let [files (sa/read-state :load-menu-files)]
      (when (< idx (count files))
        (save-load/load-game! (nth files idx))))))

(defn mouse-down
  "Handles mouse click events."
  [x y button]
  (cond
    ;; Save menu is open - ignore clicks
    (sa/read-state :save-menu-open)
    nil

    ;; Load menu is open - handle menu click
    (sa/read-state :load-menu-open)
    (when (= button :left)
      (handle-load-menu-click))

    ;; Normal map click
    (and (= button :left) (map-utils/on-map? x y))
    (let [[cell-x cell-y] (map-utils/determine-cell-coordinates x y)]
      (sa/write-state! :last-clicked-cell [cell-x cell-y])
      (handle-cell-click cell-x cell-y))))

;; Debug drag functions

(defn modifier-held?
  "Returns true if a modifier key (ctrl, meta, alt) is held."
  [modifiers]
  (or (:ctrl modifiers) (:meta modifiers) (:alt modifiers)))

(defn debug-drag-start!
  "Starts a debug drag operation at the given screen coordinates."
  [x y]
  (sa/write-state! :debug-drag-start [x y])
  (sa/write-state! :debug-drag-current [x y]))

(defn debug-drag-update!
  "Updates the current drag position. Only updates if a drag is active."
  [x y]
  (when (sa/read-state :debug-drag-start)
    (sa/write-state! :debug-drag-current [x y])))

(defn- has-area?
  "Returns true if the cell range covers more than one cell."
  [[[start-row start-col] [end-row end-col]]]
  (or (not= start-row end-row)
      (not= start-col end-col)))

(defn debug-drag-end!
  "Ends a debug drag operation and triggers the dump if ctrl is held and selection has area.
   Converts screen coordinates to cell range and writes the dump file."
  [x y modifiers]
  (when (sa/read-state :debug-drag-start)
    (when (modifier-held? modifiers)
      (let [start (sa/read-state :debug-drag-start)
            end [x y]
            cell-range (debug-dump/screen-coords-to-cell-range start end)]
        (when (has-area? cell-range)
          (let [filename (debug-dump/write-dump! (first cell-range) (second cell-range))]
            (sa/write-state! :debug-message (str "Debug: " filename))))))
    (sa/write-state! :debug-drag-start nil)
    (sa/write-state! :debug-drag-current nil)))

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

(defn- save-char-key
  [k]
  (let [s (name k)]
    (when (and (= 1 (count s))
               (re-matches #"[A-Za-z0-9._-]" s))
      s)))

(defn- clear-default-save-input!
  []
  (when (sa/read-state :save-menu-default-active)
    (sa/write-state! :save-menu-input "")
    (sa/write-state! :save-menu-default-active false)))

(defn- delete-key?
  [k]
  (let [name-lc (some-> k name string/lower-case)]
    (or (= k :backspace)
        (= k :delete)
        (= k :del)
        (= k (keyword (str (char 127))))
        (= name-lc "forward-delete")
        (= name-lc "kp-delete")
        (string/includes? (or name-lc "") "delete"))))

(defn- enter-key?
  [k]
  (let [name-lc (some-> k name string/lower-case)]
    (or (= k :enter)
        (= k :return)
        (= k :newline)
        (= k (keyword (str \newline)))
        (= name-lc "kp-enter")
        (string/includes? (or name-lc "") "enter"))))

(defn- dispatch-save-menu-key
  [k]
  (cond
    (= k :escape) (do (save-load/close-save-menu!) true)
    (enter-key? k) (do (sa/write-state! :turn-message
                                        (str "Saved to " (save-load/save-from-menu!)))
                       (sa/write-state! :turn-message-until (+ (System/currentTimeMillis) 3000))
                       true)
    :else (do
            (clear-default-save-input!)
            (cond
              (delete-key? k) (do (save-load/backspace-save-menu-input!) true)
              :else (when-let [ch (save-char-key k)]
                      (save-load/append-save-menu-char! ch)
                      true)))))

(defn- dispatch-backtick-key [k cell-coords]
  (sa/write-state! :backtick-pressed false)
  (when cell-coords
    (if-let [[unit-type owner] (backtick-unit-map k)]
      (player-orders/add-unit-at cell-coords unit-type owner)
      (when (= k :o) (player-orders/own-city-at cell-coords)))))

(def ^:private backtick-key (keyword "`"))
(def ^:private bang-key (keyword "!"))
(def ^:private caret-key (keyword "^"))

(defn- save-dialog-available?
  []
  (let [[w h] (sa/read-state :map-screen-dimensions)]
    (and (pos? w) (pos? h))))

(defn- cycle-map-display
  [current]
  ({:player-map :computer-map
    :computer-map :actual-map
    :actual-map :player-map}
   current))

(defn- dispatch-game-control-key [k]
  (cond
    (= k backtick-key) (do (sa/write-state! :backtick-pressed true) true)
    (= k :P) (do (game-loop/toggle-pause) true)
    (= k :+) (do (sa/update-state! :map-to-display cycle-map-display) true)
    (and (= k :space) (sa/read-state :paused)) (do (game-loop/step-one-round) true)))

(defn- dispatch-save-load-key [k]
  (cond
    (= k bang-key) (do
                     (if (save-dialog-available?)
                       (save-load/open-save-menu!)
                       (sa/write-state! :turn-message
                                        (str "Saved to " (save-load/save-game! "saves"))))
                     (sa/write-state! :turn-message-until (+ (System/currentTimeMillis) 3000))
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
    (sa/read-state :save-menu-open)   (dispatch-save-menu-key k)
    (sa/read-state :load-menu-open)   (dispatch-load-menu-key k)
    (sa/read-state :backtick-pressed) (dispatch-backtick-key k cell-coords)
    :else                   (dispatch-normal-key k cell-coords)))

(defn key-down
  "Process a key press with explicit mouse coordinates."
  [k mouse-x mouse-y]
  (let [cell-coords (when (map-utils/on-map? mouse-x mouse-y)
                      (map-utils/determine-cell-coordinates mouse-x mouse-y))]
    (dispatch-key k cell-coords)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:03:33.298906-05:00", :module-hash "-1485057809", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 13, :hash "168855241"} {:id "defn/handle-key", :kind "defn", :line 15, :end-line 15, :hash "-464059951"} {:id "defn/handle-unit-click", :kind "defn", :line 17, :end-line 18, :hash "371623234"} {:id "defn/handle-cell-click", :kind "defn", :line 20, :end-line 26, :hash "2123570654"} {:id "defn/handle-load-menu-click", :kind "defn", :line 28, :end-line 35, :hash "-1195583466"} {:id "defn/mouse-down", :kind "defn", :line 37, :end-line 54, :hash "-1602228014"} {:id "defn/modifier-held?", :kind "defn", :line 58, :end-line 61, :hash "1437903667"} {:id "defn/debug-drag-start!", :kind "defn", :line 63, :end-line 67, :hash "1566085345"} {:id "defn/debug-drag-update!", :kind "defn", :line 69, :end-line 73, :hash "-65310265"} {:id "defn-/has-area?", :kind "defn-", :line 75, :end-line 79, :hash "-1391867574"} {:id "defn/debug-drag-end!", :kind "defn", :line 81, :end-line 94, :hash "-482847114"} {:id "def/backtick-unit-map", :kind "def", :line 96, :end-line 102, :hash "-249117167"} {:id "def/standing-order-handlers", :kind "def", :line 104, :end-line 110, :hash "1315951812"} {:id "defn-/dispatch-load-menu-key", :kind "defn-", :line 112, :end-line 113, :hash "-795770615"} {:id "defn-/save-char-key", :kind "defn-", :line 115, :end-line 120, :hash "-94978476"} {:id "defn-/clear-default-save-input!", :kind "defn-", :line 122, :end-line 126, :hash "456498162"} {:id "defn-/delete-key?", :kind "defn-", :line 128, :end-line 137, :hash "889963059"} {:id "defn-/enter-key?", :kind "defn-", :line 139, :end-line 147, :hash "-199649867"} {:id "defn-/dispatch-save-menu-key", :kind "defn-", :line 149, :end-line 163, :hash "86409635"} {:id "defn-/dispatch-backtick-key", :kind "defn-", :line 165, :end-line 170, :hash "1219756225"} {:id "def/backtick-key", :kind "def", :line 172, :end-line 172, :hash "-102465311"} {:id "def/bang-key", :kind "def", :line 173, :end-line 173, :hash "-645220389"} {:id "def/caret-key", :kind "def", :line 174, :end-line 174, :hash "-282349008"} {:id "defn-/save-dialog-available?", :kind "defn-", :line 176, :end-line 179, :hash "1829760258"} {:id "defn-/cycle-map-display", :kind "defn-", :line 181, :end-line 186, :hash "1588047837"} {:id "defn-/dispatch-game-control-key", :kind "defn-", :line 188, :end-line 193, :hash "-1959900370"} {:id "defn-/dispatch-save-load-key", :kind "defn-", :line 195, :end-line 204, :hash "-514924900"} {:id "defn-/dispatch-standing-order-key", :kind "defn-", :line 206, :end-line 208, :hash "-1132195901"} {:id "defn-/dispatch-coord-key", :kind "defn-", :line 210, :end-line 213, :hash "1532504441"} {:id "defn-/dispatch-normal-key", :kind "defn-", :line 215, :end-line 219, :hash "-1140612864"} {:id "defn/dispatch-key", :kind "defn", :line 221, :end-line 227, :hash "-1865023036"} {:id "defn/key-down", :kind "defn", :line 229, :end-line 234, :hash "-871610466"}]}
;; clj-mutate-manifest-end
