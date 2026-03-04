;; mutation-tested: no
(ns empire.domain.services.impl.round-setup
  (:require [empire.config :as config]
            [empire.domain.services.round-setup :as round-setup]))

(defmethod round-setup/dead-unit? :default
  [contents]
  (and contents (<= (:hits contents 1) 0))
  )

(defmethod round-setup/computer-carrier? :default
  [contents]
  (and (= :carrier (:type contents)) (= :computer (:owner contents))))

(defmethod round-setup/bingo-fuel? :default
  [new-fuel friendly-city-in-range?]
  (let [threshold (quot config/fighter-fuel config/bingo-fuel-divisor)]
    (and (<= new-fuel threshold) friendly-city-in-range?)))

(defmethod round-setup/fuel-action :default
  [new-fuel bingo?]
  (cond
    (<= new-fuel 0) :crashed
    (<= new-fuel 1) :out-of-fuel
    bingo? :bingo
    :else :burn))
