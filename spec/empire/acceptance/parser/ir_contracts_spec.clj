(ns empire.acceptance.parser.ir-contracts-spec
  (:require [clojure.spec.alpha :as s]
            [empire.acceptance.parser.given :as given]
            [empire.acceptance.parser.ir-contracts :as contracts]
            [empire.acceptance.parser.then :as then]
            [empire.acceptance.parser.when :as when]
            [speclj.core :refer :all]))

(defn- valid? [spec value]
  (s/valid? spec value))

(describe "parser IR contracts"
  (it "validates representative GIVEN IR nodes"
    (let [result (given/parse-given
                   ["GIVEN game map"
                    "  X~"
                    "GIVEN X belongs to country 1."
                    "GIVEN t has army-count 6."
                    "GIVEN t has one fighter."
                    "GIVEN computer controls 12 cities."
                    "GIVEN destination [2 3]."]
                   {})]
      (should (valid? ::contracts/given-result result))
      (should (every? #(valid? ::contracts/given-ir %) (:givens result)))))

  (it "validates representative WHEN IR nodes"
    (let [result (when/parse-when
                   ["WHEN C is waiting for input and the player presses d."
                    "WHEN the player presses D and wins the battle."
                    "WHEN production for X is evaluated."
                    "WHEN computer destroyer D is processed."
                    "WHEN 3 rounds are complete."
                    "WHEN the player presses space."]
                   {:has-waiting-for-input true
                    :unit-types #{"D"}})]
      (should (valid? ::contracts/when-result result))
      (should (every? #(valid? ::contracts/when-ir %) (:whens result)))))

  (it "validates representative THEN IR nodes"
    (let [result (then/parse-then
                   ["THEN player map"
                    "  XX"
                    "THEN A is at [1 0]."
                    "THEN A has mode sentry."
                    "THEN there is no F on the map."
                    "THEN the attention message contains :army-found-city."
                    "THEN production at X is carrier."
                    "THEN waiting-for-input."]
                   {})]
      (should (valid? ::contracts/then-result result))
      (should (every? #(valid? ::contracts/then-ir %) (:thens result)))))
  )
