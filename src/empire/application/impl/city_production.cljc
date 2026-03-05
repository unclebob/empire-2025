;; mutation-tested: no
(ns empire.application.impl.city-production
  (:require [empire.application.city-production :as city-production]
            [empire.application.state-access :as sa]
            [empire.config :as config]))

(defn- item-cost
  [item]
  (if-let [f (:item-cost (sa/state-ctx))]
    (f item)
    (config/item-cost item)))

(defmethod city-production/set-city-production :default
  [coords item]
  (let [current (sa/read-state :production)]
    (sa/write-state! :production
                          (assoc current
                                 coords
                                 {:item item
                                  :remaining-rounds (item-cost item)}))))
