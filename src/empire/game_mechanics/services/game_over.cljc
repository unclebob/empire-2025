(ns empire.game-mechanics.services.game-over
  (:require [empire.notifications :as notifications]
            [empire.state.api :as sa]))

(defn declare-game-over!
  [message]
  (sa/write-state! :paused true)
  (notifications/warn! message)
  (sa/write-state! :map-to-display :actual-map)
  (sa/write-state! :player-items [])
  (sa/write-state! :computer-items []))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:11:17.314559-05:00", :module-hash "820990447", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "448272068"} {:id "defn/declare-game-over!", :kind "defn", :line 5, :end-line nil, :hash "298502601"}]}
;; clj-mutate-manifest-end
