;; mutation-tested: no
(ns empire.application.city-production
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]))

(defn set-city-production
  "Sets production and rounds remaining for a city in runtime state."
  [coords item]
  (let [current (sa/read-state :production)]
    (sa/write-state! :production
                          (assoc current
                                 coords
                                 {:item item
                                  :remaining-rounds (config/item-cost item)}))))
