(ns empire.acceptance.parser.given-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser.given :as given]))

(describe "parse-given"
    (it "parses game map"
      (let [lines ["GIVEN game map" "  A#" "  ##"]
            result (given/parse-given lines {})]
        (should= [{:type :map :target :game-map :rows ["A#" "##"]}]
                 (:givens result))))

    (it "parses player map"
      (let [lines ["GIVEN player map" "  A." "  .."]
            result (given/parse-given lines {})]
        (should= [{:type :map :target :player-map :rows ["A." ".."]}]
                 (:givens result))))

    (it "parses computer map"
      (let [lines ["GIVEN computer map" "  ~~a~" "  ####"]
            result (given/parse-given lines {})]
        (should= [{:type :map :target :computer-map :rows ["~~a~" "####"]}]
                 (:givens result))))

    (it "parses bare map (defaults to game-map)"
      (let [lines ["GIVEN map" "  A#"]
            result (given/parse-given lines {})]
        (should= [{:type :map :target :game-map :rows ["A#"]}]
                 (:givens result))))

    (it "parses unit properties - mode"
      (let [lines ["A is awake."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "A" :props {:mode :awake}}]
                 (:givens result))))

    (it "parses unit properties - mode and fuel"
      (let [lines ["F has fuel 32."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "F" :props {:fuel 32}}]
                 (:givens result))))

    (it "parses unit properties - sentry with fuel"
      (let [lines ["F is sentry with fuel 9."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "F" :props {:mode :sentry :fuel 9}}]
                 (:givens result))))

    (it "parses unit properties - explore"
      (let [lines ["A is explore."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "A" :props {:mode :explore}}]
                 (:givens result))))

    (it "parses unit props with natural language army count"
      (let [lines ["T is awake with two armies."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "T" :props {:mode :awake :army-count 2}}]
                 (:givens result))))

    (it "parses unit props with one army"
      (let [lines ["T is awake with one army."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "T" :props {:mode :awake :army-count 1}}]
                 (:givens result))))

    (it "parses unit props with three armies"
      (let [lines ["T is awake with three armies."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "T" :props {:mode :awake :army-count 3}}]
                 (:givens result))))

    (it "parses unit props with container props"
      (let [lines ["C is sentry with two fighters and no awake fighters."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "C" :props {:mode :sentry}}
                  {:type :container-state :target "C" :props {:fighter-count 2 :awake-fighters 0}}]
                 (:givens result))))

    (it "parses unit props with numeric container props"
      (let [lines ["C is sentry with fighter-count 2 and awake-fighters 1."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "C" :props {:mode :sentry}}
                  {:type :container-state :target "C" :props {:fighter-count 2 :awake-fighters 1}}]
                 (:givens result))))

    (it "parses unit props with hits and no fighters"
      (let [lines ["C has hits 5 and no fighters."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "C" :props {:hits 5}}
                  {:type :container-state :target "C" :props {:fighter-count 0}}]
                 (:givens result))))

    (it "parses waiting-for-input"
      (let [lines ["GIVEN A is waiting for input."]
            result (given/parse-given lines {})]
        (should= [{:type :waiting-for-input :unit "A" :set-mode true}]
                 (:givens result))))

    (it "does not set-mode when mode already set"
      (let [lines ["F has fuel 20." "GIVEN F is waiting for input."]
            result (given/parse-given lines {})]
        (should= 2 (count (:givens result)))
        (should= {:type :waiting-for-input :unit "F" :set-mode true}
                 (second (:givens result)))))

    (it "does not set-mode when mode explicitly set prior"
      (let [lines ["F is sentry with fuel 9." "GIVEN F is waiting for input."]
            result (given/parse-given lines {})]
        (should= {:type :waiting-for-input :unit "F" :set-mode false}
                 (second (:givens result)))))

    (it "parses production"
      (let [lines ["GIVEN production at O is army."]
            result (given/parse-given lines {})]
        (should= [{:type :production :city "O" :item :army}]
                 (:givens result))))

    (it "parses production with remaining rounds"
      (let [lines ["GIVEN production at O is transport with 1 round remaining."]
            result (given/parse-given lines {})]
        (should= [{:type :production :city "O" :item :transport :remaining-rounds 1}]
                 (:givens result))))

    (it "parses round"
      (let [lines ["GIVEN round 5."]
            result (given/parse-given lines {})]
        (should= [{:type :round :value 5}]
                 (:givens result))))

    (it "parses destination"
      (let [lines ["GIVEN destination [3 7]."]
            result (given/parse-given lines {})]
        (should= [{:type :destination :coords [3 7]}]
                 (:givens result))))

    (it "parses cell props"
      (let [lines ["GIVEN cell [0 0] has awake-fighters 1 and fighter-count 1."]
            result (given/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:awake-fighters 1 :fighter-count 1}}]
                 (:givens result))))

    (it "parses cell props with coordinate value"
      (let [lines ["GIVEN cell [0 0] has marching-orders [4 0]."]
            result (given/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:marching-orders [4 0]}}]
                 (:givens result))))

    (it "parses cell props with keyword value"
      (let [lines ["GIVEN cell [0 0] has marching-orders lookaround."]
            result (given/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:marching-orders :lookaround}}]
                 (:givens result))))

    (it "parses cell props with spawn-orders alias"
      (let [lines ["GIVEN cell [0 0] has spawn-orders [4 0]."]
            result (given/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:marching-orders [4 0]}}]
                 (:givens result))))

    (it "parses cell props with spawn-orders lookaround"
      (let [lines ["GIVEN cell [0 0] has spawn-orders lookaround."]
            result (given/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:marching-orders :lookaround}}]
                 (:givens result))))

    (it "parses cell props with flight-orders alias"
      (let [lines ["GIVEN cell [0 0] has flight-orders [11 0]."]
            result (given/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:flight-path [11 0]}}]
                 (:givens result))))

    (it "parses cell props with flight-path coordinate"
      (let [lines ["GIVEN cell [0 0] has flight-path [4 0]."]
            result (given/parse-given lines {})]
        (should= [{:type :cell-props :coords [0 0] :props {:flight-path [4 0]}}]
                 (:givens result))))

    (it "parses player-items single"
      (let [lines ["GIVEN player-items F."]
            result (given/parse-given lines {})]
        (should= [{:type :player-items :items ["F"]}]
                 (:givens result))))

    (it "parses player-items multiple"
      (let [lines ["GIVEN player-items are A, T, O."]
            result (given/parse-given lines {})]
        (should= [{:type :player-items :items ["A" "T" "O"]}]
                 (:givens result))))

    (it "parses player units single"
      (let [lines ["GIVEN player units V."]
            result (given/parse-given lines {})]
        (should= [{:type :player-items :items ["V"]}]
                 (:givens result))))

    (it "parses player units multiple"
      (let [lines ["GIVEN player units are A, T, O."]
            result (given/parse-given lines {})]
        (should= [{:type :player-items :items ["A" "T" "O"]}]
                 (:givens result))))

    (it "parses container state - city airport"
      (let [lines ["GIVEN O has one fighter in its airport."]
            result (given/parse-given lines {})]
        (should= [{:type :container-state :target "O" :props {:fighter-count 1 :awake-fighters 1}}]
                 (:givens result))))

    (it "parses container state - no fighters"
      (let [lines ["C has no fighters."]
            result (given/parse-given lines {})]
        (should= [{:type :container-state :target "C" :props {:fighter-count 0}}]
                 (:givens result))))

    (it "parses container state - natural language count"
      (let [lines ["GIVEN C has three fighters."]
            result (given/parse-given lines {})]
        (should= [{:type :container-state :target "C" :props {:fighter-count 3}}]
                 (:givens result))))

    (it "parses waiting-for-input for city"
      (let [lines ["GIVEN O is waiting for input."]
            result (given/parse-given lines {})]
        (should= [{:type :waiting-for-input :unit "O" :set-mode true}]
                 (:givens result))))

    (it "parses 'the game is waiting for input' in GIVEN"
      (let [lines ["GIVEN the game is waiting for input."]
            result (given/parse-given lines {})]
        (should= [{:type :waiting-for-input-state}]
                 (:givens result))))

    (it "parses unit target"
      (let [lines ["A's target is +"]
            result (given/parse-given lines {})]
        (should= [{:type :unit-target :unit "A" :target "+"}]
                 (:givens result))))

    (it "parses unit target with label char"
      (let [lines ["D's target is ="]
            result (given/parse-given lines {})]
        (should= [{:type :unit-target :unit "D" :target "="}]
                 (:givens result))))

    (it "parses generic unit property - country-id"
      (let [lines ["a has country-id 1."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "a" :props {:country-id 1}}]
                 (:givens result))))

    (it "parses generic unit property - patrol-country-id"
      (let [lines ["p has patrol-country-id 1."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "p" :props {:patrol-country-id 1}}]
                 (:givens result))))

    (it "parses generic unit property - army-count on transport"
      (let [lines ["t has army-count 6."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "t" :props {:army-count 6}}]
                 (:givens result))))

    (it "parses generic unit property - boolean true"
      (let [lines ["t has been-to-sea true."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "t" :props {:been-to-sea true}}]
                 (:givens result))))

    (it "parses generic unit property combined with mode"
      (let [lines ["a is awake with country-id 1."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "a" :props {:mode :awake :country-id 1}}]
                 (:givens result))))

    (it "parses 'X belongs to country 1' for city"
      (let [lines ["GIVEN X belongs to country 1."]
            result (given/parse-given lines {})]
        (should= [{:type :city-prop :city "X" :prop :country-id :value 1}]
                 (:givens result))))

    (it "parses 't belongs to country 1' for unit"
      (let [lines ["t belongs to country 1."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "t" :props {:country-id 1}}]
                 (:givens result))))

    (it "parses 'p patrols for country 1' for patrol boat"
      (let [lines ["p patrols for country 1."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "p" :props {:country-id 1 :patrol-mode :crawling}}]
                 (:givens result))))

    (it "parses 't has 6 armies' as natural language army-count"
      (let [lines ["t has 6 armies."]
            result (given/parse-given lines {})]
        (should= [{:type :unit-props :unit "t" :props {:army-count 6}}]
                 (:givens result))))

    (it "parses 'computer controls N cities' as stub"
      (let [lines ["GIVEN computer controls 12 cities."]
            result (given/parse-given lines {})]
        (should= [{:type :stub
                   :bindings [{:var "empire.computer.production/count-computer-cities"
                               :value "(constantly 12)"}]}]
                 (:givens result))))

    (it "parses 'a valid carrier position exists' as stub"
      (let [lines ["GIVEN a valid carrier position exists."]
            result (given/parse-given lines {})]
        (should= [{:type :stub
                   :bindings [{:var "empire.computer.ship/find-carrier-position"
                               :value "(constantly [0 0])"}]}]
                 (:givens result))))

    (it "parses 'X has a destroyer with 2 hits in its shipyard'"
      (let [lines ["X has a destroyer with 2 hits in its shipyard."]
            result (given/parse-given lines {})]
        (should= [{:type :shipyard-state :city "X" :ship-type :destroyer :hits 2}]
                 (:givens result))))

    (it "parses 'X has a computer army'"
      (let [lines ["X has a computer army."]
            result (given/parse-given lines {})]
        (should= [{:type :city-unit :city "X" :unit-type :army :owner :computer}]
                 (:givens result)))))
