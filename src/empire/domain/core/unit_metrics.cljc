;; mutation-tested: no
(ns empire.domain.core.unit-metrics)

(def ^:private naval-units
  #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defonce ^:private methods-loaded?
  (delay
    (try
      (require 'empire.domain.core.impl.unit-metrics)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- ensure-methods-loaded!
  []
  @methods-loaded?
  nil)

(defmulti naval-unit?
  (fn [& _] (ensure-methods-loaded!) :default))

(defmulti scale-by-hits
  "VMS ceiling division scaling helper."
  (fn [& _] (ensure-methods-loaded!) :default))

(defmulti effective-speed (fn [& _] (ensure-methods-loaded!) :default))

(defmulti effective-capacity (fn [& _] (ensure-methods-loaded!) :default))
