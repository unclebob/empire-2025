(ns empire.acceptance.parser-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser :as parser]
            [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.given :as given]
            [empire.acceptance.parser.when :as when-parser]
            [empire.acceptance.parser.then :as then-parser]
            [empire.config :as config]))

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

  (describe "parse-then"
    (it "parses unit at position"
      (let [lines ["THEN A is at [0 2]."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-at :unit "A" :coords [0 2]}]
                 (:thens result))))

    (it "parses unit at named target"
      (let [lines ["THEN F is at =."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-at :unit "F" :target "="}]
                 (:thens result))))

    (it "parses unit at named target %"
      (let [lines ["THEN A is at %."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-at :unit "A" :target "%"}]
                 (:thens result))))

    (it "parses unit mode property"
      (let [lines ["THEN A has mode sentry."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "A" :property :mode :expected :sentry}]
                 (:thens result))))

    (it "parses unit mode with 'is'"
      (let [lines ["THEN A is awake."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "A" :property :mode :expected :awake}]
                 (:thens result))))

    (it "parses unit fuel property"
      (let [lines ["THEN F has fuel 19."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "F" :property :fuel :expected 19}]
                 (:thens result))))

    (it "parses unit owner property"
      (let [lines ["THEN A has owner player."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "A" :property :owner :expected :player}]
                 (:thens result))))

    (it "parses unit absent"
      (let [lines ["THEN there is no A on the map."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-absent :unit "A"}]
                 (:thens result))))

    (it "parses no F on the map"
      (let [lines ["THEN there is no F on the map."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-absent :unit "F"}]
                 (:thens result))))

    (it "parses there is no D"
      (let [lines ["and there is no D on the map."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-absent :unit "D"}]
                 (:thens result))))

    (it "parses unit present at coords"
      (let [lines ["THEN there is an A at [0 0]."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-present :unit "A" :coords [0 0]}]
                 (:thens result))))

    (it "parses unit present with 'a' article"
      (let [lines ["THEN there is a T at [0 0]."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-present :unit "T" :coords [0 0]}]
                 (:thens result))))

    (it "parses there is an F at target"
      (let [lines ["THEN there is an F at %."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-present :unit "F" :target "%"}]
                 (:thens result))))

    (it "parses message contains literal"
      (let [lines ["THEN the attention message contains \"fuel:20\"."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :text "fuel:20"}]
                 (:thens result))))

    (it "parses message contains config key"
      (let [lines ["THEN the attention message contains :army-found-city."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :army-found-city}]
                 (:thens result))))

    (it "parses message contains :cant-move-into-city"
      (let [lines ["THEN the attention message contains :cant-move-into-city."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :cant-move-into-city}]
                 (:thens result))))

    (it "parses turn message contains literal"
      (let [lines ["THEN the turn message contains \"Destroyer destroyed\"."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :turn :text "Destroyer destroyed"}]
                 (:thens result))))

    (it "parses turn message contains with hit edge"
      (let [lines ["THEN the turn message contains \"hit edge of map\"."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :turn :text "hit edge of map"}]
                 (:thens result))))

    (it "parses error message contains config key"
      (let [lines ["THEN the error message contains :fighter-out-of-fuel."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :error :config-key :fighter-out-of-fuel}]
                 (:thens result))))

    (it "parses error message is config key"
      (let [lines ["THEN the error message is :conquest-failed."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-is :area :error :config-key :conquest-failed}]
                 (:thens result))))

    (it "parses no message"
      (let [lines ["THEN there is no attention message."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :no-message :area :attention}]
                 (:thens result))))

    (it "parses cell property"
      (let [lines ["THEN cell [1 0] has city-status player."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :cell-prop :coords [1 0] :property :city-status :expected :player}]
                 (:thens result))))

    (it "parses 'cell [1 0] is a player city'"
      (let [lines ["THEN cell [1 0] is a player city."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :cell-prop :coords [1 0] :property :city-status :expected :player}]
                 (:thens result))))

    (it "parses 'cell [1 0] is a computer city'"
      (let [lines ["THEN cell [1 0] is a computer city."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :cell-prop :coords [1 0] :property :city-status :expected :computer}]
                 (:thens result))))

    (it "parses THEN cell with spawn-orders alias"
      (let [lines ["THEN cell [0 0] has spawn-orders lookaround."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :cell-prop :coords [0 0] :property :marching-orders :expected :lookaround}]
                 (:thens result))))

    (it "parses cell type"
      (let [lines ["THEN cell [0 0] is a city."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :cell-type :coords [0 0] :expected :city}]
                 (:thens result))))

    (it "parses waiting-for-input true"
      (let [lines ["THEN waiting-for-input."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :waiting-for-input :expected true}]
                 (:thens result))))

    (it "parses not waiting-for-input"
      (let [lines ["THEN not waiting-for-input."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :waiting-for-input :expected false}]
                 (:thens result))))

    (it "parses 'the game is waiting for input'"
      (let [lines ["THEN the game is waiting for input."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :waiting-for-input :expected true}]
                 (:thens result))))

    (it "parses 'the game is not waiting for input'"
      (let [lines ["THEN the game is not waiting for input."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :waiting-for-input :expected false}]
                 (:thens result))))

    (it "parses unit-at-next-round"
      (let [lines ["THEN at next round F will be at =."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-at-next-round :unit "F" :target "=" :at-next-round true}]
                 (:thens result))))

    (it "parses unit-at-next-round D"
      (let [lines ["THEN at next round D will be at =."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-at-next-round :unit "D" :target "=" :at-next-round true}]
                 (:thens result))))

    (it "parses eventually at"
      (let [lines ["THEN eventually A will be at %."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-eventually-at :unit "A" :target "%"}]
                 (:thens result))))

    (it "parses after N moves unit will be at target"
      (let [lines ["THEN after two moves F will be at =."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-after-moves :unit "F" :moves 2 :target "="}]
                 (:thens result))))

    (it "parses after N moves with numeric count"
      (let [lines ["THEN after 3 moves D will be at =."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-after-moves :unit "D" :moves 3 :target "="}]
                 (:thens result))))

    (it "parses after one step there is a unit at target"
      (let [lines ["THEN after one step there is an F at %."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-after-steps :unit "F" :steps 1 :target "%"}]
                 (:thens result))))

    (it "parses after N steps there is a unit at coords"
      (let [lines ["THEN after 2 steps there is an A at [1 0]."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-after-steps :unit "A" :steps 2 :coords [1 0]}]
                 (:thens result))))

    (it "parses and-continuation"
      (let [lines ["THEN F wakes up and asks for input,"
                   "and the out-of-fuel message is displayed."]
            result (then-parser/parse-then lines {})]
        (should (>= (count (:thens result)) 2))))

    (it "parses 'F is waiting for input'"
      (let [lines ["THEN F is waiting for input."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-waiting-for-input :unit "F"}]
                 (:thens result))))

    (it "parses compound then with 'at the next round O has one fighter' and 'no fighter on the map'"
      (let [lines ["THEN at the next round O has one fighter in its airport and there is no fighter on the map."]
            result (then-parser/parse-then lines {})]
        (should= 2 (count (:thens result)))
        (should= :container-prop (:type (first (:thens result))))
        (should= :unit-absent (:type (second (:thens result))))))

    (it "parses 'D occupies the s cell'"
      (let [lines ["THEN at the next round D occupies the s cell and there is no s."]
            result (then-parser/parse-then lines {})]
        (should (>= (count (:thens result)) 2))
        (should= :unit-occupies-cell (:type (first (:thens result))))))

    (it "parses 's remains unmoved'"
      (let [lines ["THEN at the next round s remains unmoved."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-unmoved :unit "s" :at-next-round true}]
                 (:thens result))))

    (it "parses 'O has no fighters'"
      (let [lines ["and O has no fighters."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :container-prop :target "O" :property :fighter-count :expected 0 :lookup :city}]
                 (:thens result))))

    (it "parses 'C has one fighter aboard'"
      (let [lines ["THEN At the next round C has one fighter aboard"]
            result (then-parser/parse-then lines {})]
        (should= [{:type :container-prop :target "C" :property :fighter-count :expected 1 :lookup :unit :at-next-round true}]
                 (:thens result))))

    (it "parses 'C has two awake fighters'"
      (let [lines ["THEN C has two awake fighters."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :container-prop :target "C" :property :awake-fighters :expected 2 :lookup :unit}]
                 (:thens result))))

    (it "parses message contains :fighter-bingo"
      (let [lines ["and the attention message contains :fighter-bingo."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :fighter-bingo}]
                 (:thens result))))

    (it "parses 'attention message for F contains' as message-for-unit"
      (let [lines ["and the attention message for F contains :fighter-bingo."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-for-unit :area :attention :unit "F" :config-key :fighter-bingo}]
                 (:thens result))))

    (it "parses 'error message for A contains' as message-for-unit"
      (let [lines ["THEN the error message for A contains :some-key."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-for-unit :area :error :unit "A" :config-key :some-key}]
                 (:thens result))))

    (it "parses 'at the next round the attention message contains' with :at-next-round flag"
      (let [lines ["THEN at the next round the attention message contains :cant-move-into-city."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-round true}]
                 (:thens result))))

    (it "parses 'at the next step' with :at-next-step flag (not :at-next-round)"
      (let [lines ["THEN at the next step the attention message contains :cant-move-into-city."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-step true}]
                 (:thens result))))

    (it "parses 'at next move' with :at-next-step flag"
      (let [lines ["THEN at next move A will be at =."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-at-next-round :unit "A" :target "=" :at-next-step true}]
                 (:thens result))))

    (it "parses player-map cell is not nil"
      (let [lines ["THEN player-map cell [1 2] is not nil."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :player-map-cell-not-nil :coords [1 2]}]
                 (:thens result))))

    (it "parses player-map cell is nil"
      (let [lines ["THEN player-map cell [1 2] is nil."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :player-map-cell-nil :coords [1 2]}]
                 (:thens result))))

    (it "parses 'the player can see [1 2]'"
      (let [lines ["THEN the player can see [1 2]."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :player-map-cell-not-nil :coords [1 2]}]
                 (:thens result))))

    (it "parses 'the player cannot see [1 2]'"
      (let [lines ["THEN the player cannot see [1 2]."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :player-map-cell-nil :coords [1 2]}]
                 (:thens result))))

    (it "parses production with rounds remaining"
      (let [lines ["THEN production at O is army with 5 rounds remaining."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :production-with-rounds :city "O" :expected :army :remaining-rounds 5}]
                 (:thens result))))

    (it "parses production with 1 round remaining"
      (let [lines ["THEN production at O is fighter with 1 round remaining."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :production-with-rounds :city "O" :expected :fighter :remaining-rounds 1}]
                 (:thens result))))

    (it "parses production-with-rounds with hyphenated item name"
      (let [lines ["THEN production at O is patrol-boat with 15 rounds remaining."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :production-with-rounds :city "O" :expected :patrol-boat :remaining-rounds 15}]
                 (:thens result))))

    (it "parses unit has target with coordinate value"
      (let [lines ["THEN A has target [4 0]."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "A" :property :target :expected [4 0]}]
                 (:thens result))))

    (it "parses negative production assertion"
      (let [lines ["THEN production at X is not army."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :production-not :city "X" :excluded :army}]
                 (:thens result))))

    (it "parses negative production with hyphenated item"
      (let [lines ["THEN production at X is not patrol-boat."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :production-not :city "X" :excluded :patrol-boat}]
                 (:thens result))))

    (it "parses 'T has no mission'"
      (let [lines ["THEN T has no mission."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop-absent :unit "T" :property :transport-mission}]
                 (:thens result))))

    (it "parses 'V has 50 turns remaining'"
      (let [lines ["THEN V has 50 turns remaining."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "V" :property :turns-remaining :expected 50}]
                 (:thens result))))

    (it "parses 'T has two armies'"
      (let [lines ["THEN T has two armies."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "T" :property :army-count :expected 2}]
                 (:thens result))))

    (it "parses 'T has no armies'"
      (let [lines ["THEN T has no armies."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :unit-prop :unit "T" :property :army-count :expected 0}]
                 (:thens result))))

    (it "parses THEN player map with map rows"
      (let [lines ["THEN player map" ".###." ".###." ".###."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :player-map-visibility :rows [".###." ".###." ".###."]}]
                 (:thens result))))

    (it "parses THEN player map mixed with other assertions"
      (let [lines ["THEN player map" ".##." ".##." "THEN A is at [0 0]."]
            result (then-parser/parse-then lines {})]
        (should= 2 (count (:thens result)))
        (should= {:type :player-map-visibility :rows [".##." ".##."]}
                 (first (:thens result)))
        (should= {:type :unit-at :unit "A" :coords [0 0]}
                 (second (:thens result)))))

    (it "parses 'X has a destroyer with 2 hits in its shipyard' in THEN"
      (let [lines ["THEN X has a destroyer with 2 hits in its shipyard."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :shipyard-has-ship :city "X" :ship-type :destroyer :hits 2}]
                 (:thens result))))

    (it "parses 'X has no ships in its shipyard'"
      (let [lines ["THEN X has no ships in its shipyard."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :shipyard-empty :city "X"}]
                 (:thens result))))

    (it "parses 'the map is dX#'"
      (let [lines ["THEN the map is dX#."]
            result (then-parser/parse-then lines {})]
        (should= [{:type :map-is :expected "dX#"}]
                 (:thens result)))))

  (describe "parse-file integration"
    (it "parses army.txt correctly"
      (let [result (parser/parse-file "acceptanceTests/army.txt")]
        (should= "army.txt" (:source result))
        (should= 12 (count (:tests result)))
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
