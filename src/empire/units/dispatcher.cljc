;; mutation-tested: no
(ns empire.units.dispatcher
  "Abstract dispatcher contract for unit properties/behavior.")

(defmulti speed
  (fn [& _]
    :default))

(defmulti cost
  (fn [& _]
    :default))

(defmulti hits
  (fn [& _]
    :default))

(defmulti display-char
  (fn [& _]
    :default))

(defmulti visibility-radius
  (fn [& _]
    :default))

(defmulti strength
  (fn [& _]
    :default))

(defmulti initial-state
  (fn [& _]
    :default))

(defmulti can-move-to?
  (fn [& _]
    :default))

(defmulti needs-attention?
  (fn [& _]
    :default))

(defmulti effective-speed
  (fn [& _]
    :default))

(defmulti capacity
  (fn [& _]
    :default))

(defmulti effective-capacity
  (fn [& _]
    :default))

(defmulti naval-units
  (fn [& _]
    :default))

(defmulti naval-unit?
  (fn [& _]
    :default))
