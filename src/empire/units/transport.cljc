;; mutation-tested: 2026-02-25
(ns empire.units.transport)

#?(:clj
   (defonce ^:private methods-loaded? (atom false)))

#?(:clj
   (defn- ensure-methods-loaded!
     []
     (when-not @methods-loaded?
       (requiring-resolve 'empire.units.impl.transport/load-methods!)
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

(defmulti has-armies?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti has-awake-armies?
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti add-army
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti remove-army
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti wake-armies
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti sleep-armies
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))

(defmulti remove-awake-army
  (fn [& _]
    #?(:clj (ensure-methods-loaded!))
    :default))
