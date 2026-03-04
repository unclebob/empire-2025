;; mutation-tested: no
(ns empire.domain.core.refueling)

(defonce ^:private methods-loaded?
  (delay
    (try
      (require 'empire.domain.core.impl.refueling)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- ensure-methods-loaded!
  []
  @methods-loaded?
  nil)

(defmulti computer-city-cell? (fn [& _] (ensure-methods-loaded!) :default))

(defmulti computer-carrier-cell? (fn [& _] (ensure-methods-loaded!) :default))

(defmulti scan-refueling-positions
  "Scans a map and returns {:cities #{[x y]} :carriers #{[x y]}}."
  (fn [& _] (ensure-methods-loaded!) :default))
