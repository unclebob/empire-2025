;; mutation-tested: no
(ns empire.domain.services.round-setup
  (:require [empire.units.config :as units-config]))

(defmulti dead-unit? (fn [& _] :default))

(defmulti computer-carrier? (fn [& _] :default))

(defmulti bingo-fuel? (fn [& _] :default))

(defmulti fuel-action (fn [& _] :default))

(defmethod dead-unit? :default
  [contents]
  (and contents (<= (:hits contents 1) 0)))

(defmethod computer-carrier? :default
  [contents]
  (and (= :carrier (:type contents)) (= :computer (:owner contents))))

(defmethod bingo-fuel? :default
  [new-fuel friendly-city-in-range?]
  (let [threshold (quot units-config/fighter-fuel units-config/bingo-fuel-divisor)]
    (and (<= new-fuel threshold) friendly-city-in-range?)))

(defmethod fuel-action :default
  [new-fuel bingo?]
  (cond
    (<= new-fuel 0) :crashed
    (<= new-fuel 1) :out-of-fuel
    bingo? :bingo
    :else :burn))
