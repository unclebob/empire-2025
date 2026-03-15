(ns empire.repair-ship-docking-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.test.utils :as tu]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.player.production :as production]))
(describe "Ship docking"
  (before
    (tu/reset-all-atoms!))

  (context "ship-can-dock?"
    (it "returns true for damaged player ship at player city"
      (let [ship {:type :destroyer :owner :player :hits 2}  ; max is 3
            city {:type :city :city-status :player}]
        (should (uc/ship-can-dock? ship city))))

    (it "returns false for undamaged ship"
      (let [ship {:type :destroyer :owner :player :hits 3}  ; full health
            city {:type :city :city-status :player}]
        (should-not (uc/ship-can-dock? ship city))))

    (it "returns false for damaged ship at enemy city"
      (let [ship {:type :destroyer :owner :player :hits 2}
            city {:type :city :city-status :computer}]
        (should-not (uc/ship-can-dock? ship city))))

    (it "returns false for damaged ship at free city"
      (let [ship {:type :destroyer :owner :player :hits 2}
            city {:type :city :city-status :free}]
        (should-not (uc/ship-can-dock? ship city))))

    (it "returns false for non-city cell"
      (let [ship {:type :destroyer :owner :player :hits 2}
            sea {:type :sea}]
        (should-not (uc/ship-can-dock? ship sea))))

    (it "returns false for non-naval unit"
      (let [army {:type :army :owner :player :hits 1}
            city {:type :city :city-status :player}]
        (should-not (uc/ship-can-dock? army city))))

    (it "returns true for computer ship at computer city"
      (let [ship {:type :destroyer :owner :computer :hits 2}
            city {:type :city :city-status :computer}]
        (should (uc/ship-can-dock? ship city))))))
(run-specs)
