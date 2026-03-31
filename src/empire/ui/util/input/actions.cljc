(ns empire.ui.util.input.actions
  (:require [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.player.command-decisions :as decisions]
            [empire.state.api :as sa]
            [empire.ui.util.input.actions.helpers :as helpers]
            [empire.ui.util.input.actions.modes :as modes]
            [empire.ui.util.input.actions.movement :as movement]
            [empire.ui.util.input.actions.production :as production]))

(def army-aboard-action movement/army-aboard-action)

(defn- apply-unit-decision [decision coords cell active-unit]
  (case (:action decision)
    :skip (modes/handle-space-key coords)
    :unload (modes/handle-unload-key coords cell)
    :sentry (modes/handle-sentry-key coords cell active-unit)
    :look-around (modes/handle-look-around-key coords cell active-unit)
    :move (movement/handle-unit-movement-decision decision coords cell)
    :reject (do (helpers/set-warning-message! (:message decision)) true)
    nil))

(defn- apply-city-decision [decision coords cell]
  (case (:action decision)
    :skip (production/handle-city-production-decision decision coords cell)
    :clear-production (production/handle-city-production-decision decision coords cell)
    :set-production (production/handle-city-production-decision decision coords cell)
    nil))

(defn- apply-key-decision [decision coords cell active-unit]
  (if active-unit
    (apply-unit-decision decision coords cell active-unit)
    (apply-city-decision decision coords cell)))

(defn handle-key [k]
  (when-let [coords (first (sa/read-state :cells-needing-attention))]
    (let [cell (get-in (sa/current-world) coords)
          active-unit (movement-state/get-active-unit cell coords)
          decision (decisions/attention-key-action k cell active-unit)
          result (apply-key-decision decision coords cell active-unit)]
      (when (and (nil? result) (sa/read-state :waiting-for-input))
        (helpers/set-warning-message! "Invalid action for this unit."))
      result)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:47:09.999649-05:00", :module-hash "-2107557440", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-1913745040"} {:id "def/army-aboard-action", :kind "def", :line 9, :end-line 9, :hash "-2006119160"} {:id "defn-/apply-unit-decision", :kind "defn-", :line 11, :end-line 18, :hash "1213856076"} {:id "defn-/apply-city-decision", :kind "defn-", :line 20, :end-line 25, :hash "572543254"} {:id "defn-/apply-key-decision", :kind "defn-", :line 27, :end-line 30, :hash "-558387725"} {:id "defn/handle-key", :kind "defn", :line 32, :end-line 37, :hash "-1466559726"}]}
;; clj-mutate-manifest-end
