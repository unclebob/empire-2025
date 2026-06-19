(ns empire.ui.util.input.actions.helpers
  (:require [empire.game.loop.core :as game-loop]
            [empire.player.warnings :as warnings]))

(defn set-warning-message!
  [msg]
  (warnings/set-warning-message! msg))

(defn item-processed!
  []
  (game-loop/item-processed))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:48:22.049699-05:00", :module-hash "-545365647", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-591821068"} {:id "defn/set-error-message!", :kind "defn", :line 5, :end-line 8, :hash "-251365418"} {:id "defn/item-processed!", :kind "defn", :line 10, :end-line 12, :hash "-542777468"}]}
;; clj-mutate-manifest-end
