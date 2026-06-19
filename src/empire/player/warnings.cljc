(ns empire.player.warnings
  (:require [empire.sound :as sound]
            [empire.state.api :as sa]))

(defn set-warning-message!
  [msg]
  (sa/write-state! :warning-message msg)
  (sound/play-bonk!))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-06-19T12:40:19.053764-05:00", :module-hash "-1445081295", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "771941713"} {:id "defn/set-warning-message!", :kind "defn", :line 5, :end-line 8, :hash "1621369187"}]}
;; clj-mutate-manifest-end
