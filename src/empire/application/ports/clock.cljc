(ns empire.application.ports.clock)

(defprotocol ClockPort
  (now-ms [clock] "Current wall-clock time in milliseconds."))
