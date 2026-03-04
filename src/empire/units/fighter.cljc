;; mutation-tested: 2026-02-25
(ns empire.units.fighter)

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.units.impl.fighter/load-methods!)
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

(defmulti consume-fuel
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti refuel
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti bingo?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti out-of-fuel?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti can-land-at-city?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti can-land-on-carrier?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))
