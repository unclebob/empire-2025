(ns empire.properties.combat-spec
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [empire.config.domain.model.combat :as combat]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.properties.check :as p]
            [speclj.core :refer :all]))

(def unit-type-gen
  (gen/elements [:army :fighter :transport :carrier
                 :destroyer :submarine :battleship :patrol-boat]))

(def unit-gen
  (gen/hash-map :type unit-type-gen
                :hits (gen/choose 1 12)))

(defn- damage-to
  [log side]
  (reduce + 0 (map :damage (filter #(= side (:hit %)) log))))

(describe "combat resolution properties"
  (it "ends with exactly one destroyed unit and a living survivor"
    (p/check 80
             (prop/for-all [attacker unit-gen
                            defender unit-gen]
               (let [{:keys [winner survivor log]} (combat/resolve-combat attacker defender)
                     loser-side (if (= winner :attacker) :defender :attacker)
                     winner-unit (if (= winner :attacker) attacker defender)
                     loser-unit (if (= winner :attacker) defender attacker)]
                 (and (contains? #{:attacker :defender} winner)
                      (seq log)
                      (pos? (:hits survivor))
                      (= (:type winner-unit) (:type survivor))
                      (>= (damage-to log loser-side) (:hits loser-unit))
                      (= (:hits survivor)
                         (- (:hits winner-unit) (damage-to log winner))))))))

  (it "combat outcome names the destroyed unit"
    (p/check 40
             (prop/for-all [attacker-type unit-type-gen
                            defender-type unit-type-gen
                            winner (gen/elements [:attacker :defender])]
               (let [loser (if (= winner :attacker) defender-type attacker-type)
                     text (combat/format-combat-outcome attacker-type defender-type winner)]
                 (re-find (re-pattern (str "(?i)" (name loser))) text)))))

  (it "each fight-round damages exactly one unit by the opponent strength"
    (p/check 50
             (prop/for-all [attacker unit-gen
                            defender unit-gen]
               (let [[new-a new-d entry] (combat/fight-round attacker defender)]
                 (or (and (= :defender (:hit entry))
                          (= new-a attacker)
                          (= (:hits new-d) (- (:hits defender) (dispatcher/strength (:type attacker))))
                          (= (:damage entry) (dispatcher/strength (:type attacker))))
                     (and (= :attacker (:hit entry))
                          (= new-d defender)
                          (= (:hits new-a) (- (:hits attacker) (dispatcher/strength (:type defender))))
                          (= (:damage entry) (dispatcher/strength (:type defender))))))))))
