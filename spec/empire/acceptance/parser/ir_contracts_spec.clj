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

  (it "validates a broad catalog of GIVEN IR variants"
    (let [samples [{:type :map :target :player-map :rows ["##"]}
                   {:type :unit-props :unit "A" :props {:mode :awake}}
                   {:type :container-state :target "C" :props {:fighter-count 1}}
                   {:type :waiting-for-input :unit "A" :set-mode true}
                   {:type :production :city "O" :item :army :remaining-rounds 2}
                   {:type :no-production}
                   {:type :round :value 3}
                   {:type :destination :coords [1 2]}
                   {:type :cell-props :coords [0 0] :props {:fuel 5}}
                   {:type :player-items :items ["A" "O"]}
                   {:type :waiting-for-input-state}
                   {:type :unit-target :unit "A" :target "="}
                   {:type :city-unit :city "O" :unit-type :army :owner :player}
                   {:type :shipyard-state :city "O" :ship-type :destroyer :hits 2}
                   {:type :stub :bindings [{:var "x" :value 1}]}
                   {:type :visible-to-computer :ref "A"}
                   {:type :city-prop :city "O" :prop :country-id :value 1}
                   {:type :territory-around :city "O" :country-id 7}
                   {:type :game-over-check-enabled}
                   {:type :game-paused}
                   {:type :pause-requested}
                   {:type :load-menu-open}
                   {:type :map-display-setup :value :computer-map}
                   {:type :unrecognized :text "oops"}]]
      (should (every? #(valid? ::contracts/given-ir %) samples))))

  (it "validates a broad catalog of WHEN IR variants"
    (let [samples [{:type :key-press :key :d :input-fn :handle-key}
                   {:type :backtick :key :A :mouse-cell [0 0]}
                   {:type :mouse-at-key :coords [1 2] :key :period}
                   {:type :waiting-for-input :unit "A" :set-mode true}
                   {:type :battle :key :d :outcome :win :combat-type :ship}
                   {:type :advance-until-waiting :unit "F"}
                   {:type :start-new-round}
                   {:type :advance-game-batch}
                   {:type :advance-game}
                   {:type :process-player-items}
                   {:type :cell-visibility-update :unit "F"}
                   {:type :visibility-update}
                   {:type :evaluate-production :city "O"}
                   {:type :process-computer-transport :unit "T"}
                   {:type :process-computer-fighter :unit "F"}
                   {:type :process-computer-ship :ship-type :destroyer :unit "D"}
                   {:type :computer-rounds :count 2}
                   {:type :rounds-complete :count 3}
                   {:type :save-game}
                   {:type :open-load-menu}
                   {:type :unrecognized :text "odd"}]]
      (should (every? #(valid? ::contracts/when-ir %) samples))))

  (it "validates a broad catalog of THEN IR variants"
    (let [samples [{:type :unit-at :unit "A" :coords [0 0]}
                   {:type :unit-at :unit "A" :target "="}
                   {:type :unit-prop :unit "A" :property :mode :expected :awake}
                   {:type :unit-prop-absent :unit "A" :property :target}
                   {:type :unit-present :unit "A" :coords [0 0]}
                   {:type :unit-present :unit "A" :target "="}
                   {:type :unit-absent :unit "A"}
                   {:type :unit-waiting-for-input :unit "A"}
                   {:type :unit-after-moves :unit "A" :moves 2 :target "="}
                   {:type :unit-after-steps :unit "A" :steps 1 :coords [1 0]}
                   {:type :unit-after-steps :unit "A" :steps 1 :target "="}
                   {:type :unit-at-next-round :unit "A" :coords [1 0] :at-next-round true}
                   {:type :unit-eventually-at :unit "A" :target "="}
                   {:type :unit-occupies-cell :unit "A" :target "B"}
                   {:type :unit-unmoved :unit "A" :at-next-step true}
                   {:type :message-contains :area :attention :text "x"}
                   {:type :message-contains :area :attention :config-key :army-found-city}
                   {:type :message-for-unit :area :attention :unit "A" :config-key :army-found-city}
                   {:type :message-is :area :turn :config-key :hit-edge}
                   {:type :no-message :area :error}
                   {:type :cell-prop :coords [0 0] :property :city-status :expected :player}
                   {:type :cell-type :coords [0 0] :expected :land}
                   {:type :waiting-for-input :expected true}
                   {:type :game-paused :expected true}
                   {:type :game-not-paused}
                   {:type :round :expected 4}
                   {:type :destination :expected [1 2]}
                   {:type :production :city "O" :expected :army}
                   {:type :production-with-rounds :city "O" :expected :army :remaining-rounds 2}
                   {:type :production-not :city "O" :excluded :fighter}
                   {:type :no-production :city "O"}
                   {:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city}
                   {:type :no-unit-at :coords [1 1]}
                   {:type :refueling-position-near :unit "F" :target "="}
                   {:type :shipyard-has-ship :city "O" :ship-type :destroyer :hits 2}
                   {:type :shipyard-empty :city "O"}
                   {:type :map-is :expected "A#"}
                   {:type :map-display :expected :player-map}
                   {:type :load-menu-state :expected false}
                   {:type :player-map-visibility :rows ["##"]}
                   {:type :territory-map :rows ["11"]}
                   {:type :player-map-cell-not-nil :coords [0 0]}
                   {:type :player-map-cell-nil :coords [0 1]}
                   {:type :computer-map-cell-not-nil :coords [1 0]}
                   {:type :computer-army-count :expected 2}
                   {:type :unrecognized :text "odd"}]]
      (should (every? #(valid? ::contracts/then-ir %) samples)))))
