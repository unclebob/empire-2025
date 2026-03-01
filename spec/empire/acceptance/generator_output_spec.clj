(ns empire.acceptance.generator-output-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.generator :as gen]
            [clojure.string :as str]))

(defn- stub-test [n desc]
  {:line n :description desc :givens [] :whens [] :thens []})

;; --- generate-then tests ---

(describe "generate-then"

  (it "generates unit-prop then"
    (let [result (gen/generate-then {:type :unit-prop :unit "A" :property :mode :expected :sentry} [])]
      (should-contain "should=" result)
      (should-contain ":sentry" result)
      (should-contain ":mode" result)))

  (it "generates unit-absent then"
    (let [result (gen/generate-then {:type :unit-absent :unit "s"} [])]
      (should-contain "should-be-nil" result)
      (should-contain "\"s\"" result)))

  (it "generates unit-at then with named target"
    (let [result (gen/generate-then {:type :unit-at :unit "F" :target "="} [])]
      (should-contain "h/get-cell" result)
      (should-contain "should=" result)))

  (it "generates unit-present then with coords"
    (let [result (gen/generate-then {:type :unit-present :unit "A" :coords [0 0]} [])]
      (should-contain "should=" result)
      (should-contain "[0 0]" result)))

  (it "generates unit-at-next-round then with timeout check"
    (let [result (gen/generate-then {:type :unit-at-next-round :unit "D" :target "="} [])]
      (should-contain "should= :ok (advance-until-next-round)" result)
      (should-contain "h/get-cell" result)))

  (it "generates unit-at-next-step then with single advance"
    (let [result (gen/generate-then {:type :unit-at-next-round :unit "A" :target "%" :at-next-step true} [])]
      (should-contain "h/advance-game!" result)
      (should-not-contain "advance-until-next-round" result)
      (should-contain "h/get-cell" result)))

  (it "generates unit-after-moves then"
    (let [result (gen/generate-then {:type :unit-after-moves :unit "F" :moves 2 :target "="} [])]
      (should-contain "dotimes" result)
      (should-contain "_ 2" result)
      (should-contain "h/get-cell" result)))

  (it "generates unit-after-steps then with step advances"
    (let [result (gen/generate-then {:type :unit-after-steps :unit "F" :steps 1 :target "%"} [])]
      (should-contain "advance-game" result)
      (should-contain "dotimes" result)
      (should-contain "_ 1" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "h/get-cell" result)))

  (it "generates unit-after-steps then with multiple steps"
    (let [result (gen/generate-then {:type :unit-after-steps :unit "F" :steps 3 :target "%"} [])]
      (should-contain "_ 3" result)))

  (it "generates unit-occupies-cell then"
    (let [givens [{:type :map :target :game-map :rows ["Ds"]}]
          result (gen/generate-then {:type :unit-occupies-cell :unit "D" :target-unit "s"} givens)]
      (should-contain "should=" result)
      (should-contain "[1 0]" result)))

  (it "generates unit-unmoved then"
    (let [givens [{:type :map :target :game-map :rows ["Ds"]}]
          result (gen/generate-then {:type :unit-unmoved :unit "s"} givens)]
      (should-contain "should=" result)
      (should-contain "[1 0]" result)))

  (it "generates unit-waiting-for-input then"
    (let [result (gen/generate-then {:type :unit-waiting-for-input :unit "F"} [])]
      (should-contain "advance-until-unit-waiting" result)
      (should-contain "\"F\"" result)))

  (it "generates message-contains with config-key then using message-matches?"
    (let [result (gen/generate-then {:type :message-contains :area :attention :config-key :army-found-city} [])]
      (should-contain "should-not-be-nil" result)
      (should-contain "message-matches?" result)
      (should-contain ":army-found-city" result)
      (should-contain "config/messages" result)
      (should-contain "(h/read-state :attention-message)" result)))

  (it "generates message-contains with text then"
    (let [result (gen/generate-then {:type :message-contains :area :attention :text "fuel:20"} [])]
      (should-contain "should-contain" result)
      (should-contain "\"fuel:20\"" result)
      (should-contain "(h/read-state :attention-message)" result)))

  (it "generates message-contains with turn area"
    (let [result (gen/generate-then {:type :message-contains :area :turn :text "Destroyer destroyed"} [])]
      (should-contain "(h/read-state :turn-message)" result)))

  (it "generates message-contains with :at-next-round and timeout check"
    (let [result (gen/generate-then {:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-round true} [])]
      (should-contain "should= :ok (advance-until-next-round)" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "message-matches?" result)
      (should-contain ":cant-move-into-city" result)))

  (it "generates message-contains with :at-next-step using advance-game"
    (let [result (gen/generate-then {:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-step true} [])]
      (should-contain "h/advance-game!" result)
      (should-not-contain "advance-until-next-round" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "message-matches?" result)
      (should-contain ":cant-move-into-city" result)))

  (it "generates message-for-unit then with advance loop and message-matches?"
    (let [result (gen/generate-then {:type :message-for-unit :area :attention :unit "F" :config-key :fighter-bingo} [])]
      (should-contain "loop [n 100]" result)
      (should-contain "h/get-unit" result)
      (should-contain ":awake" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "message-matches?" result)
      (should-contain ":fighter-bingo" result)
      (should-contain "(h/read-state :attention-message)" result)))

  (it "generates message-is with config-key then"
    (let [result (gen/generate-then {:type :message-is :area :turn :config-key :hit-edge} [])]
      (should-contain "should=" result)
      (should-contain ":hit-edge" result)
      (should-contain "(h/read-state :turn-message)" result)))

  (it "generates message-is with format and no args"
    (let [result (gen/generate-then {:type :message-is
                                     :area :error
                                     :format {:key :fighter-bingo :args []}} [])]
      (should-contain "(format (:fighter-bingo config/messages)" result)
      (should-contain "(h/read-state :error-message)" result)))

  (it "generates message-is with format and args"
    (let [result (gen/generate-then {:type :message-is
                                     :area :error
                                     :format {:key :coastal-city-required :args ["transport"]}} [])]
      (should-contain "(format (:coastal-city-required config/messages) \"transport\")" result)
      (should-contain "(h/read-state :error-message)" result)))

  (it "generates no-message assertion for turn area"
    (let [result (gen/generate-then {:type :no-message :area :turn} [])]
      (should-contain "(should= \"\" (h/read-state :turn-message))" result)))

  (it "generates no-message assertion for error area"
    (let [result (gen/generate-then {:type :no-message :area :error} [])]
      (should-contain "(should= \"\" (h/read-state :error-message))" result)))

  (it "generates message-for-unit assertion for turn area"
    (let [result (gen/generate-then {:type :message-for-unit
                                     :area :turn
                                     :unit "F"
                                     :config-key :fighter-bingo} [])]
      (should-contain "loop [n 100]" result)
      (should-contain "(h/read-state :turn-message)" result)))

  (it "generates cell-prop then"
    (let [result (gen/generate-then {:type :cell-prop :coords [1 0] :property :city-status :expected :player} [])]
      (should-contain "should=" result)
      (should-contain ":player" result)
      (should-contain "[1 0]" result)))

  (it "generates waiting-for-input true then"
    (let [result (gen/generate-then {:type :waiting-for-input :expected true} [])]
      (should-contain "should (h/read-state :waiting-for-input)" result)))

  (it "generates waiting-for-input false then"
    (let [result (gen/generate-then {:type :waiting-for-input :expected false} [])]
      (should-contain "should-not (h/read-state :waiting-for-input)" result)))

  (it "generates unit-waiting-for-input then with advance"
    (let [result (gen/generate-then {:type :unit-waiting-for-input :unit "C"} [])]
      (should-contain "advance-until-unit-waiting" result)
      (should-contain "\"C\"" result)
      (should-contain "should=" result)))

  (it "generates container-state given for unit with container props"
    (let [result (gen/generate-given {:type :container-state :target "C" :props {:fighter-count 2 :awake-fighters 0}})]
      (should-contain "h/set-unit!" result)
      (should-contain ":fighter-count 2" result)
      (should-contain ":awake-fighters 0" result)))

  (it "generates container-prop city lookup then"
    (let [result (gen/generate-then {:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city} [])]
      (should-contain "h/get-city" result)
      (should-contain ":fighter-count" result)))

  (it "generates container-prop unit lookup then"
    (let [result (gen/generate-then {:type :container-prop :target "C" :property :fighter-count :expected 1 :lookup :unit} [])]
      (should-contain "h/get-unit" result)
      (should-contain ":fighter-count" result)))

  (it "generates container-prop unit lookup for unit-level prop"
    (let [result (gen/generate-then {:type :container-prop :target "C" :property :awake-fighters :expected 2 :lookup :unit} [])]
      (should-contain "h/get-unit" result)
      (should-contain ":awake-fighters" result)
      (should-contain ":unit" result)))

  (it "generates container-prop city lookup with at-next-step"
    (let [result (gen/generate-then {:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city :at-next-step true} [])]
      (should-contain "h/advance-game!" result)
      (should-contain ":fighter-count" result)))

  (it "generates container-prop city lookup with at-next-round"
    (let [result (gen/generate-then {:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city :at-next-round true} [])]
      (should-contain "advance-until-next-round" result)
      (should-contain ":fighter-count" result)))

  (it "generates player-map-cell-not-nil then"
    (let [result (gen/generate-then {:type :player-map-cell-not-nil :coords [1 2]} [])]
      (should-contain "should-not-be-nil" result)
      (should-contain "(h/cell-at :player-map [1 2])" result)
      (should-contain "[1 2]" result)))

  (it "generates player-map-cell-nil then"
    (let [result (gen/generate-then {:type :player-map-cell-nil :coords [1 2]} [])]
      (should-contain "should-be-nil" result)
      (should-contain "(h/cell-at :player-map [1 2])" result)
      (should-contain "[1 2]" result)))

  (it "generates player-map-visibility then"
    (let [result (gen/generate-then {:type :player-map-visibility :rows [".###." ".###." ".###."]} [])]
      (should-contain "should=" result)
      (should-contain "visibility-mask" result)
      (should-contain "build-test-map" result)
      (should-contain "\".###.\"" result)
      (should-contain "(h/read-state :player-map)" result)))

  (it "generates production-with-rounds then"
    (let [result (gen/generate-then {:type :production-with-rounds :city "O" :expected :army :remaining-rounds 5} [])]
      (should-contain "should=" result)
      (should-contain ":army" result)
      (should-contain ":remaining-rounds" result)
      (should-contain "5" result)
      (should-contain "h/get-city" result)))

  (it "generates cell-props given with coordinate value"
    (let [result (gen/generate-given {:type :cell-props :coords [0 0] :props {:marching-orders [4 0]}})]
      (should-contain "update-in" result)
      (should-contain "[4 0]" result)
      (should-contain ":marching-orders" result)))

  (it "generates cell-props given with keyword value"
    (let [result (gen/generate-given {:type :cell-props :coords [0 0] :props {:marching-orders :lookaround}})]
      (should-contain "update-in" result)
      (should-contain ":lookaround" result)
      (should-contain ":marching-orders" result)))

  (it "generates production-not then"
    (let [result (gen/generate-then {:type :production-not :city "X" :excluded :army} [])]
      (should-contain "should-not=" result)
      (should-contain ":army" result)
      (should-contain "h/get-city" result)
      (should-contain "(h/read-state :production)" result)))

  (it "generates stub given as empty string"
    (let [result (gen/generate-given {:type :stub
                                      :bindings [{:var "empire.computer.production/count-computer-cities"
                                                  :value "(constantly 12)"}]})]
      (should= "" result)))

  (it "wraps when-code in with-redefs when stubs present"
    (let [test-ir {:line 1
                   :description "test"
                   :givens [{:type :map :target :game-map :rows ["~X~"]}
                            {:type :stub
                             :bindings [{:var "empire.computer.production/count-computer-cities"
                                         :value "(constantly 12)"}]}]
                   :whens [{:type :evaluate-production :city "X"}]
                   :thens [{:type :production :city "X" :expected :carrier}]}
          result (gen/generate-test test-ir "test.txt")]
      (should-contain "with-redefs" result)
      (should-contain "empire.computer.production/count-computer-cities" result)
      (should-contain "(constantly 12)" result))))

