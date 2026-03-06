;; mutation-tested: 2026-03-02
(ns empire.computer.stamping
  (:require [empire.game-mechanics.services.unit-stamping :as unit-stamping]))

(defn stamp-computer-fields
  [unit cell]
  (unit-stamping/stamp-computer-fields unit cell))

(defn apply-coast-walk-fields
  [unit item cell coords]
  (unit-stamping/apply-coast-walk-fields unit item cell coords))

(defn apply-random-explore-fields
  [unit item cell]
  (unit-stamping/apply-random-explore-fields unit item cell))
