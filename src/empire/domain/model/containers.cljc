;; mutation-tested: no
(ns empire.domain.model.containers)

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.domain.model.impl.containers/load-methods!)
       (reset! methods-loaded? true))))

(defmulti wake-transport-armies
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti sleep-transport-armies
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti remove-awake-transport-army
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti disembarked-army
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti moving-disembarked-army
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti exploring-disembarked-army
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti wake-carrier-fighters
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti sleep-carrier-fighters
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti first-step-toward
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti launched-fighter
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))
