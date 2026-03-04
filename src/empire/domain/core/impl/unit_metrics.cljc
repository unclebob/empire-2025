;; mutation-tested: no
(ns empire.domain.core.impl.unit-metrics
  (:require [empire.domain.core.unit-metrics :as unit-metrics]))

(def ^:private naval-units
  #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defmethod unit-metrics/naval-unit? :default
  [unit-type]
  (contains? naval-units unit-type))

(defmethod unit-metrics/scale-by-hits :default
  [base-value current-hits max-hits]
  (quot (+ (* base-value current-hits) (dec max-hits)) max-hits))

(defmethod unit-metrics/effective-speed :default
  [base-speed current-hits max-hits]
  (unit-metrics/scale-by-hits base-speed current-hits max-hits))

(defmethod unit-metrics/effective-capacity :default
  [base-cap current-hits max-hits]
  (unit-metrics/scale-by-hits base-cap current-hits max-hits))
