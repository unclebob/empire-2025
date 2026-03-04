;; mutation-tested: 2026-02-26
(ns empire.combat)

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.domain.model.impl.combat-runtime/load-methods!)
       (reset! methods-loaded? true))))

(defmulti conquer-city-contents
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti hostile-city?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti attempt-city-conquest
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti attempt-conquest
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti attempt-fighter-overfly
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti hostile-unit?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti format-combat-log
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti format-combat-status
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti fight-round
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti resolve-combat
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti dead-escort-destroyer?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti dead-escort-transport?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti clear-escort-on-death
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti attempt-attack
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))
