;; mutation-tested: 2026-02-26
(ns empire.combat)

(defmulti conquer-city-contents
  (fn [& _]
    :default))

(defmulti hostile-city?
  (fn [& _]
    :default))

(defmulti attempt-city-conquest
  (fn [& _]
    :default))

(defmulti attempt-conquest
  (fn [& _]
    :default))

(defmulti attempt-fighter-overfly
  (fn [& _]
    :default))

(defmulti hostile-unit?
  (fn [& _]
    :default))

(defmulti format-combat-log
  (fn [& _]
    :default))

(defmulti format-combat-status
  (fn [& _]
    :default))

(defmulti fight-round
  (fn [& _]
    :default))

(defmulti resolve-combat
  (fn [& _]
    :default))

(defmulti dead-escort-destroyer?
  (fn [& _]
    :default))

(defmulti dead-escort-transport?
  (fn [& _]
    :default))

(defmulti clear-escort-on-death
  (fn [& _]
    :default))

(defmulti attempt-attack
  (fn [& _]
    :default))
