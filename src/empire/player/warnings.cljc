(ns empire.player.warnings
  (:require [empire.notifications :as notifications]))

(defn set-warning-message!
  [msg]
  (notifications/warn! msg))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:03:34.194374-05:00", :module-hash "-691938950", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-1627110669"} {:id "defn/set-warning-message!", :kind "defn", :line 4, :end-line nil, :hash "-616986449"}]}
;; clj-mutate-manifest-end
