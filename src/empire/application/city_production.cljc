;; mutation-tested: no
(ns empire.application.city-production
  (:require [empire.application.state-access :as sa]
            [empire.config :as config]))

(defn- item-cost
  [item]
  (if-let [f (:item-cost (sa/state-ctx))]
    (f item)
    (config/item-cost item)))

(defn set-city-production
  "Sets production and rounds remaining for a city in runtime state."
  [coords item]
  (let [current (sa/read-state :production)]
    (sa/write-state! :production
                          (assoc current
                                 coords
                                 {:item item
                                  :remaining-rounds (item-cost item)}))))
