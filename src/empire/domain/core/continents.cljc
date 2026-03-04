;; mutation-tested: no
(ns empire.domain.core.continents)

(defonce ^:private methods-loaded?
  (delay
    (try
      (require 'empire.domain.core.impl.continents)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- ensure-methods-loaded!
  []
  @methods-loaded?
  nil)

(defmulti on-same-continent?
  "Returns true if two country-ids are on the same landmass."
  (fn [& _] (ensure-methods-loaded!) :default))

(defmulti merge-continents
  "Returns updated union-find groups after linking two country-ids."
  (fn [& _] (ensure-methods-loaded!) :default))
