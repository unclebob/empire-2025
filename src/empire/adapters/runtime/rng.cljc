;; mutation-tested: no
(ns empire.adapters.runtime.rng
  "Random adapter for application boundary."
  (:require [empire.application.ports :as ports]))

(defrecord CoreRandom []
  ports/RandomPort
  (roll [_]
    (rand))
  (roll-int [_ n]
    (rand-int n)))

(defn core-random
  []
  (->CoreRandom))
