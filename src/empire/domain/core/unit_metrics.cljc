;; mutation-tested: no
(ns empire.domain.core.unit-metrics)

(def ^:private naval-units
  #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defmulti naval-unit?
  (fn [& _] :default))

(defmulti scale-by-hits
  "VMS ceiling division scaling helper."
  (fn [& _] :default))

(defmulti effective-speed (fn [& _] :default))

(defmulti effective-capacity (fn [& _] :default))

(defmethod naval-unit? :default
  [unit-type]
  (contains? naval-units unit-type))

(defmethod scale-by-hits :default
  [base-value current-hits max-hits]
  (quot (+ (* base-value current-hits) (dec max-hits)) max-hits))

(defmethod effective-speed :default
  [base-speed current-hits max-hits]
  (scale-by-hits base-speed current-hits max-hits))

(defmethod effective-capacity :default
  [base-cap current-hits max-hits]
  (scale-by-hits base-cap current-hits max-hits))
