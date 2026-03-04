;; mutation-tested: no
(ns empire.application.city-production
  (:require [empire.application.runtime :as app-runtime]
            [empire.config :as config]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- item-cost
  [item]
  (if-let [f (:item-cost @state-ctx)]
    (f item)
    (config/item-cost item)))

(defn set-city-production
  "Sets production and rounds remaining for a city in runtime state."
  [coords item]
  (let [current (read-runtime-state :production)]
    (write-runtime-state! :production
                          (assoc current
                                 coords
                                 {:item item
                                  :remaining-rounds (item-cost item)}))))
