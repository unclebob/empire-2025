;; mutation-tested: 2026-02-25
(ns empire.units.satellite)

(defmulti initial-state
  (fn [& _]
    :default))

(defmulti can-move-to?
  (fn [& _]
    :default))

(defmulti needs-attention?
  (fn [& _]
    :default))

(defmulti extend-target-to-boundary
  (fn [& _]
    :default))

(defmulti calculate-bounce-target
  (fn [& _]
    :default))

(defmulti move-one-step
  (fn [& _]
    :default))
