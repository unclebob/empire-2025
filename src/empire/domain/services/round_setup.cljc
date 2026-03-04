;; mutation-tested: no
(ns empire.domain.services.round-setup)

(defonce ^:private methods-loaded?
  (delay
    (try
      (require 'empire.domain.services.impl.round-setup)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- ensure-methods-loaded!
  []
  @methods-loaded?
  nil)

(defmulti dead-unit? (fn [& _] (ensure-methods-loaded!) :default))

(defmulti computer-carrier? (fn [& _] (ensure-methods-loaded!) :default))

(defmulti bingo-fuel? (fn [& _] (ensure-methods-loaded!) :default))

(defmulti fuel-action (fn [& _] (ensure-methods-loaded!) :default))
