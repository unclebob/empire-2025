(ns empire.notifications
  (:require [empire.state.api :as sa]))

(defprotocol AlertPort
  (play-alert! [this]
    "Play the user-facing alert for a warning."))

(defrecord NoopAlertPort []
  AlertPort
  (play-alert! [_] nil))

(defonce ^:private active-port (atom (->NoopAlertPort)))

(defn set-alert-port!
  [port]
  (reset! active-port port))

(defn reset-alert-port!
  []
  (reset! active-port (->NoopAlertPort)))

(defn alert-port
  []
  @active-port)

(defn alert!
  []
  (play-alert! (alert-port)))

(defn warn!
  [msg]
  (sa/write-state! :warning-message msg)
  (alert!))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:03:05.345006-05:00", :module-hash "1439396654", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "475981445"} {:id "form/1/defprotocol", :kind "defprotocol", :line 4, :end-line nil, :hash "-886741540"} {:id "form/2/defrecord", :kind "defrecord", :line 8, :end-line nil, :hash "1527220002"} {:id "form/3/defonce", :kind "defonce", :line 12, :end-line nil, :hash "414003980"} {:id "defn/set-alert-port!", :kind "defn", :line 14, :end-line nil, :hash "-842958577"} {:id "defn/reset-alert-port!", :kind "defn", :line 18, :end-line nil, :hash "1221377543"} {:id "defn/alert-port", :kind "defn", :line 22, :end-line nil, :hash "143194211"} {:id "defn/alert!", :kind "defn", :line 26, :end-line nil, :hash "1670681545"} {:id "defn/warn!", :kind "defn", :line 30, :end-line nil, :hash "1505418401"}]}
;; clj-mutate-manifest-end
