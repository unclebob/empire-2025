;; mutation-tested: 2026-02-25
(ns empire.units.carrier)

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.units.impl.carrier/load-methods!)
       (reset! methods-loaded? true))))

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

(defmulti full?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti has-fighters?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti has-awake-fighters?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti add-fighter
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti remove-fighter
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti wake-fighters
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti sleep-fighters
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti remove-awake-fighter
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))
