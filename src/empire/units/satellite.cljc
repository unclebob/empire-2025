;; mutation-tested: 2026-02-25
(ns empire.units.satellite)

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.units.impl.satellite/load-methods!)
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

(defmulti extend-target-to-boundary
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti calculate-bounce-target
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti move-one-step
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))
