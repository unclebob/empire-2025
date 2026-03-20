(ns empire.game-mechanics.movement.movement-context-spec
  (:require [empire.test.utils :as test-utils]
    [empire.config.core :as config]
    [empire.game.loop.core :as game-loop]
    [empire.game-mechanics.movement.explore :as explore]
    [empire.game-mechanics.movement.api :refer :all]
    [empire.game-mechanics.visibility :as visibility]
    [empire.game-mechanics.movement.wake-conditions :as wake]
    [empire.test.utils :refer [build-test-map get-test-unit set-test-unit reset-all-atoms! set-test-player-map! set-test-world! update-test-world!]]
    [speclj.core :refer :all]))
(describe "movement-context"
  (before (reset-all-atoms!))
  (it "returns :airport-fighter for fighter from airport"
    (let [cell {:type :city :awake-fighters 1}
          unit {:type :fighter :from-airport true}]
      (should= :airport-fighter (movement-context cell unit))))

  (it "returns :carrier-fighter for fighter from carrier"
    (let [cell {:contents {:type :carrier}}
          unit {:type :fighter :from-carrier true}]
      (should= :carrier-fighter (movement-context cell unit))))

  (it "returns :army-aboard for army aboard transport"
    (let [cell {:contents {:type :transport}}
          unit {:type :army :aboard-transport true}]
      (should= :army-aboard (movement-context cell unit))))

  (it "returns :standard-unit for regular unit"
    (let [cell {:contents {:type :army}}
          unit {:type :army :mode :awake}]
      (should= :standard-unit (movement-context cell unit))))

  (it "returns :standard-unit for nil unit"
    (should= :standard-unit (movement-context {} nil))))
