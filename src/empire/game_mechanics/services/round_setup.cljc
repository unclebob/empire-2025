;; mutation-tested: no
(ns empire.game-mechanics.services.round-setup
  (:require [empire.config.units.config :as units-config]))

(defn dead-unit?
  [contents]
  (and contents (<= (:hits contents 1) 0)))

(defn computer-carrier?
  [contents]
  (and (= :carrier (:type contents)) (= :computer (:owner contents))))

(defn bingo-fuel?
  [new-fuel friendly-city-in-range?]
  (let [threshold (quot units-config/fighter-fuel units-config/bingo-fuel-divisor)]
    (and (<= new-fuel threshold) friendly-city-in-range?)))

(defn fuel-action
  [new-fuel bingo?]
  (cond
    (<= new-fuel 0) :crashed
    (<= new-fuel 1) :out-of-fuel
    bingo? :bingo
    :else :burn))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:41:04.923026-05:00", :module-hash "-215378057", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 3, :hash "-228511216"} {:id "defn/dead-unit?", :kind "defn", :line 5, :end-line 7, :hash "783030106"} {:id "defn/computer-carrier?", :kind "defn", :line 9, :end-line 11, :hash "165670236"} {:id "defn/bingo-fuel?", :kind "defn", :line 13, :end-line 16, :hash "102138636"} {:id "defn/fuel-action", :kind "defn", :line 18, :end-line 24, :hash "-213520270"}]}
;; clj-mutate-manifest-end
