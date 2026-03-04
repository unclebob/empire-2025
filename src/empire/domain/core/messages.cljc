;; mutation-tested: no
(ns empire.domain.core.messages)

(defonce ^:private methods-loaded?
  (delay
    (try
      (require 'empire.domain.core.impl.messages)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- ensure-methods-loaded!
  []
  @methods-loaded?
  nil)

(defmulti expires-at
  "Returns an absolute expiration timestamp in milliseconds."
  (fn [& _] (ensure-methods-loaded!) :default))
