(ns empire.acceptance.parser.when-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser.when :as when-parser]))

(describe "parse-when"
    (it "parses simple key press - direction with attention"
      (let [lines ["WHEN the player presses d."]
            ctx {:has-waiting-for-input true}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :d :input-fn :handle-key}]
                 (:whens result))))

    (it "parses uppercase direction key"
      (let [lines ["WHEN the player presses D."]
            ctx {:has-waiting-for-input true}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :D :input-fn :key-down}]
                 (:whens result))))

    (it "parses non-direction key"
      (let [lines ["WHEN the player presses s."]
            ctx {:has-waiting-for-input true}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :s :input-fn :key-down}]
                 (:whens result))))

    (it "parses space key"
      (let [lines ["WHEN the player presses space."]
            ctx {:has-waiting-for-input true}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :space :input-fn :key-down}]
                 (:whens result))))

    (it "parses battle win for army"
      (let [lines ["WHEN the player presses d and wins the battle."]
            ctx {:has-waiting-for-input true :unit-types #{"A"}}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :battle :key :d :outcome :win :combat-type :army}]
                 (:whens result))))

    (it "parses battle win for ship"
      (let [lines ["WHEN the player presses d and wins the battle."]
            ctx {:has-waiting-for-input true :unit-types #{"D"}}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :battle :key :d :outcome :win :combat-type :ship}]
                 (:whens result))))

    (it "parses battle lose for ship"
      (let [lines ["WHEN the player presses d and loses the battle."]
            ctx {:has-waiting-for-input true :unit-types #{"D"}}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :battle :key :d :outcome :lose :combat-type :ship}]
                 (:whens result))))

    (it "parses backtick command"
      (let [lines ["WHEN the mouse is at cell [0 0] and the player presses backtick then A."]
            ctx {}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :backtick :key :A :mouse-cell [0 0]}]
                 (:whens result))))

    (it "parses new round starts"
      (let [lines ["WHEN a new round starts."]
            result (when-parser/parse-when lines {})]
        (should= [{:type :start-new-round}]
                 (:whens result))))

    (it "parses next round begins"
      (let [lines ["WHEN the next round begins"]
            result (when-parser/parse-when lines {})]
        (should= [{:type :start-new-round}]
                 (:whens result))))

    (it "parses game advances"
      (let [lines ["WHEN the game advances."]
            result (when-parser/parse-when lines {})]
        (should= [{:type :advance-game}]
                 (:whens result))))

    (it "parses player items processed"
      (let [lines ["WHEN player items are processed."]
            result (when-parser/parse-when lines {})]
        (should= [{:type :process-player-items}]
                 (:whens result))))

    (it "parses key press and advance until unit waiting"
      (let [lines ["WHEN the player presses D and the game advances until F is waiting for input."]
            ctx {:has-waiting-for-input true}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :D :input-fn :key-down}
                   {:type :advance-until-waiting :unit "F"}]
                 (:whens result))))

    (it "parses new round starts and advance until unit waiting"
      (let [lines ["WHEN a new round starts and F is waiting for input."]
            result (when-parser/parse-when lines {})]
        (should= [{:type :start-new-round}
                   {:type :advance-until-waiting :unit "F"}]
                 (:whens result))))

    (it "parses waiting for input and key press"
      (let [lines ["WHEN C is waiting for input and the player presses u."]
            ctx {}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :waiting-for-input :unit "C" :set-mode true}
                   {:type :key-press :key :u :input-fn :key-down}]
                 (:whens result))))

    (it "parses standalone waiting for input"
      (let [lines ["WHEN F is waiting for input."]
            ctx {}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :waiting-for-input :unit "F" :set-mode true}]
                 (:whens result))))

    (it "parses standalone waiting for input with mode already set"
      (let [lines ["WHEN F is waiting for input."]
            ctx {:units-with-mode #{"F"}}
            result (when-parser/parse-when lines ctx)]
        (should= [{:type :waiting-for-input :unit "F" :set-mode true}]
                 (:whens result))))

    (it "parses visibility updates"
      (let [lines ["WHEN visibility updates."]
            result (when-parser/parse-when lines {})]
        (should= [{:type :visibility-update}]
                 (:whens result))))

    (it "parses mouse-at-key"
      (let [lines ["WHEN the mouse is at cell [0 1] and the player presses period."]
            result (when-parser/parse-when lines {})]
        (should= [{:type :mouse-at-key :coords [0 1] :key :period}]
                 (:whens result))))

    (it "warns on unconsumed trailing text after simple key press"
      (let [lines ["WHEN the player presses D and something unexpected."]
            ctx {:has-waiting-for-input true}
            output (with-out-str (when-parser/parse-when lines ctx))]
        (should-contain "WARNING" output)))

    (it "parses production for X is evaluated"
      (let [lines ["WHEN production for X is evaluated."]
            result (when-parser/parse-when lines {})]
        (should= [{:type :evaluate-production :city "X"}]
                 (:whens result))))

    (it "parses 'the computer chooses production at X'"
      (let [lines ["WHEN the computer chooses production at X."]
            result (when-parser/parse-when lines {})]
        (should= [{:type :evaluate-production :city "X"}]
                 (:whens result)))))