;; --- Integration: generate-spec on actual EDN data ---

(describe "generate-spec integration"

  (it "generates army spec with correct ns form"
    (let [edn-data {:source "army.txt"
                    :tests [{:line 7 :description "Army put to sentry mode."
                             :givens [{:type :map :target :game-map :rows ["A#"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :s :input-fn :key-down}]
                             :thens [{:type :unit-prop :unit "A" :property :mode :expected :sentry}]}
                            {:line 18 :description "Army set to explore mode."
                             :givens [{:type :map :target :game-map :rows ["A#"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :l :input-fn :key-down}]
                             :thens [{:type :unit-prop :unit "A" :property :mode :expected :explore}]}
                            {:line 29 :description "Army wakes near hostile city with reason."
                             :givens [{:type :map :target :game-map :rows ["A#+"]}
                                      {:type :unit-target :unit "A" :target "+"}]
                             :whens [{:type :start-new-round}]
                             :thens [{:type :unit-prop :unit "A" :property :mode :expected :awake}
                                     {:type :message-contains :area :attention :config-key :army-found-city}]}
                            {:line 41 :description "Army conquers free city."
                             :givens [{:type :map :target :game-map :rows ["A+"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :battle :key :d :outcome :win :combat-type :army}]
                             :thens [{:type :cell-prop :coords [1 0] :property :city-status :expected :player}]}
                            {:line 52 :description "Army skips round with space."
                             :givens [{:type :map :target :game-map :rows ["A#"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :space :input-fn :key-down}]
                             :thens [{:type :waiting-for-input :expected false}]}
                            {:line 63 :description "Army blocked by friendly city."
                             :givens [{:type :map :target :game-map :rows ["AO"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :d :input-fn :handle-key}]
                             :thens [{:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-step true}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "(ns acceptance.army-spec" result)
      (should-contain "speclj.core :refer :all" result)
      (should-not-contain "empire.test-utils" result)
      (should-not-contain "empire.atoms :as atoms" result)
      (should-contain "empire.config :as config" result)
      (should-contain "empire.acceptance.harness :as h" result)))

  (it "generates army spec with correct describe"
    (let [edn-data {:source "army.txt"
                    :tests [(stub-test 1 "test")]}
          result (gen/generate-spec edn-data)]
      (should-contain "(describe \"army.txt\"" result)))

  (it "generates army spec with all 6 tests"
    (let [edn-data {:source "army.txt"
                    :tests (mapv #(stub-test % (str "test " %)) (range 1 7))}
          result (gen/generate-spec edn-data)]
      (should= 6 (count (re-seq #"\(it " result)))))

  (it "generates army spec with correct test descriptions"
    (let [edn-data {:source "army.txt"
                    :tests [{:line 7 :description "Army put to sentry mode." :givens [] :whens [] :thens []}
                            {:line 18 :description "Army set to explore mode." :givens [] :whens [] :thens []}
                            {:line 29 :description "Army wakes near hostile city with reason." :givens [] :whens [] :thens []}
                            {:line 41 :description "Army conquers free city." :givens [] :whens [] :thens []}
                            {:line 52 :description "Army skips round with space." :givens [] :whens [] :thens []}
                            {:line 63 :description "Army blocked by friendly city." :givens [] :whens [] :thens []}]}
          result (gen/generate-spec edn-data)]
      (should-contain "army.txt:7 - Army put to sentry mode" result)
      (should-contain "army.txt:18 - Army set to explore mode" result)
      (should-contain "army.txt:29 - Army wakes near hostile city with reason" result)
      (should-contain "army.txt:41 - Army conquers free city" result)
      (should-contain "army.txt:52 - Army skips round with space" result)
      (should-contain "army.txt:63 - Army blocked by friendly city" result)))

  (it "generates army spec sentry test with correct assertions"
    (let [edn-data {:source "army.txt"
                    :tests [{:line 7 :description "Army put to sentry mode."
                             :givens [{:type :map :target :game-map :rows ["A#"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :s :input-fn :key-down}]
                             :thens [{:type :unit-prop :unit "A" :property :mode :expected :sentry}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "build-test-map [\"A#\"]" result)
      (should-contain "(should= :sentry (:mode (:unit (h/get-unit \"A\"))))" result)))

  (it "generates backtick-commands spec with correct ns form"
    (let [edn-data {:source "backtick-commands.txt"
                    :tests [{:line 6 :description "Spawn army."
                             :givens [{:type :map :target :game-map :rows ["##"]}]
                             :whens [{:type :backtick :key :A :mouse-cell [0 0]}]
                             :thens [{:type :unit-present :unit "A" :coords [0 0]}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "(ns acceptance.backtick-commands-spec" result)
      (should-not-contain "empire.test-utils" result)
      (should-contain "empire.acceptance.harness :as h" result)))

  (it "generates backtick-commands spec with all 13 tests"
    (let [edn-data {:source "backtick-commands.txt"
                    :tests (into [{:line 6 :description "Spawn army."
                                   :givens [] :whens [{:type :backtick :key :A :mouse-cell [0 0]}] :thens []}]
                                 (mapv #(stub-test % (str "test " %)) (range 2 14)))}
          result (gen/generate-spec edn-data)]
      (should= 13 (count (re-seq #"\(it " result)))))

  (it "generates backtick-commands spec with map-screen-dimensions"
    (let [edn-data {:source "backtick-commands.txt"
                    :tests [{:line 6 :description "Spawn army."
                             :givens [{:type :map :target :game-map :rows ["##"]}]
                             :whens [{:type :backtick :key :A :mouse-cell [0 0]}]
                             :thens []}]}
          result (gen/generate-spec edn-data)]
      (should-contain "map-screen-dimensions" result)))

  (it "generates destroyer spec with all 4 tests"
    (let [edn-data {:source "destroyer.txt"
                    :tests (mapv #(stub-test % (str "test " %)) (range 1 5))}
          result (gen/generate-spec edn-data)]
      (should= 4 (count (re-seq #"\(it " result)))))

  (it "generates destroyer spec with advance-game for ship battles"
    (let [edn-data {:source "destroyer.txt"
                    :tests [{:line 17 :description "Destroyer attacks enemy ship."
                             :givens [{:type :map :target :game-map :rows ["Ds"]}
                                      {:type :waiting-for-input :unit "D" :set-mode true}]
                             :whens [{:type :battle :key :d :outcome :win :combat-type :ship}]
                             :thens [{:type :unit-occupies-cell :unit "D" :target-unit "s"}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "advance-game" result)))

  (it "generates fighter spec with all 9 tests"
    (let [edn-data {:source "fighter.txt"
                    :tests (mapv #(stub-test % (str "test " %)) (range 1 10))}
          result (gen/generate-spec edn-data)]
      (should= 9 (count (re-seq #"\(it " result)))))

  (it "generates fighter spec with advance-until-next-round helper"
    (let [edn-data {:source "fighter.txt"
                    :tests [{:line 104 :description "Fighter speed is 8 per round."
                             :givens [{:type :map :target :game-map :rows ["F~~~~~~~~=~"]}
                                      {:type :waiting-for-input :unit "F" :set-mode true}]
                             :whens [{:type :key-press :key :D :input-fn :key-down}]
                             :thens [{:type :unit-at-next-round :unit "F" :target "=" :at-next-round true}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "defn- advance-until-next-round" result)))

  (it "generates advance-until-next-round with loop and timeout"
    (let [edn-data {:source "fighter.txt"
                    :tests [{:line 104 :description "Fighter speed is 8 per round."
                             :givens [{:type :map :target :game-map :rows ["F~~~~~~~~=~"]}
                                      {:type :waiting-for-input :unit "F" :set-mode true}]
                             :whens [{:type :key-press :key :D :input-fn :key-down}]
                             :thens [{:type :unit-at-next-round :unit "F" :target "=" :at-next-round true}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "loop [n 100]" result)
      (should-contain ":timeout" result)
      (should-contain ":ok" result)))

  (it "generates advance-until-unit-waiting helper when needed"
    (let [result (gen/generate-helper-fns #{:advance-until-waiting-helper})]
      (should-contain "defn- advance-until-unit-waiting" result)
      (should-contain "cells-needing-attention" result)
      (should-contain ":timeout" result)
      (should-contain ":x" result)
      (should-contain ":space" result)))

  (it "advance-until-unit-waiting does not require awake mode"
    (let [result (gen/generate-helper-fns #{:advance-until-waiting-helper})]
      (should-not-contain ":awake" result))))
