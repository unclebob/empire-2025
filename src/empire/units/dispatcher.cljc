;; mutation-tested: 2026-02-26
(ns empire.units.dispatcher
  "Dispatches to the appropriate unit module based on unit type.
   Provides a unified interface for accessing unit configuration and behavior.")

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.units.impl.dispatcher/load-methods!)
       (reset! methods-loaded? true))))

(defmulti speed
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti cost
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti hits
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti display-char
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti visibility-radius
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti strength
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti initial-state
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti can-move-to?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti needs-attention?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti effective-speed
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti capacity
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti effective-capacity
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti naval-units
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti naval-unit?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))
