;; mutation-tested: no
(ns empire.application.unit-stamping
  "Abstract application port for unit stamping decisions.")

(defmulti stamp-computer-fields
  "Applies computer-specific initial fields when stamping a produced unit."
  (fn [& _] :default))

(defmulti apply-coast-walk-fields
  "Optionally assigns coast-walk mode to newly produced computer armies."
  (fn [& _] :default))

(defmulti apply-random-explore-fields
  "Optionally assigns random-explore mode to newly produced computer armies."
  (fn [& _] :default))
