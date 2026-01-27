(ns empire.ui.input
  "Main input dispatcher for keyboard and mouse events.

   This module is a facade that coordinates input handling across:
   - input-mouse: Mouse click handling and debug drag
   - input-movement: Keyboard movement, sentry, explore modes
   - input-commands: Production, marching orders, flight paths, waypoints"
  (:require [empire.atoms :as atoms]
            [empire.debug :as debug]
            [empire.game-loop :as game-loop]
            [empire.movement.movement :as movement]
            [empire.ui.input-mouse :as mouse]
            [empire.ui.input-movement :as move]
            [empire.ui.input-commands :as cmd]))

;; Re-export mouse functions for backwards compatibility
(def mouse-down mouse/mouse-down)
(def handle-cell-click mouse/handle-cell-click)
(def handle-unit-click mouse/handle-unit-click)
(def add-unit mouse/add-unit)
(def add-unit-at-mouse mouse/add-unit-at-mouse)
(def wake mouse/wake)
(def wake-at-mouse mouse/wake-at-mouse)
(def own-city mouse/own-city)
(def own-city-at-mouse mouse/own-city-at-mouse)
(def modifier-held? mouse/modifier-held?)
(def debug-drag-start! mouse/debug-drag-start!)
(def debug-drag-update! mouse/debug-drag-update!)
(def debug-drag-cancel! mouse/debug-drag-cancel!)
(def debug-drag-end! mouse/debug-drag-end!)

;; Re-export command functions for backwards compatibility
(def set-destination cmd/set-destination)
(def set-destination-at-mouse cmd/set-destination-at-mouse)
(def set-city-marching-orders cmd/set-city-marching-orders)
(def set-transport-marching-orders cmd/set-transport-marching-orders)
(def set-marching-orders-for-cell cmd/set-marching-orders-for-cell)
(def set-marching-orders-at-mouse cmd/set-marching-orders-at-mouse)
(def set-city-marching-orders-to-edge cmd/set-city-marching-orders-to-edge)
(def set-city-marching-orders-by-direction cmd/set-city-marching-orders-by-direction)
(def set-flight-path cmd/set-flight-path)
(def set-flight-path-at-mouse cmd/set-flight-path-at-mouse)
(def set-waypoint cmd/set-waypoint)
(def set-waypoint-at-mouse cmd/set-waypoint-at-mouse)
(def set-city-lookaround cmd/set-city-lookaround)
(def set-lookaround-at-mouse cmd/set-lookaround-at-mouse)

;; Main key handler that delegates to appropriate submodules

(defn handle-key [k]
  "Handles keyboard input for units needing attention."
  (when-let [coords (first @atoms/cells-needing-attention)]
    (let [cell (get-in @atoms/game-map coords)
          active-unit (movement/get-active-unit cell)]
      (if active-unit
        (case k
          :space (move/handle-space-key coords)
          :u (move/handle-unload-key coords cell)
          :s (move/handle-sentry-key coords cell active-unit)
          :l (move/handle-look-around-key coords cell active-unit)
          (move/handle-unit-movement-key k coords cell))
        (cmd/handle-city-production-key k coords cell)))))

(defn key-down [k]
  "Main entry point for key down events."
  (debug/log-action! [:key-pressed k])
  (if @atoms/backtick-pressed
    (do
      (reset! atoms/backtick-pressed false)
      (case k
        ;; Uppercase = player units
        :A (mouse/add-unit-at-mouse :army :player)
        :F (mouse/add-unit-at-mouse :fighter :player)
        :Z (mouse/add-unit-at-mouse :satellite :player)
        :T (mouse/add-unit-at-mouse :transport :player)
        :P (mouse/add-unit-at-mouse :patrol-boat :player)
        :D (mouse/add-unit-at-mouse :destroyer :player)
        :S (mouse/add-unit-at-mouse :submarine :player)
        :C (mouse/add-unit-at-mouse :carrier :player)
        :B (mouse/add-unit-at-mouse :battleship :player)
        ;; Lowercase = enemy/computer units
        :a (mouse/add-unit-at-mouse :army :computer)
        :f (mouse/add-unit-at-mouse :fighter :computer)
        :z (mouse/add-unit-at-mouse :satellite :computer)
        :t (mouse/add-unit-at-mouse :transport :computer)
        :p (mouse/add-unit-at-mouse :patrol-boat :computer)
        :d (mouse/add-unit-at-mouse :destroyer :computer)
        :s (mouse/add-unit-at-mouse :submarine :computer)
        :c (mouse/add-unit-at-mouse :carrier :computer)
        :b (mouse/add-unit-at-mouse :battleship :computer)
        ;; Other commands
        :o (mouse/own-city-at-mouse)
        nil))
    (cond
      (= k (keyword "`")) (reset! atoms/backtick-pressed true)
      (= k :P) (game-loop/toggle-pause)
      (and (= k :space) @atoms/paused) (game-loop/step-one-round)
      (= k :+) (swap! atoms/map-to-display {:player-map :computer-map
                                            :computer-map :actual-map
                                            :actual-map :player-map})
      (= k (keyword ".")) (cmd/set-destination-at-mouse)
      (and (= k :m) (cmd/set-marching-orders-at-mouse)) nil
      (and (= k :f) @atoms/destination (cmd/set-flight-path-at-mouse)) nil
      (and (= k :u) (mouse/wake-at-mouse)) nil
      (and (= k :l) (cmd/set-lookaround-at-mouse)) nil
      (cmd/set-city-marching-orders-by-direction k) nil
      (handle-key k) nil
      (and (= k (keyword "*")) (cmd/set-waypoint-at-mouse)) nil
      :else nil)))
