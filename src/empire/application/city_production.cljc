;; mutation-tested: no
(ns empire.application.city-production)

(defmulti set-city-production
  "Sets production and rounds remaining for a city in runtime state."
  (fn [& _] :default))
