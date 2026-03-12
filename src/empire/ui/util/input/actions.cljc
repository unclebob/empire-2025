(ns empire.ui.util.input.actions
  (:require [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.state.api :as sa]
            [empire.ui.util.input.actions.modes :as modes]
            [empire.ui.util.input.actions.movement :as movement]
            [empire.ui.util.input.actions.production :as production]))

(def army-aboard-action movement/army-aboard-action)

(defn handle-key [k]
  (when-let [coords (first (sa/read-state :cells-needing-attention))]
    (let [cell (get-in (sa/current-world) coords)
          active-unit (movement-state/get-active-unit cell)]
      (if active-unit
        (case k
          :space (modes/handle-space-key coords)
          :u (modes/handle-unload-key coords cell)
          :s (modes/handle-sentry-key coords cell active-unit)
          :l (modes/handle-look-around-key coords cell active-unit)
          (movement/handle-unit-movement-key k coords cell))
        (production/handle-city-production-key k coords cell)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:03:16.275314-05:00", :module-hash "1670463138", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1612157178"} {:id "def/army-aboard-action", :kind "def", :line 8, :end-line 8, :hash "-2006119160"} {:id "defn/handle-key", :kind "defn", :line 10, :end-line 21, :hash "661574224"}]}
;; clj-mutate-manifest-end
