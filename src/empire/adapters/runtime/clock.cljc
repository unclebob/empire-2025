;; mutation-tested: no
(ns empire.adapters.runtime.clock
  "Clock adapter for application boundary."
  (:require [empire.application.ports.clock :as ports]))

(defrecord SystemClock []
  ports/ClockPort
  (now-ms [_]
    (System/currentTimeMillis)))

(defn system-clock
  []
  (->SystemClock))
