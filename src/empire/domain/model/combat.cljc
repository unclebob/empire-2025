;; mutation-tested: no
(ns empire.domain.model.combat)

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.domain.model.impl.combat/load-methods!)
       (reset! methods-loaded? true))))

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
