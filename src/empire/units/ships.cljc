;; mutation-tested: 2026-02-25
(ns empire.units.ships
  "Dispatch contracts for simple naval units.")

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.units.impl.ships/load-methods!)
       (reset! methods-loaded? true))))

(defmulti config
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
