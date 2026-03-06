;; mutation-tested: no
(ns empire.config.domain.core.unit-metrics)

(def ^:private naval-units
  #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn naval-unit?
  [unit-type]
  (contains? naval-units unit-type))

(defn scale-by-hits
  "VMS ceiling division scaling helper."
  [base-value current-hits max-hits]
  (quot (+ (* base-value current-hits) (dec max-hits)) max-hits))

(defn effective-speed
  [base-speed current-hits max-hits]
  (scale-by-hits base-speed current-hits max-hits))

(defn effective-capacity
  [base-cap current-hits max-hits]
  (scale-by-hits base-cap current-hits max-hits))
