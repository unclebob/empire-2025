(ns empire.application.ports.random)

(defprotocol RandomPort
  (roll [rng] "Random double in [0.0, 1.0).")
  (roll-int [rng n] "Random integer in [0, n)."))
