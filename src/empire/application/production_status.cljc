;; mutation-tested: no
(ns empire.application.production-status)

(def ^:private unit-type-order
  [:army :fighter :transport :destroyer :submarine :patrol-boat :carrier :battleship :satellite])

(def ^:private unit-type-labels
  {:army "A" :fighter "F" :transport "T" :destroyer "D" :submarine "S"
   :patrol-boat "P" :carrier "C" :battleship "B" :satellite "Z"})

(defmulti format-production-status
  "Formats production status string: unit counts and exploration %.
   Format: A:n F:n T:n D:n S:n P:n C:n B:n Z:n | nn%"
  (fn [& _]
    (try
      (require 'empire.application.impl.production-status)
      (catch #?(:clj Throwable :cljs :default) _
        nil))
    :default))
