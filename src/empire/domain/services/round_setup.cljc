;; mutation-tested: no
(ns empire.domain.services.round-setup
  (:require [empire.config :as config]))

(defn dead-unit?
  [contents]
  (and contents (<= (:hits contents 1) 0)))

(defn computer-carrier?
  [contents]
  (and (= :carrier (:type contents)) (= :computer (:owner contents))))

(defn bingo-fuel?
  [new-fuel friendly-city-in-range?]
  (let [threshold (quot config/fighter-fuel config/bingo-fuel-divisor)]
    (and (<= new-fuel threshold) friendly-city-in-range?)))

(defn fuel-action
  [new-fuel bingo?]
  (cond
    (<= new-fuel 0) :crashed
    (<= new-fuel 1) :out-of-fuel
    bingo? :bingo
    :else :burn))
