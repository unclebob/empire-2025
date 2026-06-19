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
;; {:version 1, :tested-at "2026-06-19T12:43:43.36669-05:00", :module-hash "2127028053", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-979410802"} {:id "defn/set-warning-message!", :kind "defn", :line 5, :end-line 7, :hash "970286296"} {:id "defn/item-processed!", :kind "defn", :line 9, :end-line 11, :hash "-542777468"}]}
;; clj-mutate-manifest-end
