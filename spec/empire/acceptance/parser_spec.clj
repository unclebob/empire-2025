(ns empire.acceptance.parser-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser :as parser]
            [empire.config :as config]))

(describe "first-matching-pattern"
  (it "returns nil for empty patterns"
    (let [fmp @#'parser/first-matching-pattern]
      (should-be-nil (fmp [] "hello"))))

  (it "returns nil when no pattern matches"
    (let [fmp @#'parser/first-matching-pattern
          patterns [{:regex #"^foo" :handler (fn [_] :foo)}
                    {:regex #"^bar" :handler (fn [_] :bar)}]]
      (should-be-nil (fmp patterns "hello"))))

  (it "returns first matching handler result"
    (let [fmp @#'parser/first-matching-pattern
          patterns [{:regex #"^hello" :handler (fn [_] :first)}
                    {:regex #"^hello" :handler (fn [_] :second)}]]
      (should= :first (fmp patterns "hello world"))))

  (it "passes match to handler"
    (let [fmp @#'parser/first-matching-pattern
          patterns [{:regex #"(\d+)\s+(\w+)" :handler (fn [[_ n w]] {:n n :w w})}]]
      (should= {:n "42" :w "things"} (fmp patterns "42 things")))))

(describe "first-matching-pattern-with-context"
  (it "passes match and context to handler"
    (let [fmpc @#'parser/first-matching-pattern-with-context
          patterns [{:regex #"(\w+)" :handler (fn [[_ w] ctx] {:word w :ctx ctx})}]]
      (should= {:word "hi" :ctx {:x 1}} (fmpc patterns "hi" {:x 1}))))

  (it "returns nil when no pattern matches"
    (let [fmpc @#'parser/first-matching-pattern-with-context
          patterns [{:regex #"^zzz" :handler (fn [_ _] :nope)}]]
      (should-be-nil (fmpc patterns "hello" {})))))

(describe "acceptance test parser"

  (describe "split-into-tests"
    (it "splits a simple single test"
      (let [lines ["; Comment"
                   ""
                   ";==============================================================="
                   "; Army put to sentry mode."
                   ";==============================================================="
                   "GIVEN game map"
                   "  A#"
                   "GIVEN A is waiting for input."
                   ""
                   "WHEN the player presses s."
                   ""
                   "THEN A has mode sentry."]
            tests (parser/split-into-tests lines)]
        (should= 1 (count tests))
        (should= "Army put to sentry mode." (:description (first tests)))
        (should= 6 (:line (first tests)))))

    (it "splits multiple tests"
      (let [lines [";==============================================================="
                   "; Test one."
                   ";==============================================================="
                   "GIVEN game map"
                   "  A#"
                   ""
                   "WHEN the player presses s."
                   ""
                   "THEN A has mode sentry."
                   ""
                   ";==============================================================="
                   "; Test two."
                   ";==============================================================="
                   "GIVEN game map"
                   "  A+"
                   ""
                   "WHEN the player presses d."
                   ""
                   "THEN A is at [1 0]."]
            tests (parser/split-into-tests lines)]
        (should= 2 (count tests))
        (should= "Test one." (:description (first tests)))
        (should= 4 (:line (first tests)))
        (should= "Test two." (:description (second tests)))
        (should= 14 (:line (second tests))))))

  (describe "parse-given"
    (it "parses game map"
      (let [lines ["GIVEN game map" "  A#" "  ##"]
            result (parser/parse-given lines {})]
        (should= [{:type :map :target :game-map :rows ["A#" "##"]}]
                 (:givens result))))

    (it "parses player map"
      (let [lines ["GIVEN player map" "  A." "  .."]
            result (parser/parse-given lines {})]
        (should= [{:type :map :target :player-map :rows ["A." ".."]}]
                 (:givens result))))

    (it "parses computer map"
      (let [lines ["GIVEN computer map" "  ~~a~" "  ####"]
            result (parser/parse-given lines {})]
        (should= [{:type :map :target :computer-map :rows ["~~a~" "####"]}]
                 (:givens result))))

    (it "parses bare map (defaults to game-map)"
      (let [lines ["GIVEN map" "  A#"]
            result (parser/parse-given lines {})]
        (should= [{:type :map :target :game-map :rows ["A#"]}]
                 (:givens result))))

    (it "parses unit properties - mode"
      (let [lines ["A is awake."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "A" :props {:mode :awake}}]
                 (:givens result))))

    (it "parses unit properties - mode and fuel"
      (let [lines ["F has fuel 32."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "F" :props {:fuel 32}}]
                 (:givens result))))

    (it "parses unit properties - sentry with fuel"
      (let [lines ["F is sentry with fuel 9."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "F" :props {:mode :sentry :fuel 9}}]
                 (:givens result))))

    (it "parses unit properties - explore"
      (let [lines ["A is explore."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "A" :props {:mode :explore}}]
                 (:givens result))))

    (it "parses unit props with natural language army count"
      (let [lines ["T is awake with two armies."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "T" :props {:mode :awake :army-count 2}}]
                 (:givens result))))

    (it "parses unit props with one army"
      (let [lines ["T is awake with one army."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "T" :props {:mode :awake :army-count 1}}]
                 (:givens result))))

    (it "parses unit props with three armies"
      (let [lines ["T is awake with three armies."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "T" :props {:mode :awake :army-count 3}}]
                 (:givens result))))

    (it "parses unit props with container props"
      (let [lines ["C is sentry with two fighters and no awake fighters."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "C" :props {:mode :sentry}}
                  {:type :container-state :target "C" :props {:fighter-count 2 :awake-fighters 0}}]
                 (:givens result))))

    (it "parses unit props with numeric container props"
      (let [lines ["C is sentry with fighter-count 2 and awake-fighters 1."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "C" :props {:mode :sentry}}
                  {:type :container-state :target "C" :props {:fighter-count 2 :awake-fighters 1}}]
                 (:givens result))))

    (it "parses unit props with hits and no fighters"
      (let [lines ["C has hits 5 and no fighters."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "C" :props {:hits 5}}
                  {:type :container-state :target "C" :props {:fighter-count 0}}]
                 (:givens result))))

    (it "parses waiting-for-input"
      (let [lines ["GIVEN A is waiting for input."]
            result (parser/parse-given lines {})]
        (should= [{:type :waiting-for-input :unit "A" :set-mode true}]
                 (:givens result))))

    (it "does not set-mode when mode already set"
      (let [lines ["F has fuel 20." "GIVEN F is waiting for input."]
            result (parser/parse-given lines {})]
        (should= 2 (count (:givens result)))
        (should= {:type :waiting-for-input :unit "F" :set-mode true}
                 (second (:givens result)))))

    (it "does not set-mode when mode explicitly set prior"
      (let [lines ["F is sentry with fuel 9." "GIVEN F is waiting for input."]
            result (parser/parse-given lines {})]
        (should= {:type :waiting-for-input :unit "F" :set-mode false}
                 (second (:givens result)))))

    (it "parses production"
      (let [lines ["GIVEN production at O is army."]
            result (parser/parse-given lines {})]
        (should= [{:type :production :city "O" :item :army}]
                 (:givens result))))

    (it "parses production with remaining rounds"
      (let [lines ["GIVEN production at O is transport with 1 round remaining."]
            result (parser/parse-given lines {})]
        (should= [{:type :production :city "O" :item :transport :remaining-rounds 1}]
                 (:givens result))))

    (it "parses round"
      (let [lines ["GIVEN round 5."]
            result (parser/parse-given lines {})]
        (should= [{:type :round :value 5}]
                 (:givens result))))

    (it "parses destination"
      (let [lines ["GIVEN destination [3 7]."]
            result (parser/parse-given lines {})]
        (should= [{:type :destination :coords [3 7]}]
                 (:givens result))))

    (it "parses cell props"
      (let [lines ["GIVEN cell [0 0] has awake-fighters 1 and fighter-count 1."]
            result (parser/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:awake-fighters 1 :fighter-count 1}}]
                 (:givens result))))

    (it "parses cell props with coordinate value"
      (let [lines ["GIVEN cell [0 0] has marching-orders [4 0]."]
            result (parser/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:marching-orders [4 0]}}]
                 (:givens result))))

    (it "parses cell props with keyword value"
      (let [lines ["GIVEN cell [0 0] has marching-orders lookaround."]
            result (parser/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:marching-orders :lookaround}}]
                 (:givens result))))

    (it "parses cell props with spawn-orders alias"
      (let [lines ["GIVEN cell [0 0] has spawn-orders [4 0]."]
            result (parser/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:marching-orders [4 0]}}]
                 (:givens result))))

    (it "parses cell props with spawn-orders lookaround"
      (let [lines ["GIVEN cell [0 0] has spawn-orders lookaround."]
            result (parser/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:marching-orders :lookaround}}]
                 (:givens result))))

    (it "parses cell props with flight-orders alias"
      (let [lines ["GIVEN cell [0 0] has flight-orders [11 0]."]
            result (parser/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:flight-path [11 0]}}]
                 (:givens result))))

    (it "parses cell props with flight-path coordinate"
      (let [lines ["GIVEN cell [0 0] has flight-path [4 0]."]
            result (parser/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:flight-path [4 0]}}]
                 (:givens result))))

    (it "parses player-items single"
      (let [lines ["GIVEN player-items F."]
            result (parser/parse-given lines {})]
        (should= [{:type :player-items :items ["F"]}]
                 (:givens result))))

    (it "parses player-items multiple"
      (let [lines ["GIVEN player-items are A, T, O."]
            result (parser/parse-given lines {})]
        (should= [{:type :player-items :items ["A" "T" "O"]}]
                 (:givens result))))

    (it "parses player units single"
      (let [lines ["GIVEN player units V."]
            result (parser/parse-given lines {})]
        (should= [{:type :player-items :items ["V"]}]
                 (:givens result))))

    (it "parses player units multiple"
      (let [lines ["GIVEN player units are A, T, O."]
            result (parser/parse-given lines {})]
        (should= [{:type :player-items :items ["A" "T" "O"]}]
                 (:givens result))))

    (it "parses container state - city airport"
      (let [lines ["GIVEN O has one fighter in its airport."]
            result (parser/parse-given lines {})]
        (should= [{:type :container-state :target "O" :props {:fighter-count 1 :awake-fighters 1}}]
                 (:givens result))))

    (it "parses container state - no fighters"
      (let [lines ["C has no fighters."]
            result (parser/parse-given lines {})]
        (should= [{:type :container-state :target "C" :props {:fighter-count 0}}]
                 (:givens result))))

    (it "parses container state - natural language count"
      (let [lines ["GIVEN C has three fighters."]
            result (parser/parse-given lines {})]
        (should= [{:type :container-state :target "C" :props {:fighter-count 3}}]
                 (:givens result))))

    (it "parses waiting-for-input for city"
      (let [lines ["GIVEN O is waiting for input."]
            result (parser/parse-given lines {})]
        (should= [{:type :waiting-for-input :unit "O" :set-mode true}]
                 (:givens result))))

    (it "parses 'the game is waiting for input' in GIVEN"
      (let [lines ["GIVEN the game is waiting for input."]
            result (parser/parse-given lines {})]
        (should= [{:type :waiting-for-input-state}]
                 (:givens result))))

    (it "parses unit target"
      (let [lines ["A's target is +"]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-target :unit "A" :target "+"}]
                 (:givens result))))

    (it "parses unit target with label char"
      (let [lines ["D's target is ="]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-target :unit "D" :target "="}]
                 (:givens result))))

    (it "parses generic unit property - country-id"
      (let [lines ["a has country-id 1."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "a" :props {:country-id 1}}]
                 (:givens result))))

    (it "parses generic unit property - patrol-country-id"
      (let [lines ["p has patrol-country-id 1."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "p" :props {:patrol-country-id 1}}]
                 (:givens result))))

    (it "parses generic unit property - army-count on transport"
      (let [lines ["t has army-count 6."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "t" :props {:army-count 6}}]
                 (:givens result))))

    (it "parses generic unit property - boolean true"
      (let [lines ["t has been-to-sea true."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "t" :props {:been-to-sea true}}]
                 (:givens result))))

    (it "parses generic unit property combined with mode"
      (let [lines ["a is awake with country-id 1."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "a" :props {:mode :awake :country-id 1}}]
                 (:givens result))))

    (it "parses 'X belongs to country 1' for city"
      (let [lines ["GIVEN X belongs to country 1."]
            result (parser/parse-given lines {})]
        (should= [{:type :city-prop :city "X" :prop :country-id :value 1}]
                 (:givens result))))

    (it "parses 't belongs to country 1' for unit"
      (let [lines ["t belongs to country 1."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "t" :props {:country-id 1}}]
                 (:givens result))))

    (it "parses 'p patrols for country 1' for patrol boat"
      (let [lines ["p patrols for country 1."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "p" :props {:patrol-country-id 1}}]
                 (:givens result))))

    (it "parses 't has 6 armies' as natural language army-count"
      (let [lines ["t has 6 armies."]
            result (parser/parse-given lines {})]
        (should= [{:type :unit-props :unit "t" :props {:army-count 6}}]
                 (:givens result))))

    (it "parses 'computer controls N cities' as stub"
      (let [lines ["GIVEN computer controls 12 cities."]
            result (parser/parse-given lines {})]
        (should= [{:type :stub
                   :bindings [{:var "empire.computer.production/count-computer-cities"
                               :value "(constantly 12)"}]}]
                 (:givens result))))

    (it "parses 'a valid carrier position exists' as stub"
      (let [lines ["GIVEN a valid carrier position exists."]
            result (parser/parse-given lines {})]
        (should= [{:type :stub
                   :bindings [{:var "empire.computer.ship/find-carrier-position"
                               :value "(constantly [0 0])"}]}]
                 (:givens result))))

    (it "parses 'X has a destroyer with 2 hits in its shipyard'"
      (let [lines ["X has a destroyer with 2 hits in its shipyard."]
            result (parser/parse-given lines {})]
        (should= [{:type :shipyard-state :city "X" :ship-type :destroyer :hits 2}]
                 (:givens result))))

    (it "parses 'X has a computer army'"
      (let [lines ["X has a computer army."]
            result (parser/parse-given lines {})]
        (should= [{:type :city-unit :city "X" :unit-type :army :owner :computer}]
                 (:givens result)))))

  (describe "parse-when"
    (it "parses simple key press - direction with attention"
      (let [lines ["WHEN the player presses d."]
            ctx {:has-waiting-for-input true}
            result (parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :d :input-fn :handle-key}]
                 (:whens result))))

    (it "parses uppercase direction key"
      (let [lines ["WHEN the player presses D."]
            ctx {:has-waiting-for-input true}
            result (parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :D :input-fn :key-down}]
                 (:whens result))))

    (it "parses non-direction key"
      (let [lines ["WHEN the player presses s."]
            ctx {:has-waiting-for-input true}
            result (parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :s :input-fn :key-down}]
                 (:whens result))))

    (it "parses space key"
      (let [lines ["WHEN the player presses space."]
            ctx {:has-waiting-for-input true}
            result (parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :space :input-fn :key-down}]
                 (:whens result))))

    (it "parses battle win for army"
      (let [lines ["WHEN the player presses d and wins the battle."]
            ctx {:has-waiting-for-input true :unit-types #{"A"}}
            result (parser/parse-when lines ctx)]
        (should= [{:type :battle :key :d :outcome :win :combat-type :army}]
                 (:whens result))))

    (it "parses battle win for ship"
      (let [lines ["WHEN the player presses d and wins the battle."]
            ctx {:has-waiting-for-input true :unit-types #{"D"}}
            result (parser/parse-when lines ctx)]
        (should= [{:type :battle :key :d :outcome :win :combat-type :ship}]
                 (:whens result))))

    (it "parses battle lose for ship"
      (let [lines ["WHEN the player presses d and loses the battle."]
            ctx {:has-waiting-for-input true :unit-types #{"D"}}
            result (parser/parse-when lines ctx)]
        (should= [{:type :battle :key :d :outcome :lose :combat-type :ship}]
                 (:whens result))))

    (it "parses backtick command"
      (let [lines ["WHEN the mouse is at cell [0 0] and the player presses backtick then A."]
            ctx {}
            result (parser/parse-when lines ctx)]
        (should= [{:type :backtick :key :A :mouse-cell [0 0]}]
                 (:whens result))))

    (it "parses new round starts"
      (let [lines ["WHEN a new round starts."]
            result (parser/parse-when lines {})]
        (should= [{:type :start-new-round}]
                 (:whens result))))

    (it "parses next round begins"
      (let [lines ["WHEN the next round begins"]
            result (parser/parse-when lines {})]
        (should= [{:type :start-new-round}]
                 (:whens result))))

    (it "parses game advances"
      (let [lines ["WHEN the game advances."]
            result (parser/parse-when lines {})]
        (should= [{:type :advance-game}]
                 (:whens result))))

    (it "parses player items processed"
      (let [lines ["WHEN player items are processed."]
            result (parser/parse-when lines {})]
        (should= [{:type :process-player-items}]
                 (:whens result))))

    (it "parses key press and advance until unit waiting"
      (let [lines ["WHEN the player presses D and the game advances until F is waiting for input."]
            ctx {:has-waiting-for-input true}
            result (parser/parse-when lines ctx)]
        (should= [{:type :key-press :key :D :input-fn :key-down}
                   {:type :advance-until-waiting :unit "F"}]
                 (:whens result))))

    (it "parses new round starts and advance until unit waiting"
      (let [lines ["WHEN a new round starts and F is waiting for input."]
            result (parser/parse-when lines {})]
        (should= [{:type :start-new-round}
                   {:type :advance-until-waiting :unit "F"}]
                 (:whens result))))

    (it "parses waiting for input and key press"
      (let [lines ["WHEN C is waiting for input and the player presses u."]
            ctx {}
            result (parser/parse-when lines ctx)]
        (should= [{:type :waiting-for-input :unit "C" :set-mode true}
                   {:type :key-press :key :u :input-fn :key-down}]
                 (:whens result))))

    (it "parses standalone waiting for input"
      (let [lines ["WHEN F is waiting for input."]
            ctx {}
            result (parser/parse-when lines ctx)]
        (should= [{:type :waiting-for-input :unit "F" :set-mode true}]
                 (:whens result))))

    (it "parses standalone waiting for input with mode already set"
      (let [lines ["WHEN F is waiting for input."]
            ctx {:units-with-mode #{"F"}}
            result (parser/parse-when lines ctx)]
        (should= [{:type :waiting-for-input :unit "F" :set-mode true}]
                 (:whens result))))

    (it "parses visibility updates"
      (let [lines ["WHEN visibility updates."]
            result (parser/parse-when lines {})]
        (should= [{:type :visibility-update}]
                 (:whens result))))

    (it "parses mouse-at-key"
      (let [lines ["WHEN the mouse is at cell [0 1] and the player presses period."]
            result (parser/parse-when lines {})]
        (should= [{:type :mouse-at-key :coords [0 1] :key :period}]
                 (:whens result))))

    (it "warns on unconsumed trailing text after simple key press"
      (let [lines ["WHEN the player presses D and something unexpected."]
            ctx {:has-waiting-for-input true}
            output (with-out-str (parser/parse-when lines ctx))]
        (should-contain "WARNING" output)))

    (it "parses production for X is evaluated"
      (let [lines ["WHEN production for X is evaluated."]
            result (parser/parse-when lines {})]
        (should= [{:type :evaluate-production :city "X"}]
                 (:whens result))))

    (it "parses 'the computer chooses production at X'"
      (let [lines ["WHEN the computer chooses production at X."]
            result (parser/parse-when lines {})]
        (should= [{:type :evaluate-production :city "X"}]
                 (:whens result)))))

  (describe "parse-then"
    (it "parses unit at position"
      (let [lines ["THEN A is at [0 2]."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-at :unit "A" :coords [0 2]}]
                 (:thens result))))

    (it "parses unit at named target"
      (let [lines ["THEN F is at =."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-at :unit "F" :target "="}]
                 (:thens result))))

    (it "parses unit at named target %"
      (let [lines ["THEN A is at %."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-at :unit "A" :target "%"}]
                 (:thens result))))

    (it "parses unit mode property"
      (let [lines ["THEN A has mode sentry."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "A" :property :mode :expected :sentry}]
                 (:thens result))))

    (it "parses unit mode with 'is'"
      (let [lines ["THEN A is awake."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "A" :property :mode :expected :awake}]
                 (:thens result))))

    (it "parses unit fuel property"
      (let [lines ["THEN F has fuel 19."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "F" :property :fuel :expected 19}]
                 (:thens result))))

    (it "parses unit owner property"
      (let [lines ["THEN A has owner player."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "A" :property :owner :expected :player}]
                 (:thens result))))

    (it "parses unit absent"
      (let [lines ["THEN there is no A on the map."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-absent :unit "A"}]
                 (:thens result))))

    (it "parses no F on the map"
      (let [lines ["THEN there is no F on the map."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-absent :unit "F"}]
                 (:thens result))))

    (it "parses there is no D"
      (let [lines ["and there is no D on the map."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-absent :unit "D"}]
                 (:thens result))))

    (it "parses unit present at coords"
      (let [lines ["THEN there is an A at [0 0]."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-present :unit "A" :coords [0 0]}]
                 (:thens result))))

    (it "parses unit present with 'a' article"
      (let [lines ["THEN there is a T at [0 0]."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-present :unit "T" :coords [0 0]}]
                 (:thens result))))

    (it "parses there is an F at target"
      (let [lines ["THEN there is an F at %."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-present :unit "F" :target "%"}]
                 (:thens result))))

    (it "parses message contains literal"
      (let [lines ["THEN the attention message contains \"fuel:20\"."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :text "fuel:20"}]
                 (:thens result))))

    (it "parses message contains config key"
      (let [lines ["THEN the attention message contains :army-found-city."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :army-found-city}]
                 (:thens result))))

    (it "parses message contains :cant-move-into-city"
      (let [lines ["THEN the attention message contains :cant-move-into-city."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :cant-move-into-city}]
                 (:thens result))))

    (it "parses turn message contains literal"
      (let [lines ["THEN the turn message contains \"Destroyer destroyed\"."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :turn :text "Destroyer destroyed"}]
                 (:thens result))))

    (it "parses turn message contains with hit edge"
      (let [lines ["THEN the turn message contains \"hit edge of map\"."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :turn :text "hit edge of map"}]
                 (:thens result))))

    (it "parses error message contains config key"
      (let [lines ["THEN the error message contains :fighter-out-of-fuel."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :error :config-key :fighter-out-of-fuel}]
                 (:thens result))))

    (it "parses error message is config key"
      (let [lines ["THEN the error message is :conquest-failed."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-is :area :error :config-key :conquest-failed}]
                 (:thens result))))

    (it "parses no message"
      (let [lines ["THEN there is no attention message."]
            result (parser/parse-then lines {})]
        (should= [{:type :no-message :area :attention}]
                 (:thens result))))

    (it "parses cell property"
      (let [lines ["THEN cell [1 0] has city-status player."]
            result (parser/parse-then lines {})]
        (should= [{:type :cell-prop :coords [1 0] :property :city-status :expected :player}]
                 (:thens result))))

    (it "parses 'cell [1 0] is a player city'"
      (let [lines ["THEN cell [1 0] is a player city."]
            result (parser/parse-then lines {})]
        (should= [{:type :cell-prop :coords [1 0] :property :city-status :expected :player}]
                 (:thens result))))

    (it "parses 'cell [1 0] is a computer city'"
      (let [lines ["THEN cell [1 0] is a computer city."]
            result (parser/parse-then lines {})]
        (should= [{:type :cell-prop :coords [1 0] :property :city-status :expected :computer}]
                 (:thens result))))

    (it "parses THEN cell with spawn-orders alias"
      (let [lines ["THEN cell [0 0] has spawn-orders lookaround."]
            result (parser/parse-then lines {})]
        (should= [{:type :cell-prop :coords [0 0] :property :marching-orders :expected :lookaround}]
                 (:thens result))))

    (it "parses cell type"
      (let [lines ["THEN cell [0 0] is a city."]
            result (parser/parse-then lines {})]
        (should= [{:type :cell-type :coords [0 0] :expected :city}]
                 (:thens result))))

    (it "parses waiting-for-input true"
      (let [lines ["THEN waiting-for-input."]
            result (parser/parse-then lines {})]
        (should= [{:type :waiting-for-input :expected true}]
                 (:thens result))))

    (it "parses not waiting-for-input"
      (let [lines ["THEN not waiting-for-input."]
            result (parser/parse-then lines {})]
        (should= [{:type :waiting-for-input :expected false}]
                 (:thens result))))

    (it "parses 'the game is waiting for input'"
      (let [lines ["THEN the game is waiting for input."]
            result (parser/parse-then lines {})]
        (should= [{:type :waiting-for-input :expected true}]
                 (:thens result))))

    (it "parses 'the game is not waiting for input'"
      (let [lines ["THEN the game is not waiting for input."]
            result (parser/parse-then lines {})]
        (should= [{:type :waiting-for-input :expected false}]
                 (:thens result))))

    (it "parses unit-at-next-round"
      (let [lines ["THEN at next round F will be at =."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-at-next-round :unit "F" :target "=" :at-next-round true}]
                 (:thens result))))

    (it "parses unit-at-next-round D"
      (let [lines ["THEN at next round D will be at =."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-at-next-round :unit "D" :target "=" :at-next-round true}]
                 (:thens result))))

    (it "parses eventually at"
      (let [lines ["THEN eventually A will be at %."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-eventually-at :unit "A" :target "%"}]
                 (:thens result))))

    (it "parses after N moves unit will be at target"
      (let [lines ["THEN after two moves F will be at =."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-after-moves :unit "F" :moves 2 :target "="}]
                 (:thens result))))

    (it "parses after N moves with numeric count"
      (let [lines ["THEN after 3 moves D will be at =."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-after-moves :unit "D" :moves 3 :target "="}]
                 (:thens result))))

    (it "parses after one step there is a unit at target"
      (let [lines ["THEN after one step there is an F at %."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-after-steps :unit "F" :steps 1 :target "%"}]
                 (:thens result))))

    (it "parses after N steps there is a unit at coords"
      (let [lines ["THEN after 2 steps there is an A at [1 0]."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-after-steps :unit "A" :steps 2 :coords [1 0]}]
                 (:thens result))))

    (it "parses and-continuation"
      (let [lines ["THEN F wakes up and asks for input,"
                   "and the out-of-fuel message is displayed."]
            result (parser/parse-then lines {})]
        (should (>= (count (:thens result)) 2))))

    (it "parses 'F is waiting for input'"
      (let [lines ["THEN F is waiting for input."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-waiting-for-input :unit "F"}]
                 (:thens result))))

    (it "parses compound then with 'at the next round O has one fighter' and 'no fighter on the map'"
      (let [lines ["THEN at the next round O has one fighter in its airport and there is no fighter on the map."]
            result (parser/parse-then lines {})]
        (should= 2 (count (:thens result)))
        (should= :container-prop (:type (first (:thens result))))
        (should= :unit-absent (:type (second (:thens result))))))

    (it "parses 'D occupies the s cell'"
      (let [lines ["THEN at the next round D occupies the s cell and there is no s."]
            result (parser/parse-then lines {})]
        (should (>= (count (:thens result)) 2))
        (should= :unit-occupies-cell (:type (first (:thens result))))))

    (it "parses 's remains unmoved'"
      (let [lines ["THEN at the next round s remains unmoved."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-unmoved :unit "s" :at-next-round true}]
                 (:thens result))))

    (it "parses 'O has no fighters'"
      (let [lines ["and O has no fighters."]
            result (parser/parse-then lines {})]
        (should= [{:type :container-prop :target "O" :property :fighter-count :expected 0 :lookup :city}]
                 (:thens result))))

    (it "parses 'C has one fighter aboard'"
      (let [lines ["THEN At the next round C has one fighter aboard"]
            result (parser/parse-then lines {})]
        (should= [{:type :container-prop :target "C" :property :fighter-count :expected 1 :lookup :unit :at-next-round true}]
                 (:thens result))))

    (it "parses 'C has two awake fighters'"
      (let [lines ["THEN C has two awake fighters."]
            result (parser/parse-then lines {})]
        (should= [{:type :container-prop :target "C" :property :awake-fighters :expected 2 :lookup :unit}]
                 (:thens result))))

    (it "parses message contains :fighter-bingo"
      (let [lines ["and the attention message contains :fighter-bingo."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :fighter-bingo}]
                 (:thens result))))

    (it "parses 'attention message for F contains' as message-for-unit"
      (let [lines ["and the attention message for F contains :fighter-bingo."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-for-unit :area :attention :unit "F" :config-key :fighter-bingo}]
                 (:thens result))))

    (it "parses 'error message for A contains' as message-for-unit"
      (let [lines ["THEN the error message for A contains :some-key."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-for-unit :area :error :unit "A" :config-key :some-key}]
                 (:thens result))))

    (it "parses 'at the next round the attention message contains' with :at-next-round flag"
      (let [lines ["THEN at the next round the attention message contains :cant-move-into-city."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-round true}]
                 (:thens result))))

    (it "parses 'at the next step' with :at-next-step flag (not :at-next-round)"
      (let [lines ["THEN at the next step the attention message contains :cant-move-into-city."]
            result (parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-step true}]
                 (:thens result))))

    (it "parses 'at next move' with :at-next-step flag"
      (let [lines ["THEN at next move A will be at =."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-at-next-round :unit "A" :target "=" :at-next-step true}]
                 (:thens result))))

    (it "parses player-map cell is not nil"
      (let [lines ["THEN player-map cell [1 2] is not nil."]
            result (parser/parse-then lines {})]
        (should= [{:type :player-map-cell-not-nil :coords [1 2]}]
                 (:thens result))))

    (it "parses player-map cell is nil"
      (let [lines ["THEN player-map cell [1 2] is nil."]
            result (parser/parse-then lines {})]
        (should= [{:type :player-map-cell-nil :coords [1 2]}]
                 (:thens result))))

    (it "parses 'the player can see [1 2]'"
      (let [lines ["THEN the player can see [1 2]."]
            result (parser/parse-then lines {})]
        (should= [{:type :player-map-cell-not-nil :coords [1 2]}]
                 (:thens result))))

    (it "parses 'the player cannot see [1 2]'"
      (let [lines ["THEN the player cannot see [1 2]."]
            result (parser/parse-then lines {})]
        (should= [{:type :player-map-cell-nil :coords [1 2]}]
                 (:thens result))))

    (it "parses production with rounds remaining"
      (let [lines ["THEN production at O is army with 5 rounds remaining."]
            result (parser/parse-then lines {})]
        (should= [{:type :production-with-rounds :city "O" :expected :army :remaining-rounds 5}]
                 (:thens result))))

    (it "parses production with 1 round remaining"
      (let [lines ["THEN production at O is fighter with 1 round remaining."]
            result (parser/parse-then lines {})]
        (should= [{:type :production-with-rounds :city "O" :expected :fighter :remaining-rounds 1}]
                 (:thens result))))

    (it "parses production-with-rounds with hyphenated item name"
      (let [lines ["THEN production at O is patrol-boat with 15 rounds remaining."]
            result (parser/parse-then lines {})]
        (should= [{:type :production-with-rounds :city "O" :expected :patrol-boat :remaining-rounds 15}]
                 (:thens result))))

    (it "parses unit has target with coordinate value"
      (let [lines ["THEN A has target [4 0]."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "A" :property :target :expected [4 0]}]
                 (:thens result))))

    (it "parses negative production assertion"
      (let [lines ["THEN production at X is not army."]
            result (parser/parse-then lines {})]
        (should= [{:type :production-not :city "X" :excluded :army}]
                 (:thens result))))

    (it "parses negative production with hyphenated item"
      (let [lines ["THEN production at X is not patrol-boat."]
            result (parser/parse-then lines {})]
        (should= [{:type :production-not :city "X" :excluded :patrol-boat}]
                 (:thens result))))

    (it "parses 'T has no mission'"
      (let [lines ["THEN T has no mission."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop-absent :unit "T" :property :transport-mission}]
                 (:thens result))))

    (it "parses 'V has 50 turns remaining'"
      (let [lines ["THEN V has 50 turns remaining."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "V" :property :turns-remaining :expected 50}]
                 (:thens result))))

    (it "parses 'T has two armies'"
      (let [lines ["THEN T has two armies."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "T" :property :army-count :expected 2}]
                 (:thens result))))

    (it "parses 'T has no armies'"
      (let [lines ["THEN T has no armies."]
            result (parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "T" :property :army-count :expected 0}]
                 (:thens result))))

    (it "parses THEN player map with map rows"
      (let [lines ["THEN player map" ".###." ".###." ".###."]
            result (parser/parse-then lines {})]
        (should= [{:type :player-map-visibility :rows [".###." ".###." ".###."]}]
                 (:thens result))))

    (it "parses THEN player map mixed with other assertions"
      (let [lines ["THEN player map" ".##." ".##." "THEN A is at [0 0]."]
            result (parser/parse-then lines {})]
        (should= 2 (count (:thens result)))
        (should= {:type :player-map-visibility :rows [".##." ".##."]}
                 (first (:thens result)))
        (should= {:type :unit-at :unit "A" :coords [0 0]}
                 (second (:thens result)))))

    (it "parses 'X has a destroyer with 2 hits in its shipyard' in THEN"
      (let [lines ["THEN X has a destroyer with 2 hits in its shipyard."]
            result (parser/parse-then lines {})]
        (should= [{:type :shipyard-has-ship :city "X" :ship-type :destroyer :hits 2}]
                 (:thens result))))

    (it "parses 'X has no ships in its shipyard'"
      (let [lines ["THEN X has no ships in its shipyard."]
            result (parser/parse-then lines {})]
        (should= [{:type :shipyard-empty :city "X"}]
                 (:thens result))))

    (it "parses 'the map is dX#'"
      (let [lines ["THEN the map is dX#."]
            result (parser/parse-then lines {})]
        (should= [{:type :map-is :expected "dX#"}]
                 (:thens result)))))

  (describe "parse-file integration"
    (it "parses army.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/army.txt")]
        (should= "army.txt" (:source result))
        (should= 7 (count (:tests result)))
        (should= 7 (:line (first (:tests result))))
        (should= "Army put to sentry mode." (:description (first (:tests result))))))

    (it "parses fighter.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/fighter.txt")]
        (should= "fighter.txt" (:source result))
        (should= 9 (count (:tests result)))))

    (it "parses destroyer.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/destroyer.txt")]
        (should= "destroyer.txt" (:source result))
        (should= 4 (count (:tests result)))))

    (it "parses backtick-commands.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/backtick-commands.txt")]
        (should= "backtick-commands.txt" (:source result))
        (should= 13 (count (:tests result)))))

    (it "produces no unrecognized directives across all files"
      (let [files ["acceptanceTests/army.txt" "acceptanceTests/fighter.txt"
                    "acceptanceTests/destroyer.txt" "acceptanceTests/backtick-commands.txt"]
            unrecognized (for [f files
                               :let [result (parser/parse-file f)]
                               t (:tests result)
                               ir (concat (:givens t) (:whens t) (:thens t))
                               :when (= :unrecognized (:type ir))]
                           {:file f :line (:line t) :text (:text ir)})]
        (should= [] (vec unrecognized)))))

  (describe "config key validation"
    (it "warns about missing config key during parse"
      (let [output (with-out-str
                     (parser/validate-config-keys
                       "test.txt"
                       [{:line 10 :thens [{:type :message-contains :area :attention :config-key :nonexistent-key}]}]))]
        (should-contain "WARNING" output)
        (should-contain ":nonexistent-key" output)
        (should-contain "test.txt:10" output)))

    (it "does not warn about valid config key"
      (let [output (with-out-str
                     (parser/validate-config-keys
                       "test.txt"
                       [{:line 10 :thens [{:type :message-contains :area :attention :config-key :army-found-city}]}]))]
        (should= "" output)))))
